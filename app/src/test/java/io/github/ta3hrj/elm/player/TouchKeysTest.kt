package io.github.ta3hrj.elm.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Swipes on a phone or tablet stand for the remote keys the player already understands. */
class TouchKeysTest {
    private fun key(dx: Float, dy: Float, vx: Float = dx * 10, vy: Float = dy * 10) =
        TouchKeys.swipeKey(dx, dy, vx, vy, minDistance = 100f, minVelocity = 500f)

    @Test
    fun `up and down change the channel, sideways moves in the buffer`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, key(10f, -300f)) // finger up: next channel
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, key(-20f, 300f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, key(300f, 40f))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, key(-300f, -40f))
    }

    @Test
    fun `short or slow movements are not swipes`() {
        assertNull(key(0f, -60f))
        assertNull(key(0f, -300f, vy = -100f))
    }
}
