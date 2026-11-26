package io.github.ta3hrj.elm.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A stream whose timestamps stop moving must not fill the heap: the buffer has a hard ceiling. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class BufferCapTest {
    private val mb = 1024L * 1024

    @Test
    fun `multi-view panes share the ceiling`() {
        assertEquals(48 * mb, multiViewBufferBytes(384 * mb, 4).toLong())
        assertEquals(192 * mb, multiViewBufferBytes(384 * mb, 0).toLong())
        assertEquals(Int.MAX_VALUE, multiViewBufferBytes(Long.MAX_VALUE, 1))
    }

    @Test
    fun `the ceiling is half the heap`() {
        assertEquals(192 * mb, bufferCapBytes(384 * mb))
        assertEquals(128 * mb, bufferCapBytes(256 * mb))
    }

    @Test
    fun `the memory log says how much was held and how much lay ahead`() {
        assertEquals(
            "buffer full (full screen, UK: MTV HITS HD): 192 MB held, 0.4 s ahead: channel reconnected",
            bufferFullLine("full screen", "UK: MTV HITS HD", 192 * mb + 5, 400),
        )
        assertEquals("buffer full (preview): 0 MB held, 0.0 s ahead: channel reconnected", bufferFullLine("preview", null, 0, -1))
    }

    @Test
    fun `loading stops at the ceiling even while time says load more`() {
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val segment = C.DEFAULT_BUFFER_SEGMENT_SIZE.toLong()
        val inner = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(5_000, 30_000, 1_500, 3_000)
            .setTargetBufferBytes(segment.toInt())
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val control = CappedLoadControl(inner, allocator, capBytes = 4 * segment)
        val id = PlayerId.UNSET
        control.onPrepared(id)
        // Only 1 s ahead: below the minimum, so time first says load more, past the byte target.
        val timeline = SinglePeriodTimeline(C.TIME_UNSET, false, true, false, null, MediaItem.fromUri("http://example.invalid/live.ts"))
        val period = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
        val params = LoadControl.Parameters(id, timeline, period, 0, 1_000_000, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET)
        repeat(3) { allocator.allocate() }
        assertTrue(control.shouldContinueLoading(params))
        allocator.allocate()
        assertFalse(control.shouldContinueLoading(params))
    }
}
