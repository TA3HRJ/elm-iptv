package io.github.ta3hrj.elm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.ElmApplication
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.PlaylistEntity
import io.github.ta3hrj.elm.data.PlaylistType
import io.github.ta3hrj.elm.data.VodItemEntity
import io.github.ta3hrj.elm.data.VodKinds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens every screen on a Fire TV-sized display (1920x1080 = 960x540 dp) and moves around it with the
 * remote's arrow keys only. Reports what the user could not reach that way:
 * - text or buttons cut off at the screen edge outside any scrolling area, or squeezed to nothing by
 *   their neighbours (the Refresh and Settings buttons at "Extra large" before 0.9.16);
 * - scrolling areas the focus never enters while part of their text stays out of view (the About
 *   screen before 0.9.18).
 * Whether every single button can take the focus is not checked: arrow-key walks cannot try every path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ElmApplication::class, qualifiers = "w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalTestApi::class)
class RemoteReachabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var viewModel: MainViewModel
    private var playlistId = 0L
    private var firstChannel: ChannelEntity? = null

    @Before
    fun seed() = runBlocking {
        Lang.apply(Lang.Choice.TURKISH)
        val db = app.container.database
        playlistId = db.playlists().insert(
            PlaylistEntity(
                name = "Ev listesi (çok uzun bir liste adı örneği)", type = PlaylistType.XTREAM, url = "http://example.invalid",
                username = "u", password = "p", channelCount = 24, movieCount = 12, seriesCount = 6,
                updatedAt = System.currentTimeMillis(), expiresAt = System.currentTimeMillis() / 1000 + 86_400 * 30,
            ),
        )
        val groups = listOf("Ulusal", "Haber", "Spor", "Belgesel Kanalları ve Uzun Kategori Adı")
        db.channels().insertAll(
            (0 until 24).map { i ->
                ChannelEntity(
                    playlistId = playlistId, position = i, name = "Kanal ${i + 1} HD Uzun Kanal Adı Örneği", groupTitle = groups[i % groups.size],
                    logo = null, streamUrl = "http://example.invalid/$i", epgId = null, userAgent = null,
                    catchupDays = if (i % 3 == 0) 3 else 0, number = i + 1,
                )
            },
        )
        db.vod().insertAll(
            (0 until 12).map { i ->
                VodItemEntity(
                    playlistId = playlistId, kind = VodKinds.MOVIE, providerId = "m$i", position = i, name = "Film $i: Çok Uzun Bir Film Adı",
                    category = if (i < 6) "Aksiyon" else "Dram", poster = null, rating = "7.${i % 10}", year = "20${10 + i}",
                    containerExtension = "mp4", added = null,
                )
            } + (0 until 6).map { i ->
                VodItemEntity(
                    playlistId = playlistId, kind = VodKinds.SERIES, providerId = "s$i", position = i, name = "Dizi $i",
                    category = "Yerli", poster = null, rating = null, year = "2020", containerExtension = null, added = null,
                )
            },
        )
        firstChannel = db.channels().all(playlistId).first()
        viewModel = MainViewModel(app as android.app.Application)
        viewModel.selectPlaylist(playlistId)
    }

    /** Start screens: Settings (where the text size is changed back) must always be fully on screen there. */
    private val startScreens = setOf("Ana ekran", "Bölümler")

    /** Screens with a menu on the left whose entries each open their own content on the right. */
    private val withMenu = setOf("Ayarlar", "Ana ekran", "Filmler")

    private val screens: List<Pair<String, @Composable () -> Unit>> by lazy {
        listOf(
            "Ana ekran" to { HomeScreen(viewModel, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) },
            "Bölümler" to { SectionsScreen(viewModel, {}, {}, {}, {}, {}, {}) },
            "TV rehberi" to { GuideScreen(viewModel = viewModel, onPlay = { _, _ -> }) },
            "Ayarlar" to { SettingsScreen(viewModel, onBack = {}, onOpenPlaylists = {}, onOpenRecordings = {}, onOpenBackup = {}, onOpenErrorLog = {}, onOpenProfiles = {}, onExit = {}) },
            "Hata günlüğü" to { ErrorLogScreen(viewModel, onBack = {}) },
            "Ara" to { SearchScreen(viewModel, onPlay = { _, _ -> }, onOpenVod = {}, onBack = {}) },
            "Kayıtlar" to { RecordingsScreen(viewModel, onBack = {}) },
            "Yedekleme" to { BackupScreen(viewModel, onBack = {}) },
            "Hakkında" to { AboutScreen(onBack = {}) },
            "Filmler" to { VodBrowseScreen(viewModel = viewModel, onOpen = {}, onLive = {}) },
            "Hesaplar ve listeler" to { PlaylistsScreen(viewModel = viewModel, onAdd = {}, onSelected = {}, onGuideSettings = {}, onEdit = {}) },
            "Rehber ayarları" to { GuideSettingsScreen(viewModel = viewModel, playlistId = playlistId, onDone = {}) },
            "Liste ekle" to { AddPlaylistScreen(viewModel = viewModel, canCancel = true, onDone = {}, onCancel = {}) },
            "Profiller" to { ProfilesScreen(viewModel, onEdit = {}, onBack = {}) },
            "Profil düzenle" to { ProfileEditScreen(viewModel, 1L, onDone = {}) },
            "Rehberi eşleştir" to { EpgMatchScreen(viewModel, firstChannel!!, onDone = {}) },
        )
    }

    /** The largest text size (where things overflow first) and the default one; one app, one window. */
    @Test
    fun everyScreenIsUsableWithTheRemote() {
        // A diagnostic sweep (about 2 minutes), run before releases rather than on every build:
        // ./gradlew :app:testDebugUnitTest --tests '*RemoteReachabilityTest*' -Dsweep=1
        // Arrow-key walks are heuristics; confirm a finding with ShotTest screenshots before fixing it.
        // Bugs it found get their own regular test (AdaptiveHeaderTest, AboutScreenTest, GuideKeysTest).
        org.junit.Assume.assumeTrue(System.getProperty("sweep") != null)
        var current: (@Composable () -> Unit)? by mutableStateOf(null)
        compose.setContent { AppTheme { current?.invoke() } }
        val problems = mutableListOf<String>()
        for (size in listOf(3, 1)) {
            compose.runOnUiThread { app.container.settings.setUiSize(size) }
            for ((name, screen) in screens) {
                compose.runOnUiThread { current = null }
                compose.waitForIdle()
                compose.runOnUiThread { current = screen }
                settle()
                if (name in startScreens && !settingsOnScreen()) problems += "[boyut $size] $name: Ayarlar düğmesi ekranda değil"
                problems += walk(menu = name in withMenu).map { "[boyut $size] $name: $it" }
            }
        }
        println("Remote reachability:\n" + problems.joinToString("\n").ifEmpty { "no problems" })
        assertTrue("Not usable with the remote:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** Moves through the screen with arrow keys and returns what could not be reached. */
    private fun walk(menu: Boolean): List<String> {
        val focused = mutableSetOf<String>()
        val focusable = mutableMapOf<String, Rect>()
        val scrollEnds = mutableMapOf<Int, Boolean>() // scrolling area (node id) -> its end was reached
        val scrollNames = mutableMapOf<Int, String>()
        val cutOff = mutableSetOf<String>()
        val squeezed = mutableSetOf<String>()
        // Text inside a scrolling area -> whether it was ever shown in that area's visible part.
        val textShown = mutableMapOf<Pair<Int, String>, Boolean>()
        // Scroll position of each area when first seen, and the areas that moved since.
        val firstScroll = mutableMapOf<Int, Float>()
        val areasScrolled = mutableSetOf<Int>()

        fun observe() {
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val screen = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
            for (node in root.allNodes()) {
                val label = node.label()
                for (axis in listOf(SemanticsProperties.VerticalScrollAxisRange, SemanticsProperties.HorizontalScrollAxisRange)) {
                    val range = node.config.getOrNull(axis) ?: continue
                    val key = node.id * 2 + if (axis == SemanticsProperties.VerticalScrollAxisRange) 0 else 1
                    val max = range.maxValue()
                    if (max <= 0f || max.isInfinite()) continue
                    scrollNames.getOrPut(key) { "${if (key % 2 == 0) "↕" else "↔"} ${node.firstText() ?: "#${node.id}"}" }
                    scrollEnds[key] = (scrollEnds[key] ?: false) || range.value() >= max - 1f
                    val start = firstScroll.getOrPut(key) { range.value() }
                    if (range.value() != start) areasScrolled += key
                }
                // Focus sits on the clickable node, which in this (unmerged) tree usually has no text of its own.
                val itemLabel = label ?: node.firstText()
                if (node.config.getOrNull(SemanticsProperties.Focused) == true) {
                    itemLabel?.let { focused += it }
                }
                if (SemanticsProperties.Focused in node.config && itemLabel != null) focusable[itemLabel] = node.boundsInRoot
                if (label == null) continue
                val bounds = Rect(node.positionInRoot, androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
                val outside = bounds.right > screen.right + 2 || bounds.bottom > screen.bottom + 2 || bounds.left < -2 || bounds.top < -2
                val area = node.scrollingArea()
                if (outside && area == null) cutOff += label
                // Squeezed to nothing by its neighbours (a row with more buttons than room).
                if (node.size.width < 2 || node.size.height < 2) squeezed += label
                if (area != null && SemanticsProperties.Text in node.config) {
                    val view = area.boundsInRoot
                    // The focused item is brought into view by the system; its own measured position can lag
                    // behind focus animations, so it counts as shown.
                    val shown = node.inFocusedItem() || bounds.height > 0 &&
                        (minOf(bounds.bottom, view.bottom) - maxOf(bounds.top, view.top)) >= minOf(bounds.height, view.height) * 0.6f
                    val textKey = area.id to label
                    textShown[textKey] = (textShown[textKey] ?: false) || shown
                }
            }
        }

        fun press(key: Key, times: Int) = repeat(times) {
            runCatching { compose.onRoot().performKeyInput { pressKey(key) } }
            compose.waitForIdle()
            observe()
            if (DEBUG) {
                val root = compose.onRoot().fetchSemanticsNode()
                val f = root.allNodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
                println("DBG $key -> ${f?.firstText()} @ ${f?.boundsInRoot}")
            }
        }

        observe()
        // Each column (panel) top to bottom: down and back up, then one step right; then the same leftwards.
        repeat(5) { press(Key.DirectionDown, 32); press(Key.DirectionUp, 34); press(Key.DirectionRight, 1) }
        repeat(5) { press(Key.DirectionLeft, 1); press(Key.DirectionDown, 32); press(Key.DirectionUp, 34) }
        // Menu screens: for each menu entry, step into its content and go through it top to bottom.
        // (Back to the menu with Left, which returns to the entry left last; then Down to the next entry.)
        if (menu) {
            press(Key.DirectionUp, 30); press(Key.DirectionLeft, 8)
            repeat(8) {
                press(Key.DirectionDown, 1); settle(); press(Key.DirectionRight, 1)
                press(Key.DirectionDown, 32); press(Key.DirectionUp, 34); press(Key.DirectionLeft, 8)
            }
        }
        // Row by row: down, then across the row both ways; then the same going up.
        repeat(30) { press(Key.DirectionDown, 1); press(Key.DirectionRight, 8); press(Key.DirectionLeft, 8) }
        repeat(34) { press(Key.DirectionUp, 1); press(Key.DirectionRight, 8); press(Key.DirectionLeft, 8) }

        val problems = mutableListOf<String>()
        cutOff.forEach { problems += "ekrandan taşıyor: \"$it\"" }
        squeezed.forEach { problems += "sığmadığı için görünmüyor: \"$it\"" }
        // An area whose end was never reached is a problem only if some of its text never came into view
        // (a list that ends with empty padding is fine once its last item has been shown).
        // Only areas that never scrolled at all: their content that does not fit cannot be reached (the
        // About screen before 0.9.18). Lists the focus moves through are scrolled by the system.
        scrollEnds.filterValues { !it }.keys.filter { it !in areasScrolled }.forEach { key ->
            // By text: a list whose content changes with the chosen category shows the same channel in
            // several places; it counts once it was on screen anywhere.
            val shownAnywhere = textShown.filterValues { it }.keys.map { it.second }.toSet()
            val hidden = textShown.filter { (k, shown) -> k.first == key / 2 && !shown }.keys.map { it.second }.filter { it !in shownAnywhere }
            if (hidden.isNotEmpty()) problems += "kaydırılan alanın sonuna inilemiyor: ${scrollNames[key]} (görünmeyen: ${hidden.take(3).joinToString(" | ")})"
        }
        return problems
    }

    private val DEBUG = System.getProperty("rr.debug") != null

    private fun settingsOnScreen(): Boolean {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val screen = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
        // The button itself (the clickable node around the label or icon) must be fully on screen.
        return root.allNodes().filter { it.label() == "Ayarlar" }.mapNotNull { label ->
            generateSequence(label) { it.parent }.firstOrNull { androidx.compose.ui.semantics.SemanticsActions.OnClick in it.config }
        }.any { button ->
            val r = Rect(button.positionInRoot, androidx.compose.ui.geometry.Size(button.size.width.toFloat(), button.size.height.toFloat()))
            r.width >= 40 && r.height >= 40 && r.left >= 0 && r.top >= 0 && r.right <= screen.right && r.bottom <= screen.bottom
        }
    }

    /**
     * Waits until the screen stops changing: lists are loaded from the database on background threads,
     * which the test's idle check does not wait for.
     */
    private fun settle() {
        var last = ""
        var same = 0
        repeat(60) {
            compose.waitForIdle()
            val now = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().allNodes().mapNotNull { it.label() }.joinToString("|")
            if (now == last) same++ else same = 0
            if (same >= 3) return
            last = now
            Thread.sleep(40)
        }
    }

    private fun SemanticsNode.allNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.allNodes() }

    private fun SemanticsNode.label(): String? {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return text?.takeIf { it.isNotBlank() }?.take(60)
    }

    private fun SemanticsNode.firstText(): String? = label() ?: children.firstNotNullOfOrNull { it.firstText() }

    private fun SemanticsNode.inFocusedItem(): Boolean {
        var node: SemanticsNode? = this
        while (node != null) {
            if (node.config.getOrNull(SemanticsProperties.Focused) == true) return true
            node = node.parent
        }
        return false
    }

    private fun SemanticsNode.scrollingArea(): SemanticsNode? {
        var node = parent
        while (node != null) {
            if (SemanticsProperties.VerticalScrollAxisRange in node.config || SemanticsProperties.HorizontalScrollAxisRange in node.config) return node
            node = node.parent
        }
        return null
    }
}
