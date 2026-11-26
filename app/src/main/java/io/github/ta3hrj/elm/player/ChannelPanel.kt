package io.github.ta3hrj.elm.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import io.github.ta3hrj.elm.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelProgramme
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.ui.ChannelLogo
import io.github.ta3hrj.elm.ui.ListScrollbar
import io.github.ta3hrj.elm.ui.listItemFocusBorder
import io.github.ta3hrj.elm.ui.listItemFocusColors
import io.github.ta3hrj.elm.ui.listItemFocusScale

/** A category shown in the in-player channel list. */
internal data class PanelGroup(val title: String, val query: ChannelQuery)

/**
 * Channel list over the running stream: up/down picks a channel, left/right changes category,
 * OK switches to the channel, Back closes the list.
 */
@Composable
internal fun ChannelPanel(
    group: PanelGroup?,
    hasMoreGroups: Boolean,
    channels: List<ChannelEntity>,
    currentChannelId: Long,
    now: Map<String, ChannelProgramme>,
    onGroupStep: (Int) -> Unit,
    onSelect: (ChannelEntity) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val listState = rememberLazyListState()
    val start = channels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
    val focus = remember(channels) { FocusRequester() }
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            listState.scrollToItem((start - 3).coerceAtLeast(0))
            runCatching { focus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(600.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color(0xF20B0F15), Color(0xE60B0F15), Color(0x000B0F15))))
                .padding(start = 32.dp, top = 28.dp, end = 48.dp, bottom = 20.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onGroupStep(-1); true }
                        Key.DirectionRight -> { onGroupStep(+1); true }
                        else -> false
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasMoreGroups) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFF7FB8FF))
                Text(
                    group?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 6.dp),
                )
                if (hasMoreGroups) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF7FB8FF))
                Spacer(Modifier.weight(1f))
                Text(tx("${channels.size} kanal", "${channels.size} channels"), color = Color(0xFF9AA4B2), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            if (channels.isEmpty()) {
                Text(tx("Bu kategoride kanal yok.", "No channels in this category."), color = Color(0xFF9AA4B2))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                    val programme = now[channel.streamUrl]
                    val playing = channel.id == currentChannelId
                    ListItem(
                        selected = playing,
                        onClick = { onSelect(channel) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(channel.number.toString(), color = Color(0xFF7FB8FF), fontSize = 13.sp, modifier = Modifier.width(40.dp))
                                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                            }
                        },
                        supportingContent = programme?.let { p ->
                            {
                                Column {
                                    Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    val total = (p.stop - p.start).coerceAtLeast(1)
                                    val progress = ((System.currentTimeMillis() - p.start).toFloat() / total).coerceIn(0f, 1f)
                                    Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.6f).height(3.dp).background(Color(0x33FFFFFF))) {
                                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFF4FA3FF)))
                                    }
                                }
                            }
                        },
                        leadingContent = { ChannelLogo(channel.name, channel.logo, width = 56.dp, height = 36.dp, fontSize = 12.sp) },
                        trailingContent = if (playing) {
                            { Text("▶", color = Color(0xFF4FA3FF)) }
                        } else {
                            null
                        },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                        modifier = if (index == start) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            ListScrollbar(listState, Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tx("◀ ▶ kategori  ·  OK: izle  ·  Geri: kapat", "◀ ▶ category  ·  OK: watch  ·  Back: close"),
                color = Color(0xFF8A94A4),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
