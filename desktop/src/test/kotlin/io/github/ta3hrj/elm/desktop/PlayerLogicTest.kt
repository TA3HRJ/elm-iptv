package io.github.ta3hrj.elm.desktop

import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.EpgProgramme
import io.github.ta3hrj.elm.desktop.player.PlayerAction
import io.github.ta3hrj.elm.desktop.player.PlayerKeys
import io.github.ta3hrj.elm.desktop.player.ReconnectPolicy
import io.github.ta3hrj.elm.desktop.player.VlcOptions
import io.github.ta3hrj.elm.desktop.player.formatTime
import io.github.ta3hrj.elm.desktop.ui.GuideWindow
import io.github.ta3hrj.elm.desktop.ui.initials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class PlayerLogicTest {
    @Test
    fun `keys work like a remote and like common desktop players`() {
        assertEquals(PlayerAction.NEXT_CHANNEL, PlayerKeys.action(KeyEvent.VK_DOWN))
        assertEquals(PlayerAction.PREVIOUS_CHANNEL, PlayerKeys.action(KeyEvent.VK_PAGE_UP))
        assertEquals(PlayerAction.VOLUME_UP, PlayerKeys.action(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK))
        assertEquals(PlayerAction.TOGGLE_PAUSE, PlayerKeys.action(KeyEvent.VK_SPACE))
        assertEquals(PlayerAction.TOGGLE_FULLSCREEN, PlayerKeys.action(KeyEvent.VK_F11))
        assertEquals(PlayerAction.EXIT, PlayerKeys.action(KeyEvent.VK_ESCAPE))
        assertNull(PlayerKeys.action(KeyEvent.VK_Q))
    }

    @Test
    fun `a dropped stream is tried again, less and less often, then given up`() {
        val policy = ReconnectPolicy(maxAttempts = 7)
        val delays = generateSequence { policy.nextDelayMs() }.toList()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
        policy.onPlaying()
        assertEquals(1_000L, policy.nextDelayMs())
    }

    @Test
    fun `VLC gets the user agent, the cache and the start position`() {
        val options = VlcOptions.forMedia("UA/1", 3_000, live = false, startMs = 95_000, hardwareDecoding = false).toList()
        assertTrue(":http-user-agent=UA/1" in options)
        assertTrue(":network-caching=3000" in options)
        assertTrue(":start-time=95" in options)
        assertTrue(":avcodec-hw=none" in options)
        assertFalse(":http-reconnect" in options)
        assertTrue(":http-reconnect" in VlcOptions.forMedia("UA", 100, live = true))
        assertTrue(":network-caching=300" in VlcOptions.forMedia("UA", 100, live = true))
    }

    @Test
    fun `times and initials`() {
        assertEquals("2:05", formatTime(125_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("T1", initials("TRT 1"))
        assertEquals("SH", initials("Show HD"))
        assertEquals("?", initials("  "))
    }

    @Test
    fun `the guide time line starts on a half hour before now and clips programmes to the window`() {
        val now = 1_700_000_000_000L
        val w = GuideWindow.around(now)
        assertEquals(0L, w.start % (30 * 60_000L))
        assertTrue(w.start <= now - 2 * 60 * 60_000L + 30 * 60_000L && w.start > now - 3 * 60 * 60_000L)
        assertEquals(26 * 60, w.minutes)
        val early = EpgProgramme("k", w.start - 60 * 60_000L, w.start + 15 * 60_000L, "Early", null)
        assertEquals(0, w.offsetMinutes(early.start))
        assertEquals(15, w.widthMinutes(early))
    }

    @Test
    fun `search ignores case and Turkish accents`() {
        val list = listOf(Channel("ÇOCUK TV", "G", "u1"), Channel("İstanbul", "G", "u2"), Channel("News", "G", "u3"))
        assertEquals(listOf("ÇOCUK TV"), AppState.filter(list, "cocuk") { it.name }.map { it.name })
        assertEquals(listOf("İstanbul"), AppState.filter(list, "istanbul") { it.name }.map { it.name })
        assertEquals(3, AppState.filter(list, " ") { it.name }.size)
    }
}
