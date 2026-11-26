package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With large text the header must shrink and scroll, but Settings has to stay on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1600dp-h900dp-land")
class AdaptiveHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val fit = mutableIntStateOf(-1)

    private fun header(width: Dp, screen: String, buttons: Int) = compose.setContent {
        Box(Modifier.width(width).testTag("header")) {
            AdaptiveHeader(
                screen = screen,
                leading = { level -> fit.intValue = level; Text("ELM IPTV") },
                nav = { compact ->
                    repeat(buttons) { HeaderButton(Icons.Filled.LiveTv, "Section number $it", compact) {} }
                },
                trailing = { level -> HeaderButton(Icons.Filled.Settings, "Settings", level >= 2) {} },
            )
        }
    }

    @Test
    fun settingsStaysOnScreenWhenTheHeaderIsTooNarrow() {
        header(500.dp, "narrow", buttons = 8)
        compose.waitForIdle()
        assertEquals(2, fit.intValue)
        val settings = compose.onNodeWithContentDescription("Settings").assertIsDisplayed().getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        assertTrue("Settings at ${settings.right}, header ends at ${header.right}", settings.right <= header.right)
    }

    @Test
    fun wideHeaderKeepsItsLabels() {
        header(1600.dp, "wide", buttons = 3)
        compose.waitForIdle()
        assertEquals(0, fit.intValue)
        compose.onNodeWithText("Section number 0").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
