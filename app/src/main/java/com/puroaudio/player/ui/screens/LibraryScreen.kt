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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puroaudio.player.data.Library
import com.puroaudio.player.data.Playlist
import com.puroaudio.player.data.Song
import com.puroaudio.player.PlayerUiState
import com.puroaudio.player.ui.components.AlbumListRow
import com.puroaudio.player.ui.components.ArtistRow
import com.puroaudio.player.ui.components.EmptyState
import com.puroaudio.player.ui.components.PuroTopBar
import com.puroaudio.player.ui.components.SectionHeader
import com.puroaudio.player.ui.components.SongRow
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroSurfaceHigh
import com.puroaudio.player.ui.theme.PuroTextDim

private val tabs = listOf("Canciones", "Álbumes", "Artistas", "Favoritos", "Playlists")

@Composable
fun LibraryScreen(
    library: Library,
    favorites: Set<Long>,
    playlists: List<Playlist>,
    playerState: PlayerUiState,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onCreatePlaylist: (String) -> Playlist,
    onFavorite: (Long) -> Unit,
    onMore: (Song) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
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
            PuroTopBar(title = "Biblioteca")
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs) { label ->
                    val index = tabs.indexOf(label)
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PuroAmber,
                            selectedLabelColor = Color(0xFF1A1400),
                            labelColor = PuroTextDim,
                        ),
                    )
                }
            }
        }

        when (tab) {
            0 -> {
                if (library.songs.isEmpty()) {
                    item { EmptyState("Sin canciones", "Añade música a tu dispositivo y actualiza.") }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${library.songs.size} canciones",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PuroTextDim,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(onClick = { onSongClick(library.songs.first(), library.songs) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reproducir")
                            }
                        }
                    }
                    items(library.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = playerState.currentSong?.id == song.id,
                            isPlaying = playerState.isPlaying,
                            isFavorite = song.id in favorites,
                            onClick = { onSongClick(song, library.songs) },
                            onToggleFavorite = { onFavorite(song.id) },
                            onMore = { onMore(song) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
            1 -> {
                if (library.albums.isEmpty()) {
                    item { EmptyState("Sin álbumes", "Tus álbumes aparecerán aquí.") }
                } else {
                    items(library.albums, key = { it.id }) { album ->
                        AlbumListRow(
                            album = album,
                            onClick = { onAlbumClick(album.id) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
            2 -> {
                if (library.artists.isEmpty()) {
                    item { EmptyState("Sin artistas", "Tus artistas aparecerán aquí.") }
                } else {
                    items(library.artists, key = { it.name }) { artist ->
                        ArtistRow(
                            artist = artist,
                            onClick = { onArtistClick(artist.name) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
            3 -> {
                if (favoriteSongs.isEmpty()) {
                    item { EmptyState("Sin favoritos", "Marca canciones con el corazón para verlas aquí.") }
                } else {
                    items(favoriteSongs, key = { "fav_${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = playerState.currentSong?.id == song.id,
                            isPlaying = playerState.isPlaying,
                            isFavorite = true,
                            onClick = { onSongClick(song, favoriteSongs) },
                            onToggleFavorite = { onFavorite(song.id) },
                            onMore = { onMore(song) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
            else -> {
                item {
                    SectionHeader(
                        title = "Tus playlists",
                        action = "Nueva",
                        onAction = { showCreate = true },
                    )
                }
                if (playlists.isEmpty()) {
                    item { EmptyState("Sin playlists", "Crea una playlist con el botón Nueva.") }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name.trim())
                            showCreate = false
                        }
                    },
                ) { Text("Crear", color = PuroAmber) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(PuroSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = PuroAmber,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.songIds.size} canciones",
                style = MaterialTheme.typography.bodyMedium,
                color = PuroTextDim,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = PuroTextDim,
            modifier = Modifier.size(18.dp),
        )
    }
}
