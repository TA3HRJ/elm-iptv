package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** After the last item of a list, Down goes round to the first one (one item row at a time, never while held). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class)
class WrapDownTest {
    @get:Rule
    val compose = createComposeRule()

    private fun all(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { all(it) }
    private fun focused(): String? {
        fun text(n: SemanticsNode): String? = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: n.children.firstNotNullOfOrNull { text(it) }
        return all(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let { text(it) }
    }

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        Thread.sleep(20)
        compose.waitForIdle()
    }

    @Test
    fun downOnTheLastItemGoesToTheFirstEvenWhenItIsScrolledAway() {
        val items = (1..30).map { "Kanal $it" }
        val first = FocusRequester()
        compose.setContent {
            val state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.wrapDown(state, first)) {
                itemsIndexed(items) { i, name ->
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text(name) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        assertEquals("Kanal 1", focused())

        press(Key.DirectionDown, 29)
        assertEquals("Kanal 30", focused())

        // The last item: Down goes round, although item 1 has been scrolled out of the list.
        press(Key.DirectionDown)
        assertEquals("Kanal 1", focused())
        // And on from there as usual.
        press(Key.DirectionDown)
        assertEquals("Kanal 2", focused())
    }
}
