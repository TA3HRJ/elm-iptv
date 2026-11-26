package io.github.ta3hrj.elm.desktop.data

import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.EpgChannelRef
import io.github.ta3hrj.elm.core.EpgImporter
import io.github.ta3hrj.elm.core.EpgProgramme
import io.github.ta3hrj.elm.core.Episode
import io.github.ta3hrj.elm.core.M3uParser
import io.github.ta3hrj.elm.core.M3uVod
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.PlaylistLoader
import io.github.ta3hrj.elm.core.PlaylistSource
import io.github.ta3hrj.elm.core.SeriesInfo
import io.github.ta3hrj.elm.core.VodItem
import io.github.ta3hrj.elm.core.VodKind
import io.github.ta3hrj.elm.core.XtreamAccount
import io.github.ta3hrj.elm.core.XtreamClient
import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import org.kxml2.io.KXmlParser
import java.io.File

/** What a playlist holds: live channels, and for M3U the films and series found in it. */
data class PlaylistContent(
    val live: List<Channel>,
    val account: XtreamAccount?,
    val epgUrls: List<String>,
    /** M3U only: films and series found in the list (Xtream loads them from the server on demand). */
    val m3uMovies: List<VodItem> = emptyList(),
    val m3uSeries: List<VodItem> = emptyList(),
    /** M3U only: episodes by series id. */
    val m3uEpisodes: Map<String, List<Episode>> = emptyMap(),
    /** When it was downloaded (epoch ms). */
    val loadedAt: Long = System.currentTimeMillis(),
)

/**
 * Downloads and caches playlists, films, series and the guide with the shared `core` code, the same code the
 * Android app runs. Everything is kept in memory (a computer has room for it) and on disk as a cache.
 */
class Catalog(
    private val http: OkHttpClient,
    private val storage: Storage,
    val defaultUserAgent: String = DEFAULT_USER_AGENT,
) {
    private val loader = PlaylistLoader(http, defaultUserAgent)

    fun userAgent(playlist: SavedPlaylist, channel: Channel? = null): String =
        channel?.userAgent?.ifBlank { null } ?: loader.userAgentFor(playlist.source())

    /** The playlist from the cache, or downloaded when there is none or [refresh] is set. */
    suspend fun content(playlist: SavedPlaylist, refresh: Boolean = false): PlaylistContent {
        if (!refresh) storage.readCache(contentFile(playlist), CachedContent.serializer())?.let { return it.toContent() }
        val content = download(playlist)
        storage.writeCache(contentFile(playlist), CachedContent.serializer(), CachedContent.from(content))
        return content
    }

    private suspend fun download(playlist: SavedPlaylist): PlaylistContent {
        val local = localFile(playlist)
        if (local != null) {
            val parsed = withContext(Dispatchers.IO) { local.bufferedReader().use { M3uParser.parsePlaylist(it) } }
            if (parsed.channels.isEmpty()) throw PlaylistException(tx("Listede oynatılabilir kanal bulunamadı.", "No playable channels in the playlist."))
            return split(parsed.channels, null, parsed.epgUrls)
        }
        val loaded = loader.load(playlist.source())
        return if (playlist.isXtream) PlaylistContent(loaded.channels, loaded.account, loaded.epgUrls) else split(loaded.channels, null, loaded.epgUrls)
    }

    /** M3U lists mix live channels with films and episodes; they are told apart by their address (see [M3uVod]). */
    internal fun split(channels: List<Channel>, account: XtreamAccount?, epgUrls: List<String>): PlaylistContent {
        val live = ArrayList<Channel>()
        val movies = ArrayList<VodItem>()
        val episodes = LinkedHashMap<String, MutableList<Episode>>()
        val seriesCategory = HashMap<String, String>()
        val seriesPoster = HashMap<String, String?>()
        for (channel in channels) {
            when (M3uVod.classify(channel)) {
                M3uVod.Kind.LIVE -> live += channel
                M3uVod.Kind.MOVIE -> movies += VodItem(
                    kind = VodKind.MOVIE,
                    providerId = channel.streamUrl,
                    name = channel.name,
                    category = channel.group,
                    poster = channel.logo,
                    rating = null,
                    year = M3uVod.year(channel.name),
                    containerExtension = M3uVod.extension(channel.streamUrl),
                    added = null,
                )
                M3uVod.Kind.EPISODE -> {
                    val parsed = M3uVod.parseEpisode(channel.name)
                    val series = parsed?.series ?: channel.group
                    val id = "m3u:$series"
                    seriesCategory.getOrPut(id) { channel.group }
                    if (seriesPoster[id] == null) seriesPoster[id] = channel.logo
                    episodes.getOrPut(id) { ArrayList() } += Episode(
                        id = channel.streamUrl,
                        season = parsed?.season ?: 1,
                        number = parsed?.episode ?: (episodes[id]?.size ?: 0) + 1,
                        title = channel.name,
                        containerExtension = M3uVod.extension(channel.streamUrl) ?: "mp4",
                        plot = null,
                        duration = null,
                        image = channel.logo,
                    )
                }
            }
        }
        val series = episodes.keys.map { id ->
            VodItem(VodKind.SERIES, id, id.removePrefix("m3u:"), seriesCategory[id].orEmpty(), seriesPoster[id], null, null, null, null)
        }
        return PlaylistContent(live, account, epgUrls, movies, series, episodes)
    }

    /** Films or series of a playlist: from the Xtream server (cached), or the ones the M3U list holds. */
    suspend fun vod(playlist: SavedPlaylist, kind: VodKind, content: PlaylistContent, refresh: Boolean = false): List<VodItem> {
        if (!playlist.isXtream) return if (kind == VodKind.MOVIE) content.m3uMovies else content.m3uSeries
        val file = vodFile(playlist, kind)
        if (!refresh) storage.readCache(file, ListSerializer(CachedVod.serializer()))?.let { list -> return list.map { it.toItem() } }
        val client = xtream(playlist)
        val items = if (kind == VodKind.MOVIE) client.movies() else client.series()
        storage.writeCache(file, ListSerializer(CachedVod.serializer()), items.map { CachedVod.from(it) })
        return items
    }

    suspend fun seriesInfo(playlist: SavedPlaylist, item: VodItem, content: PlaylistContent): SeriesInfo {
        if (!playlist.isXtream) {
            val episodes = content.m3uEpisodes[item.providerId].orEmpty()
            return SeriesInfo(null, null, item.category, null, null, item.poster, null,
                episodes.groupBy { it.season }.toSortedMap().mapValues { (_, list) -> list.sortedBy { it.number } })
        }
        return xtream(playlist).seriesInfo(item.providerId)
    }

    suspend fun movieInfo(playlist: SavedPlaylist, item: VodItem) =
        if (playlist.isXtream) runCatching { xtream(playlist).movieInfo(item.providerId) }.getOrNull() else null

    fun movieUrl(playlist: SavedPlaylist, item: VodItem): String =
        if (playlist.isXtream) xtream(playlist).movieUrl(item.providerId, item.containerExtension) else item.providerId

    fun episodeUrl(playlist: SavedPlaylist, episode: Episode): String =
        if (playlist.isXtream) xtream(playlist).episodeUrl(episode.id, episode.containerExtension) else episode.id

    /**
     * The guide for [channels]: from the cache while it is younger than [GUIDE_MAX_AGE_MS], else downloaded from
     * the playlist's own guide address(es) and kept from [pastDays] ago to three days ahead.
     */
    suspend fun guide(playlist: SavedPlaylist, content: PlaylistContent, refresh: Boolean = false, now: Long = System.currentTimeMillis()): Guide {
        val file = guideFile(playlist)
        if (!refresh && (storage.cacheAge(file, now) ?: Long.MAX_VALUE) < GUIDE_MAX_AGE_MS) {
            storage.readCache(file, CachedGuide.serializer())?.let { return it.toGuide() }
        }
        val urls = guideUrls(playlist, content)
        if (urls.isEmpty()) return Guide.EMPTY
        val pastDays = content.live.maxOfOrNull { io.github.ta3hrj.elm.core.Catchup.availableDays(it.catchupDays, it.catchupType) }?.coerceIn(1, 7) ?: 1
        val refs = content.live.map { EpgChannelRef(it.streamUrl, it.name, it.epgId) }
        val links = HashMap<String, String>()
        val programmes = ArrayList<EpgProgramme>()
        var lastError: Exception? = null
        withContext(Dispatchers.IO) {
            for (url in urls) {
                try {
                    val result = loader.openGuide(url, userAgent(playlist)) { input ->
                        EpgImporter({ KXmlParser() }).import(input, refs, now - pastDays * DAY_MS, now + 3 * DAY_MS, alreadyLinked = links.keys)
                    }
                    result.links.forEach { links[it.streamUrl] = it.epgKey }
                    programmes += result.programmes
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        if (links.isEmpty() && lastError != null) throw lastError!!
        val guide = Guide.of(links, programmes)
        storage.writeCache(file, CachedGuide.serializer(), CachedGuide.from(links, programmes))
        return guide
    }

    fun guideUrls(playlist: SavedPlaylist, content: PlaylistContent): List<String> =
        playlist.guideUrl.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { content.epgUrls }

    fun forget(playlist: SavedPlaylist) = storage.deleteCaches("p${playlist.id}-")

    private fun xtream(playlist: SavedPlaylist) = XtreamClient(http, playlist.source() as PlaylistSource.Xtream, userAgent(playlist))

    private fun localFile(playlist: SavedPlaylist): File? {
        if (playlist.isXtream) return null
        val url = playlist.url.trim()
        val path = when {
            url.startsWith("file:", ignoreCase = true) -> runCatching { File(java.net.URI(url)) }.getOrNull()
            url.contains("://") -> null
            else -> File(url)
        }
        return path?.takeIf { it.isFile }
    }

    private fun contentFile(p: SavedPlaylist) = "p${p.id}-content.json.gz"
    private fun vodFile(p: SavedPlaylist, kind: VodKind) = "p${p.id}-${kind.name.lowercase()}.json.gz"
    private fun guideFile(p: SavedPlaylist) = "p${p.id}-guide.json.gz"

    companion object {
        /** A neutral name of the player engine, as VLC itself sends it; playlists can set their own. */
        const val DEFAULT_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"
        const val DAY_MS = 86_400_000L
        const val GUIDE_MAX_AGE_MS = 12 * 60 * 60_000L
    }
}

/** A guide in memory: programmes by guide channel, found for a stream through its link. */
class Guide private constructor(
    private val links: Map<String, String>,
    private val byKey: Map<String, List<EpgProgramme>>,
) {
    val linkedChannels: Int get() = links.size
    val programmeCount: Int get() = byKey.values.sumOf { it.size }

    fun programmes(streamUrl: String): List<EpgProgramme> = links[streamUrl]?.let { byKey[it] }.orEmpty()

    /** The programme on air at [at], if any. */
    fun now(streamUrl: String, at: Long): EpgProgramme? {
        val list = programmes(streamUrl)
        val i = indexAt(list, at)
        return list.getOrNull(i)?.takeIf { it.start <= at && at < it.stop }
    }

    /** The first programme that starts after [at]. */
    fun next(streamUrl: String, at: Long): EpgProgramme? = programmes(streamUrl).firstOrNull { it.start > at }

    /** Index of the last programme starting at or before [at] (binary search on the sorted list). */
    private fun indexAt(list: List<EpgProgramme>, at: Long): Int {
        var lo = 0
        var hi = list.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].start <= at) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    companion object {
        val EMPTY = Guide(emptyMap(), emptyMap())

        fun of(links: Map<String, String>, programmes: List<EpgProgramme>): Guide =
            Guide(links.toMap(), programmes.groupBy { it.channelKey }.mapValues { (_, list) -> list.sortedBy { it.start } })
    }
}

@Serializable
internal data class CachedChannel(
    val name: String,
    val group: String,
    val streamUrl: String,
    val logo: String? = null,
    val epgId: String? = null,
    val providerId: String? = null,
    val userAgent: String? = null,
    val catchupDays: Int = 0,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val number: Int? = null,
) {
    fun toChannel() = Channel(name, group, streamUrl, logo, epgId, providerId, userAgent, catchupDays, catchupType, catchupSource, number)

    companion object {
        fun from(c: Channel) = CachedChannel(c.name, c.group, c.streamUrl, c.logo, c.epgId, c.providerId, c.userAgent, c.catchupDays, c.catchupType, c.catchupSource, c.number)
    }
}

@Serializable
internal data class CachedVod(
    val kind: String,
    val providerId: String,
    val name: String,
    val category: String,
    val poster: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val containerExtension: String? = null,
    val added: Long? = null,
) {
    fun toItem() = VodItem(VodKind.valueOf(kind), providerId, name, category, poster, rating, year, containerExtension, added)

    companion object {
        fun from(v: VodItem) = CachedVod(v.kind.name, v.providerId, v.name, v.category, v.poster, v.rating, v.year, v.containerExtension, v.added)
    }
}

@Serializable
internal data class CachedEpisode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val containerExtension: String,
    val image: String? = null,
)

@Serializable
internal data class CachedAccount(
    val username: String,
    val status: String? = null,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val serverTimezone: String? = null,
)

@Serializable
internal data class CachedContent(
    val live: List<CachedChannel>,
    val account: CachedAccount? = null,
    val epgUrls: List<String> = emptyList(),
    val movies: List<CachedVod> = emptyList(),
    val series: List<CachedVod> = emptyList(),
    val episodes: Map<String, List<CachedEpisode>> = emptyMap(),
    val loadedAt: Long = 0,
) {
    fun toContent() = PlaylistContent(
        live = live.map { it.toChannel() },
        account = account?.let { XtreamAccount(it.username, it.status, it.expiresAt, it.maxConnections, it.activeConnections, it.serverTimezone) },
        epgUrls = epgUrls,
        m3uMovies = movies.map { it.toItem() },
        m3uSeries = series.map { it.toItem() },
        m3uEpisodes = episodes.mapValues { (_, list) -> list.map { Episode(it.id, it.season, it.number, it.title, it.containerExtension, null, null, it.image) } },
        loadedAt = loadedAt,
    )

    companion object {
        fun from(c: PlaylistContent) = CachedContent(
            live = c.live.map { CachedChannel.from(it) },
            account = c.account?.let { CachedAccount(it.username, it.status, it.expiresAt, it.maxConnections, it.activeConnections, it.serverTimezone) },
            epgUrls = c.epgUrls,
            movies = c.m3uMovies.map { CachedVod.from(it) },
            series = c.m3uSeries.map { CachedVod.from(it) },
            episodes = c.m3uEpisodes.mapValues { (_, list) -> list.map { CachedEpisode(it.id, it.season, it.number, it.title, it.containerExtension, it.image) } },
            loadedAt = c.loadedAt,
        )
    }
}

@Serializable
internal data class CachedProgramme(val key: String, val start: Long, val stop: Long, val title: String, val description: String? = null)

@Serializable
internal data class CachedGuide(val links: Map<String, String>, val programmes: List<CachedProgramme>) {
    fun toGuide() = Guide.of(links, programmes.map { EpgProgramme(it.key, it.start, it.stop, it.title, it.description) })

    companion object {
        fun from(links: Map<String, String>, programmes: List<EpgProgramme>) =
            CachedGuide(links, programmes.map { CachedProgramme(it.channelKey, it.start, it.stop, it.title, it.description) })
    }
}
