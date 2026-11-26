package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The memory log stays small, and the error log screen reads the right numbers from it. */
@RunWith(RobolectricTestRunner::class)
class MemoryLogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = MemoryLog.clear(context)

    @Test
    fun snapshotHasTheNumbersTheSummaryReads() {
        val line = MemoryLog.snapshot(context)
        for (field in listOf(" up ", " pss ", " java ", " native ", " sys ", " low ", " threads ", " plays ", " retries ")) {
            assertTrue("$field missing in: $line", line.contains(field))
        }
        MemoryLog.note(context, line)
        assertEquals(1, MemoryLog.summary(context).samples)
    }

    @Test
    fun summaryCoversOnlyTheLatestStart() {
        MemoryLog.note(context, "2026-11-10 10:00:00  === ELM IPTV 0.9.16 (31) started ===")
        MemoryLog.note(context, "2026-11-10 10:00:01  up 0h00m  pss 900  java 1/2")
        MemoryLog.note(context, "2026-11-11 20:00:00  === ELM IPTV 0.9.16 (31) started ===")
        MemoryLog.note(context, "2026-11-11 20:00:01  up 0h00m  pss 150  java 1/2")
        MemoryLog.note(context, "2026-11-11 20:05:01  ! system asks to free memory: running low")
        MemoryLog.note(context, "2026-11-11 20:05:01  up 0h05m  pss 240  java 1/2")
        MemoryLog.note(context, "2026-11-11 20:10:01  up 0h10m  pss 200  java 1/2")
        val summary = MemoryLog.summary(context)
        assertEquals(3, summary.samples)
        assertEquals(150, summary.firstPssMb)
        assertEquals(240, summary.peakPssMb)
        assertEquals(200, summary.lastPssMb)
    }

    @Test
    fun oldLinesAreDropped() {
        repeat(MemoryLog.KEEP_LINES + 200) { i ->
            MemoryLog.note(context, "2026-11-11 20:00:00  up 0h00m  pss $i  java 45/256  native 31  sys 912/1906  low no  threads 48  plays 17")
        }
        val lines = MemoryLog.text(context).lines().filter { it.isNotEmpty() }
        assertTrue("${lines.size} lines kept", lines.size <= MemoryLog.KEEP_LINES)
        assertTrue(lines.last().contains("pss ${MemoryLog.KEEP_LINES + 199} "))
    }
}
