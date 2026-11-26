package io.github.ta3hrj.elm.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.PlaylistEntity
import io.github.ta3hrj.elm.data.VodRepository
import io.github.ta3hrj.elm.ui.timeRange
import io.github.ta3hrj.elm.core.Catchup
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.Job
import androidx.compose.material.icons.Icons
import io.github.ta3hrj.elm.data.AppSettings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import coil.load
import io.github.ta3hrj.elm.data.SubtitleStyles
import io.github.ta3hrj.elm.ui.AppTheme
import io.github.ta3hrj.elm.ui.DialogOption
import io.github.ta3hrj.elm.ui.OptionsDialog
import kotlinx.coroutines.launch

/**
 * Full-screen live TV player.
 *
 * Remote control: Up/Down or Channel +/- switch channels within the list the user opened,
 * OK opens the channel list, Info shows the channel info, Left/Right move within the live buffer,
 * Menu opens the options, Back closes the player.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val repository by lazy { container.repository }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var playerView: PlayerView
    private lateinit var infoPanel: LinearLayout
    private lateinit var infoNumber: TextView
    private lateinit var infoName: TextView
    private lateinit var infoGroup: TextView
    private lateinit var infoTech: TextView
    private var tech: TechCollector? = null
    /** The player with its extras, so it can be given back to the preview when the screen closes. */
    private var liveHandle: LivePlayer? = null
    private lateinit var infoNow: TextView
    private lateinit var infoProgress: ProgressBar
    private lateinit var infoNext: TextView
    private lateinit var infoLogo: android.widget.ImageView
    private lateinit var infoBadges: LinearLayout
    private var infoJob: Job? = null
    private lateinit var statusText: TextView
    private lateinit var idleCard: android.widget.LinearLayout
    private lateinit var idleTitle: TextView
    private lateinit var idleBody: TextView
    private lateinit var idleHint: TextView
    private lateinit var progress: ProgressBar

    private var player: ExoPlayer? = null
    private var playlist: PlaylistEntity? = null
    private var channels: List<ChannelEntity> = emptyList()
    private var index = 0
    private var retries = 0
    /** The channel on screen, and the one before it (for "previous channel"). */
    private var currentChannel: ChannelEntity? = null
    private var previousChannel: ChannelEntity? = null

    /** The options menu (and its sub-menus) on screen, drawn with Compose like the rest of the app. */
    private data class PlayerMenu(val title: String, val subtitle: String?, val options: List<DialogOption>)
    private var menu by mutableStateOf<PlayerMenu?>(null)

    private fun showMenu(title: String, options: List<DialogOption>, subtitle: String? = null) {
        menu = PlayerMenu(title, subtitle, options)
    }

    /** The list the channels came from, so the channel list opens on it. */
    private var currentQuery: ChannelQuery = ChannelQuery.Group("")

    // In-player channel list (see ChannelPanel).
    private var panelOpen by mutableStateOf(false)
    private var panelGroups by mutableStateOf<List<PanelGroup>>(emptyList())
    private var panelIndex by mutableIntStateOf(0)
    private var panelChannels by mutableStateOf<List<ChannelEntity>>(emptyList())
    private var panelNow by mutableStateOf<Map<String, io.github.ta3hrj.elm.data.ChannelProgramme>>(emptyMap())

    /** Set for films, episodes and archive programmes: seekable playback of a single item. */
    private var media: MediaRequest? = null
    private var resumeChecked = false
    /** Position to continue from when the activity comes back after onStop. */
    private var mediaPosition = -1L

    private val hideInfo = Runnable { infoPanel.visibility = View.GONE }
    private val retryPlayback = Runnable {
        retryPending = false
        retrying = true
        if (media != null) playMedia() else playCurrent(showInfo = false)
        retrying = false
    }
    /** True while a dropped stream is being reconnected (counted apart from the user's own plays). */
    private var retrying = false
    /** A reconnect is waiting; when the network comes back it is tried at once. */
    private var retryPending = false
    /** Lets the menu change the lip-sync delay while playing. */
    private var audioSink: DelayedAudioSink? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SleepHold.channelId = 0L
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildLayout()

        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST, -1)
        media = MediaRequest.from(intent)
        if (media != null) {
            playerView.useController = true
            playerView.controllerShowTimeoutMs = 4_000
            playerView.setShowNextButton(false)
            playerView.setShowPreviousButton(false)
            playerView.isFocusable = true
            playerView.requestFocus()
            lifecycleScope.launch {
                playlist = repository.playlist(playlistId)
                if (player != null) playMedia()
            }
            return
        }
        val startChannelId = savedInstanceState?.takeIf { it.containsKey(STATE_CHANNEL) }?.getLong(STATE_CHANNEL)
            ?: intent.getLongExtra(EXTRA_CHANNEL, -1)
        val query = readQuery(intent)
        currentQuery = query

        lifecycleScope.launch {
            playlist = repository.playlist(playlistId)
            channels = repository.channels(playlistId, query, container.profiles.contentUnlockedNow)
            if (channels.isEmpty()) {
                showStatus(tx("Kanal bulunamadı.", "Channel not found."))
                return@launch
            }
            index = channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)
            if (player != null) playCurrent(showInfo = true)
        }
    }

    override fun onStart() {
        super.onStart()
        watchNetwork()
        initializePlayer()
        // A question that was showing when the player left the screen no longer applies; a stop for
        // inactivity stays (e.g. the TV woke up): the note, not the stream.
        timers.onReturn()
        idleCard.visibility = View.GONE
        handler.post(timerTick)
        if (timers.idle == PlaybackTimers.Idle.STOPPED) {
            showIdleCard()
            return
        }
        if (media != null) {
            if (playlist != null) playMedia()
        } else if (channels.isNotEmpty()) {
            playCurrent(showInfo = true)
        }
    }

    override fun onStop() {
        super.onStop()
        unwatchNetwork()
        retryPending = false
        handler.removeCallbacksAndMessages(null)
        saveMediaProgress()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        channels.getOrNull(index)?.let { outState.putLong(STATE_CHANNEL, it.id) }
    }

    /** Key handling for seekable media; called by the root layout before the views see the key. */
    private fun handleMediaKey(event: KeyEvent): Boolean {
        if (media == null) return false
        val next = pendingNext
        if (next != null && event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            playNextEpisode(next)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            showOptions()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            // With the controls hidden, left/right skip directly; everything else just shows the controls.
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekBack(); playerView.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekForward(); playerView.showController(); return true }
            }
        }
        // Everything else reaches the focused player view (and its controls) through normal dispatch.
        return false
    }

    private var typedNumber = ""
    private val commitNumber = Runnable { jumpToNumber() }

    /** Digits typed on the remote select a channel by number after a short pause. */
    private fun onDigit(digit: Int) {
        if (typedNumber.length >= 5) return
        typedNumber += digit
        showStatus(typedNumber + "_")
        handler.removeCallbacks(commitNumber)
        handler.postDelayed(commitNumber, NUMBER_ENTRY_TIMEOUT_MS)
    }

    private fun jumpToNumber() {
        val number = typedNumber.toIntOrNull()
        typedNumber = ""
        statusText.visibility = View.GONE
        val current = channels.getOrNull(index) ?: return
        if (number == null) return
        if (number == 0) {
            // "0" on its own jumps back to the previous channel, as on most TV remotes.
            goToPreviousChannel()
            return
        }
        channels.indexOfFirst { it.number == number }.takeIf { it >= 0 }?.let {
            index = it
            retries = 0
            playCurrent(showInfo = true)
            return
        }
        lifecycleScope.launch {
            val target = repository.channelByNumber(current.playlistId, number)
            if (target == null) {
                showStatus(tx("$number numaralı kanal yok", "No channel $number"))
                handler.postDelayed({ statusText.visibility = View.GONE }, 2_000)
                return@launch
            }
            val unlocked = container.profiles.contentUnlockedNow
            if (!unlocked && repository.isGroupLocked(target.playlistId, FlagScopes.LIVE, target.groupTitle)) {
                showStatus(tx("$number numaralı kanal kilitli bir kategoride.\nKilidi ana ekrandan açabilirsiniz.", "Channel $number is in a locked category.\nUnlock it on the home screen."))
                handler.postDelayed({ statusText.visibility = View.GONE }, 3_000)
                return@launch
            }
            // Continue channel up/down within the target's category.
            currentQuery = ChannelQuery.Group(target.groupTitle)
            channels = repository.channels(target.playlistId, currentQuery, unlocked)
            index = channels.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
            retries = 0
            playCurrent(showInfo = true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // The open channel list handles its own keys; nothing may switch channels behind it. Back still closes it.
        if (panelOpen && keyCode != KeyEvent.KEYCODE_BACK) return true
        if (media == null && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onDigit(keyCode - KeyEvent.KEYCODE_0)
            return true
        }
        if (media == null && keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            onDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> switchChannel(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> switchChannel(+1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> when {
                livePausedAt > 0 -> togglePause()
                // First OK shows what is on (the remote may have no Info key); OK again opens the channel list.
                infoPanel.visibility != View.VISIBLE -> showInfo()
                else -> openChannelPanel()
            }
            KeyEvent.KEYCODE_INFO -> showInfo()
            KeyEvent.KEYCODE_MENU -> showOptions()
            KeyEvent.KEYCODE_LAST_CHANNEL -> goToPreviousChannel()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> liveSeek(-LIVE_SEEK_MS)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> liveSeek(+LIVE_SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> togglePause()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---- Live pause / rewind ---------------------------------------------------------------

    /** When live playback was paused (0 = not paused). */
    private var livePausedAt = 0L
    private val pauseTicker = object : Runnable {
        override fun run() {
            if (livePausedAt == 0L) return
            val seconds = (System.currentTimeMillis() - livePausedAt) / 1000
            showStatus(tx("⏸ Duraklatıldı · %d:%02d", "⏸ Paused · %d:%02d").format(seconds / 60, seconds % 60) +
                tx("\nOK veya Oynat: devam", "\nOK or Play: resume"))
            handler.postDelayed(this, 1_000)
        }
    }

    private fun togglePause() {
        val exo = player ?: return
        if (media != null) {
            exo.playWhenReady = !exo.playWhenReady
            return
        }
        if (livePausedAt == 0L) {
            exo.pause()
            livePausedAt = System.currentTimeMillis()
            handler.post(pauseTicker)
        } else {
            val pausedFor = System.currentTimeMillis() - livePausedAt
            livePausedAt = 0L
            handler.removeCallbacks(pauseTicker)
            statusText.visibility = View.GONE
            // A long pause outlives the buffer; channels with catch-up continue from the archive instead.
            val channel = channels.getOrNull(index)
            if (pausedFor > LIVE_BACK_BUFFER_MS && channel != null && resumeFromArchive(channel, System.currentTimeMillis() - pausedFor)) return
            exo.play()
        }
    }

    /** The channel's catch-up type, or null if it has no archive. */
    private fun catchupTypeOf(channel: ChannelEntity): String? {
        val playlist = playlist ?: return null
        return channel.catchupType ?: if (channel.catchupDays > 0 && playlist.type == io.github.ta3hrj.elm.data.PlaylistType.XTREAM) Catchup.TYPE_XTREAM else null
    }

    /** Plays the programme on air now from its beginning, through the channel's archive. */
    private fun startOver(channel: ChannelEntity) {
        val playlist = playlist ?: return
        val type = catchupTypeOf(channel) ?: return
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val current = container.guide.upcoming(channel.playlistId, channel.streamUrl, now, 2).firstOrNull { it.start <= now }
            val url = current?.let {
                Catchup.archiveUrl(channel.streamUrl, type, channel.catchupSource, it.start, it.stop, timezone = playlist.serverTimezone)
            }
            if (current == null || url == null) {
                showStatus(tx("Şu anki program bilinmiyor; baştan başlatılamıyor.", "The current programme is unknown, so it can't start over."))
                handler.removeCallbacks(hideStatus)
                handler.postDelayed(hideStatus, 3_000)
                return@launch
            }
            startMedia(this@PlayerActivity, playlist.id, url, current.title, tx("${channel.name} · Baştan", "${channel.name} · From the start"), progressKey = null)
        }
    }

    /** Continues a paused channel from its catch-up archive at [from]; false if the channel has none. */
    private fun resumeFromArchive(channel: ChannelEntity, from: Long): Boolean {
        val playlist = playlist ?: return false
        val type = channel.catchupType ?: if (channel.catchupDays > 0 && playlist.type == io.github.ta3hrj.elm.data.PlaylistType.XTREAM) Catchup.TYPE_XTREAM else null
        if (!Catchup.isAvailable(channel.catchupDays, type, from, System.currentTimeMillis())) return false
        val url = Catchup.archiveUrl(channel.streamUrl, type, channel.catchupSource, from, System.currentTimeMillis() + 3 * 3_600_000L,
            timezone = playlist.serverTimezone) ?: return false
        startMedia(this, playlist.id, url, channel.name, tx("Zaman kaydırmalı izleme", "Time-shifted"), progressKey = null)
        return true
    }

    /** Moves within the live buffer: back up to 2 minutes, forward until live. */
    private fun liveSeek(delta: Long) {
        val exo = player ?: return
        if (exo.isCurrentMediaItemLive && exo.isCurrentMediaItemSeekable) {
            val target = (exo.currentPosition + delta).coerceAtLeast(0)
            exo.seekTo(target)
        } else {
            // Progressive TS streams: the back buffer is all we have.
            val target = (exo.currentPosition + delta).coerceIn(maxOf(0, exo.currentPosition - LIVE_BACK_BUFFER_MS), exo.bufferedPosition)
            exo.seekTo(target)
        }
        val behind = (exo.bufferedPosition - exo.currentPosition).coerceAtLeast(0) / 1000
        showStatus(if (behind < 3) tx("● Canlı", "● Live") else tx("Canlıdan %d sn geride", "%d s behind live").format(behind))
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 2_000)
    }

    private val hideStatus = Runnable { if (livePausedAt == 0L && typedNumber.isEmpty()) statusText.visibility = View.GONE }

    // ---- Options menu: audio, subtitles, picture, recording ----------------------------------

    private fun showOptions() {
        val exo = player ?: return
        val items = mutableListOf<DialogOption>()
        if (media == null) previousChannel?.let { previous ->
            items += DialogOption(tx("Önceki kanal: ", "Previous channel: ") + previous.name, Icons.Filled.History) { goToPreviousChannel() }
        }
        val audio = tracksOf(exo, C.TRACK_TYPE_AUDIO)
        if (audio.size > 1) {
            items += DialogOption(tx("Ses: ", "Audio: ") + (audio.firstOrNull { it.selected }?.label ?: "—"), Icons.Filled.Audiotrack) { chooseTrack(C.TRACK_TYPE_AUDIO) }
        }
        val text = tracksOf(exo, C.TRACK_TYPE_TEXT)
        if (text.isNotEmpty()) {
            val current = text.firstOrNull { it.selected }?.label ?: tx("Kapalı", "Off")
            items += DialogOption(tx("Altyazı: ", "Subtitles: ") + current, Icons.Filled.Subtitles) { chooseTrack(C.TRACK_TYPE_TEXT) }
        }
        if (media != null) {
            items += DialogOption(tx("Hız: ", "Speed: ") + speedLabel(exo.playbackParameters.speed), Icons.Filled.Speed) { chooseSpeed() }
        }
        items += DialogOption(tx("Görüntü: ", "Picture: ") + resizeLabel(playerView.resizeMode), Icons.Filled.AspectRatio) { chooseResize() }
        items += DialogOption(tx("Ses gecikmesi: ", "Audio delay: ") + audioDelayLabel(container.settings.audioDelayMs.value), Icons.Filled.GraphicEq) { chooseAudioDelay() }
        items += DialogOption(tx("Uyku zamanlayıcısı: ", "Sleep timer: ") + sleepLabel(), Icons.Filled.Bedtime) { chooseSleep() }
        if (media == null) channels.getOrNull(index)?.let { channel ->
            items += DialogOption(tx("Kanal bilgisi", "Channel info"), Icons.Filled.Info) { showInfo() }
            if (catchupTypeOf(channel) != null) {
                items += DialogOption(tx("Baştan başlat", "Start over"), Icons.Filled.Replay) { startOver(channel) }
            }
            items += DialogOption(tx("Kaydet…", "Record…"), Icons.Filled.FiberManualRecord) { chooseRecording(channel) }
            items += DialogOption(tx("Çoklu ekran", "Multi-view"), Icons.Filled.GridView) {
                // Free this connection before the multi-view windows open theirs.
                releasePlayer()
                MultiViewActivity.start(this, channel)
                finish()
            }
        }
        val external = media?.takeIf { it.url.startsWith("http", ignoreCase = true) }?.let { Triple(it.url, it.title, repository.userAgentFor(playlist)) }
            ?: channels.getOrNull(index)?.takeIf { media == null }?.let { Triple(it.streamUrl, it.name, repository.userAgentFor(playlist, it)) }
        if (external != null) {
            items += DialogOption(tx("Harici oynatıcıda aç", "Open in external player"), Icons.AutoMirrored.Filled.OpenInNew) {
                val (url, title, userAgent) = external
                val intent = ExternalPlayer.intent(this, url, title, userAgent, if (media != null) exo.currentPosition else 0L)
                if (intent == null) {
                    showStatus(ExternalPlayer.NOT_FOUND)
                    handler.removeCallbacks(hideStatus)
                    handler.postDelayed(hideStatus, 3_000)
                } else {
                    // Most providers allow one connection: hand it over instead of keeping ours open.
                    saveMediaProgress()
                    releasePlayer()
                    runCatching { startActivity(intent) }
                    finish()
                }
            }
        }
        showMenu(media?.title ?: channels.getOrNull(index)?.name ?: "", items)
    }

    private fun audioDelayLabel(ms: Int): String = when {
        ms == 0 -> tx("yok", "none")
        ms > 0 -> tx("ses $ms ms geç", "sound $ms ms later")
        else -> tx("ses ${-ms} ms erken", "sound ${-ms} ms earlier")
    }

    /** Lip sync for streams whose sound runs ahead of or behind the picture; kept for every channel. */
    private fun chooseAudioDelay() {
        val current = container.settings.audioDelayMs.value
        showMenu(
            tx("Ses gecikmesi", "Audio delay"),
            AppSettings.AUDIO_DELAY_CHOICES.map { ms ->
                DialogOption(audioDelayLabel(ms), selected = ms == current) {
                    container.settings.setAudioDelayMs(ms)
                    audioSink?.delayUs = ms * 1000L
                }
            },
        )
    }

    private data class TrackChoice(val group: androidx.media3.common.Tracks.Group, val index: Int, val label: String, val selected: Boolean)

    private fun tracksOf(exo: ExoPlayer, type: Int): List<TrackChoice> =
        exo.currentTracks.groups.filter { it.type == type && it.isSupported }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
                val format = group.getTrackFormat(i)
                val language = format.language?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(Lang.locale) }?.ifBlank { null }
                val name = format.label ?: language ?: tx("Parça ${i + 1}", "Track ${i + 1}")
                val label = if (type == C.TRACK_TYPE_TEXT) {
                    subtitleLabel(
                        name,
                        format.language,
                        forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                        hearingImpaired = format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0,
                    )
                } else {
                    listOfNotNull(
                        name,
                        format.channelCount.takeIf { it > 2 }?.let { "$it ch" },
                        format.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
                    ).joinToString(" · ")
                }
                TrackChoice(group, i, label, group.isTrackSelected(i))
            }
        }

    private fun chooseTrack(type: Int) {
        val exo = player ?: return
        val tracks = tracksOf(exo, type)
        val select = { track: TrackChoice? ->
            val builder = exo.trackSelectionParameters.buildUpon()
            if (track == null) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(type, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(track.group.mediaTrackGroup, track.index))
            }
            exo.trackSelectionParameters = builder.build()
        }
        val options = buildList {
            if (type == C.TRACK_TYPE_TEXT) add(DialogOption(tx("Kapalı", "Off"), selected = tracks.none { it.selected }) { select(null) })
            tracks.forEach { track -> add(DialogOption(track.label, selected = track.selected) { select(track) }) }
        }
        showMenu(if (type == C.TRACK_TYPE_AUDIO) tx("Ses", "Audio") else tx("Altyazı", "Subtitles"), options)
    }

    // ---- Playback speed (films, episodes, archive) ---------------------------------------------

    private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    private fun speedLabel(speed: Float) = if (speed == 1f) tx("Normal", "Normal") else "${"%.2f".format(java.util.Locale.ROOT, speed).trimEnd('0').trimEnd('.')}x"

    private fun chooseSpeed() {
        val exo = player ?: return
        val current = exo.playbackParameters.speed
        showMenu(tx("Oynatma hızı", "Playback speed"), speeds.map { speed ->
            DialogOption(speedLabel(speed), selected = speed == current) { exo.setPlaybackSpeed(speed) }
        })
    }

    // ---- Sleep timer and "Are you still there?" (logic in PlaybackTimers) -----------------------

    private val timers = PlaybackTimers()
    private val timerTick = object : Runnable {
        override fun run() {
            when (val tick = timers.tick(playing = player?.isPlaying == true, idleHours = container.settings.idleHours.value)) {
                is PlaybackTimers.Tick.SleepWarning -> showStatus(
                    tx("Uyku zamanlayıcısı: %d sn sonra kapanacak\nİzlemeye devam etmek için bir tuşa basın", "Sleep timer: closing in %d s\nPress any key to keep watching").format(tick.secondsLeft),
                )
                PlaybackTimers.Tick.SleepEnd -> {
                    // Sleep means silence: neither the way back to the preview nor a restarted preview.
                    SleepHold.channelId = currentChannel?.id ?: 0L
                    ReturnTo.channelId = 0L
                    finish()
                    return
                }
                is PlaybackTimers.Tick.IdleAsking -> showIdleCard(tick.secondsLeft)
                PlaybackTimers.Tick.IdleStop -> stopForIdle()
                PlaybackTimers.Tick.None -> Unit
            }
            checkPicture()
            checkBuffer()
            handler.postDelayed(this, 1_000)
        }
    }

    /** Watches a live channel's start for a picture that never comes while the sound plays (see [PictureWatchdog]). */
    private val pictureWatch = PictureWatchdog()
    /** Stops reconnecting a stream the server keeps closing (see [ServerCloseGuard]). */
    private val closeGuard = ServerCloseGuard()
    /** When the playing channel was last (re)connected. */
    private var connectedAt = 0L
    /** The playing channel's player came from the preview (for the memory log). */
    private var fromPreview = false

    private fun checkPicture() {
        val exo = player ?: return
        val wants = media == null && exo.playWhenReady && exo.playerError == null && !retryPending &&
            exo.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO) &&
            (exo.isPlaying || (exo.playbackState == Player.STATE_BUFFERING && exo.totalBufferedDuration >= PictureWatchdog.STUCK_AHEAD_MS))
        val frames = exo.videoDecoderCounters?.let { it.ensureUpdated(); it.renderedOutputBufferCount.toLong() } ?: 0L
        when (pictureWatch.check(android.os.SystemClock.elapsedRealtime(), wants, frames)) {
            PictureWatchdog.Action.REATTACH -> {
                val from = if (fromPreview) " (from preview)" else ""
                io.github.ta3hrj.elm.data.MemoryLog.event(this, "no picture while the sound plays$from: picture surface attached again")
                playerView.player = null
                playerView.player = exo
            }
            PictureWatchdog.Action.RECONNECT -> {
                io.github.ta3hrj.elm.data.MemoryLog.event(this, "still no picture: channel reconnected")
                // Stopping releases the decoders, so the reconnect starts with fresh ones.
                exo.stop()
                retryPlayback.run()
            }
            PictureWatchdog.Action.NONE -> Unit
        }
    }

    /** A live buffer that reached its ceiling ([bufferCapBytes]) is freed by starting the channel again. */
    private fun checkBuffer() {
        if (media != null || retryPending) return
        val live = liveHandle ?: return
        if (live.exo !== player || !live.bufferFull) return
        val line = bufferFullLine("full screen", currentChannel?.name, live.bufferedBytes, live.exo.totalBufferedDuration)
        io.github.ta3hrj.elm.data.MemoryLog.event(this, line)
        live.exo.stop()
        retryPlayback.run()
    }

    private fun sleepLabel(): String {
        val minutes = timers.sleepMinutesLeft()
        return if (minutes == 0) tx("Kapalı", "Off") else tx("$minutes dk kaldı", "$minutes min left")
    }

    private fun chooseSleep() {
        val choices = listOf(0, 15, 30, 60, 90, 120)
        showMenu(tx("Uyku zamanlayıcısı", "Sleep timer"), choices.map { minutes ->
            DialogOption(if (minutes == 0) tx("Kapalı", "Off") else tx("$minutes dakika", "$minutes minutes"), selected = minutes == 0 && timers.sleepAt == 0L) {
                setSleepTimer(minutes)
            }
        }, subtitle = if (timers.sleepAt > 0) sleepLabel() else null)
    }

    private fun setSleepTimer(minutes: Int) {
        timers.setSleep(minutes)
        showStatus(if (minutes == 0) tx("Uyku zamanlayıcısı kapatıldı", "Sleep timer off") else tx("$minutes dakika sonra kapanacak", "Closing in $minutes minutes"))
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 2_500)
    }

    /** Playback was stopped for inactivity: nothing may start the stream again but a key press. */
    private val idleStopped: Boolean get() = timers.idle == PlaybackTimers.Idle.STOPPED

    /** Nobody answered: stop the stream but stay here with the note, so the user knows what happened. */
    private fun stopForIdle() {
        retryPending = false
        handler.removeCallbacks(retryPlayback)
        saveMediaProgress()
        player?.stop()
        progress.visibility = View.GONE
        // Lets the TV's screensaver and standby take over.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView.keepScreenOn = false
        showIdleCard()
    }

    private fun resumeAfterIdle() {
        idleCard.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView.keepScreenOn = true
        if (media != null) playMedia() else playCurrent(showInfo = true)
    }

    /** The question (with [secondsLeft]) or, after the stop, what happened and how to go on. */
    private fun showIdleCard(secondsLeft: Int = 0) {
        val hours = container.settings.idleHours.value
        if (idleStopped) {
            idleTitle.text = tx("Yayın durduruldu", "Playback stopped")
            idleBody.text = tx(
                "$hours saattir hiç tuşa basılmadığı için yayını durdurdum.",
                "No key was pressed for $hours hours, so I stopped playback.",
            )
            idleHint.text = tx("Kaldığınız yerden devam etmek için bir tuşa basın · Geri: çık", "Press any key to continue where you were · Back: exit")
        } else {
            idleTitle.text = tx("Orada mısınız?", "Are you still there?")
            idleBody.text = tx(
                "$hours saattir hiç tuşa basılmadı. Hâlâ izliyor musunuz?\nYayın $secondsLeft sn sonra duraklatılacak.",
                "No key has been pressed for $hours hours. Are you still watching?\nPlayback pauses in $secondsLeft s.",
            )
            idleHint.text = tx("İzlemeye devam etmek için bir tuşa basın", "Press any key to keep watching")
        }
        statusText.visibility = View.GONE
        idleCard.visibility = View.VISIBLE
    }

    /** Every key press goes here first; while a timer asks, the press answers it and does nothing else. */
    private fun interceptTimers(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (timers.onKeyDown(isBack = event.keyCode == KeyEvent.KEYCODE_BACK)) {
            PlaybackTimers.Key.PASS -> return false
            PlaybackTimers.Key.SLEEP_CANCELLED -> showStatus(tx("Uyku zamanlayıcısı iptal edildi", "Sleep timer cancelled"))
            PlaybackTimers.Key.IDLE_ANSWERED -> {
                idleCard.visibility = View.GONE
                showStatus(tx("İzlemeye devam", "Continuing"))
            }
            PlaybackTimers.Key.RESUME -> {
                resumeAfterIdle()
                return true
            }
            PlaybackTimers.Key.EXIT -> {
                finish()
                return true
            }
        }
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 2_000)
        return true
    }

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )

    private fun resizeLabel(mode: Int) = when (mode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> tx("Doldur (kırp)", "Fill (crop)")
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> tx("Uzat", "Stretch")
        else -> tx("Sığdır", "Fit")
    }

    private fun chooseResize() {
        showMenu(tx("Görüntü", "Picture"), resizeModes.map { mode ->
            DialogOption(resizeLabel(mode), selected = mode == playerView.resizeMode) {
                playerView.resizeMode = mode
                container.prefs.edit().putInt(PREF_RESIZE_MODE, mode).apply()
            }
        })
    }

    private fun chooseRecording(channel: ChannelEntity) {
        val minutes = io.github.ta3hrj.elm.recordings.Recordings.DURATION_CHOICES
        showMenu(tx("Kayıt süresi", "Recording length"), minutes.indices.map { which ->
            DialogOption(tx("${minutes[which]} dakika", "${minutes[which]} minutes"), Icons.Filled.Timer) {
                lifecycleScope.launch {
                    val now = System.currentTimeMillis()
                    val title = container.guide.upcoming(channel.playlistId, channel.streamUrl, now, 1).firstOrNull()?.title ?: channel.name
                    val (saved, error) = container.recordings.schedule(
                        playlistId = channel.playlistId,
                        channelName = channel.name,
                        title = title,
                        streamUrl = channel.streamUrl,
                        userAgent = repository.userAgentFor(playlist, channel),
                        start = now,
                        stop = now + minutes[which] * 60_000L,
                    )
                    showStatus(if (saved != null) tx("● Kayıt başladı (${minutes[which]} dk)", "● Recording started (${minutes[which]} min)") else error.orEmpty())
                    handler.removeCallbacks(hideStatus)
                    handler.postDelayed(hideStatus, 3_000)
                }
            }
        })
    }

    // ---- In-player channel list ---------------------------------------------------------------

    private fun openChannelPanel() {
        val playlistId = playlist?.id ?: channels.firstOrNull()?.playlistId ?: return
        lifecycleScope.launch {
            val unlocked = container.profiles.contentUnlockedNow
            val lockedNames = repository.observeGroupFlags(playlistId).first()
                .filter { it.scope == FlagScopes.LIVE && it.locked }.mapTo(HashSet()) { it.name }
            val groups = buildList {
                if (repository.channels(playlistId, ChannelQuery.Recent, unlocked).isNotEmpty()) {
                    add(PanelGroup(tx("🕒 Son izlenenler", "🕒 Recently watched"), ChannelQuery.Recent))
                }
                if (repository.observeFavoriteUrls(playlistId).first().isNotEmpty()) add(PanelGroup(tx("★ Favoriler", "★ Favorites"), ChannelQuery.Favorites))
                repository.observeFavoriteLists(playlistId).first().forEach { add(PanelGroup("☆ " + it.name, ChannelQuery.FavoriteList(it.id, it.name))) }
                repository.observeGroups(playlistId).first().forEach {
                    if (unlocked || it.name !in lockedNames) add(PanelGroup(it.name, ChannelQuery.Group(it.name)))
                }
            }
            if (groups.isEmpty()) return@launch
            // Open on the list being watched; a search result list falls back to the channel's own category.
            val matching = groups.indexOfFirst { it.query == currentQuery }
            val index = if (matching >= 0) matching else groups.indexOfFirst { it.query == ChannelQuery.Group(currentChannel?.groupTitle.orEmpty()) }.coerceAtLeast(0)
            panelChannels = if (matching >= 0) channels else repository.channels(playlistId, groups[index].query, unlocked)
            panelGroups = groups
            panelIndex = index
            panelNow = container.guide.observeNow(playlistId, System.currentTimeMillis()).first().associateBy { it.streamUrl }
            infoPanel.visibility = View.GONE
            panelOpen = true
        }
    }

    private fun stepPanelGroup(delta: Int) {
        val groups = panelGroups
        if (groups.size < 2) return
        val next = (panelIndex + delta).mod(groups.size)
        panelIndex = next
        val playlistId = playlist?.id ?: return
        lifecycleScope.launch {
            val list = repository.channels(playlistId, groups[next].query, container.profiles.contentUnlockedNow)
            if (panelIndex == next) panelChannels = list
        }
    }

    private fun selectFromPanel(channel: ChannelEntity) {
        val list = panelChannels
        currentQuery = panelGroups.getOrNull(panelIndex)?.query ?: currentQuery
        channels = list
        index = list.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        retries = 0
        livePausedAt = 0L
        handler.removeCallbacks(pauseTicker)
        panelOpen = false
        playCurrent(showInfo = true)
    }

    /** Back to the channel watched before this one, even if it is in another category. */
    private fun goToPreviousChannel() {
        val target = previousChannel ?: run {
            showStatus(tx("Önceki kanal yok", "No previous channel"))
            handler.removeCallbacks(hideStatus)
            handler.postDelayed(hideStatus, 2_000)
            return
        }
        livePausedAt = 0L
        handler.removeCallbacks(pauseTicker)
        retries = 0
        val position = channels.indexOfFirst { it.id == target.id }
        if (position >= 0) {
            index = position
            playCurrent(showInfo = true)
            return
        }
        lifecycleScope.launch {
            currentQuery = ChannelQuery.Group(target.groupTitle)
            channels = repository.channels(target.playlistId, currentQuery, container.profiles.contentUnlockedNow)
            index = channels.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
            playCurrent(showInfo = true)
        }
    }

    private fun switchChannel(delta: Int) {
        if (channels.size < 2) {
            showInfo()
            return
        }
        index = (index + delta).mod(channels.size)
        retries = 0
        livePausedAt = 0L
        handler.removeCallbacks(pauseTicker)
        playCurrent(showInfo = true)
    }

    /** The channel whose player came from the preview and is already playing it (see [PlayerHandoff]); 0 = none. */
    private var adoptedChannelId = 0L

    private fun initializePlayer() {
        if (player != null) return
        // Coming from a preview that plays this very channel: take its player over as it is (same
        // connection and buffer, no black screen). Anything else gets a fresh player.
        val channelId = if (media == null) intent.getLongExtra(EXTRA_CHANNEL, -1L) else -1L
        val adopted = if (channelId > 0) PlayerHandoff.take(channelId) else null
        val live = adopted ?: buildLivePlayer(this, container.settings)
        adoptedChannelId = if (adopted != null) channelId else 0L
        tech = live.tech
        liveHandle = live
        // The decoders are reported a moment after the stream starts: refresh the open info panel then.
        live.tech.onChange = { handler.post { if (infoPanel.visibility == View.VISIBLE) updateTech() } }
        player = live.exo
            .also { exo ->
                audioSink = live.audioSink
                audioSink?.delayUs = container.settings.audioDelayMs.value * 1000L
                exo.addListener(playerListener)
                exo.addAnalyticsListener(frameRateListener)
                if (container.settings.frameRateMatching.value) {
                    // The display mode is switched here instead (seamless switching alone rarely changes anything).
                    exo.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
                    // An adopted stream already told its frame rate before this listener existed.
                    if (adopted != null) exo.videoFormat?.let { matchFrameRate(it.frameRate) }
                }
                exo.playWhenReady = true
                playerView.player = exo
            }
    }

    // ---- Frame rate matching ------------------------------------------------------------------

    private val frameRateListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            if (container.settings.frameRateMatching.value) matchFrameRate(format.frameRate)
        }
    }

    /** Asks the TV for the display mode whose refresh rate suits [fps], keeping the current resolution. */
    @Suppress("DEPRECATION")
    private fun matchFrameRate(fps: Float) {
        if (fps <= 0f) return
        val display = (if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val current = display.mode
        val modes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val rate = io.github.ta3hrj.elm.core.FrameRate.bestRefreshRate(fps, current.refreshRate, modes.map { it.refreshRate }) ?: return
        val mode = modes.firstOrNull { it.refreshRate == rate } ?: return
        if (window.attributes.preferredDisplayModeId == mode.modeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        showStatus(tx("Ekran ${"%.2f".format(java.util.Locale.ROOT, rate).trimEnd('0').trimEnd('.')} Hz'e geçiyor…", "Switching the display to ${"%.2f".format(java.util.Locale.ROOT, rate).trimEnd('0').trimEnd('.')} Hz…"))
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 2_500)
    }

    /** Lets go of the player without releasing it: no listeners, no view. Null when there is none. */
    private fun detachPlayer(): ExoPlayer? {
        tech?.onChange = null
        tech = null
        liveHandle = null
        val exo = player ?: return null
        exo.removeListener(playerListener)
        exo.removeAnalyticsListener(frameRateListener)
        player = null
        playerView.player = null
        return exo
    }

    private fun releasePlayer() {
        detachPlayer()?.release()
    }

    override fun onPause() {
        super.onPause()
        // Back from the full-screen player to the preview: this runs before the screen below resumes, so
        // the player is already on offer when the preview looks for it.
        if (isFinishing) handBackToPreview()
    }

    /**
     * Gives the playing channel's player back to the preview that is waiting for it, so the channel goes on
     * in the preview without a new connection. Muted while it is in transit (nothing is heard if nobody
     * takes it); the preview gives it its sound back.
     */
    private fun handBackToPreview() {
        val handle = liveHandle ?: return
        val channel = currentChannel
        val wanted = ReturnTo.channelId
        ReturnTo.channelId = 0
        val exo = player ?: return
        val usable = media == null && channel != null && wanted == channel.id && !idleStopped && livePausedAt == 0L &&
            exo.playbackState != Player.STATE_IDLE && exo.playerError == null
        if (!usable) return
        handler.removeCallbacksAndMessages(null)
        retryPending = false
        // Muted, not paused: it keeps decoding while it is on its way, so the preview shows a picture at once.
        exo.volume = 0f
        detachPlayer()
        PlayerReturn.offer(channel!!.id, handle)
        // Nobody below to take it (e.g. the screen was recreated): release it soon.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ PlayerReturn.expire() }, 5_000L)
    }

    private fun playMedia() {
        val exo = player ?: return
        val request = media ?: return
        if (idleStopped) return
        if (retrying) io.github.ta3hrj.elm.data.MemoryLog.countRetry() else io.github.ta3hrj.elm.data.MemoryLog.countPlay()
        handler.removeCallbacks(retryPlayback)
        statusText.visibility = View.GONE
        progress.visibility = View.VISIBLE
        val dataSource = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(container.http).setUserAgent(repository.userAgentFor(playlist)),
        )
        val item = MediaItem.Builder()
            .setUri(request.url)
            .apply { if (isHls(request.url)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        exo.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        exo.prepare()
        exo.playWhenReady = true
        if (mediaPosition > 0) exo.seekTo(mediaPosition)

        infoNumber.text = request.subtitle
        infoName.text = request.title
        infoGroup.text = ""
        showLogo(null)
        updateBadges()
        infoNow.visibility = View.GONE
        infoProgress.visibility = View.GONE
        infoNext.visibility = View.GONE
        infoPanel.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, INFO_TIMEOUT_MS)

        val key = request.progressKey
        if (key != null && !resumeChecked && mediaPosition <= 0) {
            resumeChecked = true
            lifecycleScope.launch {
                val saved = container.vod.progress(request.playlistId, key) ?: return@launch
                player?.seekTo(saved.positionMs)
                showStatus(tx("Kaldığınız yerden devam ediliyor (${formatDuration(saved.positionMs)})", "Continuing where you left off (${formatDuration(saved.positionMs)})"))
                handler.postDelayed({ statusText.visibility = View.GONE }, 3_000)
            }
        }
        resumeChecked = true
    }

    /** The episode the countdown will start, while the countdown is on screen. */
    private var pendingNext: MediaRequest? = null
    private var countdown = 0
    private val countdownTick = object : Runnable {
        override fun run() {
            val next = pendingNext ?: return
            if (countdown <= 0) {
                playNextEpisode(next)
                return
            }
            showStatus(tx("Sonraki bölüm: ${next.title}\n${next.subtitle}\n\n$countdown sn içinde başlıyor  ·  OK: şimdi  ·  Geri: kapat", "Next episode: ${next.title}\n${next.subtitle}\n\nStarting in $countdown s  ·  OK: now  ·  Back: close"))
            countdown--
            handler.postDelayed(this, 1_000)
        }
    }

    private fun offerNextEpisode(current: MediaRequest) {
        val playlist = playlist ?: return finish()
        lifecycleScope.launch {
            val next = runCatching {
                val series = container.vod.item(current.seriesItemId) ?: return@runCatching null
                val episodes = container.vod.seriesInfo(playlist, series).seasons.values.flatten()
                val index = episodes.indexOfFirst { it.id == current.episodeId }
                val episode = episodes.getOrNull(index + 1).takeIf { index >= 0 } ?: return@runCatching null
                current.copy(
                    url = container.vod.episodeUrl(playlist, episode.id, episode.containerExtension),
                    title = episode.title,
                    subtitle = tx("${series.name} · S${episode.season} B${episode.number}", "${series.name} · S${episode.season} E${episode.number}"),
                    progressKey = VodRepository.episodeKey(episode.id),
                    episodeId = episode.id,
                )
            }.getOrNull()
            if (next == null) {
                finish()
                return@launch
            }
            pendingNext = next
            countdown = NEXT_EPISODE_COUNTDOWN_S
            handler.removeCallbacks(countdownTick)
            handler.post(countdownTick)
        }
    }

    private fun playNextEpisode(next: MediaRequest) {
        handler.removeCallbacks(countdownTick)
        pendingNext = null
        media = next
        mediaPosition = -1
        resumeChecked = false
        statusText.visibility = View.GONE
        playMedia()
    }

    private fun saveMediaProgress() {
        val request = media ?: return
        val exo = player ?: return
        val position = exo.currentPosition
        mediaPosition = position
        val key = request.progressKey ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (position <= 0) return
        container.appScope.launch {
            container.vod.saveProgress(request.playlistId, key, position, duration)
            if (request.privateViewing) return@launch
            container.vod.updateContinue(
                io.github.ta3hrj.elm.data.ContinueWatchingEntity(
                    playlistId = request.playlistId,
                    mediaKey = key,
                    title = request.title,
                    subtitle = request.subtitle,
                    url = request.url,
                    poster = request.poster,
                    seriesItemId = request.seriesItemId,
                    episodeId = request.episodeId,
                    positionMs = position,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        if (!request.privateViewing) container.watchNext.update(key, request.title, request.subtitle, request.toUri(), position, duration)
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun playCurrent(showInfo: Boolean) {
        if (idleStopped) return
        val exo = player ?: return
        val channel = channels.getOrNull(index) ?: return
        currentChannel?.let { if (it.id != channel.id) previousChannel = it }
        currentChannel = channel
        if (retrying) io.github.ta3hrj.elm.data.MemoryLog.countRetry() else io.github.ta3hrj.elm.data.MemoryLog.countPlay()
        container.appScope.launch { repository.markWatched(channel) }
        handler.removeCallbacks(retryPlayback)
        statusText.visibility = View.GONE
        progress.visibility = View.VISIBLE

        // The preview already plays this channel (handed over): carry on instead of loading it again.
        val carryOn = adoptedChannelId == channel.id && exo.currentMediaItem != null && exo.playbackState != Player.STATE_IDLE
        adoptedChannelId = 0L
        fromPreview = carryOn
        if (retrying) pictureWatch.watch() else pictureWatch.start()
        if (!retrying) closeGuard.reset()
        connectedAt = android.os.SystemClock.elapsedRealtime()
        if (carryOn) {
            progress.visibility = if (exo.playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            exo.playWhenReady = true
        } else {
            exo.setMediaSource(liveMediaSource(container.http, repository.userAgentFor(playlist, channel), channel.streamUrl))
            exo.prepare()
            exo.playWhenReady = true
        }

        container.prefs.edit()
            .putLong(lastPlaylistKey(container.profiles.activeId), channel.playlistId)
            .putString(lastStreamKey(container.profiles.activeId), channel.streamUrl)
            .putString(lastQueryKey(container.profiles.activeId), encodeQuery(currentQuery) ?: encodeQuery(ChannelQuery.Group(channel.groupTitle)))
            .apply()
        if (showInfo) showInfo()
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            if (infoPanel.visibility == View.VISIBLE) updateBadges()
        }

        // Some streams only reveal the picture size once the first frame is decoded.
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (infoPanel.visibility == View.VISIBLE) updateBadges()
        }

        override fun onPlaybackStateChanged(state: Int) {
            progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            if (state == Player.STATE_READY) {
                retries = 0
                statusText.visibility = View.GONE
            }
            if (state == Player.STATE_ENDED) {
                val request = media
                if (request != null) {
                    request.progressKey?.let { key ->
                        // Finished: clear the saved position.
                        container.appScope.launch {
                            container.vod.saveProgress(request.playlistId, key, 1, 1)
                            container.vod.removeContinue(request.playlistId, key)
                        }
                    }
                    mediaPosition = -1
                    if (request.episodeId != null && request.seriesItemId > 0) offerNextEpisode(request) else finish()
                } else if (closeGuard.onServerClosed(android.os.SystemClock.elapsedRealtime() - connectedAt)) {
                    logDrop(null, Reconnect.QUICK_DELAY_MS)
                    scheduleRetry(tx("Yayın kesildi, yeniden bağlanılıyor…", "Stream interrupted, reconnecting…"))
                } else {
                    // Another device on the same account most likely: reconnecting again would only throw it off.
                    val line = dropLine("full screen", currentChannel?.name, ServerCloseGuard.REASON, 0L, retries + 1, null)
                    io.github.ta3hrj.elm.data.MemoryLog.event(this@PlayerActivity, line)
                    showStatus(
                        tx(
                            "Sağlayıcı yayını tekrar tekrar kapatıyor.\nAynı hesapla başka bir cihaz izliyor olabilir.\n\nYeniden denemek için kanalı tekrar seçin.",
                            "The provider keeps closing the stream.\nAnother device may be watching on the same account.\n\nChoose the channel again to retry.",
                        ),
                    )
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                io.github.ta3hrj.elm.data.MemoryLog.event(this@PlayerActivity, "fell behind the live window (full screen${currentChannel?.name?.let { ", $it" }.orEmpty()}): back to live")
                player?.seekToDefaultPosition()
                player?.prepare()
                return
            }
            val wait = Reconnect.delayMs(retries, isNetworkProblem(error))
            logDrop(error, wait)
            if (wait != null && retries < Reconnect.QUICK_TRIES) {
                val code = httpStatus(error)?.let { " (HTTP $it)" }.orEmpty()
                val tries = Reconnect.QUICK_TRIES
                scheduleRetry(tx("Bağlantı hatası$code, yeniden deneniyor (${retries + 1}/$tries)…", "Connection error$code, retrying (${retries + 1}/$tries)…"), wait)
            } else if (wait != null) {
                // Connection trouble (not a refused or broken stream): keep trying with longer pauses while
                // the player is open, and at once when the network comes back.
                val delay = wait
                scheduleRetry(
                    tx("Bağlantı yok. ${delay / 1000} sn sonra yeniden denenecek; ağ gelince hemen bağlanılır.",
                        "No connection. Retrying in ${delay / 1000} s, or as soon as the network is back."),
                    delay,
                )
            } else {
                progress.visibility = View.GONE
                showStatus(
                    if (media != null) {
                        tx("Oynatılamadı.\n${describe(error)}", "Could not play.\n${describe(error)}")
                    } else {
                        tx("Bu kanal oynatılamadı.\n${describe(error)}\n\nYukarı/aşağı ile başka kanala geçebilirsiniz.", "This channel could not be played.\n${describe(error)}\n\nUse up/down to switch channels.")
                    },
                )
            }
        }
    }

    /** Notes in the memory log why the stream dropped and what happens next ([waitMs] null: not tried again). */
    private fun logDrop(error: PlaybackException?, waitMs: Long?) {
        val name = if (media != null) media?.title else currentChannel?.name
        val line = dropLine("full screen", name, dropReason(error), player?.totalBufferedDuration ?: 0L, retries + 1, waitMs)
        io.github.ta3hrj.elm.data.MemoryLog.event(this, line)
    }

    private fun scheduleRetry(message: String, delayMs: Long = Reconnect.QUICK_DELAY_MS) {
        retries++
        showStatus(message)
        handler.removeCallbacks(retryPlayback)
        retryPending = true
        handler.postDelayed(retryPlayback, delayMs)
    }

    /** Retries at once when a network becomes available while a reconnect is waiting. */
    private fun watchNetwork() {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                handler.post {
                    if (retryPending) {
                        handler.removeCallbacks(retryPlayback)
                        handler.postDelayed(retryPlayback, NETWORK_SETTLE_MS)
                    }
                }
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        if (runCatching { manager.registerNetworkCallback(request, callback) }.isSuccess) networkCallback = callback
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { (getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)?.unregisterNetworkCallback(callback) }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> tx(
            "Sunucu yayını reddetti (HTTP ${httpStatus(error) ?: "?"}). Abonelik, bağlantı sınırı veya kanal kapalı olabilir; " +
                "sağlayıcı belirli bir User-Agent istiyorsa Listeler ekranından girebilirsiniz.",
            "The server refused the stream (HTTP ${httpStatus(error) ?: "?"}). The subscription, connection limit or the channel " +
                "may be the cause; if your provider needs a specific User-Agent, enter it on the Playlists screen.",
        )
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> tx("Sunucuya bağlanılamadı.", "Could not connect to the server.")
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> tx("Yayın biçimi desteklenmiyor. Liste ayarında HLS / MPEG-TS seçimini değiştirmeyi deneyin.", "Stream format not supported. Try switching HLS / MPEG-TS in the playlist settings.")
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> tx("Bu cihaz yayının video/ses biçimini çözemiyor.", "This device cannot decode the stream's video/audio format.")
        else -> error.errorCodeName
    }

    private fun showInfo() {
        val channel = channels.getOrNull(index) ?: return
        infoNumber.text = "${channel.number}  ·  ${index + 1}/${channels.size}"
        infoName.text = channel.name
        infoGroup.text = channel.groupTitle
        showLogo(channel.logo)
        updateBadges()
        infoPanel.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, INFO_TIMEOUT_MS)

        infoJob?.cancel()
        infoJob = lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val upcoming = container.guide.upcoming(channel.playlistId, channel.streamUrl, now, limit = 2)
            if (channels.getOrNull(index)?.id != channel.id) return@launch
            val current = upcoming.firstOrNull { it.start <= now }
            val next = upcoming.firstOrNull { it.start > now }
            if (current != null) {
                val left = ((current.stop - now) / 60_000 + 1).coerceAtLeast(1)
                infoNow.text = "${timeRange(current.start, current.stop)}   ${current.title}" + tx("   ·  $left dk kaldı", "   ·  $left min left")
                infoNow.visibility = View.VISIBLE
                infoProgress.max = 1000
                infoProgress.progress = (((now - current.start).toDouble() / (current.stop - current.start)) * 1000)
                    .toInt().coerceIn(0, 1000)
                infoProgress.visibility = View.VISIBLE
            } else {
                infoNow.text = if (upcoming.isEmpty()) tx("Rehber bilgisi yok", "No guide data") else ""
                infoNow.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
                infoProgress.visibility = View.GONE
            }
            if (next != null) {
                infoNext.text = tx("Sonra  ${timeRange(next.start, next.stop)}   ${next.title}", "Next  ${timeRange(next.start, next.stop)}   ${next.title}")
                infoNext.visibility = View.VISIBLE
            } else {
                infoNext.visibility = View.GONE
            }
        }
    }

    private fun showLogo(url: String?) {
        if (url.isNullOrBlank()) {
            infoLogo.visibility = View.GONE
        } else {
            infoLogo.visibility = View.VISIBLE
            infoLogo.load(url) {
                crossfade(true)
                // Dead logo links are common; hide the box instead of showing it empty.
                listener(onError = { _, _ -> infoLogo.visibility = View.GONE })
            }
        }
    }

    private fun selectedFormat(exo: ExoPlayer, type: Int): androidx.media3.common.Format? =
        exo.currentTracks.groups.firstOrNull { it.type == type && it.isSelected }?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
        }

    /** Picture and sound badges for the info bar: 4K / FHD / HD, 50 fps, Dolby, 5.1, CC, archive. */
    private fun updateBadges() {
        infoBadges.removeAllViews()
        val exo = player ?: return
        val labels = ArrayList<String>()
        // Right after a channel switch the player still reports the previous channel's formats until the
        // new stream's tracks are known; take the formats from the current tracks so no stale badges show
        // (they are added as soon as the new stream's tracks arrive).
        val tracks = exo.currentTracks
        val video = if (tracks.isEmpty) null else selectedFormat(exo, C.TRACK_TYPE_VIDEO)?.takeIf { it.height > 0 } ?: exo.videoFormat
        val audio = if (tracks.isEmpty) null else selectedFormat(exo, C.TRACK_TYPE_AUDIO)?.takeIf { it.channelCount > 0 } ?: exo.audioFormat
        video?.let { video ->
            val height = minOf(video.height, video.width).takeIf { it > 0 } ?: video.height
            when {
                height >= 2000 -> labels += "4K"
                height >= 1000 -> labels += "FHD"
                height >= 700 -> labels += "HD"
                height > 0 -> labels += "SD"
            }
            if (video.frameRate >= 48f) labels += "${kotlin.math.round(video.frameRate).toInt()} fps"
            if (video.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || video.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG) labels += "HDR"
        }
        audio?.let { audio ->
            when (audio.sampleMimeType) {
                MimeTypes.AUDIO_AC3 -> labels += "Dolby Digital"
                MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> labels += "Dolby Digital+"
                MimeTypes.AUDIO_TRUEHD -> labels += "Dolby TrueHD"
                MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> labels += "DTS"
            }
            when (audio.channelCount) {
                6 -> labels += "5.1"
                8 -> labels += "7.1"
                2 -> labels += "Stereo"
            }
        }
        if (tracksOf(exo, C.TRACK_TYPE_TEXT).isNotEmpty()) labels += "CC"
        if (media == null && (channels.getOrNull(index)?.catchupDays ?: 0) > 0) labels += tx("⟲ Arşiv", "⟲ Archive")
        if (timers.sleepAt > 0) labels += "☾ " + sleepLabel()
        for (label in labels) {
            infoBadges.addView(
                TextView(this).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    background = GradientDrawable().apply {
                        setColor(0x33FFFFFF)
                        setStroke(dp(1), 0x66FFFFFF)
                        cornerRadius = dp(4).toFloat()
                    }
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) },
            )
        }
        infoBadges.visibility = if (labels.isEmpty()) View.GONE else View.VISIBLE
        updateTech()
    }

    private fun updateTech() {
        val t = tech?.tech
        val lines = listOfNotNull(
            t?.let { videoTechLabel(it) }?.let { tx("Görüntü: ", "Picture: ") + it },
            t?.let { audioTechLabel(it) }?.let { tx("Ses: ", "Sound: ") + it },
        )
        infoTech.text = lines.joinToString("\n")
        infoTech.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun applySubtitleStyle() {
        val style = container.settings.subtitleStyle.value
        playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setStyle(
                CaptionStyleCompat(
                    SubtitleStyles.foreground(style),
                    SubtitleStyles.background(style),
                    Color.TRANSPARENT,
                    if (style.background == 0) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else CaptionStyleCompat.EDGE_TYPE_NONE,
                    Color.BLACK,
                    null,
                ),
            )
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * SubtitleStyles.scale(style.size))
        }
    }

    private fun showStatus(text: String) {
        statusText.text = text
        statusText.visibility = View.VISIBLE
    }

    private fun buildLayout() {
        val root = KeyInterceptLayout(this) { interceptTimers(it) || handleMediaKey(it) }.apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = container.prefs.getInt(PREF_RESIZE_MODE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
            setShutterBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))
        applySubtitleStyle()

        progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = rounded(0xCC1B2230.toInt())
            visibility = View.GONE
        }
        root.addView(statusText, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

        idleTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        idleBody = TextView(this).apply {
            setTextColor(0xFFDCE3EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }
        idleHint = TextView(this).apply {
            setTextColor(0xFF7FB8FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
        }
        idleCard = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(28), dp(40), dp(28))
            background = rounded(0xEE1B2230.toInt())
            addView(idleTitle)
            addView(idleBody)
            addView(idleHint)
            visibility = View.GONE
            // Above the info bar and the channel list, which are added later.
            translationZ = dp(24).toFloat()
        }
        root.addView(idleCard, FrameLayout.LayoutParams(dp(640), WRAP, Gravity.CENTER))

        infoNumber = TextView(this).apply {
            setTextColor(0xFF4FA3FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        infoName = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            maxLines = 1
        }
        infoGroup = TextView(this).apply {
            setTextColor(0xFFB8C2D0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        infoNow = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        infoProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF4FA3FF.toInt())
            visibility = View.GONE
        }
        infoNext = TextView(this).apply {
            setTextColor(0xFFB8C2D0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }
        infoLogo = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = rounded(0x33FFFFFF)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
        }
        infoBadges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(2))
            visibility = View.GONE
        }
        // Who decodes picture and sound (hardware, Android software, FFmpeg or passed through to the TV).
        infoTech = TextView(this).apply {
            setTextColor(0xFF9AA4B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, dp(2))
            visibility = View.GONE
        }
        val infoText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(infoNumber)
            addView(infoName)
            addView(infoGroup)
            addView(infoBadges)
            addView(infoTech)
            addView(infoNow)
            addView(infoProgress, LinearLayout.LayoutParams(dp(480), dp(6)).apply { topMargin = dp(6) })
            addView(infoNext)
        }
        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(18), dp(28), dp(18))
            background = rounded(0xD9101620.toInt())
            minimumWidth = dp(560)
            addView(infoLogo, LinearLayout.LayoutParams(dp(120), dp(80)).apply { marginEnd = dp(20) })
            addView(infoText)
            visibility = View.GONE
        }
        root.addView(
            infoPanel,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(48), 0, dp(48), dp(40))
            },
        )
        // Options menus are Compose dialogs, so they share the app's look and icons.
        root.addView(
            ComposeView(this).apply {
                setContent {
                    AppTheme {
                        if (panelOpen) {
                            ChannelPanel(
                                group = panelGroups.getOrNull(panelIndex),
                                hasMoreGroups = panelGroups.size > 1,
                                channels = panelChannels,
                                currentChannelId = currentChannel?.id ?: -1L,
                                now = panelNow,
                                onGroupStep = ::stepPanelGroup,
                                onSelect = ::selectFromPanel,
                                onClose = { panelOpen = false },
                            )
                        }
                        menu?.let { current ->
                            key(current) {
                                OptionsDialog(current.title, current.subtitle, current.options, onDismiss = { if (menu === current) menu = null })
                            }
                        }
                    }
                }
            },
            FrameLayout.LayoutParams(MATCH, MATCH),
        )
        setContentView(root)
    }

    /** Gestures on phones and tablets, turned into remote keys (see [TouchKeys]). */
    private val touch by lazy { TouchKeys.detector(this, this) { media == null } }
    /** A touch that answered a timer question: the rest of it (move, release) does nothing else. */
    private var swallowTouch = false

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // An open menu or channel list takes touches itself.
        if (panelOpen || menu != null) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            // Films: a touch is someone at the screen, like a key press for the timers ("Are you still there?").
            swallowTouch = media != null && interceptTimers(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN))
        }
        if (swallowTouch) return true
        val handled = touch.onTouchEvent(ev)
        // Live channels have no on-screen controls: every gesture is a key. Films keep their controls.
        if (media == null) return true
        return super.dispatchTouchEvent(ev) || handled
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        internal const val EXTRA_PLAYLIST = "playlist"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_QUERY_TYPE = "query_type"
        private const val EXTRA_QUERY_VALUE = "query_value"
        private const val EXTRA_QUERY_ID = "query_id"
        private const val STATE_CHANNEL = "state_channel"
        private const val PREF_LAST_PLAYLIST = "last_playlist"
        private const val PREF_LAST_STREAM = "last_stream_url"

        /** The last channel is remembered per profile; the main profile keeps the old keys. */
        fun lastPlaylistKey(profileId: Long) = if (profileId == 1L) PREF_LAST_PLAYLIST else "${PREF_LAST_PLAYLIST}_$profileId"

        fun lastStreamKey(profileId: Long) = if (profileId == 1L) PREF_LAST_STREAM else "${PREF_LAST_STREAM}_$profileId"

        /** The list the last channel was watched in (favorites, an own list, a category…), per profile. */
        fun lastQueryKey(profileId: Long) = "last_query_$profileId"

        // Preferences are stored as XML, which cannot hold control characters; names come last and are
        // split off with a limit, so a "|" inside a name is kept.
        private const val SEP = '|'

        /** Stores a channel list as text; a search is not kept (the channel's own category is used instead). */
        fun encodeQuery(query: ChannelQuery): String? = when (query) {
            is ChannelQuery.Group -> "group$SEP${query.name}"
            ChannelQuery.Favorites -> "favorites"
            ChannelQuery.Recent -> "recent"
            is ChannelQuery.FavoriteList -> "list$SEP${query.id}$SEP${query.name}"
            ChannelQuery.Archive -> "archive"
            is ChannelQuery.Search -> null
        }

        fun decodeQuery(text: String?): ChannelQuery? {
            val parts = text?.split(SEP, limit = 3) ?: return null
            return when (parts[0]) {
                "group" -> text.split(SEP, limit = 2).getOrNull(1)?.let { ChannelQuery.Group(it) }
                "favorites" -> ChannelQuery.Favorites
                "recent" -> ChannelQuery.Recent
                "archive" -> ChannelQuery.Archive
                "list" -> parts.getOrNull(1)?.toLongOrNull()?.let { ChannelQuery.FavoriteList(it, parts.getOrNull(2).orEmpty()) }
                else -> null
            }
        }
        private const val NUMBER_ENTRY_TIMEOUT_MS = 1_800L
        private const val PREF_RESIZE_MODE = "resize_mode"
        private const val LIVE_SEEK_MS = 10_000L

        private const val NETWORK_SETTLE_MS = 1_500L
        private const val INFO_TIMEOUT_MS = 4_000L
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT

        fun start(context: Context, playlistId: Long, query: ChannelQuery, channelId: Long) {
            val intent = Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_PLAYLIST, playlistId)
                .putExtra(EXTRA_CHANNEL, channelId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when (query) {
                is ChannelQuery.Group -> intent.putExtra(EXTRA_QUERY_TYPE, "group").putExtra(EXTRA_QUERY_VALUE, query.name)
                ChannelQuery.Favorites -> intent.putExtra(EXTRA_QUERY_TYPE, "favorites")
                is ChannelQuery.Search -> intent.putExtra(EXTRA_QUERY_TYPE, "search").putExtra(EXTRA_QUERY_VALUE, query.text)
                ChannelQuery.Recent -> intent.putExtra(EXTRA_QUERY_TYPE, "recent")
                ChannelQuery.Archive -> intent.putExtra(EXTRA_QUERY_TYPE, "archive")
                is ChannelQuery.FavoriteList -> intent.putExtra(EXTRA_QUERY_TYPE, "list").putExtra(EXTRA_QUERY_VALUE, query.name)
                    .putExtra(EXTRA_QUERY_ID, query.id)
            }
            context.startActivity(intent)
        }

        /**
         * Plays one seekable item (film, episode or archived programme).
         * @param progressKey saves and resumes the position under this key; null for no resume.
         */
        fun startMedia(
            context: Context,
            playlistId: Long,
            url: String,
            title: String,
            subtitle: String,
            progressKey: String?,
            seriesItemId: Long = -1,
            episodeId: String? = null,
            poster: String? = null,
            privateViewing: Boolean = false,
        ) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_PLAYLIST, playlistId)
                    .putExtra(EXTRA_MEDIA_URL, url)
                    .putExtra(EXTRA_MEDIA_TITLE, title)
                    .putExtra(EXTRA_MEDIA_SUBTITLE, subtitle)
                    .putExtra(EXTRA_MEDIA_KEY, progressKey)
                    .putExtra(EXTRA_SERIES_ITEM, seriesItemId)
                    .putExtra(EXTRA_EPISODE_ID, episodeId)
                    .putExtra(EXTRA_POSTER, poster)
                    .putExtra(EXTRA_PRIVATE, privateViewing)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        internal const val EXTRA_MEDIA_URL = "media_url"
        internal const val EXTRA_MEDIA_TITLE = "media_title"
        internal const val EXTRA_MEDIA_SUBTITLE = "media_subtitle"
        internal const val EXTRA_MEDIA_KEY = "media_key"
        internal const val EXTRA_SERIES_ITEM = "series_item"
        internal const val EXTRA_EPISODE_ID = "episode_id"
        internal const val EXTRA_POSTER = "poster"
        internal const val EXTRA_PRIVATE = "private"
        private const val NEXT_EPISODE_COUNTDOWN_S = 10


        private fun readQuery(intent: Intent): ChannelQuery {
            val value = intent.getStringExtra(EXTRA_QUERY_VALUE).orEmpty()
            return when (intent.getStringExtra(EXTRA_QUERY_TYPE)) {
                "favorites" -> ChannelQuery.Favorites
                "search" -> ChannelQuery.Search(value)
                "recent" -> ChannelQuery.Recent
                "archive" -> ChannelQuery.Archive
                "list" -> ChannelQuery.FavoriteList(intent.getLongExtra(EXTRA_QUERY_ID, -1), value)
                else -> ChannelQuery.Group(value)
            }
        }

        private fun isHls(url: String): Boolean = isHlsUrl(url)
    }
}

/** A single seekable item to play instead of the live channel list. */
internal data class MediaRequest(
    val playlistId: Long,
    val url: String,
    val title: String,
    val subtitle: String,
    val progressKey: String?,
    /** For episodes: the series (vod_items id) and episode id, so the next episode can follow. */
    val seriesItemId: Long = -1,
    val episodeId: String? = null,
    /** Artwork for the "continue watching" row. */
    val poster: String? = null,
    /** From a locked category: keep the position for resuming, but list it nowhere (home rows, Android TV). */
    val privateViewing: Boolean = false,
) {
    /** Deep link used by the Android TV "continue watching" row. */
    fun toUri(): android.net.Uri = android.net.Uri.Builder().scheme("elmiptv").authority("play")
        .appendQueryParameter("playlist", playlistId.toString())
        .appendQueryParameter("url", url)
        .appendQueryParameter("title", title)
        .appendQueryParameter("subtitle", subtitle)
        .apply { progressKey?.let { appendQueryParameter("key", it) } }
        .apply { if (seriesItemId > 0) appendQueryParameter("series", seriesItemId.toString()) }
        .apply { episodeId?.let { appendQueryParameter("episode", it) } }
        .apply { poster?.let { appendQueryParameter("poster", it) } }
        .apply { if (privateViewing) appendQueryParameter("private", "1") }
        .build()

    companion object {
        /** Opens a "continue watching" deep link. */
        fun open(context: Context, uri: android.net.Uri) {
            val url = uri.getQueryParameter("url") ?: return
            PlayerActivity.startMedia(
                context,
                uri.getQueryParameter("playlist")?.toLongOrNull() ?: -1,
                url,
                uri.getQueryParameter("title").orEmpty(),
                uri.getQueryParameter("subtitle").orEmpty(),
                uri.getQueryParameter("key"),
                uri.getQueryParameter("series")?.toLongOrNull() ?: -1,
                uri.getQueryParameter("episode"),
                uri.getQueryParameter("poster"),
                uri.getQueryParameter("private") == "1",
            )
        }

        fun from(intent: Intent): MediaRequest? {
            val url = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL) ?: return null
            return MediaRequest(
                playlistId = intent.getLongExtra(PlayerActivity.EXTRA_PLAYLIST, -1),
                url = url,
                title = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_TITLE).orEmpty(),
                subtitle = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_SUBTITLE).orEmpty(),
                progressKey = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_KEY),
                seriesItemId = intent.getLongExtra(PlayerActivity.EXTRA_SERIES_ITEM, -1),
                episodeId = intent.getStringExtra(PlayerActivity.EXTRA_EPISODE_ID),
                poster = intent.getStringExtra(PlayerActivity.EXTRA_POSTER),
                privateViewing = intent.getBooleanExtra(PlayerActivity.EXTRA_PRIVATE, false),
            )
        }
    }
}

/** Root layout that offers every key event to [interceptor] before dispatching it normally. */
private class KeyInterceptLayout(
    context: Context,
    private val interceptor: (KeyEvent) -> Boolean,
) : FrameLayout(context) {
    /** The key whose press the interceptor took: its release is taken too (OK's release clicks in a list). */
    private var swallowRelease = -1

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == swallowRelease) {
            swallowRelease = -1
            return true
        }
        if (interceptor(event)) {
            if (event.action == KeyEvent.ACTION_DOWN) swallowRelease = event.keyCode
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
