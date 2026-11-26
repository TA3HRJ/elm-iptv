package io.github.ta3hrj.elm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guide links on the playlist screen never show a login, whatever form it has in the address. */
class MaskCredentialsTest {
    @Test
    fun `query, path and user info logins are hidden`() {
        assertEquals("http://h/xmltv.php?username=***&password=***", maskCredentials("http://h/xmltv.php?username=bob&password=s3cret"))
        assertEquals("http://***:***@h/epg.xml", maskCredentials("http://bob:s3cret@h/epg.xml"))
        assertEquals("http://h/live/***/***/epg.xml", maskCredentials("http://h/live/bob/s3cret/epg.xml"))
        assertEquals("https://epg.example/guide.xml.gz", maskCredentials("https://epg.example/guide.xml.gz"))
    }
}
