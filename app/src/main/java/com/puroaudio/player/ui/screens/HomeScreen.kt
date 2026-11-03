package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Library
import com.puroaudio.player.data.Song
import com.puroaudio.player.playback.BitPerfectState
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.AlbumCard
import com.puroaudio.player.ui.components.ArtistChip
import com.puroaudio.player.ui.components.Artwork
import com.puroaudio.player.ui.components.BitPerfectCard
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.SectionHeader
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun HomeScreen(
    library: Library,
    bitPerfect: BitPerfectState,
    playerState: PlayerUiState,
    favorites: Set<Long>,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
    onPlayShuffled: (List<Song>) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlayer: () -> Unit,
    onRefresh: () -> Unit,
) {
    val recent = remember(library.songs) {
        library.songs.sortedByDescending { it.dateAdded }.take(12)
    }
    val favoriteSongs = remember(library.songs, favorites) {
        library.songs.filter { it.id in favorites }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Puro Audio",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "Tu música, sin pérdidas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PuroTextDim,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar biblioteca",
                        tint = PuroTextDim,
                    )
                }
            }
        }

        item {
            BitPerfectCard(
                state = bitPerfect,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (library.songs.isEmpty()) {
            item {
                EmptyState(
                    title = "No hay música todavía",
                    subtitle = "Añade archivos de audio a tu dispositivo y actualiza la biblioteca.",
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Escanear de nuevo")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onSongClick(library.songs.first(), library.songs) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PuroAmber,
                        contentColor = Color(0xFF1A1400),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir todo")
                }
                FilledTonalButton(
                    onClick = { onPlayShuffled(library.songs.shuffled()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader("Añadidos recientemente") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recent, key = { it.id }) { song ->
                        RecentSongCard(
                            song = song,
                            onClick = { onSongClick(song, recent) },
                        )
                    }
                }
            }
        }

        if (library.albums.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Álbumes",
                    action = "Ver todo",
                    onAction = onOpenLibrary,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(library.albums.take(12), key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
                    }
                }
            }
        }

        if (library.artists.isNotEmpty()) {
            item { SectionHeader(title = "Artistas") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(library.artists.take(12), key = { it.name }) { artist ->
                        ArtistChip(artist = artist, onClick = { onArtistClick(artist.name) })
                    }
                }
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item { SectionHeader(title = "Tus favoritos") }
            items(favoriteSongs.take(6), key = { "fav_${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.currentSong?.id == song.id,
                    isPlaying = playerState.isPlaying,
                    isFavorite = song.id in favorites,
                    onClick = { onSongClick(song, favoriteSongs) },
                    onToggleFavorite = { onFavorite(song.id) },
                    onMore = { onMore(song) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = PuroTextDim,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Conecta un DAC USB y activa bit-perfect en Ajustes para escuchar sin procesado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PuroTextDim,
                )
            }
        }
    }
}

@Composable
private fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            model = song.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            cornerRadius = 14.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = PuroTextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
