package io.github.ta3hrj.elm.player

import java.util.Locale

/** How well a live stream is coming in, from how much of it is already buffered and how often it stalled. */
enum class StreamQuality(val bars: Int) {
    /** Not started yet. */
    UNKNOWN(0),
    /** The stream failed. */
    NONE(0),
    WEAK(1),
    FAIR(2),
    GOOD(3),
    EXCELLENT(4),
}

/**
 * Rates a stream while it plays. The player reports each stall ([onStall]) and, once a second, how many
 * milliseconds are buffered ahead; the rating drops when stalls repeat within the last minute and
 * rises with a deeper buffer.
 */
class StreamMeter(private val now: () -> Long = System::currentTimeMillis) {
    private val stalls = ArrayDeque<Long>()

    /** Playback ran dry and had to wait for data. */
    fun onStall() {
        stalls += now()
    }

    fun reset() = stalls.clear()

    fun rate(started: Boolean, failed: Boolean, bufferedMs: Long): StreamQuality {
        if (failed) return StreamQuality.NONE
        if (!started) return StreamQuality.UNKNOWN
        val t = now()
        while (stalls.isNotEmpty() && t - stalls.first() > WINDOW_MS) stalls.removeFirst()
        val recent = stalls.size
        return when {
            recent >= 3 || bufferedMs < 500 -> StreamQuality.WEAK
            recent >= 1 || bufferedMs < 3_000 -> StreamQuality.FAIR
            bufferedMs < 8_000 -> StreamQuality.GOOD
            else -> StreamQuality.EXCELLENT
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}

/** "1920x1080 (1080p) · 25 fps"; parts that are not known are left out. */
fun videoLabel(width: Int, height: Int, frameRate: Float): String? {
    if (width <= 0 || height <= 0) return null
    val fps = frameRate.takeIf { it > 0f }?.let { f ->
        val whole = Math.round(f)
        // 23.976 stays 23.98, 25.0 becomes 25.
        if (kotlin.math.abs(f - whole) < 0.01f) "$whole fps" else String.format(Locale.ROOT, "%.2f fps", f)
    }
    return listOfNotNull("${width}x$height (${height}p)", fps).joinToString(" · ")
}
