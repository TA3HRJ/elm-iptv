package io.github.ta3hrj.elm.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dev aid: renders screens to PNG files (run with -Dshots=DIR). -Ddemo=1 fills the list with made-up channel
 * names for the website; -Dlang=en renders in English. The file is named after -Dscreen (default "home").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ElmApplication::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
open class ShotTest {
    /** Added to the file name (the phone variant writes `home-phone.png`). */
    protected open val suffix = ""

    @get:Rule val compose = createComposeRule()

    @Test
    fun shots() {
        val dir = System.getProperty("shots") ?: return
        val app = ApplicationProvider.getApplicationContext<Context>()
        val english = System.getProperty("lang") == "en"
        Lang.apply(if (english) Lang.Choice.ENGLISH else Lang.Choice.TURKISH)
        val demo = System.getProperty("demo") != null
        val id = runBlocking {
            val db = app.container.database
            val id = db.playlists().insert(PlaylistEntity(name = System.getProperty("listName") ?: "Ev listesi", type = PlaylistType.XTREAM, url = "http://x", channelCount = 24, movieCount = 12, seriesCount = 6))
            db.channels().insertAll((0 until 24).map { i ->
                val name = if (demo) DEMO_CHANNELS[i % DEMO_CHANNELS.size] else "Kanal ${i + 1} HD Uzun Kanal Adı Örneği"
                val group = if (demo) DEMO_GROUPS[i % DEMO_GROUPS.size].let { if (english) it.second else it.first } else listOf("Ulusal", "Haber")[i % 2]
                ChannelEntity(playlistId = id, position = i, name = name, groupTitle = group, logo = null,
                    streamUrl = "http://x/$i", epgId = null, userAgent = null, catchupDays = 0, number = i + 1)
            })
            id
        }
        val vm = MainViewModel(app as android.app.Application)
        vm.selectPlaylist(id)
        app.container.settings.setUiSize(System.getProperty("size")?.toInt() ?: 3)
        compose.setContent {
            AppTheme {
                // The window background is not part of the captured root; paint it so the shot looks like the TV.
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(AppBackground)) {
                when (System.getProperty("screen")) {
                    "guide" -> GuideScreen(vm, onPlay = { _, _ -> })
                    "sections" -> SectionsScreen(vm, {}, {}, {}, {}, {}, {})
                    "about" -> AboutScreen(onBack = {})
                    "settings" -> SettingsScreen(vm, onBack = {}, onOpenPlaylists = {}, onOpenRecordings = {}, onOpenBackup = {}, onOpenErrorLog = {}, onOpenProfiles = {}, onExit = {})
                    else -> HomeScreen(vm, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
                }
                }
            }
        }
        repeat(20) { compose.waitForIdle(); Thread.sleep(50) }
        // Optional key presses before the shot, e.g. -Dkeys=RDDDDDDD (R right, L left, D down, U up, C OK).
        for (c in System.getProperty("keys").orEmpty()) {
            val key = when (c) { 'R' -> androidx.compose.ui.input.key.Key.DirectionRight; 'L' -> androidx.compose.ui.input.key.Key.DirectionLeft; 'U' -> androidx.compose.ui.input.key.Key.DirectionUp; 'C' -> androidx.compose.ui.input.key.Key.DirectionCenter; else -> androidx.compose.ui.input.key.Key.DirectionDown }
            compose.onRoot().performKeyInput { pressKey(key) }
            repeat(3) { compose.waitForIdle(); Thread.sleep(30) }
        }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir).mkdirs()
        File(dir, "${System.getProperty("screen") ?: "home"}$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** Made-up names only: screenshots for the website must not show real channels or brands. */
        val DEMO_CHANNELS = listOf(
            "Gündem Haber", "Spor Bir", "Belgesel Dünyası", "Çocuk Saati", "Müzik Hit", "Sinema Gecesi", "Doğa ve Yaşam", "Yemek Atölyesi",
            "Ekonomi Gündem", "Spor İki", "Tarih Kanalı", "Komedi Plus", "Seyahat TV", "Bilim Kanalı", "Klasik Film", "Haber Plus",
            "Otomobil TV", "Sanat Kanalı", "Basketbol HD", "Çizgi Dünya", "Caz FM TV", "Dizi Plus", "Hava Durumu", "Kültür Yolu",
        )
        val DEMO_GROUPS = listOf("Ulusal" to "National", "Haber" to "News", "Spor" to "Sports", "Belgesel" to "Documentary")
    }
}

/** The same shots on a phone held upright (run with -Dshots=DIR as well). */
@Config(application = ElmApplication::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class PhoneShotTest : ShotTest() {
    override val suffix = "-phone"
}

/** And on a phone turned sideways. */
@Config(application = ElmApplication::class, qualifiers = "w891dp-h411dp-land-xxhdpi")
class PhoneLandShotTest : ShotTest() {
    override val suffix = "-phone-land"
}
