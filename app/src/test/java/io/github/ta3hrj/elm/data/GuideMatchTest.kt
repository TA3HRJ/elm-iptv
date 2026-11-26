package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.core.PlaylistLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Matching a channel to the guide by hand (the match screen's data). */
@RunWith(RobolectricTestRunner::class)
class GuideMatchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var guide: GuideRepository
    private lateinit var channel: ChannelEntity
    private var playlistId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val prefs = context.getSharedPreferences("guide-match-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val http = OkHttpClient()
        guide = GuideRepository(db, PlaylistLoader(http, "test"), scope, AppSettings(prefs), http)
        playlistId = db.playlists().insert(PlaylistEntity(name = "Liste", type = PlaylistType.M3U, url = "http://x"))
        channel = ChannelEntity(
            playlistId = playlistId, position = 0, name = "TR: BEIN SERIES 4 FHD", groupTitle = "Sinema", logo = null,
            streamUrl = "http://s/145", epgId = null, userAgent = null, catchupDays = 0, number = 145,
        )
        db.channels().insertAll(listOf(channel))
        db.epg().insertSources(
            listOf(
                EpgSourceEntity(playlistId, "trt1.tr", "TRT 1"),
                EpgSourceEntity(playlistId, "beinsports4.tr", "beIN Sports 4"),
                EpgSourceEntity(playlistId, "beinseries4.tr", "beIN Series 4 HD"),
            ),
        )
        db.epg().insertProgrammes(listOf(ProgrammeEntity(playlistId = playlistId, channelKey = "beinseries4.tr", start = 0, stop = 1, title = "Organize İşler", description = null)))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun likelyMatchesComeFirst() = runBlocking {
        val info = guide.matchInfo(playlistId, channel)
        assertEquals("beIN Series 4 HD", info.sources.first().name)
        assertNull(info.current)
        assertNull(info.override)
    }

    @Test
    fun aHandMadeMatchAppliesAtOnceWhenTheGuideHasItsProgrammes() = runBlocking {
        assertFalse("no download needed", guide.setManualMatch(playlistId, channel, "beinseries4.tr"))
        val info = guide.matchInfo(playlistId, channel)
        assertEquals("beinseries4.tr", info.current)
        assertEquals("beinseries4.tr", info.override)
    }

    @Test
    fun noGuideRemovesTheMatchAndIsKept() = runBlocking {
        guide.setManualMatch(playlistId, channel, "beinseries4.tr")
        assertFalse(guide.setManualMatch(playlistId, channel, ""))
        val info = guide.matchInfo(playlistId, channel)
        assertNull(info.current)
        assertEquals("", info.override)
    }
}
