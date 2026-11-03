# 🎵 PuroAudio

Reproductor de música audiófilo para Android con salida **bit-perfect** hacia DAC USB, motor de reproducción basado en **Media3 / ExoPlayer** y soporte completo para formatos de alta resolución.

![Android](https://img.shields.io/badge/Android-14%2B-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?logo=kotlin)
![License](https://img.shields.io/badge/Licencia-MIT-blue)
![Versión](https://img.shields.io/badge/Versión-1.0-orange)

---

## ✨ Características principales

| Función | Descripción |
|---------|-------------|
| 🎚️ **Modo Bit-Perfect** | Envía PCM directamente al DAC USB sin pasar por el mezclador de Android |
| 🔊 **Soporte DAC USB** | Compatible con DACs UAC2.0 de alta resolución (hasta 32-bit / 384 kHz) |
| 📚 **Biblioteca musical** | Canciones, álbumes, artistas, favoritos y listas de reproducción propias |
| 🎤 **Letras sincronizadas** | Descarga automática desde LRCLIB con resaltado y auto-scroll |
| 🖼️ **Portadas automáticas** | Obtención desde iTunes cuando el archivo no tiene carátula incrustada |
| ▶️ **Reproducción en segundo plano** | Servicio `MediaSessionService` con controles en la notificación |
| 🔁 **Modos de reproducción** | Cola, siguiente/anterior, aleatorio y repetir (todo / una) |
| 🌙 **Material 3 Oscuro** | Tema oscuro con acento ámbar y diseño Jetpack Compose moderno |

---

## 📱 Capturas de pantalla

> La app incluye pantalla "Ahora suena" con portada, controles, cola, letras y estado del DAC USB.

---

## 🔌 Modo Bit-Perfect — Cómo funciona

### Flujo de audio

```
Archivo de audio (FLAC/WAV/etc.)
        ↓
   MediaCodec (decodificación)
        ↓
   ExoPlayer + BitPerfectEngine
        ↓
   AudioTrack (PCM 32-bit / Float)
        ↓
   DAC USB (KM-HIFI-384KHZ, etc.)
        ↓
   Salida analógica (auriculares / amplificador)
```

### Mecanismo en Android 14+

1. **Detección del DAC:** El motor escanea dispositivos `TYPE_USB_DEVICE`, `TYPE_USB_HEADSET` y `TYPE_USB_ACCESSORY`.
2. **Análisis del formato:** Lee frecuencia de muestreo, canales y profundidad de bits con `MediaExtractor` / `MediaMetadataRetriever`.
3. **Solicitud de modo exclusivo:** Llama a `AudioManager.setPreferredMixerAttributes()` con `MIXER_BEHAVIOR_BIT_PERFECT`.
4. **Fallback inteligente:** Si el HAL del fabricante no soporta `MIXER_BEHAVIOR_BIT_PERFECT` (como en algunos dispositivos ZTE, Xiaomi, etc.), el motor activa el modo de **salida directa USB**:
   - Fija el volumen del sistema (`STREAM_MUSIC`) al 100% (0 dB).
   - Registra un `BroadcastReceiver` que restaura el volumen si el sistema lo cambia.
   - El control de volumen se realiza **digitalmente dentro de ExoPlayer** (slider en la UI).
5. **Monitorización:** El listener `OnPreferredMixerAttributesChangedListener` confirma si el HAL activó el modo exclusivo.

### Controles de volumen reasignados

Cuando el modo Bit-Perfect está activo con un DAC USB conectado, los botones físicos de volumen se reasignan:

| Gesto | Acción |
|-------|--------|
| **Doble tap** (↑ o ↓) | ⏯ Pausar / Reanudar |
| **Pulsación larga** en ↑ | ⏭ Siguiente pista |
| **Pulsación larga** en ↓ | ⏮ Pista anterior |

El volumen digital se controla con el **slider de volumen** en la pantalla del reproductor.

### Limitaciones

- El modo bit-perfect exclusivo del HAL requiere **Android 14+** y que el fabricante del dispositivo lo implemente.
- En dispositivos donde el HAL no lo soporta, se usa el modo de **salida directa con volumen fijo a 0 dB**.
- No hay decodificación nativa de **DSD (DSF/DFF)** ni **MQA**.
- La confirmación final del modo bit-perfect depende del firmware del dispositivo.

---

## 🎵 Formatos soportados

| Formato | Soporte |
|---------|---------|
| FLAC | ✅ (hasta 32-bit / 384 kHz) |
| WAV / AIFF | ✅ |
| MP3 | ✅ |
| AAC / M4A / ALAC | ✅ |
| OGG / Opus | ✅ |
| DSD (DSF/DFF) | ❌ No soportado |
| MQA | ❌ No soportado |

> Soporte adicional según los codecs disponibles en `MediaCodec` del dispositivo.

---

## 🛠️ Compilar el proyecto

### Requisitos

- **JDK 17+** (recomendado: JBR de Android Studio)
- **Android SDK** con plataforma 36 (Android 16)
- **Android Studio Meerkat** o superior (opcional, se puede compilar por línea de comandos)

### Compilación

```bat
cd proyecto
gradlew.bat assembleDebug
```

El APK de depuración se genera en:

```
app\build\outputs\apk\debug\app-debug.apk
```

### Instalación por ADB

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Depuración inalámbrica

```bat
adb tcpip 5555
adb connect <IP_DEL_DISPOSITIVO>:5555
adb -s <IP_DEL_DISPOSITIVO>:5555 install -r app-debug.apk
```

---

## 🗂️ Estructura del proyecto

```
app/src/main/java/com/puroaudio/player/
├── App.kt                        # Navegación y composición principal (Compose)
├── MainActivity.kt               # Actividad principal + interceptación de botones de volumen
├── PlayerViewModel.kt            # Estado del reproductor, volumen, cola, skip
├── LibraryViewModel.kt           # Biblioteca, letras, portadas, playlists
├── data/
│   ├── AppSettings.kt            # Preferencias (bit-perfect, volumen, etc.)
│   ├── MusicCatalog.kt           # Acceso a MediaStore
│   ├── Song.kt                   # Modelo de datos de canción
│   └── ...
├── playback/
│   ├── PlaybackService.kt        # MediaSessionService + ExoPlayer lifecycle
│   ├── BitPerfectEngine.kt       # Motor bit-perfect + fallback USB directo
│   ├── BitPerfectEngine_original.kt  # Copia de seguridad del motor (solo API Android)
│   └── UsbAudioStreamer.kt       # Wrapper para streaming USB directo (Opción C)
└── ui/
    ├── Theme.kt                  # Material 3 oscuro + acento ámbar
    ├── screens/
    │   ├── NowPlayingScreen.kt   # Pantalla principal con slider de volumen
    │   ├── LibraryScreen.kt
    │   └── ...
    └── components/               # Componentes Compose reutilizables
```

---

## ⚙️ Permisos requeridos

| Permiso | Propósito |
|---------|-----------|
| `READ_MEDIA_AUDIO` | Acceso a la biblioteca musical |
| `FOREGROUND_SERVICE` | Reproducción en segundo plano |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Notificación de reproducción |
| `android.hardware.usb.host` | Comunicación con el DAC USB |

---

## 🔧 Configuración del DAC USB

La app fue desarrollada y probada con el DAC **KM-HIFI-384KHZ** (TTGK Technology Co., Ltd):

| Parámetro | Valor |
|-----------|-------|
| Interfaz | USB Audio Class 2.0 (UAC2) |
| Resolución máxima | 32-bit |
| Frecuencia máxima | 384 kHz |
| Canales | Estéreo |
| VendorID | 0x3302 (13058) |
| ProductID | 0x3366 (13158) |

Compatible con cualquier DAC USB UAC2.0 que Android reconozca como `AUDIO_DEVICE_OUT_USB_HEADSET`.

---

## 📦 Descarga

Descarga el APK más reciente desde la sección [**Releases**](../../releases).

---

## 📝 Licencia

MIT License — consulta el archivo [LICENSE](LICENSE) para más información.

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor abre un *Issue* antes de enviar un *Pull Request* para discutir los cambios propuestos.
