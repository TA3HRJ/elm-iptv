package io.github.ta3hrj.elm.core

/**
 * Picks the display mode for a video of [fps] frames per second: same resolution as the current mode,
 * refresh rate an exact multiple of the frame rate (25 fps -> 50 Hz, 23.976 fps -> 23.976 or 47.95 Hz).
 * Returns null when no mode fits better than the current one.
 */
object FrameRate {
    fun bestRefreshRate(fps: Float, current: Float, available: List<Float>): Float? {
    if (fps < 10f) return null
    fun error(rate: Float): Float {
        val multiple = kotlin.math.round(rate / fps).coerceAtLeast(1f)
        return kotlin.math.abs(rate - multiple * fps) / rate
    }
    // Prefer the lowest exact multiple; 0.2 % tolerance covers 23.976 vs 24 and 59.94 vs 60.
    val best = available.filter { error(it) < 0.002f }.minByOrNull { kotlin.math.round(it / fps) * 1000 + error(it) } ?: return null
    return if (error(current) < 0.002f && kotlin.math.round(current / fps) <= kotlin.math.round(best / fps)) null else best
}
}
