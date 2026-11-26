package io.github.ta3hrj.elm.core

import java.io.BufferedReader
import java.io.Reader

/**
 * Parser for extended M3U / M3U8 IPTV playlists.
 *
 * Supports `#EXTINF` attributes (tvg-id, tvg-name, tvg-logo, group-title, catchup-days),
 * `#EXTGRP`, `#EXTVLCOPT:http-user-agent=` and plain URL lines. Lines it does not know are ignored.
 */
object M3uParser {

    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    /** Channels plus the guide (XMLTV) addresses announced in the `#EXTM3U` header. */
    data class Result(val channels: List<Channel>, val epgUrls: List<String>)

    fun parse(reader: Reader): List<Channel> = parsePlaylist(reader).channels

    fun parsePlaylist(reader: Reader): Result {
        val channels = ArrayList<Channel>()
        val epgUrls = LinkedHashSet<String>()
        // Playlist-wide catch-up defaults from the #EXTM3U header.
        var defaultCatchup: String? = null
        var defaultCatchupSource: String? = null
        var defaultCatchupDays = 0
        var pendingInfo: ExtInf? = null
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null

        BufferedReader(reader).useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim().removePrefix("﻿")
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val attributes = attributeRegex.findAll(line)
                            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        listOfNotNull(attributes["url-tvg"], attributes["x-tvg-url"], attributes["tvg-url"])
                            .flatMap { it.split(',', ' ') }
                            .map { it.trim() }
                            .filter { isStreamUrl(it) }
                            .forEach { epgUrls += it }
                        defaultCatchup = attributes["catchup"]?.trim()?.ifEmpty { null }
                        defaultCatchupSource = attributes["catchup-source"]?.trim()?.ifEmpty { null }
                        defaultCatchupDays = attributes["catchup-days"]?.trim()?.toIntOrNull() ?: 0
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingInfo = parseExtInf(line)
                        pendingGroup = null
                        pendingUserAgent = null
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) ->
                        pendingGroup = line.substringAfter(':').trim().ifEmpty { null }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val option = line.substringAfter(':')
                        if (option.startsWith("http-user-agent=", ignoreCase = true)) {
                            pendingUserAgent = option.substringAfter('=').trim().ifEmpty { null }
                        }
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = pendingInfo
                        if (isStreamUrl(line)) {
                            channels += Channel(
                                name = info?.name?.ifBlank { null } ?: line.substringAfterLast('/'),
                                group = info?.attributes?.get("group-title")?.trim()?.ifEmpty { null }
                                    ?: pendingGroup
                                    ?: UNGROUPED,
                                streamUrl = line,
                                logo = info?.attributes?.get("tvg-logo")?.trim()?.ifEmpty { null },
                                epgId = info?.attributes?.get("tvg-id")?.trim()?.ifEmpty { null },
                                userAgent = pendingUserAgent,
                                catchupDays = info?.attributes?.get("catchup-days")?.trim()?.toIntOrNull()
                                    ?: info?.attributes?.get("tvg-rec")?.trim()?.toIntOrNull()
                                    ?: info?.attributes?.get("timeshift")?.trim()?.toIntOrNull()
                                    ?: defaultCatchupDays,
                                catchupType = info?.attributes?.get("catchup")?.trim()?.ifEmpty { null }
                                    ?: defaultCatchup,
                                catchupSource = info?.attributes?.get("catchup-source")?.trim()?.ifEmpty { null }
                                    ?: defaultCatchupSource,
                                number = (info?.attributes?.get("tvg-chno") ?: info?.attributes?.get("channel-number"))
                                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                            )
                        }
                        pendingInfo = null
                        pendingGroup = null
                        pendingUserAgent = null
                    }
                }
            }
        }
        return Result(channels, epgUrls.toList())
    }

    fun parse(text: String): List<Channel> = parse(text.reader())

    /** Quick check used to reject HTML error pages served instead of a playlist. */
    fun looksLikeM3u(firstLine: String): Boolean =
        firstLine.trim().removePrefix("﻿").startsWith("#EXTM3U", ignoreCase = true)

    private data class ExtInf(val attributes: Map<String, String>, val name: String)

    private fun parseExtInf(line: String): ExtInf {
        val body = line.substringAfter(':', "")
        // The display name follows the first comma that is not inside a quoted attribute value.
        var inQuotes = false
        var commaIndex = -1
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    commaIndex = i
                    break
                }
            }
        }
        val header = if (commaIndex >= 0) body.substring(0, commaIndex) else body
        val name = if (commaIndex >= 0) body.substring(commaIndex + 1).trim() else ""
        val attributes = attributeRegex.findAll(header)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val displayName = name.ifEmpty { attributes["tvg-name"].orEmpty() }
        return ExtInf(attributes, displayName)
    }

    private val streamSchemes = setOf("http", "https", "rtmp", "rtmps", "rtsp", "rtp", "udp", "mms")

    private fun isStreamUrl(line: String): Boolean =
        line.substringBefore("://", "").lowercase() in streamSchemes
}
