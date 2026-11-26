package io.github.ta3hrj.elm.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class XtreamClientTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }


    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath != "/player_api.php") return MockResponse().setResponseCode(404)
                if (url.queryParameter("password") != "secret") {
                    return json("""{"user_info":{"auth":0}}""")
                }
                return when (url.queryParameter("action")) {
                    null -> json(
                        """{"user_info":{"username":"erhan","auth":1,"status":"Active",
                           "exp_date":"1798761600","max_connections":"2","active_cons":"0",
                           "created_at":"1656633600","message":"PRO4SERVER","is_trial":"0"},
                           "server_info":{"url":"example","port":"8080"}}""",
                    )
                    "get_live_categories" -> json(
                        """[{"category_id":"20","category_name":"Spor","parent_id":0},
                            {"category_id":"10","category_name":"Ulusal","parent_id":0}]""",
                    )
                    "get_live_streams" -> json(
                        """[
                          {"num":2,"name":"Ulusal B","stream_id":102,"stream_icon":"","epg_channel_id":null,"category_id":"10","tv_archive":0},
                          {"num":1,"name":"Ulusal A","stream_id":101,"stream_icon":"http://l/a.png","epg_channel_id":"a.tr","category_id":"10","tv_archive":1,"tv_archive_duration":"3"},
                          {"num":3,"name":"Spor 1","stream_id":"201","category_id":"20"},
                          {"num":4,"name":"Kategorisiz","stream_id":301,"category_id":null}
                        ]""",
                    )
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun client(password: String = "secret", format: String = "ts") = XtreamClient(
        http,
        PlaylistSource.Xtream(server.url("/").toString(), "erhan", password, liveFormat = format),
        "TestAgent",
    )

    @Test
    fun readsAccount() = runBlocking {
        val account = client().account()
        assertEquals("erhan", account.username)
        assertEquals(1656633600L, account.createdAt)
        assertEquals("PRO4SERVER", account.message)
        assertEquals(false, account.trial)
        assertEquals("Active", account.status)
        assertEquals(1798761600L, account.expiresAt)
        assertEquals(2, account.maxConnections)
        assertEquals("TestAgent", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun wrongPasswordFailsWithReadableMessage() = runBlocking {
        try {
            client(password = "wrong").account()
            fail("expected PlaylistException")
        } catch (e: PlaylistException) {
            assertTrue(e.message!!.contains("şifre"))
        }
    }

    @Test
    fun channelsAreOrderedByCategoryThenNumberAndHaveStreamUrls() = runBlocking {
        val channels = client().liveChannels()

        assertEquals(listOf("Spor 1", "Ulusal A", "Ulusal B", "Kategorisiz"), channels.map { it.name })
        assertEquals(listOf("Spor", "Ulusal", "Ulusal", UNGROUPED), channels.map { it.group })

        val first = channels[1]
        assertEquals(server.url("/live/erhan/secret/101.ts").toString(), first.streamUrl)
        assertEquals("a.tr", first.epgId)
        assertEquals("http://l/a.png", first.logo)
        assertEquals(3, first.catchupDays)
        assertNull(channels[2].logo)
        assertNull(channels[2].epgId)
    }

    @Test
    fun hlsFormatChangesExtension() {
        assertTrue(client(format = "m3u8").liveStreamUrl("5").endsWith("/live/erhan/secret/5.m3u8"))
        assertEquals(server.url("/xmltv.php?username=erhan&password=secret").toString(), client().xmltvUrl())
    }

    @Test
    fun normalizesCommonServerInputs() {
        assertEquals("http://host.example:8080/", XtreamClient.normalizeServer("host.example:8080").toString())
        assertEquals("http://host.example:8080/", XtreamClient.normalizeServer(" http://host.example:8080/ ").toString())
        assertEquals(
            "http://host.example/",
            XtreamClient.normalizeServer("http://host.example/player_api.php?username=a&password=b").toString(),
        )
        assertEquals(
            "https://host.example/panel/",
            XtreamClient.normalizeServer("https://host.example/panel/get.php?username=a").toString(),
        )
        assertNull(XtreamClient.normalizeServer("   "))
    }
}

class ArchiveTextTest {
    @org.junit.Test
    fun decodesBase64Titles() {
        org.junit.Assert.assertEquals("Haber Bülteni", decodeBase64Text("SGFiZXIgQsO8bHRlbmk="))
        org.junit.Assert.assertEquals(null, decodeBase64Text(""))
    }
}
