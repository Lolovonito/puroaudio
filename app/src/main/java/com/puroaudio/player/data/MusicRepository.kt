package com.puroaudio.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun loadLibrary(): Library = withContext(Dispatchers.IO) {
        val songs = querySongs()
        val sortedSongs = songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val albums = songs
            .groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val ordered = albumSongs.sortedWith(
                    compareBy<Song> { it.trackNumber }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
                Album(
                    id = albumId,
                    title = ordered.first().album,
                    artist = ordered.first().artist,
                    year = ordered.first().year,
                    songs = ordered,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val artists = songs
            .groupBy { it.artist }
            .map { (name, artistSongs) ->
                Artist(
                    name = name,
                    songs = artistSongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                    albumCount = artistSongs.map { it.albumId }.distinct().size,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        Library(sortedSongs, albums, artists)
    }

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 3000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val albumId = cursor.getLong(albumIdIndex)
                val rawTrack = cursor.getInt(trackIndex)
                val artist = cursor.getString(artistIndex)
                    ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                    ?: "Artista desconocido"
                val album = cursor.getString(albumIndex)
                    ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                    ?: "Álbum desconocido"

                result += Song(
                    id = id,
                    title = cursor.getString(titleIndex) ?: "Sin título",
                    artist = artist,
                    album = album,
                    albumId = albumId,
                    durationMs = cursor.getLong(durationIndex),
                    trackNumber = if (rawTrack > 0) rawTrack % 1000 else 0,
                    year = cursor.getInt(yearIndex),
                    path = cursor.getString(dataIndex),
                    mimeType = cursor.getString(mimeIndex),
                    dateAdded = cursor.getLong(addedIndex),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    artworkUri = if (albumId > 0) ContentUris.withAppendedId(albumArtBase, albumId) else null,
                )
            }
        }
        return result
    }
}
