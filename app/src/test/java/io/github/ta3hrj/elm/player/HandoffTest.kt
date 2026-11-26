package io.github.ta3hrj.elm.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoffTest {
    private var clock = 1_000L
    private val released = mutableListOf<String>()
    private val handoff = Handoff<String>(release = { released += it }, now = { clock }, maxAgeMs = 10_000)

    @Test
    fun theOfferedItemGoesToTheScreenThatAsksForItsId() {
        handoff.offer(7, "player-7")
        assertEquals("player-7", handoff.take(7))
        assertEquals(emptyList<String>(), released)
        // Taken once: nothing is left.
        assertNull(handoff.take(7))
    }

    @Test
    fun anOfferForAnotherIdIsReleasedNotLent() {
        handoff.offer(7, "player-7")
        assertNull(handoff.take(8))
        assertEquals(listOf("player-7"), released)
    }

    @Test
    fun aStaleOfferIsReleased() {
        handoff.offer(7, "player-7")
        clock += 10_001
        assertNull(handoff.take(7))
        assertEquals(listOf("player-7"), released)
    }

    @Test
    fun aNewOfferReleasesTheOneNobodyTook() {
        handoff.offer(1, "a")
        handoff.offer(2, "b")
        assertEquals(listOf("a"), released)
        assertEquals("b", handoff.take(2))
    }

    @Test
    fun expireReleasesOnlyOldOffers() {
        handoff.offer(1, "a")
        handoff.expire()
        assertEquals(emptyList<String>(), released)
        clock += 20_000
        handoff.expire()
        assertEquals(listOf("a"), released)
        assertNull(handoff.take(1))
    }
}
