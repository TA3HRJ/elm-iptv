package io.github.ta3hrj.elm.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Separates films and series episodes from live channels in M3U playlists.
 *
 * Detection uses the stream address only (group names like "Film" are often live channels):
 * Xtream-style `/movie/` and `/series/` paths, and video file extensions such as `.mp4` or `.mkv`.
 */
object M3uVod {
    enum class Kind { LIVE, MOVIE, EPISODE }

    data class EpisodeName(val series: String, val season: Int, val episode: Int)

    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "webm", "mpg", "mpeg", "divx", "3gp")
    private val episodePattern = Regex("""^(.*?)[\s._\-:|]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,4})\b.*$""")
    private val altEpisodePattern = Regex("""^(.*?)[\s._\-:|]+(\d{1,2})x(\d{1,3})\b.*$""")
    private val yearPattern = Regex("""[(\[\s\-]((?:19|20)\d{2})[)\]]?\s*$""")

    fun classify(channel: Channel): Kind {
        val url = channel.streamUrl.toHttpUrlOrNull()
        val segments = url?.pathSegments?.map { it.lowercase(Locale.ROOT) }.orEmpty()
        val extension = segments.lastOrNull()?.substringAfterLast('.', "").orEmpty()
        return when {
            "series" in segments -> Kind.EPISODE
            "movie" in segments || "movies" in segments -> Kind.MOVIE
            extension in videoExtensions -> if (parseEpisode(channel.name) != null) Kind.EPISODE else Kind.MOVIE
            else -> Kind.LIVE
        }
    }

    /** "Dizi Adı S01 E02", "Dizi.Adi.S1E2", "Dizi Adı - 1x02" → series name, season and episode. */
    fun parseEpisode(name: String): EpisodeName? {
        val match = episodePattern.matchEntire(name.trim()) ?: altEpisodePattern.matchEntire(name.trim()) ?: return null
        val series = match.groupValues[1].replace('.', ' ').replace('_', ' ').trim().trimEnd('-', ':', '|').trim()
        if (series.isEmpty()) return null
        return EpisodeName(series, match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }

    /** Film year from a trailing "(2021)", "[2021]" or "- 2021". */
    fun year(name: String): String? = yearPattern.find(name.trim())?.groupValues?.get(1)

    fun extension(streamUrl: String): String? =
        streamUrl.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it in videoExtensions }
}
