package io.github.ta3hrj.elm.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Film and series sort orders; items without the sorted value always go last. */
class VodSortTest {
    private fun item(name: String, year: String? = null, added: Long? = null, rating: String? = null) = VodItemEntity(
        playlistId = 1, kind = VodKinds.MOVIE, providerId = name, position = 0, name = name, category = "c",
        poster = null, rating = rating, year = year, containerExtension = null, added = added,
    )

    private val items = listOf(
        item("Beta", year = "2010", added = 300, rating = "7,5"),
        item("Alfa", year = "1998", added = 100),
        item("Çay", year = null, added = null, rating = "9.1"),
        item("Delta", year = "2024", added = 200, rating = "5"),
    )

    private fun names(order: VodOrder) = sortVod(items, order).map { it.name }

    @Test
    fun yearBothWaysKeepsMissingLast() {
        assertEquals(listOf("Delta", "Beta", "Alfa", "Çay"), names(VodOrder.YEAR))
        assertEquals(listOf("Alfa", "Beta", "Delta", "Çay"), names(VodOrder.YEAR_OLD))
    }

    @Test
    fun addedBothWaysKeepsMissingLast() {
        assertEquals(listOf("Beta", "Delta", "Alfa", "Çay"), names(VodOrder.ADDED_NEW))
        assertEquals(listOf("Alfa", "Delta", "Beta", "Çay"), names(VodOrder.ADDED_OLD))
    }

    @Test
    fun nameBothWays() {
        assertEquals(listOf("Alfa", "Beta", "Çay", "Delta"), names(VodOrder.NAME))
        assertEquals(listOf("Delta", "Çay", "Beta", "Alfa"), names(VodOrder.NAME_DESC))
    }

    @Test
    fun ratingHighestFirst() {
        assertEquals(listOf("Çay", "Beta", "Delta", "Alfa"), names(VodOrder.RATING))
    }
}
