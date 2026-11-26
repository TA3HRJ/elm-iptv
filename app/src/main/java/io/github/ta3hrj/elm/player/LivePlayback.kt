package io.github.ta3hrj.elm.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import io.github.ta3hrj.elm.data.AppSettings
import okhttp3.OkHttpClient

/** How far back a live stream stays buffered (rewind and pause). */
internal const val LIVE_BACK_BUFFER_MS = 120_000
internal const val LIVE_BUFFER_BYTES = 64 * 1024 * 1024

/** A player for live channels with the sink that carries the lip-sync delay. */
@UnstableApi
internal class LivePlayer(
    val exo: ExoPlayer,
    val audioSink: DelayedAudioSink?,
    /** The decoders and audio output this player ended up with (kept across a hand-over). */
    val tech: TechCollector,
    /** The audio profile it was built with; a preview with another one is rebuilt. */
    val audioProfile: Int,
    /** Where the buffer lives; read to see how much it holds. */
    val allocator: DefaultAllocator,
    /** The most the buffer may hold ([bufferCapBytes]). */
    val bufferCap: Long,
) {
    /** Bytes held in the buffer now (ahead and behind the playhead). */
    val bufferedBytes: Long get() = allocator.totalBytesAllocated.toLong()

    /** The buffer reached its ceiling: the stream has to start again to free it. */
    val bufferFull: Boolean get() = bufferedBytes >= bufferCap
}

/**
 * The one way a live-channel player is built, for the full-screen player and for the previews alike.
 * Because they are identical, a preview that is already playing a channel can be handed to the full-screen
 * player as it is: the same connection, the same buffer, no black screen in between.
 */
@UnstableApi
internal fun buildLivePlayer(context: Context, settings: AppSettings): LivePlayer {
    val app = context.applicationContext
    // Hardware decoders first; FFmpeg (AC3, E-AC3, DTS, MP2…) only for formats the device cannot decode.
    val renderers = DelayRenderersFactory(app)
    renderers
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    // Live: start quickly, keep up to 2 minutes behind the playhead for rewinding and pausing.
    // Buffer size from Settings: small starts fastest, large rides out a weak connection.
    val (minMs, maxMs, startMs, rebufferMs, bytes) = when (settings.bufferSize.value) {
        0 -> listOf(2_500, 15_000, 1_000, 2_000, 32 * 1024 * 1024)
        2 -> listOf(15_000, 60_000, 3_000, 6_000, 96 * 1024 * 1024)
        else -> listOf(5_000, 30_000, 1_500, 3_000, LIVE_BUFFER_BYTES)
    }
    // The back buffer counts towards the byte target. On a stream above a few Mbit/s the two minutes behind
    // the playhead alone fill it, and with size first the player then stopped loading ahead, ran dry and
    // gave up as stuck after a few minutes. Time first: the minimum ahead is always loaded; the byte target
    // still caps everything beyond the minimum.
    // Media3 does not stop on its own before the heap is full: the hard ceiling does (see bufferCapBytes).
    val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
    val cap = bufferCapBytes(Runtime.getRuntime().maxMemory())
    val loadControl = CappedLoadControl(
        DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(minMs, maxMs, startMs, rebufferMs)
            .setBackBuffer(LIVE_BACK_BUFFER_MS, true)
            .setTargetBufferBytes(bytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build(),
        allocator,
        cap,
    )
    val profile = settings.audioProfile.value
    val exo = ExoPlayer.Builder(app, renderers)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                // "Film" lets the TV and the device apply their movie sound processing; "General" leaves it out.
                .setContentType(if (profile == AppSettings.AUDIO_PROFILE_MOVIE) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_UNKNOWN)
                .build(),
            true,
        )
        .build()
    renderers.audioSink?.delayUs = settings.audioDelayMs.value * 1000L
    exo.playWhenReady = true
    val tech = TechCollector()
    exo.addAnalyticsListener(tech)
    return LivePlayer(exo, renderers.audioSink, tech, profile, allocator, cap)
}

internal fun isHlsUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.contains("/hls/")
}

/** The media source for a live channel; the same for every player that plays one. */
@UnstableApi
internal fun liveMediaSource(http: OkHttpClient, userAgent: String, url: String): MediaSource {
    val item = MediaItem.Builder()
        .setUri(url)
        .apply { if (isHlsUrl(url)) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
        .build()
    return DefaultMediaSourceFactory(OkHttpDataSource.Factory(http).setUserAgent(userAgent)).createMediaSource(item)
}

/**
 * One thing handed from the screen that has it to the one that starts next, by id: the preview's playing
 * player to the full-screen player. Whatever is not taken in time is released, so nothing leaks if the
 * next screen never starts.
 */
internal class Handoff<T : Any>(
    private val release: (T) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 10_000L,
) {
    private class Entry<T>(val id: Long, val item: T, val at: Long)

    private var entry: Entry<T>? = null

    /** Offers [item] for [id]; an earlier offer that nobody took is released. */
    @Synchronized
    fun offer(id: Long, item: T) {
        discard()
        entry = Entry(id, item, now())
    }

    /** Takes the offer for [id] if there is one and it is fresh; an offer for anything else is released. */
    @Synchronized
    fun take(id: Long): T? {
        val e = entry ?: return null
        entry = null
        if (e.id == id && now() - e.at <= maxAgeMs) return e.item
        release(e.item)
        return null
    }

    @Synchronized
    fun discard() {
        entry?.let { release(it.item) }
        entry = null
    }

    /** Releases an offer that is older than the maximum age (called from time to time). */
    @Synchronized
    fun expire() {
        val e = entry ?: return
        if (now() - e.at > maxAgeMs) discard()
    }
}

@UnstableApi
internal val PlayerHandoff = Handoff<LivePlayer>(release = { it.exo.release() })

/**
 * The way back: when the full-screen player closes on a channel that the preview handed over (or was
 * showing), it gives the playing player back to the preview, which carries on without reconnecting.
 * [ReturnTo] says which channel the screen below is waiting for; an offer nobody claims is released fast.
 */
@UnstableApi
internal val PlayerReturn = Handoff<LivePlayer>(release = { it.exo.release() }, maxAgeMs = 4_000L)

/** The sleep timer closed the player on this channel: the preview must not start playing it again (sound!). */
internal object SleepHold {
    @Volatile
    var channelId = 0L
}

internal object ReturnTo {
    /** The channel the preview below is waiting to get back; 0 = none. */
    @Volatile
    var channelId = 0L
}
