package com.puroaudio.player.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val path: String?,
    val mimeType: String?,
    val dateAdded: Long,
    val uri: Uri,
    val artworkUri: Uri?,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val artworkUri: Uri? get() = songs.firstOrNull()?.artworkUri
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int,
)

data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)

data class TrackInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val codec: String,
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
)

data class Lyrics(
    val synced: String?,
    val plain: String?,
) {
    val isEmpty: Boolean get() = synced.isNullOrBlank() && plain.isNullOrBlank()
}

data class LyricLine(
    val timeMs: Long,
    val text: String,
)
