package io.github.ta3hrj.elm.player

import io.github.ta3hrj.elm.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrackLabelsTest {
    @Before
    fun english() = Lang.apply(Lang.Choice.ENGLISH)

    @Test
    fun languageCodeAndForcedMarker() {
        assertEquals("Turkish · Forced · tr", subtitleLabel("Turkish", "tr", forced = true, hearingImpaired = false))
        assertEquals("English · en", subtitleLabel("English", "en", forced = false, hearingImpaired = false))
        assertEquals("Deutsch - [German] · de", subtitleLabel("Deutsch - [German]", "de", forced = false, hearingImpaired = false))
    }

    @Test
    fun doesNotRepeatWhatTheNameAlreadySays() {
        assertEquals("Türkçe[Forced] - [Turkish] · tr", subtitleLabel("Türkçe[Forced] - [Turkish]", "tr", forced = true, hearingImpaired = false))
        assertEquals("English SDH · en", subtitleLabel("English SDH", "en", forced = false, hearingImpaired = true))
        assertEquals("tr", subtitleLabel("tr", "tr", forced = false, hearingImpaired = false))
    }

    @Test
    fun unknownLanguageGetsNoCode() {
        assertEquals("Track 2", subtitleLabel("Track 2", "und", forced = false, hearingImpaired = false))
        assertEquals("Track 2", subtitleLabel("Track 2", null, forced = false, hearingImpaired = false))
    }

    @Test
    fun hearingImpairedAndForcedTogether() {
        assertEquals("English · Forced · Hearing impaired · en", subtitleLabel("English", "en", forced = true, hearingImpaired = true))
    }
}
