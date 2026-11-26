package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.ta3hrj.elm.core.PlaylistLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Channel lists as the screens get them: search, per-profile favorites, kids restrictions, own order. */
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var lock: ParentalLock
    private lateinit var profiles: ProfileManager
    private lateinit var repository: PlaylistRepository
    private var playlistId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(AppDatabase.DEFAULT_PROFILE_SQL)
            })
            .allowMainThreadQueries()
            .build()
        val prefs = context.getSharedPreferences("repository-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        lock = ParentalLock(prefs)
        profiles = ProfileManager(db, prefs, lock, scope)
        val settings = AppSettings(prefs)
        repository = PlaylistRepository(db, PlaylistLoader(OkHttpClient(), "test"), settings, profiles)
        playlistId = db.playlists().insert(PlaylistEntity(name = "Liste", type = PlaylistType.M3U, url = "http://x"))
        db.channels().insertAll(
            listOf(
                channel("ÇOCUK TV", "Çocuk", 0),
                channel("İSTANBUL HABER", "Haber", 1),
                channel("Spor 1", "Spor", 2),
                channel("Gece Kanalı", "Yetişkin", 3),
                channel("Minika", "Çocuk", 4),
            ),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun channel(name: String, group: String, position: Int) = ChannelEntity(
        playlistId = playlistId, position = position, name = name, groupTitle = group, logo = null,
        streamUrl = "http://s/$position", epgId = null, userAgent = null, catchupDays = 0, number = position + 1,
    )

    private suspend fun search(text: String) =
        repository.channels(playlistId, ChannelQuery.Search(text), unlocked = true).map { it.name }

    @Test
    fun searchIgnoresCaseAndTurkishLetters() = runBlocking {
        assertEquals(listOf("ÇOCUK TV"), search("cocuk"))
        assertEquals(listOf("ÇOCUK TV"), search("Çocuk"))
        assertEquals(listOf("İSTANBUL HABER"), search("istanbul"))
        assertEquals(listOf("İSTANBUL HABER"), search("ISTANBUL"))
        assertEquals(listOf("Gece Kanalı"), search("kanali"))
        // Wildcards typed by the user are not wildcards.
        assertEquals(emptyList<String>(), search("%"))
    }

    @Test
    fun rowsStoredBeforeTheSearchColumnAreFilledAtStart() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE channels SET searchName = ''")
        assertEquals(emptyList<String>(), search("cocuk"))
        repository.fillSearchIndex()
        assertEquals(listOf("ÇOCUK TV"), search("cocuk"))
    }

    @Test
    fun favoritesBelongToTheProfile() = runBlocking {
        val all = db.channels().all(playlistId)
        repository.toggleFavorite(all[0])
        val second = profiles.get(profiles.create("İkinci", ProfileManager.DEFAULT_COLOR, kids = false, filter = null))!!
        profiles.select(second)
        assertEquals(emptyList<String>(), repository.channels(playlistId, ChannelQuery.Favorites, true).map { it.name })
        repository.toggleFavorite(all[2])
        assertEquals(listOf("Spor 1"), repository.channels(playlistId, ChannelQuery.Favorites, true).map { it.name })
        profiles.select(profiles.get(ProfileManager.MAIN_ID)!!)
        assertEquals(listOf("ÇOCUK TV"), repository.channels(playlistId, ChannelQuery.Favorites, true).map { it.name })
    }

    @Test
    fun kidsProfileSeesOnlyAllowedAndNeverLockedCategories() = runBlocking {
        lock.setPin("1234")
        repository.setGroupFlag(playlistId, FlagScopes.LIVE, "Yetişkin", locked = true)
        val kids = profiles.get(
            profiles.create("Çocuk", ProfileManager.DEFAULT_COLOR, kids = true, filter = ContentFilter(kids = true, live = setOf("Çocuk", "Yetişkin"))),
        )!!
        profiles.select(kids)
        // Even with the PIN entered, the kids profile does not get locked content.
        assertTrue(lock.unlock("1234"))
        assertFalse(profiles.contentUnlockedNow)
        assertEquals(listOf("Çocuk"), repository.observeGroups(playlistId).first().map { it.name })
        assertEquals(listOf("ÇOCUK TV", "Minika"), search(""))
        assertEquals(null, repository.channelByNumber(playlistId, 4))
        assertEquals("Minika", repository.channelByNumber(playlistId, 5)?.name)
    }

    @Test
    fun archiveListsOnlyChannelsWithCatchUp() = runBlocking {
        db.channels().insertAll(
            listOf(
                channel("Arşivli Haber", "Haber", 10).copy(streamUrl = "http://s/a1", catchupDays = 3),
                channel("Arşivli Gece", "Yetişkin", 11).copy(streamUrl = "http://s/a2", catchupType = "xtream"),
            ),
        )
        repository.setGroupFlag(playlistId, FlagScopes.LIVE, "Yetişkin", locked = true)
        assertEquals(listOf("Arşivli Haber", "Arşivli Gece"), repository.channels(playlistId, ChannelQuery.Archive, unlocked = true).map { it.name })
        // Locked categories stay out until the PIN is entered.
        assertEquals(listOf("Arşivli Haber"), repository.channels(playlistId, ChannelQuery.Archive, unlocked = false).map { it.name })
    }

    @Test
    fun favoritesKeepTheirOwnOrder() = runBlocking {
        val all = db.channels().all(playlistId)
        repository.toggleFavorite(all[0])
        repository.toggleFavorite(all[1])
        repository.toggleFavorite(all[2])
        repository.move(ChannelQuery.Favorites, all[2], -2)
        assertEquals(
            listOf("Spor 1", "ÇOCUK TV", "İSTANBUL HABER"),
            repository.channels(playlistId, ChannelQuery.Favorites, true).map { it.name },
        )
        assertFalse(repository.move(ChannelQuery.Favorites, all[2], -1))
    }
}
