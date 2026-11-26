package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.tx

import androidx.room.withTransaction
import io.github.ta3hrj.elm.core.Episode
import io.github.ta3hrj.elm.core.MovieInfo
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.PlaylistLoader
import io.github.ta3hrj.elm.core.PlaylistSource
import io.github.ta3hrj.elm.core.SeriesInfo
import io.github.ta3hrj.elm.core.VodItem
import io.github.ta3hrj.elm.core.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Films and series of Xtream accounts (M3U playlists have no separate VOD catalogue). */
@OptIn(ExperimentalCoroutinesApi::class)
class VodRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val loader: PlaylistLoader,
    private val scope: CoroutineScope,
    private val profiles: ProfileManager,
) {
    private val vod = db.vod()

    private val _updating = MutableStateFlow<Set<Long>>(emptySet())
    val updating: StateFlow<Set<Long>> = _updating.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val seriesCache = HashMap<String, SeriesInfo>()

    /** Names of locked categories of one kind. */
    private fun locked(playlistId: Long, kind: String): Flow<Set<String>> =
        db.flags().observeGroupFlags(playlistId).map { list -> list.filter { it.scope == kind && it.locked }.mapTo(HashSet()) { it.name } }

    /** Drops what a kids profile may not see: locked categories and categories it was not given. */
    private fun <T> Flow<List<T>>.restricted(playlistId: Long, kind: String, category: (T) -> String): Flow<List<T>> =
        combine(this, profiles.filter, locked(playlistId, kind)) { list, filter, locked ->
            if (!filter.kids) list else list.filter { val c = category(it); filter.allows(kind, c) && c !in locked }
        }

    private fun Flow<List<VodItemEntity>>.restrictedItems(playlistId: Long): Flow<List<VodItemEntity>> =
        combine(this, profiles.filter, db.flags().observeGroupFlags(playlistId)) { list, filter, flags ->
            if (!filter.kids) return@combine list
            val locked = flags.filter { it.locked }.mapTo(HashSet()) { "${it.scope}|${it.name}" }
            list.filter { filter.allows(it.kind, it.category) && "${it.kind}|${it.category}" !in locked }
        }

    fun observeCategories(playlistId: Long, kind: String): Flow<List<GroupRow>> =
        vod.observeCategories(playlistId, kind).restricted(playlistId, kind) { it.name }

    fun observeCategory(playlistId: Long, kind: String, category: String) =
        vod.observeCategory(playlistId, kind, category).restrictedItems(playlistId)

    fun observeRecent(playlistId: Long, kind: String, unlocked: Boolean) =
        vod.observeRecent(playlistId, kind, 60, unlocked).restrictedItems(playlistId)

    fun observeSearch(playlistId: Long, kind: String, query: String, unlocked: Boolean) =
        vod.observeSearch(playlistId, kind, searchPattern(query), unlocked).restrictedItems(playlistId)

    fun observeProgress(playlistId: Long) = profiles.active.flatMapLatest { vod.observeProgress(playlistId, it.id) }

    suspend fun item(id: Long): VodItemEntity? = vod.get(id)

    suspend fun progress(playlistId: Long, mediaKey: String) = vod.progress(playlistId, profiles.activeId, mediaKey)

    suspend fun saveProgress(playlistId: Long, mediaKey: String, positionMs: Long, durationMs: Long) {
        val profileId = profiles.activeId
        // Treat the last few minutes (credits) as "finished".
        if (durationMs > 0 && durationMs - positionMs < FINISHED_MARGIN_MS) {
            vod.clearProgress(playlistId, profileId, mediaKey)
        } else if (positionMs > MIN_RESUME_MS) {
            vod.saveProgress(WatchProgressEntity(profileId, playlistId, mediaKey, positionMs, durationMs, System.currentTimeMillis()))
        }
    }

    // ---- Watchlist and "continue watching" ---------------------------------------------------

    private val library = db.library()

    fun observeWatchlistKeys(playlistId: Long) = profiles.active.flatMapLatest { library.observeWatchlistKeys(playlistId, it.id) }

    fun observeWatchlist(playlistId: Long, kind: String, unlocked: Boolean) =
        profiles.active.flatMapLatest { library.observeWatchlist(playlistId, it.id, kind, unlocked) }.restrictedItems(playlistId)

    /** Adds the film or series to the watchlist, or removes it if it is there. Returns true if added. */
    suspend fun toggleWatchlist(item: VodItemEntity): Boolean {
        val profileId = profiles.activeId
        val inList = library.watchlist(item.playlistId, profileId).any { it.kind == item.kind && it.providerId == item.providerId }
        if (inList) {
            library.removeFromWatchlist(item.playlistId, profileId, item.kind, item.providerId)
        } else {
            library.addToWatchlist(WatchlistEntity(profileId, item.playlistId, item.kind, item.providerId))
        }
        return !inList
    }

    fun observeContinue(playlistId: Long) = profiles.active.flatMapLatest { library.observeContinue(playlistId, it.id) }

    /** Keeps the home screen's "continue watching" row current; finished items leave it. */
    suspend fun updateContinue(entry: ContinueWatchingEntity) {
        val finished = entry.durationMs > 0 && entry.durationMs - entry.positionMs < FINISHED_MARGIN_MS
        val own = entry.copy(profileId = profiles.activeId)
        when {
            finished -> library.removeContinue(own.playlistId, own.profileId, own.mediaKey)
            own.positionMs > MIN_RESUME_MS -> library.saveContinue(own)
        }
    }

    suspend fun removeContinue(playlistId: Long, mediaKey: String) = library.removeContinue(playlistId, profiles.activeId, mediaKey)

    /**
     * Removes "continue watching" entries whose film or series is in a locked or hidden category, so
     * their titles never show on the home screen. Returns the removed media keys.
     */
    suspend fun purgePrivateContinue(playlistId: Long): List<String> {
        val flags = db.flags().observeGroupFlags(playlistId).first().filter { it.locked || it.hidden }
            .mapTo(HashSet()) { "${it.scope}|${it.name}" }
        if (flags.isEmpty()) return emptyList()
        val removed = ArrayList<String>()
        for (entry in library.continueEntries(playlistId)) {
            val item = when {
                entry.seriesItemId > 0 -> library.vodItemById(entry.seriesItemId)
                entry.mediaKey.startsWith("movie:") -> library.vodItem(playlistId, VodKinds.MOVIE, entry.mediaKey.removePrefix("movie:"))
                else -> null
            } ?: continue
            if ("${item.kind}|${item.category}" in flags) {
                library.removeContinue(playlistId, entry.profileId, entry.mediaKey)
                removed += entry.mediaKey
            }
        }
        return removed
    }

    /** M3U lists carry no film details; only Xtream has an info endpoint. */
    suspend fun movieInfo(playlist: PlaylistEntity, item: VodItemEntity): MovieInfo? =
        if (item.streamUrl != null) null else client(playlist).movieInfo(item.providerId)

    suspend fun seriesInfo(playlist: PlaylistEntity, item: VodItemEntity): SeriesInfo {
        val key = "${playlist.id}:${item.providerId}"
        seriesCache[key]?.let { return it }
        val info = if (item.seriesKey != null) m3uSeriesInfo(playlist.id, item) else client(playlist).seriesInfo(item.providerId)
        return info.also { seriesCache[key] = it }
    }

    fun movieUrl(playlist: PlaylistEntity, item: VodItemEntity) =
        item.streamUrl ?: client(playlist).movieUrl(item.providerId, item.containerExtension)

    /** M3U episode ids are their stream addresses. */
    fun episodeUrl(playlist: PlaylistEntity, episodeId: String, extension: String?) =
        if (playlist.type == PlaylistType.XTREAM) client(playlist).episodeUrl(episodeId, extension) else episodeId

    private suspend fun m3uSeriesInfo(playlistId: Long, item: VodItemEntity): SeriesInfo {
        val episodes = vod.episodes(playlistId, item.seriesKey!!).map {
            Episode(
                id = it.streamUrl ?: it.providerId,
                season = it.season,
                number = it.episode,
                title = it.name,
                containerExtension = it.containerExtension ?: "",
                plot = null,
                duration = null,
                image = it.poster,
            )
        }
        return SeriesInfo(
            plot = null, cast = null, genre = null, releaseDate = null, rating = null,
            poster = item.poster, backdrop = null,
            seasons = episodes.groupBy { it.season }.toSortedMap(),
        )
    }

    fun refreshInBackground(playlistId: Long, quiet: Boolean = false) {
        if (playlistId in _updating.value) return
        scope.launch {
            _updating.update { it + playlistId }
            try {
                val message = refresh(playlistId)
                if (!quiet && message != null) _events.emit(message)
            } catch (e: Exception) {
                val text = (e as? PlaylistException)?.message ?: e.message ?: e.javaClass.simpleName
                db.playlists().get(playlistId)?.let { db.playlists().update(it.copy(vodError = text)) }
                if (!quiet) _events.emit(tx("Film/dizi listesi alınamadı: $text", "Could not load films/series: $text"))
            } finally {
                _updating.update { it - playlistId }
            }
        }
    }

    fun refreshIfStale(playlist: PlaylistEntity, maxAgeMs: Long) {
        if (playlist.type == PlaylistType.XTREAM && System.currentTimeMillis() - playlist.vodUpdatedAt > maxAgeMs) {
            refreshInBackground(playlist.id, quiet = true)
        }
    }

    private suspend fun refresh(playlistId: Long): String? {
        val playlist = db.playlists().get(playlistId) ?: return null
        if (playlist.type != PlaylistType.XTREAM) return null
        val client = client(playlist)
        val movies = client.movies()
        val series = client.series()
        db.withTransaction {
            vod.deleteKind(playlistId, VodKinds.MOVIE)
            vod.deleteKind(playlistId, VodKinds.SERIES)
            movies.toEntities(playlistId, VodKinds.MOVIE).chunked(1000).forEach { vod.insertAll(it) }
            series.toEntities(playlistId, VodKinds.SERIES).chunked(1000).forEach { vod.insertAll(it) }
            db.playlists().get(playlistId)?.let {
                db.playlists().update(
                    it.copy(
                        vodUpdatedAt = System.currentTimeMillis(),
                        vodError = null,
                        movieCount = movies.size,
                        seriesCount = series.size,
                    ),
                )
            }
        }
        seriesCache.keys.removeAll { it.startsWith("$playlistId:") }
        return tx("Film ve diziler güncellendi: ${movies.size} film, ${series.size} dizi.", "Films and series updated: ${movies.size} films, ${series.size} series.")
    }

    private fun client(playlist: PlaylistEntity): XtreamClient {
        val source = playlist.toSource() as? PlaylistSource.Xtream
            ?: throw PlaylistException(tx("Film ve diziler yalnızca Xtream Codes listelerinde var.", "Films and series are only available in Xtream Codes playlists."))
        return XtreamClient(http, source, loader.userAgentFor(source))
    }

    private fun List<VodItem>.toEntities(playlistId: Long, kind: String) = mapIndexed { index, item ->
        VodItemEntity(
            playlistId = playlistId,
            kind = kind,
            providerId = item.providerId,
            position = index,
            name = item.name,
            category = item.category,
            poster = item.poster,
            rating = item.rating,
            year = item.year,
            containerExtension = item.containerExtension,
            added = item.added,
        )
    }

    companion object {
        const val STALE_AFTER_MS = 24 * 3_600_000L
        private const val MIN_RESUME_MS = 60_000L
        private const val FINISHED_MARGIN_MS = 4 * 60_000L

        fun movieKey(providerId: String) = "movie:$providerId"
        fun episodeKey(episodeId: String) = "episode:$episodeId"
    }
}
