package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.Icon
import io.github.ta3hrj.elm.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.GuideRepository
import java.util.Calendar

private val ArchiveMuted = Color(0xFF9AA4B2)

/** Past programmes of one catch-up channel, day by day; OK plays a programme from the archive. */
@Composable
fun ArchiveScreen(viewModel: MainViewModel, onPlayLive: (ChannelEntity) -> Unit, onBack: () -> Unit) {
    val channel by viewModel.archiveChannel.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val current = channel
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var entries by remember(current.id) { mutableStateOf<List<GuideRepository.ArchiveEntry>?>(null) }
    LaunchedEffect(current.id) {
        entries = runCatching { viewModel.archive(current) }.getOrElse { emptyList() }
    }
    val days = viewModel.archiveDays(current)
    var dayIndex by remember(current.id) { mutableIntStateOf(0) }
    val firstDay = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstDay.requestFocus() } }

    val now = System.currentTimeMillis()
    val dayStart = startOfDay(now, dayIndex)
    val dayEnd = dayStart + DAY_MS
    // Newest first, so the programme that just ended is at the top.
    val shown = entries?.filter { it.programme.start in dayStart until dayEnd }?.sortedByDescending { it.programme.start }

    Column(Modifier.fillMaxSize().screenPadding(48.dp, 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(current.name, current.logo, width = 72.dp, height = 46.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(current.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tx("Arşiv · son $days gün", "Archive · last $days days"), color = ArchiveMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onPlayLive(current) }) { IconLabel(Icons.Filled.LiveTv, tx("Canlı izle", "Watch live")) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items((0..days).toList()) { index ->
                val label = dayLabel(startOfDay(now, index) + 12 * 3_600_000L, now)
                val modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstDay) else Modifier)
                    .onFocusChanged { if (it.isFocused) dayIndex = index }
                ChoiceButton(label, selected = index == dayIndex, modifier = modifier, check = false) { dayIndex = index }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            shown == null -> SkeletonList(rows = 7, rowHeight = 52.dp, withLogo = false)
            shown.isEmpty() -> Text(tx("Bu gün için arşiv bilgisi yok.", "No archive information for this day."), color = ArchiveMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { "${it.programme.start}|${it.programme.title}" }) { entry ->
                    val programme = entry.programme
                    val airing = now in programme.start until programme.stop
                    ListItem(
                        selected = false,
                        enabled = entry.playable,
                        onClick = { viewModel.playArchive(current, programme) },
                        headlineContent = { Text(programme.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(timeRange(programme.start, programme.stop), programme.description).joinToString("  ·  "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                        trailingContent = {
                            Text(
                                when {
                                    airing -> tx("● Şimdi · baştan izle", "● Now · from the start")
                                    !entry.playable -> tx("Arşivde yok", "Not archived")
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (airing) Color(0xFFFF6B6B) else ArchiveMuted,
                            )
                        },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                    )
                }
            }
        }
        message?.let { Box(Modifier.padding(top = 6.dp)) { Text(it, color = ArchiveMuted, style = MaterialTheme.typography.bodySmall) } }
    }
}

private const val DAY_MS = 24 * 3_600_000L

/** Local midnight [daysAgo] days before [now]. */
private fun startOfDay(now: Long, daysAgo: Int): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
}.timeInMillis
