package io.github.ta3hrj.elm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import io.github.ta3hrj.elm.core.EpgNames
import io.github.ta3hrj.elm.core.SearchText
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.GuideRepository

private val MatchMuted = Color(0xFF9AA4B2)

/**
 * Matches one channel to the guide by hand: the guide's channels with the likeliest first, a search
 * box, "back to automatic" and "no guide". The choice is kept across guide updates.
 */
@Composable
fun EpgMatchScreen(viewModel: MainViewModel, channel: ChannelEntity, onDone: () -> Unit) {
    val updating by viewModel.guideUpdating.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<GuideRepository.MatchInfo?>(null) }
    var search by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val isUpdating = channel.playlistId in updating
    BackHandler(onBack = onDone)
    // Reloads after a guide update, which may bring the guide's channel list.
    LaunchedEffect(channel.id, isUpdating) { if (!isUpdating) info = viewModel.epgMatchInfo(channel) }
    LaunchedEffect(info != null) { if (info != null) runCatching { first.requestFocus() } }

    val data = info
    val byKey = data?.sources?.associateBy { it.epgKey }.orEmpty()
    val currentName = data?.current?.let { byKey[it]?.name ?: it }
    val status = when {
        data == null -> tx("Yükleniyor…", "Loading…")
        data.override == "" -> tx("Rehber yok (elle seçildi)", "No guide (chosen by hand)")
        data.override != null -> tx("Elle seçildi: ", "Chosen by hand: ") + (currentName ?: data.override)
        currentName != null -> tx("Otomatik eşleşti: ", "Matched automatically: ") + currentName
        else -> tx("Eşleşme yok", "Not matched")
    }
    val query = SearchText.normalize(search)
    val shown = data?.sources.orEmpty()
        .let { all -> if (query.isEmpty()) all else all.filter { SearchText.normalize(it.name).contains(query) || SearchText.normalize(it.epgKey).contains(query) } }
        .take(400)

    Column(Modifier.fillMaxSize().screenPadding(56.dp, 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx("Rehberi eşleştir", "Match to the guide"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${channel.number}  ${channel.name}  ·  $status", color = MatchMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onDone) { Text(tx("Geri", "Back")) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (data?.override != null) {
                OutlinedButton(onClick = { viewModel.setEpgMatch(channel, null, null); onDone() }) {
                    IconLabel(Icons.Filled.AutoMode, tx("Otomatik eşleştirmeye dön", "Back to automatic"))
                }
            }
            if (data?.override != "") {
                OutlinedButton(onClick = { viewModel.setEpgMatch(channel, "", null); onDone() }) {
                    IconLabel(Icons.Filled.TvOff, tx("Rehber yok", "No guide"))
                }
            }
            Box(Modifier.weight(1f)) {
                TvTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = tx("Rehberde kanal ara…", "Search the guide's channels…"),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            data == null -> Text(tx("Yükleniyor…", "Loading…"), color = MatchMuted)
            data.sources.isEmpty() -> Column {
                Text(
                    if (isUpdating) {
                        tx("Rehber indiriliyor…", "Downloading the guide…")
                    } else {
                        tx(
                            "Rehberin kanal listesi henüz yok. Rehberi bir kez güncelleyin; liste ondan sonra burada çıkar.",
                            "The guide's channel list is not here yet. Update the guide once; the list shows here after that.",
                        )
                    },
                    color = MatchMuted,
                )
                Spacer(Modifier.height(10.dp))
                if (!isUpdating) {
                    Button(onClick = { viewModel.refreshGuide(channel.playlistId) }, modifier = Modifier.focusRequester(first)) {
                        IconLabel(Icons.Filled.Sync, tx("Rehberi güncelle", "Update the guide"))
                    }
                }
            }
            shown.isEmpty() -> Text(tx("\"$search\" ile eşleşen rehber kanalı yok.", "No guide channel matches \"$search\"."), color = MatchMuted)
            else -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shown, key = { it.epgKey }) { source ->
                        val isCurrent = source.epgKey == data.current
                        val likely = query.isEmpty() && EpgNames.similarity(channel.name, source.name) >= 65
                        ListItem(
                            selected = isCurrent,
                            onClick = { viewModel.setEpgMatch(channel, source.epgKey, source.name); onDone() },
                            headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    (if (likely) tx("Önerilen  ·  ", "Suggested  ·  ") else "") + source.epgKey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = if (isCurrent) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = Accent) }
                            } else {
                                null
                            },
                            scale = listItemFocusScale(),
                            colors = listItemFocusColors(),
                            border = listItemFocusBorder(),
                            modifier = if (source == shown.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                ListScrollbar(listState, Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
