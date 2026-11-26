package io.github.ta3hrj.elm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    @Test
    fun hidesLoginsInAddresses() {
        assertEquals("http://host:8080/live/***/***/123.ts", Redact.text("http://host:8080/live/erhan/s3cret/123.ts"))
        assertEquals("http://host/movie/***/***/55.mp4", Redact.text("http://host/movie/erhan/s3cret/55.mp4"))
        assertEquals("http://host/timeshift/***/***/120/2026-11-13:20-00/9.ts", Redact.text("http://host/timeshift/u/p/120/2026-11-13:20-00/9.ts"))
        assertEquals("http://host/get.php?username=***&password=***&type=m3u", Redact.text("http://host/get.php?username=erhan&password=s3cret&type=m3u"))
        assertEquals("https://***:***@host/list.m3u", Redact.text("https://erhan:s3cret@host/list.m3u"))
    }

    @Test
    fun hidesTokensAndHeadersButKeepsTheRestReadable() {
        val text = "java.io.IOException: GET http://h/x?token=abc123&q=1 failed; Authorization: Bearer abcdefghijkl12345"
        val out = Redact.text(text)
        assertFalse("abc123" in out)
        assertFalse("abcdefghijkl12345" in out)
        assertEquals("java.io.IOException: GET http://h/x?token=***&q=1 failed; Authorization: Bearer ***", out)
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        val text = "java.net.SocketException: Connection reset\n\tat java.net.SocketInputStream.read(SocketInputStream.java:215)"
        assertEquals(text, Redact.text(text))
        assertEquals("Kanal 12 HD", Redact.text("Kanal 12 HD"))
    }
}
