package io.github.ta3hrj.elm.ui

import io.github.ta3hrj.elm.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The badge shown for channels without a logo. */
class InitialsTest {
    @Before
    fun turkish() = Lang.apply(Lang.Choice.TURKISH)

    @Test
    fun namesAndNumbers() {
        assertEquals("ST", initials("Show TV"))
        assertEquals("K14", initials("Kanal 14 HD Uzun Kanal Adı"))
        assertEquals("K16", initials("Kanal 16 HD"))
        assertEquals("TRT1", initials("TRT 1 HD"))
        assertEquals("BO", initials("UK: BBC One"))
        assertEquals("3E", initials("3E HD"))
        assertEquals("Mİ", initials("Minika"))
        assertEquals("İH", initials("İstanbul Haber"))
        assertEquals("A100", initials("A 1000"))
        assertEquals("?", initials(""))
    }
}
