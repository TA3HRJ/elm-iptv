package io.github.ta3hrj.elm.player

import io.github.ta3hrj.elm.player.PlaybackTimers.Idle
import io.github.ta3hrj.elm.player.PlaybackTimers.Key
import io.github.ta3hrj.elm.player.PlaybackTimers.Tick
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimersTest {
    private var clock = 1_000_000L
    private val timers = PlaybackTimers { clock }

    private fun pass(ms: Long) {
        clock += ms
    }

    private val hour = 3_600_000L
    private val minute = 60_000L

    @Test
    fun asksAfterTheHoursWithoutAKeyThenStopsWithoutAnAnswer() {
        pass(4 * hour - minute)
        assertEquals(Tick.None, timers.tick(playing = true, idleHours = 4))
        pass(2 * minute)
        assertEquals(Tick.IdleAsking(120), timers.tick(playing = true, idleHours = 4))
        assertEquals(Idle.ASKING, timers.idle)
        pass(60_000)
        assertEquals(Tick.IdleAsking(60), timers.tick(playing = true, idleHours = 4))
        pass(60_000)
        assertEquals(Tick.IdleStop, timers.tick(playing = true, idleHours = 4))
        assertEquals(Idle.STOPPED, timers.idle)
        // Stays stopped (the player shows why) until a key.
        pass(hour)
        assertEquals(Tick.None, timers.tick(playing = false, idleHours = 4))
        assertEquals(Key.RESUME, timers.onKeyDown(isBack = false))
        assertEquals(Idle.WATCHING, timers.idle)
    }

    @Test
    fun aKeyAnswersTheQuestionAndStartsTheHoursAgain() {
        pass(2 * hour + minute)
        assertEquals(Tick.IdleAsking(120), timers.tick(playing = true, idleHours = 2))
        assertEquals(Key.IDLE_ANSWERED, timers.onKeyDown(isBack = false))
        pass(2 * hour - minute)
        assertEquals(Tick.None, timers.tick(playing = true, idleHours = 2))
        // The next key is an ordinary key again.
        assertEquals(Key.PASS, timers.onKeyDown(isBack = false))
    }

    @Test
    fun backWhileStoppedLeavesThePlayer() {
        pass(2 * hour + minute)
        timers.tick(playing = true, idleHours = 2)
        pass(3 * minute)
        assertEquals(Tick.IdleStop, timers.tick(playing = true, idleHours = 2))
        assertEquals(Key.EXIT, timers.onKeyDown(isBack = true))
    }

    @Test
    fun neverAsksWhenOffOrNothingPlays() {
        pass(10 * hour)
        assertEquals(Tick.None, timers.tick(playing = true, idleHours = 0))
        assertEquals(Tick.None, timers.tick(playing = false, idleHours = 2))
    }

    @Test
    fun comingBackDropsAQuestionButKeepsAStop() {
        pass(2 * hour + minute)
        timers.tick(playing = true, idleHours = 2)
        timers.onReturn()
        assertEquals(Idle.WATCHING, timers.idle)
        pass(2 * hour + minute)
        timers.tick(playing = true, idleHours = 2)
        pass(3 * minute)
        timers.tick(playing = true, idleHours = 2)
        timers.onReturn()
        assertEquals(Idle.STOPPED, timers.idle)
    }

    @Test
    fun sleepTimerWarnsInItsLastMinuteAndAKeyCancelsIt() {
        timers.setSleep(30)
        assertEquals(30, timers.sleepMinutesLeft())
        pass(29 * minute - 1_000)
        assertEquals(Tick.None, timers.tick(playing = true, idleHours = 4))
        // Before the warning (61 s left) a key is an ordinary key.
        assertEquals(Key.PASS, timers.onKeyDown(isBack = false))
        pass(31_000)
        assertEquals(Tick.SleepWarning(30), timers.tick(playing = true, idleHours = 4))
        assertEquals(Key.SLEEP_CANCELLED, timers.onKeyDown(isBack = false))
        assertEquals(0L, timers.sleepAt)
        pass(minute)
        assertEquals(Tick.None, timers.tick(playing = true, idleHours = 4))
    }

    @Test
    fun sleepTimerClosesThePlayerAndTheQuestionWaitsForIt() {
        timers.setSleep(15)
        // Hours without a key: with a sleep timer running, the sleep timer decides.
        pass(15 * minute)
        assertEquals(Tick.SleepEnd, timers.tick(playing = true, idleHours = 0))
        assertEquals(0, timers.sleepMinutesLeft())
    }
}
