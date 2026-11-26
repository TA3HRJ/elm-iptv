package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Measures how quickly the provider's server answers and how fast a channel downloads, to tell
 * whether stalls come from the connection and which buffer size suits it.
 */
class ConnectionTest(http: OkHttpClient) {
    private val client = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Result(val latencyMs: Long?, val mbps: Double?, val error: String?)

    suspend fun run(serverUrl: String, streamUrl: String?, userAgent: String): Result = withContext(Dispatchers.IO) {
        val latency = runCatching { latency(serverUrl, userAgent) }.getOrNull()
        if (streamUrl == null) return@withContext Result(latency, null, tx("Ölçülecek kanal bulunamadı.", "No channel to measure."))
        val speed = runCatching { speed(streamUrl, userAgent) }
        Result(latency, speed.getOrNull(), speed.exceptionOrNull()?.let { tx("Hız ölçülemedi: ", "Speed test failed: ") + (it.message ?: it.javaClass.simpleName) })
    }

    /** Time until the server's first response headers, best of three. */
    private fun latency(url: String, userAgent: String): Long {
        val root = url.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build() ?: error("bad url")
        return (1..3).minOf {
            val started = System.nanoTime()
            client.newCall(Request.Builder().url(root).header("User-Agent", userAgent).build()).execute().close()
            (System.nanoTime() - started) / 1_000_000
        }
    }

    /** Downloads a channel for a few seconds; for HLS, the first media segment(s). Returns Mbit/s. */
    private fun speed(streamUrl: String, userAgent: String): Double {
        var target = streamUrl
        // Follow HLS playlists (master -> media) to an actual video segment.
        repeat(3) {
            if (!target.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".m3u") }) return@repeat
            val text = client.newCall(Request.Builder().url(target).header("User-Agent", userAgent).build()).execute().use { it.body?.string().orEmpty() }
            val next = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: error("empty playlist")
            target = target.toHttpUrlOrNull()?.resolve(next)?.toString() ?: next
        }
        val request = Request.Builder().url(target).header("User-Agent", userAgent).build()
        val started = System.nanoTime()
        var bytes = 0L
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val source = response.body?.byteStream() ?: error("empty response")
            val buffer = ByteArray(64 * 1024)
            while (System.nanoTime() - started < MEASURE_NS && bytes < MAX_BYTES) {
                val read = source.read(buffer)
                if (read < 0) break
                bytes += read
            }
        }
        val seconds = (System.nanoTime() - started) / 1e9
        return bytes * 8 / seconds / 1_000_000
    }

    companion object {
        private const val MEASURE_NS = 6_000_000_000L
        private const val MAX_BYTES = 40L * 1024 * 1024

        /** Advice for a measured download speed (Mbit/s). */
        fun advice(mbps: Double): String = when {
            mbps < 4 -> tx(
                "Bağlantı HD için zayıf; takılma olabilir. \"Büyük\" tampon önerilir.",
                "The connection is weak for HD and may stall. A \"Large\" buffer is recommended.",
            )
            mbps < 10 -> tx(
                "HD için yeterli, Full HD'de zaman zaman takılabilir. \"Büyük\" tampon yardımcı olur.",
                "Enough for HD; Full HD may stall now and then. A \"Large\" buffer helps.",
            )
            mbps < 25 -> tx("Full HD için iyi.", "Good for Full HD.")
            else -> tx("4K dahil her yayın için yeterli.", "Enough for every stream, 4K included.")
        }
    }
}
