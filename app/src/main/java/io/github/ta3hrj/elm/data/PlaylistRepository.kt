package io.github.ta3hrj.elm.data

import androidx.room.withTransaction
import io.github.ta3hrj.elm.core.AdultContent
import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.M3uVod
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.PlaylistLoader
import io.github.ta3hrj.elm.core.PlaylistSource
import io.github.ta3hrj.elm.core.SearchText
import io.github.ta3hrj.elm.core.XtreamClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Which channels a list shows; also passed to the player so channel up/down follows the same list. */
sealed interface ChannelQuery {
    data class Group(val name: String) : ChannelQuery
    data object Favorites : ChannelQuery
    data class Search(val text: String) : ChannelQuery
    /** Recently watched channels, newest first. */
    data object Recent : ChannelQuery
    /** One of the user's own favorite lists. */
    data class FavoriteList(val id: Long, val name: String) : ChannelQuery
    /** Channels with catch-up (their past programmes can be watched); choosing one opens its archive. */
    data object Archive : ChannelQuery

    /** Favorites and own lists are in the user's own order, not the channel order from Settings. */
    val hasOwnOrder: Boolean get() = this == Favorites || this is FavoriteList
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepository(
    private val db: AppDatabase,
    private val loader: PlaylistLoader,
    private val settings: AppSettings,
    private val profiles: ProfileManager,
) {
    private val playlists = db.playlists()
    private val channels = db.channels()

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlists.observeAll()

    suspend fun playlist(id: Long): PlaylistEntity? = playlists.get(id)

    fun observeGroups(playlistId: Long): Flow<List<GroupRow>> =
        combine(channels.observeGroups(playlistId), settings.groupOrder, profiles.filter, lockedLive(playlistId)) { all, order, filter, locked ->
            // A kids profile does not see locked categories at all, nor categories it was not given.
            val rows = if (filter.kids) all.filter { filter.allows(FlagScopes.LIVE, it.name) && it.name !in locked } else all
            if (order == GroupOrder.NAME) {
                val collator = nameCollator()
                rows.sortedWith { a, b -> collator.compare(a.name, b.name) }
            } else {
                rows
            }
        }

    /** Names of locked live categories. */
    private fun lockedLive(playlistId: Long): Flow<Set<String>> =
        flags.observeGroupFlags(playlistId).map { list -> list.filter { it.scope == FlagScopes.LIVE && it.locked }.mapTo(HashSet()) { it.name } }

    /** Drops channels a kids profile may not see. */
    private fun Flow<List<ChannelEntity>>.restricted(playlistId: Long): Flow<List<ChannelEntity>> =
        combine(this, profiles.filter, lockedLive(playlistId)) { list, filter, locked ->
            if (!filter.kids) list else list.filter { filter.allows(FlagScopes.LIVE, it.groupTitle) && it.groupTitle !in locked }
        }

    private suspend fun restrict(playlistId: Long, list: List<ChannelEntity>): List<ChannelEntity> {
        // Read from the active profile itself: right after a switch the derived flow may still be catching up.
        val filter = profiles.active.value.filter
        if (!filter.kids) return list
        val locked = lockedLive(playlistId).first()
        return list.filter { filter.allows(FlagScopes.LIVE, it.groupTitle) && it.groupTitle !in locked }
    }

    /** True if the active profile may watch this channel (kids profiles: allowed and not locked). */
    suspend fun allowed(channel: ChannelEntity): Boolean = restrict(channel.playlistId, listOf(channel)).isNotEmpty()

    // Lists come from the database in playlist order and are sorted here, so the home list,
    // the guide and channel up/down in the player all follow the order chosen in Settings.
    fun observeChannels(playlistId: Long, query: ChannelQuery, unlocked: Boolean): Flow<List<ChannelEntity>> {
        val source = when (query) {
            is ChannelQuery.Group -> channels.observeGroup(playlistId, query.name)
            ChannelQuery.Favorites -> profiles.active.flatMapLatest { channels.observeFavorites(playlistId, it.id, unlocked) }
            is ChannelQuery.Search -> channels.observeSearch(playlistId, searchPattern(query.text), unlocked)
            // Recently watched keeps its own order (newest first).
            ChannelQuery.Recent -> return profiles.active.flatMapLatest { lists.observeRecent(playlistId, it.id, unlocked) }.restricted(playlistId)
            is ChannelQuery.FavoriteList -> lists.observeListChannels(playlistId, query.id, unlocked)
            ChannelQuery.Archive -> channels.observeArchive(playlistId, unlocked)
        }
        // Favorites and own lists keep the order the user gave them; everything else follows Settings.
        if (query.hasOwnOrder) return source.restricted(playlistId)
        return source.restricted(playlistId).combine(settings.channelOrder) { list, order -> sortChannels(list, order) }
    }

    suspend fun channels(playlistId: Long, query: ChannelQuery, unlocked: Boolean): List<ChannelEntity> {
        val profileId = profiles.activeId
        val list = when (query) {
            is ChannelQuery.Group -> sortChannels(channels.group(playlistId, query.name), settings.channelOrder.value)
            ChannelQuery.Favorites -> channels.favorites(playlistId, profileId, unlocked)
            is ChannelQuery.Search -> sortChannels(channels.search(playlistId, searchPattern(query.text), unlocked), settings.channelOrder.value)
            ChannelQuery.Recent -> lists.recent(playlistId, profileId, unlocked)
            is ChannelQuery.FavoriteList -> lists.listChannels(playlistId, query.id, unlocked)
            ChannelQuery.Archive -> sortChannels(channels.archive(playlistId, unlocked), settings.channelOrder.value)
        }
        return restrict(playlistId, list)
    }

    /** Moves a channel [step] places within the favorites or an own list. Returns false at either end. */
    suspend fun move(query: ChannelQuery, channel: ChannelEntity, step: Int): Boolean {
        val profileId = profiles.activeId
        // Locked channels are included so their places are kept while they are out of sight.
        val list = when (query) {
            ChannelQuery.Favorites -> channels.favorites(channel.playlistId, profileId, unlocked = true)
            is ChannelQuery.FavoriteList -> lists.listChannels(channel.playlistId, query.id, unlocked = true)
            else -> return false
        }.distinctBy { it.streamUrl }.toMutableList()
        val from = list.indexOfFirst { it.streamUrl == channel.streamUrl }
        val to = from + step
        if (from < 0 || to !in list.indices) return false
        list.add(to, list.removeAt(from))
        db.withTransaction {
            list.forEachIndexed { i, c ->
                when (query) {
                    ChannelQuery.Favorites -> channels.setFavoriteOrder(c.playlistId, profileId, c.streamUrl, i.toLong())
                    is ChannelQuery.FavoriteList -> lists.setItemOrder(query.id, c.streamUrl, i.toLong())
                    else -> Unit
                }
            }
        }
        return true
    }

    /**
     * Fills the search columns of rows stored before database version 10, in batches, so searching
     * ignores case and accents for them too. Runs once at start; new rows get the column when stored.
     */
    suspend fun fillSearchIndex() {
        val dao = db.searchIndex()
        suspend fun fill(load: suspend () -> List<IdText>, store: suspend (Long, String) -> Unit) {
            while (true) {
                val rows = load()
                if (rows.isEmpty()) return
                db.withTransaction { rows.forEach { store(it.id, SearchText.normalize(it.text).ifEmpty { " " }) } }
            }
        }
        fill(dao::channelsToFill, dao::setChannel)
        fill(dao::vodToFill, dao::setVod)
        fill(dao::programmesToFill, dao::setProgramme)
    }

    /** One-time start of the own order (see [ChannelDao.seedFavoriteOrder]). */
    suspend fun seedOwnOrder() {
        db.withTransaction {
            channels.seedFavoriteOrder()
            lists.seedItemOrder()
        }
    }

    // ---- Automatic lock for adult categories ---------------------------------------------------

    /**
     * Locks categories that look adult (live, films, series). Without a PIN a lock protects nothing, so
     * they are hidden instead and come back locked once a PIN is set. Each category is handled once, so a
     * category the user unlocks by hand stays unlocked after later refreshes. Turning the feature off
     * removes what it added.
     */
    suspend fun applyAdultLock(playlistId: Long, hasPin: Boolean) {
        val handled = settings.adultHandled(playlistId).toMutableSet()
        val hidden = settings.adultHidden(playlistId).toMutableSet()
        if (!settings.adultLock.value) {
            for (key in handled) {
                val (scope, name) = key.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                setGroupFlag(playlistId, scope, name, locked = false, hidden = false)
            }
            settings.setAdultState(playlistId, emptySet(), emptySet())
            return
        }
        val vod = db.vod()
        val names = channels.allGroupNames(playlistId).map { FlagScopes.LIVE to it } +
            vod.allCategoryNames(playlistId, VodKinds.MOVIE).map { FlagScopes.MOVIE to it } +
            vod.allCategoryNames(playlistId, VodKinds.SERIES).map { FlagScopes.SERIES to it }
        for ((scope, name) in names) {
            if (!AdultContent.isAdultCategory(name)) continue
            val key = "$scope|$name"
            if (key !in handled) {
                handled += key
                setGroupFlag(playlistId, scope, name, locked = true, hidden = if (hasPin) null else true)
                if (!hasPin) hidden += key
            } else if (hasPin && key in hidden) {
                setGroupFlag(playlistId, scope, name, locked = true, hidden = false)
                hidden -= key
            } else if (!hasPin && key !in hidden && flags.groupFlag(playlistId, scope, name)?.locked == true) {
                // The PIN was removed: a lock no longer protects anything.
                setGroupFlag(playlistId, scope, name, hidden = true)
                hidden += key
            }
        }
        settings.setAdultState(playlistId, handled, hidden)
    }

    // ---- Recently watched and own favorite lists ----------------------------------------------

    private val lists = db.lists()

    /** Remembers a channel as watched; only the last [RECENT_LIMIT] are kept. */
    suspend fun markWatched(channel: ChannelEntity) {
        // Channels of locked or hidden categories leave no trace in the history.
        val flag = flags.groupFlag(channel.playlistId, FlagScopes.LIVE, channel.groupTitle)
        if (flag != null && (flag.locked || flag.hidden)) return
        val profileId = profiles.activeId
        lists.markWatched(RecentChannelEntity(profileId, channel.playlistId, channel.streamUrl, System.currentTimeMillis()))
        lists.trimRecent(channel.playlistId, profileId, RECENT_LIMIT)
    }

    suspend fun clearRecent(playlistId: Long) = lists.clearRecent(playlistId, profiles.activeId)

    fun observeFavoriteLists(playlistId: Long): Flow<List<FavoriteListRow>> =
        profiles.active.flatMapLatest { lists.observeLists(playlistId, it.id) }

    suspend fun createList(playlistId: Long, name: String): Long {
        val profileId = profiles.activeId
        return lists.insertList(
            FavoriteListEntity(profileId = profileId, playlistId = playlistId, name = name.trim(), position = lists.nextPosition(playlistId, profileId)),
        )
    }

    suspend fun renameList(id: Long, name: String) = lists.renameList(id, name.trim())

    suspend fun deleteList(id: Long) = lists.deleteList(id)

    suspend fun listsContaining(channel: ChannelEntity): List<Long> = lists.listsContaining(channel.playlistId, profiles.activeId, channel.streamUrl)

    /** Adds the channel to the list, or removes it if it is already there. Returns true if added. */
    suspend fun toggleInList(listId: Long, channel: ChannelEntity): Boolean {
        val inList = listId in listsContaining(channel)
        if (inList) lists.removeItem(listId, channel.streamUrl) else lists.addItem(FavoriteListItemEntity(listId, channel.streamUrl))
        return !inList
    }

    fun observeAllVisible(playlistId: Long, unlocked: Boolean): Flow<List<ChannelEntity>> =
        combine(channels.observeAllVisible(playlistId, unlocked).restricted(playlistId), settings.channelOrder, settings.groupOrder) { list, channelOrder, groupOrder ->
            arrangeChannels(list, channelOrder, groupOrder)
        }

    suspend fun channelByNumber(playlistId: Long, number: Int) =
        channels.byNumber(playlistId, number)?.takeIf { allowed(it) }

    suspend fun channelByStreamUrl(playlistId: Long, streamUrl: String) = channels.byStreamUrl(playlistId, streamUrl)

    // ---- Locking and hiding --------------------------------------------------------------

    private val flags = db.flags()

    fun observeGroupFlags(playlistId: Long) = flags.observeGroupFlags(playlistId)

    fun observeHiddenChannels(playlistId: Long) = flags.observeHiddenChannels(playlistId)

    suspend fun isGroupLocked(playlistId: Long, scope: String, name: String) =
        flags.groupFlag(playlistId, scope, name)?.locked == true

    suspend fun setGroupFlag(playlistId: Long, scope: String, name: String, locked: Boolean? = null, hidden: Boolean? = null) {
        val current = flags.groupFlag(playlistId, scope, name) ?: GroupFlagEntity(playlistId, scope, name)
        val updated = current.copy(locked = locked ?: current.locked, hidden = hidden ?: current.hidden)
        if (!updated.locked && !updated.hidden) flags.deleteGroupFlag(playlistId, scope, name) else flags.saveGroupFlag(updated)
    }

    suspend fun hideChannel(channel: ChannelEntity) =
        flags.hideChannel(HiddenChannelEntity(channel.playlistId, channel.streamUrl, channel.name))

    suspend fun unhideChannel(playlistId: Long, streamUrl: String) = flags.unhideChannel(playlistId, streamUrl)

    fun observeFavoriteUrls(playlistId: Long): Flow<List<String>> =
        profiles.active.flatMapLatest { channels.observeFavoriteUrls(playlistId, it.id) }

    suspend fun toggleFavorite(channel: ChannelEntity): Boolean {
        val profileId = profiles.activeId
        val isFavorite = channels.isFavorite(channel.playlistId, profileId, channel.streamUrl) > 0
        if (isFavorite) {
            channels.removeFavorite(channel.playlistId, profileId, channel.streamUrl)
        } else {
            channels.addFavorite(FavoriteEntity(profileId, channel.playlistId, channel.streamUrl))
        }
        return !isFavorite
    }

    /** Validates the source by downloading it, then stores the playlist and its channels. */
    suspend fun add(playlist: PlaylistEntity): Long {
        val loaded = loader.load(playlist.toSource())
        val id = playlists.insert(
            playlist.copy(
                url = normalizedUrl(playlist),
                channelCount = loaded.channels.count { M3uVod.classify(it) == M3uVod.Kind.LIVE },
                updatedAt = System.currentTimeMillis(),
                expiresAt = loaded.account?.expiresAt,
                serverTimezone = loaded.account?.serverTimezone,
                epgAutoUrls = loaded.epgUrls.joinToString("\n").ifEmpty { null },
            ),
        )
        storeChannels(id, loaded.channels)
        return id
    }

    /** Re-downloads a playlist; on failure the previous channels are kept and the error is recorded. */
    suspend fun refresh(id: Long) {
        val playlist = playlists.get(id) ?: return
        try {
            val loaded = loader.load(playlist.toSource())
            val split = storeChannels(id, loaded.channels)
            val latest = playlists.get(id) ?: return
            playlists.update(
                latest.copy(
                    channelCount = split,
                    updatedAt = System.currentTimeMillis(),
                    lastError = null,
                    expiresAt = loaded.account?.expiresAt ?: latest.expiresAt,
                    serverTimezone = loaded.account?.serverTimezone ?: latest.serverTimezone,
                    epgAutoUrls = loaded.epgUrls.joinToString("\n").ifEmpty { null },
                ),
            )
        } catch (e: PlaylistException) {
            playlists.get(id)?.let { playlists.update(it.copy(lastError = e.message)) }
            throw e
        }
    }

    suspend fun delete(playlist: PlaylistEntity) = playlists.delete(playlist)

    /**
     * Saves changed account details. When the source changed (address, user, password…) the list is
     * downloaded first, so wrong details are refused and nothing is changed. Xtream stream addresses
     * contain the user name and password, so favorites, own lists, history, hidden channels, reminders
     * and recording rules are moved to the new addresses. Returns old -> new stream address.
     */
    suspend fun edit(updated: PlaylistEntity): Map<String, String> {
        val old = playlists.get(updated.id) ?: return emptyMap()
        val next = updated.copy(url = normalizedUrl(updated))
        if (next.toSource() == old.toSource()) {
            playlists.update(old.copy(name = next.name, epgUrl = next.epgUrl))
            return emptyMap()
        }
        val loaded = loader.load(next.toSource())
        val before = channels.all(old.id)
        val count = storeChannels(old.id, loaded.channels)
        val latest = playlists.get(old.id) ?: return emptyMap()
        playlists.update(
            latest.copy(
                name = next.name, type = next.type, url = next.url, username = next.username, password = next.password,
                userAgent = next.userAgent, liveFormat = next.liveFormat, epgUrl = next.epgUrl,
                channelCount = count,
                updatedAt = System.currentTimeMillis(),
                lastError = null,
                expiresAt = loaded.account?.expiresAt,
                serverTimezone = loaded.account?.serverTimezone ?: latest.serverTimezone,
                epgAutoUrls = loaded.epgUrls.joinToString("\n").ifEmpty { null },
            ),
        )
        val mapping = matchStreams(before, channels.all(old.id))
        db.withTransaction {
            val remap = db.remap()
            for ((from, to) in mapping) remap.all(old.id, from, to)
            // Films and episodes in "continue watching" carry the old account in their address too.
            if (old.type == PlaylistType.XTREAM && next.type == PlaylistType.XTREAM) {
                val oldBase = old.url.trimEnd('/') + "/"
                val newBase = next.url.trimEnd('/') + "/"
                val oldAccount = "/${old.username}/${old.password}/"
                val newAccount = "/${next.username}/${next.password}/"
                val library = db.library()
                for (entry in library.continueEntries(old.id)) {
                    val url = entry.url.replace(oldBase, newBase).replace(oldAccount, newAccount)
                    if (url != entry.url) library.saveContinue(entry.copy(url = url))
                }
            }
        }
        return mapping
    }

    /** Pairs each old channel with its new address: by provider id (Xtream), else by unique name and category. */
    private fun matchStreams(before: List<ChannelEntity>, after: List<ChannelEntity>): Map<String, String> {
        val byProvider = after.filter { it.providerId != null }.groupBy { it.providerId }
        val byNameGroup = after.groupBy { it.name to it.groupTitle }
        val byName = after.groupBy { it.name }
        val result = LinkedHashMap<String, String>()
        for (channel in before) {
            val match = channel.providerId?.let { byProvider[it]?.singleOrNull() }
                ?: byNameGroup[channel.name to channel.groupTitle]?.singleOrNull()
                ?: byName[channel.name]?.singleOrNull()
                ?: continue
            if (match.streamUrl != channel.streamUrl && channel.streamUrl !in result) result[channel.streamUrl] = match.streamUrl
        }
        return result
    }

    /**
     * Stores live channels; for M3U playlists, films and series episodes found in the list go to the
     * film/series section instead. Returns the number of live channels.
     */
    private suspend fun storeChannels(playlistId: Long, all: List<Channel>): Int {
        val playlist = playlists.get(playlistId)
        val isM3u = playlist?.type != PlaylistType.XTREAM
        val byKind = all.groupBy { if (isM3u) M3uVod.classify(it) else M3uVod.Kind.LIVE }
        val live = byKind[M3uVod.Kind.LIVE].orEmpty()
        db.withTransaction {
            channels.replace(playlistId, live.toEntities(playlistId))
            if (isM3u && playlist != null) {
                val movies = byKind[M3uVod.Kind.MOVIE].orEmpty()
                val episodes = byKind[M3uVod.Kind.EPISODE].orEmpty()
                val vod = db.vod()
                vod.deleteKind(playlistId, VodKinds.MOVIE)
                vod.deleteKind(playlistId, VodKinds.SERIES)
                vod.deleteKind(playlistId, VodKinds.EPISODE)
                movies.mapIndexed { i, c ->
                    VodItemEntity(
                        playlistId = playlistId, kind = VodKinds.MOVIE, providerId = c.streamUrl, position = i,
                        name = c.name, category = c.group, poster = c.logo, rating = null, year = M3uVod.year(c.name),
                        containerExtension = M3uVod.extension(c.streamUrl), added = null, streamUrl = c.streamUrl,
                    )
                }.chunked(1000).forEach { vod.insertAll(it) }

                val seriesRows = ArrayList<VodItemEntity>()
                val episodeRows = ArrayList<VodItemEntity>()
                val seen = LinkedHashMap<String, Channel>()
                episodes.forEachIndexed { i, c ->
                    val parsed = M3uVod.parseEpisode(c.name)
                    val seriesName = parsed?.series ?: c.group
                    val key = "${c.group}|$seriesName"
                    if (key !in seen) seen[key] = c
                    episodeRows += VodItemEntity(
                        playlistId = playlistId, kind = VodKinds.EPISODE, providerId = c.streamUrl, position = i,
                        name = c.name, category = c.group, poster = c.logo, rating = null, year = null,
                        containerExtension = M3uVod.extension(c.streamUrl), added = null, streamUrl = c.streamUrl,
                        seriesKey = key, season = parsed?.season ?: 1, episode = parsed?.episode ?: (i + 1),
                    )
                }
                seen.entries.forEachIndexed { i, (key, first) ->
                    seriesRows += VodItemEntity(
                        playlistId = playlistId, kind = VodKinds.SERIES, providerId = key, position = i,
                        name = key.substringAfter('|'), category = first.group, poster = first.logo, rating = null,
                        year = null, containerExtension = null, added = null, seriesKey = key,
                    )
                }
                seriesRows.chunked(1000).forEach { vod.insertAll(it) }
                episodeRows.chunked(1000).forEach { vod.insertAll(it) }
                playlists.get(playlistId)?.let {
                    playlists.update(
                        it.copy(
                            movieCount = movies.size,
                            seriesCount = seriesRows.size,
                            vodUpdatedAt = System.currentTimeMillis(),
                            vodError = null,
                        ),
                    )
                }
            }
        }
        return live.size
    }

    fun userAgentFor(playlist: PlaylistEntity?): String =
        playlist?.let { loader.userAgentFor(it.toSource()) } ?: DEFAULT_USER_AGENT

    fun userAgentFor(playlist: PlaylistEntity?, channel: ChannelEntity): String =
        channel.userAgent ?: playlist?.let { loader.userAgentFor(it.toSource()) } ?: DEFAULT_USER_AGENT

    companion object {
        /**
         * Neutral User-Agent: no app name and no version. It names the player engine (shared by
         * thousands of apps) rather than a browser, because many IPTV panels reject browser
         * ("Mozilla/…") User-Agents to stop web playback. Playlists can still set their own.
         */
        const val DEFAULT_USER_AGENT = "ExoPlayer (Linux; Android)"
        const val RECENT_LIMIT = 10
    }
}

/** What the user typed, in the form the search columns are stored in (wildcards taken literally). */
fun searchPattern(text: String): String = SearchText.escapeLike(SearchText.normalize(text))

fun PlaylistEntity.toSource(): PlaylistSource = when (type) {
    PlaylistType.XTREAM -> PlaylistSource.Xtream(
        server = url,
        username = username.orEmpty(),
        password = password.orEmpty(),
        userAgent = userAgent,
        liveFormat = liveFormat,
    )
    else -> PlaylistSource.M3u(url = url, userAgent = userAgent)
}

private fun normalizedUrl(playlist: PlaylistEntity): String = when (playlist.type) {
    PlaylistType.XTREAM -> XtreamClient.normalizeServer(playlist.url)?.toString() ?: playlist.url.trim()
    else -> playlist.url.trim()
}

private fun List<Channel>.toEntities(playlistId: Long) = mapIndexed { index, c ->
    ChannelEntity(
        playlistId = playlistId,
        position = index,
        name = c.name,
        groupTitle = c.group,
        logo = c.logo,
        streamUrl = c.streamUrl,
        epgId = c.epgId,
        userAgent = c.userAgent,
        catchupDays = c.catchupDays,
        providerId = c.providerId,
        catchupType = c.catchupType,
        catchupSource = c.catchupSource,
        number = c.number ?: (index + 1),
    )
}
