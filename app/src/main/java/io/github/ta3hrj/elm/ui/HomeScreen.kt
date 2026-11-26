package io.github.ta3hrj.elm.ui

import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.tv.material3.Icon
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import io.github.ta3hrj.elm.ui.touch.ListItem
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelProgramme
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.PlaylistType
import io.github.ta3hrj.elm.data.VodKinds
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenGuide: () -> Unit,
    onOpenVod: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onPlay: (ChannelEntity, ChannelQuery) -> Unit,
    onOpenArchive: (ChannelEntity) -> Unit,
    onOpenEpgMatch: (ChannelEntity) -> Unit,
) {
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedQuery by viewModel.selectedQuery.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val search by viewModel.searchText.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val selectedLocked by viewModel.selectedLocked.collectAsStateWithLifecycle()
    val homePreview by viewModel.homePreview.collectAsStateWithLifecycle()

    var groupOptions by remember { mutableStateOf<GroupItem?>(null) }
    var channelOptions by remember { mutableStateOf<ChannelEntity?>(null) }
    var askPin by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var listPicker by remember { mutableStateOf<Pair<ChannelEntity, List<Long>>?>(null) }
    var newListFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var newListOpen by remember { mutableStateOf(false) }
    var renameList by remember { mutableStateOf<ChannelQuery.FavoriteList?>(null) }
    val favoriteLists by viewModel.favoriteLists.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueWatching.collectAsStateWithLifecycle()
    var continueOptions by remember { mutableStateOf<io.github.ta3hrj.elm.data.ContinueWatchingEntity?>(null) }
    /** The channel being moved within the favorites / an own list (▲▼ move it, OK or Back ends). */
    var moving by remember { mutableStateOf<ChannelEntity?>(null) }
    val channelListState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.activity.compose.BackHandler(enabled = moving != null) { moving = null }
    LaunchedEffect(selectedQuery, search) { moving = null }
    // Keep the moved channel in view.
    LaunchedEffect(channels, moving) {
        val id = moving?.id ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        val visible = channelListState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (index <= first || index >= last) channelListState.scrollToItem((index - 3).coerceAtLeast(0))
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val form = LocalFormFactor.current
    /** On a compact screen (a phone held upright) the categories and the channels take turns; true: channels. */
    var compactChannels by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val showGroups = !form.compact || !compactChannels
    val showChannels = !form.compact || compactChannels
    androidx.activity.compose.BackHandler(enabled = form.compact && compactChannels && moving == null) {
        compactChannels = false
        viewModel.setSearch("")
    }

    val firstGroupFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    val groupListState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Start on the list that is already selected (e.g. favorites, when the last channel was resumed from there).
    val initialGroup = remember(groups.isNotEmpty()) { groups.indexOfFirst { it.query == selectedQuery }.coerceAtLeast(0) }
    val activeQuery = selectedQuery ?: groups.firstOrNull()?.query

    LaunchedEffect(groups.isNotEmpty()) {
        if (groups.isNotEmpty()) {
            groupListState.scrollToItem((initialGroup - 2).coerceAtLeast(0))
            runCatching { firstGroupFocus.requestFocus() }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            viewModel.clearMessage()
        }
    }

    // The focused channel's logo, blurred, tints the background.
    AmbientBackdrop(focusedChannel?.logo, alpha = 0.3f)
    Column(Modifier.fillMaxSize().screenPadding(32.dp, 20.dp)) {
        // Header. Settings always stays on screen (it is where the text size is changed back); the other
        // buttons scroll if they do not fit, and with large text they drop their labels.
        val kids by viewModel.activeProfile.collectAsStateWithLifecycle()
        val hasVod = playlist?.let { it.type == PlaylistType.XTREAM || it.movieCount + it.seriesCount > 0 } == true
        AdaptiveHeader(
            screen = "home",
            leading = { fit ->
                LogoButton(playlist?.name, onClick = onOpenAbout, showName = fit < 2)
                Spacer(Modifier.width(16.dp))
                HeaderClock(showDate = fit < 1)
                Spacer(Modifier.width(16.dp))
            },
            nav = { compact ->
                HeaderButton(Icons.Filled.LiveTv, tx("TV Rehberi", "TV Guide"), compact, enabled = playlist != null, onClick = onOpenGuide)
                if (hasVod) {
                    HeaderButton(Icons.Filled.Movie, tx("Filmler", "Films"), compact) { onOpenVod(VodKinds.MOVIE) }
                    HeaderButton(Icons.Filled.VideoLibrary, tx("Diziler", "Series"), compact) { onOpenVod(VodKinds.SERIES) }
                }
                HeaderButton(Icons.Filled.Search, tx("Ara", "Search"), iconOnly = true, onClick = onOpenSearch)
                // Recordings may hold anything that was recorded; a kids profile reaches them only via Settings (PIN).
                if (!kids.kids) {
                    HeaderButton(Icons.Filled.FiberManualRecord, tx("Kayıtlar", "Recordings"), iconOnly = true, tint = Color(0xFFFF5A6E), onClick = onOpenRecordings)
                }
                ProfileButton(viewModel, compact = compact)
                RefreshButton(viewModel, compact = compact)
            },
            trailing = { fit -> HeaderButton(Icons.Filled.Settings, tx("Ayarlar", "Settings"), fit >= 2, onClick = onOpenSettings) },
        )

        Spacer(Modifier.height(16.dp))

        if (continueItems.isNotEmpty()) {
            ContinueRow(continueItems, onOpen = viewModel::resume, onOptions = { continueOptions = it })
            Spacer(Modifier.height(12.dp))
        }

        if (groups.isEmpty() && (playlist?.channelCount ?: 0) > 0) {
            // First load after start: placeholders instead of an empty screen.
            Row(Modifier.weight(1f)) {
                Box(Modifier.width(300.dp)) { SkeletonList(rows = 9, rowHeight = 40.dp, withLogo = false) }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.weight(1f)) { SkeletonList(rows = 8) }
            }
        } else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
        // The preview needs room: on a narrower screen (larger text size) the list keeps all of it.
        val showPreview = homePreview && maxWidth >= PREVIEW_MIN_WIDTH && !selectedLocked && moving == null &&
            !io.github.ta3hrj.elm.recordings.RecordingService.anyRunning()
        // Only a channel of the list on screen: after a category change the old focus is stale.
        val previewChannel = if (showPreview) focusedChannel?.takeIf { f -> channels.any { it.id == f.id } } else null
        // The preview's player goes on to the full-screen player when its channel is chosen.
        val previewSession = rememberPreviewSession(viewModel, previewChannel)
        Row(Modifier.fillMaxSize()) {
            // Categories
            if (showGroups) Row((if (form.compact) Modifier.fillMaxWidth() else Modifier.width(310.dp)).fillMaxHeight()) {
            LazyColumn(
                state = groupListState,
                modifier = Modifier.weight(1f).fillMaxHeight().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(groups) { index, group ->
                    ListItem(
                        selected = search.isBlank() && group.query == activeQuery,
                        onClick = {
                            viewModel.setSearch("")
                            viewModel.selectGroup(group.query)
                            compactChannels = true
                        },
                        onLongClick = { if (group.query !is ChannelQuery.Favorites) groupOptions = group },
                        headlineContent = {
                            Text((if (group.locked) "🔒 " else "") + group.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Text(group.count.toString(), style = MaterialTheme.typography.labelSmall) },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                        glow = listItemFocusGlow(),
                        modifier = Modifier
                            .then(if (index == initialGroup) Modifier.focusRequester(firstGroupFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused && search.isBlank()) viewModel.selectGroup(group.query) }
                            .onMenuKey { if (group.query !is ChannelQuery.Favorites) groupOptions = group },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            ListScrollbar(groupListState, Modifier.padding(vertical = 4.dp))
            }

            if (!form.compact) Spacer(Modifier.width(20.dp))

            // Channels
            if (showChannels) Column(Modifier.weight(1f).fillMaxHeight()) {
                if (form.compact) {
                    // The way back to the categories, with the one on screen.
                    OutlinedButton(onClick = { compactChannels = false; viewModel.setSearch("") }) {
                        Text("‹  " + (groups.firstOrNull { it.query == activeQuery }?.title ?: tx("Kategoriler", "Categories")), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SearchField(value = search, onValueChange = viewModel::setSearch)
                Spacer(Modifier.height(8.dp))
                // Touch has no ▲ ▼ keys: the order is changed with these buttons.
                if (moving != null && form.touch) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { moving?.let { viewModel.moveChannel(it, -1) } }) { Text("▲") }
                        Button(onClick = { moving?.let { viewModel.moveChannel(it, +1) } }) { Text("▼") }
                        OutlinedButton(onClick = { moving = null }) { Text(tx("Bitti", "Done")) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (selectedLocked) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(tx("🔒 Bu kategori kilitli", "🔒 This category is locked"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { askPin = true }) { Text(tx("PIN gir", "Enter PIN")) }
                    }
                } else if (channels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (search.isNotBlank()) tx("\"$search\" ile eşleşen kanal yok.", "No channels match \"$search\".") else tx("Bu kategoride kanal yok.", "No channels in this category."),
                            color = Color(0xFF9AA4B2),
                        )
                    }
                } else Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier.weight(1f).fillMaxHeight().focusRestorer()
                            .reorderKeys(
                                active = { moving != null },
                                onMove = { step -> moving?.let { viewModel.moveChannel(it, step) } },
                                onDone = { moving = null },
                            )
                            // After the last channel, Down goes round to the first.
                            .wrapDown(channelListState, firstChannelFocus, enabled = moving == null),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { position, channel ->
                            ChannelRow(
                                modifier = if (position == 0) Modifier.focusRequester(firstChannelFocus) else Modifier,
                                channel = channel,
                                now = nowPlaying[channel.streamUrl],
                                isFavorite = channel.streamUrl in favorites,
                                isMoving = channel.id == moving?.id,
                                onClick = {
                                    val query = viewModel.activeQuery() ?: ChannelQuery.Group(channel.groupTitle)
                                    // In the archive list a channel opens its past programmes.
                                    if (query == ChannelQuery.Archive) {
                                        onOpenArchive(channel)
                                    } else {
                                        previewSession.handOff(channel)
                                        onPlay(channel, query)
                                    }
                                },
                                onOptions = { channelOptions = channel },
                                onFocused = { focusedChannel = channel },
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    ListScrollbar(channelListState, Modifier.padding(vertical = 4.dp))
                }
            }
            if (showPreview) {
                Spacer(Modifier.width(16.dp))
                PreviewPanel(previewSession, previewChannel, previewChannel?.let { nowPlaying[it.streamUrl]?.title }, Modifier.width(PREVIEW_WIDTH))
            }
        }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (moving != null) {
                    hint(
                        tx("⇅ \"${moving?.name}\" taşınıyor: ▲ ▼ ile yerini seçin  ·  OK veya Geri: bitir", "⇅ Moving \"${moving?.name}\": ▲ ▼ to choose its place  ·  OK or Back: done"),
                        tx("⇅ \"${moving?.name}\" taşınıyor: ▲ ▼ ile yerini seçin, Bitti ile bitirin", "⇅ Moving \"${moving?.name}\": ▲ ▼ to choose its place, Done to finish"),
                    )
                } else {
                    if (activeQuery == ChannelQuery.Archive) {
                        hint(
                            tx("OK: kanalın geçmiş programları  ·  Menü (☰): seçenekler", "OK: the channel's past programmes  ·  Menu (☰): options"),
                            tx("Dokun: geçmiş programlar  ·  Basılı tut: seçenekler", "Tap: past programmes  ·  Hold: options"),
                        )
                    } else {
                        hint(
                            tx("OK: İzle  ·  Menü (☰) veya OK'a basılı tut: seçenekler (favori, sıra, gizle, kilitle)", "OK: watch  ·  Menu (☰) or hold OK: options (favorite, order, hide, lock)"),
                            tx("Dokun: izle  ·  Basılı tut: seçenekler (favori, sıra, gizle, kilitle)", "Tap: watch  ·  Hold: options (favorite, order, hide, lock)"),
                        )
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8A94A4),
                modifier = Modifier.weight(1f),
            )
            // Playlist details sit here, where there is room for them; a status message replaces them for a moment.
            if (message == null && playlist != null && !form.compact) {
                Text(
                    playlistSummary(playlist?.channelCount ?: 0, playlist?.updatedAt ?: 0, playlist?.expiresAt).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7D8796),
                    maxLines = 1,
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF243042))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    groupOptions?.let { group ->
        when (val query = group.query) {
            is ChannelQuery.Group -> {
                val name = query.name
                OptionsDialog(
                    title = name,
                    subtitle = tx("${group.count} kanal", "${group.count} channels"),
                    options = listOf(
                        if (group.locked) {
                            DialogOption(tx("Kilidi kaldır", "Remove lock"), Icons.Filled.LockOpen) { askPinThen(viewModel) { viewModel.setGroupLocked(FlagScopes.LIVE, name, false) } }
                        } else {
                            DialogOption(tx("Ebeveyn kilidi koy", "Add parental lock"), Icons.Filled.Lock) { viewModel.setGroupLocked(FlagScopes.LIVE, name, true) }
                        },
                        DialogOption(tx("Kategoriyi gizle", "Hide category"), Icons.Filled.VisibilityOff) { viewModel.setGroupHidden(FlagScopes.LIVE, name, true) },
                        DialogOption(tx("Yeni liste oluştur", "Create a new list"), Icons.AutoMirrored.Filled.PlaylistAdd) { newListOpen = true },
                    ),
                    onDismiss = { groupOptions = null },
                )
            }
            is ChannelQuery.FavoriteList -> OptionsDialog(
                title = query.name,
                subtitle = tx("${group.count} kanal", "${group.count} channels"),
                options = listOf(
                    DialogOption(tx("Yeniden adlandır", "Rename"), Icons.Filled.Edit) { renameList = query },
                    DialogOption(tx("Listeyi sil", "Delete list"), Icons.Filled.Delete) { viewModel.deleteList(query.id, query.name) },
                    DialogOption(tx("Yeni liste oluştur", "Create a new list"), Icons.AutoMirrored.Filled.PlaylistAdd) { newListOpen = true },
                ),
                onDismiss = { groupOptions = null },
            )
            ChannelQuery.Recent -> OptionsDialog(
                title = group.title,
                options = listOf(DialogOption(tx("Geçmişi temizle", "Clear history"), Icons.Filled.Delete) { viewModel.clearRecent() }),
                onDismiss = { groupOptions = null },
            )
            else -> groupOptions = null
        }
    }
    channelOptions?.let { channel ->
        OptionsDialog(
            title = "${channel.number}  ${channel.name}",
            subtitle = channel.groupTitle,
            options = listOfNotNull(
                if (viewModel.hasArchive(channel)) DialogOption(tx("Kanal arşivi", "Channel archive"), Icons.Filled.History) { onOpenArchive(channel) } else null,
                DialogOption(
                    if (channel.streamUrl in favorites) tx("Favorilerden çıkar", "Remove from favorites") else tx("Favorilere ekle", "Add to favorites"),
                    if (channel.streamUrl in favorites) Icons.Filled.StarBorder else Icons.Filled.Star,
                ) { viewModel.toggleFavorite(channel) },
                if (activeQuery?.hasOwnOrder == true && search.isBlank() && channels.size > 1) {
                    DialogOption(tx("Sırayı değiştir", "Change order"), Icons.Filled.SwapVert) { moving = channel }
                } else {
                    null
                },
                DialogOption(tx("Listelerim…", "My lists…"), Icons.AutoMirrored.Filled.PlaylistAdd) {
                    scope.launch { listPicker = channel to viewModel.listsContaining(channel) }
                },
                DialogOption(tx("Rehberi eşleştir…", "Match to the guide…"), Icons.Filled.Link) { onOpenEpgMatch(channel) },
                DialogOption(tx("Harici oynatıcıda aç", "Open in external player"), Icons.AutoMirrored.Filled.OpenInNew) { viewModel.openExternal(channel) },
                DialogOption(tx("Çoklu ekranda aç", "Open in multi-view"), Icons.Filled.GridView) {
                    io.github.ta3hrj.elm.player.MultiViewActivity.start(viewModel.getApplication(), channel)
                },
                DialogOption(tx("Kanalı gizle", "Hide channel"), Icons.Filled.VisibilityOff) { viewModel.hideChannel(channel) },
            ),
            onDismiss = { channelOptions = null },
        )
    }
    continueOptions?.let { entry ->
        OptionsDialog(
            title = entry.title,
            subtitle = entry.subtitle,
            options = listOf(
                DialogOption(tx("Devam et", "Continue"), Icons.Filled.PlayArrow) { viewModel.resume(entry) },
                DialogOption(tx("Listeden kaldır", "Remove from the row"), Icons.Filled.Delete) { viewModel.removeFromContinue(entry) },
            ),
            onDismiss = { continueOptions = null },
        )
    }
    listPicker?.let { (channel, inLists) ->
        OptionsDialog(
            title = tx("Listelerim", "My lists"),
            subtitle = channel.name,
            options = favoriteLists.map { list ->
                DialogOption(
                    if (list.id in inLists) tx("${list.name} · çıkar", "${list.name} · remove") else list.name,
                    Icons.Filled.Bookmark,
                    selected = list.id in inLists,
                ) { viewModel.toggleInList(list.id, list.name, channel) }
            } + DialogOption(tx("Yeni liste…", "New list…"), Icons.Filled.Add) { newListFor = channel },
            onDismiss = { listPicker = null },
        )
    }
    if (newListOpen || newListFor != null) {
        TextInputDialog(
            title = tx("Yeni liste adı", "New list name"),
            confirmLabel = tx("Oluştur", "Create"),
            onSubmit = { viewModel.createList(it, newListFor) },
            onDismiss = { newListOpen = false; newListFor = null },
        )
    }
    renameList?.let { list ->
        TextInputDialog(
            title = tx("Listeyi yeniden adlandır", "Rename list"),
            initial = list.name,
            confirmLabel = tx("Kaydet", "Save"),
            onSubmit = { viewModel.renameList(list.id, it) },
            onDismiss = { renameList = null },
        )
    }
    if (askPin) {
        PinDialog(tx("PIN girin", "Enter PIN"), tx("Kilitli kategorileri açmak için", "To open locked categories"), onSubmit = viewModel::unlock, onDismiss = { askPin = false })
    }
    PendingPin(viewModel)
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    now: ChannelProgramme?,
    isFavorite: Boolean,
    isMoving: Boolean,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = isMoving,
        onClick = onClick,
        onLongClick = onOptions,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7FB8FF),
                    modifier = Modifier.width(44.dp),
                )
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
            }
        },
        supportingContent = now?.let { programme ->
            {
                Column {
                    Text(
                        "${timeRange(programme.start, programme.stop)}  ${programme.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val total = (programme.stop - programme.start).coerceAtLeast(1)
                    val progress = ((System.currentTimeMillis() - programme.start).toFloat() / total).coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.5f).height(3.dp).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFF4FA3FF)))
                    }
                }
            }
        },
        leadingContent = { ChannelLogo(channel.name, channel.logo, width = 64.dp, height = 40.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.catchupDays > 0) {
                    Text("⟲ ${channel.catchupDays}g", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA4B2))
                    Spacer(Modifier.width(8.dp))
                }
                if (isFavorite) Text("★", color = Color(0xFFFFC94D), fontSize = 18.sp)
                if (isMoving) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.SwapVert, contentDescription = null, tint = Accent)
                }
            }
        },
        scale = listItemFocusScale(),
        colors = listItemFocusColors(),
        border = listItemFocusBorder(),
        glow = listItemFocusGlow(),
        modifier = modifier.onMenuKey(onOptions).onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = tx("Kanal ara…", "Search channels…"),
        imeAction = ImeAction.Search,
        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun playlistSummary(count: Int, updatedAt: Long, expiresAt: Long?): List<String> {
    val parts = mutableListOf(tx("$count kanal", "$count channels"))
    if (updatedAt > 0) {
        parts += tx("Güncellendi: ", "Updated: ") + SimpleDateFormat("dd.MM.yyyy HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(updatedAt))
    }
    if (expiresAt != null && expiresAt > 0) {
        parts += tx("Abonelik bitişi: ", "Subscription ends: ") + SimpleDateFormat("dd.MM.yyyy", io.github.ta3hrj.elm.core.Lang.locale).format(Date(expiresAt * 1000))
    }
    return parts
}

/** Calls [action] when the remote's Menu (☰) key is released on this element. */
fun Modifier.onMenuKey(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
        action()
        true
    } else {
        false
    }
}

/** Runs [action] right away if no PIN is set or it is already entered, otherwise after asking for it. */
fun askPinThen(viewModel: MainViewModel, action: () -> Unit) {
    if (viewModel.unlocked.value) action() else viewModel.requestPin(action)
}

/** Shows the PIN prompt requested through [askPinThen]. */
@Composable
fun PendingPin(viewModel: MainViewModel) {
    val pending by viewModel.pendingPinAction.collectAsStateWithLifecycle()
    if (pending != null) {
        PinDialog(
            tx("PIN girin", "Enter PIN"),
            onSubmit = { pin -> viewModel.unlock(pin).also { if (it) viewModel.runPendingPinAction() } },
            onDismiss = viewModel::cancelPendingPinAction,
        )
    }
}

/** "Continue watching": films, episodes and recordings left part-way. OK resumes, Menu offers removal. */
@Composable
private fun ContinueRow(
    items: List<io.github.ta3hrj.elm.data.ContinueWatchingEntity>,
    onOpen: (io.github.ta3hrj.elm.data.ContinueWatchingEntity) -> Unit,
    onOptions: (io.github.ta3hrj.elm.data.ContinueWatchingEntity) -> Unit,
) {
    Column {
        Text(tx("İzlemeye devam et", "Continue watching"), style = MaterialTheme.typography.titleSmall, color = Color(0xFF7FB8FF))
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(items, key = { it.mediaKey }) { entry ->
                io.github.ta3hrj.elm.ui.touch.Surface(
                    onClick = { onOpen(entry) },
                    onLongClick = { onOptions(entry) },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = surfaceFocusBorder(RoundedCornerShape(8.dp)),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1A212B),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF1F4E86),
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.width(300.dp).height(84.dp).onMenuKey { onOptions(entry) },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(56.dp).fillMaxHeight().background(Color(0xFF242C38))) {
                            if (!entry.poster.isNullOrBlank()) {
                                AsyncImage(model = entry.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                androidx.tv.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(entry.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8C2D0))
                            Spacer(Modifier.weight(1f))
                            val fraction = if (entry.durationMs > 0) (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x44FFFFFF))) {
                                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color(0xFFFF5A6E)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A header button: icon and label, or just the icon when space is short ([iconOnly]). */
@Composable
fun HeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconOnly: Boolean,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled) {
        if (iconOnly) {
            Icon(icon, contentDescription = label, tint = tint ?: androidx.tv.material3.LocalContentColor.current)
        } else {
            IconLabel(icon, label)
        }
    }
}

/** Updates channels, guide, films and series now; shows when the playlist was last updated. */
@Composable
fun RefreshButton(viewModel: MainViewModel, compact: Boolean = false) {
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val p = playlist ?: return
    // Re-read the clock now and then so "5 min ago" stays true while the screen is open.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    Button(onClick = { viewModel.refresh(p.id) }, enabled = !busy) {
        if (compact) {
            Icon(Icons.Filled.Sync, contentDescription = tx("Yenile", "Refresh"))
        } else {
            IconLabel(Icons.Filled.Sync, if (busy) tx("Güncelleniyor…", "Updating…") else updatedAgo(p.updatedAt, now))
        }
    }
}

private fun updatedAgo(updatedAt: Long, now: Long): String {
    if (updatedAt <= 0) return tx("Güncelle", "Update")
    val minutes = (now - updatedAt) / 60_000
    return when {
        minutes < 1 -> tx("Az önce", "Just now")
        minutes < 60 -> tx("$minutes dk önce", "$minutes min ago")
        minutes < 24 * 60 -> tx("${minutes / 60} sa önce", "${minutes / 60} h ago")
        else -> SimpleDateFormat("dd.MM HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(updatedAt))
    }
}

/**
 * Keys while a channel is being moved ("Change order"): up and down move it, OK or Back finish.
 * OK finishes on its release and keeps that release: finishing on the press let the release reach the
 * channel underneath, which then started playing.
 */
fun Modifier.reorderKeys(active: () -> Boolean, onMove: (Int) -> Unit, onDone: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (!active()) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionUp, Key.DirectionDown -> {
            if (event.type == KeyEventType.KeyDown) onMove(if (event.key == Key.DirectionUp) -1 else +1)
            true
        }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            if (event.type == KeyEventType.KeyUp) onDone()
            true
        }
        Key.DirectionLeft, Key.DirectionRight -> true
        else -> false
    }
}

/** The live preview beside the channel list needs at least this much screen width (in dp at the chosen text size). */
private val PREVIEW_MIN_WIDTH = 1050.dp
private val PREVIEW_WIDTH = 290.dp
