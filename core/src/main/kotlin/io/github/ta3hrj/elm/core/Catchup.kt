package io.github.ta3hrj.elm.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds catch-up (archive) addresses for past programmes.
 *
 * Supported `catchup` types, following the conventions used by IPTV players such as Kodi's
 * IPTV Simple Client:
 * - `xc`: Xtream Codes `/timeshift/user/pass/minutes/YYYY-MM-DD:HH-MM/id.ts`
 * - `default`: `catchup-source` is a full URL template
 * - `append`: `catchup-source` is appended to the live stream URL
 * - `shift` / `timeshift`: `?utc=START&lutc=NOW` is appended
 * - `flussonic` (`fs`, `flussonic-hls`, `flussonic-ts`): Flussonic archive paths
 *
 * Templates may use `{utc}`/`{start}`, `{utcend}`/`{end}`, `{lutc}`/`{now}`/`{timestamp}`, `{duration}`
 * (seconds), `{duration:60}` (seconds divided by 60), `{offset}` (seconds since start), and the local
 * start time parts `{Y}` `{m}` `{d}` `{H}` `{M}` `{S}`; the `${name}` spelling works as well.
 */
object Catchup {
    const val TYPE_XTREAM = "xc"
    private const val DEFAULT_DAYS_WHEN_UNSPECIFIED = 5

    /** Days of archive a channel offers; a declared catch-up type without a day count means a few days. */
    fun availableDays(catchupDays: Int, catchupType: String?): Int = when {
        catchupDays > 0 -> catchupDays
        !catchupType.isNullOrBlank() -> DEFAULT_DAYS_WHEN_UNSPECIFIED
        else -> 0
    }

    /** Whether a programme that started at [start] can still be watched from the archive at [now]. */
    fun isAvailable(catchupDays: Int, catchupType: String?, start: Long, now: Long): Boolean {
        val days = availableDays(catchupDays, catchupType)
        return days > 0 && start < now && start >= now - days * 86_400_000L
    }

    /**
     * @param timezone the server's time zone for Xtream links; null uses the device time zone.
     * @return the archive URL, or null if the channel has no usable catch-up configuration.
     */
    fun archiveUrl(
        streamUrl: String,
        catchupType: String?,
        catchupSource: String?,
        start: Long,
        stop: Long,
        now: Long = System.currentTimeMillis(),
        timezone: String? = null,
    ): String? {
        val type = catchupType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val source = catchupSource?.trim().orEmpty()
        return when (type) {
            TYPE_XTREAM, "xtream" -> xtreamFromLiveUrl(streamUrl, start, stop, timezone)
            "append" -> if (source.isEmpty()) null else streamUrl + fill(source, start, stop, now)
            "shift", "timeshift" -> streamUrl + (if ('?' in streamUrl) "&" else "?") +
                fill("utc={utc}&lutc={lutc}", start, stop, now)
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> flussonic(streamUrl, type, start, stop)
            "default", "" -> when {
                source.startsWith("http://", true) || source.startsWith("https://", true) -> fill(source, start, stop, now)
                source.isNotEmpty() -> streamUrl + fill(source, start, stop, now)
                else -> null
            }
            else -> if (source.isNotEmpty()) fill(source, start, stop, now) else null
        }
    }

    fun xtreamTimeshiftUrl(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        start: Long,
        durationMinutes: Int,
        timezone: String?,
    ): String {
        val base = baseUrl.toHttpUrlOrNull() ?: throw PlaylistException(tx("Geçersiz sunucu adresi.", "Invalid server address."))
        return base.newBuilder()
            .addPathSegment("timeshift")
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment(durationMinutes.toString())
            .addPathSegment(format("yyyy-MM-dd:HH-mm", start, timezone))
            .addPathSegment("$streamId.ts")
            .build()
            .toString()
    }

    /** Turns `http://host/live/user/pass/123.ts` (or `/user/pass/123`) into its timeshift address. */
    private fun xtreamFromLiveUrl(streamUrl: String, start: Long, stop: Long, timezone: String?): String? {
        val url = streamUrl.toHttpUrlOrNull() ?: return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.firstOrNull() == "live") segments.removeAt(0)
        if (segments.size < 3) return null
        val (user, pass, file) = segments.takeLast(3)
        val streamId = file.substringBefore('.')
        val prefix = url.newBuilder().encodedPath("/")
        segments.dropLast(3).forEach { prefix.addPathSegment(it) }
        return xtreamTimeshiftUrl(prefix.build().toString(), user, pass, streamId, start, durationMinutes(start, stop), timezone)
    }

    private fun flussonic(streamUrl: String, type: String, start: Long, stop: Long): String? {
        val url = streamUrl.toHttpUrlOrNull() ?: return null
        val startSec = start / 1000
        val duration = (stop - start) / 1000
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val last = segments.last()
        val channelPath = if (last.contains('.') || last == "mpegts") segments.dropLast(1) else segments
        val hls = type == "flussonic-hls" || (type != "flussonic-ts" && last.endsWith(".m3u8"))
        val file = if (hls) {
            val name = last.substringBefore('.').takeIf { last.endsWith(".m3u8") } ?: "index"
            "$name-$startSec-$duration.m3u8"
        } else {
            "timeshift_abs-$startSec.ts"
        }
        val builder = url.newBuilder().encodedPath("/")
        channelPath.forEach { builder.addPathSegment(it) }
        builder.addPathSegment(file)
        return builder.build().toString()
    }

    /** Minutes to request: the programme length plus a little slack at the end. */
    fun durationMinutes(start: Long, stop: Long): Int = (((stop - start) / 60_000) + 5).toInt().coerceAtLeast(1)

    private val placeholder = Regex("""\$?\{([a-zA-Z]+)(?::(-?\d+))?\}""")

    private fun fill(template: String, start: Long, stop: Long, now: Long): String =
        placeholder.replace(template) { match ->
            val name = match.groupValues[1]
            val divisor = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: 1L
            val value: String? = when (name.lowercase(Locale.ROOT)) {
                "utc", "start", "startutc" -> (start / 1000).toString()
                "utcend", "end", "endutc", "stop" -> (stop / 1000).toString()
                "lutc", "now", "timestamp" -> (now / 1000).toString()
                "duration" -> ((stop - start) / 1000 / divisor).toString()
                "offset" -> ((now - start) / 1000 / divisor).toString()
                else -> null
            } ?: when (name) {
                "Y" -> format("yyyy", start, null)
                "m" -> format("MM", start, null)
                "d" -> format("dd", start, null)
                "H" -> format("HH", start, null)
                "M" -> format("mm", start, null)
                "S" -> format("ss", start, null)
                else -> null
            }
            value ?: match.value
        }

    private fun format(pattern: String, time: Long, timezone: String?): String =
        SimpleDateFormat(pattern, Locale.ROOT).apply {
            timeZone = timezone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        }.format(Date(time))
}
