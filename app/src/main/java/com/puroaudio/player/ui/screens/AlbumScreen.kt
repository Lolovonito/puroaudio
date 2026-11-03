package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Album
import com.puroaudio.player.data.Song
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.Artwork
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.PuroTopBar
import com.puroaudio.player.ui.components.SongCountRow
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun AlbumScreen(
    album: Album?,
    favorites: Set<Long>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
) {
    if (album == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PuroTopBar(title = "Álbum", onBack = onBack)
            EmptyState("Álbum no encontrado", "Vuelve a la biblioteca y vuelve a intentarlo.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item { PuroTopBar(title = "", onBack = onBack) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Artwork(
                    model = album.artworkUri,
                    modifier = Modifier.size(148.dp),
                    cornerRadius = 18.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = PuroAmber,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onArtistClick(album.artist) },
                    )
                    if (album.year > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = album.year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PuroTextDim,
                        )
                    }
                }
            }
        }
        item { SongCountRow(album.songs.size, album.durationMs) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onPlayAll(album.songs) },
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
                    onClick = { onShuffle(album.songs.shuffled()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }
        items(album.songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = playerState.currentSong?.id == song.id,
                isPlaying = playerState.isPlaying,
                isFavorite = song.id in favorites,
                onClick = { onSongClick(song, album.songs) },
                onToggleFavorite = { onFavorite(song.id) },
                onMore = { onMore(song) },
                modifier = Modifier.padding(horizontal = 12.dp),
                showArtwork = false,
                leadingText = if (song.trackNumber > 0) song.trackNumber.toString() else null,
            )
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
                    text = "${album.songs.size} pistas · ${album.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PuroTextDim,
                )
            }
        }
    }
}
