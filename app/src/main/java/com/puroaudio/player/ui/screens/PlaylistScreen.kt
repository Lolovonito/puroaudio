package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Playlist
import com.puroaudio.player.data.Song
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.PuroTopBar
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun PlaylistScreen(
    playlist: Playlist?,
    songs: List<Song>,
    favorites: Set<Long>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onDeletePlaylist: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (playlist == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PuroTopBar(title = "Playlist", onBack = onBack)
            EmptyState("Playlist no encontrada", "Puede que se haya eliminado.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            PuroTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Eliminar playlist", tint = PuroTextDim)
                    }
                },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    text = "${songs.size} canciones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PuroTextDim,
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onPlayAll(songs) },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PuroAmber,
                        contentColor = Color(0xFF1A1400),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir")
                }
                FilledTonalButton(
                    onClick = { onShuffle(songs.shuffled()) },
                    enabled = songs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    title = "Playlist vacía",
                    subtitle = "Añade canciones desde el menú de cada canción.",
                )
            }
        } else {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.currentSong?.id == song.id,
                    isPlaying = playerState.isPlaying,
                    isFavorite = song.id in favorites,
                    onClick = { onSongClick(song, songs) },
                    onToggleFavorite = { onFavorite(song.id) },
                    onMore = { onMore(song) },
                    onRemove = { onRemoveSong(song.id) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = PuroTextDim,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Playlist creada por ti",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PuroTextDim,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Seguro que quieres eliminar «${playlist.name}»? La música no se borrará.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeletePlaylist()
                        onBack()
                    },
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}
