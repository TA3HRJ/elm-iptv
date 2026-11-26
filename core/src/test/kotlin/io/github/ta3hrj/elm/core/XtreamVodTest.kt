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
import org.junit.Before
import org.junit.Test

class XtreamVodTest {
    init {
        Lang.apply(Lang.Choice.TURKISH)
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val body = when (url.queryParameter("action")) {
                    null -> """{"user_info":{"auth":1,"username":"u"},"server_info":{"timezone":"Europe/Istanbul"}}"""
                    "get_vod_categories" -> """[{"category_id":"1","category_name":"Aksiyon"},{"category_id":"2","category_name":"Dram"}]"""
                    "get_vod_streams" -> """[
                        {"num":2,"name":"Film B","stream_id":12,"stream_icon":"http://p/b.jpg","rating":"7.5","category_id":"2","container_extension":"mkv","added":"1700000000"},
                        {"num":1,"name":"Film A","stream_id":11,"stream_icon":"","rating":"0","category_id":"1","container_extension":"mp4"}]"""
                    "get_series_categories" -> """[{"category_id":"5","category_name":"Yerli Dizi"}]"""
                    "get_series" -> """[{"num":1,"name":"Dizi X","series_id":77,"cover":"http://p/x.jpg","category_id":"5","releaseDate":"2021-03-01","rating":"8"}]"""
                    "get_vod_info" -> """{"info":{"plot":"Konu","cast":"Oyuncu","genre":"Aksiyon","duration":"01:45:00","backdrop_path":["http://p/bd.jpg"]},"movie_data":{"stream_id":11}}"""
                    "get_series_info" -> """{"info":{"plot":"Dizi konusu","backdrop_path":"http://p/sbd.jpg"},
                        "episodes":{"2":[{"id":"902","episode_num":"1","title":"S2B1","container_extension":"mp4","season":2}],
                                    "1":[{"id":"802","episode_num":2,"title":"S1B2","container_extension":"mkv","season":1,"info":{"plot":"P2"}},
                                         {"id":"801","episode_num":1,"title":"S1B1","container_extension":"mkv","season":1}]}}"""
                    else -> return MockResponse().setResponseCode(400)
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client() = XtreamClient(OkHttpClient(), PlaylistSource.Xtream(server.url("/").toString(), "u", "p"), "UA")

    @Test
    fun accountReportsServerTimezone() = runBlocking {
        assertEquals("Europe/Istanbul", client().account().serverTimezone)
    }

    @Test
    fun moviesAreOrderedAndHaveUrls() = runBlocking {
        val movies = client().movies()
        assertEquals(listOf("Film A", "Film B"), movies.map { it.name })
        assertEquals(listOf("Aksiyon", "Dram"), movies.map { it.category })
        assertNull(movies[0].poster)
        assertNull("rating 0 means unrated", movies[0].rating)
        assertEquals("7.5", movies[1].rating)
        assertEquals(server.url("/movie/u/p/12.mkv").toString(), client().movieUrl(movies[1].providerId, movies[1].containerExtension))
    }

    @Test
    fun seriesAndEpisodes() = runBlocking {
        val series = client().series().single()
        assertEquals("Dizi X", series.name)
        assertEquals("2021", series.year)
        val info = client().seriesInfo(series.providerId)
        assertEquals(listOf(1, 2), info.seasons.keys.toList())
        assertEquals(listOf("S1B1", "S1B2"), info.seasons.getValue(1).map { it.title })
        assertEquals("P2", info.seasons.getValue(1)[1].plot)
        assertEquals("http://p/sbd.jpg", info.backdrop)
        assertEquals(server.url("/series/u/p/801.mkv").toString(), client().episodeUrl("801", "mkv"))
    }

    @Test
    fun movieInfo() = runBlocking {
        val info = client().movieInfo("11")
        assertEquals("Konu", info.plot)
        assertEquals("01:45:00", info.duration)
        assertEquals("http://p/bd.jpg", info.backdrop)
    }
}
