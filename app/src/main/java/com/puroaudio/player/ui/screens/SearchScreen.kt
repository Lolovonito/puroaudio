package com.puroaudio.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Library
import com.puroaudio.player.data.Song
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.AlbumListRow
import com.puroaudio.player.ui.components.ArtistRow
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.SectionHeader
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroSurfaceHigh
import com.puroaudio.player.ui.theme.PuroTextDim

@Composable
fun SearchScreen(
    library: Library,
    favorites: Set<Long>,
    playerState: PlayerUiState,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val songs = remember(q, library.songs) {
        if (q.isBlank()) emptyList() else library.songs.filter { song ->
            song.title.contains(q, ignoreCase = true) ||
                song.artist.contains(q, ignoreCase = true) ||
                song.album.contains(q, ignoreCase = true)
        }.take(30)
    }
    val albums = remember(q, library.albums) {
        if (q.isBlank()) emptyList() else library.albums.filter { album ->
            album.title.contains(q, ignoreCase = true) || album.artist.contains(q, ignoreCase = true)
        }.take(12)
    }
    val artists = remember(q, library.artists) {
        if (q.isBlank()) emptyList() else library.artists.filter {
            it.name.contains(q, ignoreCase = true)
        }.take(12)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar canciones, álbumes o artistas") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }

        if (q.isBlank()) {
            item {
                EmptyState(
                    title = "Busca en tu biblioteca",
                    subtitle = "Escribe un título, artista o álbum.",
                )
            }
        } else if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin resultados",
                    subtitle = "No se encontró nada para «$q».",
                )
            }
        } else {
            if (songs.isNotEmpty()) {
                item { SectionHeader("Canciones") }
                items(songs, key = { "search_song_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = playerState.currentSong?.id == song.id,
                        isPlaying = playerState.isPlaying,
                        isFavorite = song.id in favorites,
                        onClick = { onSongClick(song, songs) },
                        onToggleFavorite = { onFavorite(song.id) },
                        onMore = { onMore(song) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionHeader("Álbumes") }
                items(albums, key = { "search_album_${it.id}" }) { album ->
                    AlbumListRow(
                        album = album,
                        onClick = { onAlbumClick(album.id) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            if (artists.isNotEmpty()) {
                item { SectionHeader("Artistas") }
                items(artists, key = { "search_artist_${it.name}" }) { artist ->
                    ArtistRow(
                        artist = artist,
                        onClick = { onArtistClick(artist.name) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}
