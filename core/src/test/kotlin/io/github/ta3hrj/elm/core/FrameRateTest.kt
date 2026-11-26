package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateTest {
    private val modes = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)

    @Test
    fun picksExactMultiples() {
        assertEquals(25f, FrameRate.bestRefreshRate(25f, 60f, modes))
        assertEquals(23.976f, FrameRate.bestRefreshRate(23.976f, 60f, modes))
        assertEquals(24f, FrameRate.bestRefreshRate(24f, 60f, modes))
        assertEquals(29.97f, FrameRate.bestRefreshRate(29.97f, 60f, modes))
        assertEquals(50f, FrameRate.bestRefreshRate(50f, 60f, modes))
    }

    @Test
    fun usesDoubleRateWhenNoSingleRateExists() {
        assertEquals(50f, FrameRate.bestRefreshRate(25f, 60f, listOf(50f, 60f)))
    }

    @Test
    fun keepsAFittingModeAndIgnoresUnknownRates() {
        assertNull(FrameRate.bestRefreshRate(50f, 50f, modes))
        assertNull(FrameRate.bestRefreshRate(25f, 60f, listOf(60f)))
        assertNull(FrameRate.bestRefreshRate(-1f, 60f, modes))
    }
}
