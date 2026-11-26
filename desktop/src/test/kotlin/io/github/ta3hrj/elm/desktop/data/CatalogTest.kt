package io.github.ta3hrj.elm.desktop.data

import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.VodKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The desktop app loads playlists and guides with the shared core code; these check the desktop side of it. */
class CatalogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val now = System.currentTimeMillis()
    private var playlistRequests = 0

    @Before
    fun setUp() {
        Lang.apply(Lang.Choice.ENGLISH)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/list.m3u" -> {
                    playlistRequests++
                    MockResponse().setBody(
                        """
                        #EXTM3U url-tvg="${server.url("/guide.xml")}"
                        #EXTINF:-1 tvg-id="one.tr" group-title="News",One HD
                        http://example.invalid/live/1.ts
                        #EXTINF:-1 group-title="Films",A Film (2021)
                        http://example.invalid/movie/u/p/7.mp4
                        #EXTINF:-1 group-title="Series",Show S01 E02
                        http://example.invalid/series/u/p/9.mkv
                        #EXTINF:-1 group-title="Series",Show S01 E01
                        http://example.invalid/series/u/p/8.mkv
                        """.trimIndent(),
                    )
                }
                "/guide.xml" -> MockResponse().setBody(
                    """
                    <tv>
                      <channel id="one.tr"><display-name>One HD</display-name></channel>
                      <programme channel="one.tr" start="${xmltv(now - 30 * 60_000)}" stop="${xmltv(now + 30 * 60_000)}"><title>News at Nine</title></programme>
                      <programme channel="one.tr" start="${xmltv(now + 30 * 60_000)}" stop="${xmltv(now + 90 * 60_000)}"><title>Weather</title></programme>
                    </tv>
                    """.trimIndent(),
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun xmltv(time: Long): String =
        java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(time))

    private fun catalog() = Catalog(OkHttpClient(), Storage(tmp.root))

    private val playlist get() = SavedPlaylist(1, "List", SavedPlaylist.TYPE_M3U, url = server.url("/list.m3u").toString())

    @Test
    fun `an M3U list is split into live channels, films and series`() = runBlocking {
        val content = catalog().content(playlist)
        assertEquals(listOf("One HD"), content.live.map { it.name })
        assertEquals(listOf("A Film (2021)"), content.m3uMovies.map { it.name })
        assertEquals("2021", content.m3uMovies.single().year)
        assertEquals(listOf("Show"), content.m3uSeries.map { it.name })
        val info = catalog().seriesInfo(playlist, content.m3uSeries.single(), content)
        assertEquals(listOf(1, 2), info.seasons[1]!!.map { it.number })
        assertEquals("http://example.invalid/series/u/p/8.mkv", catalog().episodeUrl(playlist, info.seasons[1]!!.first()))
    }

    @Test
    fun `the list is cached and only downloaded again on refresh`() = runBlocking {
        val catalog = catalog()
        catalog.content(playlist)
        catalog.content(playlist)
        assertEquals(1, playlistRequests)
        catalog.content(playlist, refresh = true)
        assertEquals(2, playlistRequests)
        assertEquals(listOf("A Film (2021)"), catalog.vod(playlist, VodKind.MOVIE, catalog.content(playlist)).map { it.name })
    }

    @Test
    fun `the guide announced by the list gives now and next`() = runBlocking {
        val catalog = catalog()
        val content = catalog.content(playlist)
        val guide = catalog.guide(playlist, content, now = now)
        val url = content.live.single().streamUrl
        assertEquals("News at Nine", guide.now(url, now)?.title)
        assertEquals("Weather", guide.next(url, now)?.title)
        assertNull(guide.now("http://other", now))
        // Read back from the cache, without the server.
        server.shutdown()
        assertEquals("News at Nine", catalog.guide(playlist, content, now = now).now(url, now)?.title)
    }

    @Test
    fun `a local M3U file works without a server`() = runBlocking {
        val file = File(tmp.root, "local.m3u")
        file.writeText("#EXTM3U\n#EXTINF:-1 group-title=\"G\",Local One\nhttp://example.invalid/a.ts\n")
        val content = catalog().content(SavedPlaylist(2, "Local", SavedPlaylist.TYPE_M3U, url = file.absolutePath))
        assertEquals(listOf("Local One"), content.live.map { it.name })
        val viaUri = catalog().content(SavedPlaylist(3, "Local", SavedPlaylist.TYPE_M3U, url = file.toURI().toString()))
        assertEquals(1, viaUri.live.size)
    }

    @Test
    fun `the user's own guide address wins over the list's`() {
        val content = io.github.ta3hrj.elm.desktop.data.PlaylistContent(emptyList(), null, listOf("http://list-guide"))
        assertEquals(listOf("http://list-guide"), catalog().guideUrls(playlist, content))
        assertEquals(listOf("http://a", "http://b"), catalog().guideUrls(playlist.copy(guideUrl = "http://a, http://b"), content))
    }

    @Test
    fun `a programme is found by binary search across many`() {
        val programmes = (0 until 1000).map { io.github.ta3hrj.elm.core.EpgProgramme("k", it * 60_000L, (it + 1) * 60_000L, "P$it", null) }
        val guide = Guide.of(mapOf("u" to "k"), programmes.shuffled())
        assertEquals("P500", guide.now("u", 500 * 60_000L + 1)?.title)
        assertEquals("P501", guide.next("u", 500 * 60_000L + 1)?.title)
        assertNotNull(guide.now("u", 0))
        assertNull(guide.now("u", 1000 * 60_000L))
        assertTrue(guide.programmeCount == 1000)
    }
}
