package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uVodTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }

    private fun ch(name: String, url: String, group: String = "G") = Channel(name, group, url)

    @Test
    fun classifiesByAddress() {
        assertEquals(M3uVod.Kind.LIVE, M3uVod.classify(ch("Film 1 HD", "http://h/live/u/p/1.ts", "Film")))
        assertEquals(M3uVod.Kind.LIVE, M3uVod.classify(ch("Haber", "http://h/u/p/2")))
        assertEquals(M3uVod.Kind.LIVE, M3uVod.classify(ch("HLS", "https://cdn/x/index.m3u8")))
        assertEquals(M3uVod.Kind.MOVIE, M3uVod.classify(ch("Kış Uykusu (2014)", "http://h/movie/u/p/77.mkv")))
        assertEquals(M3uVod.Kind.MOVIE, M3uVod.classify(ch("Film", "http://files/a/film.mp4?token=1")))
        assertEquals(M3uVod.Kind.EPISODE, M3uVod.classify(ch("Dizi S01 E02", "http://h/series/u/p/901.mp4")))
        assertEquals(M3uVod.Kind.EPISODE, M3uVod.classify(ch("Dizi S01E03", "http://files/dizi/s1e3.mkv")))
    }

    @Test
    fun parsesEpisodeNames() {
        assertEquals(M3uVod.EpisodeName("Diriliş Ertuğrul", 1, 2), M3uVod.parseEpisode("Diriliş Ertuğrul S01 E02"))
        assertEquals(M3uVod.EpisodeName("Ezel", 2, 15), M3uVod.parseEpisode("Ezel.S02E15.1080p"))
        assertEquals(M3uVod.EpisodeName("TR: Kurtlar Vadisi", 10, 3), M3uVod.parseEpisode("TR: Kurtlar Vadisi - S10 E03"))
        assertEquals(M3uVod.EpisodeName("Dizi", 3, 7), M3uVod.parseEpisode("Dizi - 3x07"))
        assertNull(M3uVod.parseEpisode("Sadece Bir Film"))
        assertNull(M3uVod.parseEpisode("S01E01"))
    }

    @Test
    fun yearsAndExtensions() {
        assertEquals("2014", M3uVod.year("Kış Uykusu (2014)"))
        assertEquals("1999", M3uVod.year("Matrix - 1999"))
        assertNull(M3uVod.year("Film 2"))
        assertEquals("mkv", M3uVod.extension("http://h/movie/u/p/77.mkv?x=1"))
        assertNull(M3uVod.extension("http://h/live/u/p/1.ts"))
    }
}
