package io.github.ta3hrj.elm.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import java.util.Locale

/**
 * The most a live player may hold in its buffer, whatever the times say: half the Java heap.
 *
 * Time comes first in the live load control, so the minimum ahead is loaded even past the byte target.
 * When a stream's timestamps stop moving forward, the time ahead never reaches that minimum and the player
 * kept loading: on the Fire TV one channel took the heap from 70 to 376 of 384 MB in 20 minutes. Half
 * the heap still holds two minutes behind the playhead of a stream up to about 12 Mbit/s.
 */
internal fun bufferCapBytes(heapMaxBytes: Long): Long = heapMaxBytes / 2

/**
 * The byte target of one multi-view pane: the live ceiling shared by the most panes that can play at once.
 * Without it each pane used Media3's default target (about 140 MB with video), so four panes could ask for more
 * than the whole heap of a TV stick.
 */
internal fun multiViewBufferBytes(heapMaxBytes: Long, panes: Int): Int =
    (bufferCapBytes(heapMaxBytes) / panes.coerceAtLeast(1)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** The memory-log line for a buffer that reached [bufferCapBytes] and the channel that was reconnected for it. */
internal fun bufferFullLine(where: String, channel: String?, heldBytes: Long, aheadMs: Long): String {
    val mb = heldBytes / (1024 * 1024)
    val ahead = String.format(Locale.US, "%.1f", aheadMs.coerceAtLeast(0L) / 1000.0)
    return "buffer full ($where${channel?.let { ", $it" }.orEmpty()}): $mb MB held, $ahead s ahead: channel reconnected"
}

/**
 * [DefaultLoadControl] with a hard ceiling: it stops loading once [allocator] holds [capBytes], so a stream
 * gone wrong cannot fill the heap even before the player notices it ([LivePlayer.bufferFull]).
 */
@UnstableApi
internal class CappedLoadControl(
    private val inner: DefaultLoadControl,
    private val allocator: DefaultAllocator,
    private val capBytes: Long,
) : LoadControl {
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        allocator.totalBytesAllocated < capBytes && inner.shouldContinueLoading(parameters)

    override fun onPrepared(playerId: PlayerId) = inner.onPrepared(playerId)
    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray, trackSelections: Array<out ExoTrackSelection?>) =
        inner.onTracksSelected(parameters, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) = inner.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = inner.onReleased(playerId)
    override fun getAllocator(playerId: PlayerId): Allocator = inner.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId): Long = inner.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = inner.retainBackBufferFromKeyframe(playerId)
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = inner.shouldStartPlayback(parameters)
    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId, bufferedDurationUs: Long): Boolean =
        inner.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
}
