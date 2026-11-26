package io.github.ta3hrj.elm.player

import android.app.Activity
import android.content.Context
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Touch for the players on phones and tablets. The players were built for the remote, so a gesture is turned
 * into the key it stands for and goes the same way a key press goes (sleep timer, "Are you still there?",
 * channel list): nothing is handled twice and the remote keeps working as before.
 *
 * - tap: OK (what is on; again: the channel list)
 * - swipe up / down: next / previous channel (Down / Up on the remote)
 * - swipe right / left: forward / back in the live buffer (Right / Left)
 * - long press or double tap: the options (Menu)
 */
internal object TouchKeys {
    /** The key a swipe stands for, or null for a movement too short or too slow to be a swipe. */
    fun swipeKey(dx: Float, dy: Float, velocityX: Float, velocityY: Float, minDistance: Float, minVelocity: Float): Int? {
        val vertical = abs(dy) > abs(dx)
        val distance = if (vertical) abs(dy) else abs(dx)
        val velocity = if (vertical) abs(velocityY) else abs(velocityX)
        if (distance < minDistance || velocity < minVelocity) return null
        return when {
            // The finger moves up: the list moves on to the next channel, as Down does on the remote.
            vertical && dy < 0 -> KeyEvent.KEYCODE_DPAD_DOWN
            vertical -> KeyEvent.KEYCODE_DPAD_UP
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> KeyEvent.KEYCODE_DPAD_LEFT
        }
    }

    /** Presses and releases [keyCode] through the activity, exactly as the remote would. */
    fun press(activity: Activity, keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /**
     * A detector that presses keys on [activity]. With [swipesAndTaps] false (films, which have the on-screen
     * controls) only the long press is taken, for the options.
     */
    fun detector(context: Context, activity: Activity, swipesAndTaps: () -> Boolean): GestureDetector {
        val density = context.resources.displayMetrics.density
        return GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!swipesAndTaps()) return false
                    press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!swipesAndTaps()) return false
                    press(activity, KeyEvent.KEYCODE_MENU)
                    return true
                }

                override fun onLongPress(e: MotionEvent) = press(activity, KeyEvent.KEYCODE_MENU)

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (!swipesAndTaps() || e1 == null) return false
                    val key = swipeKey(e2.x - e1.x, e2.y - e1.y, velocityX, velocityY, 48 * density, 300 * density) ?: return false
                    press(activity, key)
                    return true
                }
            },
        )
    }
}
