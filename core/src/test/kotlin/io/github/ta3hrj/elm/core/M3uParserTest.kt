package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }


    @Test
    fun parsesAttributesNameAndGroup() {
        val text = """
            #EXTM3U url-tvg="http://epg.example/guide.xml"
            #EXTINF:-1 tvg-id="trt1.tr" tvg-chno="101" tvg-name="TRT 1" tvg-logo="http://logo.example/trt1.png" group-title="Ulusal",TRT 1 HD
            http://stream.example/live/1.ts
        """.trimIndent()

        val channels = M3uParser.parse(text)

        assertEquals(1, channels.size)
        with(channels[0]) {
            assertEquals("TRT 1 HD", name)
            assertEquals("Ulusal", group)
            assertEquals("trt1.tr", epgId)
            assertEquals("http://logo.example/trt1.png", logo)
            assertEquals("http://stream.example/live/1.ts", streamUrl)
            assertEquals(101, number)
        }
    }

    @Test
    fun commaInsideQuotedAttributeDoesNotSplitName() {
        val text = """
            #EXTM3U
            #EXTINF:-1 group-title="Haber, Ekonomi" tvg-logo="",Kanal, Bir
            https://s.example/a.m3u8
        """.trimIndent()

        val channel = M3uParser.parse(text).single()

        assertEquals("Kanal, Bir", channel.name)
        assertEquals("Haber, Ekonomi", channel.group)
        assertNull(channel.logo)
    }

    @Test
    fun handlesExtGrpUserAgentCrlfBomAndMissingInfo() {
        val text = "﻿#EXTM3U\r\n" +
            "#EXTINF:0,Spor 1\r\n" +
            "#EXTGRP:Spor\r\n" +
            "#EXTVLCOPT:http-user-agent=MyPlayer/1.0\r\n" +
            "http://s.example/spor1\r\n" +
            "\r\n" +
            "http://s.example/raw/stream.ts\r\n"

        val channels = M3uParser.parse(text)

        assertEquals(2, channels.size)
        assertEquals("Spor 1", channels[0].name)
        assertEquals("Spor", channels[0].group)
        assertEquals("MyPlayer/1.0", channels[0].userAgent)
        assertEquals("stream.ts", channels[1].name)
        assertEquals(UNGROUPED, channels[1].group)
        assertNull(channels[1].userAgent)
    }

    @Test
    fun usesTvgNameWhenDisplayNameMissingAndReadsCatchup() {
        val text = """
            #EXTINF:-1 tvg-name="Belgesel" catchup-days="7",
            http://s.example/b.ts
        """.trimIndent()

        val channel = M3uParser.parse(text).single()

        assertEquals("Belgesel", channel.name)
        assertEquals(7, channel.catchupDays)
    }

    @Test
    fun ignoresNonStreamLinesAndUnknownTags() {
        val text = """
            #EXTM3U
            #PLAYLIST:Test
            #EXTINF:-1,Bozuk
            not a url
            #EXTINF:-1,Doğru
            rtmp://s.example/live
        """.trimIndent()

        val channels = M3uParser.parse(text)

        assertEquals(listOf("Doğru"), channels.map { it.name })
        assertTrue(M3uParser.looksLikeM3u("﻿#EXTM3U"))
    }
}
