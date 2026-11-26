package io.github.ta3hrj.elm.core

/** Where a playlist's channels come from. */
sealed interface PlaylistSource {
    /** Optional User-Agent some providers require; null uses the app default. */
    val userAgent: String?

    data class M3u(
        val url: String,
        override val userAgent: String? = null,
    ) : PlaylistSource

    data class Xtream(
        val server: String,
        val username: String,
        val password: String,
        override val userAgent: String? = null,
        /** "ts" (MPEG-TS) is the most widely supported; "m3u8" uses HLS. */
        val liveFormat: String = "ts",
    ) : PlaylistSource
}

/** One live channel, independent of the source format. */
data class Channel(
    val name: String,
    val group: String,
    val streamUrl: String,
    val logo: String? = null,
    /** XMLTV channel id (tvg-id / epg_channel_id) used to match the TV guide later. */
    val epgId: String? = null,
    /** Provider-specific id (Xtream stream_id); null for plain M3U. */
    val providerId: String? = null,
    /** Per-channel User-Agent from #EXTVLCOPT, if any. */
    val userAgent: String? = null,
    /** Days of catch-up archive the provider offers (0 = none). */
    val catchupDays: Int = 0,
    /** How archive URLs are built: "xc", "default", "append", "shift", "flussonic"… (see [Catchup]). */
    val catchupType: String? = null,
    /** Archive URL template or suffix from `catchup-source`. */
    val catchupSource: String? = null,
    /** Channel number from `tvg-chno` (M3U) or `num` (Xtream); null means "position in the list". */
    val number: Int? = null,
)

/** Account details returned by an Xtream Codes server. */
data class XtreamAccount(
    val username: String,
    val status: String?,
    /** Expiry as Unix seconds, or null if the server reports none (unlimited). */
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    /** Server time zone (e.g. "Europe/Istanbul"); archive start times are given in it. */
    val serverTimezone: String? = null,
    /** Account creation as Unix seconds. */
    val createdAt: Long? = null,
    /** Free text from the provider ("message" in user_info). */
    val message: String? = null,
    val trial: Boolean = false,
)

enum class VodKind { MOVIE, SERIES }

/** A film or a series from an Xtream account. */
data class VodItem(
    val kind: VodKind,
    /** Xtream stream_id (films) or series_id. */
    val providerId: String,
    val name: String,
    val category: String,
    val poster: String?,
    val rating: String?,
    val year: String?,
    /** File extension of a film (mp4, mkv…); null for series. */
    val containerExtension: String?,
    /** Unix seconds the provider added it, if known. */
    val added: Long?,
)

data class MovieInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val duration: String?,
    val rating: String?,
    val poster: String?,
    val backdrop: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val containerExtension: String,
    val plot: String?,
    val duration: String?,
    val image: String?,
)

data class SeriesInfo(
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val poster: String?,
    val backdrop: String?,
    /** Episodes grouped by season number, in season order. */
    val seasons: Map<Int, List<Episode>>,
)

class PlaylistException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Group name for entries without one. */
val UNGROUPED: String get() = tx("Diğer", "Other")
