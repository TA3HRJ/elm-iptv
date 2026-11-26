package io.github.ta3hrj.elm.ui

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewScreenOnTest {
    @Test
    fun keepsTheTvAwakeWhilePlayingOrBuffering() {
        assertTrue(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_READY, retryPending = false, idleStopped = false))
        assertTrue(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_BUFFERING, retryPending = false, idleStopped = false))
    }

    @Test
    fun staysAwakeWhileAReconnectIsOnItsWay() {
        assertTrue(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_IDLE, retryPending = true, idleStopped = false))
    }

    @Test
    fun letsTheScreensaverInWhenNothingPlays() {
        assertFalse(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_IDLE, retryPending = false, idleStopped = false))
        assertFalse(previewKeepsScreenOn(playWhenReady = false, state = Player.STATE_READY, retryPending = false, idleStopped = false))
        assertFalse(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_ENDED, retryPending = false, idleStopped = false))
    }

    @Test
    fun neverAfterAStopForInactivity() {
        assertFalse(previewKeepsScreenOn(playWhenReady = true, state = Player.STATE_READY, retryPending = true, idleStopped = true))
    }
}
