package com.puroaudio.player.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.puroaudio.player.data.TrackInfo

object TrackAnalyzer {

    fun analyze(context: Context, uri: Uri): TrackInfo {
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var codec = ""

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                codec = mime
                sampleRate = format.intOrZero(MediaFormat.KEY_SAMPLE_RATE)
                channels = format.intOrZero(MediaFormat.KEY_CHANNEL_COUNT)
                bits = when {
                    format.containsKey("bits-per-sample") -> format.intOrZero("bits-per-sample")
                    format.containsKey(MediaFormat.KEY_PCM_ENCODING) -> when (format.intOrZero(MediaFormat.KEY_PCM_ENCODING)) {
                        AudioFormat.ENCODING_PCM_8BIT -> 8
                        AudioFormat.ENCODING_PCM_16BIT -> 16
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                        AudioFormat.ENCODING_PCM_32BIT -> 32
                        AudioFormat.ENCODING_PCM_FLOAT -> 32
                        else -> 0
                    }
                    else -> 0
                }
                break
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        if (sampleRate <= 0 || bits <= 0) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                if (sampleRate <= 0) {
                    sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull() ?: 0
                }
                if (bits <= 0) {
                    bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull() ?: 0
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
        }

        if (sampleRate <= 0) sampleRate = 44100
        if (channels <= 0) channels = 2
        if (bits <= 0) bits = if (codec.contains("flac") || codec.contains("alac")) 24 else 16
        return TrackInfo(sampleRate, channels, bits, codec)
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}
