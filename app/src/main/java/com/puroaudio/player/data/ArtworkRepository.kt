package com.puroaudio.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ArtworkRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cacheDir: File by lazy {
        File(context.filesDir, "artwork").apply { mkdirs() }
    }

    fun cachedArtwork(song: Song): File? {
        val file = File(cacheDir, "${cacheKey(song)}.jpg")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    suspend fun fetchArtwork(song: Song, forceRefresh: Boolean = false): File? = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedArtwork(song)?.let { return@withContext it }

        val term = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        val searchUrl = "https://itunes.apple.com/search?term=$term&entity=song&limit=8&country=ES"
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "PuroAudio/1.0 (Android)")
            .build()

        val json = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string() ?: return@withContext null)
            }
        }.getOrNull() ?: return@withContext null

        val results = json.optJSONArray("results") ?: return@withContext null
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val url = item.optString("artworkUrl100").takeUnless { it.isBlank() } ?: continue
            val highRes = url
                .replace("100x100bb", "1200x1200bb")
                .replace("100x100", "1200x1200")
            val bytes = download(highRes) ?: download(url) ?: continue
            val target = File(cacheDir, "${cacheKey(song)}.jpg")
            val temp = File(cacheDir, "${cacheKey(song)}.tmp")
            return@withContext runCatching {
                temp.writeBytes(bytes)
                if (target.exists()) target.delete()
                temp.renameTo(target)
                target
            }.getOrNull()
        }
        null
    }

    private fun download(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PuroAudio/1.0 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun cacheKey(song: Song): String =
        if (song.albumId > 0) "album_${song.albumId}" else "song_${song.id}"
}
