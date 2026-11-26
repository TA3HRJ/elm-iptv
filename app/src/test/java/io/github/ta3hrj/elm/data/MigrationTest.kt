package io.github.ta3hrj.elm.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Upgrades databases of every earlier version (built from app/schemas) with the real Room migrations.
 * Room checks the resulting tables against the current schema when it opens the database, so a wrong
 * migration fails here instead of on a user's device; personal data is checked to survive.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyOldVersionUpgradesToTheCurrentSchema() = runBlocking {
        for (version in 1 until CURRENT) {
            val name = "upgrade-$version.db"
            createAt(version, name) { db -> insert(db, "playlists", mapOf("id" to 5, "name" to "Liste", "type" to "m3u", "url" to "http://x")) }
            val room = open(name)
            val playlists = room.playlists().observeAll().first()
            assertEquals("version $version", listOf("Liste"), playlists.map { it.name })
            room.close()
        }
    }

    @Test
    fun personalDataMovesToTheMainProfile() = runBlocking {
        createAt(8, "v8.db") { db ->
            insert(db, "playlists", mapOf("id" to 5, "name" to "Liste", "type" to "m3u", "url" to "http://x"))
            insert(db, "favorites", mapOf("playlistId" to 5, "streamUrl" to "http://a", "addedAt" to 1))
            insert(db, "recent_channels", mapOf("playlistId" to 5, "streamUrl" to "http://a", "watchedAt" to 2))
            insert(db, "favorite_lists", mapOf("id" to 3, "playlistId" to 5, "name" to "Spor", "position" to 0))
            insert(db, "favorite_list_items", mapOf("listId" to 3, "streamUrl" to "http://a", "addedAt" to 1))
            insert(db, "watchlist", mapOf("playlistId" to 5, "kind" to "movie", "providerId" to "7", "addedAt" to 1))
        }
        val room = open("v8.db")
        assertEquals(listOf("http://a"), room.channels().observeFavoriteUrls(5, ProfileManager.MAIN_ID).first())
        assertEquals(listOf("Spor"), room.lists().lists(5, ProfileManager.MAIN_ID).map { it.name })
        assertEquals(listOf("http://a"), room.lists().itemUrls(3))
        assertEquals(1, room.library().watchlist(5, ProfileManager.MAIN_ID).size)
        assertEquals(listOf(ProfileManager.MAIN_ID), room.profiles().all().map { it.id })
        room.close()
    }

    @Test
    fun searchColumnsStartEmptyAndAreFilledAtStart() = runBlocking {
        createAt(9, "v9.db") { db ->
            insert(db, "playlists", mapOf("id" to 5, "name" to "Liste", "type" to "m3u", "url" to "http://x"))
            insert(db, "channels", mapOf("playlistId" to 5, "position" to 0, "name" to "ÇOCUK TV", "groupTitle" to "Çocuk", "streamUrl" to "http://c", "catchupDays" to 0))
        }
        val room = open("v9.db")
        assertEquals(1, room.searchIndex().channelsToFill().size)
        room.close()
    }

    private fun open(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()

    /** Builds the database exactly as version [version] of the app left it. */
    private fun createAt(version: Int, name: String, fill: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val schema = JSONObject(File("schemas/io.github.ta3hrj.elm.data.AppDatabase/$version.json").readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
        fill(db)
        db.version = version
        db.close()
    }

    /** Inserts a row, giving every other required column a neutral value. */
    private fun insert(db: SQLiteDatabase, table: String, values: Map<String, Any>) {
        val row = ContentValues()
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            while (c.moveToNext()) {
                val column = c.getString(1)
                val type = c.getString(2).uppercase()
                val notNull = c.getInt(3) == 1
                val hasDefault = !c.isNull(4)
                val isPk = c.getInt(5) > 0
                when {
                    column in values -> when (val v = values.getValue(column)) {
                        is Int -> row.put(column, v)
                        is Long -> row.put(column, v)
                        else -> row.put(column, v.toString())
                    }
                    notNull && !hasDefault && !(isPk && type == "INTEGER") -> if (type == "INTEGER") row.put(column, 0) else row.put(column, "")
                }
            }
        }
        assertTrue("insert into $table", db.insert(table, null, row) >= 0)
    }

    private companion object {
        const val CURRENT = 12
    }
}
