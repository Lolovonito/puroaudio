package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Lyrics
import com.puroaudio.player.data.LyricsRepository
import com.puroaudio.player.data.Song
import com.puroaudio.player.playback.BitPerfectState
import com.puroaudio.player.ArtworkUiState
import com.puroaudio.player.LyricsUiState
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.Artwork
import com.puroaudio.player.ui.components.BitPerfectBadge
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.formatBits
import com.puroaudio.player.ui.formatDuration
import com.puroaudio.player.ui.formatSampleRate
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroBackground
import com.puroaudio.player.ui.theme.PuroSurfaceHigh
import com.puroaudio.player.ui.theme.PuroTextDim
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerState: PlayerUiState,
    bitPerfect: BitPerfectState,
    lyricsState: LyricsUiState,
    artworkState: ArtworkUiState,
    isFavorite: Boolean,
    showLyrics: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: (Song) -> Unit,
    onRefreshLyrics: (Song) -> Unit,
    onRefreshArtwork: (Song) -> Unit,
    onToggleLyrics: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onQueueSong: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val song = playerState.currentSong
    if (song == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PuroBackground),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", tint = Color.White)
                }
            }
            EmptyState("Nada reproduciéndose", "Elige una canción de tu biblioteca.")
        }
        return
    }

    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var volumeSliderValue by remember { mutableFloatStateOf(playerState.volume) }
    var isVolumeDragging by remember { mutableStateOf(false) }
    val duration = max(1L, playerState.durationMs)

    LaunchedEffect(playerState.positionMs) {
        if (!dragging) dragValue = playerState.positionMs.toFloat()
    }

    LaunchedEffect(playerState.volume) {
        if (!isVolumeDragging) volumeSliderValue = playerState.volume
    }

    val artworkModel = if (artworkState.songId == song.id && artworkState.file != null) {
        artworkState.file
    } else {
        song.artworkUri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF211A06), PuroBackground, PuroBackground),
                )
            )
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Reproduciendo",
                    style = MaterialTheme.typography.labelMedium,
                    color = PuroTextDim,
                )
                BitPerfectBadge(bitPerfect, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Más", tint = Color.White)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Descargar portada") },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onRefreshArtwork(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Buscar letra") },
                    leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onRefreshLyrics(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Opciones de canción") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMore(song)
                    },
                )
            }
        }

        if (showLyrics) {
            LyricsPanel(
                lyrics = lyricsState.lyrics,
                loading = lyricsState.loading,
                error = lyricsState.error,
                positionMs = playerState.positionMs,
                onRetry = { onRefreshLyrics(song) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val artSize = minOf(maxWidth * 0.82f, maxHeight * 0.66f, 340.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(
                        model = artworkModel,
                        modifier = Modifier.size(artSize),
                        cornerRadius = 24.dp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = PuroTextDim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PuroTextDim.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricPill(formatSampleRate(bitPerfect.sampleRate.coerceAtLeast(0)))
                        MetricPill(formatBits(bitPerfect.bits.coerceAtLeast(0)))
                        MetricPill(if (bitPerfect.bitPerfectActive) "Bit-perfect" else "PCM")
                    }
                }
            }
        }

        Slider(
            value = (if (dragging) dragValue else playerState.positionMs.toFloat()).coerceIn(0f, duration.toFloat()),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = PuroAmber,
                activeTrackColor = PuroAmber,
                inactiveTrackColor = PuroSurfaceHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(if (dragging) dragValue.toLong() else playerState.positionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = PuroTextDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(playerState.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = PuroTextDim,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (playerState.shuffle) PuroAmber else PuroTextDim,
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PuroAmber)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pausa" else "Reproducir",
                    tint = Color(0xFF1A1400),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Repetir",
                    tint = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        PuroAmber
                    } else {
                        PuroTextDim
                    },
                )
            }
        }

        // Control de volumen
        val currentVol = if (isVolumeDragging) volumeSliderValue else playerState.volume
        val volumePercent = (currentVol * 100f).roundToInt().coerceIn(0, 100)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    currentVol <= 0.001f -> Icons.Rounded.VolumeMute
                    currentVol < 0.5f -> Icons.Rounded.VolumeDown
                    else -> Icons.Rounded.VolumeUp
                },
                contentDescription = "Volumen",
                tint = PuroTextDim,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = currentVol.coerceIn(0f, 1f),
                onValueChange = {
                    isVolumeDragging = true
                    volumeSliderValue = it
                    onVolumeChange(it)
                },
                onValueChangeFinished = {
                    isVolumeDragging = false
                    onVolumeChange(volumeSliderValue)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = PuroAmber,
                    activeTrackColor = PuroAmber,
                    inactiveTrackColor = PuroSurfaceHigh,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Volumen: $volumePercent%",
                style = MaterialTheme.typography.bodySmall,
                color = PuroTextDim,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) PuroAmber else PuroTextDim,
                )
            }
            IconButton(onClick = onToggleLyrics) {
                Icon(
                    Icons.Rounded.Notes,
                    contentDescription = "Letras",
                    tint = if (showLyrics) PuroAmber else PuroTextDim,
                )
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Cola", tint = PuroTextDim)
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Más", tint = PuroTextDim)
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = com.puroaudio.player.ui.theme.PuroSurface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.height(420.dp)) {
                    itemsIndexed(playerState.queue, key = { _, item -> item.id }) { index, item ->
                        val current = index == playerState.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (current) PuroAmber.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable {
                                    onQueueSong(index)
                                    showQueue = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current) PuroAmber else PuroTextDim,
                                modifier = Modifier.width(28.dp),
                            )
                            Artwork(
                                model = item.artworkUri,
                                modifier = Modifier.size(38.dp),
                                cornerRadius = 7.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (current) PuroAmber else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PuroTextDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = formatDuration(item.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PuroTextDim,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MetricPill(text: String) {
    Text(
        text = text,
        color = PuroAmber,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PuroAmber.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun LyricsPanel(
    lyrics: Lyrics?,
    loading: Boolean,
    error: String?,
    positionMs: Long,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val synced = lyrics?.synced
    val plain = lyrics?.plain
    val lines = remember(synced) {
        if (synced.isNullOrBlank()) emptyList() else LyricsRepository.parse(synced)
    }
    val listState = rememberLazyListState()

    if (loading && lyrics == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PuroAmber)
        }
        return
    }

    if (lyrics == null || (synced.isNullOrBlank() && plain.isNullOrBlank())) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error ?: "No hay letras disponibles",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PuroTextDim,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = PuroAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("Buscar de nuevo", color = PuroAmber)
                }
            }
        }
        return
    }

    if (lines.isNotEmpty()) {
        val currentIndex = lines.indexOfLast { it.timeMs <= positionMs + 200 }
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0) {
                listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
            }
        }
        LazyColumn(
            state = listState,
            modifier = modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == currentIndex
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = if (active) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (active) PuroAmber else PuroTextDim.copy(alpha = 0.75f),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(plain.orEmpty().lines().filter { it.isNotBlank() }) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PuroTextDim,
                )
            }
        }
    }
}
