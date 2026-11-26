package io.github.ta3hrj.elm.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Lip sync: the picture follows the audio clock, so shifting the clock the sink reports moves the
 * sound against the picture. A positive delay plays the sound later (for streams where it runs ahead).
 */
@UnstableApi
internal class DelayedAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    @Volatile
    var delayUs: Long = 0

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else (position + delayUs).coerceAtLeast(0)
    }
}

/** Renderers whose audio (hardware or FFmpeg) goes through a [DelayedAudioSink]. */
@UnstableApi
internal class DelayRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    var audioSink: DelayedAudioSink? = null
        private set

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let { sink ->
            DelayedAudioSink(sink).also { audioSink = it }
        }
}
