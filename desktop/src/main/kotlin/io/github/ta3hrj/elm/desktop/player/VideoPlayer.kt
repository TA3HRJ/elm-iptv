package io.github.ta3hrj.elm.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.InputEvents
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities

/** A track VLC offers (audio language, subtitle). */
data class Track(val id: Int, val name: String)

/** What the player is doing, for the controls. Written on the Swing thread, read by Compose. */
data class PlayerStatus(
    val playing: Boolean = false,
    val buffering: Float = 0f,
    val timeMs: Long = 0,
    val lengthMs: Long = 0,
    val error: String? = null,
    val volume: Int = 80,
    val muted: Boolean = false,
)

/** VLC on this computer, found once. */
object Vlc {
    /** Null when no VLC (libvlc 3) is installed. */
    val factory: MediaPlayerFactory? by lazy {
        runCatching {
            if (!NativeDiscovery().discover()) null else MediaPlayerFactory("--no-video-title-show", "--quiet")
        }.getOrNull()
    }

    val version: String? get() = runCatching { factory?.application()?.version() }.getOrNull()
}

/**
 * One VLC player inside a Swing component that Compose shows with SwingPanel. Mouse and keys on the video
 * reach the app (VLC's own handling is off), so the player is controlled the same way everywhere.
 */
class VideoPlayer(
    factory: MediaPlayerFactory,
    private val onAction: (PlayerAction) -> Unit,
    private val onFinished: () -> Unit,
    private val onFailed: () -> Unit,
) {
    var status by mutableStateOf(PlayerStatus())
        private set

    val component: EmbeddedMediaPlayerComponent = object : EmbeddedMediaPlayerComponent(factory, null, null, InputEvents.DISABLE_NATIVE, null) {
        override fun playing(mediaPlayer: MediaPlayer) = ui { status = status.copy(playing = true, error = null, buffering = 100f) }
        override fun paused(mediaPlayer: MediaPlayer) = ui { status = status.copy(playing = false) }
        override fun stopped(mediaPlayer: MediaPlayer) = ui { status = status.copy(playing = false) }
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui { status = status.copy(buffering = newCache) }
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = ui { status = status.copy(timeMs = newTime) }
        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = ui { status = status.copy(lengthMs = newLength) }
        override fun finished(mediaPlayer: MediaPlayer) = ui { onFinished() }
        override fun error(mediaPlayer: MediaPlayer) = ui {
            status = status.copy(playing = false, error = io.github.ta3hrj.elm.core.tx("Yayın açılamadı.", "The stream could not be opened."))
            onFailed()
        }

        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2) onAction(PlayerAction.TOGGLE_FULLSCREEN)
        }

        override fun mouseWheelMoved(e: MouseWheelEvent) {
            onAction(if (e.wheelRotation < 0) PlayerAction.VOLUME_UP else PlayerAction.VOLUME_DOWN)
        }

        override fun keyPressed(e: KeyEvent) {
            PlayerKeys.action(e.keyCode, e.modifiersEx)?.let(onAction)
        }
    }

    private val player get() = component.mediaPlayer()

    fun play(url: String, options: Array<String>) {
        status = status.copy(error = null, timeMs = 0, lengthMs = 0, buffering = 0f)
        player.media().play(url, *options)
        component.videoSurfaceComponent().requestFocusInWindow()
    }

    fun togglePause() {
        if (player.status().isPlaying) player.controls().setPause(true) else player.controls().play()
    }

    fun stop() = player.controls().stop()

    fun seekBy(deltaMs: Long) {
        if (player.status().isSeekable) player.controls().skipTime(deltaMs)
    }

    fun seekTo(ms: Long) {
        if (player.status().isSeekable) player.controls().setTime(ms.coerceAtLeast(0))
    }

    fun setVolume(volume: Int) {
        val v = volume.coerceIn(0, 150)
        player.audio().setVolume(v)
        status = status.copy(volume = v)
    }

    fun toggleMute() {
        val muted = !status.muted
        player.audio().setMute(muted)
        status = status.copy(muted = muted)
    }

    fun audioTracks(): List<Track> = player.audio().trackDescriptions().map { Track(it.id(), it.description()) }
    fun audioTrack(): Int = player.audio().track()
    fun setAudioTrack(id: Int) { player.audio().setTrack(id) }

    fun subtitleTracks(): List<Track> = player.subpictures().trackDescriptions().map { Track(it.id(), it.description()) }
    fun subtitleTrack(): Int = player.subpictures().track()
    fun setSubtitleTrack(id: Int) { player.subpictures().setTrack(id) }

    fun timeMs(): Long = player.status().time()

    fun release() {
        runCatching { player.controls().stop() }
        component.release()
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)
}
