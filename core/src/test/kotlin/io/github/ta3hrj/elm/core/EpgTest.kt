package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class EpgTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }


    private val importer = EpgImporter({ KXmlParser() })

    // 2026-11-08 18:00:00 UTC
    private val t1800 = 1_794_160_800_000L
    private val hour = 3_600_000L

    private val guide = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="TRT1.tr"><display-name lang="tr">TRT 1 HD</display-name></channel>
          <channel id="spor.1"><display-name>Spor Bir</display-name><display-name>S1</display-name></channel>
          <channel id="unused"><display-name>Kimsenin Kanalı</display-name></channel>
          <programme start="20261108200000 +0300" stop="20261108210000 +0300" channel="trt1.tr">
            <title lang="en">News</title><title lang="tr">Ana Haber</title>
            <desc lang="tr">Günün   önemli
              gelişmeleri &amp; hava durumu</desc>
            <category>News</category>
          </programme>
          <programme start="20261108210000 +0300" stop="20261108230000 +0300" channel="TRT1.tr">
            <title><![CDATA[Film: Kış Uykusu]]></title>
          </programme>
          <programme start="20261101100000 +0300" stop="20261101110000 +0300" channel="TRT1.tr">
            <title>Çok eski</title>
          </programme>
          <programme start="20261108180000" stop="20261108190000" channel="spor.1"><title>Maç Önü</title></programme>
          <programme start="20261108180000" stop="20261108190000" channel="unused"><title>Gereksiz</title></programme>
          <programme start="bozuk" stop="20261108190000" channel="spor.1"><title>Bozuk zaman</title></programme>
        </tv>
    """.trimIndent()

    private val channels = listOf(
        EpgChannelRef("http://s/1", "TR: TRT 1 FHD", epgId = "TRT1.tr"),
        EpgChannelRef("http://s/2", "TR | SPOR BİR HD", epgId = null),
        EpgChannelRef("http://s/3", "Bilinmeyen", epgId = "yok.tr"),
    )

    private fun run(bytes: ByteArray, maxProgrammes: Int = 1000) =
        EpgImporter({ KXmlParser() }, maxProgrammes).import(
            ByteArrayInputStream(bytes), channels, windowStart = t1800 - 12 * hour, windowEnd = t1800 + 72 * hour,
        )

    @Test
    fun manualMatchesReplaceTheAutomaticOnes() {
        val result = EpgImporter({ KXmlParser() }).import(
            ByteArrayInputStream(guide.toByteArray()), channels,
            windowStart = t1800 - 12 * hour, windowEnd = t1800 + 72 * hour,
            manual = mapOf(
                "http://s/3" to "UNUSED", // matched by hand, id case does not matter
                "http://s/2" to "", // "no guide" chosen by hand
                "http://s/1" to "not.in.this.guide", // never falls back to the automatic match
            ),
        )
        assertEquals(listOf(EpgLink("http://s/3", "unused")), result.links)
        assertEquals(listOf("Gereksiz"), result.programmes.map { it.title })
    }

    @Test
    fun listsTheGuidesChannels() {
        val result = run(guide.toByteArray())
        assertEquals(
            listOf(EpgSourceChannel("TRT1.tr", "TRT 1 HD"), EpgSourceChannel("spor.1", "Spor Bir"), EpgSourceChannel("unused", "Kimsenin Kanalı")),
            result.sourceChannels,
        )
    }

    @Test
    fun similarityPutsLikelyMatchesFirst() {
        assertEquals(100, EpgNames.similarity("TR: BEIN SERIES 4 FHD", "beIN Series 4 HD"))
        assertTrue(EpgNames.similarity("BEIN SERIES 4 FHD", "beIN Series 4") > EpgNames.similarity("BEIN SERIES 4 FHD", "beIN Sports 4"))
        assertTrue(EpgNames.similarity("Show TV", "Show TV Max") >= 65)
        assertEquals(0, EpgNames.similarity("TRT 1", "Kanal D"))
    }

    @Test
    fun parsesXmltvTimes() {
        assertEquals(t1800, XmltvTime.parse("20261108210000 +0300"))
        assertEquals(t1800, XmltvTime.parse("20261108180000"))
        assertEquals(t1800, XmltvTime.parse("202611081800 Z"))
        assertEquals(t1800, XmltvTime.parse("20261108123000 -05:30"))
        assertNull(XmltvTime.parse("2026"))
        assertNull(XmltvTime.parse("20261308180000"))
        assertNull(XmltvTime.parse("20261108180000 EST"))
    }

    @Test
    fun normalizesChannelNames() {
        assertEquals("trt1", EpgNames.normalize("TR: TRT 1 HD"))
        assertEquals("trt1", EpgNames.normalize("[TR] TRT-1 FHD"))
        assertEquals("sporbir", EpgNames.normalize("TR | SPOR BİR HD"))
        assertEquals("cnnturk", EpgNames.normalize("CNN Türk"))
        assertEquals("bein1", EpgNames.normalize("beIN 1 4K HEVC"))
    }

    @Test
    fun linksByIdCaseInsensitivelyAndByNameAndFiltersProgrammes() {
        val result = run(guide.toByteArray())

        assertEquals(3, result.sourceChannelCount)
        assertEquals(
            listOf(EpgLink("http://s/1", "TRT1.tr"), EpgLink("http://s/2", "spor.1")),
            result.links,
        )
        assertEquals(listOf("Ana Haber", "Film: Kış Uykusu", "Maç Önü"), result.programmes.map { it.title })
        val news = result.programmes[0]
        assertEquals("TRT1.tr", news.channelKey)
        assertEquals(t1800 - hour, news.start) // 20:00 +03:00 = 17:00 UTC
        assertEquals(t1800, news.stop)
        assertEquals("Günün önemli gelişmeleri & hava durumu", news.description)
        assertNull(result.programmes[1].description)
        assertFalse(result.truncated)
    }

    @Test
    fun readsGzipAndStopsAtProgrammeLimit() {
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(guide.toByteArray()) } }
        val result = run(gz.toByteArray(), maxProgrammes = 2)

        assertEquals(2, result.programmes.size)
        assertTrue(result.truncated)
    }

    @Test
    fun trustsTvgIdWhenGuideHasNoChannelList() {
        val bare = """<tv><programme start="20261108180000" stop="20261108190000" channel="yok.tr"><title>X</title></programme></tv>"""
        val result = importer.import(
            ByteArrayInputStream(bare.toByteArray()), channels, t1800 - hour, t1800 + hour,
        )
        assertEquals(listOf("TRT1.tr", "yok.tr"), result.links.map { it.epgKey })
        assertEquals(listOf("X"), result.programmes.map { it.title })
    }

    @Test
    fun m3uHeaderAnnouncesGuideUrls() {
        val text = "#EXTM3U url-tvg=\"http://epg.a/guide.xml.gz,http://epg.b/g.xml\" x-tvg-url=\"http://epg.a/guide.xml.gz\"\n" +
            "#EXTINF:-1,Kanal\nhttp://s/1\n"
        val result = M3uParser.parsePlaylist(text.reader())
        assertEquals(listOf("http://epg.a/guide.xml.gz", "http://epg.b/g.xml"), result.epgUrls)
        assertEquals(1, result.channels.size)
    }
}
