package io.github.ta3hrj.elm.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultContentTest {
    @Test
    fun recognisesAdultCategories() {
        listOf("+18 Yetişkin", "YETİŞKİN", "XXX Movies", "Adult", "For Adults Only", "18+ Kanallar", "Erotik", "VOD | XXX", "Adults")
            .forEach { assertTrue(it, AdultContent.isAdultCategory(it)) }
    }

    @Test
    fun leavesOrdinaryCategoriesAlone() {
        listOf("UK Kids", "Adult Swim", "Sussex Radio", "Sinema", "UK Entertainment", "Sports 4K", "Belgesel", "Young Adult Movies", "TRT 1", "Box Office 2018")
            .forEach { assertFalse(it, AdultContent.isAdultCategory(it)) }
    }
}
