package io.github.ta3hrj.elm.recordings

import io.github.ta3hrj.elm.core.SeriesTitle
import io.github.ta3hrj.elm.data.AppDatabase
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.PlaylistRepository
import io.github.ta3hrj.elm.data.RecordingRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Series recording: a rule ("every episode of X on channel Y") schedules each matching programme the
 * guide knows about. Rules are applied at start, after every guide update and when a rule is made.
 * An episode the user cancelled (or that was already recorded) is not scheduled again.
 */
class SeriesRecording(
    private val db: AppDatabase,
    private val recordings: Recordings,
    private val repository: PlaylistRepository,
) {
    private val rules = db.recordingRules()
    private val lock = Mutex()

    fun observeRules(): Flow<List<RecordingRuleEntity>> = rules.observeAll()

    /** Adds a rule for the programme's series on this channel; false if it already exists. */
    suspend fun addRule(channel: ChannelEntity, programmeTitle: String): Boolean {
        val title = programmeTitle.trim()
        if (rules.all().any { it.playlistId == channel.playlistId && it.streamUrl == channel.streamUrl && SeriesTitle.sameSeries(it.title, title) }) {
            return false
        }
        rules.insert(RecordingRuleEntity(playlistId = channel.playlistId, streamUrl = channel.streamUrl, channelName = channel.name, title = title))
        return true
    }

    suspend fun deleteRule(id: Long) = rules.delete(id)

    /** Schedules every upcoming episode of every rule; returns how many new recordings were planned. */
    suspend fun apply(): Int = lock.withLock {
        val now = System.currentTimeMillis()
        var planned = 0
        for (rule in rules.all()) {
            val playlist = db.playlists().get(rule.playlistId) ?: continue
            val channel = db.channels().byStreamUrl(rule.playlistId, rule.streamUrl) ?: continue
            val episodes = db.epg().upcoming(rule.playlistId, rule.streamUrl, now, UPCOMING_LIMIT)
                .filter { it.start > now && SeriesTitle.sameSeries(it.title, rule.title) }
            for (programme in episodes) {
                if (db.recordings().anyOverlapping(rule.streamUrl, programme.start, programme.stop) != null) continue
                val (saved, _) = recordings.schedule(
                    playlistId = rule.playlistId,
                    channelName = channel.name,
                    title = programme.title,
                    streamUrl = channel.streamUrl,
                    userAgent = repository.userAgentFor(playlist, channel),
                    start = programme.start - Recordings.START_MARGIN_MS,
                    stop = programme.stop + Recordings.END_MARGIN_MS,
                )
                if (saved != null) planned++
            }
        }
        planned
    }

    private companion object {
        const val UPCOMING_LIMIT = 300
    }
}
