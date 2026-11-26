package io.github.ta3hrj.elm.core

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SeriesTitleTest {
    @Test
    fun stripsEpisodeEndings() {
        assertEquals("avrupa yakası", SeriesTitle.key("Avrupa Yakası (125)"))
        assertEquals("avrupa yakası", SeriesTitle.key("Avrupa Yakası 126. Bölüm"))
        assertEquals("kuruluş osman", SeriesTitle.key("Kuruluş Osman - Bölüm 150"))
        assertEquals("the news", SeriesTitle.key("The News - Episode 4"))
        assertEquals("show", SeriesTitle.key("Show S02E05"))
        assertEquals("show", SeriesTitle.key("Show [S2 E6]"))
        assertEquals("doc", SeriesTitle.key("Doc 3/6"))
        assertEquals("ana haber", SeriesTitle.key("  Ana   Haber "))
    }

    @Test
    fun keepsTitlesThatAreOnlyNumbersOrDifferent() {
        assertEquals("1923", SeriesTitle.key("1923"))
        assertTrue(SeriesTitle.sameSeries("Avrupa Yakası (12)", "Avrupa Yakası 13. Bölüm"))
        assertFalse(SeriesTitle.sameSeries("Ana Haber", "Spor Haber"))
        assertFalse(SeriesTitle.sameSeries("", ""))
    }
}
