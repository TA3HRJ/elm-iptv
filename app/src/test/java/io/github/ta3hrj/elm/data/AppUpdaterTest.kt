package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Update checks, "Later", and that only a real, signed, newer build of this app is handed on. */
@RunWith(RobolectricTestRunner::class)
class AppUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("updater-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() = server.shutdown()

    private fun updater(current: String = "0.9.19") =
        AppUpdater(context, OkHttpClient(), prefs, currentVersion = current, manifestUrl = server.url("/elm/latest.json").toString(), enabled = true)

    private fun latest(version: String, size: Long = 4, sha256: String = "0".repeat(64)) =
        """{"version":"$version","apk":"ELM-IPTV-$version.apk","size":$size,"sha256":"$sha256"}"""

    @Test
    fun newerUpToDateAndUnreachable() = runBlocking {
        server.enqueue(MockResponse().setBody(latest("0.9.20")))
        server.enqueue(MockResponse().setBody(latest("0.9.19")))
        server.enqueue(MockResponse().setResponseCode(404))
        val result = updater().check()
        assertEquals("0.9.20", (result as AppUpdater.Check.Newer).release.version)
        assertEquals(AppUpdater.Check.UpToDate, updater().check())
        assertEquals(AppUpdater.Check.Unreachable, updater().check())
    }

    @Test
    fun checksAtMostOnceADayOnItsOwn() = runBlocking {
        val u = updater()
        assertTrue(u.dueForCheck())
        server.enqueue(MockResponse().setBody(latest("0.9.19")))
        u.check()
        assertFalse(u.dueForCheck())
        assertTrue(u.dueForCheck(now = System.currentTimeMillis() + 21L * 60 * 60 * 1000))
    }

    @Test
    fun laterAsksAgainAfterAFewDaysOrForANewerVersion() {
        val u = updater()
        u.postpone("0.9.20")
        assertTrue(u.postponed("0.9.20"))
        assertFalse(u.postponed("0.9.21"))
        assertFalse(u.postponed("0.9.20", now = System.currentTimeMillis() + 4L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun aDownloadThatIsNotThisAppSignedWithItsKeyIsRefusedAndDeleted() = runBlocking {
        // Right checksum, so the refusal comes from the package check.
        server.enqueue(MockResponse().setBody(latest("0.9.20", sha256 = "b5c1fb2efc6d6b4674c2fdcc48ce01b43a3b7c03763c0c3355de0099ee0f8c73")))
        val release = (updater().check() as AppUpdater.Check.Newer).release
        server.enqueue(MockResponse().setBody("evil"))
        val result = updater().download(release) {}
        assertEquals(AppUpdater.Download.Failed("not an app package"), result)
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aDownloadWithAnotherChecksumIsRefused() = runBlocking {
        server.enqueue(MockResponse().setBody(latest("0.9.20")))
        val release = (updater().check() as AppUpdater.Check.Newer).release
        assertEquals("/elm/ELM-IPTV-0.9.20.apk", java.net.URI(release.apkUrl).path)
        server.enqueue(MockResponse().setBody("evil"))
        assertEquals("checksum", (updater().download(release) {} as AppUpdater.Download.Failed).reason)
    }

    @Test
    fun aBuildWithoutAnUpdateServerNeverChecks() = runBlocking {
        val u = AppUpdater(context, OkHttpClient(), prefs, currentVersion = "0.9.19", manifestUrl = "")
        assertFalse(u.enabled)
        assertFalse(u.dueForCheck())
        assertEquals(AppUpdater.Check.Unreachable, u.check())
    }

    @Test
    fun aShortDownloadIsRefused() = runBlocking {
        server.enqueue(MockResponse().setBody(latest("0.9.20", size = 1000)))
        val release = (updater().check() as AppUpdater.Check.Newer).release
        server.enqueue(MockResponse().setBody("half"))
        val result = updater().download(release) {}
        assertTrue((result as AppUpdater.Download.Failed).reason.startsWith("size"))
    }
}
