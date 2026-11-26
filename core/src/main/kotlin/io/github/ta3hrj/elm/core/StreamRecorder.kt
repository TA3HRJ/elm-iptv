package io.github.ta3hrj.elm.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Records a live stream to an output stream until a deadline.
 *
 * - MPEG-TS / progressive streams are copied byte for byte (reconnecting after drops).
 * - HLS playlists (`#EXTM3U`) are followed: the best variant is chosen and new segments are appended.
 *   Encrypted (`#EXT-X-KEY`) and fMP4 (`#EXT-X-MAP`) HLS are not supported.
 *
 * Blocking: run on a background thread.
 */
class StreamRecorder(
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    data class Result(val bytes: Long, val error: String?)

    fun record(
        url: String,
        userAgent: String,
        out: OutputStream,
        until: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long) -> Unit = {},
    ): Result {
        var written = 0L
        var failures = 0
        var lastError: String? = null
        while (clock() < until && !isCancelled()) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val input = BufferedInputStream(response.body!!.byteStream(), 64 * 1024)
                    input.mark(16)
                    val head = ByteArray(7)
                    val n = input.read(head)
                    input.reset()
                    if (n == 7 && String(head, Charsets.US_ASCII) == "#EXTM3U") {
                        val text = input.readBytes().toString(Charsets.UTF_8)
                        return recordHls(response.request.url.toString(), text, userAgent, out, until, isCancelled, onProgress, written)
                    }
                    val buffer = ByteArray(64 * 1024)
                    var thisConnection = 0L
                    while (clock() < until && !isCancelled()) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        thisConnection += read
                        failures = 0
                        onProgress(written)
                    }
                    // The server ended the stream: wait a moment before reconnecting; give up after
                    // several connections in a row that delivered nothing.
                    if (clock() < until && !isCancelled()) {
                        if (thisConnection == 0L && ++failures > MAX_FAILURES) return Result(written, tx("Yayın veri göndermiyor.", "The stream is not sending data."))
                        sleep(RECONNECT_DELAY_MS)
                    }
                }
            } catch (e: IOException) {
                lastError = e.message ?: e.javaClass.simpleName
                if (++failures > MAX_FAILURES) return Result(written, lastError)
                sleep(RETRY_DELAY_MS)
            } catch (e: UnsupportedStreamException) {
                return Result(written, e.message)
            }
        }
        return Result(written, null)
    }

    private fun recordHls(
        playlistUrl: String,
        firstText: String,
        userAgent: String,
        out: OutputStream,
        until: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long) -> Unit,
        alreadyWritten: Long,
    ): Result {
        var written = alreadyWritten
        var mediaUrl = playlistUrl
        var text = firstText
        if (text.contains("#EXT-X-STREAM-INF")) {
            mediaUrl = bestVariant(playlistUrl, text) ?: return Result(written, tx("HLS listesinde yayın bulunamadı.", "No stream found in the HLS playlist."))
            text = fetchText(mediaUrl, userAgent)
        }
        var lastSequence = -1L
        var failures = 0
        while (clock() < until && !isCancelled()) {
            try {
                if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) {
                    throw UnsupportedStreamException(tx("Şifreli HLS yayınları kaydedilemiyor.", "Encrypted HLS streams cannot be recorded."))
                }
                if (text.contains("#EXT-X-MAP")) throw UnsupportedStreamException(tx("Bu HLS biçimi (fMP4) kaydedilemiyor.", "This HLS format (fMP4) cannot be recorded."))
                val playlist = parseMedia(mediaUrl, text)
                for (segment in playlist.segments) {
                    if (segment.sequence <= lastSequence) continue
                    if (clock() >= until || isCancelled()) break
                    written += download(segment.url, userAgent, out)
                    lastSequence = segment.sequence
                    onProgress(written)
                }
                failures = 0
                if (playlist.ended) break
                sleep((playlist.targetDurationMs / 2).coerceIn(1_000, 6_000))
                text = fetchText(mediaUrl, userAgent)
            } catch (e: IOException) {
                if (++failures > MAX_FAILURES) return Result(written, e.message ?: e.javaClass.simpleName)
                sleep(RETRY_DELAY_MS)
                text = runCatching { fetchText(mediaUrl, userAgent) }.getOrElse { text }
            }
        }
        return Result(written, null)
    }

    private class UnsupportedStreamException(message: String) : Exception(message)

    private data class Segment(val sequence: Long, val url: String)
    private data class MediaPlaylist(val segments: List<Segment>, val targetDurationMs: Long, val ended: Boolean)

    private fun parseMedia(baseUrl: String, text: String): MediaPlaylist {
        var sequence = 0L
        var target = 6_000L
        var ended = false
        val segments = ArrayList<Segment>()
        var expectUri = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-TARGETDURATION:") -> target = (line.substringAfter(':').trim().toDoubleOrNull() ?: 6.0).times(1000).toLong()
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.startsWith("#EXTINF") -> expectUri = true
                line.isEmpty() || line.startsWith("#") -> Unit
                expectUri -> {
                    segments += Segment(sequence++, resolve(baseUrl, line))
                    expectUri = false
                }
            }
        }
        return MediaPlaylist(segments, target, ended)
    }

    private fun bestVariant(baseUrl: String, text: String): String? {
        var best: Pair<Long, String>? = null
        var bandwidth: Long? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") ->
                    bandwidth = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                line.isEmpty() || line.startsWith("#") -> Unit
                bandwidth != null -> {
                    if (best == null || bandwidth!! > best!!.first) best = bandwidth!! to resolve(baseUrl, line)
                    bandwidth = null
                }
            }
        }
        return best?.second
    }

    private fun resolve(baseUrl: String, reference: String): String =
        baseUrl.toHttpUrlOrNull()?.resolve(reference)?.toString() ?: reference

    private fun fetchText(url: String, userAgent: String): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body!!.string()
        }
    }

    private fun download(url: String, userAgent: String, out: OutputStream): Long {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body!!.byteStream().copyTo(out)
        }
    }

    companion object {
        private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")
        private const val MAX_FAILURES = 5
        private const val RETRY_DELAY_MS = 3_000L
        private const val RECONNECT_DELAY_MS = 1_000L
    }
}
