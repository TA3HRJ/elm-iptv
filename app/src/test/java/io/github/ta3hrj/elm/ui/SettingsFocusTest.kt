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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pressing OK on a choice in Settings must keep the focus where it is. It used to fall back to the first
 * entry of the section menu (which then opened "Accounts and system") because the pressed button was
 * replaced by a different kind of button when it became the selected one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ElmApplication::class, qualifiers = "w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalTestApi::class)
class SettingsFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { all(it) }

    private fun texts(): List<String> =
        all(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()).mapNotNull {
            it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { t -> t.text }
        }

    private fun focusedText(): String? {
        fun text(n: SemanticsNode): String? = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: n.children.firstNotNullOfOrNull { text(it) }
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        return all(root).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let { text(it) }
    }

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun okOnAPlaybackChoiceKeepsTheFocusAndTheSection() {
        Lang.apply(Lang.Choice.TURKISH)
        val viewModel = MainViewModel(app as android.app.Application)
        compose.setContent {
            AppTheme {
                SettingsScreen(viewModel, onBack = {}, onOpenPlaylists = {}, onOpenRecordings = {}, onOpenBackup = {}, onOpenErrorLog = {}, onOpenProfiles = {}, onExit = {})
            }
        }
        repeat(10) { compose.waitForIdle(); Thread.sleep(30) }

        // Menu: Accounts, General, Playback. Moving onto Playback shows it.
        press(Key.DirectionDown, 2)
        assertTrue("Playback should be shown: ${texts()}", "Tampon" in texts())
        // Into the content: the first choice the focus finds ("Are you still watching?" is the first row).
        press(Key.DirectionRight)
        val choice = focusedText()!!
        assertFalse("expected an unselected choice, got $choice", choice.startsWith("✓"))
        val idleBefore = app.container.settings.idleHours.value

        press(Key.DirectionCenter)

        assertTrue("the choice was not applied", app.container.settings.idleHours.value != idleBefore)
        assertTrue("the Playback section closed: ${texts()}", "Tampon" in texts())
        assertFalse("jumped to Accounts and system: ${texts()}", "Hesaplar ve listeler" in texts())
        // The focus is still on the choice that was pressed, now marked as selected.
        assertEquals("✓ $choice", focusedText())
    }
}
