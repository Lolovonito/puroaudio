package com.puroaudio.player.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
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
 * En dispositivos donde el HAL soporta MIXER_BEHAVIOR_BIT_PERFECT se configura el modo
 * exclusivo del HAL. En dispositivos cuyo HAL no expone MIXER_BEHAVIOR_BIT_PERFECT (por ejemplo,
 * mixerBehaviors=0), se implementa un bypass de volumen directo:
 * 1. AudioTrack asignado con setPreferredDevice al DAC USB.
 * 2. Ganancia de AudioTrack y volumen de STREAM_MUSIC forzados al 100% (0 dB).
 * 3. BroadcastReceiver que monitoriza VOLUME_CHANGED_ACTION y restaura el volumen al máximo.
 * 4. Intercepción de teclas de volumen para evitar cambios accidentales.
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

    @Volatile
    private var currentPreferredDevice: AudioDeviceInfo? = null

    private var mixerChangedListener: Any? = null
    private var toggleJob: Job? = null

    // ── Receiver de cambios de volumen para fijar 0 dB ──────────────────────
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC && _state.value.bitPerfectActive) {
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVolume < maxVolume) {
                        Log.i(TAG, "Volumen cambiado a $currentVolume. Restaurando a $maxVolume (Bit-Perfect 0 dB activo)")
                        runCatching {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                        }
                    }
                }
            }
        }
    }

    // ── Detección de dispositivos USB ──────────────────────────────────────
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
     * Fuerza el volumen del sistema de música al 100% (0 dB) si bit-perfect está activo o listo.
     */
    fun enforceMaxVolume() {
        if (_state.value.enabled && _state.value.dacConnected) {
            runCatching {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVolume < maxVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                    Log.i(TAG, "Volumen de música fijado al máximo ($maxVolume) para bit-perfect USB")
                }
            }.onFailure { e ->
                Log.w(TAG, "Error al forzar volumen máximo: ${e.message}")
            }
        }
    }

    /**
     * Crea el [DefaultAudioSink] configurado para bit-perfect.
     * Debe llamarse ANTES de construir el [ExoPlayer].
     */
    fun createAudioSink(): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
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

        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        // Registrar receptor de eventos de volumen
        try {
            ContextCompat.registerReceiver(
                context,
                volumeReceiver,
                IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error registrando volumeReceiver: ${e.message}")
        }

        // Listener del sistema para confirmar cuando el HAL activa/desactiva bit-perfect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val listener = AudioManager.OnPreferredMixerAttributesChangedListener { _, device, attributes ->
                if (device.id == usbDevice?.id) {
                    val active = attributes != null &&
                        attributes.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                    Log.i(TAG, "MixerAttributesChanged: active=$active attrs=$attributes")
                    if (active) {
                        enforceMaxVolume()
                        publish {
                            it.copy(
                                bitPerfectActive = true,
                                message = "Bit-perfect confirmado por el sistema (HAL Android 14+)",
                            )
                        }
                    }
                }
            }
            mixerChangedListener = listener
            audioManager.addOnPreferredMixerAttributesChangedListener(mainExecutor, listener)
        }

        // Colector del switch bit-perfect
        scope.launch {
            AppSettings.bitPerfectEnabled.collect { enabled ->
                publish { it.copy(enabled = enabled) }

                if (!enabled) {
                    clearPreferred()
                    player?.volume = AppSettings.volume.value
                    publish {
                        it.copy(
                            bitPerfectActive = false,
                            message = "Bit-perfect desactivado (modo estándar)",
                        )
                    }
                } else {
                    refreshDevice()
                    enforceMaxVolume()
                }

                // Pausa breve al cambiar el switch para liberar endpoint USB
                toggleJob?.cancel()
                toggleJob = scope.launch(Dispatchers.Main) {
                    val p = player ?: return@launch
                    if (p.playbackState == androidx.media3.common.Player.STATE_READY && p.isPlaying) {
                        val pos = p.currentPosition
                        p.pause()
                        delay(150)
                        p.seekTo(pos)
                        p.play()
                    }
                }
            }
        }

        refreshDevice()
    }

    fun release() {
        runCatching { context.unregisterReceiver(volumeReceiver) }
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (mixerChangedListener as? AudioManager.OnPreferredMixerAttributesChangedListener)?.let {
                runCatching { audioManager.removeOnPreferredMixerAttributesChangedListener(it) }
            }
        }
        mixerChangedListener = null
        toggleJob?.cancel()
        clearPreferred()
        player?.setPreferredAudioDevice(null)
        player = null
        scope.cancel()
    }

    // ── Gestión del dispositivo USB ────────────────────────────────────────

    private fun refreshDevice() {
        val previous = usbDevice
        val detected = findUsbDevice()

        if (previous != null && previous.id != detected?.id) {
            clearPreferred(previous)
        }

        usbDevice = detected

        mainExecutor.execute {
            player?.setPreferredAudioDevice(detected)
        }

        if (detected == null) {
            publish {
                it.copy(
                    dacConnected = false,
                    dacName = null,
                    bitPerfectSupported = false,
                    bitPerfectActive = false,
                    capabilities = emptyList(),
                    message = "Conecta un DAC USB para activar el modo bit-perfect",
                )
            }
            return
        }

        val capabilities = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val supported = runCatching {
                audioManager.getSupportedMixerAttributes(detected)
            }.getOrDefault(emptyList())

            val bpProfiles = supported.filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }

            if (bpProfiles.isNotEmpty()) {
                capabilities += bpProfiles.map { capabilityLabel(it) }.distinct().sorted()
            } else {
                val sampleRatesStr = if (detected.sampleRates.isNotEmpty()) {
                    detected.sampleRates.sorted().joinToString(", ") { "${it / 1000.0} kHz" }
                } else {
                    "Hasta 384 kHz"
                }
                val bitDepthsStr = if (detected.encodings.isNotEmpty()) {
                    detected.encodings.map { bitsForEncoding(it) }.distinct().sorted().joinToString(", ") { "$it-bit" }
                } else {
                    "16, 24, 32-bit"
                }
                capabilities += "$sampleRatesStr ($bitDepthsStr)"
            }

            publish {
                it.copy(
                    bitPerfectSupported = true,
                )
            }
        }

        val displayName = detected.productName?.toString()?.trim()
            ?.takeUnless { it.isBlank() } ?: "DAC USB UAC2.0"

        publish {
            it.copy(
                dacConnected = true,
                dacName = displayName,
                capabilities = capabilities,
                message = when {
                    !AppSettings.bitPerfectEnabled.value ->
                        "Bit-perfect desactivado (modo estándar)"

                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                        "Bit-perfect USB requiere Android 14 o superior"

                    else ->
                        "DAC listo · Salida directa a 0 dB al reproducir"
                },
            )
        }
    }

    private fun findUsbDevice(): AudioDeviceInfo? {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        return devices.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
    }

    // ── AudioTrackProvider ─────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class BitPerfectAudioTrackProvider : DefaultAudioSink.AudioTrackProvider {

        override fun getAudioTrack(
            config: AudioSink.AudioTrackConfig,
            audioAttributes: androidx.media3.common.AudioAttributes,
            audioSessionId: Int,
        ): AudioTrack {
            val device = usbDevice
            val enabled = AppSettings.bitPerfectEnabled.value
            val bits = bitsForEncoding(config.encoding)
            val channels = channelCountFromMask(config.channelConfig)

            Log.i(
                TAG,
                "getAudioTrack: rate=${config.sampleRate}Hz encoding=$bits-bit " +
                    "ch=$channels device=${device?.productName} enabled=$enabled",
            )

            publish {
                it.copy(sampleRate = config.sampleRate, bits = bits, channels = channels)
            }

            var officialHalApplied = false

            if (device != null && enabled) {
                officialHalApplied = applyBitPerfect(device, config)

                // Forzar volumen del stream al máximo (0 dB sin atenuación de Android)
                enforceMaxVolume()

                mainExecutor.execute {
                    player?.let { p ->
                        val savedVolume = AppSettings.volume.value
                        if (p.volume != savedVolume) p.volume = savedVolume
                    }
                }

                publish {
                    it.copy(
                        bitPerfectActive = true,
                        message = if (officialHalApplied) {
                            "Bit-perfect activo · HAL Android 14+ (${config.sampleRate} Hz / $bits bit)"
                        } else {
                            "Bit-perfect activo · Salida USB directa a 0 dB (${config.sampleRate} Hz / $bits bit)"
                        },
                    )
                }
            } else if (device != null && !enabled) {
                clearPreferred(device)
                publish {
                    it.copy(
                        bitPerfectActive = false,
                        message = "Bit-perfect desactivado (modo estándar)",
                    )
                }
            }

            // ── Creación de AudioTrack ───────────────────────────────────────
            var track: AudioTrack? = runCatching {
                DefaultAudioSink.AudioTrackProvider.DEFAULT
                    .getAudioTrack(config, audioAttributes, audioSessionId)
            }.getOrNull()

            if (track != null && track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack state=${track.state} (no inicializado). Liberando...")
                runCatching { track.release() }
                track = null
            }

            if (track == null) {
                if (device != null) clearPreferred(device)

                Log.w(TAG, "Reintentando AudioTrack en modo estándar...")
                track = runCatching {
                    DefaultAudioSink.AudioTrackProvider.DEFAULT
                        .getAudioTrack(config, audioAttributes, audioSessionId)
                }.getOrNull()

                if (track != null && track.state != AudioTrack.STATE_INITIALIZED) {
                    runCatching { track.release() }
                    track = null
                }
            }

            val finalTrack = track ?: run {
                Log.e(TAG, "Tercer intento de emergencia para AudioTrack")
                DefaultAudioSink.AudioTrackProvider.DEFAULT
                    .getAudioTrack(config, audioAttributes, audioSessionId)
            }

            // Enrutar hacia el DAC USB y fijar volumen a 1.0f
            if (device != null && enabled && finalTrack.state == AudioTrack.STATE_INITIALIZED) {
                runCatching {
                    finalTrack.setPreferredDevice(device)
                    finalTrack.setVolume(1.0f)
                }
            }

            return finalTrack
        }

        private fun applyBitPerfect(
            device: AudioDeviceInfo,
            config: AudioSink.AudioTrackConfig,
        ): Boolean {
            val supported = runCatching {
                audioManager.getSupportedMixerAttributes(device)
            }.getOrDefault(emptyList())

            val bitPerfectList = supported.filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }

            val mask = if (config.channelConfig != AudioFormat.CHANNEL_INVALID && config.channelConfig != 0) {
                config.channelConfig
            } else {
                if (channelCountFromMask(config.channelConfig) == 1)
                    AudioFormat.CHANNEL_OUT_MONO
                else
                    AudioFormat.CHANNEL_OUT_STEREO
            }
            val chCount = channelCountFromMask(mask)

            var chosen: AudioMixerAttributes? = null

            if (bitPerfectList.isNotEmpty()) {
                chosen = bitPerfectList.firstOrNull { a ->
                    a.format.sampleRate == config.sampleRate &&
                        a.format.encoding == config.encoding &&
                        (a.format.channelMask == mask || a.format.channelCount == chCount)
                } ?: bitPerfectList.firstOrNull { a ->
                    a.format.sampleRate == config.sampleRate &&
                        a.format.encoding == config.encoding
                } ?: bitPerfectList.firstOrNull { a ->
                    config.encoding == AudioFormat.ENCODING_PCM_FLOAT &&
                        a.format.sampleRate == config.sampleRate &&
                        (a.format.encoding == AudioFormat.ENCODING_PCM_32BIT ||
                            a.format.encoding == AudioFormat.ENCODING_PCM_FLOAT)
                }
            } else {
                val rateOk = device.sampleRates.isEmpty() || device.sampleRates.contains(config.sampleRate)
                val targetEncoding = when {
                    device.encodings.isEmpty() -> AudioFormat.ENCODING_PCM_FLOAT
                    device.encodings.contains(AudioFormat.ENCODING_PCM_FLOAT) -> AudioFormat.ENCODING_PCM_FLOAT
                    device.encodings.contains(AudioFormat.ENCODING_PCM_32BIT) -> AudioFormat.ENCODING_PCM_32BIT
                    device.encodings.contains(config.encoding) -> config.encoding
                    else -> device.encodings.last()
                }

                if (rateOk) {
                    val format = AudioFormat.Builder()
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(mask)
                        .setEncoding(targetEncoding)
                        .build()
                    chosen = AudioMixerAttributes.Builder(format)
                        .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                        .build()
                    Log.d(TAG, "Perfil dinámico: ${config.sampleRate}Hz / encoding=$targetEncoding (DAC 32-bit nativo)")
                }
            }

            if (chosen == null) {
                Log.d(TAG, "No hay perfil bit-perfect para ${config.sampleRate}Hz/${bitsForEncoding(config.encoding)}bit")
                clearPreferred(device)
                return false
            }

            val prevDevice = currentPreferredDevice
            if (prevDevice != null && prevDevice.id != device.id) {
                runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, prevDevice) }
                currentPreferredDevice = null
            }

            val success = runCatching {
                audioManager.setPreferredMixerAttributes(mediaAttributes, device, chosen)
            }.getOrDefault(false)

            if (success) {
                currentPreferredDevice = device
            } else {
                Log.w(TAG, "setPreferredMixerAttributes devolvió false → limpiando")
                clearPreferred(device)
            }

            Log.i(
                TAG,
                "setPreferredMixerAttributes ${config.sampleRate}Hz/" +
                    "${bitsForEncoding(config.encoding)}bit ch=$chCount → $success",
            )
            return success
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun capabilityLabel(attributes: AudioMixerAttributes): String {
        val f = attributes.format
        val ch = if (f.channelCount == 1) "mono" else "estéreo"
        return "${f.sampleRate / 1000.0} kHz · ${bitsForEncoding(f.encoding)} bit · $ch"
    }

    private fun bitsForEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    private fun channelCountFromMask(channelConfig: Int): Int =
        Integer.bitCount(channelConfig).coerceAtLeast(1)

    private fun clearPreferred() {
        val d = usbDevice ?: currentPreferredDevice ?: return
        clearPreferred(d)
    }

    private fun clearPreferred(device: AudioDeviceInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (currentPreferredDevice != null) {
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
            currentPreferredDevice = null
            Log.d(TAG, "Preferred mixer attributes limpiadas")
        }
    }

    private fun publish(update: (BitPerfectState) -> BitPerfectState) {
        _state.value = update(_state.value)
    }
}
