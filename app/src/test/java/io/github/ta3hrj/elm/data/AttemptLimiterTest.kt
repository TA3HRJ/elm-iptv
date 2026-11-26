package io.github.ta3hrj.elm.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {
    private var clock = 0L
    private val limiter = AttemptLimiter(maxFailures = 3, lockMs = 60_000, now = { clock })

    @Test
    fun locksAfterTooManyWrongTriesAndOpensAgainLater() {
        repeat(2) { limiter.failed() }
        assertTrue(limiter.allowed())
        limiter.failed()
        assertFalse(limiter.allowed())
        clock += 59_999
        assertFalse(limiter.allowed())
        clock += 2
        assertTrue(limiter.allowed())
        // Counting starts from zero again.
        repeat(2) { limiter.failed() }
        assertTrue(limiter.allowed())
    }

    @Test
    fun aRightKeyClearsTheCount() {
        repeat(2) { limiter.failed() }
        limiter.succeeded()
        repeat(2) { limiter.failed() }
        assertTrue(limiter.allowed())
    }
}
