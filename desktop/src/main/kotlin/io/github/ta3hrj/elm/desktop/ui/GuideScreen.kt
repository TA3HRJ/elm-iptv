package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.EpgProgramme
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.ChannelGroup

/** Where programmes sit on the guide's time line: [DP_PER_MINUTE] wide per minute from [start]. */
data class GuideWindow(val start: Long, val end: Long) {
    fun offsetMinutes(time: Long): Int = ((time.coerceIn(start, end) - start) / 60_000).toInt()
    fun widthMinutes(p: EpgProgramme): Int = offsetMinutes(p.stop) - offsetMinutes(p.start)
    val minutes: Int get() = ((end - start) / 60_000).toInt()

    companion object {
        const val DP_PER_MINUTE = 4
        private const val HALF_HOUR = 30 * 60_000L

        /** From the half hour two hours ago (for the archive) to a day ahead. */
        fun around(now: Long): GuideWindow {
            val start = (now - 2 * 60 * 60_000L) / HALF_HOUR * HALF_HOUR
            return GuideWindow(start, start + 26 * 60 * 60_000L)
        }
    }
}

@Composable
fun GuideScreen(state: AppState) {
    val now = rememberNow()
    val window = remember(now / (30 * 60_000L)) { GuideWindow.around(now) }
    val groups = state.groups()
    var selectedKey by remember { mutableStateOf("#fav") }
    var selected by remember { mutableStateOf<Pair<Channel, EpgProgramme>?>(null) }
    val group = groups.firstOrNull { it.key == selectedKey }?.takeIf { state.channels(it).isNotEmpty() }
        ?: groups.firstOrNull { it is ChannelGroup.Category } ?: groups.firstOrNull()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(window) {
        // Start at "now" with a little of the past in view.
        val minutes = window.offsetMinutes(now) - 20
        hScroll.scrollTo(with(density) { (minutes * GuideWindow.DP_PER_MINUTE).dp.roundToPx() })
    }

    if (state.content == null) {
        EmptyNote(state.busy ?: tx("Liste yüklenmedi.", "No playlist loaded."))
        return
    }
    Row(Modifier.fillMaxSize()) {
        SidePanel(Modifier.width(220.dp)) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                items(groups.filter { it !is ChannelGroup.All }, key = { it.key }) { g ->
                    GroupRow(g.title, null, selected = g.key == group?.key) { selectedKey = g.key }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            ProgrammeInfo(state, selected, now)
            if (state.guide.linkedChannels == 0) {
                Text(
                    tx("Bu liste için rehber bilgisi yok. Listeler ekranında bir XMLTV adresi girebilirsiniz.", "No guide data for this playlist. You can enter an XMLTV address on the Playlists screen."),
                    color = Muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Time line
            Row(Modifier.fillMaxWidth().height(28.dp)) {
                Spacer(Modifier.width(CHANNEL_COLUMN))
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    Box(Modifier.width((window.minutes * GuideWindow.DP_PER_MINUTE).dp).height(28.dp)) {
                        var t = window.start
                        while (t < window.end) {
                            Text(hhmm(t), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.offset(x = (window.offsetMinutes(t) * GuideWindow.DP_PER_MINUTE).dp + 4.dp, y = 6.dp))
                            t += 30 * 60_000L
                        }
                        Box(Modifier.offset(x = (window.offsetMinutes(now) * GuideWindow.DP_PER_MINUTE).dp).width(2.dp).fillMaxHeight().background(Accent))
                    }
                }
            }
            val channels = group?.let { state.channels(it) }.orEmpty()
            Box(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState) {
                    items(channels, key = { it.streamUrl }) { channel ->
                        Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
                            Row(
                                Modifier.width(CHANNEL_COLUMN).fillMaxHeight().clickable { state.playChannel(channel, channels) }.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Logo(channel.logo, channel.name, Modifier.size(width = 44.dp, height = 30.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                                ProgrammeLane(state, channel, window, now, selected?.second) { selected = channel to it }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

private val CHANNEL_COLUMN: Dp = 200.dp
private val ROW_HEIGHT: Dp = 52.dp

@Composable
private fun ProgrammeLane(state: AppState, channel: Channel, window: GuideWindow, now: Long, selected: EpgProgramme?, onSelect: (EpgProgramme) -> Unit) {
    val programmes = state.guide.programmes(channel.streamUrl).filter { it.stop > window.start && it.start < window.end }
    Box(Modifier.width((window.minutes * GuideWindow.DP_PER_MINUTE).dp).fillMaxHeight()) {
        if (programmes.isEmpty()) {
            Box(Modifier.width((window.minutes * GuideWindow.DP_PER_MINUTE).dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Panel).clickable { state.playChannel(channel, listOf(channel)) }.padding(8.dp)) {
                Text(tx("Program bilgisi yok", "No programme information"), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        for (p in programmes) {
            val onAir = p.start <= now && now < p.stop
            val archive = p.stop <= now && state.archiveUrl(channel, p, now) != null
            val width = (window.widthMinutes(p) * GuideWindow.DP_PER_MINUTE).dp - 2.dp
            if (width <= 0.dp) continue
            Box(
                Modifier
                    .offset(x = (window.offsetMinutes(p.start) * GuideWindow.DP_PER_MINUTE).dp)
                    .width(width)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (onAir) Color(0xFF1F3550) else Panel)
                    .then(if (p == selected) Modifier.border(2.dp, Accent, RoundedCornerShape(4.dp)) else Modifier)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Column {
                    Text((if (archive) "⏪ " else "") + p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text("${hhmm(p.start)}–${hhmm(p.stop)}", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

/** The chosen programme: its text, and what can be done with it (watch live, watch from the archive). */
@Composable
private fun ProgrammeInfo(state: AppState, selected: Pair<Channel, EpgProgramme>?, now: Long) {
    Column(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (selected == null) {
            Text(tx("TV Rehberi", "TV Guide"), style = MaterialTheme.typography.headlineSmall)
            Text(tx("Bir programa tıklayın: açıklaması burada görünür. Kanal adına tıklamak kanalı açar.", "Click a programme to see its description here. Clicking a channel's name opens it."), color = Muted)
            return@Column
        }
        val (channel, p) = selected
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${channel.name}  ·  ${hhmm(p.start)}–${hhmm(p.stop)}", color = Accent, style = MaterialTheme.typography.bodySmall)
            }
            when {
                p.start <= now && now < p.stop -> TextButton(onClick = { state.playChannel(channel, listOf(channel)) }) { Text(tx("Canlı izle", "Watch live")) }
                p.stop <= now && state.archiveUrl(channel, p, now) != null -> TextButton(onClick = { state.playArchive(channel, p) }) { Text(tx("Arşivden izle", "Watch from the archive")) }
            }
        }
        p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}
