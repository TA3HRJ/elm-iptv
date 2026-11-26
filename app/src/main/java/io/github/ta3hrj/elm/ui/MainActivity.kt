package io.github.ta3hrj.elm.ui

import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.AppSettings
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import androidx.tv.material3.darkColorScheme
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.VodKinds
import io.github.ta3hrj.elm.data.PlaylistType
import io.github.ta3hrj.elm.data.VodItemEntity
import io.github.ta3hrj.elm.core.tx
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.player.PlayerActivity
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

enum class Screen { EpgMatch, Sections, Welcome, Archive, ErrorLog, Home, GuideHome, Guide, Vod, VodDetail, Playlists, AddPlaylist, GuideSettings, Settings, Search, Recordings, Backup, About, Profiles, ProfileEdit }

val Accent = Color(0xFF4FA3FF)
val AppBackground = Color(0xFF0E1116)
val TextPrimary = Color(0xFFE8ECF2)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !openDeepLink(intent)) resumeLastChannel()
        // Keep the playlist current while the app is on screen; TV apps often stay open for days.
        // Not while the player is in front, so updates never compete with the stream for a connection.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshIfStale()
                    delay(30 * 60_000L)
                }
            }
        }
        setContent {
            AppTheme {
                Box(
                    Modifier.fillMaxSize().background(AppBackground).onPreviewKeyEvent { event ->
                        // Someone is at the remote: the live preview's "Are you still there?" starts counting again.
                        val native = event.nativeKeyEvent
                        if (native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            KeyPresses.pressed(native.keyCode == android.view.KeyEvent.KEYCODE_BACK)
                        }
                        false
                    },
                ) {
                    // "Exit" closes the app and takes it off the recent apps list, so it is clearly closed.
                    App(viewModel, onBack = { finishAndRemoveTask() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDeepLink(intent)
    }

    /** Android TV "continue watching" entries open the player directly. */
    private fun openDeepLink(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "elmiptv") return false
        io.github.ta3hrj.elm.player.MediaRequest.open(this, uri)
        return true
    }

    /** "Continue with the last channel" on a fresh start from the launcher. */
    private fun resumeLastChannel() {
        if (intent?.action != Intent.ACTION_MAIN) return
        lifecycleScope.launch {
            // With several profiles, "Who's watching?" comes first; the chosen profile's last channel follows.
            if (viewModel.askProfileAtStart(resume = true)) return@launch
            viewModel.resumeLastChannel()
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // The chosen size scales the whole interface (text, rows and spacing) the same way, so it stays in proportion.
    val settings = androidx.compose.ui.platform.LocalContext.current.container.settings
    val size by settings.uiSize.collectAsStateWithLifecycle()
    val base = androidx.compose.ui.platform.LocalDensity.current
    val scale = AppSettings.UI_SCALES.getOrElse(size) { 1f }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale)) {
        ThemedContent(content)
    }
}

@Composable
private fun ThemedContent(content: @Composable () -> Unit) {
    // Compose for TV components (lists, buttons) use the TV theme; text fields come from Material 3.
    M3Theme(colorScheme = m3DarkColorScheme(primary = Accent, background = AppBackground, surface = AppBackground)) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                background = AppBackground,
                surface = Color(0xFF161B22),
                onBackground = TextPrimary,
                onSurface = TextPrimary,
            ),
        ) {
            // TV text outside a Surface would otherwise default to black, which is unreadable on the dark background.
            CompositionLocalProvider(LocalContentColor provides TextPrimary) { ProvideFormFactor(content) }
        }
    }
}

@Composable
private fun App(viewModel: MainViewModel, onBack: () -> Unit) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val homeView by viewModel.homeView.collectAsStateWithLifecycle()
    val currentPlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val home = when (homeView) {
        MainViewModel.HOME_GUIDE -> Screen.GuideHome
        MainViewModel.HOME_SECTIONS -> Screen.Sections
        else -> Screen.Home
    }
    var screen by rememberSaveable { mutableStateOf(home) }
    var guideSettingsFor by rememberSaveable { mutableLongStateOf(-1L) }
    var vodItemId by rememberSaveable { mutableLongStateOf(-1L) }
    var vodItemIsSeries by rememberSaveable { mutableStateOf(false) }
    /** Where Back from a film/series detail page returns to. */
    var vodBack by rememberSaveable { mutableStateOf(Screen.Vod) }
    /** Where Back from a channel archive returns to. */
    var archiveBack by rememberSaveable { mutableStateOf(Screen.Home) }
    var editProfileId by rememberSaveable { mutableLongStateOf(-1L) }
    var editPlaylistId by rememberSaveable { mutableLongStateOf(-1L) }
    /** Where Back from the guide match screen returns to. */
    var epgMatchBack by rememberSaveable { mutableStateOf(Screen.Home) }
    val epgMatchChannel by viewModel.epgMatchChannel.collectAsStateWithLifecycle()
    var askExit by remember { mutableStateOf(false) }
    val askProfile by viewModel.askProfile.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    // The splash stays for a moment even when the database answers at once, so it doesn't just flicker.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900)
        splashDone = true
    }
    val list = playlists
    if (list == null || !splashDone) {
        SplashScreen()
        return
    }

    // With no playlists, start with the welcome screen.
    LaunchedEffect(list.isEmpty()) {
        if (list.isEmpty() && screen != Screen.AddPlaylist && screen != Screen.Backup) screen = Screen.Welcome
        if (list.isNotEmpty() && screen == Screen.Welcome) screen = home
    }

    var askSettingsPin by remember { mutableStateOf(false) }
    val openSettings = {
        if (viewModel.settingsNeedPin()) askSettingsPin = true else screen = Screen.Settings
    }

    BackHandler {
        when {
            list.isEmpty() && screen == Screen.Welcome -> askExit = true
            list.isEmpty() && (screen == Screen.AddPlaylist || screen == Screen.Backup) -> screen = Screen.Welcome
            screen == Screen.AddPlaylist -> screen = if (list.isEmpty()) Screen.Welcome else Screen.Playlists
            screen == Screen.VodDetail -> screen = vodBack
            screen == Screen.Archive -> screen = archiveBack
            screen == Screen.EpgMatch -> screen = epgMatchBack
            screen == Screen.GuideSettings -> screen = Screen.Playlists
            screen == Screen.ProfileEdit -> screen = Screen.Profiles
            screen in listOf(Screen.Playlists, Screen.Backup, Screen.ErrorLog, Screen.Profiles) -> screen = Screen.Settings
            screen == Screen.Recordings -> screen = if (activeProfile.kids) Screen.Settings else home
            screen == home -> askExit = true
            else -> screen = home
        }
    }

    // Updates: asked at start (at most once a day); the dialogs show over any screen.
    LaunchedEffect(Unit) { viewModel.checkForUpdate(manual = false) }
    UpdateDialogs(viewModel)

    if (askExit) {
        OptionsDialog(
            title = tx("ELM IPTV'den çıkılsın mı?", "Exit ELM IPTV?"),
            options = listOf(DialogOption(tx("Çık", "Exit"), Icons.AutoMirrored.Filled.ExitToApp) { onBack() }),
            onDismiss = { askExit = false },
        )
    }

    if (askSettingsPin) {
        PinDialog(
            tx("PIN girin", "Enter PIN"),
            tx("Ayarlar PIN ile korunuyor", "Settings are protected by the PIN"),
            onSubmit = { pin -> viewModel.unlock(pin).also { if (it) screen = Screen.Settings } },
            onDismiss = { askSettingsPin = false },
        )
    }

    val play: (ChannelEntity, ChannelQuery) -> Unit = { channel, query ->
        PlayerActivity.start(
            context = viewModel.getApplication(),
            playlistId = channel.playlistId,
            query = query,
            channelId = channel.id,
        )
    }
    val openArchive: (ChannelEntity) -> Unit = { channel ->
        viewModel.openArchive(channel)
        archiveBack = screen
        screen = Screen.Archive
    }
    val openEpgMatch: (ChannelEntity) -> Unit = { channel ->
        viewModel.openEpgMatch(channel)
        epgMatchBack = screen
        screen = Screen.EpgMatch
    }
    val openVod: (String) -> Unit = { kind ->
        viewModel.showVod(kind)
        screen = Screen.Vod
    }
    val openItem: (VodItemEntity, Screen) -> Unit = { item, back ->
        vodItemId = item.id
        vodItemIsSeries = item.kind == VodKinds.SERIES
        vodBack = back
        screen = Screen.VodDetail
    }

    // A profile switch returns to the start screen (the old screen may show the previous profile's lists).
    var shownProfile by rememberSaveable { mutableLongStateOf(activeProfile.id) }
    LaunchedEffect(activeProfile.id) {
        if (activeProfile.id == shownProfile) return@LaunchedEffect
        shownProfile = activeProfile.id
        if (screen != Screen.Welcome && screen != Screen.Profiles && screen != Screen.ProfileEdit) screen = home
    }
    if (askProfile && list.isNotEmpty()) {
        ProfilePicker(viewModel)
        return
    }

    when (screen) {
        Screen.EpgMatch -> epgMatchChannel?.let { channel ->
            EpgMatchScreen(viewModel, channel, onDone = { screen = epgMatchBack })
        } ?: LaunchedEffect(Unit) { screen = epgMatchBack }
        Screen.Sections -> SectionsScreen(
            viewModel,
            onLive = { screen = Screen.Home },
            onGuide = { screen = Screen.Guide },
            onVod = openVod,
            onArchive = {
                viewModel.selectGroup(ChannelQuery.Archive)
                screen = Screen.Home
            },
            onOpenSettings = openSettings,
            onOpenAbout = { screen = Screen.About },
        )
        Screen.Profiles -> ProfilesScreen(
            viewModel,
            onEdit = { id ->
                editProfileId = id
                screen = Screen.ProfileEdit
            },
            onBack = { screen = Screen.Settings },
        )
        Screen.ProfileEdit -> ProfileEditScreen(viewModel, editProfileId, onDone = { screen = Screen.Profiles })
        Screen.Archive -> ArchiveScreen(
            viewModel,
            onPlayLive = { play(it, ChannelQuery.Group(it.groupTitle)) },
            onBack = { screen = archiveBack },
        )
        Screen.Welcome -> WelcomeScreen(viewModel, onAddPlaylist = { editPlaylistId = -1L; screen = Screen.AddPlaylist }, onRestore = { screen = Screen.Backup })
        Screen.Home -> HomeScreen(
            viewModel = viewModel,
            onOpenGuide = { screen = Screen.Guide },
            onOpenVod = openVod,
            onOpenSearch = { screen = Screen.Search },
            onOpenRecordings = { screen = Screen.Recordings },
            onOpenSettings = openSettings,
            onOpenAbout = { screen = Screen.About },
            onPlay = play,
            onOpenArchive = openArchive,
            onOpenEpgMatch = openEpgMatch,
        )
        Screen.GuideHome -> GuideScreen(viewModel = viewModel, onPlay = play, onOpenArchive = openArchive, onOpenEpgMatch = openEpgMatch, preview = true, onOpenAbout = { screen = Screen.About },
            settingsAction = { compact -> HeaderButton(Icons.Filled.Settings, tx("Ayarlar", "Settings"), compact, onClick = openSettings) },
            headerActions = { compact ->
            val hasVod = currentPlaylist?.let { it.type == PlaylistType.XTREAM || it.movieCount + it.seriesCount > 0 } == true
            HeaderButton(Icons.AutoMirrored.Filled.List, tx("Kanallar", "Channels"), compact) { screen = Screen.Home }
            if (hasVod) {
                HeaderButton(Icons.Filled.Movie, tx("Filmler", "Films"), compact) { openVod(VodKinds.MOVIE) }
                HeaderButton(Icons.Filled.VideoLibrary, tx("Diziler", "Series"), compact) { openVod(VodKinds.SERIES) }
            }
            HeaderButton(Icons.Filled.Search, tx("Ara", "Search"), compact) { screen = Screen.Search }
            ProfileButton(viewModel, compact = compact)
            RefreshButton(viewModel, compact = compact)
            Spacer(Modifier.width(8.dp))
        })
        Screen.Guide -> GuideScreen(viewModel = viewModel, onPlay = play, onOpenArchive = openArchive, onOpenEpgMatch = openEpgMatch)
        Screen.Settings -> SettingsScreen(
            viewModel,
            onBack = { screen = home },
            onOpenPlaylists = { screen = Screen.Playlists },
            onOpenRecordings = { screen = Screen.Recordings },
            onOpenBackup = { screen = Screen.Backup },
            onOpenErrorLog = { screen = Screen.ErrorLog },
            onOpenProfiles = { screen = Screen.Profiles },
            onExit = { askExit = true },
        )
        Screen.ErrorLog -> ErrorLogScreen(viewModel, onBack = { screen = Screen.Settings })
        Screen.Search -> SearchScreen(viewModel, onPlay = play, onOpenVod = { openItem(it, Screen.Search) }, onBack = { screen = home })
        Screen.Recordings -> RecordingsScreen(viewModel, onBack = { screen = if (activeProfile.kids) Screen.Settings else home })
        Screen.Backup -> BackupScreen(viewModel, onBack = { screen = if (list.isEmpty()) Screen.Welcome else Screen.Settings })
        Screen.About -> AboutScreen(onBack = { screen = home })
        Screen.Vod -> VodBrowseScreen(
            viewModel = viewModel,
            onOpen = { openItem(it, Screen.Vod) },
            onLive = { screen = home },
        )
        Screen.VodDetail -> if (vodItemIsSeries) {
            SeriesDetailScreen(viewModel, vodItemId, onBack = { screen = vodBack })
        } else {
            MovieDetailScreen(viewModel, vodItemId, onBack = { screen = vodBack })
        }
        Screen.Playlists -> PlaylistsScreen(
            viewModel = viewModel,
            onAdd = {
                editPlaylistId = -1L
                screen = Screen.AddPlaylist
            },
            onEdit = { id ->
                editPlaylistId = id
                screen = Screen.AddPlaylist
            },
            onSelected = { screen = home },
            onGuideSettings = { id ->
                guideSettingsFor = id
                screen = Screen.GuideSettings
            },
        )
        Screen.GuideSettings -> GuideSettingsScreen(
            viewModel = viewModel,
            playlistId = guideSettingsFor,
            onDone = { screen = Screen.Playlists },
        )
        Screen.AddPlaylist -> AddPlaylistScreen(
            viewModel = viewModel,
            canCancel = true,
            onDone = { screen = if (editPlaylistId >= 0) Screen.Playlists else home },
            onCancel = { screen = if (list.isEmpty()) Screen.Welcome else Screen.Playlists },
            editId = editPlaylistId.takeIf { it >= 0 },
        )
    }
}
