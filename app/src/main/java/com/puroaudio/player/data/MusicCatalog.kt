package com.puroaudio.player.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicCatalog {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun update(songs: List<Song>) {
        _songs.value = songs
    }

    fun find(id: Long): Song? = _songs.value.firstOrNull { it.id == id }
}
