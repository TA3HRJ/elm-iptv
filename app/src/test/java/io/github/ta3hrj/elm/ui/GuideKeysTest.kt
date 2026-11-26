package io.github.ta3hrj.elm.ui

import android.content.Context
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Right on the last programme of a row moves the guide's time window; it must not jump to the header
 * buttons above (which lie further right on the screen), as it did before 0.9.19.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ElmApplication::class, qualifiers = "w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalTestApi::class)
class GuideKeysTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rightAtTheEndOfARowStaysInTheGuide() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        Lang.apply(Lang.Choice.TURKISH)
        val id = runBlocking {
            val db = app.container.database
            val id = db.playlists().insert(PlaylistEntity(name = "Liste", type = PlaylistType.M3U, url = "http://example.invalid", channelCount = 3))
            db.channels().insertAll((0 until 3).map { i ->
                ChannelEntity(playlistId = id, position = i, name = "Kanal ${i + 1}", groupTitle = "Ulusal", logo = null,
                    streamUrl = "http://example.invalid/$i", epgId = null, userAgent = null, catchupDays = 0, number = i + 1)
            })
            id
        }
        val viewModel = MainViewModel(app as android.app.Application)
        viewModel.selectPlaylist(id)
        compose.setContent { AppTheme { GuideScreen(viewModel = viewModel, onPlay = { _, _ -> }) } }
        repeat(20) { compose.waitForIdle(); Thread.sleep(30) }

        val start = focusedText()
        assertTrue("the guide grid should have the focus at start, not $start", start == "Program bilgisi yok")
        repeat(3) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.waitForIdle()
            assertNotEquals("Right jumped out of the guide grid", "1 saat ▶", focusedText())
            assertTrue("focus left the guide grid: ${focusedText()}", focusedText() == "Program bilgisi yok")
        }
    }

    private fun focusedText(): String? {
        fun SemanticsNode.all(): List<SemanticsNode> = listOf(this) + children.flatMap { it.all() }
        fun SemanticsNode.text(): String? = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: children.firstNotNullOfOrNull { it.text() }
        return compose.onRoot().fetchSemanticsNode().all().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.text()
    }
}
