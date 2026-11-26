package io.github.ta3hrj.elm.ui

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VisibilityOff
import io.github.ta3hrj.elm.core.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import io.github.ta3hrj.elm.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import io.github.ta3hrj.elm.ui.touch.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ta3hrj.elm.core.Episode
import io.github.ta3hrj.elm.core.MovieInfo
import io.github.ta3hrj.elm.core.SeriesInfo
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.PlaylistType
import io.github.ta3hrj.elm.data.VodItemEntity
import io.github.ta3hrj.elm.data.VodKinds
import io.github.ta3hrj.elm.data.VodRepository
import io.github.ta3hrj.elm.data.WatchProgressEntity

private val VodMuted = Color(0xFF9AA4B2)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VodBrowseScreen(
    viewModel: MainViewModel,
    onOpen: (VodItemEntity) -> Unit,
    onLive: () -> Unit,
) {
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val kind by viewModel.vodKind.collectAsStateWithLifecycle()
    val categories by viewModel.vodCategories.collectAsStateWithLifecycle()
    val category by viewModel.vodCategory.collectAsStateWithLifecycle()
    val items by viewModel.vodItems.collectAsStateWithLifecycle()
    val vodOrder by viewModel.vodOrder.collectAsStateWithLifecycle()
    var sortOpen by remember { mutableStateOf(false) }
    val search by viewModel.vodSearch.collectAsStateWithLifecycle()
    val updating by viewModel.vodUpdating.collectAsStateWithLifecycle()
    val progress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    var categoryOptions by remember { mutableStateOf<GroupItem?>(null) }
    var focusedPoster by remember { mutableStateOf<String?>(null) }

    val isMovies = kind == VodKinds.MOVIE
    val scope = if (isMovies) FlagScopes.MOVIE else FlagScopes.SERIES
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(categories.isNotEmpty(), kind) {
        if (categories.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    // The focused poster, blurred, fills the background.
    AmbientBackdrop(focusedPoster, alpha = 0.45f)
    Column(Modifier.fillMaxSize().screenPadding(32.dp, 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isMovies) tx("Filmler", "Films") else tx("Diziler", "Series"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        playlist?.name,
                        playlist?.let { if (isMovies) tx("${it.movieCount} film", "${it.movieCount} films") else tx("${it.seriesCount} dizi", "${it.seriesCount} series") },
                        if (playlist?.id in updating) tx("güncelleniyor…", "updating…") else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = VodMuted,
                )
            }
            OutlinedButton(onClick = onLive) { Text(tx("Canlı TV", "Live TV")) }
            Spacer(Modifier.width(8.dp))
            // One button that becomes the other kind's: replacing it would drop the focus.
            OutlinedButton(onClick = { viewModel.showVod(if (isMovies) VodKinds.SERIES else VodKinds.MOVIE) }) {
                Text(if (isMovies) tx("Diziler", "Series") else tx("Filmler", "Films"))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    playlist?.let { if (it.type == PlaylistType.XTREAM) viewModel.refreshVod(it.id) else viewModel.refresh(it.id) }
                },
                enabled = playlist != null && playlist?.id !in updating,
            ) {
                Text(tx("Yenile", "Refresh"))
            }
        }
        Spacer(Modifier.height(14.dp))

        if (categories.isEmpty() && playlist?.id in updating) {
            Row {
                Box(Modifier.width(280.dp)) { SkeletonList(rows = 9, rowHeight = 40.dp, withLogo = false) }
                Spacer(Modifier.width(20.dp))
                SkeletonPosters()
            }
            return@Column
        }
        if (categories.isEmpty()) {
            Text(
                when {
                    playlist?.id in updating -> tx("Film ve dizi listesi indiriliyor…", "Downloading films and series…")
                    playlist?.vodError != null -> tx("Liste alınamadı: ${playlist?.vodError}", "Could not load the list: ${playlist?.vodError}")
                    playlist?.type != PlaylistType.XTREAM -> if (isMovies) tx("Bu listede film bulunamadı.", "No films in this playlist.") else tx("Bu listede dizi bulunamadı.", "No series in this playlist.")
                    else -> if (isMovies) tx("Bu hesapta film bulunamadı. \"Yenile\" ile tekrar deneyin.", "No films in this account. Try \"Refresh\".")
                        else tx("Bu hesapta dizi bulunamadı. \"Yenile\" ile tekrar deneyin.", "No series in this account. Try \"Refresh\".")
                },
                color = VodMuted,
            )
            return@Column
        }

        Row(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.width(280.dp).fillMaxHeight().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(categories) { index, group ->
                    val name = (group.query as ChannelQuery.Group).name
                    val selected = search.isBlank() && (category ?: MainViewModel.RECENT) == name
                    ListItem(
                        selected = selected,
                        onClick = { viewModel.setVodSearch(""); viewModel.selectVodCategory(name) },
                        onLongClick = { if (!isSpecialCategory(name)) categoryOptions = group },
                        headlineContent = {
                            Text((if (group.locked) "🔒 " else "") + group.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            if (name != MainViewModel.RECENT) Text(group.count.toString(), style = MaterialTheme.typography.labelSmall)
                        },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                        glow = listItemFocusGlow(),
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused && search.isBlank()) viewModel.selectVodCategory(name) }
                            .onMenuKey { if (!isSpecialCategory(name)) categoryOptions = group },
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        VodSearchField(search, viewModel::setVodSearch, if (isMovies) tx("Film ara…", "Search films…") else tx("Dizi ara…", "Search series…"))
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { sortOpen = true }) { IconLabel(Icons.AutoMirrored.Filled.Sort, vodOrderLabel(vodOrder)) }
                }
                Spacer(Modifier.height(10.dp))
                val selectedGroup = categories.firstOrNull { (it.query as ChannelQuery.Group).name == (category ?: MainViewModel.RECENT) }
                if (search.isBlank() && selectedGroup?.locked == true && !unlocked) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(tx("🔒 Bu kategori kilitli", "🔒 This category is locked"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { askPinThen(viewModel) {} }) { Text(tx("PIN gir", "Enter PIN")) }
                    }
                    return@Column
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp, start = 4.dp, end = 4.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    items(items, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            progress = if (isMovies) progress[VodRepository.movieKey(item.providerId)] else null,
                            onClick = { onOpen(item) },
                            onFocused = { focusedPoster = item.poster },
                        )
                    }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VodMuted) }
    }

    if (sortOpen) {
        OptionsDialog(
            title = tx("Sıralama", "Sort by"),
            options = io.github.ta3hrj.elm.data.VodOrder.entries.map { order ->
                DialogOption(vodOrderLabel(order), selected = order == vodOrder) { viewModel.setVodOrder(order) }
            },
            onDismiss = { sortOpen = false },
        )
    }

    categoryOptions?.let { group ->
        val name = (group.query as ChannelQuery.Group).name
        OptionsDialog(
            title = name,
            subtitle = "${group.count} " + if (isMovies) tx("film", "films") else tx("dizi", "series"),
            options = listOf(
                if (group.locked) {
                    DialogOption(tx("Kilidi kaldır", "Remove lock"), Icons.Filled.LockOpen) { askPinThen(viewModel) { viewModel.setGroupLocked(scope, name, false) } }
                } else {
                    DialogOption(tx("Ebeveyn kilidi koy", "Add parental lock"), Icons.Filled.Lock) { viewModel.setGroupLocked(scope, name, true) }
                },
                DialogOption(tx("Kategoriyi gizle", "Hide category"), Icons.Filled.VisibilityOff) { viewModel.setGroupHidden(scope, name, true) },
            ),
            onDismiss = { categoryOptions = null },
        )
    }
    PendingPin(viewModel)
}

@Composable
private fun PosterCard(item: VodItemEntity, progress: WatchProgressEntity?, onClick: () -> Unit, onFocused: () -> Unit) {
    Column {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Accent.copy(alpha = 0.55f), 14.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A212B), focusedContainerColor = Color(0xFF4FA3FF)),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Box(Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F2630))) {
                if (!item.poster.isNullOrBlank()) Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(6.dp)))
                Text(
                    item.name,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
                if (!item.poster.isNullOrBlank()) {
                    AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                item.year?.let {
                    Text(
                        it.take(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let {
                    Text(
                        "★ $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFC94D),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                progress?.let { ProgressStripe(it, Modifier.align(Alignment.BottomStart)) }
            }
        }
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp))
    }
}

private fun vodOrderLabel(order: io.github.ta3hrj.elm.data.VodOrder): String = when (order) {
    io.github.ta3hrj.elm.data.VodOrder.PLAYLIST -> tx("Liste sırası", "Playlist order")
    io.github.ta3hrj.elm.data.VodOrder.NAME -> tx("Ad (A–Z)", "Name (A–Z)")
    io.github.ta3hrj.elm.data.VodOrder.RATING -> tx("Puan (yüksekten düşüğe)", "Rating (highest first)")
    io.github.ta3hrj.elm.data.VodOrder.YEAR -> tx("Yıl (yeniden eskiye)", "Year (newest first)")
    io.github.ta3hrj.elm.data.VodOrder.YEAR_OLD -> tx("Yıl (eskiden yeniye)", "Year (oldest first)")
    io.github.ta3hrj.elm.data.VodOrder.ADDED_NEW -> tx("Eklenme (yeni önce)", "Added (newest first)")
    io.github.ta3hrj.elm.data.VodOrder.ADDED_OLD -> tx("Eklenme (eski önce)", "Added (oldest first)")
    io.github.ta3hrj.elm.data.VodOrder.NAME_DESC -> tx("Ad (Z–A)", "Name (Z–A)")
}

@Composable
private fun ProgressStripe(progress: WatchProgressEntity, modifier: Modifier = Modifier) {
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color(0xFFFF5A6E)))
    }
}

@Composable
fun MovieDetailScreen(viewModel: MainViewModel, itemId: Long, onBack: () -> Unit) {
    var item by remember { mutableStateOf<VodItemEntity?>(null) }
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(itemId) {
        item = viewModel.vodItem(itemId) ?: run { onBack(); return@LaunchedEffect }
        runCatching { playFocus.requestFocus() }
        info = runCatching { viewModel.movieInfo(item!!) }.onFailure { error = it.message }.getOrNull()
    }
    val movie = item ?: return
    val saved = progress[VodRepository.movieKey(movie.providerId)]

    DetailLayout(
        backdrop = info?.backdrop ?: info?.poster ?: movie.poster,
        poster = info?.poster ?: movie.poster,
        title = movie.name,
        facts = listOfNotNull(movie.year, info?.genre, info?.duration?.let { formatClock(it) }, (info?.rating ?: movie.rating)?.let { "★ $it" }),
        plot = info?.plot ?: error?.let { tx("Film bilgisi alınamadı: $it", "Could not load film details: $it") },
        credits = listOfNotNull(info?.director?.let { tx("Yönetmen: $it", "Director: $it") }, info?.cast?.let { tx("Oyuncular: $it", "Cast: $it") }),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.playMovie(movie, fromStart = false) }, modifier = Modifier.focusRequester(playFocus)) {
                Text(if (saved != null) tx("▶ Devam et (${formatMs(saved.positionMs)})", "▶ Continue (${formatMs(saved.positionMs)})") else tx("▶ Oynat", "▶ Play"))
            }
            if (saved != null) OutlinedButton(onClick = { viewModel.playMovie(movie, fromStart = true) }) { Text(tx("Baştan oynat", "Play from start")) }
            WatchlistButton(viewModel, movie)
            OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
        }
        saved?.let { Spacer(Modifier.height(10.dp)); Box(Modifier.width(360.dp)) { ProgressStripe(it) } }
    }
}

@Composable
fun SeriesDetailScreen(viewModel: MainViewModel, itemId: Long, onBack: () -> Unit) {
    var item by remember { mutableStateOf<VodItemEntity?>(null) }
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var seasonIndex by remember { mutableIntStateOf(0) }
    val progress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(itemId) {
        item = viewModel.vodItem(itemId) ?: run { onBack(); return@LaunchedEffect }
        info = runCatching { viewModel.seriesInfo(item!!) }.onFailure { error = it.message }.getOrNull()
        runCatching { firstFocus.requestFocus() }
    }
    val series = item ?: return
    val seasons = info?.seasons.orEmpty()
    val seasonNumbers = seasons.keys.toList()
    val episodes = seasonNumbers.getOrNull(seasonIndex)?.let { seasons[it] }.orEmpty()

    DetailLayout(
        backdrop = info?.backdrop ?: info?.poster ?: series.poster,
        poster = info?.poster ?: series.poster,
        title = series.name,
        facts = listOfNotNull(series.year, info?.genre, (info?.rating ?: series.rating)?.let { "★ $it" },
            if (seasons.isNotEmpty()) tx("${seasons.size} sezon", "${seasons.size} seasons") else null),
        plot = info?.plot ?: when {
            error != null -> tx("Dizi bilgisi alınamadı: $error", "Could not load series details: $error")
            info == null -> tx("Bölümler yükleniyor…", "Loading episodes…")
            else -> null
        },
        credits = listOfNotNull(info?.cast?.let { tx("Oyuncular: $it", "Cast: $it") }),
        compact = true,
    ) {
        WatchlistButton(viewModel, series)
        Spacer(Modifier.height(10.dp))
        if (info != null && seasons.isEmpty()) Text(tx("Bu dizide bölüm bulunamadı.", "No episodes found in this series."), color = VodMuted)
        if (seasonNumbers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(seasonNumbers) { index, number ->
                    val modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    ChoiceButton(
                        tx("$number. Sezon", "Season $number"),
                        selected = index == seasonIndex,
                        modifier = modifier.onFocusChanged { if (it.isFocused) seasonIndex = index },
                        check = false,
                    ) { seasonIndex = index }
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(320.dp)) {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(episode, progress[VodRepository.episodeKey(episode.id)]) {
                        viewModel.playEpisode(series, episode, fromStart = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: WatchProgressEntity?, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        scale = listItemFocusScale(),
        colors = listItemFocusColors(),
        border = listItemFocusBorder(),
        headlineContent = { Text("${episode.number}. ${episode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(episode.duration?.let { formatClock(it) }, progress?.let { tx("kaldığınız yer ${formatMs(it.positionMs)}", "stopped at ${formatMs(it.positionMs)}") }, episode.plot)
                        .joinToString("  ·  "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                progress?.let { Box(Modifier.padding(top = 3.dp).width(240.dp)) { ProgressStripe(it) } }
            }
        },
        leadingContent = {
            Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1F2630))) {
                if (!episode.image.isNullOrBlank()) {
                    AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        },
    )
}

@Composable
private fun DetailLayout(
    backdrop: String?,
    poster: String?,
    title: String,
    facts: List<String>,
    plot: String?,
    credits: List<String>,
    compact: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(AppBackground, AppBackground.copy(alpha = 0.92f), AppBackground.copy(alpha = 0.55f))),
            ),
        )
        Row(Modifier.fillMaxSize().screenPadding(56.dp, 40.dp)) {
            Box(Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2630))) {
                if (!poster.isNullOrBlank()) {
                    AsyncImage(model = poster, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Color(0xFF7FB8FF), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                plot?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 3 else 7, overflow = TextOverflow.Ellipsis)
                }
                credits.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = VodMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(22.dp))
                actions()
            }
        }
    }
}

@Composable
private fun VodSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val focusManager = LocalFocusManager.current
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        imeAction = ImeAction.Search,
        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "01:45:00" -> "1 sa 45 dk"; other formats are shown as they are. */
private fun formatClock(text: String): String {
    val parts = text.split(':').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return text
    val (h, m, _) = parts
    return when {
        h > 0 && m > 0 -> tx("$h sa $m dk", "$h h $m min")
        h > 0 -> tx("$h sa", "$h h")
        else -> tx("$m dk", "$m min")
    }
}

private fun formatMs(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) tx("${minutes / 60} sa ${minutes % 60} dk", "${minutes / 60} h ${minutes % 60} min") else tx("$minutes dk", "$minutes min")
}

private fun isSpecialCategory(name: String) = name == MainViewModel.RECENT || name == MainViewModel.WATCHLIST

/** Adds the film or series to the watchlist, or takes it off. */
@Composable
private fun WatchlistButton(viewModel: MainViewModel, item: VodItemEntity) {
    val keys by viewModel.watchlistKeys.collectAsStateWithLifecycle()
    val inList = "${item.kind}|${item.providerId}" in keys
    OutlinedButton(onClick = { viewModel.toggleWatchlist(item) }) {
        IconLabel(
            if (inList) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (inList) tx("İzleme listemde", "In my watchlist") else tx("İzleme listeme ekle", "Add to watchlist"),
        )
    }
}
