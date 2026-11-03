package com.puroaudio.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object LibraryStore {

    private const val PREFS = "puro_audio_library"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_PLAYLISTS = "playlists"

    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _favorites.value = prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        _playlists.value = decodePlaylists(prefs.getString(KEY_PLAYLISTS, null))
    }

    fun toggleFavorite(songId: Long) {
        val current = _favorites.value
        val next = if (songId in current) current - songId else current + songId
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAVORITES, next.map { it.toString() }.toSet()).apply()
    }

    fun isFavorite(songId: Long): Boolean = songId in _favorites.value

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            songIds = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        setPlaylists(_playlists.value + playlist)
        return playlist
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        setPlaylists(_playlists.value.map { playlist ->
            if (playlist.id == playlistId && songId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else {
                playlist
            }
        })
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        setPlaylists(_playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(songIds = playlist.songIds - songId)
            } else {
                playlist
            }
        })
    }

    fun deletePlaylist(playlistId: String) {
        setPlaylists(_playlists.value.filterNot { it.id == playlistId })
    }

    private fun setPlaylists(playlists: List<Playlist>) {
        _playlists.value = playlists
        prefs.edit().putString(KEY_PLAYLISTS, encodePlaylists(playlists)).apply()
    }

    private fun encodePlaylists(playlists: List<Playlist>): String {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val ids = JSONArray()
            playlist.songIds.forEach { ids.put(it) }
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("createdAt", playlist.createdAt)
                    .put("songIds", ids)
            )
        }
        return array.toString()
    }

    private fun decodePlaylists(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ids = obj.optJSONArray("songIds") ?: JSONArray()
                    add(
                        Playlist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            songIds = buildList {
                                for (j in 0 until ids.length()) add(ids.getLong(j))
                            },
                            createdAt = obj.optLong("createdAt"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
