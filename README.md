# Puro Audio

Reproductor de música audiófilo para Android, con motor de reproducción basado en Media3/ExoPlayer, salida hacia DAC USB y modo bit-perfect mediante las APIs `AudioMixerAttributes` de Android 14+.

## Funciones

- Biblioteca local vía MediaStore: canciones, álbumes, artistas, favoritos y playlists propias.
- Reproducción en segundo plano con `MediaSessionService` y controles en la notificación.
- Cola de reproducción, siguiente/anterior, aleatorio y repetir (todo / una).
- Pantalla "Ahora suena" con portada, controles, cola y letras.
- Letras automáticas desde LRCLIB y letras sincronizadas con resaltado y auto-scroll.
- Portadas automáticas desde iTunes cuando el archivo no tiene carátula incrustada.
- Ajustes de bit-perfect, metadatos automáticos y reescaneo de biblioteca.
- Tema oscuro con acento ámbar, diseño Material 3 + Compose.

## Bit-perfect: cómo funciona y sus límites

El motor hace lo siguiente:

1. Detecta DACs USB conectados (`TYPE_USB_DEVICE`, `TYPE_USB_HEADSET`, `TYPE_USB_ACCESSORY`).
2. Analiza la pista (frecuencia de muestreo, canales y bits) con `MediaExtractor` / `MediaMetadataRetriever`.
3. Consulta los `AudioMixerAttributes` soportados por el DAC y busca uno con `MIXER_BEHAVIOR_BIT_PERFECT` que coincida con el formato de la pista.
4. Solicita al sistema el modo bit-perfect con `AudioManager.setPreferredMixerAttributes(...)` y fija el DAC como dispositivo de salida preferido de ExoPlayer.
5. Muestra el estado (activo, solicitado, no soportado) en la interfaz y en Ajustes.

Requisitos y limitaciones reales:

- El modo bit-perfect USB es una función de **Android 14 o superior**.
- El teléfono debe implementar la parte opcional `AUDIO_OUTPUT_FLAG_BIT_PERFECT` del HAL de audio. Muchos fabricantes no la implementan.
- El DAC debe exponer ese formato (por ejemplo, 44.1 kHz / 24 bits). Si la pista y el DAC no coinciden, la app lo indica y no falsea el estado.
- El audio se decodifica con MediaCodec/Media3. No hay decodificación DSD nativa ni MQA.
- Para bit-perfect real con archivos de alta resolución, el decodificador del dispositivo debe entregar el mismo ancho de muestra que la pista. En algunos móviles el decodificador FLAC entrega 16 bits y el modo no se activará para pistas de 24 bits.
- El volumen del sistema y los efectos de audio del sistema pueden afectar la ruta si el HAL no aísla el stream bit-perfect. La app no aplica EQ ni normalización.
- La app solicita y verifica el modo, pero la confirmación final depende del HAL del fabricante. No se puede garantizar bit-perfect en todos los dispositivos.

## Compilar

Requisitos: JDK 17+ (probado con el JBR de Android Studio 21) y Android SDK con plataforma 36.

```bat
gradlew.bat assembleDebug
```

El APK queda en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

También se incluye una copia en la raíz del proyecto:

```text
PuroAudio-1.0-debug.apk
```

Instalación por ADB:

```bat
adb install -r PuroAudio-1.0-debug.apk
```

## Estructura

```text
app/src/main/java/com/puroaudio/player/
  App.kt                  Navegación y composición principal
  MainActivity.kt
  LibraryViewModel.kt     Biblioteca, letras, portadas, playlists
  PlayerViewModel.kt      Conexión con MediaController y estado del reproductor
  data/                   MediaStore, ajustes, favoritos, letras, portadas
  playback/               PlaybackService, BitPerfectEngine, TrackAnalyzer
  ui/                     Tema, componentes y pantallas Compose
```

## Formatos

MP3, AAC/M4A, FLAC, ALAC, WAV, OGG, Opus y los formatos que soporte MediaCodec en el dispositivo. DSD (DSF/DFF) y MQA no están soportados.

## Notas

- Las letras y portadas se descargan solo si están activadas en Ajustes.
- LRCLIB e iTunes se consultan por HTTPS y los resultados se cachean en el almacenamiento privado de la app.
- Proyecto de ejemplo funcional; no incluye firma de release ni publicación en tiendas.
