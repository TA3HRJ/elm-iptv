package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.tx

import android.util.Xml
import androidx.room.withTransaction
import io.github.ta3hrj.elm.core.EpgChannelRef
import io.github.ta3hrj.elm.core.EpgImporter
import io.github.ta3hrj.elm.core.EpgNames
import io.github.ta3hrj.elm.core.EpgLink
import io.github.ta3hrj.elm.core.EpgProgramme
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.PlaylistLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads XMLTV guides, matches them to playlist channels and stores the programmes.
 *
 * Updates run in an application-wide scope so they finish even if the user leaves the screen.
 */
class GuideRepository(
    private val db: AppDatabase,
    private val loader: PlaylistLoader,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val http: okhttp3.OkHttpClient,
) {
    private val epg = db.epg()
    private val importer = EpgImporter({ Xml.newPullParser() })

    private val _updating = MutableStateFlow<Set<Long>>(emptySet())
    /** Playlists whose guide is being downloaded right now. */
    val updating: StateFlow<Set<Long>> = _updating.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short status messages for the UI ("Rehber güncellendi…"). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Runs after a guide was updated successfully (series recordings pick up new episodes). */
    var onUpdated: (suspend () -> Unit)? = null

    fun observeNow(playlistId: Long, now: Long): Flow<List<ChannelProgramme>> = epg.observeNow(playlistId, now)

    fun observeWindow(playlistId: Long, from: Long, to: Long): Flow<List<ChannelProgramme>> =
        epg.observeWindow(playlistId, from, to)

    suspend fun upcoming(playlistId: Long, streamUrl: String, now: Long = System.currentTimeMillis(), limit: Int = 2) =
        epg.upcoming(playlistId, streamUrl, now, limit)

    fun observeProgrammeSearch(playlistId: Long, query: String, from: Long, unlocked: Boolean) =
        epg.observeProgrammeSearch(playlistId, searchPattern(query), from, unlocked)

    /** An archive entry: a programme and whether the server can play it back. */
    data class ArchiveEntry(val programme: ChannelProgramme, val playable: Boolean)

    /**
     * Past programmes of a channel within [days] days. Xtream accounts are asked for the stream's own
     * archive guide (it reaches further back than the stored guide); otherwise the stored guide is used.
     */
    suspend fun archive(playlist: PlaylistEntity, channel: ChannelEntity, days: Int): List<ArchiveEntry> {
        val now = System.currentTimeMillis()
        val from = now - days * 24 * HOUR
        val source = playlist.toSource() as? io.github.ta3hrj.elm.core.PlaylistSource.Xtream
        val streamId = channel.providerId ?: channel.streamUrl.substringAfterLast('/').substringBefore('.').takeIf { it.all(Char::isDigit) }
        if (source != null && streamId != null) {
            val remote = runCatching {
                withContext(Dispatchers.IO) {
                    io.github.ta3hrj.elm.core.XtreamClient(http, source, loader.userAgentFor(source)).archiveListings(streamId)
                }
            }.getOrNull()
            if (!remote.isNullOrEmpty()) {
                return remote.filter { it.stop > from && it.start < now }.map {
                    ArchiveEntry(ChannelProgramme(channel.streamUrl, it.start, it.stop, it.title, it.description), playable = it.hasArchive || it.stop > now)
                }
            }
        }
        return epg.programmesFor(playlist.id, channel.streamUrl, from, now).map { ArchiveEntry(it, playable = true) }
    }

    /** Starts a guide update unless one is already running for this playlist. */
    fun refreshInBackground(playlistId: Long, quiet: Boolean = false) {
        if (playlistId in _updating.value) return
        scope.launch {
            try {
                val message = refresh(playlistId)
                if (!quiet && message != null) _events.emit(message)
                runCatching { onUpdated?.invoke() }
            } catch (e: PlaylistException) {
                _events.emit(tx("Rehber güncellenemedi: ${e.message}", "Guide update failed: ${e.message}"))
            } catch (e: Exception) {
                _events.emit(tx("Rehber güncellenemedi: ${e.message ?: e.javaClass.simpleName}", "Guide update failed: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    // ---- Matching a channel to the guide by hand ----------------------------------------------

    /** What the match screen shows for one channel. */
    data class MatchInfo(
        /** Guide channel id in use now (automatic or by hand), null when the channel has no guide. */
        val current: String?,
        /** The hand-made choice: null = automatic, "" = no guide, else a guide channel id. */
        val override: String?,
        /** The guide's channels, likeliest matches first. */
        val sources: List<EpgSourceEntity>,
    )

    suspend fun matchInfo(playlistId: Long, channel: ChannelEntity): MatchInfo {
        val sources = epg.sources(playlistId)
        val sorted = sources.sortedWith(
            compareByDescending<EpgSourceEntity> { EpgNames.similarity(channel.name, it.name) }
                .thenBy { it.name.lowercase() },
        )
        return MatchInfo(
            current = epg.link(playlistId, channel.streamUrl),
            override = epg.overrides(playlistId).firstOrNull { it.streamUrl == channel.streamUrl }?.epgKey,
            sources = sorted,
        )
    }

    /**
     * Saves a hand-made match ([epgKey] null: back to automatic, "": no guide). It applies at once when
     * the guide already holds that channel's programmes; otherwise the guide is downloaded again.
     * Returns true if a guide update was started.
     */
    suspend fun setManualMatch(playlistId: Long, channel: ChannelEntity, epgKey: String?): Boolean {
        when {
            epgKey == null -> {
                epg.deleteOverride(playlistId, channel.streamUrl)
                refreshInBackground(playlistId, quiet = true)
                return true
            }
            epgKey.isEmpty() -> {
                epg.saveOverride(EpgOverrideEntity(playlistId, channel.streamUrl, ""))
                epg.deleteLink(playlistId, channel.streamUrl)
                return false
            }
            else -> {
                epg.saveOverride(EpgOverrideEntity(playlistId, channel.streamUrl, epgKey))
                epg.insertLinks(listOf(EpgLinkEntity(playlistId, channel.streamUrl, epgKey)))
                if (epg.hasProgrammes(playlistId, epgKey)) return false
                refreshInBackground(playlistId, quiet = true)
                return true
            }
        }
    }

    /** Updates the guide now and waits for it (background work); errors are thrown. */
    suspend fun refreshNow(playlistId: Long) {
        if (playlistId in _updating.value) return
        refresh(playlistId)
        runCatching { onUpdated?.invoke() }
    }

    /** Refreshes the guide when it is older than [maxAgeMs]. */
    fun refreshIfStale(playlist: PlaylistEntity, maxAgeMs: Long) {
        if (playlist.guideUrls().isNotEmpty() && System.currentTimeMillis() - playlist.epgUpdatedAt > maxAgeMs) {
            refreshInBackground(playlist.id, quiet = true)
        }
    }

    suspend fun setGuideUrl(playlistId: Long, url: String?) {
        val playlist = db.playlists().get(playlistId) ?: return
        db.playlists().update(playlist.copy(epgUrl = url?.trim()?.ifEmpty { null }))
    }

    /** Downloads and stores the guide. Returns a summary message, or null if the playlist is gone. */
    private suspend fun refresh(playlistId: Long): String? {
        _updating.update { it + playlistId }
        try {
            val playlist = db.playlists().get(playlistId) ?: return null
            val urls = playlist.guideUrls()
            if (urls.isEmpty()) {
                db.playlists().update(playlist.copy(epgError = tx("Rehber adresi yok. Listeler ekranından ekleyebilirsiniz.", "No guide address. You can add one on the Playlists screen.")))
                return tx("Bu liste için rehber adresi yok.", "This playlist has no guide address.")
            }
            val channels = epg.channelRefs(playlistId).map { EpgChannelRef(it.streamUrl, it.name, it.epgId) }
            val userAgent = loader.userAgentFor(playlist.toSource())
            val now = System.currentTimeMillis()
            // Keep more history when the playlist offers catch-up, so archived programmes can be picked.
            val hasCatchup = db.channels().hasCatchup(playlistId)
            val windowStart = now - if (hasCatchup) KEEP_PAST_CATCHUP_MS else KEEP_PAST_MS
            val windowEnd = now + KEEP_FUTURE_MS

            val manual = epg.overrides(playlistId).associate { it.streamUrl to it.epgKey }
            val links = ArrayList<EpgLink>()
            val programmes = ArrayList<EpgProgramme>()
            val sources = LinkedHashMap<String, String>()
            val errors = ArrayList<String>()
            var truncated = false
            withContext(Dispatchers.IO) {
                for (url in urls) {
                    try {
                        val linked = links.mapTo(HashSet()) { it.streamUrl }
                        val result = loader.openGuide(url, userAgent) { body ->
                            importer.import(body, channels, windowStart, windowEnd, alreadyLinked = linked, manual = manual)
                        }
                        result.sourceChannels.forEach { if (it.id !in sources) sources[it.id] = it.name }
                        links += result.links
                        programmes += result.programmes
                        truncated = truncated || result.truncated
                    } catch (e: PlaylistException) {
                        errors += e.message.orEmpty()
                    } catch (e: Exception) {
                        // Malformed XML and similar parser failures.
                        errors += tx("Rehber okunamadı (${e.javaClass.simpleName}): ${e.message.orEmpty()}", "Could not read the guide (${e.javaClass.simpleName}): ${e.message.orEmpty()}").take(200)
                    }
                }
            }

            if (links.isEmpty() && errors.isNotEmpty()) {
                // Keep the previous guide; only record what went wrong.
                db.playlists().get(playlistId)?.let { db.playlists().update(it.copy(epgError = errors.joinToString(" · "))) }
                throw PlaylistException(errors.first())
            }

            // Guides published in the wrong time zone are moved by the offset set in the guide settings.
            val offset = settings.epgOffsetMinutes(playlistId) * 60_000L
            db.withTransaction {
                epg.deleteLinks(playlistId)
                epg.deleteProgrammes(playlistId)
                epg.deleteSources(playlistId)
                sources.entries.chunked(1000).forEach { chunk ->
                    epg.insertSources(chunk.map { (key, name) -> EpgSourceEntity(playlistId, key, name) })
                }
                links.chunked(1000).forEach { chunk ->
                    epg.insertLinks(chunk.map { EpgLinkEntity(playlistId, it.streamUrl, it.epgKey) })
                }
                // Convert in chunks so a large guide is never held twice in memory.
                programmes.asSequence().chunked(2000).forEach { chunk ->
                    epg.insertProgrammes(
                        chunk.map {
                            ProgrammeEntity(
                                playlistId = playlistId,
                                channelKey = it.channelKey,
                                start = it.start + offset,
                                stop = it.stop + offset,
                                title = it.title,
                                description = it.description,
                            )
                        },
                    )
                }
                db.playlists().get(playlistId)?.let {
                    db.playlists().update(
                        it.copy(
                            epgUpdatedAt = now,
                            programmeCount = programmes.size,
                            epgLinkedChannels = links.size,
                            epgError = buildList {
                                addAll(errors)
                                if (truncated) add(tx("Rehber çok büyük; bazı programlar atlandı.", "The guide is very large; some programmes were skipped."))
                                if (links.isEmpty()) add(tx("Rehberdeki kanallar bu listedeki kanallarla eşleşmedi.", "The guide's channels did not match this playlist's channels."))
                            }.joinToString(" · ").ifEmpty { null },
                        ),
                    )
                }
            }
            val distinctChannels = channels.distinctBy { it.streamUrl }.size
            return tx("Rehber güncellendi: ${links.size}/$distinctChannels kanal, ${programmes.size} program.", "Guide updated: ${links.size}/$distinctChannels channels, ${programmes.size} programmes.")
        } finally {
            _updating.update { it - playlistId }
        }
    }

    companion object {
        private const val HOUR = 3_600_000L
        const val KEEP_PAST_MS = 6 * HOUR
        const val KEEP_PAST_CATCHUP_MS = 48 * HOUR
        const val KEEP_FUTURE_MS = 48 * HOUR
        const val STALE_AFTER_MS = 12 * HOUR
    }
}
