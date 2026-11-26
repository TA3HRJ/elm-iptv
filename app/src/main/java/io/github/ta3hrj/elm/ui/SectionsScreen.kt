package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.Surface
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.PlaylistType

/** Section colours, also a quick way to tell where you are. */
private val LiveColors = listOf(Color(0xFF1B3A7A), Color(0xFF3B7BEA))
private val GuideColors = listOf(Color(0xFF123F4F), Color(0xFF1FA2B8))
private val ArchiveColors = listOf(Color(0xFF4A2A0E), Color(0xFFE08A1E))
private val FilmColors = listOf(Color(0xFF3A1866), Color(0xFF8E44E0))
private val SeriesColors = listOf(Color(0xFF0D4A36), Color(0xFF14B37D))

/**
 * Optional start screen: one card per section with its size. Kept compact (cards share one row) so it
 * is a quick switchboard rather than a full-screen poster.
 */
@Composable
fun SectionsScreen(
    viewModel: MainViewModel,
    onLive: () -> Unit,
    onGuide: () -> Unit,
    onVod: (String) -> Unit,
    onArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val archive by viewModel.archiveChannels.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val p = playlist
    val hasVod = p != null && (p.type == PlaylistType.XTREAM || p.movieCount + p.seriesCount > 0)

    Column(Modifier.fillMaxSize().screenPadding(40.dp, 24.dp)) {
        AdaptiveHeader(
            screen = "sections",
            leading = { fit ->
                LogoButton(p?.name, onClick = onOpenAbout, showName = fit < 2)
                Spacer(Modifier.width(20.dp))
                HeaderClock(showDate = fit < 1)
                Spacer(Modifier.width(16.dp))
            },
            nav = { compact ->
                ProfileButton(viewModel, compact = compact)
                RefreshButton(viewModel, compact = compact)
            },
            trailing = { fit -> HeaderButton(Icons.Filled.Settings, tx("Ayarlar", "Settings"), fit >= 2, onClick = onOpenSettings) },
            spacing = 10.dp,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            SectionCard(
                tx("Canlı TV", "Live TV"), tx("${p?.channelCount ?: 0} kanal", "${p?.channelCount ?: 0} channels"),
                Icons.Filled.LiveTv, LiveColors, onLive, Modifier.weight(1f).focusRequester(first),
            )
            SectionCard(tx("TV Rehberi", "TV Guide"), tx("Ne zaman, hangi kanalda", "What's on, and when"), Icons.AutoMirrored.Filled.List, GuideColors, onGuide, Modifier.weight(1f))
            if (archive.isNotEmpty()) {
                SectionCard(
                    tx("Arşiv", "Archive"), tx("${archive.size} kanalda geçmiş yayınlar", "Past shows on ${archive.size} channels"),
                    Icons.Filled.History, ArchiveColors, onArchive, Modifier.weight(1f),
                )
            }
            if (hasVod) {
                SectionCard(
                    tx("Filmler", "Films"), tx("${p?.movieCount ?: 0} film", "${p?.movieCount ?: 0} films"),
                    Icons.Filled.Movie, FilmColors, { onVod(io.github.ta3hrj.elm.data.VodKinds.MOVIE) }, Modifier.weight(1f),
                )
                SectionCard(
                    tx("Diziler", "Series"), tx("${p?.seriesCount ?: 0} dizi", "${p?.seriesCount ?: 0} series"),
                    Icons.Filled.VideoLibrary, SeriesColors, { onVod(io.github.ta3hrj.elm.data.VodKinds.SERIES) }, Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val expires = p?.expiresAt?.takeIf { it > 0 }?.let {
                tx("Abonelik bitişi: ", "Subscription ends: ") +
                    java.text.SimpleDateFormat("dd.MM.yyyy", io.github.ta3hrj.elm.core.Lang.locale).format(java.util.Date(it * 1000))
            }
            Text(message ?: expires.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA4B2))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    detail: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = surfaceFocusBorder(RoundedCornerShape(18.dp)),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(colors.last().copy(alpha = 0.6f), 16.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = modifier.height(220.dp),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).background(Color(0x33FFFFFF), RoundedCornerShape(42.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xDDFFFFFF))
            }
        }
    }
}
