package io.github.ta3hrj.elm.desktop.player

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** What a key does in the player. The same keys work on the video and on the rest of the window. */
enum class PlayerAction {
    TOGGLE_PAUSE, TOGGLE_FULLSCREEN, EXIT, NEXT_CHANNEL, PREVIOUS_CHANNEL, SEEK_BACK, SEEK_FORWARD,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_LIST, LAST_CHANNEL,
}

object PlayerKeys {
    /**
     * Keys as on a TV remote and in common desktop players: Up/Down and Page Up/Down change the channel,
     * Left/Right move 10 s, Space pauses, F or F11 is full screen, Esc leaves it (or the player), Ctrl+Up/Down
     * and +/− change the volume, M mutes, L shows the channel list, Backspace goes back to the previous channel.
     */
    fun action(keyCode: Int, modifiers: Int = 0): PlayerAction? {
        val ctrl = modifiers and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK) != 0
        return when (keyCode) {
            KeyEvent.VK_SPACE, KeyEvent.VK_K, KeyEvent.VK_PAUSE -> PlayerAction.TOGGLE_PAUSE
            KeyEvent.VK_F, KeyEvent.VK_F11 -> PlayerAction.TOGGLE_FULLSCREEN
            KeyEvent.VK_ESCAPE -> PlayerAction.EXIT
            KeyEvent.VK_UP -> if (ctrl) PlayerAction.VOLUME_UP else PlayerAction.PREVIOUS_CHANNEL
            KeyEvent.VK_DOWN -> if (ctrl) PlayerAction.VOLUME_DOWN else PlayerAction.NEXT_CHANNEL
            KeyEvent.VK_PAGE_UP -> PlayerAction.PREVIOUS_CHANNEL
            KeyEvent.VK_PAGE_DOWN -> PlayerAction.NEXT_CHANNEL
            KeyEvent.VK_LEFT -> PlayerAction.SEEK_BACK
            KeyEvent.VK_RIGHT -> PlayerAction.SEEK_FORWARD
            KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> PlayerAction.VOLUME_UP
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> PlayerAction.VOLUME_DOWN
            KeyEvent.VK_M -> PlayerAction.MUTE
            KeyEvent.VK_L, KeyEvent.VK_C -> PlayerAction.CHANNEL_LIST
            KeyEvent.VK_BACK_SPACE -> PlayerAction.LAST_CHANNEL
            else -> null
        }
    }
}

/**
 * When to try a dropped live stream again: soon at first, then less often, and not for ever. A stream that
 * played for a while starts counting from the beginning again.
 */
class ReconnectPolicy(private val maxAttempts: Int = 8) {
    var attempts = 0
        private set

    /** Delay before the next attempt in milliseconds, or null when it is time to give up. */
    fun nextDelayMs(): Long? {
        if (attempts >= maxAttempts) return null
        val delay = (1_000L shl attempts.coerceAtMost(5)).coerceAtMost(30_000L)
        attempts++
        return delay
    }

    fun onPlaying() {
        attempts = 0
    }
}

/** The options VLC gets with each stream. */
object VlcOptions {
    fun forMedia(userAgent: String, networkCacheMs: Int, live: Boolean, startMs: Long = 0, hardwareDecoding: Boolean = true): Array<String> {
        val options = ArrayList<String>()
        options += ":http-user-agent=$userAgent"
        options += ":network-caching=${networkCacheMs.coerceIn(300, 60_000)}"
        // Live streams: reconnect inside VLC on short drops before the app has to start again.
        if (live) options += ":http-reconnect"
        if (startMs > 0) options += ":start-time=${startMs / 1000}"
        options += ":avcodec-hw=${if (hardwareDecoding) "any" else "none"}"
        return options.toTypedArray()
    }
}

/** "1:02:03" or "2:03". */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
