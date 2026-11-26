package io.github.ta3hrj.elm.ui

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.ElmApplication
import androidx.media3.common.C
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.AppSettings
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.player.PlayerHandoff
import io.github.ta3hrj.elm.player.PlayerReturn
import io.github.ta3hrj.elm.player.ReturnTo
import io.github.ta3hrj.elm.player.buildLivePlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Choosing the channel the preview is playing hands that very player to the full-screen player. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = ElmApplication::class)
class PreviewHandoffTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val viewModel = MainViewModel(context as android.app.Application)
    private val session = PreviewSession(context, viewModel)

    private fun channel(id: Long) = ChannelEntity(
        id = id, playlistId = 1, position = id.toInt(), name = "Kanal $id", groupTitle = "G", logo = null,
        streamUrl = "http://127.0.0.1:1/live/$id.ts", epgId = null, userAgent = null, catchupDays = 0, number = id.toInt(),
    )

    @After
    fun clean() {
        session.release()
        PlayerHandoff.discard()
        PlayerReturn.discard()
        ReturnTo.channelId = 0
        context.container.settings.setAudioProfile(AppSettings.AUDIO_PROFILE_GENERAL)
    }

    @Test
    fun nothingPlayingMeansNothingToHandOver() {
        assertFalse(session.handOff(channel(1)))
        assertNull(PlayerHandoff.take(1))
    }

    @Test
    fun thePlayingChannelsPlayerIsOfferedAndTheSessionLetsGo() {
        session.play(channel(1))
        val player = session.live
        assertNotNull(player)
        assertTrue(session.handOff(channel(1)))
        // The preview no longer owns it ...
        assertNull(session.live)
        // ... the full-screen player gets exactly that instance, once.
        val taken = PlayerHandoff.take(1)
        assertSame(player, taken)
        assertNull(PlayerHandoff.take(1))
        taken!!.exo.release()
    }

    @Test
    fun anotherChannelIsNotHandedOverAndTheSessionKeepsItsPlayer() {
        session.play(channel(1))
        assertFalse(session.handOff(channel(2)))
        assertNotNull(session.live)
        assertEquals(1L, session.channel?.id)
        assertNull(PlayerHandoff.take(2))
    }

    @Test
    fun theSoundProfileSetsTheContentTypeAndRebuildsAnOldPreviewPlayer() {
        val settings = context.container.settings
        settings.setAudioProfile(AppSettings.AUDIO_PROFILE_GENERAL)
        session.play(channel(1))
        val general = session.live!!
        assertEquals(C.AUDIO_CONTENT_TYPE_UNKNOWN, general.exo.audioAttributes.contentType)

        settings.setAudioProfile(AppSettings.AUDIO_PROFILE_MOVIE)
        session.play(channel(1))
        val movie = session.live!!
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, movie.exo.audioAttributes.contentType)
        assertEquals(AppSettings.AUDIO_PROFILE_MOVIE, movie.audioProfile)
        // A different instance: the first one was released when the setting changed.
        assertFalse(general === movie)
    }

    @Test
    fun choosingTheChannelOnScreenTellsThePlayerToGiveItBackWhenItCloses() {
        session.target = channel(1)
        session.play(channel(1))
        session.handOff(channel(1))
        assertEquals(1L, ReturnTo.channelId)
        PlayerHandoff.take(1)!!.exo.release()
    }

    @Test
    fun noPreviewOnScreenMeansNothingIsWaitingForThePlayer() {
        session.target = null
        session.handOff(channel(1))
        assertEquals(0L, ReturnTo.channelId)
    }

    @Test
    fun thePlayerComesBackToThePreviewWhichCarriesOnWithIt() {
        // What the full-screen player does when it closes on channel 1 (paused while in transit).
        val returned = buildLivePlayer(context, context.container.settings)
        returned.exo.volume = 0f
        PlayerReturn.offer(1, returned)

        assertTrue(session.reclaim(channel(1)))
        assertSame(returned, session.live)
        assertEquals(1L, session.channel?.id)
        assertTrue(returned.exo.playWhenReady)
        assertEquals(1f, returned.exo.volume, 0f)
        // Nothing is left on offer, and the preview does not connect to the channel again.
        assertNull(PlayerReturn.take(1))
        session.play(channel(1))
        assertSame(returned, session.live)
    }

    @Test
    fun aPlayerReturnedForAnotherChannelIsNotUsed() {
        val returned = buildLivePlayer(context, context.container.settings)
        PlayerReturn.offer(2, returned)
        assertFalse(session.reclaim(channel(1)))
        assertNull(session.live)
    }

    @Test
    fun aReturnedPlayerReplacesAStoppedPreviewPlayer() {
        session.play(channel(1))
        val stopped = session.live!!
        val returned = buildLivePlayer(context, context.container.settings)
        PlayerReturn.offer(1, returned)
        assertTrue(session.reclaim(channel(1)))
        assertSame(returned, session.live)
        assertFalse(stopped === session.live)
    }
}
