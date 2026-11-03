package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puroaudio.player.playback.BitPerfectState
import com.puroaudio.player.ui.components.BitPerfectCard
import com.puroaudio.player.ui.components.PuroTopBar
import com.puroaudio.player.ui.components.SectionHeader
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroOutline
import com.puroaudio.player.ui.theme.PuroSurfaceHigh
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun SettingsScreen(
    bitPerfect: BitPerfectState,
    autoLyrics: Boolean,
    autoArtwork: Boolean,
    showLyrics: Boolean,
    onBitPerfectChange: (Boolean) -> Unit,
    onAutoLyricsChange: (Boolean) -> Unit,
    onAutoArtworkChange: (Boolean) -> Unit,
    onShowLyricsChange: (Boolean) -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item { PuroTopBar(title = "Ajustes") }

        item { SectionHeader("Audio") }
        item {
            SettingsCard {
                ToggleRow(
                    title = "Modo bit-perfect",
                    subtitle = "Envía el PCM al DAC USB sin mezcla ni remuestreo.",
                    checked = bitPerfect.enabled,
                    onCheckedChange = onBitPerfectChange,
                )
                HorizontalDivider(color = PuroOutline)
                BitPerfectCard(
                    state = bitPerfect,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }

        if (bitPerfect.capabilities.isNotEmpty()) {
            item { SectionHeader("Formatos bit-perfect del DAC") }
            item {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bitPerfect.capabilities.forEach { capability ->
                            Text(
                                text = "• $capability",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PuroTextDim,
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("Metadatos automáticos") }
        item {
            SettingsCard {
                ToggleRow(
                    title = "Descargar letras",
                    subtitle = "Busca letras sincronizadas en LRCLIB al reproducir.",
                    checked = autoLyrics,
                    onCheckedChange = onAutoLyricsChange,
                )
                HorizontalDivider(color = PuroOutline)
                ToggleRow(
                    title = "Descargar portadas",
                    subtitle = "Busca carátulas en iTunes cuando el archivo no tenga una.",
                    checked = autoArtwork,
                    onCheckedChange = onAutoArtworkChange,
                )
                HorizontalDivider(color = PuroOutline)
                ToggleRow(
                    title = "Mostrar letras al reproducir",
                    subtitle = "Sustituye la portada por la letra sincronizada.",
                    checked = showLyrics,
                    onCheckedChange = onShowLyricsChange,
                )
            }
        }

        item { SectionHeader("Biblioteca") }
        item {
            SettingsCard {
                TextButton(onClick = onRefreshLibrary, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = PuroAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Volver a escanear la música del dispositivo", color = PuroAmber)
                }
            }
        }

        item { SectionHeader("Acerca de") }
        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Puro Audio 1.0.0",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "Reproductor audiófilo para Android. El modo bit-perfect usa las APIs " +
                            "AudioMixerAttributes de Android 14+ y requiere un DAC USB cuyo firmware " +
                            "exponga el comportamiento BIT_PERFECT para el formato de la pista. " +
                            "La confirmación depende del HAL del fabricante: la app solicita el modo " +
                            "y verifica que el sistema lo mantenga activo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PuroTextDim,
                    )
                    Text(
                        text = "Formatos: MP3, AAC, FLAC, ALAC, WAV, OGG, Opus y los soportados por " +
                            "MediaCodec. DSD/MQA no están soportados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PuroTextDim,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PuroSurfaceHigh)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = PuroTextDim,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1400),
                checkedTrackColor = PuroAmber,
                uncheckedThumbColor = PuroTextDim,
                uncheckedTrackColor = PuroOutline,
            ),
        )
    }
}
