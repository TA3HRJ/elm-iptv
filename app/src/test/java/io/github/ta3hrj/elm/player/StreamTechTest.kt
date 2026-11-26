package io.github.ta3hrj.elm.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import io.github.ta3hrj.elm.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamTechTest {
    @Before
    fun english() = Lang.apply(Lang.Choice.ENGLISH)

    @Test
    fun decoderKinds() {
        assertEquals(DecoderKind.NONE, decoderKind(null))
        assertEquals(DecoderKind.FFMPEG, decoderKind("ffmpeg6.0-ac3"))
        assertEquals(DecoderKind.ANDROID_SOFTWARE, decoderKind("c2.android.aac.decoder"))
        assertEquals(DecoderKind.ANDROID_SOFTWARE, decoderKind("OMX.google.h264.decoder"))
        assertEquals(DecoderKind.HARDWARE, decoderKind("c2.amlogic.avc.decoder"))
        assertEquals(DecoderKind.HARDWARE, decoderKind("OMX.MTK.VIDEO.DECODER.HEVC"))
    }

    @Test
    fun videoLabel() {
        val tech = StreamTech(videoMime = MimeTypes.VIDEO_H265, videoDecoder = "c2.amlogic.hevc.decoder")
        assertEquals("HEVC · hardware · c2.amlogic.hevc.decoder", videoTechLabel(tech))
        assertNull(videoTechLabel(StreamTech()))
    }

    @Test
    fun decodedAudioNamesTheDecoderAndAnyDownmix() {
        val tech = StreamTech(
            audioMime = MimeTypes.AUDIO_AC3, audioChannels = 6, audioDecoder = "ffmpeg6.0-ac3",
            outputEncoding = C.ENCODING_PCM_16BIT, outputChannels = 2,
        )
        assertFalse(tech.audioPassthrough)
        assertEquals("AC3 5.1 · software (FFmpeg) · ffmpeg6.0-ac3 · out stereo", audioTechLabel(tech))
    }

    @Test
    fun hardwareAacAtTheSameChannelCountSaysNothingAboutOutput() {
        val tech = StreamTech(
            audioMime = MimeTypes.AUDIO_AAC, audioChannels = 2, audioDecoder = "c2.amlogic.aac.decoder",
            outputEncoding = C.ENCODING_PCM_16BIT, outputChannels = 2,
        )
        assertEquals("AAC stereo · hardware · c2.amlogic.aac.decoder", audioTechLabel(tech))
    }

    @Test
    fun anEncodedOutputWithoutADecoderIsPassthrough() {
        val tech = StreamTech(audioMime = MimeTypes.AUDIO_E_AC3, audioChannels = 6, outputEncoding = C.ENCODING_E_AC3, outputChannels = 2)
        assertTrue(tech.audioPassthrough)
        assertEquals("E-AC3 5.1 · passed through (the TV or amplifier decodes it)", audioTechLabel(tech))
    }

    @Test
    fun channelLabels() {
        assertEquals(listOf(null, "mono", "stereo", "5.1", "7.1", "3 ch"), listOf(0, 1, 2, 6, 8, 3).map { channelsLabel(it) })
    }
}
