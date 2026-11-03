package com.puroaudio.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.puroaudio.player.data.AppSettings
import com.puroaudio.player.data.MusicCatalog
import com.puroaudio.player.data.Song
import com.puroaudio.player.playback.BitPerfectEngine
import com.puroaudio.player.playback.BitPerfectState
import com.puroaudio.player.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connected: Boolean = false,
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackState: Int = Player.STATE_IDLE,
    val error: String? = null,
    val volume: Float = 1.0f,
) {
    val hasNext: Boolean
        get() = currentIndex < queue.lastIndex || repeatMode != Player.REPEAT_MODE_OFF

    val hasPrevious: Boolean
        get() = currentIndex > 0 || positionMs > 3_000 || repeatMode != Player.REPEAT_MODE_OFF
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState(volume = AppSettings.volume.value))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val bitPerfect: StateFlow<BitPerfectState> = BitPerfectEngine.state

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val detailed = error.cause?.message ?: error.message ?: "Error de reproducción (${error.errorCodeName})"
            _state.update { it.copy(error = detailed) }
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val context = getApplication<Application>()
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val result = runCatching { future.get() }
            val connected = result.getOrNull()
            if (connected == null) {
                _state.update {
                    it.copy(
                        connected = false,
                        error = result.exceptionOrNull()?.message
                            ?: "No se pudo conectar con el reproductor",
                    )
                }
                return@addListener
            }
            controller = connected
            connected.addListener(listener)
            val initialVol = AppSettings.volume.value
            connected.volume = initialVol
            _state.update { it.copy(connected = true, error = null, volume = initialVol) }
            sync(connected)
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                controller?.let { syncProgress(it) }
                delay(500)
            }
        }
    }

    private fun sync(player: Player) {
        val song = songFrom(player.currentMediaItem)
        val duration = when {
            player.duration > 0 -> player.duration
            else -> song?.durationMs ?: 0L
        }
        _state.update {
            it.copy(
                currentSong = song,
                queue = buildQueue(player),
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playbackState = player.playbackState,
                volume = player.volume,
            )
        }
    }

    private fun syncProgress(player: Player) {
        val duration = when {
            player.duration > 0 -> player.duration
            else -> _state.value.currentSong?.durationMs ?: 0L
        }
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            )
        }
    }

    private fun buildQueue(player: Player): List<Song> {
        if (player.mediaItemCount == 0) return emptyList()
        val queue = ArrayList<Song>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            queue += songFrom(item) ?: continue
        }
        return queue
    }

    private fun songFrom(item: MediaItem?): Song? {
        if (item == null) return null
        val id = item.mediaId.toLongOrNull() ?: return null
        MusicCatalog.find(id)?.let { return it }

        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val albumId = extras?.getLong("album_id", -1L) ?: -1L
        return Song(
            id = id,
            title = metadata.title?.toString() ?: "Sin título",
            artist = metadata.artist?.toString() ?: "Artista desconocido",
            album = metadata.albumTitle?.toString() ?: "Álbum desconocido",
            albumId = albumId,
            durationMs = extras?.getLong("duration", 0L) ?: 0L,
            trackNumber = extras?.getInt("track", 0) ?: 0,
            year = extras?.getInt("year", 0) ?: 0,
            path = null,
            mimeType = extras?.getString("mime"),
            dateAdded = extras?.getLong("date_added", 0L) ?: 0L,
            uri = item.localConfiguration?.uri ?: Uri.EMPTY,
            artworkUri = metadata.artworkUri,
        )
    }

    private fun toMediaItem(song: Song): MediaItem {
        val extras = Bundle().apply {
            putLong("album_id", song.albumId)
            putLong("duration", song.durationMs)
            putInt("track", song.trackNumber)
            putInt("year", song.year)
            putLong("date_added", song.dateAdded)
            putString("mime", song.mimeType)
        }
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val player = controller ?: run {
            _state.update { it.copy(error = "El reproductor aún se está conectando") }
            return
        }
        if (songs.isEmpty()) return
        _state.update { it.copy(error = null) }
        val index = startIndex.coerceIn(0, songs.lastIndex)
        player.setMediaItems(songs.map(::toMediaItem), index, 0L)
        player.prepare()
        player.play()
    }

    fun playSong(song: Song, queue: List<Song>) {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(if (queue.isEmpty()) listOf(song) else queue, index)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        _state.update { it.copy(error = null) }
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) {
                player.prepare()
            }
            player.play()
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        controller?.volume = clamped
        AppSettings.setVolume(clamped)
        _state.update { it.copy(volume = clamped) }
    }

    fun skipToNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > 3_000) {
            player.seekTo(0L)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L)))
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(song: Song) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            playSongs(listOf(song), 0)
        } else {
            val index = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            player.addMediaItem(index, toMediaItem(song))
        }
    }

    fun addToQueue(songs: List<Song>) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            playSongs(songs, 0)
        } else {
            player.addMediaItems(songs.map(::toMediaItem))
        }
    }

    fun playQueueIndex(index: Int) {
        controller?.seekToDefaultPosition(index)
        controller?.play()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }
}
