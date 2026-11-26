package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.ui.touch.Button
import io.github.ta3hrj.elm.ui.touch.ListItem
import io.github.ta3hrj.elm.ui.touch.Surface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose for TV's buttons and rows react to the remote only. The app's versions of them also answer a finger
 * (phones and tablets), and the remote still works the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-port")
@OptIn(ExperimentalTestApi::class)
class TouchClickTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTapClicksAndALongPressOpensTheOptions() {
        val events = mutableListOf<String>()
        compose.setContent {
            Column {
                Button(onClick = { events += "button" }, modifier = Modifier.testTag("button")) { Text("Button") }
                ListItem(
                    selected = false,
                    onClick = { events += "row" },
                    onLongClick = { events += "row options" },
                    headlineContent = { Text("Row") },
                    modifier = Modifier.testTag("row"),
                )
                Surface(onClick = { events += "card" }, modifier = Modifier.testTag("card")) { Text("Card") }
            }
        }
        compose.onNodeWithTag("button").performTouchInput { click() }
        compose.onNodeWithTag("row").performTouchInput { click() }
        compose.onNodeWithTag("row").performTouchInput { longClick() }
        compose.onNodeWithTag("card").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf("button", "row", "row options", "card"), events)
    }

    @Test
    fun aTapFocusesTheItemSoTheRemoteCarriesOnFromThere() {
        val events = mutableListOf<String>()
        compose.setContent {
            Column {
                Button(onClick = { events += "first" }, modifier = Modifier.testTag("first")) { Text("First") }
                Button(onClick = { events += "second" }, modifier = Modifier.testTag("second")) { Text("Second") }
            }
        }
        compose.onNodeWithTag("second").performTouchInput { click() }
        // OK on the remote now presses the tapped button again.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("second", "second"), events)
    }

    @Test
    fun aDisabledButtonIgnoresTaps() {
        val events = mutableListOf<String>()
        compose.setContent {
            Button(onClick = { events += "tap" }, enabled = false, modifier = Modifier.testTag("off")) { Text("Off") }
        }
        compose.onNodeWithTag("off").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), events)
    }
}
