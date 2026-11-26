package io.github.ta3hrj.elm.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import io.github.ta3hrj.elm.ui.touch.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelProgramme
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.reminders.Reminders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GuideMuted = Color(0xFF9AA4B2)
private val CellColor = Color(0xFF1A212B)
private val NowCellColor = Color(0xFF213A5C)
private val ChannelColumn = 230.dp

private data class Segment(val programme: ChannelProgramme?, val start: Long, val stop: Long)

private data class Focused(val channel: ChannelEntity, val programme: ChannelProgramme?, val query: ChannelQuery)

private enum class Edge { Start, End, Now }

@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onPlay: (ChannelEntity, ChannelQuery) -> Unit,
    onOpenArchive: (ChannelEntity) -> Unit = {},
    onOpenEpgMatch: (ChannelEntity) -> Unit = {},
    /** Shows a live preview of the focused channel (used when the guide is the start screen). */
    preview: Boolean = false,
    /** Shows the app emblem in the header, opening the About screen (start screen only). */
    onOpenAbout: (() -> Unit)? = null,
    /** Extra header buttons; `compact` asks for icon-only buttons when the header is short of room. */
    headerActions: @Composable (compact: Boolean) -> Unit = {},
    /** The Settings button: kept at the right edge and never scrolled out of view. */
    settingsAction: (@Composable (compact: Boolean) -> Unit)? = null,
) {
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val rows by viewModel.guideRows.collectAsStateWithLifecycle()
    val programmes by viewModel.guideProgrammes.collectAsStateWithLifecycle()
    val windowStart by viewModel.guideWindowStart.collectAsStateWithLifecycle()
    val updating by viewModel.guideUpdating.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedQuery by viewModel.selectedQuery.collectAsStateWithLifecycle()
    val reminderKeys by viewModel.reminderKeys.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val windowEnd = windowStart + MainViewModel.GUIDE_WINDOW_MS
    val now = System.currentTimeMillis()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var focused by remember { mutableStateOf<Focused?>(null) }
    // The start screen's live preview; its player goes on to the full-screen player when a channel is chosen.
    val previewSession = if (preview) rememberPreviewSession(viewModel, focused?.channel) else null
    var focusedRow by remember { mutableIntStateOf(0) }
    // Whether the focused programme is the first / last one of its row in the shown time window.
    var focusAtStart by remember { mutableStateOf(false) }
    var focusAtEnd by remember { mutableStateOf(false) }
    var pendingEdge by remember { mutableStateOf<Pair<Int, Edge>?>(null) }
    var options by remember { mutableStateOf<Focused?>(null) }
    val listState = rememberLazyListState()

    val focusedGroup = focused?.let { f -> f.channel.groupTitle }

    /** Scrolls so the row sits a little below the top (its neighbours stay visible) and focuses its current programme. */
    fun jumpTo(row: Int) {
        if (row !in rows.indices) return
        scope.launch {
            listState.scrollToItem((row - 2).coerceAtLeast(0))
            pendingEdge = row to Edge.Now
        }
    }

    /** Row of a channel in its own category (not the favorites copy at the top). */
    fun rowOf(streamUrl: String?): Int =
        if (streamUrl == null) -1 else rows.indexOfFirst { it is MainViewModel.GuideRow.Row && !it.favorite && it.channel.streamUrl == streamUrl }

    // Open on the channel being watched (or watched last); otherwise on the category selected on the
    // home screen. The list continues through every category either way.
    var shownChannel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.resetGuideWindow() }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isEmpty()) return@LaunchedEffect
        val last = viewModel.lastWatchedStreamUrl()
        shownChannel = last
        val watched = rowOf(last)
        val target = if (watched >= 0) {
            watched
        } else {
            val startQuery = viewModel.activeQuery().takeUnless { it is ChannelQuery.Search }
            val heading = rows.indexOfFirst { it is MainViewModel.GuideRow.Heading && it.query == startQuery }.coerceAtLeast(0)
            (heading + 1).coerceAtMost(rows.lastIndex)
        }
        jumpTo(target)
    }
    // Back from the player (possibly on another channel): follow it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val last = viewModel.lastWatchedStreamUrl()
        if (rows.isNotEmpty() && last != null && last != shownChannel) {
            shownChannel = last
            rowOf(last).takeIf { it >= 0 }?.let { jumpTo(it) }
        }
    }

    // Remote shortcuts: ⏪ ⏩ / CH± / page keys jump a category, digits jump to a channel number.
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_500)
        val number = digits.toIntOrNull()
        digits = ""
        val row = rows.indexOfFirst { it is MainViewModel.GuideRow.Row && !it.favorite && it.channel.number == number }
        if (row >= 0) jumpTo(row) else viewModel.showMessage(tx("$number numaralı kanal yok", "No channel $number"))
    }
    fun jumpCategory(step: Int) {
        val headings = rows.indices.filter { rows[it] is MainViewModel.GuideRow.Heading }
        if (headings.isEmpty()) return
        val current = headings.lastOrNull { it <= focusedRow } ?: headings.first()
        val position = headings.indexOf(current)
        val target = headings.getOrNull(position + step) ?: return
        jumpTo((target + 1).coerceAtMost(rows.lastIndex))
    }

    Column(Modifier.fillMaxSize().screenPadding(32.dp, 20.dp)) {
        // Header: title and window controls. The buttons scroll if they do not fit (large text) and
        // the title gives way first; Settings always stays on screen.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val headerWidth = maxWidth
        val navScroll = rememberScrollState()
        val compact = rememberHeaderFit(if (onOpenAbout != null) "guide-home" else "guide", maxWidth, navScroll) >= 1
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onOpenAbout != null) {
                LogoButton(null, onClick = onOpenAbout)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (preview && onOpenAbout == null) "ELM IPTV" else tx("TV Rehberi", "TV Guide"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        playlist?.name,
                        focusedGroup,
                        if (digits.isNotEmpty()) tx("Kanal: $digits", "Channel: $digits") else null,
                        if (updating.isNotEmpty()) tx("rehber güncelleniyor…", "updating guide…") else null,
                        tx("⏪ ⏩ kategori atla · rakamlar: kanal no", "⏪ ⏩ skip category · digits: channel no."),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = GuideMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.widthIn(max = headerWidth * 0.72f).horizontalScroll(navScroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderClock(showDate = !compact)
                Spacer(Modifier.width(8.dp))
                headerActions(compact)
                OutlinedButton(onClick = { viewModel.shiftGuideWindow(-1) }) { Text(tx("◀ 1 saat", "◀ 1 h")) }
                Button(onClick = { viewModel.resetGuideWindow() }) { Text(tx("Şimdi", "Now")) }
                OutlinedButton(onClick = { viewModel.shiftGuideWindow(+1) }) { Text(tx("1 saat ▶", "1 h ▶")) }
            }
            if (settingsAction != null) {
                Spacer(Modifier.width(8.dp))
                settingsAction(compact)
            }
        }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { DetailsPanel(focused, now, viewModel, reminderKeys, message) }
            if (preview) {
                Spacer(Modifier.width(12.dp))
                PreviewPlayer(previewSession!!)
            }
        }
        Spacer(Modifier.height(10.dp))
        TimeRuler(windowStart)
        Spacer(Modifier.height(4.dp))

        if (rows.isEmpty()) {
            if ((playlist?.channelCount ?: 0) > 0) {
                SkeletonList(rows = 7, rowHeight = 58.dp)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tx("Bu kategoride kanal yok.", "No channels in this category."), color = GuideMuted)
                }
            }
            return@Column
        }
        if (programmes.isEmpty() && updating.isEmpty()) {
            Text(
                if ((playlist?.programmeCount ?: 0) == 0) {
                    tx("Bu liste için henüz rehber bilgisi yok. Listeler ekranından rehberi güncelleyebilir veya bir XMLTV adresi girebilirsiniz.", "No guide data for this playlist yet. Update the guide or enter an XMLTV address on the Playlists screen.")
                } else {
                    tx("Bu kanallar için bu saat aralığında program bilgisi yok.", "No programme information for these channels in this time range.")
                },
                color = GuideMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    digitFor(event.key)?.let { digit ->
                        if (digits.length < 5) digits += digit
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.MediaFastForward, Key.ChannelDown, Key.PageDown -> { jumpCategory(+1); true }
                        Key.MediaRewind, Key.ChannelUp, Key.PageUp -> { jumpCategory(-1); true }
                        // At the right/left edge of the grid, move the time window. Asking the focus to move on
                        // from the last programme would jump to the header buttons above (they lie further right).
                        Key.DirectionRight -> {
                            if (focusAtEnd || !focusManager.moveFocus(FocusDirection.Right)) {
                                if (viewModel.shiftGuideWindow(+1)) pendingEdge = focusedRow to Edge.Start
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (focusAtStart || !focusManager.moveFocus(FocusDirection.Left)) {
                                if (viewModel.shiftGuideWindow(-1)) pendingEdge = focusedRow to Edge.End
                            }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
                if (row is MainViewModel.GuideRow.Heading) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF7FB8FF),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    return@itemsIndexed
                }
                val channel = (row as MainViewModel.GuideRow.Row).channel
                val rowQuery = row.query
                val segments = remember(programmes, channel.streamUrl, windowStart) {
                    segmentsFor(programmes[channel.streamUrl].orEmpty(), windowStart, windowEnd)
                }
                val firstRequester = remember { FocusRequester() }
                val lastRequester = remember { FocusRequester() }
                val nowRequester = remember { FocusRequester() }
                LaunchedEffect(pendingEdge, segments) {
                    val pending = pendingEdge ?: return@LaunchedEffect
                    if (pending.first == index) {
                        val requester = when (pending.second) {
                            Edge.Start -> firstRequester
                            Edge.End -> lastRequester
                            Edge.Now -> nowRequester
                        }
                        runCatching { requester.requestFocus() }
                        pendingEdge = null
                    }
                }
                val focusable = segments.filter { it.programme != null }.ifEmpty { segments.take(1) }
                val nowCell = focusable.firstOrNull { now in it.start until it.stop } ?: focusable.first()

                Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelCell(channel)
                    Row(Modifier.weight(1f).fillMaxHeight()) {
                        segments.forEach { segment ->
                            val isFocusable = segment in focusable
                            var cellModifier: Modifier = Modifier
                            if (segment === focusable.first()) cellModifier = cellModifier.focusRequester(firstRequester)
                            if (segment === focusable.last() && focusable.size > 1) cellModifier = cellModifier.focusRequester(lastRequester)
                            if (focusable.size == 1 && segment === focusable.first()) {
                                cellModifier = Modifier.focusRequester(firstRequester).focusRequester(lastRequester)
                            }
                            if (segment === nowCell) cellModifier = cellModifier.focusRequester(nowRequester)
                            val programme = segment.programme
                            val archived = programme != null && viewModel.isArchived(channel, programme, now)
                            val upcoming = programme != null && programme.start > now
                            val reminded = programme != null && Reminders.key(channel.streamUrl, programme.start) in reminderKeys
                            ProgrammeCell(
                                segment = segment,
                                weight = (segment.stop - segment.start).toFloat(),
                                isNow = programme != null && now in segment.start until segment.stop,
                                focusable = isFocusable,
                                emptyLabel = if (segments.all { it.programme == null }) tx("Program bilgisi yok", "No programme information") else "",
                                marker = when {
                                    reminded -> "⏰ "
                                    archived -> "⟲ "
                                    else -> ""
                                },
                                modifier = cellModifier.onMenuKey { options = Focused(channel, programme, rowQuery) },
                                onFocused = {
                                    focused = Focused(channel, programme, rowQuery)
                                    focusedRow = index
                                    focusAtStart = segment === focusable.first()
                                    focusAtEnd = segment === focusable.last()
                                },
                                onClick = {
                                    when {
                                        archived -> viewModel.playArchive(channel, programme)
                                        upcoming -> viewModel.toggleReminder(channel, programme)
                                        else -> {
                                            previewSession?.handOff(channel)
                                            onPlay(channel, rowQuery)
                                        }
                                    }
                                },
                                onLongClick = { options = Focused(channel, programme, rowQuery) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ListScrollbar(listState, Modifier.padding(bottom = 16.dp))
        }
    }

    options?.let { target ->
        val programme = target.programme
        val now2 = System.currentTimeMillis()
        val items = buildList {
            add(DialogOption(tx("Canlı izle", "Watch live"), Icons.Filled.PlayArrow) {
                previewSession?.handOff(target.channel)
                onPlay(target.channel, target.query)
            })
            if (programme != null && viewModel.isArchived(target.channel, programme, now2)) {
                add(DialogOption(tx("Arşivden izle", "Watch from archive"), Icons.Filled.History) { viewModel.playArchive(target.channel, programme) })
            }
            if (programme != null && programme.start > now2) {
                val set = Reminders.key(target.channel.streamUrl, programme.start) in reminderKeys
                add(DialogOption(if (set) tx("Hatırlatıcıyı kaldır", "Remove reminder") else tx("Hatırlatıcı kur", "Set reminder"), if (set) Icons.Filled.AlarmOff else Icons.Filled.Alarm) {
                    viewModel.toggleReminder(target.channel, programme)
                })
            }
            if (viewModel.hasArchive(target.channel)) {
                add(DialogOption(tx("Kanal arşivi", "Channel archive"), Icons.Filled.DateRange) { onOpenArchive(target.channel) })
            }
            add(DialogOption(tx("Rehberi eşleştir…", "Match to the guide…"), Icons.Filled.Link) { onOpenEpgMatch(target.channel) })
            if (programme != null && programme.stop > now2) {
                add(DialogOption(tx("Kaydet", "Record"), Icons.Filled.FiberManualRecord) { viewModel.recordProgramme(target.channel, programme) })
                add(DialogOption(tx("Dizi kaydı (tüm bölümler)", "Record series (all episodes)"), Icons.Filled.Repeat) { viewModel.recordSeries(target.channel, programme) })
            }
        }
        OptionsDialog(
            title = programme?.title ?: target.channel.name,
            subtitle = target.channel.name + (programme?.let { "  ·  " + timeRange(it.start, it.stop) } ?: ""),
            options = items,
            onDismiss = { options = null },
        )
    }
}

@Composable
private fun DetailsPanel(
    focused: Focused?,
    now: Long,
    viewModel: MainViewModel,
    reminderKeys: Set<String>,
    message: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF151B23))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        val programme = focused?.programme
        if (focused == null) {
            Text(tx("Bir programın üzerine gelin. OK ile kanalı açın.", "Move to a programme. OK opens the channel."), color = GuideMuted)
            return@Column
        }
        Text(
            programme?.title ?: focused.channel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(focused.channel.name)
                if (programme != null) {
                    append("  ·  ${timeRange(programme.start, programme.stop)}")
                    when {
                        now >= programme.stop -> append(tx("  ·  yayınlandı", "  ·  aired"))
                        now >= programme.start -> append(tx("  ·  şimdi, ${(programme.stop - now) / 60_000 + 1} dk kaldı", "  ·  now, ${(programme.stop - now) / 60_000 + 1} min left"))
                        else -> append("  ·  ${dayLabel(programme.start, now)}")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7FB8FF),
            maxLines = 1,
        )
        programme?.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = GuideMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val hint = when {
            message != null -> message
            programme == null -> tx("OK: canlı izle", "OK: watch live")
            programme.start > now ->
                if (Reminders.key(focused.channel.streamUrl, programme.start) in reminderKeys) {
                    tx("⏰ Hatırlatıcı kurulu · OK: kaldır", "⏰ Reminder set · OK: remove")
                } else {
                    tx("OK veya Menü (☰): hatırlatıcı kur", "OK or Menu (☰): set reminder")
                }
            viewModel.isArchived(focused.channel, programme, now) -> tx("⟲ OK: arşivden izle", "⟲ OK: watch from archive")
            programme.stop <= now -> tx("Bu kanalda geriye dönük izleme yok", "No catch-up on this channel")
            else -> tx("OK: canlı izle", "OK: watch live")
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD18A), maxLines = 1)
    }
}

@Composable
private fun TimeRuler(windowStart: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(dayLabel(windowStart, System.currentTimeMillis()), color = GuideMuted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(ChannelColumn))
        Row(Modifier.weight(1f)) {
            repeat((MainViewModel.GUIDE_WINDOW_MS / (MainViewModel.HOUR_MS / 2)).toInt()) { slot ->
                Text(
                    clock(windowStart + slot * MainViewModel.HOUR_MS / 2),
                    style = MaterialTheme.typography.labelMedium,
                    color = GuideMuted,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: ChannelEntity) {
    Row(
        Modifier.width(ChannelColumn).fillMaxHeight().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.name, channel.logo, width = 56.dp, height = 36.dp, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RowScope.ProgrammeCell(
    segment: Segment,
    weight: Float,
    isNow: Boolean,
    focusable: Boolean,
    emptyLabel: String,
    marker: String,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val programme = segment.programme
    val base = Modifier.weight(weight).fillMaxHeight().padding(horizontal = 2.dp)
    if (!focusable) {
        Box(base)
        return
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = base.then(modifier).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = surfaceFocusBorder(RoundedCornerShape(6.dp)),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Accent.copy(alpha = 0.5f), 8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isNow) NowCellColor else CellColor,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF4FA3FF),
            focusedContentColor = Color(0xFF0E1116),
        ),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                programme?.let { marker + it.title } ?: emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (programme != null) {
                Text(
                    timeRange(programme.start, programme.stop),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Splits [from, to) into programme cells and gaps, clipping programmes that overlap the edges. */
private fun segmentsFor(programmes: List<ChannelProgramme>, from: Long, to: Long): List<Segment> {
    val result = ArrayList<Segment>()
    var cursor = from
    for (p in programmes.sortedBy { it.start }) {
        val start = maxOf(p.start, cursor)
        val stop = minOf(p.stop, to)
        if (stop <= start) continue
        if (start - cursor >= MIN_GAP_MS) result += Segment(null, cursor, start)
        result += Segment(p, if (start - cursor < MIN_GAP_MS) cursor else start, stop)
        cursor = stop
        if (cursor >= to) break
    }
    if (cursor < to) {
        if (result.isNotEmpty() && to - cursor < MIN_GAP_MS) {
            val last = result.removeAt(result.lastIndex)
            result += last.copy(stop = to)
        } else {
            result += Segment(null, cursor, to)
        }
    }
    return result
}

private const val MIN_GAP_MS = 3 * 60_000L

private fun clock(time: Long): String = SimpleDateFormat("HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(time))

fun timeRange(start: Long, stop: Long): String = "${clock(start)}–${clock(stop)}"

fun dayLabel(time: Long, now: Long): String {
    val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
    return when (day.format(Date(time))) {
        day.format(Date(now)) -> tx("Bugün", "Today")
        day.format(Date(now + 86_400_000L)) -> tx("Yarın", "Tomorrow")
        day.format(Date(now - 86_400_000L)) -> tx("Dün", "Yesterday")
        else -> SimpleDateFormat("d MMMM EEEE", io.github.ta3hrj.elm.core.Lang.locale).format(Date(time))
    }
}
