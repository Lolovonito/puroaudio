package com.puroaudio.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettings {

    private const val PREFS = "puro_audio_settings"
    private const val KEY_BIT_PERFECT = "bit_perfect"
    private const val KEY_AUTO_LYRICS = "auto_lyrics"
    private const val KEY_AUTO_ARTWORK = "auto_artwork"
    private const val KEY_SHOW_LYRICS = "show_lyrics"
    private const val KEY_VOLUME = "player_volume"

    private lateinit var prefs: SharedPreferences

    private val _bitPerfectEnabled = MutableStateFlow(true)
    val bitPerfectEnabled: StateFlow<Boolean> = _bitPerfectEnabled.asStateFlow()

    private val _autoLyrics = MutableStateFlow(true)
    val autoLyrics: StateFlow<Boolean> = _autoLyrics.asStateFlow()

    private val _autoArtwork = MutableStateFlow(true)
    val autoArtwork: StateFlow<Boolean> = _autoArtwork.asStateFlow()

    private val _showLyrics = MutableStateFlow(true)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _bitPerfectEnabled.value = prefs.getBoolean(KEY_BIT_PERFECT, true)
        _autoLyrics.value = prefs.getBoolean(KEY_AUTO_LYRICS, true)
        _autoArtwork.value = prefs.getBoolean(KEY_AUTO_ARTWORK, true)
        _showLyrics.value = prefs.getBoolean(KEY_SHOW_LYRICS, true)
        _volume.value = prefs.getFloat(KEY_VOLUME, 1.0f)
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        _bitPerfectEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BIT_PERFECT, enabled).apply()
    }

    fun setAutoLyrics(enabled: Boolean) {
        _autoLyrics.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_LYRICS, enabled).apply()
    }

    fun setAutoArtwork(enabled: Boolean) {
        _autoArtwork.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_ARTWORK, enabled).apply()
    }

    fun setShowLyrics(enabled: Boolean) {
        _showLyrics.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_LYRICS, enabled).apply()
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        if (::prefs.isInitialized) {
            prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        }
    }
}
