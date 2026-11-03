package com.puroaudio.player

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.puroaudio.player.data.AppSettings
import com.puroaudio.player.data.Song
import com.puroaudio.player.ui.components.MiniPlayer
import com.puroaudio.player.ui.components.SongActionsSheet
import com.puroaudio.player.ui.screens.AlbumScreen
import com.puroaudio.player.ui.screens.ArtistScreen
import com.puroaudio.player.ui.screens.HomeScreen
import com.puroaudio.player.ui.screens.LibraryScreen
import com.puroaudio.player.ui.screens.NowPlayingScreen
import com.puroaudio.player.ui.screens.PlaylistScreen
import com.puroaudio.player.ui.screens.SearchScreen
import com.puroaudio.player.ui.screens.SettingsScreen
import com.puroaudio.player.ui.theme.PuroAmber
import com.puroaudio.player.ui.theme.PuroBackground
import com.puroaudio.player.ui.theme.PuroSurface
import com.puroaudio.player.ui.theme.PuroTextDim

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabItems = listOf(
    TabItem("home", "Inicio", Icons.Rounded.Home),
    TabItem("library", "Biblioteca", Icons.Rounded.LibraryMusic),
    TabItem("search", "Buscar", Icons.Rounded.Search),
    TabItem("settings", "Ajustes", Icons.Rounded.Settings),
)

@Composable
fun PuroApp(
    playerVm: PlayerViewModel = viewModel(),
    libraryVm: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val library by libraryVm.library.collectAsState()
    val loading by libraryVm.loading.collectAsState()
    val favorites by libraryVm.favorites.collectAsState()
    val playlists by libraryVm.playlists.collectAsState()
    val lyricsState by libraryVm.lyrics.collectAsState()
    val artworkState by libraryVm.artwork.collectAsState()
    val playerState by playerVm.state.collectAsState()
    val bitPerfect by playerVm.bitPerfect.collectAsState()
    val autoLyrics by libraryVm.autoLyrics.collectAsState()
    val autoArtwork by libraryVm.autoArtwork.collectAsState()
    val showLyrics by AppSettings.showLyrics.collectAsState()

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[audioPermission] == true
    }

    fun requestPermissions() {
        val permissions = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted && library.songs.isEmpty() && !loading) {
            libraryVm.loadLibrary()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) libraryVm.loadLibrary()
    }

    LaunchedEffect(playerState.currentSong?.id) {
        libraryVm.prepareForSong(playerState.currentSong)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(playerState.error) {
        playerState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            playerVm.clearError()
        }
    }

    var actionSong by remember { mutableStateOf<Song?>(null) }

    if (!hasPermission) {
        PermissionGate(onRequest = ::requestPermissions)
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBottomBar = route in tabItems.map { it.route }
    val showMiniPlayer = playerState.currentSong != null && route != "player"

    fun openAlbum(albumId: Long) {
        if (albumId > 0) navController.navigate("album/$albumId")
    }

    fun openArtist(name: String) {
        navController.navigate("artist/${Uri.encode(name)}")
    }

    fun play(song: Song, queue: List<Song>) {
        playerVm.playSong(song, if (queue.any { it.id == song.id }) queue else listOf(song))
    }

    Scaffold(
        containerColor = PuroBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    playerState.currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = playerState.isPlaying,
                            positionMs = playerState.positionMs,
                            durationMs = playerState.durationMs,
                            onTogglePlay = playerVm::togglePlayPause,
                            onNext = playerVm::next,
                            onOpen = { navController.navigate("player") },
                        )
                    }
                }
                if (showBottomBar) {
                    NavigationBar(containerColor = PuroSurface) {
                        tabItems.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = {
                                    if (route != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PuroAmber,
                                    selectedTextColor = PuroAmber,
                                    indicatorColor = PuroAmber.copy(alpha = 0.12f),
                                    unselectedIconColor = PuroTextDim,
                                    unselectedTextColor = PuroTextDim,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    library = library,
                    bitPerfect = bitPerfect,
                    playerState = playerState,
                    favorites = favorites,
                    onSongClick = ::play,
                    onAlbumClick = ::openAlbum,
                    onArtistClick = ::openArtist,
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                    onPlayShuffled = { songs -> playerVm.playSongs(songs, 0) },
                    onOpenLibrary = { navController.navigate("library") },
                    onOpenPlayer = { navController.navigate("player") },
                    onRefresh = libraryVm::loadLibrary,
                )
            }
            composable("library") {
                LibraryScreen(
                    library = library,
                    favorites = favorites,
                    playlists = playlists,
                    playerState = playerState,
                    onSongClick = ::play,
                    onAlbumClick = ::openAlbum,
                    onArtistClick = ::openArtist,
                    onPlaylistClick = { navController.navigate("playlist/$it") },
                    onCreatePlaylist = libraryVm::createPlaylist,
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                )
            }
            composable("search") {
                SearchScreen(
                    library = library,
                    favorites = favorites,
                    playerState = playerState,
                    onSongClick = ::play,
                    onAlbumClick = ::openAlbum,
                    onArtistClick = ::openArtist,
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                )
            }
            composable("settings") {
                SettingsScreen(
                    bitPerfect = bitPerfect,
                    autoLyrics = autoLyrics,
                    autoArtwork = autoArtwork,
                    showLyrics = showLyrics,
                    onBitPerfectChange = AppSettings::setBitPerfectEnabled,
                    onAutoLyricsChange = AppSettings::setAutoLyrics,
                    onAutoArtworkChange = AppSettings::setAutoArtwork,
                    onShowLyricsChange = AppSettings::setShowLyrics,
                    onRefreshLibrary = libraryVm::loadLibrary,
                )
            }
            composable("player") {
                NowPlayingScreen(
                    playerState = playerState,
                    bitPerfect = bitPerfect,
                    lyricsState = lyricsState,
                    artworkState = artworkState,
                    isFavorite = playerState.currentSong?.id?.let { it in favorites } == true,
                    showLyrics = showLyrics,
                    onBack = { navController.popBackStack() },
                    onToggleFavorite = {
                        playerState.currentSong?.let { libraryVm.toggleFavorite(it.id) }
                    },
                    onMore = { actionSong = it },
                    onRefreshLyrics = libraryVm::refreshLyrics,
                    onRefreshArtwork = libraryVm::refreshArtwork,
                    onToggleLyrics = { AppSettings.setShowLyrics(!showLyrics) },
                    onSeek = playerVm::seekTo,
                    onPrevious = playerVm::previous,
                    onNext = playerVm::next,
                    onTogglePlay = playerVm::togglePlayPause,
                    onToggleShuffle = playerVm::toggleShuffle,
                    onCycleRepeat = playerVm::cycleRepeatMode,
                    onQueueSong = playerVm::playQueueIndex,
                    onVolumeChange = playerVm::setVolume,
                )
            }
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) { entry ->
                val albumId = entry.arguments?.getLong("albumId") ?: -1L
                val album = library.albums.firstOrNull { it.id == albumId }
                AlbumScreen(
                    album = album,
                    favorites = favorites,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onSongClick = ::play,
                    onPlayAll = { songs -> playerVm.playSongs(songs, 0) },
                    onShuffle = { songs -> playerVm.playSongs(songs, 0) },
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                    onArtistClick = ::openArtist,
                )
            }
            composable(
                route = "artist/{artistName}",
                arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
            ) { entry ->
                val name = Uri.decode(entry.arguments?.getString("artistName").orEmpty())
                val artist = library.artists.firstOrNull { it.name == name }
                ArtistScreen(
                    artist = artist,
                    favorites = favorites,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onSongClick = ::play,
                    onPlayAll = { songs -> playerVm.playSongs(songs, 0) },
                    onShuffle = { songs -> playerVm.playSongs(songs, 0) },
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                    onAlbumClick = ::openAlbum,
                )
            }
            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) { entry ->
                val playlistId = entry.arguments?.getString("playlistId").orEmpty()
                val playlist = playlists.firstOrNull { it.id == playlistId }
                val songs = playlist?.let(libraryVm::playlistSongs).orEmpty()
                PlaylistScreen(
                    playlist = playlist,
                    songs = songs,
                    favorites = favorites,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onSongClick = ::play,
                    onPlayAll = { list -> playerVm.playSongs(list, 0) },
                    onShuffle = { list -> playerVm.playSongs(list, 0) },
                    onFavorite = libraryVm::toggleFavorite,
                    onMore = { actionSong = it },
                    onRemoveSong = { songId ->
                        playlist?.let { libraryVm.removeFromPlaylist(it.id, songId) }
                    },
                    onDeletePlaylist = {
                        playlist?.let { libraryVm.deletePlaylist(it.id) }
                    },
                )
            }
        }
    }

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            playlists = playlists,
            isFavorite = song.id in favorites,
            onDismiss = { actionSong = null },
            onPlayNext = {
                playerVm.playNext(song)
                actionSong = null
            },
            onAddToQueue = {
                playerVm.addToQueue(listOf(song))
                actionSong = null
            },
            onToggleFavorite = {
                libraryVm.toggleFavorite(song.id)
                actionSong = null
            },
            onAddToPlaylist = { playlist ->
                libraryVm.addToPlaylist(playlist.id, song.id)
                actionSong = null
            },
            onCreatePlaylist = { name ->
                val playlist = libraryVm.createPlaylist(name)
                libraryVm.addToPlaylist(playlist.id, song.id)
                actionSong = null
            },
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PuroBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(PuroAmber.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = PuroAmber,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Acceso a tu música",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Puro Audio necesita permiso para leer los archivos de audio de tu dispositivo. " +
                "No se envía nada a internet salvo las búsquedas de letras y portadas que tú actives.",
            style = MaterialTheme.typography.bodyLarge,
            color = PuroTextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) {
            Text("Conceder permiso")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "También se pedirá permiso de notificaciones para los controles de reproducción.",
                style = MaterialTheme.typography.bodyMedium,
                color = PuroTextDim,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Si ya lo concediste y no aparece la música, revisa Ajustes del sistema › Apps › Puro Audio › Permisos.",
            style = MaterialTheme.typography.bodyMedium,
            color = PuroTextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
