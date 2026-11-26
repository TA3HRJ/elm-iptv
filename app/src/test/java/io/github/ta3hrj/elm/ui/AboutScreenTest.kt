package io.github.ta3hrj.elm.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.ta3hrj.elm.core.Lang
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The About text is longer than a TV screen; the remote's Down key must reach its end. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land")
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun english() = Lang.apply(Lang.Choice.ENGLISH)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun downKeyScrollsToTheLastSection() {
        compose.setContent { AboutScreen(onBack = {}) }
        compose.waitForIdle()
        repeat(12) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        compose.onNodeWithText("not affiliated with", substring = true).assertIsDisplayed()
    }
}
