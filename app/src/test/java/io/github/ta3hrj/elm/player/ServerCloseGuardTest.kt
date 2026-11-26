package io.github.ta3hrj.elm.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two devices on a one-connection account throw each other off; the player stops after a few short plays. */
class ServerCloseGuardTest {
    @Test
    fun `stops after four short plays in a row`() {
        val guard = ServerCloseGuard()
        repeat(ServerCloseGuard.LIMIT - 1) { assertTrue(guard.onServerClosed(35_000)) }
        assertFalse(guard.onServerClosed(35_000))
    }

    @Test
    fun `a long play in between starts the count again`() {
        val guard = ServerCloseGuard()
        repeat(ServerCloseGuard.LIMIT - 1) { assertTrue(guard.onServerClosed(30_000)) }
        assertTrue(guard.onServerClosed(10 * 60_000))
        repeat(ServerCloseGuard.LIMIT - 2) { assertTrue(guard.onServerClosed(30_000)) }
        assertFalse(guard.onServerClosed(30_000))
    }

    @Test
    fun `choosing the channel again starts the count again`() {
        val guard = ServerCloseGuard()
        repeat(ServerCloseGuard.LIMIT) { guard.onServerClosed(30_000) }
        guard.reset()
        assertTrue(guard.onServerClosed(30_000))
    }
}
