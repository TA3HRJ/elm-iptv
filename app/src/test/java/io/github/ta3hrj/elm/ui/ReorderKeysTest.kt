package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Finishing "Change order" with OK must not also open the channel (its release used to reach the row). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class)
class ReorderKeysTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun okFinishesMovingWithoutPlayingTheChannel() {
        var moving by mutableStateOf(true)
        val played = mutableListOf<String>()
        val moves = mutableListOf<Int>()
        val first = FocusRequester()
        compose.setContent {
            LazyColumn(Modifier.reorderKeys(active = { moving }, onMove = { moves += it }, onDone = { moving = false })) {
                items(listOf("A", "B", "C")) { name ->
                    ListItem(
                        selected = false,
                        onClick = { played += name },
                        headlineContent = { Text(name) },
                        modifier = if (name == "B") Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf(-1), moves)
        assertFalse("still moving", moving)
        assertEquals("OK that finished moving must not play", emptyList<String>(), played)

        // After that, OK plays as usual.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertTrue(played.isNotEmpty())
    }
}
