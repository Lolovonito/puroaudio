package com.puroaudio.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puroaudio.player.data.AppSettings
import com.puroaudio.player.data.ArtworkRepository
import com.puroaudio.player.data.Library
import com.puroaudio.player.data.LibraryStore
import com.puroaudio.player.data.Lyrics
import com.puroaudio.player.data.LyricsRepository
import com.puroaudio.player.data.MusicCatalog
import com.puroaudio.player.data.MusicRepository
import com.puroaudio.player.data.Playlist
import com.puroaudio.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LyricsUiState(
    val songId: Long? = null,
    val lyrics: Lyrics? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class ArtworkUiState(
    val songId: Long? = null,
    val file: File? = null,
    val loading: Boolean = false,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val lyricsRepository = LyricsRepository(application)
    private val artworkRepository = ArtworkRepository(application)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

    private val _artwork = MutableStateFlow(ArtworkUiState())
    val artwork: StateFlow<ArtworkUiState> = _artwork.asStateFlow()

    val favorites: StateFlow<Set<Long>> = LibraryStore.favorites
    val playlists: StateFlow<List<Playlist>> = LibraryStore.playlists
    val autoLyrics: StateFlow<Boolean> = AppSettings.autoLyrics
    val autoArtwork: StateFlow<Boolean> = AppSettings.autoArtwork

    private var lyricsJob: Job? = null
    private var artworkJob: Job? = null

    fun loadLibrary() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                repository.loadLibrary()
            }.onSuccess { loaded ->
                _library.value = loaded
                MusicCatalog.update(loaded.songs)
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "No se pudo leer la biblioteca"
            }
            _loading.value = false
        }
    }

    fun prepareForSong(song: Song?) {
        if (song == null) {
            lyricsJob?.cancel()
            artworkJob?.cancel()
            _lyrics.value = LyricsUiState()
            _artwork.value = ArtworkUiState()
            return
        }
        loadLyrics(song)
        loadArtwork(song)
    }

    fun loadLyrics(song: Song, forceRefresh: Boolean = false) {
        if (!forceRefresh && !AppSettings.autoLyrics.value) return
        if (lyricsJob?.isActive == true && _lyrics.value.songId == song.id) return
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState(songId = song.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.getLyrics(song, forceRefresh)
            _lyrics.value = LyricsUiState(
                songId = song.id,
                lyrics = result,
                loading = false,
                error = if (result == null) "No se encontraron letras para esta canción" else null,
            )
        }
    }

    fun loadArtwork(song: Song, forceRefresh: Boolean = false) {
        if (!forceRefresh && !AppSettings.autoArtwork.value) return
        if (artworkJob?.isActive == true && _artwork.value.songId == song.id) return
        artworkJob?.cancel()
        _artwork.value = ArtworkUiState(songId = song.id, loading = true)
        artworkJob = viewModelScope.launch {
            val embedded = withContext(Dispatchers.IO) { hasEmbeddedArtwork(song) }
            if (embedded && !forceRefresh) {
                _artwork.value = ArtworkUiState(songId = song.id, loading = false)
                return@launch
            }
            val file = artworkRepository.fetchArtwork(song, forceRefresh)
            _artwork.value = ArtworkUiState(songId = song.id, file = file, loading = false)
        }
    }

    fun refreshArtwork(song: Song) = loadArtwork(song, forceRefresh = true)

    fun refreshLyrics(song: Song) = loadLyrics(song, forceRefresh = true)

    private fun hasEmbeddedArtwork(song: Song): Boolean {
        val uri = song.artworkUri ?: return false
        return runCatching {
            val descriptor = getApplication<Application>().contentResolver
                .openAssetFileDescriptor(uri, "r") ?: return@runCatching false
            descriptor.use { }
            true
        }.getOrDefault(false)
    }

    fun toggleFavorite(songId: Long) = LibraryStore.toggleFavorite(songId)

    fun createPlaylist(name: String): Playlist = LibraryStore.createPlaylist(name)

    fun addToPlaylist(playlistId: String, songId: Long) = LibraryStore.addToPlaylist(playlistId, songId)

    fun removeFromPlaylist(playlistId: String, songId: Long) =
        LibraryStore.removeFromPlaylist(playlistId, songId)

    fun deletePlaylist(playlistId: String) = LibraryStore.deletePlaylist(playlistId)

    fun playlistSongs(playlist: Playlist): List<Song> {
        val byId = _library.value.songs.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    fun songById(id: Long): Song? = _library.value.songs.firstOrNull { it.id == id }
}
