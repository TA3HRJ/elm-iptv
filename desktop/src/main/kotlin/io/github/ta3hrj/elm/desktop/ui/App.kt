package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import io.github.ta3hrj.elm.core.VodKind
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.Screen
import io.github.ta3hrj.elm.desktop.player.Vlc
import kotlinx.coroutines.delay

val Accent = Color(0xFF4FA3FF)
val Background = Color(0xFF0E1116)
val Panel = Color(0xFF161B22)
val Muted = Color(0xFF8A94A4)
val TextPrimary = Color(0xFFE8ECF2)

@Composable
fun ElmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = Background,
            surfaceVariant = Panel,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        ),
        content = content,
    )
}

@Composable
fun App(state: AppState, window: WindowState) {
    ElmTheme {
        Surface(Modifier.fillMaxSize(), color = Background) {
            val request = state.playing
            when {
                Vlc.factory == null -> VlcMissing()
                request != null -> PlayerScreen(state, request, window)
                else -> Shell(state)
            }
        }
    }
}

@Composable
private fun Shell(state: AppState) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = Panel) {
            Spacer(Modifier.height(8.dp))
            Text("ELM", style = MaterialTheme.typography.titleMedium, color = Accent)
            Spacer(Modifier.height(12.dp))
            val hasVod = state.playlist?.isXtream == true || state.content?.let { it.m3uMovies.isNotEmpty() || it.m3uSeries.isNotEmpty() } == true
            RailItem(state, Screen.Live, Icons.Filled.LiveTv, tx("Canlı TV", "Live TV"))
            RailItem(state, Screen.Guide, Icons.Filled.ViewTimeline, tx("Rehber", "Guide"))
            if (hasVod) {
                RailItem(state, Screen.Movies, Icons.Filled.Movie, tx("Filmler", "Films"))
                RailItem(state, Screen.Series, Icons.Filled.VideoLibrary, tx("Diziler", "Series"))
            }
            RailItem(state, Screen.Search, Icons.Filled.Search, tx("Ara", "Search"))
            RailItem(state, Screen.Playlists, Icons.AutoMirrored.Filled.List, tx("Listeler", "Playlists"))
            RailItem(state, Screen.Settings, Icons.Filled.Settings, tx("Ayarlar", "Settings"))
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.data.playlists.isEmpty() && state.screen != Screen.Settings) {
                    PlaylistsScreen(state, welcome = true)
                } else {
                    when (state.screen) {
                        Screen.Live -> LiveScreen(state)
                        Screen.Guide -> GuideScreen(state)
                        Screen.Movies -> VodScreen(state, VodKind.MOVIE)
                        Screen.Series -> VodScreen(state, VodKind.SERIES)
                        Screen.Search -> SearchScreen(state)
                        Screen.Playlists -> PlaylistsScreen(state, welcome = false)
                        Screen.Settings -> SettingsScreen(state)
                    }
                }
            }
            StatusBar(state)
        }
    }
}

@Composable
private fun RailItem(state: AppState, screen: Screen, icon: ImageVector, label: String) {
    NavigationRailItem(
        selected = state.screen == screen,
        onClick = { state.screen = screen },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, maxLines = 1) },
    )
}

/** The bottom line: the playlist, what is loading, and messages (which go away by themselves). */
@Composable
private fun StatusBar(state: AppState) {
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(6_000)
            if (state.message == message) state.message = null
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val p = state.playlist
        val c = state.content
        Text(
            listOfNotNull(
                p?.name,
                c?.let { tx("${it.live.size} kanal", "${it.live.size} channels") },
                state.guide.takeIf { it.linkedChannels > 0 }?.let { tx("rehber: ${it.linkedChannels} kanal", "guide: ${it.linkedChannels} channels") },
                c?.account?.expiresAt?.let { tx("bitiş: ", "expires: ") + java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date(it * 1000)) },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        state.busy?.let {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        if (state.busy == null && p != null) {
            IconButton(onClick = { state.refresh() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = tx("Yenile (F5)", "Refresh (F5)"), tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    message?.let {
        Snackbar(
            modifier = Modifier.padding(12.dp),
            action = { TextButton(onClick = { state.message = null }) { Text(tx("Tamam", "OK")) } },
        ) { Text(it) }
    }
}

@Composable
private fun VlcMissing() {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(tx("VLC gerekli", "VLC is needed"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            tx(
                "ELM IPTV yayınları bilgisayarınızdaki VLC ile oynatır. VLC 3'ü kurup uygulamayı yeniden açın.",
                "ELM IPTV plays streams with the VLC on your computer. Install VLC 3 and open the app again.",
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text("Windows: https://www.videolan.org/vlc/", color = Accent)
        Text("Linux: sudo apt install vlc  ·  sudo dnf install vlc  ·  sudo pacman -S vlc", color = Accent)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://www.videolan.org/vlc/")) } }) {
            Text(tx("İndirme sayfasını aç", "Open the download page"))
        }
    }
}

/** A channel logo or poster, with the initials when there is none or it does not load. */
@Composable
fun Logo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Panel, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        Text(initials(name), style = MaterialTheme.typography.labelMedium, color = Muted)
        if (!url.isNullOrBlank()) {
            coil3.compose.AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp))
        }
    }
}

fun initials(name: String): String =
    name.split(' ', '-', '_', '|', ':').filter { it.isNotBlank() && it[0].isLetterOrDigit() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

@Composable
fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Muted) }
}

@Composable
fun ScreenTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SidePanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(modifier.fillMaxHeight().background(Panel)) { content() }
}
