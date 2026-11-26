package io.github.ta3hrj.elm.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.ta3hrj.elm.data.MemoryLog
import io.github.ta3hrj.elm.player.LivePlayer
import io.github.ta3hrj.elm.player.PlaybackTimers
import io.github.ta3hrj.elm.player.Reconnect
import io.github.ta3hrj.elm.player.ServerCloseGuard
import io.github.ta3hrj.elm.player.isNetworkProblem
import io.github.ta3hrj.elm.player.dropLine
import io.github.ta3hrj.elm.player.bufferFullLine
import io.github.ta3hrj.elm.player.dropReason
import io.github.ta3hrj.elm.player.PlayerHandoff
import io.github.ta3hrj.elm.player.PlayerReturn
import io.github.ta3hrj.elm.player.ReturnTo
import io.github.ta3hrj.elm.player.SleepHold
import androidx.compose.runtime.SideEffect
import androidx.media3.common.C
import io.github.ta3hrj.elm.player.buildLivePlayer
import io.github.ta3hrj.elm.player.liveMediaSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.player.StreamMeter
import io.github.ta3hrj.elm.player.audioTechLabel
import io.github.ta3hrj.elm.player.videoTechLabel
import io.github.ta3hrj.elm.player.StreamQuality
import io.github.ta3hrj.elm.player.videoLabel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.ChannelEntity
import kotlinx.coroutines.delay

/** What the preview says over its picture: reconnecting, or the inactivity question and its outcome. */
sealed interface PreviewNote {
    data object Reconnecting : PreviewNote
    data class Asking(val secondsLeft: Int) : PreviewNote
    data object IdleStopped : PreviewNote
    /** The server keeps closing the stream: probably another device on the same account. */
    data object ServerCloses : PreviewNote
}

/**
 * Whether the preview keeps the TV awake: while it plays or waits to play (buffering, a reconnect on its
 * way), like the full-screen player; never after it was stopped for inactivity, so the screensaver and
 * standby can take over.
 */
internal fun previewKeepsScreenOn(playWhenReady: Boolean, state: Int, retryPending: Boolean, idleStopped: Boolean): Boolean =
    !idleStopped && (retryPending || (playWhenReady && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)))

/**
 * The live preview's player and what goes with it. It builds the player the same way the full-screen
 * player does, so a preview that is already playing a channel can be handed over to the full-screen player
 * as it is ([handOff]): one connection, no gap, no black screen. After a hand-over the player belongs to
 * the full-screen player and the preview builds a new one when it is needed again.
 *
 * People also simply watch in the preview, so it behaves like the full-screen player: it keeps the TV
 * awake while it plays, reconnects a dropped stream ([Reconnect]) and, after the hours set for "Are you
 * still there?" without a key press, asks and then stops.
 */
@OptIn(UnstableApi::class)
@Stable
class PreviewSession internal constructor(private val context: Context, private val viewModel: MainViewModel) {
    internal var live by mutableStateOf<LivePlayer?>(null)
        private set

    /** Shown over the picture; null when there is nothing to say. */
    var note by mutableStateOf<PreviewNote?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    internal val timers = PlaybackTimers()

    /** Reconnects in a row without the stream playing in between. */
    private var failures = 0
    /** Stops reconnecting a stream the server keeps closing (see [ServerCloseGuard]). */
    private val closeGuard = ServerCloseGuard()
    /** When the stream was last (re)connected. */
    private var connectedAt = 0L
    private var retryPending = false
    private val retry = Runnable {
        retryPending = false
        val current = channel
        // Only the channel still on screen is tried again; a newly focused one starts on its own.
        if (current != null && target?.id == current.id && live != null) {
            MemoryLog.countRetry()
            startStream(current)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                failures = 0
                if (note == PreviewNote.Reconnecting) note = null
            }
            // A live stream does not end: the server closed it.
            if (state == Player.STATE_ENDED) {
                if (closeGuard.onServerClosed(SystemClock.elapsedRealtime() - connectedAt)) {
                    scheduleRetry(networkProblem = true, error = null)
                } else {
                    // Another device on the same account most likely: reconnecting again would only throw it off.
                    MemoryLog.event(context, dropLine("preview", channel?.name, ServerCloseGuard.REASON, 0L, failures + 1, null))
                    handler.removeCallbacks(retry)
                    retryPending = false
                    note = PreviewNote.ServerCloses
                }
            }
            updateScreenOn()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateScreenOn()

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                MemoryLog.event(context, "fell behind the live window (preview${channel?.name?.let { ", $it" }.orEmpty()}): back to live")
                player?.seekToDefaultPosition()
                player?.prepare()
                return
            }
            scheduleRetry(isNetworkProblem(error), error)
        }
    }

    private val idleStopped: Boolean get() = timers.idle == PlaybackTimers.Idle.STOPPED

    /** [error] null: the stream ended (the server closed it). */
    private fun scheduleRetry(networkProblem: Boolean, error: PlaybackException?) {
        if (idleStopped || live == null) return
        val wait = Reconnect.delayMs(failures, networkProblem)
        MemoryLog.event(context, dropLine("preview", channel?.name, dropReason(error), player?.totalBufferedDuration ?: 0L, failures + 1, wait))
        handler.removeCallbacks(retry)
        if (wait == null) {
            // A refused or broken stream: the panel says it could not be played.
            retryPending = false
            note = null
        } else {
            failures++
            retryPending = true
            note = PreviewNote.Reconnecting
            handler.postDelayed(retry, wait)
        }
        updateScreenOn()
    }

    private fun cancelRetry() {
        handler.removeCallbacks(retry)
        retryPending = false
        failures = 0
        if (note == PreviewNote.Reconnecting) note = null
    }

    /** Keeps the TV awake while the preview plays (see [previewKeepsScreenOn]). */
    internal fun updateScreenOn() {
        val exo = player
        view?.keepScreenOn = previewKeepsScreenOn(
            playWhenReady = exo?.playWhenReady == true,
            state = exo?.playbackState ?: Player.STATE_IDLE,
            retryPending = retryPending,
            idleStopped = idleStopped,
        )
    }

    /** Called once a second: the inactivity question, its countdown and the stop. */
    internal fun tick(idleHours: Int) {
        checkBuffer()
        when (val t = timers.tick(playing = player?.isPlaying == true || retryPending, idleHours = idleHours)) {
            is PlaybackTimers.Tick.IdleAsking -> note = PreviewNote.Asking(t.secondsLeft)
            PlaybackTimers.Tick.IdleStop -> {
                handler.removeCallbacks(retry)
                retryPending = false
                live?.exo?.stop()
                note = PreviewNote.IdleStopped
                updateScreenOn()
            }
            else -> Unit
        }
    }

    /** A buffer that reached its ceiling ([bufferCapBytes]) is freed by starting the channel again. */
    private fun checkBuffer() {
        val handle = live ?: return
        val current = channel ?: return
        if (retryPending || !handle.bufferFull) return
        MemoryLog.event(context, bufferFullLine("preview", current.name, handle.bufferedBytes, handle.exo.totalBufferedDuration))
        handle.exo.stop()
        startStream(current)
    }

    /** A key on the remote: someone is here. After a stop for inactivity, any key but Back starts it again. */
    internal fun onKey(isBack: Boolean) {
        when (timers.onKeyDown(isBack)) {
            PlaybackTimers.Key.IDLE_ANSWERED -> note = null
            PlaybackTimers.Key.RESUME -> {
                note = null
                restart++
            }
            else -> Unit
        }
    }

    /** Back on screen (from the full-screen player, or the TV woke up): the hours start again. */
    internal fun onReturn() {
        timers.onReturn()
        if (note is PreviewNote.Asking) note = null
    }

    /** False while stopped for inactivity: then only a key press starts the preview again. */
    internal val mayPlay: Boolean get() = !idleStopped

    /** The channel the player was last told to play. */
    internal var channel: ChannelEntity? = null

    /** The channel whose preview is on screen now (the focused one); null while no preview is shown. */
    internal var target: ChannelEntity? = null
    internal var view: PlayerView? = null

    /** Counts returns from the full-screen player, so the preview starts again for the same channel. */
    internal var restart by mutableIntStateOf(0)

    val player: ExoPlayer? get() = live?.exo

    internal fun ensure(): LivePlayer {
        val settings = context.container.settings
        live?.let { existing ->
            if (existing.audioProfile == settings.audioProfile.value) return existing
            // The sound setting changed since this player was built: start again with the new one.
            view?.player = null
            existing.exo.release()
            live = null
        }
        return buildLivePlayer(context, settings).also {
            it.exo.addListener(listener)
            live = it
        }
    }

    internal fun play(target: ChannelEntity) {
        if (!mayPlay) return
        // Chosen by the user (focused or focused again): the count of closes starts over.
        closeGuard.reset()
        if (note == PreviewNote.ServerCloses) note = null
        if (channel?.id != target.id) cancelRetry()
        // Already playing it (e.g. the player came back from full screen): do not connect again.
        live?.let { handle ->
            val current = handle.exo
            // (A player built with another sound profile is not kept: it is rebuilt below.)
            val sameSound = handle.audioProfile == context.container.settings.audioProfile.value
            if (sameSound && channel?.id == target.id && current.currentMediaItem != null && current.playbackState != Player.STATE_IDLE && current.playerError == null) {
                current.playWhenReady = true
                return
            }
        }
        startStream(target)
    }

    /** Connects to [target] (again). */
    private fun startStream(target: ChannelEntity) {
        val exo = ensure().exo
        connectedAt = SystemClock.elapsedRealtime()
        val playlist = viewModel.currentPlaylist.value
        exo.setMediaSource(liveMediaSource(context.container.http, context.container.repository.userAgentFor(playlist, target), target.streamUrl))
        exo.prepare()
        exo.playWhenReady = true
        channel = target
        updateScreenOn()
    }

    internal fun stop() {
        cancelRetry()
        live?.exo?.stop()
        updateScreenOn()
    }

    /**
     * Offers the player to the full-screen player if it is playing [target] (what the user just chose);
     * true when it did. The picture view is detached first so that the next screen can take the surface.
     */
    fun handOff(target: ChannelEntity): Boolean {
        // The full-screen player gives the channel back when it closes on this very channel.
        if (this.target?.id == target.id) ReturnTo.channelId = target.id
        val handed = live ?: return false
        val exo = handed.exo
        val playing = channel?.id == target.id && exo.currentMediaItem != null &&
            exo.playbackState != Player.STATE_IDLE && exo.playerError == null
        if (!playing) return false
        cancelRetry()
        exo.removeListener(listener)
        view?.player = null
        view?.keepScreenOn = false
        live = null
        channel = null
        PlayerHandoff.offer(target.id, handed)
        // If the full-screen player never starts, nobody takes it: release it after a while.
        Handler(Looper.getMainLooper()).postDelayed({ PlayerHandoff.expire() }, HANDOFF_EXPIRY_MS)
        return true
    }

    /**
     * The full-screen player was closed on [wanted] and gave its player back: carry on with it, the same
     * connection, instead of connecting to the channel again. False when there is nothing to take.
     */
    internal fun reclaim(wanted: ChannelEntity): Boolean {
        val back = PlayerReturn.take(wanted.id) ?: return false
        val exo = back.exo
        // A stopped preview player that is still here has nothing the returned one lacks.
        live?.let { old ->
            view?.player = null
            old.exo.release()
        }
        // Rewound or paused in full screen: the preview shows the live picture.
        if (exo.isCurrentMediaItemLive && exo.currentLiveOffset != C.TIME_UNSET && exo.currentLiveOffset > 10_000) exo.seekToDefaultPosition()
        exo.volume = 1f
        exo.playWhenReady = true
        cancelRetry()
        exo.addListener(listener)
        live = back
        channel = wanted
        // Attached at once, not with the next recomposition: the picture is already running.
        view?.player = exo
        updateScreenOn()
        return true
    }

    internal fun release() {
        handler.removeCallbacks(retry)
        retryPending = false
        view?.player = null
        live?.exo?.release()
        live = null
    }
}

/**
 * A preview session for the focused [channel]: it starts after the focus rests on a channel for a
 * moment and stops as soon as the screen is left without a hand-over, so it never holds a second
 * connection to the provider while something else plays.
 */
@Composable
fun rememberPreviewSession(viewModel: MainViewModel, channel: ChannelEntity?): PreviewSession {
    val context = LocalContext.current
    val session = remember { PreviewSession(context.applicationContext, viewModel) }
    DisposableEffect(Unit) { onDispose { session.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { session.stop() }
    // The focused channel, for the lifecycle callbacks below.
    SideEffect { session.target = channel }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        session.onReturn()
        val wanted = session.target
        if (wanted != null && session.reclaim(wanted)) return@LifecycleEventEffect
        // Stopped for inactivity: only a key press starts it again.
        if (!session.mayPlay) return@LifecycleEventEffect
        val exo = session.player
        if (exo == null) session.restart++ else if (exo.currentMediaItem != null) exo.prepare()
    }
    // "Are you still there?", as in the full-screen player: the clock, and the keys that answer it.
    LaunchedEffect(Unit) {
        val settings = context.container.settings
        while (true) {
            delay(1_000)
            session.tick(settings.idleHours.value)
        }
    }
    LaunchedEffect(Unit) { KeyPresses.flow.collect { session.onKey(it) } }
    LaunchedEffect(channel?.streamUrl, session.restart) {
        if (channel == null) {
            session.stop()
            return@LaunchedEffect
        }
        delay(PREVIEW_DELAY_MS)
        // The sleep timer ended the player on this channel: stay quiet until another channel is focused.
        if (channel.id == SleepHold.channelId) {
            session.stop()
            return@LaunchedEffect
        }
        session.play(channel)
    }
    return session
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewSurface(session: PreviewSession, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    // A player that arrives already playing (hand-over, hand-back) must not be hidden behind the
                    // black shutter until its next first frame: show what it renders at once.
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    session.view = this
                    session.updateScreenOn()
                }
            },
            // Follows the session's player: empty after a hand-over until the preview starts again.
            update = {
                it.player = session.player
                session.updateScreenOn()
            },
            onRelease = {
                it.keepScreenOn = false
                if (session.view === it) session.view = null
            },
            modifier = Modifier.fillMaxSize(),
        )
        session.note?.let { note ->
            Text(
                previewNoteText(note),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xB3000000)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun previewNoteText(note: PreviewNote): String = when (note) {
    PreviewNote.Reconnecting -> tx("Yayın kesildi, yeniden bağlanılıyor…", "Stream interrupted, reconnecting…")
    is PreviewNote.Asking -> tx(
        "Orada mısınız? ${note.secondsLeft} sn içinde bir tuşa basın",
        "Still there? Press a key within ${note.secondsLeft} s",
    )
    PreviewNote.ServerCloses -> tx(
        "Sağlayıcı yayını tekrar tekrar kapatıyor; aynı hesapla başka bir cihaz izliyor olabilir.",
        "The provider keeps closing the stream; another device may be watching on the same account.",
    )
    PreviewNote.IdleStopped -> tx("Önizleme durduruldu. Devam için bir tuşa basın.", "Preview stopped. Press a key to continue.")
}

/** Small live preview of the focused channel (TV guide start screen). */
@Composable
fun PreviewPlayer(session: PreviewSession) {
    PreviewSurface(session, Modifier.size(width = 186.dp, height = 104.dp))
}

/** What a stream looks like right now, read once a second from the preview player. */
private data class StreamReading(val video: String?, val quality: StreamQuality, val videoTech: String? = null, val audioTech: String? = null)

/**
 * Live preview with a stream information box beneath it: channel, programme, picture size and
 * frame rate, and how well the stream is coming in. Shown beside the channel list on wide screens.
 */
@OptIn(UnstableApi::class)
@Composable
fun PreviewPanel(session: PreviewSession, channel: ChannelEntity?, programmeTitle: String?, modifier: Modifier = Modifier) {
    val player = session.player
    val meter = remember { StreamMeter() }
    var reading by remember { mutableStateOf(StreamReading(null, StreamQuality.UNKNOWN)) }
    var failed by remember { mutableStateOf(false) }

    // Whether this channel has played yet: the first fill is not a stall, a later wait for data is.
    val wasReady = remember { booleanArrayOf(false) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING && wasReady[0]) meter.onStall()
                if (state == Player.STATE_READY) {
                    wasReady[0] = true
                    // Reconnected after an error: it plays again.
                    failed = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, channel?.streamUrl) {
        // A new channel starts a new reading (a live stream's own list updates do not).
        failed = false
        wasReady[0] = false
        meter.reset()
        while (true) {
            val format = player?.videoFormat
            val state = player?.playbackState ?: Player.STATE_IDLE
            val started = state == Player.STATE_READY || (state == Player.STATE_BUFFERING && format != null)
            val tech = session.live?.tech?.tech
            reading = StreamReading(
                video = format?.let { videoLabel(it.width, it.height, it.frameRate) },
                quality = meter.rate(started = started, failed = failed, bufferedMs = player?.totalBufferedDuration ?: 0L),
                videoTech = tech?.let { videoTechLabel(it) },
                audioTech = tech?.let { audioTechLabel(it) },
            )
            delay(1_000)
        }
    }

    Column(modifier.fillMaxWidth()) {
        PreviewSurface(session, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x66161B22)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Yayın bilgisi", "Stream info"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (channel != null && !failed) Text("● " + tx("CANLI", "LIVE"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF3DDC84))
            }
            InfoLine(tx("Kanal", "Channel"), channel?.name ?: "—")
            if (programmeTitle != null) InfoLine(tx("Program", "Programme"), programmeTitle)
            InfoLine(tx("Video", "Video"), reading.video ?: "—")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Bağlantı", "Connection"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA4B2), modifier = Modifier.weight(1f))
                QualityBars(reading.quality)
                Spacer(Modifier.width(6.dp))
                Text(qualityLabel(reading.quality), style = MaterialTheme.typography.labelSmall, color = qualityColor(reading.quality))
            }
            // Who decodes what: tells at a glance whether picture and sound use the device's hardware.
            reading.videoTech?.let { TechLine(tx("Görüntü", "Picture"), it) }
            reading.audioTech?.let { TechLine(tx("Ses", "Sound"), it) }
        }
    }
}

@Composable
private fun TechLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF9AA4B2),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA4B2), modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QualityBars(quality: StreamQuality) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (bar in 1..4) {
            Box(
                Modifier.width(4.dp).height((4 + bar * 3).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (bar <= quality.bars) qualityColor(quality) else Color(0x33FFFFFF)),
            )
        }
    }
}

private fun qualityColor(q: StreamQuality) = when (q) {
    StreamQuality.EXCELLENT, StreamQuality.GOOD -> Color(0xFF3DDC84)
    StreamQuality.FAIR -> Color(0xFFFFC857)
    StreamQuality.WEAK, StreamQuality.NONE -> Color(0xFFFF6B6B)
    StreamQuality.UNKNOWN -> Color(0xFF9AA4B2)
}

private fun qualityLabel(q: StreamQuality) = when (q) {
    StreamQuality.EXCELLENT -> tx("Mükemmel", "Excellent")
    StreamQuality.GOOD -> tx("İyi", "Good")
    StreamQuality.FAIR -> tx("Orta", "Fair")
    StreamQuality.WEAK -> tx("Zayıf", "Weak")
    StreamQuality.NONE -> tx("Yok", "None")
    StreamQuality.UNKNOWN -> "…"
}

private const val PREVIEW_DELAY_MS = 800L
private const val HANDOFF_EXPIRY_MS = 12_000L
