package io.github.ta3hrj.elm.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DropReasonTest {
    @Test
    fun anEndedStreamSaysTheServerClosedIt() {
        assertEquals("stream ended (the server closed it)", dropReason(null, null, null))
    }

    @Test
    fun anHttpStatusIsEnough() {
        assertEquals("HTTP 503", dropReason("ERROR_CODE_IO_BAD_HTTP_STATUS", 503, RuntimeException("Response code: 503")))
    }

    @Test
    fun otherwiseTheErrorAndItsDeepestCause() {
        val cause = java.io.IOException("wrapper", java.net.SocketTimeoutException("timeout\nat line 2"))
        assertEquals("IO_NETWORK_CONNECTION_TIMEOUT (SocketTimeoutException: timeout)", dropReason("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", null, cause))
        assertEquals("IO_UNSPECIFIED (EOFException)", dropReason("ERROR_CODE_IO_UNSPECIFIED", null, java.io.EOFException()))
        assertEquals("TIMEOUT", dropReason("ERROR_CODE_TIMEOUT", null, null))
    }

    @Test
    fun theLineSaysWhereWhatAndWhatComesNext() {
        assertEquals(
            "stream dropped (full screen, TRT 1 HD): HTTP 503 · 2.5 s buffered · retry 1 in 3 s",
            dropLine("full screen", "TRT 1 HD", "HTTP 503", 2_450, 1, 3_000),
        )
        assertEquals(
            "stream dropped (preview): HTTP 404 · 0.0 s buffered · not tried again",
            dropLine("preview", null, "HTTP 404", -5, 6, null),
        )
    }
}
