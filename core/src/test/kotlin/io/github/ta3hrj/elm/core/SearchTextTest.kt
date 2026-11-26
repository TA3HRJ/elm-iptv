package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun foldsTurkishLettersAndCase() {
        assertEquals("cocuk", SearchText.normalize("ÇOCUK"))
        assertEquals("cocuk", SearchText.normalize("Çocuk"))
        assertEquals("istanbul", SearchText.normalize("İSTANBUL"))
        assertEquals("istanbul", SearchText.normalize("ISTANBUL"))
        assertEquals("isik", SearchText.normalize("IŞIK"))
        assertEquals("isik", SearchText.normalize("ışık"))
        assertEquals("guzel sogus", SearchText.normalize("Güzel  Söğüş"))
        assertEquals("trt 1 hd", SearchText.normalize(" TRT 1 HD "))
    }

    @Test
    fun foldsOtherAccents() {
        assertEquals("cafe", SearchText.normalize("Café"))
        assertEquals("munchen", SearchText.normalize("München"))
    }

    @Test
    fun escapesLikeWildcards() {
        assertEquals("100\\% a\\_b", SearchText.escapeLike("100% a_b"))
    }
}
