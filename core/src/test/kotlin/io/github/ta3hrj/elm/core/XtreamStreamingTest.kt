package io.github.ta3hrj.elm.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Big Xtream lists are read element by element; odd but common server answers still work. */
class XtreamStreamingTest {
    private val server = MockWebServer()
    private var vodStreams: String = "[]"

    private fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.queryParameter("action")) {
                "get_vod_categories" -> MockResponse().setBody("""[{"category_id":"1","category_name":"Film"}]""")
                "get_vod_streams" -> MockResponse().setBody(Buffer().writeUtf8(vodStreams))
                else -> MockResponse().setResponseCode(400)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client() = XtreamClient(OkHttpClient(), PlaylistSource.Xtream(server.url("/").toString(), "u", "p"), "UA")

    @Test
    fun readsAVeryLongList() = runBlocking {
        vodStreams = (1..30_000).joinToString(",", "[", "]") { """{"num":$it,"name":"Film $it","stream_id":$it,"category_id":"1"}""" }
        start()
        val movies = client().movies()
        assertEquals(30_000, movies.size)
        assertEquals("Film 1", movies.first().name)
        assertEquals("Film 30000", movies.last().name)
    }

    @Test
    fun toleratesByteOrderMarkWhitespaceAndOddElements() = runBlocking {
        vodStreams = "﻿ \n [ 1, null, \"x\", {\"name\":\"Film\",\"stream_id\":5,\"category_id\":\"1\"}, {\"name\":\"no id\"} ]"
        start()
        assertEquals(listOf("Film"), client().movies().map { it.name })
    }

    @Test
    fun objectInsteadOfArrayMeansEmpty() = runBlocking {
        vodStreams = "{}"
        start()
        assertTrue(client().movies().isEmpty())
    }
}
