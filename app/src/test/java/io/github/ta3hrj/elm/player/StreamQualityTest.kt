package io.github.ta3hrj.elm.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamQualityTest {
    private var clock = 1_000_000L
    private val meter = StreamMeter { clock }

    @Test
    fun notStartedAndFailedStreams() {
        assertEquals(StreamQuality.UNKNOWN, meter.rate(started = false, failed = false, bufferedMs = 0))
        assertEquals(StreamQuality.NONE, meter.rate(started = true, failed = true, bufferedMs = 20_000))
    }

    @Test
    fun deeperBufferRatesHigher() {
        assertEquals(StreamQuality.WEAK, meter.rate(true, false, 200))
        assertEquals(StreamQuality.FAIR, meter.rate(true, false, 2_000))
        assertEquals(StreamQuality.GOOD, meter.rate(true, false, 5_000))
        assertEquals(StreamQuality.EXCELLENT, meter.rate(true, false, 30_000))
    }

    @Test
    fun repeatedStallsPullTheRatingDownUntilTheyAgeOut() {
        meter.onStall()
        assertEquals(StreamQuality.FAIR, meter.rate(true, false, 30_000))
        meter.onStall()
        meter.onStall()
        assertEquals(StreamQuality.WEAK, meter.rate(true, false, 30_000))
        clock += 61_000
        assertEquals(StreamQuality.EXCELLENT, meter.rate(true, false, 30_000))
    }

    @Test
    fun barsMatchTheRating() {
        assertEquals(listOf(0, 0, 1, 2, 3, 4), StreamQuality.entries.map { it.bars })
    }

    @Test
    fun videoLabels() {
        assertEquals("1920x1080 (1080p) · 25 fps", videoLabel(1920, 1080, 25f))
        assertEquals("1280x720 (720p) · 23.98 fps", videoLabel(1280, 720, 23.976f))
        assertEquals("960x540 (540p)", videoLabel(960, 540, -1f))
        assertEquals(null, videoLabel(0, 0, 25f))
    }
}
