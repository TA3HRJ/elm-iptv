package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }

    // 2026-11-08 18:00:00 UTC, one hour long
    private val start = 1_794_160_800_000L
    private val stop = start + 3_600_000L
    private val now = start + 2 * 3_600_000L

    @Test
    fun xtreamUsesServerTimezoneAndPadsDuration() {
        val url = Catchup.archiveUrl("http://h.tv:8080/live/u/p/123.ts", "xc", null, start, stop, now, "Europe/Istanbul")
        assertEquals("http://h.tv:8080/timeshift/u/p/65/2026-11-08:21-00/123.ts", url)
        // Short form without /live/
        assertEquals(
            "http://h.tv/timeshift/u/p/65/2026-11-08:18-00/9.ts",
            Catchup.archiveUrl("http://h.tv/u/p/9", "xc", null, start, stop, now, "UTC"),
        )
    }

    @Test
    fun templatesAppendAndShift() {
        assertEquals(
            "http://a/arch?s=1794160800&e=1794164400&d=60&dur=3600",
            Catchup.archiveUrl("http://a/live.m3u8", "default", "http://a/arch?s={utc}&e={utcend}&d={duration:60}&dur=\${duration}", start, stop, now),
        )
        assertEquals(
            "http://a/live.m3u8?utc=1794160800&lutc=1794168000",
            Catchup.archiveUrl("http://a/live.m3u8", "shift", null, start, stop, now),
        )
        assertEquals(
            "http://a/live.m3u8?token=1&utc=1794160800&lutc=1794168000",
            Catchup.archiveUrl("http://a/live.m3u8?token=1", "timeshift", null, start, stop, now),
        )
        assertEquals(
            "http://a/ch?archive=1794160800",
            Catchup.archiveUrl("http://a/ch", "append", "?archive={start}", start, stop, now),
        )
        assertNull(Catchup.archiveUrl("http://a/ch", "append", null, start, stop, now))
        assertNull(Catchup.archiveUrl("http://a/ch", null, null, start, stop, now))
    }

    @Test
    fun flussonicPaths() {
        assertEquals(
            "http://f.tv/trt1/index-1794160800-3600.m3u8",
            Catchup.archiveUrl("http://f.tv/trt1/index.m3u8?token=x", "flussonic", null, start, stop, now)?.substringBefore('?'),
        )
        assertEquals(
            "http://f.tv/trt1/timeshift_abs-1794160800.ts",
            Catchup.archiveUrl("http://f.tv/trt1/mpegts", "flussonic", null, start, stop, now)?.substringBefore('?'),
        )
    }

    @Test
    fun availability() {
        assertTrue(Catchup.isAvailable(3, "xc", start, now))
        assertFalse(Catchup.isAvailable(0, null, start, now))
        assertFalse(Catchup.isAvailable(1, "xc", start - 2 * 86_400_000L, now))
        assertFalse("future programmes are not archived", Catchup.isAvailable(3, "xc", now + 1, now))
        assertEquals(5, Catchup.availableDays(0, "default"))
    }

    @Test
    fun m3uCatchupAttributesAndHeaderDefaults() {
        val text = """
            #EXTM3U catchup="shift" catchup-days="2"
            #EXTINF:-1 tvg-id="a",A
            http://s/a
            #EXTINF:-1 catchup="default" catchup-source="http://s/b?utc={utc}" catchup-days="7",B
            http://s/b
        """.trimIndent()
        val (a, b) = M3uParser.parse(text)
        assertEquals("shift", a.catchupType)
        assertEquals(2, a.catchupDays)
        assertEquals("default", b.catchupType)
        assertEquals("http://s/b?utc={utc}", b.catchupSource)
        assertEquals(7, b.catchupDays)
    }
}
