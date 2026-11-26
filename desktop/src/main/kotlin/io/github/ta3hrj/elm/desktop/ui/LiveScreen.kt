package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.ChannelGroup
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** The clock the lists use for "now"; ticks every 30 s so the programme bars move. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun hhmm(time: Long): String = SimpleDateFormat("HH:mm").format(Date(time))

@Composable
fun LiveScreen(state: AppState) {
    val groups = state.groups()
    var selectedKey by remember { mutableStateOf("#all") }
    var query by remember { mutableStateOf("") }
    var archiveFor by remember { mutableStateOf<Channel?>(null) }
    val group = groups.firstOrNull { it.key == selectedKey } ?: groups.firstOrNull { it is ChannelGroup.All }
    val now = rememberNow()

    if (state.content == null) {
        EmptyNote(state.busy ?: tx("Liste yüklenemedi. Listeler ekranından kontrol edin.", "The playlist did not load. Check it on the Playlists screen."))
        return
    }
    Row(Modifier.fillMaxSize()) {
        SidePanel(Modifier.width(260.dp)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                items(groups, key = { it.key }) { g ->
                    val count = if (g is ChannelGroup.Category) null else state.channels(g).size
                    GroupRow(g.title, count, selected = g.key == group?.key) {
                        selectedKey = g.key
                        query = ""
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(tx("Bu listede ara…", "Search this list…")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val channels = group?.let { state.channels(it, query) }.orEmpty()
            if (channels.isEmpty()) {
                EmptyNote(
                    when {
                        query.isNotBlank() -> tx("\"$query\" ile eşleşen kanal yok.", "No channels match \"$query\".")
                        group is ChannelGroup.Favorites -> tx("Henüz favori yok. Bir kanala sağ tıklayıp ekleyin.", "No favorites yet. Right-click a channel to add it.")
                        else -> tx("Bu listede kanal yok.", "No channels in this list.")
                    },
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(end = 12.dp)) {
                        items(channels, key = { it.streamUrl }) { channel ->
                            ChannelRow(state, channel, now, onPlay = { state.playChannel(channel, channels) }, onArchive = { archiveFor = channel })
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
    archiveFor?.let { channel -> ArchiveDialog(state, channel, now) { archiveFor = null } }
}

@Composable
fun GroupRow(title: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = if (selected) Accent else TextPrimary)
        count?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = Muted) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(state: AppState, channel: Channel, now: Long, onPlay: () -> Unit, onArchive: () -> Unit) {
    val favorite = state.isFavorite(channel)
    val programme = state.guide.now(channel.streamUrl, now)
    val hasArchive = io.github.ta3hrj.elm.core.Catchup.availableDays(channel.catchupDays, channel.catchupType) > 0
    ContextMenuArea(items = {
        listOfNotNull(
            ContextMenuItem(if (favorite) tx("Favorilerden çıkar", "Remove from favorites") else tx("Favorilere ekle", "Add to favorites")) { state.toggleFavorite(channel) },
            if (hasArchive) ContextMenuItem(tx("Kanal arşivi", "Channel archive"), onArchive) else null,
        )
    }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text((channel.number ?: "").toString(), style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.width(40.dp))
            Logo(channel.logo, channel.name, Modifier.size(width = 56.dp, height = 36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (programme != null) {
                    Text("${hhmm(programme.start)}  ${programme.title}", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { ((now - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.5f).height(3.dp),
                    )
                }
            }
            if (hasArchive) {
                IconButton(onClick = onArchive) { Icon(Icons.Filled.History, contentDescription = tx("Arşiv", "Archive"), tint = Muted) }
            }
            IconButton(onClick = { state.toggleFavorite(channel) }) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = tx("Favori", "Favorite"), tint = if (favorite) Color(0xFFFFC857) else Muted)
            }
        }
    }
}

/** A channel's past programmes that can be played from the archive, newest first. */
@Composable
fun ArchiveDialog(state: AppState, channel: Channel, now: Long, onDismiss: () -> Unit) {
    val past = state.guide.programmes(channel.streamUrl).filter { it.stop <= now && state.archiveUrl(channel, it, now) != null }.reversed()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tx("Kapat", "Close")) } },
        title = { Text(tx("Arşiv: ", "Archive: ") + channel.name) },
        text = {
            if (past.isEmpty()) {
                Text(tx("Arşivde izlenebilir program yok (rehber bilgisi gerekir).", "Nothing to watch in the archive (needs guide data)."))
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    items(past) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { state.playArchive(channel, p); onDismiss() }.padding(vertical = 6.dp),
                        ) {
                            Text(SimpleDateFormat("dd.MM HH:mm").format(Date(p.start)), color = Muted, modifier = Modifier.width(96.dp))
                            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}
