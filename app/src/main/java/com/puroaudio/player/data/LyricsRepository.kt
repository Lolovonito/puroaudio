package com.puroaudio.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cacheDir: File by lazy {
        File(context.filesDir, "lyrics").apply { mkdirs() }
    }

    suspend fun getLyrics(song: Song, forceRefresh: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, "${song.id}.lrc")
        if (!forceRefresh && cache.exists()) {
            return@withContext readCached(cache)
        }
        val fetched = download(song) ?: return@withContext null
        val text = fetched.synced ?: fetched.plain
        if (!text.isNullOrBlank()) {
            runCatching { cache.writeText(text) }
        }
        fetched
    }

    private fun readCached(file: File): Lyrics? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return if (hasTimestamps(text)) Lyrics(text, null) else Lyrics(null, text)
    }

    private fun download(song: Song): Lyrics? {
        val duration = (song.durationMs / 1000).toInt().coerceAtLeast(0)
        val url = buildString {
            append("https://lrclib.net/api/get?")
            append("artist_name=").append(encode(song.artist))
            append("&track_name=").append(encode(song.title))
            append("&album_name=").append(encode(song.album))
            append("&duration=").append(duration)
        }
        requestJson(url)?.let { obj ->
            if (obj.has("syncedLyrics") || obj.has("plainLyrics")) {
                val synced = obj.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
                val plain = obj.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
                if (synced != null || plain != null) return Lyrics(synced, plain)
            }
        }

        val searchUrl = "https://lrclib.net/api/search?q=" +
            encode("${song.artist} ${song.title}")
        val response = request(searchUrl) ?: return null
        val body = runCatching { response.body?.string() }.getOrNull() ?: return null
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val synced = obj.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
            val plain = obj.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
            if (synced != null || plain != null) return Lyrics(synced, plain)
        }
        return null
    }

    private fun requestJson(url: String): JSONObject? {
        val response = request(url) ?: return null
        val body = runCatching { response.body?.string() }.getOrNull() ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun request(url: String): okhttp3.Response? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PuroAudio/1.0 (Android)")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response else {
                response.close()
                null
            }
        }.getOrNull()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val timestampRegex = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""")

        fun hasTimestamps(text: String): Boolean = timestampRegex.containsMatchIn(text)

        fun parse(raw: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            var offset = 0L
            raw.lineSequence().forEach { original ->
                val trimmed = original.trim()
                if (trimmed.isEmpty()) return@forEach
                val offsetMatch = Regex("""\[offset:([+-]?\d+)]""").find(trimmed)
                if (offsetMatch != null) {
                    offset = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                    return@forEach
                }
                val matches = timestampRegex.findAll(trimmed).toList()
                if (matches.isEmpty()) return@forEach
                val text = trimmed.replace(timestampRegex, "").trim()
                matches.forEach { match ->
                    val stamp = match.value.removeSurrounding("[", "]")
                    val time = parseTimestamp(stamp)
                    if (time >= 0) lines += LyricLine((time + offset).coerceAtLeast(0), text)
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        private fun parseTimestamp(value: String): Long {
            return runCatching {
                val parts = value.replace(',', '.').replace(':', '.', ignoreCase = false)
                val split = parts.split('.')
                if (split.size >= 2) {
                    val minutes = split[0].toLong()
                    val seconds = split[1].toLong()
                    val millis = if (split.size >= 3) {
                        split[2].padEnd(3, '0').take(3).toLong()
                    } else {
                        0L
                    }
                    (minutes * 60_000) + (seconds * 1_000) + millis
                } else {
                    -1L
                }
            }.getOrDefault(-1L)
        }
    }
}
