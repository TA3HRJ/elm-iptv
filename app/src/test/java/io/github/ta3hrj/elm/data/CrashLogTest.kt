package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The error log tells which version wrote each report, so old, fixed problems stand out. */
@RunWith(RobolectricTestRunner::class)
class CrashLogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun entriesKnowTheirVersion() {
        CrashLog.clear(context)
        val dir = File(context.filesDir, "crashes").apply { mkdirs() }
        File(dir, "crash-1000.txt").writeText("ELM IPTV 0.9.12 (27)\n2026-11-11 22:19:14\n\njava.net.SocketException: Connection reset\n")
        CrashLog.record(context, IllegalStateException("now"))
        val entries = CrashLog.entries(context)
        val old = entries.single { it.time == 1000L }
        assertEquals("0.9.12", old.version)
        assertTrue(old.fromOlderVersion)
        assertEquals("java.net.SocketException: Connection reset", old.summary)
        val current = entries.single { it.time != 1000L }
        assertEquals(BuildConfig.VERSION_NAME, current.version)
        assertFalse(current.fromOlderVersion)
    }
}
