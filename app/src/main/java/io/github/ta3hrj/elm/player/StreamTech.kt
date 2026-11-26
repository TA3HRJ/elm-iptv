package io.github.ta3hrj.elm.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import io.github.ta3hrj.elm.core.tx

/** Who decodes a stream: the device's hardware, Android's built-in software codec, or the app's FFmpeg. */
enum class DecoderKind { NONE, HARDWARE, ANDROID_SOFTWARE, FFMPEG }

fun decoderKind(name: String?): DecoderKind {
    if (name.isNullOrBlank()) return DecoderKind.NONE
    val n = name.lowercase()
    return when {
        n.startsWith("ffmpeg") -> DecoderKind.FFMPEG
        n.startsWith("omx.google.") || n.startsWith("c2.android.") || ".sw." in n || n.endsWith(".sw") -> DecoderKind.ANDROID_SOFTWARE
        else -> DecoderKind.HARDWARE
    }
}

/** What the player knows about how the current stream is decoded and sent to the speakers. */
data class StreamTech(
    val videoMime: String? = null,
    val videoDecoder: String? = null,
    val audioMime: String? = null,
    val audioChannels: Int = 0,
    val audioRate: Int = 0,
    val audioDecoder: String? = null,
    /** The encoding the audio track is fed ([C.ENCODING_*]); null until the track exists. */
    val outputEncoding: Int? = null,
    val outputChannels: Int = 0,
)

@UnstableApi
private val PcmEncodings = setOf(
    C.ENCODING_PCM_8BIT, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_24BIT,
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT,
)

/** The audio goes out as it came (Dolby / DTS bitstream) and the TV or amplifier decodes it. */
val StreamTech.audioPassthrough: Boolean
    @UnstableApi get() = outputEncoding != null && outputEncoding !in PcmEncodings && audioDecoder == null

@UnstableApi
fun codecName(mime: String?): String? = when (mime) {
    null -> null
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
    MimeTypes.AUDIO_MPEG_L2 -> "MP2"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    else -> mime.substringAfter('/').uppercase()
}

fun channelsLabel(count: Int): String? = when (count) {
    0 -> null
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count ch"
}

private fun kindLabel(kind: DecoderKind): String? = when (kind) {
    DecoderKind.NONE -> null
    DecoderKind.HARDWARE -> tx("donanım", "hardware")
    DecoderKind.ANDROID_SOFTWARE -> tx("yazılım (Android)", "software (Android)")
    DecoderKind.FFMPEG -> tx("yazılım (FFmpeg)", "software (FFmpeg)")
}

/** "HEVC · hardware · c2.vendor.hevc.decoder"; null while nothing is known. */
@UnstableApi
fun videoTechLabel(t: StreamTech): String? {
    val codec = codecName(t.videoMime) ?: return null
    return listOfNotNull(codec, kindLabel(decoderKind(t.videoDecoder)), t.videoDecoder).joinToString(" · ")
}

/**
 * "AC3 5.1 · software (FFmpeg) · ffmpeg6.0-ac3 · out stereo", or "AC3 5.1 · passed through (the TV or
 * amplifier decodes it)"; null while nothing is known.
 */
@UnstableApi
fun audioTechLabel(t: StreamTech): String? {
    val codec = codecName(t.audioMime) ?: return null
    val source = listOfNotNull(codec, channelsLabel(t.audioChannels)).joinToString(" ")
    if (t.audioPassthrough) return "$source · " + tx("doğrudan çıkış (TV / ses sistemi çözer)", "passed through (the TV or amplifier decodes it)")
    val out = if (t.outputChannels > 0 && t.audioChannels > 0 && t.outputChannels != t.audioChannels) {
        tx("çıkış ", "out ") + channelsLabel(t.outputChannels)
    } else {
        null
    }
    return listOfNotNull(source, kindLabel(decoderKind(t.audioDecoder)), t.audioDecoder, out).joinToString(" · ")
}

/**
 * Collects the decoders and the audio output of a player for its whole life, so a player that is handed
 * from the preview to the full-screen player keeps what it learned. Attached when the player is built.
 */
@UnstableApi
class TechCollector : AnalyticsListener {
    @Volatile
    var tech = StreamTech()
        private set

    /** Called on the main thread whenever [tech] changed. */
    @Volatile
    var onChange: (() -> Unit)? = null

    private fun update(change: (StreamTech) -> StreamTech) {
        tech = change(tech)
        onChange?.invoke()
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        update { it.copy(videoMime = format.sampleMimeType, videoDecoder = if (reused(decoderReuseEvaluation)) it.videoDecoder else null) }
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        update { it.copy(videoDecoder = decoderName) }
    }

    override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
        update { if (it.videoDecoder == decoderName) it.copy(videoDecoder = null) else it }
    }

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        update {
            it.copy(
                audioMime = format.sampleMimeType,
                audioChannels = format.channelCount.coerceAtLeast(0),
                audioRate = format.sampleRate.coerceAtLeast(0),
                audioDecoder = if (reused(decoderReuseEvaluation)) it.audioDecoder else null,
            )
        }
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        update { it.copy(audioDecoder = decoderName) }
    }

    override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
        update { if (it.audioDecoder == decoderName) it.copy(audioDecoder = null) else it }
    }

    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        update { it.copy(outputEncoding = audioTrackConfig.encoding, outputChannels = Integer.bitCount(audioTrackConfig.channelConfig)) }
    }

    /** A decoder that survives a format change keeps its name; one that does not announces its successor. */
    private fun reused(evaluation: DecoderReuseEvaluation?) = evaluation != null && evaluation.result != DecoderReuseEvaluation.REUSE_RESULT_NO
}
