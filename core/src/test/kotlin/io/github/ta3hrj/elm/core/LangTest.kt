package io.github.ta3hrj.elm.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LangTest {
    @After
    fun reset() = Lang.apply(Lang.Choice.TURKISH)

    @Test
    fun choosesLanguage() {
        Lang.apply(Lang.Choice.ENGLISH)
        assertEquals("Hello", tx("Merhaba", "Hello"))
        Lang.apply(Lang.Choice.TURKISH)
        assertEquals("Merhaba", tx("Merhaba", "Hello"))
        Lang.apply(Lang.Choice.SYSTEM, deviceLanguage = "tr")
        assertEquals("Merhaba", tx("Merhaba", "Hello"))
        Lang.apply(Lang.Choice.SYSTEM, deviceLanguage = "de")
        assertEquals("Hello", tx("Merhaba", "Hello"))
    }
}
