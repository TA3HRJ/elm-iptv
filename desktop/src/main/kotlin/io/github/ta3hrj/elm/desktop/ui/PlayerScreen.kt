package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.PlayRequest
import io.github.ta3hrj.elm.desktop.player.PlayerAction
import io.github.ta3hrj.elm.desktop.player.PlayerKeys
import io.github.ta3hrj.elm.desktop.player.ReconnectPolicy
import io.github.ta3hrj.elm.desktop.player.Track
import io.github.ta3hrj.elm.desktop.player.Vlc
import io.github.ta3hrj.elm.desktop.player.VideoPlayer
import io.github.ta3hrj.elm.desktop.player.VlcOptions
import io.github.ta3hrj.elm.desktop.player.formatTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(state: AppState, request: PlayRequest, window: WindowState) {
    val factory = Vlc.factory ?: return
    val scope = rememberCoroutineScope()
    var index by remember(request) { mutableIntStateOf(request.index) }
    var previous by remember(request) { mutableIntStateOf(-1) }
    var panelOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val reconnect = remember(request) { ReconnectPolicy() }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    val placementBefore = remember { window.placement }
    val fullscreen = window.placement == WindowPlacement.Fullscreen
    val channel = if (request.live) request.channels.getOrNull(index) else null

    lateinit var player: VideoPlayer
    fun start() {
        val p = state.playlist
        // Read now, not when this screen was first drawn: the callbacks below outlive that moment.
        val current = if (request.live) request.channels.getOrNull(index) else null
        val url = current?.streamUrl ?: request.url
        val ua = if (current != null && p != null) state.catalog.userAgent(p, current) else request.userAgent
        player.play(url, VlcOptions.forMedia(ua, state.settings.networkCacheMs, request.live, if (current == null) request.startMs else 0, state.settings.hardwareDecoding))
    }

    fun close() {
        if (window.placement == WindowPlacement.Fullscreen) window.placement = placementBefore
        state.playing = null
    }

    fun zap(to: Int) {
        val list = request.channels
        if (list.size < 2) return
        previous = index
        index = (to + list.size) % list.size
        list.getOrNull(index)?.let { state.noteRecent(it) }
    }

    fun act(action: PlayerAction) {
        when (action) {
            PlayerAction.TOGGLE_PAUSE -> player.togglePause()
            PlayerAction.TOGGLE_FULLSCREEN -> window.placement = if (window.placement == WindowPlacement.Fullscreen) placementBefore.takeIf { it != WindowPlacement.Fullscreen } ?: WindowPlacement.Floating else WindowPlacement.Fullscreen
            PlayerAction.EXIT -> if (window.placement == WindowPlacement.Fullscreen) window.placement = placementBefore.takeIf { it != WindowPlacement.Fullscreen } ?: WindowPlacement.Floating else close()
            PlayerAction.NEXT_CHANNEL -> if (request.live) zap(index + 1)
            PlayerAction.PREVIOUS_CHANNEL -> if (request.live) zap(index - 1)
            PlayerAction.LAST_CHANNEL -> if (request.live && previous >= 0) zap(previous)
            PlayerAction.SEEK_BACK -> player.seekBy(-10_000)
            PlayerAction.SEEK_FORWARD -> player.seekBy(10_000)
            PlayerAction.VOLUME_UP -> setVolume(state, player, player.status.volume + 5)
            PlayerAction.VOLUME_DOWN -> setVolume(state, player, player.status.volume - 5)
            PlayerAction.MUTE -> player.toggleMute()
            PlayerAction.CHANNEL_LIST -> if (request.live) panelOpen = !panelOpen
        }
    }

    player = remember(request) {
        VideoPlayer(
            factory,
            onAction = { act(it) },
            onFinished = {
                if (request.live) {
                    // A live stream does not end: the connection dropped. Try again a few times.
                    retryJob?.cancel()
                    val delayMs = reconnect.nextDelayMs()
                    if (delayMs == null) note = tx("Yayın kesildi.", "The stream stopped.") else retryJob = scope.launch {
                        note = tx("Yeniden bağlanılıyor…", "Reconnecting…")
                        delay(delayMs)
                        start()
                    }
                } else {
                    request.resumeKey?.let { state.saveResume(it, player.status.lengthMs, player.status.lengthMs) }
                    close()
                }
            },
            onFailed = {
                if (request.live) {
                    retryJob?.cancel()
                    val delayMs = reconnect.nextDelayMs() ?: return@VideoPlayer
                    retryJob = scope.launch { delay(delayMs); start() }
                }
            },
        )
    }
    val status = player.status
    LaunchedEffect(status.playing) {
        if (status.playing) {
            reconnect.onPlaying()
            note = null
        }
    }
    LaunchedEffect(request, index) {
        retryJob?.cancel()
        player.setVolume(state.settings.volume)
        start()
    }
    DisposableEffect(request) {
        onDispose {
            retryJob?.cancel()
            request.resumeKey?.let { key -> runCatching { state.saveResume(key, player.timeMs(), player.status.lengthMs) } }
            player.release()
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val awt = event.nativeKeyEvent as? java.awt.event.KeyEvent ?: return@onKeyEvent false
                PlayerKeys.action(awt.keyCode, awt.modifiersEx)?.let { act(it); true } ?: false
            },
    ) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tx("Geri (Esc)", "Back (Esc)")) }
                Column(Modifier.weight(1f)) {
                    val title = channel?.let { listOfNotNull(it.number, it.name).joinToString("  ") } ?: request.title
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = channel?.let { c ->
                        val now = System.currentTimeMillis()
                        listOfNotNull(
                            state.guide.now(c.streamUrl, now)?.let { "${hhmm(it.start)} ${it.title}" },
                            state.guide.next(c.streamUrl, now)?.let { tx("Sonra: ", "Next: ") + "${hhmm(it.start)} ${it.title}" },
                        ).joinToString("   ·   ").ifEmpty { c.group }
                    } ?: request.subtitle
                    sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                (note ?: status.error)?.let { Text(it, color = Color(0xFFFFC857), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SwingPanel(background = Color.Black, factory = { player.component }, modifier = Modifier.fillMaxSize())
            }
            if (panelOpen && request.live && !fullscreen) {
                SidePanel(Modifier.width(300.dp)) {
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 3).coerceAtLeast(0))
                    LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                        itemsIndexed(request.channels, key = { _, c -> c.streamUrl }) { i, c ->
                            GroupRow(listOfNotNull(c.number, c.name).joinToString("  "), null, selected = i == index) { zap(i) }
                        }
                    }
                }
            }
        }
        if (!fullscreen) Controls(state, request, player, status, ::act, onPanel = { panelOpen = !panelOpen })
    }
}

private fun setVolume(state: AppState, player: VideoPlayer, volume: Int) {
    val v = volume.coerceIn(0, 150)
    player.setVolume(v)
    state.updateSettings { it.copy(volume = v) }
}

@Composable
private fun Controls(
    state: AppState,
    request: PlayRequest,
    player: VideoPlayer,
    status: io.github.ta3hrj.elm.desktop.player.PlayerStatus,
    act: (PlayerAction) -> Unit,
    onPanel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (!request.live && status.lengthMs > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(status.timeMs), style = MaterialTheme.typography.labelSmall, color = Muted)
                Slider(
                    value = (status.timeMs.toFloat() / status.lengthMs).coerceIn(0f, 1f),
                    onValueChange = { player.seekTo((it * status.lengthMs).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatTime(status.lengthMs), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (request.live) IconButton(onClick = { act(PlayerAction.PREVIOUS_CHANNEL) }) { Icon(Icons.Filled.SkipPrevious, tx("Önceki kanal", "Previous channel")) }
            IconButton(onClick = { act(PlayerAction.TOGGLE_PAUSE) }) {
                Icon(if (status.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tx("Duraklat / oynat", "Pause / play"))
            }
            if (request.live) IconButton(onClick = { act(PlayerAction.NEXT_CHANNEL) }) { Icon(Icons.Filled.SkipNext, tx("Sonraki kanal", "Next channel")) }
            if (status.buffering in 0.1f..99.9f) Text(tx("Yükleniyor %${status.buffering.toInt()}", "Loading ${status.buffering.toInt()}%"), color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            TrackMenu(Icons.Filled.Audiotrack, tx("Ses", "Audio"), { player.audioTracks() }, { player.audioTrack() }, player::setAudioTrack)
            TrackMenu(Icons.Filled.Subtitles, tx("Altyazı", "Subtitles"), { player.subtitleTracks() }, { player.subtitleTrack() }, player::setSubtitleTrack)
            IconButton(onClick = { act(PlayerAction.MUTE) }) {
                Icon(if (status.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, tx("Sessiz (M)", "Mute (M)"))
            }
            Slider(
                value = status.volume / 150f,
                onValueChange = { setVolume(state, player, (it * 150).toInt()) },
                modifier = Modifier.width(120.dp),
            )
            if (request.live) IconButton(onClick = onPanel) { Icon(Icons.AutoMirrored.Filled.List, tx("Kanal listesi (L)", "Channel list (L)")) }
            IconButton(onClick = { act(PlayerAction.TOGGLE_FULLSCREEN) }) { Icon(Icons.Filled.Fullscreen, tx("Tam ekran (F)", "Full screen (F)")) }
        }
    }
}

@Composable
private fun TrackMenu(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tracks: () -> List<Track>, current: () -> Int, select: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(icon, label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val list = runCatching { tracks() }.getOrDefault(emptyList())
            val selected = runCatching { current() }.getOrDefault(-1)
            if (list.isEmpty()) DropdownMenuItem(text = { Text(tx("Seçenek yok", "None available")) }, onClick = { open = false })
            for (t in list) {
                DropdownMenuItem(text = { Text((if (t.id == selected) "✓ " else "   ") + t.name) }, onClick = { select(t.id); open = false })
            }
        }
    }
}
