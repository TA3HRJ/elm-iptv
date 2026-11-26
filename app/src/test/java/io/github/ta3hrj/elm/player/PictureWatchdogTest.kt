package io.github.ta3hrj.elm.player

import io.github.ta3hrj.elm.player.PictureWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureWatchdogTest {
    /** Runs one check per second from [from] for [seconds] seconds and returns the actions that were not NONE. */
    private fun PictureWatchdog.run(from: Long, seconds: Int, wants: Boolean, frames: (Long) -> Long): List<Action> =
        (0 until seconds).map { s -> (from + s * 1_000L).let { t -> check(t, wants, frames(t)) } }.filter { it != Action.NONE }

    @Test
    fun aMovingPictureIsLeftAlone() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(emptyList<Action>(), w.run(0, 60, wants = true) { it / 40 })
    }

    @Test
    fun noPictureWhileTheSoundPlaysFirstReattachesThenReconnectsOnce() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(listOf(Action.REATTACH, Action.RECONNECT), w.run(0, 60, wants = true) { 500L })
    }

    @Test
    fun aStillPictureAfterTheFirstFrameIsNotStuck() {
        val w = PictureWatchdog().apply { start() }
        // One frame, then nothing more (a logo on a radio channel).
        assertEquals(emptyList<Action>(), w.run(0, 60, wants = true) { if (it < 2_000) 0L else 1L })
    }

    @Test
    fun waitingForTheNetworkIsNotCounted() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(emptyList<Action>(), w.run(0, 60, wants = false) { 0L })
    }

    @Test
    fun aReconnectDoesNotStartTheStepsOver() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(listOf(Action.REATTACH), w.run(0, 7, wants = true) { 10L })
        w.watch()
        // A new decoder counts from zero again, and still shows nothing.
        assertEquals(listOf(Action.RECONNECT), w.run(10_000, 7, wants = true) { 0L })
        w.watch()
        assertEquals(emptyList<Action>(), w.run(20_000, 60, wants = true) { 0L })
    }

    @Test
    fun theUsersNextChannelStartsTheStepsOver() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(listOf(Action.REATTACH, Action.RECONNECT), w.run(0, 30, wants = true) { 5L })
        w.start()
        assertEquals(listOf(Action.REATTACH), w.run(40_000, 7, wants = true) { 5L })
    }

    @Test
    fun aPictureThatComesBackEndsTheWatch() {
        val w = PictureWatchdog().apply { start() }
        assertEquals(listOf(Action.REATTACH), w.run(0, 7, wants = true) { 3L })
        assertEquals(emptyList<Action>(), w.run(7_000, 60, wants = true) { 3L + (it - 7_000) / 40 })
    }
}
