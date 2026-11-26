package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.core.PlaylistLoader
import io.github.ta3hrj.elm.core.SearchText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

/**
 * A big catalogue (30 000 channels, 20 000 films) through the real queries and sorts. A diagnostic, run on
 * request (-Dperf=1): the numbers are from a computer, not a Fire TV Stick, so they show what grows badly
 * with size rather than absolute times on the device.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogSpeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bigCatalogue() = runBlocking {
        assumeTrue(System.getProperty("perf") != null)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(AppDatabase.DEFAULT_PROFILE_SQL)
            })
            .allowMainThreadQueries().build()
        val prefs = context.getSharedPreferences("speed-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lock = ParentalLock(prefs)
        val profiles = ProfileManager(db, prefs, lock, scope)
        val repository = PlaylistRepository(db, PlaylistLoader(OkHttpClient(), "test"), AppSettings(prefs), profiles)
        val playlistId = db.playlists().insert(PlaylistEntity(name = "Büyük", type = PlaylistType.XTREAM, url = "http://x"))
        val report = StringBuilder("CATALOGUE SPEED (computer, not a TV stick)\n")
        fun line(name: String, ms: Long, extra: String = "") { report.append("  %-46s %6d ms %s\n".format(name, ms, extra)) }

        val channels = (0 until 30_000).map { i ->
            val name = "TRT ${i} Kanalı İSTANBUL HD $i"
            ChannelEntity(
                playlistId = playlistId, position = i, name = name, groupTitle = if (i < 3_000) "Dev Kategori" else "Kategori ${i % 300}",
                logo = null, streamUrl = "http://x/live/$i.ts", epgId = null, userAgent = null, catchupDays = 0, number = i + 1,
                searchName = SearchText.normalize(name),
            )
        }
        line("insert 30 000 channels", measureTimeMillis { db.channels().insertAll(channels) })
        val films = (0 until 20_000).map { i ->
            VodItemEntity(
                playlistId = playlistId, kind = VodKinds.MOVIE, providerId = "m$i", position = i, name = "Film $i: Çok Uzun Bir Film Adı",
                category = "Kategori ${i % 200}", poster = null, rating = "${i % 10}.${i % 7}", year = "${1980 + i % 46}",
                containerExtension = "mp4", added = i.toLong(),
            )
        }
        line("insert 20 000 films", measureTimeMillis { db.vod().insertAll(films) })

        var size = 0
        line("channels of one 3 000-channel category", measureTimeMillis { size = repository.channels(playlistId, ChannelQuery.Group("Dev Kategori"), true).size }, "-> $size")
        line("channels of one 100-channel category", measureTimeMillis { size = repository.channels(playlistId, ChannelQuery.Group("Kategori 7"), true).size }, "-> $size")
        line("search 'istanbul 29999' in 30 000", measureTimeMillis { size = repository.channels(playlistId, ChannelQuery.Search("istanbul 29999"), true).size }, "-> $size")
        line("search 'trt' (matches everything)", measureTimeMillis { size = repository.channels(playlistId, ChannelQuery.Search("trt"), true).size }, "-> $size")
        line("category list (300 groups), first emission", measureTimeMillis { size = repository.observeGroups(playlistId).first().size }, "-> $size")
        line("fillSearchIndex when nothing to fill", measureTimeMillis { repository.fillSearchIndex() })
        line("film search 'uzun bir film 19999'", measureTimeMillis { size = db.vod().observeSearch(playlistId, VodKinds.MOVIE, "%uzun bir film 19999%", true).first().size }, "-> $size")
        val all = db.vod().observeCategory(playlistId, VodKinds.MOVIE, "Kategori 5").first()
        for (order in VodOrder.entries) line("sort 20 000 films: $order", measureTimeMillis { sortVod(films, order) })
        line("films of one category (100)", 0, "-> ${all.size}")
        report.append("  names normalised for the search column: %d ms for 30 000\n".format(measureTimeMillis { channels.forEach { SearchText.normalize(it.name) } }))
        println(report)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }
}
