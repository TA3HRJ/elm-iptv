package io.github.ta3hrj.elm.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Every remote key pressed on the main screens (true for Back). The live preview listens to it to tell
 * whether anyone is still watching, the way the full-screen player does with its own keys.
 */
object KeyPresses {
    private val presses = MutableSharedFlow<Boolean>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow: SharedFlow<Boolean> = presses

    fun pressed(isBack: Boolean) {
        presses.tryEmit(isBack)
    }
}
