package io.github.ta3hrj.elm.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ta3hrj.elm.core.AdultContent
import io.github.ta3hrj.elm.core.Catchup
import io.github.ta3hrj.elm.core.Channel
import io.github.ta3hrj.elm.core.EpgProgramme
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.SearchText
import io.github.ta3hrj.elm.core.VodItem
import io.github.ta3hrj.elm.core.VodKind
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.data.AppData
import io.github.ta3hrj.elm.desktop.data.Catalog
import io.github.ta3hrj.elm.desktop.data.DesktopSettings
import io.github.ta3hrj.elm.desktop.data.Guide
import io.github.ta3hrj.elm.desktop.data.PlaylistContent
import io.github.ta3hrj.elm.desktop.data.SavedPlaylist
import io.github.ta3hrj.elm.desktop.data.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Live, Guide, Movies, Series, Search, Playlists, Settings }

/** A list of channels the user picks from: a category, the favorites, the recently watched or all. */
sealed class ChannelGroup(val title: String) {
    class Favorites : ChannelGroup(tx("★ Favoriler", "★ Favorites"))
    class Recent : ChannelGroup(tx("Son izlenenler", "Recently watched"))
    class All : ChannelGroup(tx("Tüm kanallar", "All channels"))
    class Category(val name: String) : ChannelGroup(name)

    val key: String get() = when (this) {
        is Favorites -> "#fav"
        is Recent -> "#recent"
        is All -> "#all"
        is Category -> name
    }
}

/** What the player screen shows: a live channel (with its list for zapping) or a film / episode / archive. */
data class PlayRequest(
    val title: String,
    val url: String,
    val userAgent: String,
    val live: Boolean,
    /** Live: the list to zap through and where the channel is in it. */
    val channels: List<Channel> = emptyList(),
    val index: Int = 0,
    /** Films and episodes: where to continue from, saved under [resumeKey]. */
    val resumeKey: String? = null,
    val startMs: Long = 0,
    val subtitle: String? = null,
)

/**
 * The desktop app's state: the user's data, the active playlist's content and guide, and what is loading.
 * Compose reads the fields directly; work runs on [scope] and writes them back.
 */
class AppState(
    private val storage: Storage,
    val catalog: Catalog,
    private val scope: CoroutineScope,
) {
    var data by mutableStateOf(storage.load())
        private set
    var screen by mutableStateOf(Screen.Live)
    var content by mutableStateOf<PlaylistContent?>(null)
        private set
    var guide by mutableStateOf(Guide.EMPTY)
        private set
    var movies by mutableStateOf<List<VodItem>?>(null)
        private set
    var series by mutableStateOf<List<VodItem>?>(null)
        private set
    /** What is loading now (shown in the status bar), or null. */
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var playing by mutableStateOf<PlayRequest?>(null)

    private var loadJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val saver = Dispatchers.IO.limitedParallelism(1)

    val settings: DesktopSettings get() = data.settings
    val playlist: SavedPlaylist?
        get() = data.playlists.firstOrNull { it.id == data.settings.activePlaylistId } ?: data.playlists.firstOrNull()

    init {
        applyLanguage(data.settings.language)
    }

    fun start() {
        playlist?.let { load(it, refresh = false) }
    }

    private fun update(transform: (AppData) -> AppData) {
        val next = transform(data)
        data = next
        scope.launch(saver) { runCatching { storage.save(next) }.onFailure { message = tx("Kaydedilemedi: ", "Could not save: ") + it.message } }
    }

    // ---- Playlists ----

    fun addOrUpdatePlaylist(p: SavedPlaylist) {
        val id = if (p.id == 0L) (data.playlists.maxOfOrNull { it.id } ?: 0L) + 1 else p.id
        val saved = p.copy(id = id)
        update { d ->
            val list = if (d.playlists.any { it.id == id }) d.playlists.map { if (it.id == id) saved else it } else d.playlists + saved
            d.copy(playlists = list, settings = d.settings.copy(activePlaylistId = id))
        }
        catalog.forget(saved)
        load(saved, refresh = true)
    }

    fun removePlaylist(p: SavedPlaylist) {
        catalog.forget(p)
        update { d -> d.copy(playlists = d.playlists.filter { it.id != p.id }, favorites = d.favorites - p.id, recent = d.recent - p.id) }
        content = null
        guide = Guide.EMPTY
        playlist?.let { load(it, refresh = false) }
    }

    fun selectPlaylist(p: SavedPlaylist) {
        update { it.copy(settings = it.settings.copy(activePlaylistId = p.id)) }
        load(p, refresh = false)
    }

    fun refresh() = playlist?.let { load(it, refresh = true) }

    private fun load(p: SavedPlaylist, refresh: Boolean) {
        loadJob?.cancel()
        movies = null
        series = null
        if (!refresh) {
            content = null
            guide = Guide.EMPTY
        }
        loadJob = scope.launch {
            try {
                busy = tx("Liste yükleniyor…", "Loading the playlist…")
                val c = catalog.content(p, refresh)
                content = c
                busy = tx("Rehber yükleniyor…", "Loading the guide…")
                guide = runCatching { catalog.guide(p, c, refresh) }.getOrElse {
                    message = tx("Rehber indirilemedi: ", "Guide download failed: ") + it.message
                    Guide.EMPTY
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: e.javaClass.simpleName
            } finally {
                busy = null
            }
        }
    }

    fun loadVod(kind: VodKind, refresh: Boolean = false) {
        val p = playlist ?: return
        val c = content ?: return
        if (!refresh && (if (kind == VodKind.MOVIE) movies else series) != null) return
        scope.launch {
            busy = if (kind == VodKind.MOVIE) tx("Filmler yükleniyor…", "Loading films…") else tx("Diziler yükleniyor…", "Loading series…")
            try {
                val items = catalog.vod(p, kind, c, refresh)
                if (kind == VodKind.MOVIE) movies = items else series = items
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: e.javaClass.simpleName
                if (kind == VodKind.MOVIE) movies = emptyList() else series = emptyList()
            } finally {
                busy = null
            }
        }
    }

    // ---- Channels ----

    /** Live channels the user may see (adult categories hidden when the setting says so). */
    fun visibleChannels(): List<Channel> {
        val live = content?.live.orEmpty()
        return if (settings.hideAdult) live.filter { !AdultContent.isAdultCategory(it.group) } else live
    }

    fun groups(): List<ChannelGroup> {
        val categories = visibleChannels().map { it.group }.distinct().map { ChannelGroup.Category(it) }
        return listOf(ChannelGroup.Favorites(), ChannelGroup.Recent(), ChannelGroup.All()) + categories
    }

    fun channels(group: ChannelGroup, query: String = ""): List<Channel> {
        val visible = visibleChannels()
        val p = playlist?.id ?: 0
        val list = when (group) {
            is ChannelGroup.Favorites -> byUrl(visible, data.favoritesOf(p))
            is ChannelGroup.Recent -> byUrl(visible, data.recentOf(p))
            is ChannelGroup.All -> visible
            is ChannelGroup.Category -> visible.filter { it.group == group.name }
        }
        return filter(list, query) { it.name }
    }

    private fun byUrl(channels: List<Channel>, urls: List<String>): List<Channel> {
        val byUrl = channels.associateBy { it.streamUrl }
        return urls.mapNotNull { byUrl[it] }
    }

    fun isFavorite(channel: Channel) = channel.streamUrl in data.favoritesOf(playlist?.id ?: 0)

    fun toggleFavorite(channel: Channel) {
        val p = playlist?.id ?: return
        update { d ->
            val list = d.favoritesOf(p)
            val next = if (channel.streamUrl in list) list - channel.streamUrl else list + channel.streamUrl
            d.copy(favorites = d.favorites + (p to next))
        }
    }

    fun playChannel(channel: Channel, list: List<Channel>) {
        val p = playlist ?: return
        val index = list.indexOfFirst { it.streamUrl == channel.streamUrl }.coerceAtLeast(0)
        noteRecent(channel)
        playing = PlayRequest(
            title = channel.name,
            url = channel.streamUrl,
            userAgent = catalog.userAgent(p, channel),
            live = true,
            channels = list.ifEmpty { listOf(channel) },
            index = index,
        )
    }

    fun noteRecent(channel: Channel) {
        val p = playlist?.id ?: return
        update { d -> d.copy(recent = d.recent + (p to (listOf(channel.streamUrl) + (d.recentOf(p) - channel.streamUrl)).take(RECENT_MAX))) }
    }

    /** A past programme from the archive, when the channel has one and the programme is still in it. */
    fun archiveUrl(channel: Channel, programme: EpgProgramme, now: Long = System.currentTimeMillis()): String? {
        if (!Catchup.isAvailable(channel.catchupDays, channel.catchupType, programme.start, now)) return null
        return Catchup.archiveUrl(channel.streamUrl, channel.catchupType, channel.catchupSource, programme.start, programme.stop, now, content?.account?.serverTimezone)
    }

    fun playArchive(channel: Channel, programme: EpgProgramme) {
        val p = playlist ?: return
        val url = archiveUrl(channel, programme) ?: return
        playing = PlayRequest(title = programme.title, subtitle = channel.name, url = url, userAgent = catalog.userAgent(p, channel), live = false)
    }

    // ---- Films and series ----

    fun playMovie(item: VodItem) {
        val p = playlist ?: return
        val url = catalog.movieUrl(p, item)
        playing = PlayRequest(title = item.name, url = url, userAgent = catalog.userAgent(p), live = false, resumeKey = url, startMs = data.resume[url] ?: 0)
    }

    fun playEpisode(series: VodItem, episode: io.github.ta3hrj.elm.core.Episode) {
        val p = playlist ?: return
        val url = catalog.episodeUrl(p, episode)
        playing = PlayRequest(
            title = episode.title,
            subtitle = series.name + tx(" · Sezon ${episode.season}, Bölüm ${episode.number}", " · Season ${episode.season}, Episode ${episode.number}"),
            url = url,
            userAgent = catalog.userAgent(p),
            live = false,
            resumeKey = url,
            startMs = data.resume[url] ?: 0,
        )
    }

    /** Remembers where a film stopped; near the end it counts as watched and starts from the beginning next time. */
    fun saveResume(key: String, positionMs: Long, lengthMs: Long) {
        val finished = lengthMs > 0 && positionMs > lengthMs - 120_000
        update { d ->
            val resume = if (finished || positionMs < 30_000) d.resume - key else d.resume + (key to positionMs)
            d.copy(resume = if (resume.size > RESUME_MAX) resume.entries.toList().takeLast(RESUME_MAX).associate { it.toPair() } else resume)
        }
    }

    suspend fun seriesInfo(item: VodItem) = withContext(Dispatchers.IO) {
        catalog.seriesInfo(playlist ?: error("no playlist"), item, content ?: error("no content"))
    }

    suspend fun movieInfo(item: VodItem) = withContext(Dispatchers.IO) { playlist?.let { catalog.movieInfo(it, item) } }

    fun vodCategories(items: List<VodItem>): List<String> {
        val names = items.map { it.category }.distinct()
        return if (settings.hideAdult) names.filter { !AdultContent.isAdultCategory(it) } else names
    }

    // ---- Settings ----

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
        val before = settings
        update { it.copy(settings = transform(it.settings)) }
        if (before.language != settings.language) applyLanguage(settings.language)
    }

    fun clearCaches() {
        catalogClear()
        message = tx("Önbellek temizlendi.", "Cache cleared.")
    }

    private fun catalogClear() {
        storage.clearCaches()
        refresh()
    }

    private fun applyLanguage(language: String) {
        Lang.apply(
            when (language) {
                "tr" -> Lang.Choice.TURKISH
                "en" -> Lang.Choice.ENGLISH
                else -> Lang.Choice.SYSTEM
            },
        )
    }

    companion object {
        const val RECENT_MAX = 30
        const val RESUME_MAX = 500

        /** Case- and accent-free search, the same as on the TV ("cocuk" finds "ÇOCUK"). */
        fun <T> filter(items: List<T>, query: String, name: (T) -> String): List<T> {
            val q = SearchText.normalize(query)
            if (q.isEmpty()) return items
            return items.filter { SearchText.normalize(name(it)).contains(q) }
        }
    }
}
