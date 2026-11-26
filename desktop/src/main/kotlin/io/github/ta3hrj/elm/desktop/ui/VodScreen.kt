package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ta3hrj.elm.core.MovieInfo
import io.github.ta3hrj.elm.core.SeriesInfo
import io.github.ta3hrj.elm.core.VodItem
import io.github.ta3hrj.elm.core.VodKind
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.player.formatTime

private const val ALL = "#all"

@Composable
fun VodScreen(state: AppState, kind: VodKind) {
    LaunchedEffect(kind, state.content) { state.loadVod(kind) }
    val items = if (kind == VodKind.MOVIE) state.movies else state.series
    var category by remember(kind) { mutableStateOf(ALL) }
    var query by remember(kind) { mutableStateOf("") }
    var open by remember(kind) { mutableStateOf<VodItem?>(null) }

    open?.let { item ->
        VodDetail(state, item) { open = null }
        return
    }
    if (items == null) {
        EmptyNote(state.busy ?: tx("Yükleniyor…", "Loading…"))
        return
    }
    val categories = state.vodCategories(items)
    Row(Modifier.fillMaxSize()) {
        SidePanel(Modifier.width(240.dp)) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                item { GroupRow(tx("Tümü", "All"), items.size, category == ALL) { category = ALL } }
                items(categories) { c -> GroupRow(c, null, category == c) { category = c } }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (kind == VodKind.MOVIE) tx("Film ara…", "Search films…") else tx("Dizi ara…", "Search series…")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val allowed = categories.toSet()
            val shown = AppState.filter(items.filter { (category == ALL && it.category in allowed) || it.category == category }, query) { it.name }
            if (shown.isEmpty()) {
                EmptyNote(tx("Burada bir şey yok.", "Nothing here."))
            } else {
                Box(Modifier.fillMaxSize()) {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        items(shown, key = { it.providerId }) { item -> PosterCard(item) { open = item } }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun PosterCard(item: VodItem, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Logo(item.poster, item.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Spacer(Modifier.height(4.dp))
        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text(listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  "), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

/** A film (plot, play / continue) or a series (seasons and episodes). */
@Composable
fun VodDetail(state: AppState, item: VodItem, onBack: () -> Unit) {
    var movie by remember(item) { mutableStateOf<MovieInfo?>(null) }
    var series by remember(item) { mutableStateOf<SeriesInfo?>(null) }
    var error by remember(item) { mutableStateOf<String?>(null) }
    var season by remember(item) { mutableStateOf<Int?>(null) }
    LaunchedEffect(item) {
        try {
            if (item.kind == VodKind.MOVIE) movie = state.movieInfo(item) else series = state.seriesInfo(item).also { season = it.seasons.keys.firstOrNull() }
        } catch (e: Exception) {
            error = e.message
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(tx("‹ Geri", "‹ Back")) }
            Spacer(Modifier.width(16.dp))
            Text(item.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize()) {
            Logo(movie?.poster ?: series?.poster ?: item.poster, item.name, Modifier.width(220.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val plot = movie?.plot ?: series?.plot
                val facts = listOfNotNull(
                    movie?.releaseDate ?: series?.releaseDate ?: item.year,
                    movie?.genre ?: series?.genre,
                    movie?.duration,
                    (movie?.rating ?: series?.rating ?: item.rating)?.let { "★ $it" },
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Accent)
                plot?.let { Spacer(Modifier.height(8.dp)); Text(it, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                (movie?.cast ?: series?.cast)?.let { Spacer(Modifier.height(6.dp)); Text(tx("Oyuncular: ", "Cast: ") + it, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                movie?.director?.let { Text(tx("Yönetmen: ", "Director: ") + it, color = Muted) }
                error?.let { Text(it, color = Muted) }
                Spacer(Modifier.height(16.dp))
                if (item.kind == VodKind.MOVIE) {
                    val url = state.playlist?.let { state.catalog.movieUrl(it, item) }
                    val resume = url?.let { state.data.resume[it] }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { state.playMovie(item) }) {
                            Text(if (resume != null) tx("Devam et (${formatTime(resume)})", "Continue (${formatTime(resume)})") else tx("İzle", "Play"))
                        }
                        if (resume != null && url != null) {
                            OutlinedButton(onClick = { state.saveResume(url, 0, 0); state.playMovie(item) }) { Text(tx("Baştan izle", "Play from the start")) }
                        }
                    }
                } else {
                    val info = series
                    if (info == null) {
                        if (error == null) CircularProgressIndicator()
                    } else if (info.seasons.isEmpty()) {
                        Text(tx("Bu dizide bölüm yok.", "This series has no episodes."), color = Muted)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(info.seasons.keys.toList()) { s ->
                                FilterChip(selected = s == season, onClick = { season = s }, label = { Text(tx("Sezon $s", "Season $s")) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val episodes = info.seasons[season].orEmpty()
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            for (ep in episodes) {
                                val url = state.playlist?.let { state.catalog.episodeUrl(it, ep) }
                                val resume = url?.let { state.data.resume[it] }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { state.playEpisode(item, ep) }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${ep.number}", color = Muted, modifier = Modifier.width(36.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        ep.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    }
                                    resume?.let { Text("▶ " + formatTime(it), color = Accent, style = MaterialTheme.typography.labelSmall) }
                                    ep.duration?.let { Text("  $it", color = Muted, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
