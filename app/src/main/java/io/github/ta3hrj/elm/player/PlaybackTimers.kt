package io.github.ta3hrj.elm.player

/**
 * The player's two timers, apart from its views so they can be tested with a fake clock:
 *
 * - the sleep timer the user sets: a warning in its last minute, then the player closes;
 * - "Are you still there?": after [tick]'s `idleHours` without a key press while something plays, a
 *   question with a countdown; without an answer playback stops (the player stays, saying why).
 *
 * The player calls [tick] once a second and [onKeyDown] for every key press, and shows what they return.
 */
class PlaybackTimers(private val now: () -> Long = System::currentTimeMillis) {
    enum class Idle { WATCHING, ASKING, STOPPED }

    sealed interface Tick {
        data object None : Tick
        data class SleepWarning(val secondsLeft: Int) : Tick
        data object SleepEnd : Tick
        data class IdleAsking(val secondsLeft: Int) : Tick
        data object IdleStop : Tick
    }

    enum class Key {
        /** Not about the timers: the key does what it normally does. */
        PASS,
        SLEEP_CANCELLED,
        IDLE_ANSWERED,
        /** Playback was stopped for inactivity: start it again. */
        RESUME,
        /** Back while stopped for inactivity: leave the player. */
        EXIT,
    }

    /** When the sleep timer closes the player; 0 = off. */
    var sleepAt = 0L
        private set

    var idle = Idle.WATCHING
        private set

    private var lastKey = now()
    private var idleDeadline = 0L

    fun setSleep(minutes: Int) {
        sleepAt = if (minutes <= 0) 0L else now() + minutes * 60_000L
    }

    /** Whole minutes left on the sleep timer, at least 1; 0 when it is off. */
    fun sleepMinutesLeft(): Int = if (sleepAt == 0L) 0 else ((sleepAt - now() + 59_999) / 60_000).toInt().coerceAtLeast(1)

    private val sleepWarning: Boolean get() = sleepAt > 0 && sleepAt - now() <= SLEEP_WARNING_MS

    fun tick(playing: Boolean, idleHours: Int): Tick {
        val t = now()
        if (sleepAt > 0) {
            val left = sleepAt - t
            if (left <= 0) {
                sleepAt = 0L
                return Tick.SleepEnd
            }
            if (left <= SLEEP_WARNING_MS) return Tick.SleepWarning(seconds(left))
        }
        when (idle) {
            Idle.WATCHING -> if (idleHours > 0 && playing && sleepAt == 0L && t - lastKey > idleHours * 3_600_000L) {
                idle = Idle.ASKING
                idleDeadline = t + IDLE_WARNING_MS
                return Tick.IdleAsking(seconds(IDLE_WARNING_MS))
            }
            Idle.ASKING -> {
                val left = idleDeadline - t
                if (left <= 0) {
                    idle = Idle.STOPPED
                    return Tick.IdleStop
                }
                return Tick.IdleAsking(seconds(left))
            }
            Idle.STOPPED -> Unit
        }
        return Tick.None
    }

    /** Every key press counts as "someone is here"; while a timer asks, the press answers it. */
    fun onKeyDown(isBack: Boolean): Key {
        lastKey = now()
        when (idle) {
            Idle.STOPPED -> return if (isBack) Key.EXIT else Key.RESUME.also { idle = Idle.WATCHING }
            Idle.ASKING -> {
                idle = Idle.WATCHING
                return Key.IDLE_ANSWERED
            }
            Idle.WATCHING -> Unit
        }
        if (sleepWarning) {
            sleepAt = 0L
            return Key.SLEEP_CANCELLED
        }
        return Key.PASS
    }

    /**
     * The player comes back on screen (Home key, the TV woke up): the hours start again, and a question
     * that was showing no longer applies. A stop for inactivity stays until a key is pressed.
     */
    fun onReturn() {
        lastKey = now()
        if (idle == Idle.ASKING) idle = Idle.WATCHING
    }

    /** Rounded up: exactly two minutes left shows 120 s, not 121. */
    private fun seconds(ms: Long) = ((ms + 999) / 1000).toInt()

    companion object {
        const val SLEEP_WARNING_MS = 60_000L
        /** How long "Are you still there?" waits for a key before playback stops. */
        const val IDLE_WARNING_MS = 120_000L
    }
}
