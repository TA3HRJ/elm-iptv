package io.github.ta3hrj.elm.data

/**
 * Stops guessing: after [maxFailures] wrong tries everything is refused for [lockMs] (the right key too),
 * then counting starts again. For the transfer page, whose key anyone on the home network may try.
 */
class AttemptLimiter(
    private val maxFailures: Int = 10,
    private val lockMs: Long = 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var failures = 0
    private var lockedUntil = 0L

    @Synchronized
    fun allowed(): Boolean {
        if (lockedUntil != 0L && now() >= lockedUntil) {
            lockedUntil = 0L
            failures = 0
        }
        return lockedUntil == 0L
    }

    @Synchronized
    fun failed() {
        if (lockedUntil != 0L) return
        failures++
        if (failures >= maxFailures) lockedUntil = now() + lockMs
    }

    @Synchronized
    fun succeeded() {
        failures = 0
    }
}
