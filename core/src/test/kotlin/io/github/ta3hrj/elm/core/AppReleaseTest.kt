package io.github.ta3hrj.elm.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    private val sha = "a".repeat(64)
    private val manifest = "https://updates.example/elm/latest.json"
    private val latest = """{"version":"0.9.20","apk":"ELM-IPTV-0.9.20.apk","size":6900000,"sha256":"${sha.uppercase()}","notes":"Fixes.\r\n\r\nMore fixes."}"""

    @Test
    fun readsTheManifestAndResolvesTheApkNextToIt() {
        val release = AppRelease.parse(latest, manifest)!!
        assertEquals("0.9.20", release.version)
        assertEquals("https://updates.example/elm/ELM-IPTV-0.9.20.apk", release.apkUrl)
        assertEquals("ELM-IPTV-0.9.20.apk", release.apkName)
        assertEquals(6_900_000L, release.apkSize)
        assertEquals(sha, release.sha256)
        assertTrue(release.notes.startsWith("Fixes."))
    }

    @Test
    fun missingOrWrongFieldsMeanNoUpdate() {
        fun m(body: String) = AppRelease.parse(body, manifest)
        assertNull(m("""{"version":"0.9.20","sha256":"$sha"}"""))
        assertNull(m("""{"version":"latest","apk":"a.apk","sha256":"$sha"}"""))
        assertNull(m("""{"version":"0.9.20","apk":"a-debug.apk","sha256":"$sha"}"""))
        assertNull(m("""{"version":"0.9.20","apk":"a.apk"}"""))
        assertNull(m("""{"version":"0.9.20","apk":"a.apk","sha256":"nothex"}"""))
        // An https manifest never sends the download over plain http.
        assertNull(m("""{"version":"0.9.20","apk":"http://elsewhere.example/a.apk","sha256":"$sha"}"""))
        assertNull(m("not json"))
    }

    @Test
    fun comparesVersionsNumberByNumber() {
        assertTrue(Versions.isNewer("0.9.20", "0.9.19"))
        assertTrue(Versions.isNewer("0.9.100", "0.9.99"))
        assertTrue(Versions.isNewer("1.0", "0.9.19"))
        assertTrue(Versions.isNewer("v1.0.1", "1.0"))
        assertFalse(Versions.isNewer("1.0", "1.0.0"))
        assertFalse(Versions.isNewer("0.9.19", "0.9.19"))
        assertFalse(Versions.isNewer("0.9.18", "0.9.19"))
        // The debug build's suffix does not change the number.
        assertFalse(Versions.isNewer("0.9.19", "0.9.19-debug"))
        assertFalse(Versions.isNewer("garbage", "0.9.19"))
    }

    @Test
    fun aServerThatCannotBeReadGivesNoUpdate() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody(latest))
            val url = server.url("/elm/latest.json").toString()
            assertNull(AppRelease.fetchLatest(OkHttpClient(), url))
            assertEquals("0.9.20", AppRelease.fetchLatest(OkHttpClient(), url)?.version)
            assertNull(AppRelease.fetchLatest(OkHttpClient(), "not a url"))
        }
    }
}
