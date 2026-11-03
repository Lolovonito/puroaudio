package com.puroaudio.player.ui

import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatSampleRate(hz: Int): String {
    if (hz <= 0) return "—"
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }
}

fun formatBits(bits: Int): String = if (bits > 0) "$bits bit" else "—"
