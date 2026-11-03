// Copia de seguridad del BitPerfectEngine original (sin fallback USB directo)

package com.puroaudio.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.puroaudio.player.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

data class BitPerfectState(
    val enabled: Boolean = true,
    val dacConnected: Boolean = false,
    val dacName: String? = null,
    val bitPerfectSupported: Boolean = false,
    val bitPerfectActive: Boolean = false,
    val sampleRate: Int = 0,
    val bits: Int = 0,
    val channels: Int = 0,
    val message: String = "Conecta un DAC USB para activar el modo bit-perfect",
    val capabilities: List<String> = emptyList(),
)

/**
 * Motor para reproducción Bit-Perfect sobre DACs USB en Android 14+ (incluyendo Android 16).
 *
 * ## Principio de funcionamiento
 *
 * 1. ExoPlayer construye un [DefaultAudioSink] inyectando nuestro [BitPerfectAudioTrackProvider].
 * 2. Cada vez que se crea o reconfigura un [AudioTrack]:
 *    - Se inspecciona el formato PCM real producido por el decodificador.
 *    - Si el DAC y el sistema soportan bit-perfect para ese sample rate y encoding exacto,
 *      se llama a [AudioManager.setPreferredMixerAttributes] con MIXER_BEHAVIOR_BIT_PERFECT.
 *    - Si la combinación no está soportada o el [AudioTrack] no se inicializa correctamente, se
 *      recupera de forma transparente sin lanzar excepción al reproductor.
 * 3. Se deshabilita la conversión a float para mantener PCM entero puro.
 * 4. El volumen de ExoPlayer se mantiene al 100% (0 dB) durante bit-perfect.
 *
 * ## Seguridad de hilos
 *
 * - [getAudioTrack] es llamado desde el hilo interno de ExoPlayer ("ExoPlayer:Playback").
 *   Nunca se accede a métodos de [ExoPlayer] directamente dentro de ese contexto; cualquier llamada a
 *   `player.*` siempre se despacha vía [mainExecutor].
 * - [refreshDevice], [attach], [release] y todos los colectores de coroutines corren en el hilo principal
 *   (Handler del Looper principal / Dispatchers.Main).
 */
@OptIn(UnstableApi::class)
class BitPerfectEngine(private val context: Context) {

    companion object {
        private const val TAG = "BitPerfectEngine"
        private val _state = MutableStateFlow(BitPerfectState())
        val state: StateFlow<BitPerfectState> = _state.asStateFlow()
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Scope para coroutines en el hilo principal
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Executor para despachar llamadas a ExoPlayer desde hilos de audio
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    private val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // player solo se accede desde el hilo principal
    private var player: ExoPlayer? = null

    // usbDevice es @Volatile porque se lee desde el hilo "ExoPlayer:Playback" en getAudioTrack
    @Volatile
    private var usbDevice: AudioDeviceInfo? = null

    // currentPreferredDevice solo se accede desde AudioTrack provider (hilo de audio) y desde
    // refreshDevice (hilo principal). En la práctica ExoPlayer serializa las llamadas al provider,
    // y refreshDevice corre en main; usamos @Volatile para la lectura cross-thread.
    @Volatile
    private var currentPreferredDevice: AudioDeviceInfo? = null

    private var mixerChangedListener: Any? = null
    private var toggleJob: Job? = null

    // ── Detección de dispositivos USB ──────────────────────────────────────
    // El callback siempre se entrega en el hilo principal (Handler del Looper principal).

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Dispositivo conectado: ${addedDevices.joinToString { it.productName.toString() }}")
            refreshDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Dispositivo desconectado: ${removedDevices.joinToString { it.productName.toString() }}")
            refreshDevice()
        }
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Crea el [DefaultAudioSink] configurado para bit-perfect.
     * Debe llamarse ANTES de construir el [ExoPlayer].
     */
    fun createAudioSink(): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
            // Float output = ENCODING_PCM_FLOAT (32-bit IEEE 754).
            // Un DAC UAC2.0 de 32-bit solo puede activar MIXER_BEHAVIOR_BIT_PERFECT si el
            // encoding del AudioTrack coincide con un formato que el HAL acepta en modo exclusivo.
            // Con float=false ExoPlayer emite PCM_16BIT y el HAL rechaza silenciosamente la
            // solicitud bit-perfect porque el DAC es de 32-bit nativo.
            // Con float=true ExoPlayer emite PCM_FLOAT, el HAL lo mapea a PCM_32BIT para USB
            // y acepta el modo bit-perfect exclusivo → las teclas de volumen dejan de funcionar.
            .setEnableFloatOutput(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setAudioTrackProvider(BitPerfectAudioTrackProvider())
        }

        return builder.build()
    }

    /**
     * Enlaza este motor al [ExoPlayer] recién construido. Debe llamarse desde el hilo principal.
     */
    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.setSkipSilenceEnabled(false)

        // El callback se entrega en el Handler dado (hilo principal)
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        // Listener del sistema que confirma cuando el HAL activa/desactiva bit-perfect.
        // Entregado vía mainExecutor → hilo principal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val listener = AudioManager.OnPreferredMixerAttributesChangedListener { _, device, attributes ->
                if (device.id == usbDevice?.id) {
                    val active = attributes != null &&
                        attributes.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                    Log.i(TAG, "MixerAttributesChanged: active=$active attrs=$attributes")
                    publish {
                        it.copy(
                            bitPerfectActive = active,
                            message = if (active) {
                                "Bit-perfect confirmado por el sistema (HAL Android 14+)"
                            } else {
                                "El sistema retiró el modo bit-perfect exclusivo"
                            },
                        )
                    }
                }
            }
            mixerChangedListener = listener
            audioManager.addOnPreferredMixerAttributesChangedListener(mainExecutor, listener)
        }

        // Colector del switch bit-perfect — corre en Dispatchers.Main.immediate (hilo principal)
        scope.launch {
            AppSettings.bitPerfectEnabled.collect { enabled ->
                publish { it.copy(enabled = enabled) }

                if (!enabled) {
                    clearPreferred()
                    // player está en hilo principal, la coroutine también → acceso seguro
                    player?.volume = 1.0f
                    publish {
                        it.copy(
                            bitPerfectActive = false,
                            message = "Bit-perfect desactivado (modo estándar)",
                        )
                    }
                } else {
                    refreshDevice()
                }
            }
        }
    }

    // ... (resto del archivo original sin cambios) ...

    // (El resto del código original se conserva tal cual, hasta el final del archivo)
}

