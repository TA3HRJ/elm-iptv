package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.SearchText

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object PlaylistType {
    const val M3U = "m3u"
    const val XTREAM = "xtream"
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    /** M3U link, or the Xtream server address. */
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val userAgent: String? = null,
    /** Xtream live stream container: "ts" or "m3u8". */
    val liveFormat: String = "ts",
    val channelCount: Int = 0,
    val updatedAt: Long = 0,
    val lastError: String? = null,
    /** Xtream subscription expiry (Unix seconds), when the server reports one. */
    val expiresAt: Long? = null,
    /** Guide (XMLTV) address entered by the user; overrides [epgAutoUrls] when set. */
    val epgUrl: String? = null,
    /** Guide addresses announced by the source (M3U `url-tvg`, Xtream `xmltv.php`), one per line. */
    val epgAutoUrls: String? = null,
    @ColumnInfo(defaultValue = "0") val epgUpdatedAt: Long = 0,
    val epgError: String? = null,
    @ColumnInfo(defaultValue = "0") val programmeCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val epgLinkedChannels: Int = 0,
    /** Xtream server time zone; catch-up start times are expressed in it. */
    val serverTimezone: String? = null,
    @ColumnInfo(defaultValue = "0") val vodUpdatedAt: Long = 0,
    val vodError: String? = null,
    @ColumnInfo(defaultValue = "0") val movieCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val seriesCount: Int = 0,
) {
    /** Addresses the guide is downloaded from. */
    fun guideUrls(): List<String> = (epgUrl?.takeIf { it.isNotBlank() } ?: epgAutoUrls.orEmpty())
        .split('\n', ',', ' ')
        .map { it.trim() }
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .distinct()
}

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // streamUrl / number: favorites, history, resume and remote-control numbers look channels up by them.
    indices = [Index("playlistId", "groupTitle"), Index("playlistId", "position"), Index("playlistId", "streamUrl"), Index("playlistId", "number")],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val name: String,
    val groupTitle: String,
    val logo: String?,
    val streamUrl: String,
    val epgId: String?,
    val userAgent: String?,
    val catchupDays: Int,
    /** Xtream stream_id. */
    val providerId: String? = null,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    /** Number for remote-control channel selection: tvg-chno / Xtream num, else the list position + 1. */
    @ColumnInfo(defaultValue = "0") val number: Int = 0,
    /** [name] without case and accents, for searching (see [SearchText]). */
    @ColumnInfo(defaultValue = "") val searchName: String = SearchText.normalize(name),
)

/**
 * Favorites are keyed by stream URL rather than row id, so they survive a playlist refresh
 * (which replaces all channel rows).
 */
@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "playlistId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    val streamUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** One programme of the TV guide. Times are Unix epoch milliseconds. */
@Entity(
    tableName = "programmes",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "channelKey", "stop")],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** XMLTV channel id. */
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
    @ColumnInfo(defaultValue = "") val searchTitle: String = SearchText.normalize(title),
)

/**
 * A guide match the user chose by hand for one channel; kept across guide updates and preferred over
 * the automatic match. An empty [epgKey] means "this channel has no guide".
 */
@Entity(
    tableName = "epg_overrides",
    primaryKeys = ["playlistId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpgOverrideEntity(val playlistId: Long, val streamUrl: String, val epgKey: String)

/** The channels a playlist's guide offers (saved at every guide update), to choose from by hand. */
@Entity(
    tableName = "epg_sources",
    primaryKeys = ["playlistId", "epgKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpgSourceEntity(val playlistId: Long, val epgKey: String, val name: String)

/** Which XMLTV channel a playlist channel's guide comes from; keyed by stream URL like favorites. */
@Entity(
    tableName = "epg_links",
    primaryKeys = ["playlistId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpgLinkEntity(
    val playlistId: Long,
    val streamUrl: String,
    val epgKey: String,
)

object VodKinds {
    const val MOVIE = "movie"
    const val SERIES = "series"
    /** An episode of an M3U series (Xtream episodes are fetched on demand instead). */
    const val EPISODE = "episode"
}

/** A film or series of an Xtream account. Episodes are loaded on demand and not stored. */
@Entity(
    tableName = "vod_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId", "kind", "category"),
        Index("playlistId", "kind", "position"),
        Index("playlistId", "kind", "seriesKey"),
    ],
)
data class VodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** [VodKinds.MOVIE] or [VodKinds.SERIES]. */
    val kind: String,
    val providerId: String,
    val position: Int,
    val name: String,
    val category: String,
    val poster: String?,
    val rating: String?,
    val year: String?,
    val containerExtension: String?,
    val added: Long?,
    /** Direct address (M3U films and episodes); null for Xtream items. */
    val streamUrl: String? = null,
    /** Groups M3U episodes with their series. */
    val seriesKey: String? = null,
    @ColumnInfo(defaultValue = "0") val season: Int = 0,
    @ColumnInfo(defaultValue = "0") val episode: Int = 0,
    @ColumnInfo(defaultValue = "") val searchName: String = SearchText.normalize(name),
)

/** Where the user stopped watching a film, episode or archived programme. */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "playlistId", "mediaKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatchProgressEntity(
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    /** e.g. "movie:123" or "episode:456". */
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** A "remind me" entry for a guide programme. */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId", "streamUrl", "start"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val streamUrl: String,
    val channelName: String,
    val title: String,
    val start: Long,
    val stop: Long,
)

object FlagScopes {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
}

/**
 * A user-made channel list ("Sports", "News"…) in addition to the main favorites.
 * [profileId] has no foreign key (a column with one cannot be added to an existing table, and
 * rebuilding the table would drop the list items); ProfileManager deletes a profile's lists itself.
 */
@Entity(
    tableName = "favorite_lists",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("profileId")],
)
data class FavoriteListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    val name: String,
    val position: Int,
)

/** A channel in a [FavoriteListEntity], by stream URL so it survives playlist refreshes. */
@Entity(
    tableName = "favorite_list_items",
    primaryKeys = ["listId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteListItemEntity(
    val listId: Long,
    val streamUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Channels watched recently, newest first. */
@Entity(
    tableName = "recent_channels",
    primaryKeys = ["profileId", "playlistId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecentChannelEntity(
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    val streamUrl: String,
    val watchedAt: Long,
)

/** A film or series the user wants to watch later, by provider id so it survives refreshes. */
@Entity(
    tableName = "watchlist",
    primaryKeys = ["profileId", "playlistId", "kind", "providerId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatchlistEntity(
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    val kind: String,
    val providerId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A film, episode or recording left part-way, with everything needed to resume it from the home screen. */
@Entity(
    tableName = "continue_watching",
    primaryKeys = ["profileId", "playlistId", "mediaKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ContinueWatchingEntity(
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    val playlistId: Long,
    val mediaKey: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val poster: String?,
    val seriesItemId: Long,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** Someone who watches: own favorites, lists, history and resume positions. Kids profiles are restricted. */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val kids: Boolean,
    /** Allowed categories of a kids profile, one per line; null = every category that is not locked. */
    val allowedLive: String? = null,
    val allowedMovies: String? = null,
    val allowedSeries: String? = null,
    val position: Int = 0,
)

/** "Record every episode": the programme title is recorded whenever it airs on the channel. */
@Entity(
    tableName = "recording_rules",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class RecordingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val streamUrl: String,
    val channelName: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A favorite list with its number of channels. */
data class FavoriteListRow(val id: Long, val name: String, val count: Int)

/** Parental lock and visibility of a category (live group, film or series category). */
@Entity(
    tableName = "group_flags",
    primaryKeys = ["playlistId", "scope", "name"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GroupFlagEntity(
    val playlistId: Long,
    /** [FlagScopes] value. */
    val scope: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

/** A live channel the user hid; keyed by stream URL so it stays hidden across refreshes. */
@Entity(
    tableName = "hidden_channels",
    primaryKeys = ["playlistId", "streamUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HiddenChannelEntity(
    val playlistId: Long,
    val streamUrl: String,
    val name: String,
)

object RecordingStatus {
    const val SCHEDULED = "scheduled"
    const val RECORDING = "recording"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

/** A scheduled, running or finished recording. Not tied to the playlist so files outlive it. */
@Entity(tableName = "recordings", indices = [Index("status"), Index("start")])
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelName: String,
    val title: String,
    val streamUrl: String,
    val userAgent: String,
    val start: Long,
    val stop: Long,
    /** [RecordingStatus] value. */
    val status: String,
    val filePath: String? = null,
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GroupRow(val name: String, val count: Int)

/** A programme joined with the playlist channel (stream URL) it belongs to. */
data class ChannelProgramme(
    val streamUrl: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

data class EpgChannelRow(val streamUrl: String, val name: String, val epgId: String?)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)
}

@Dao
interface ChannelDao {
    /** Visible groups (hidden groups left out), counting only visible channels. */
    @Query(
        """SELECT groupTitle AS name, COUNT(*) AS count FROM channels
           WHERE playlistId = :playlistId AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1)
           GROUP BY groupTitle ORDER BY MIN(position)""",
    )
    fun observeGroups(playlistId: Long): Flow<List<GroupRow>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) ORDER BY position")
    fun observeGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) ORDER BY position")
    suspend fun group(playlistId: Long, group: String): List<ChannelEntity>

    /** Favorites, without hidden channels and (unless [unlocked]) without channels of locked groups. */
    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM favorites WHERE playlistId = :playlistId AND profileId = :profileId) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY (SELECT f.addedAt FROM favorites f WHERE f.playlistId = :playlistId AND f.profileId = :profileId AND f.streamUrl = channels.streamUrl), position""")
    fun observeFavorites(playlistId: Long, profileId: Long, unlocked: Boolean): Flow<List<ChannelEntity>>

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM favorites WHERE playlistId = :playlistId AND profileId = :profileId) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY (SELECT f.addedAt FROM favorites f WHERE f.playlistId = :playlistId AND f.profileId = :profileId AND f.streamUrl = channels.streamUrl), position""")
    suspend fun favorites(playlistId: Long, profileId: Long, unlocked: Boolean): List<ChannelEntity>

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND searchName LIKE '%' || :query || '%' ESCAPE '\' AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY position LIMIT 300""")
    fun observeSearch(playlistId: Long, query: String, unlocked: Boolean): Flow<List<ChannelEntity>>

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND searchName LIKE '%' || :query || '%' ESCAPE '\' AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY position LIMIT 300""")
    suspend fun search(playlistId: Long, query: String, unlocked: Boolean): List<ChannelEntity>

    /** Visible channels with catch-up, without locked groups unless [unlocked]. */
    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND (catchupDays > 0 OR catchupType IS NOT NULL) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY position""")
    fun observeArchive(playlistId: Long, unlocked: Boolean): Flow<List<ChannelEntity>>

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND (catchupDays > 0 OR catchupType IS NOT NULL) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY position""")
    suspend fun archive(playlistId: Long, unlocked: Boolean): List<ChannelEntity>

    /** Every visible channel in list order (for the guide), without locked groups unless [unlocked]. */
    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1))
              ORDER BY position""")
    fun observeAllVisible(playlistId: Long, unlocked: Boolean): Flow<List<ChannelEntity>>

    /** A visible channel by its remote-control number. */
    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1)
              ORDER BY position LIMIT 1""")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId")
    suspend fun allGroupNames(playlistId: Long): List<String>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY position")
    suspend fun all(playlistId: Long): List<ChannelEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE playlistId = :playlistId AND (catchupDays > 0 OR catchupType IS NOT NULL))")
    suspend fun hasCatchup(playlistId: Long): Boolean

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl = :streamUrl ORDER BY position LIMIT 1")
    suspend fun byStreamUrl(playlistId: Long, streamUrl: String): ChannelEntity?

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)

    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Transaction
    suspend fun replace(playlistId: Long, channels: List<ChannelEntity>) {
        deleteForPlaylist(playlistId)
        channels.chunked(1000).forEach { insertAll(it) }
    }

    @Query("SELECT streamUrl FROM favorites WHERE playlistId = :playlistId AND profileId = :profileId ORDER BY addedAt")
    fun observeFavoriteUrls(playlistId: Long, profileId: Long): Flow<List<String>>

    /** Favorites are listed by [FavoriteEntity.addedAt]; moving one rewrites it as its place in the list. */
    @Query("UPDATE favorites SET addedAt = :order WHERE playlistId = :playlistId AND profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun setFavoriteOrder(playlistId: Long, profileId: Long, streamUrl: String, order: Long)

    /**
     * 0.9.5 lists favorites in their own order instead of playlist order: start that order from the
     * playlist order they were shown in until now (later additions go to the end).
     */
    @Query("UPDATE favorites SET addedAt = COALESCE((SELECT MIN(c.position) FROM channels c WHERE c.playlistId = favorites.playlistId AND c.streamUrl = favorites.streamUrl), addedAt)")
    suspend fun seedFavoriteOrder()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun removeFavorite(playlistId: Long, profileId: Long, streamUrl: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :playlistId AND profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun isFavorite(playlistId: Long, profileId: Long, streamUrl: String): Int
}

@Dao
interface EpgDao {
    @Query("SELECT DISTINCT streamUrl, name, epgId FROM channels WHERE playlistId = :playlistId")
    suspend fun channelRefs(playlistId: Long): List<EpgChannelRow>

    /** The programme airing now on every linked channel of a playlist. */
    @Query(
        """SELECT l.streamUrl AS streamUrl, p.start AS start, p.stop AS stop, p.title AS title, NULL AS description
           FROM epg_links l INNER JOIN programmes p ON p.playlistId = l.playlistId AND p.channelKey = l.epgKey
           WHERE l.playlistId = :playlistId AND p.start <= :now AND p.stop > :now""",
    )
    fun observeNow(playlistId: Long, now: Long): Flow<List<ChannelProgramme>>

    /** Current and upcoming programmes of one channel. */
    @Query(
        """SELECT l.streamUrl AS streamUrl, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description
           FROM epg_links l INNER JOIN programmes p ON p.playlistId = l.playlistId AND p.channelKey = l.epgKey
           WHERE l.playlistId = :playlistId AND l.streamUrl = :streamUrl AND p.stop > :now
           ORDER BY p.start LIMIT :limit""",
    )
    suspend fun upcoming(playlistId: Long, streamUrl: String, now: Long, limit: Int): List<ChannelProgramme>

    /** Every programme overlapping [from, to) on the playlist's linked channels. */
    @Query(
        """SELECT l.streamUrl AS streamUrl, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description
           FROM epg_links l INNER JOIN programmes p ON p.playlistId = l.playlistId AND p.channelKey = l.epgKey
           WHERE l.playlistId = :playlistId AND p.stop > :from AND p.start < :to
           ORDER BY p.start""",
    )
    fun observeWindow(playlistId: Long, from: Long, to: Long): Flow<List<ChannelProgramme>>

    /** Programmes of one channel overlapping [from, to), oldest first (for the archive screen). */
    @Query(
        """SELECT l.streamUrl AS streamUrl, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description
           FROM epg_links l INNER JOIN programmes p ON p.playlistId = l.playlistId AND p.channelKey = l.epgKey
           WHERE l.playlistId = :playlistId AND l.streamUrl = :streamUrl AND p.stop > :from AND p.start < :to
           ORDER BY p.start""",
    )
    suspend fun programmesFor(playlistId: Long, streamUrl: String, from: Long, to: Long): List<ChannelProgramme>

    /** Programmes whose title contains [query], on channels the user can see. */
    @Query(
        """SELECT l.streamUrl AS streamUrl, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description
           FROM epg_links l INNER JOIN programmes p ON p.playlistId = l.playlistId AND p.channelKey = l.epgKey
           WHERE l.playlistId = :playlistId AND p.searchTitle LIKE '%' || :query || '%' ESCAPE '\' AND p.stop > :from
           AND l.streamUrl IN (SELECT streamUrl FROM channels WHERE playlistId = :playlistId AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)))
           ORDER BY p.start LIMIT 80""",
    )
    fun observeProgrammeSearch(playlistId: Long, query: String, from: Long, unlocked: Boolean): Flow<List<ChannelProgramme>>

    @Query("DELETE FROM programmes WHERE playlistId = :playlistId")
    suspend fun deleteProgrammes(playlistId: Long)

    @Query("DELETE FROM epg_links WHERE playlistId = :playlistId")
    suspend fun deleteLinks(playlistId: Long)

    @Insert
    suspend fun insertProgrammes(programmes: List<ProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<EpgLinkEntity>)

    @Query("SELECT epgKey FROM epg_links WHERE playlistId = :playlistId AND streamUrl = :streamUrl")
    suspend fun link(playlistId: Long, streamUrl: String): String?

    @Query("DELETE FROM epg_links WHERE playlistId = :playlistId AND streamUrl = :streamUrl")
    suspend fun deleteLink(playlistId: Long, streamUrl: String)

    @Query("SELECT EXISTS(SELECT 1 FROM programmes WHERE playlistId = :playlistId AND channelKey = :epgKey)")
    suspend fun hasProgrammes(playlistId: Long, epgKey: String): Boolean

    @Query("SELECT * FROM epg_overrides WHERE playlistId = :playlistId")
    suspend fun overrides(playlistId: Long): List<EpgOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOverride(override: EpgOverrideEntity)

    @Query("DELETE FROM epg_overrides WHERE playlistId = :playlistId AND streamUrl = :streamUrl")
    suspend fun deleteOverride(playlistId: Long, streamUrl: String)

    @Query("SELECT * FROM epg_sources WHERE playlistId = :playlistId")
    suspend fun sources(playlistId: Long): List<EpgSourceEntity>

    @Query("DELETE FROM epg_sources WHERE playlistId = :playlistId")
    suspend fun deleteSources(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<EpgSourceEntity>)
}

@Dao
interface VodDao {
    @Query(
        """SELECT category AS name, COUNT(*) AS count FROM vod_items
           WHERE playlistId = :playlistId AND kind = :kind AND category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND hidden = 1)
           GROUP BY category ORDER BY MIN(position)""",
    )
    fun observeCategories(playlistId: Long, kind: String): Flow<List<GroupRow>>

    @Query("SELECT DISTINCT category FROM vod_items WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun allCategoryNames(playlistId: Long, kind: String): List<String>

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId AND kind = :kind AND category = :category ORDER BY position")
    fun observeCategory(playlistId: Long, kind: String, category: String): Flow<List<VodItemEntity>>

    @Query(
        """SELECT * FROM vod_items WHERE playlistId = :playlistId AND kind = :kind AND category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND hidden = 1) AND (:unlocked OR category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND locked = 1))
           ORDER BY added DESC, position LIMIT :limit""",
    )
    fun observeRecent(playlistId: Long, kind: String, limit: Int, unlocked: Boolean): Flow<List<VodItemEntity>>

    @Query(
        """SELECT * FROM vod_items WHERE playlistId = :playlistId AND kind = :kind
           AND searchName LIKE '%' || :query || '%' ESCAPE '\' AND category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND hidden = 1) AND (:unlocked OR category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND locked = 1)) ORDER BY position LIMIT 300""",
    )
    fun observeSearch(playlistId: Long, kind: String, query: String, unlocked: Boolean): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId AND kind = 'episode' AND seriesKey = :seriesKey ORDER BY season, episode, position")
    suspend fun episodes(playlistId: Long, seriesKey: String): List<VodItemEntity>

    @Query("SELECT * FROM vod_items WHERE id = :id")
    suspend fun get(id: Long): VodItemEntity?

    @Query("DELETE FROM vod_items WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun deleteKind(playlistId: Long, kind: String)

    @Insert
    suspend fun insertAll(items: List<VodItemEntity>)

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND profileId = :profileId AND mediaKey = :mediaKey")
    suspend fun progress(playlistId: Long, profileId: Long, mediaKey: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND profileId = :profileId")
    fun observeProgress(playlistId: Long, profileId: Long): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE playlistId = :playlistId AND profileId = :profileId AND mediaKey = :mediaKey")
    suspend fun clearProgress(playlistId: Long, profileId: Long, mediaKey: String)
}

@Dao
interface FlagsDao {
    @Query("SELECT * FROM group_flags WHERE playlistId = :playlistId")
    fun observeGroupFlags(playlistId: Long): Flow<List<GroupFlagEntity>>

    @Query("SELECT * FROM group_flags WHERE playlistId = :playlistId AND scope = :scope AND name = :name")
    suspend fun groupFlag(playlistId: Long, scope: String, name: String): GroupFlagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGroupFlag(flag: GroupFlagEntity)

    @Query("DELETE FROM group_flags WHERE playlistId = :playlistId AND scope = :scope AND name = :name")
    suspend fun deleteGroupFlag(playlistId: Long, scope: String, name: String)

    @Query("SELECT * FROM hidden_channels WHERE playlistId = :playlistId ORDER BY name")
    fun observeHiddenChannels(playlistId: Long): Flow<List<HiddenChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideChannel(channel: HiddenChannelEntity)

    @Query("DELETE FROM hidden_channels WHERE playlistId = :playlistId AND streamUrl = :streamUrl")
    suspend fun unhideChannel(playlistId: Long, streamUrl: String)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY start DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE status IN ('scheduled', 'recording')")
    suspend fun active(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE streamUrl = :streamUrl AND start < :stop AND stop > :start AND status IN ('scheduled', 'recording') LIMIT 1")
    suspend fun overlapping(streamUrl: String, start: Long, stop: Long): RecordingEntity?

    /** Any recording of that time on the channel, whatever its state (cancelled ones included). */
    @Query("SELECT * FROM recordings WHERE streamUrl = :streamUrl AND start < :stop AND stop > :start LIMIT 1")
    suspend fun anyOverlapping(streamUrl: String, start: Long, stop: Long): RecordingEntity?

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY start")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE playlistId = :playlistId")
    fun observeForPlaylist(playlistId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE playlistId = :playlistId AND streamUrl = :streamUrl AND start = :start")
    suspend fun find(playlistId: Long, streamUrl: String, start: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders WHERE start < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ListsDao {
    @Query(
        """SELECT l.id AS id, l.name AS name, (SELECT COUNT(*) FROM favorite_list_items i WHERE i.listId = l.id) AS count
           FROM favorite_lists l WHERE l.playlistId = :playlistId AND l.profileId = :profileId ORDER BY l.position, l.id""",
    )
    fun observeLists(playlistId: Long, profileId: Long): Flow<List<FavoriteListRow>>

    @Query("SELECT * FROM favorite_lists WHERE playlistId = :playlistId AND profileId = :profileId ORDER BY position, id")
    suspend fun lists(playlistId: Long, profileId: Long): List<FavoriteListEntity>

    @Query("SELECT * FROM favorite_lists WHERE id = :id")
    suspend fun list(id: Long): FavoriteListEntity?

    @Insert
    suspend fun insertList(list: FavoriteListEntity): Long

    @Query("UPDATE favorite_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM favorite_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM favorite_lists WHERE profileId = :profileId")
    suspend fun deleteProfileLists(profileId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM favorite_lists WHERE playlistId = :playlistId AND profileId = :profileId")
    suspend fun nextPosition(playlistId: Long, profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: FavoriteListItemEntity)

    @Query("DELETE FROM favorite_list_items WHERE listId = :listId AND streamUrl = :streamUrl")
    suspend fun removeItem(listId: Long, streamUrl: String)

    @Query("SELECT listId FROM favorite_list_items WHERE streamUrl = :streamUrl AND listId IN (SELECT id FROM favorite_lists WHERE playlistId = :playlistId AND profileId = :profileId)")
    suspend fun listsContaining(playlistId: Long, profileId: Long, streamUrl: String): List<Long>

    @Query("SELECT streamUrl FROM favorite_list_items WHERE listId = :listId ORDER BY addedAt")
    suspend fun itemUrls(listId: Long): List<String>

    @Query("UPDATE favorite_list_items SET addedAt = :order WHERE listId = :listId AND streamUrl = :streamUrl")
    suspend fun setItemOrder(listId: Long, streamUrl: String, order: Long)

    @Query("UPDATE favorite_list_items SET addedAt = COALESCE((SELECT MIN(c.position) FROM channels c JOIN favorite_lists l ON l.playlistId = c.playlistId WHERE l.id = favorite_list_items.listId AND c.streamUrl = favorite_list_items.streamUrl), addedAt)")
    suspend fun seedItemOrder()

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM favorite_list_items WHERE listId = :listId) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY (SELECT i.addedAt FROM favorite_list_items i WHERE i.listId = :listId AND i.streamUrl = channels.streamUrl), position""")
    fun observeListChannels(playlistId: Long, listId: Long, unlocked: Boolean): Flow<List<ChannelEntity>>

    @Query("""SELECT * FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM favorite_list_items WHERE listId = :listId) AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1)) ORDER BY (SELECT i.addedAt FROM favorite_list_items i WHERE i.listId = :listId AND i.streamUrl = channels.streamUrl), position""")
    suspend fun listChannels(playlistId: Long, listId: Long, unlocked: Boolean): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(recent: RecentChannelEntity)

    @Query("DELETE FROM recent_channels WHERE playlistId = :playlistId AND profileId = :profileId AND streamUrl NOT IN (SELECT streamUrl FROM recent_channels WHERE playlistId = :playlistId AND profileId = :profileId ORDER BY watchedAt DESC LIMIT :keep)")
    suspend fun trimRecent(playlistId: Long, profileId: Long, keep: Int)

    @Query("DELETE FROM recent_channels WHERE playlistId = :playlistId AND profileId = :profileId")
    suspend fun clearRecent(playlistId: Long, profileId: Long)

    /** Forgets recently watched channels that are now in a locked or hidden category. */
    @Query(
        """DELETE FROM recent_channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM channels WHERE playlistId = :playlistId
           AND groupTitle IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND (locked = 1 OR hidden = 1)))""",
    )
    suspend fun purgePrivateRecent(playlistId: Long)

    /** Recently watched channels, newest first; one row per stream even if it is listed in several groups. */
    @Query(
        """SELECT * FROM channels WHERE id IN (SELECT MIN(id) FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM recent_channels WHERE playlistId = :playlistId AND profileId = :profileId) GROUP BY streamUrl)
           AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1))
           ORDER BY (SELECT r.watchedAt FROM recent_channels r WHERE r.playlistId = channels.playlistId AND r.profileId = :profileId AND r.streamUrl = channels.streamUrl) DESC""",
    )
    fun observeRecent(playlistId: Long, profileId: Long, unlocked: Boolean): Flow<List<ChannelEntity>>

    @Query(
        """SELECT * FROM channels WHERE id IN (SELECT MIN(id) FROM channels WHERE playlistId = :playlistId AND streamUrl IN (SELECT streamUrl FROM recent_channels WHERE playlistId = :playlistId AND profileId = :profileId) GROUP BY streamUrl)
           AND streamUrl NOT IN (SELECT streamUrl FROM hidden_channels WHERE playlistId = :playlistId) AND groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND hidden = 1) AND (:unlocked OR groupTitle NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = 'live' AND locked = 1))
           ORDER BY (SELECT r.watchedAt FROM recent_channels r WHERE r.playlistId = channels.playlistId AND r.profileId = :profileId AND r.streamUrl = channels.streamUrl) DESC""",
    )
    suspend fun recent(playlistId: Long, profileId: Long, unlocked: Boolean): List<ChannelEntity>
}

@Dao
interface LibraryDao {
    @Query("SELECT kind || '|' || providerId FROM watchlist WHERE playlistId = :playlistId AND profileId = :profileId")
    fun observeWatchlistKeys(playlistId: Long, profileId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE playlistId = :playlistId AND profileId = :profileId AND kind = :kind AND providerId = :providerId")
    suspend fun removeFromWatchlist(playlistId: Long, profileId: Long, kind: String, providerId: String)

    @Query("SELECT * FROM watchlist WHERE playlistId = :playlistId AND profileId = :profileId")
    suspend fun watchlist(playlistId: Long, profileId: Long): List<WatchlistEntity>

    /** Watchlist films or series, newest first, without hidden or (unless [unlocked]) locked categories. */
    @Query(
        """SELECT v.* FROM vod_items v INNER JOIN watchlist w ON w.playlistId = v.playlistId AND w.profileId = :profileId AND w.kind = v.kind AND w.providerId = v.providerId
           WHERE v.playlistId = :playlistId AND v.kind = :kind
           AND v.category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND hidden = 1)
           AND (:unlocked OR v.category NOT IN (SELECT name FROM group_flags WHERE playlistId = :playlistId AND scope = :kind AND locked = 1))
           ORDER BY w.addedAt DESC""",
    )
    fun observeWatchlist(playlistId: Long, profileId: Long, kind: String, unlocked: Boolean): Flow<List<VodItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContinue(entry: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE playlistId = :playlistId AND profileId = :profileId AND mediaKey = :mediaKey")
    suspend fun removeContinue(playlistId: Long, profileId: Long, mediaKey: String)

    @Query("SELECT * FROM continue_watching WHERE playlistId = :playlistId")
    suspend fun continueEntries(playlistId: Long): List<ContinueWatchingEntity>

    @Query("SELECT * FROM vod_items WHERE playlistId = :playlistId AND kind = :kind AND providerId = :providerId LIMIT 1")
    suspend fun vodItem(playlistId: Long, kind: String, providerId: String): VodItemEntity?

    @Query("SELECT * FROM vod_items WHERE id = :id")
    suspend fun vodItemById(id: Long): VodItemEntity?

    @Query("SELECT * FROM continue_watching WHERE playlistId = :playlistId AND profileId = :profileId ORDER BY updatedAt DESC LIMIT 15")
    fun observeContinue(playlistId: Long, profileId: Long): Flow<List<ContinueWatchingEntity>>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY position, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY position, id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecordingRuleDao {
    @Query("SELECT * FROM recording_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingRuleEntity>>

    @Query("SELECT * FROM recording_rules")
    suspend fun all(): List<RecordingRuleEntity>

    @Query("SELECT * FROM recording_rules WHERE playlistId = :playlistId AND streamUrl = :streamUrl AND title = :title LIMIT 1")
    suspend fun find(playlistId: Long, streamUrl: String, title: String): RecordingRuleEntity?

    @Insert
    suspend fun insert(rule: RecordingRuleEntity): Long

    @Query("DELETE FROM recording_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Fills the search columns of rows stored before they existed (database version 10). */
@Dao
interface SearchIndexDao {
    @Query("SELECT id, name AS text FROM channels WHERE searchName = '' AND name != '' LIMIT 2000")
    suspend fun channelsToFill(): List<IdText>

    @Query("UPDATE channels SET searchName = :value WHERE id = :id")
    suspend fun setChannel(id: Long, value: String)

    @Query("SELECT id, name AS text FROM vod_items WHERE searchName = '' AND name != '' LIMIT 2000")
    suspend fun vodToFill(): List<IdText>

    @Query("UPDATE vod_items SET searchName = :value WHERE id = :id")
    suspend fun setVod(id: Long, value: String)

    @Query("SELECT id, title AS text FROM programmes WHERE searchTitle = '' AND title != '' LIMIT 2000")
    suspend fun programmesToFill(): List<IdText>

    @Query("UPDATE programmes SET searchTitle = :value WHERE id = :id")
    suspend fun setProgramme(id: Long, value: String)
}

data class IdText(val id: Long, val text: String)

/** Moves everything stored per channel to a channel's new stream address (after account details changed). */
@Dao
interface RemapDao {
    @Query("UPDATE OR IGNORE favorites SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun favorites(playlistId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE recent_channels SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun recent(playlistId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE hidden_channels SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun hidden(playlistId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE reminders SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun reminders(playlistId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE favorite_list_items SET streamUrl = :to WHERE streamUrl = :from AND listId IN (SELECT id FROM favorite_lists WHERE playlistId = :playlistId)")
    suspend fun listItems(playlistId: Long, from: String, to: String)

    @Query("UPDATE recording_rules SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun recordingRules(playlistId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE epg_overrides SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from")
    suspend fun epgOverrides(playlistId: Long, from: String, to: String)

    @Query("UPDATE recordings SET streamUrl = :to WHERE playlistId = :playlistId AND streamUrl = :from AND status = 'scheduled'")
    suspend fun scheduledRecordings(playlistId: Long, from: String, to: String)

    suspend fun all(playlistId: Long, from: String, to: String) {
        favorites(playlistId, from, to)
        recent(playlistId, from, to)
        hidden(playlistId, from, to)
        reminders(playlistId, from, to)
        listItems(playlistId, from, to)
        recordingRules(playlistId, from, to)
        scheduledRecordings(playlistId, from, to)
        epgOverrides(playlistId, from, to)
    }
}

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        ProgrammeEntity::class,
        EpgLinkEntity::class,
        VodItemEntity::class,
        WatchProgressEntity::class,
        ReminderEntity::class,
        GroupFlagEntity::class,
        HiddenChannelEntity::class,
        RecordingEntity::class,
        FavoriteListEntity::class,
        FavoriteListItemEntity::class,
        RecentChannelEntity::class,
        WatchlistEntity::class,
        ContinueWatchingEntity::class,
        ProfileEntity::class,
        RecordingRuleEntity::class,
        EpgOverrideEntity::class,
        EpgSourceEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun channels(): ChannelDao
    abstract fun epg(): EpgDao
    abstract fun vod(): VodDao
    abstract fun reminders(): ReminderDao
    abstract fun flags(): FlagsDao
    abstract fun recordings(): RecordingDao
    abstract fun lists(): ListsDao
    abstract fun library(): LibraryDao
    abstract fun profiles(): ProfileDao
    abstract fun recordingRules(): RecordingRuleDao
    abstract fun remap(): RemapDao
    abstract fun searchIndex(): SearchIndexDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db")
                .addMigrations(*Migrations.ALL)
                .addCallback(object : Callback() {
                    // A fresh install starts with the main profile, which everything is stored under by default.
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(DEFAULT_PROFILE_SQL)
                    }
                })
                .build()

        /** The main profile (id 1). An empty name is shown as "Main profile" in the app language. */
        const val DEFAULT_PROFILE_SQL = "INSERT OR IGNORE INTO profiles (id, name, color, kids, position) VALUES (1, '', -11557889, 0, 0)"
    }
}
