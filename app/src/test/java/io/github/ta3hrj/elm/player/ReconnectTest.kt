package io.github.ta3hrj.elm.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectTest {
    @Test
    fun anyErrorGetsTheQuickTries() {
        for (failures in 0 until Reconnect.QUICK_TRIES) {
            assertEquals(Reconnect.QUICK_DELAY_MS, Reconnect.delayMs(failures, networkProblem = false))
            assertEquals(Reconnect.QUICK_DELAY_MS, Reconnect.delayMs(failures, networkProblem = true))
        }
    }

    @Test
    fun aRefusedStreamIsNotTriedForever() {
        assertNull(Reconnect.delayMs(Reconnect.QUICK_TRIES, networkProblem = false))
        assertNull(Reconnect.delayMs(100, networkProblem = false))
    }

    @Test
    fun connectionTroubleWaitsLongerAndLongerUpToAMinute() {
        val q = Reconnect.QUICK_TRIES
        assertEquals(10_000L, Reconnect.delayMs(q, networkProblem = true))
        assertEquals(20_000L, Reconnect.delayMs(q + 1, networkProblem = true))
        assertEquals(40_000L, Reconnect.delayMs(q + 2, networkProblem = true))
        assertEquals(Reconnect.MAX_DELAY_MS, Reconnect.delayMs(q + 3, networkProblem = true))
        // Never gives up and never overflows, however long the outage.
        assertEquals(Reconnect.MAX_DELAY_MS, Reconnect.delayMs(10_000, networkProblem = true))
    }
}
