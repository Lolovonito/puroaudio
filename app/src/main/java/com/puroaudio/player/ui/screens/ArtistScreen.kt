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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.puroaudio.player.data.Artist
import com.puroaudio.player.data.Song
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.Artwork
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.PuroTopBar
import com.puroaudio.player.ui.components.SectionHeader
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun ArtistScreen(
    artist: Artist?,
    favorites: Set<Long>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
) {
    if (artist == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PuroTopBar(title = "Artista", onBack = onBack)
            EmptyState("Artista no encontrado", "Vuelve a la biblioteca y vuelve a intentarlo.")
        }
        return
    }

    val groups = artist.songs.groupBy { it.albumId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item { PuroTopBar(title = "", onBack = onBack) }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    model = artist.songs.firstOrNull()?.artworkUri,
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color.Transparent, CircleShape),
                    cornerRadius = 75.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${artist.albumCount} álbumes · ${artist.songs.size} canciones",
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
                    onClick = { onPlayAll(artist.songs) },
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
                    onClick = { onShuffle(artist.songs.shuffled()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }
        groups.forEach { (albumId, songs) ->
            item(key = "artist_album_$albumId") {
                SectionHeader(
                    title = songs.first().album,
                    action = "Abrir",
                    onAction = { onAlbumClick(albumId) },
                )
            }
            items(
                songs.sortedWith(compareBy<Song> { it.trackNumber }.thenBy { it.title }),
                key = { "artist_song_${it.id}" },
            ) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.currentSong?.id == song.id,
                    isPlaying = playerState.isPlaying,
                    isFavorite = song.id in favorites,
                    onClick = { onSongClick(song, artist.songs) },
                    onToggleFavorite = { onFavorite(song.id) },
                    onMore = { onMore(song) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    showArtwork = false,
                    leadingText = if (song.trackNumber > 0) song.trackNumber.toString() else null,
                )
            }
        }
    }
}
