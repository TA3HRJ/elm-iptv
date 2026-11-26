package io.github.ta3hrj.elm.core

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class StreamRecorderTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }

    private lateinit var server: MockWebServer
    private var fakeNow = 0L
    private val recorder by lazy { StreamRecorder(OkHttpClient(), clock = { fakeNow }, sleep = { fakeNow += it }) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/live.ts" -> MockResponse().setBody(Buffer().write(ByteArray(200_000) { (it % 251).toByte() }))
                "/master.m3u8" -> MockResponse().setBody(
                    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nhigh/index.m3u8\n",
                )
                "/high/index.m3u8" -> MockResponse().setBody(
                    "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:10\n" +
                        "#EXTINF:4,\nseg10.ts\n#EXTINF:4,\nseg11.ts\n#EXTINF:4,\nhttp://${request.requestUrl!!.host}:${request.requestUrl!!.port}/abs/seg12.ts\n#EXT-X-ENDLIST\n",
                )
                "/high/seg10.ts" -> MockResponse().setBody("AAA")
                "/high/seg11.ts" -> MockResponse().setBody("BBB")
                "/abs/seg12.ts" -> MockResponse().setBody("CCC")
                "/enc.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n#EXTINF:4,\ns.ts\n")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun copiesTransportStreamUntilItEndsThenStopsAtDeadline() {
        val out = ByteArrayOutputStream()
        // The server closes after 200 kB; the recorder reconnects until the (fake) deadline passes.
        val result = recorder.record(server.url("/live.ts").toString(), "UA", out, until = 2_000, isCancelled = { false })
        assertNull(result.error)
        assertTrue(result.bytes >= 200_000)
        assertEquals(out.size().toLong(), result.bytes)
        assertEquals("UA", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun followsBestHlsVariantAndAppendsSegmentsInOrder() {
        val out = ByteArrayOutputStream()
        val result = recorder.record(server.url("/master.m3u8").toString(), "UA", out, until = 60_000, isCancelled = { false })
        assertNull(result.error)
        assertArrayEquals("AAABBBCCC".toByteArray(), out.toByteArray())
    }

    @Test
    fun refusesEncryptedHls() {
        val result = recorder.record(server.url("/enc.m3u8").toString(), "UA", ByteArrayOutputStream(), until = 60_000, isCancelled = { false })
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Şifreli"))
    }

    @Test
    fun stopsWhenCancelled() {
        val result = recorder.record(server.url("/live.ts").toString(), "UA", ByteArrayOutputStream(), until = Long.MAX_VALUE, isCancelled = { true })
        assertEquals(0, result.bytes)
    }
}
