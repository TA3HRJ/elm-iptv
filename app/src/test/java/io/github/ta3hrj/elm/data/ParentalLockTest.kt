package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParentalLockTest {
    private var now = 1_000_000L
    private lateinit var lock: ParentalLock

    @Before
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("lock-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        lock = ParentalLock(prefs) { now }
        lock.setPin("4321")
        lock.lock()
    }

    @Test
    fun wrongPinsLeadToGrowingWaits() {
        repeat(ParentalLock.FREE_ATTEMPTS - 1) { assertFalse(lock.verify("0000")) }
        assertEquals(0L, lock.lockoutRemainingMs())
        assertFalse(lock.verify("0000"))
        assertEquals(30_000L, lock.lockoutRemainingMs())
        // While waiting, even the right PIN is refused.
        assertFalse(lock.verify("4321"))
        now += 30_000
        assertFalse(lock.verify("1111"))
        assertEquals(60_000L, lock.lockoutRemainingMs())
        now += 60_000
        assertTrue(lock.unlock("4321"))
        // A right PIN starts over.
        assertFalse(lock.verify("0000"))
        assertEquals(0L, lock.lockoutRemainingMs())
    }

    @Test
    fun waitNeverExceedsFifteenMinutes() {
        repeat(30) {
            lock.verify("0000")
            now += lock.lockoutRemainingMs()
        }
        lock.verify("0000")
        assertEquals(ParentalLock.MAX_WAIT_MS, lock.lockoutRemainingMs())
    }
}
