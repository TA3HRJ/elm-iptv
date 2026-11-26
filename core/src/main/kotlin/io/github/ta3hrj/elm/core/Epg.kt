package io.github.ta3hrj.elm.core

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** A playlist channel, as the EPG matcher sees it. */
data class EpgChannelRef(val streamUrl: String, val name: String, val epgId: String?)

/** Which XMLTV channel id a playlist channel's guide comes from. */
data class EpgLink(val streamUrl: String, val epgKey: String)

data class EpgProgramme(
    val channelKey: String,
    /** Unix epoch milliseconds. */
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/** A channel the XMLTV file declares, offered when the user matches a channel by hand. */
data class EpgSourceChannel(val id: String, val name: String)

data class EpgImportResult(
    val links: List<EpgLink>,
    val programmes: List<EpgProgramme>,
    /** Every `<channel>` of the file (id and first display name). */
    val sourceChannels: List<EpgSourceChannel> = emptyList(),
    /** Number of `<channel>` entries the XMLTV file declared. */
    val sourceChannelCount: Int,
    /** True if [EpgImporter.maxProgrammes] was reached and later programmes were dropped. */
    val truncated: Boolean,
)

/** Parses XMLTV timestamps such as `20261108203000 +0300`, `202611082030` or `20261108203000Z`. */
object XmltvTime {
    fun parse(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.length < 12) return null
        val digits = text.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.substring(8, 10).toInt()
        val minute = digits.substring(10, 12).toInt()
        val second = if (digits.length >= 14) digits.substring(12, 14).toInt() else 0
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        val offsetMinutes = parseOffset(text.substring(digits.length).trim()) ?: return null
        return calendar.timeInMillis - offsetMinutes * 60_000L
    }

    /** `+0300`, `-05:30`, `Z` or nothing (UTC). Returns null for anything else. */
    private fun parseOffset(text: String): Int? {
        if (text.isEmpty() || text.equals("Z", ignoreCase = true) || text.equals("UTC", ignoreCase = true)) return 0
        val sign = when (text[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = text.substring(1).filter { it.isDigit() }
        if (digits.length != 4) return null
        return sign * (digits.substring(0, 2).toInt() * 60 + digits.substring(2, 4).toInt())
    }
}

/** Builds the lookup key used to match a playlist channel name with an XMLTV display name. */
object EpgNames {
    private val prefix = Regex("""^\s*(\[[^\]]{1,12}\]|\([^)]{1,12}\)|[A-Za-z]{2,4}\s*[:|]+)\s*""")
    // Compiled once: normalize runs for every channel of the playlist and of the guide.
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^a-z0-9+]+")
    private val qualityWords = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "hq", "raw", "backup")

    /**
     * How alike a playlist channel name and a guide channel name are, 0 (unrelated) to 100 (same after
     * [normalize]); used to put likely matches first when a channel is matched by hand.
     */
    fun similarity(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        if (x == y) return 100
        if (x.startsWith(y) || y.startsWith(x)) return 80
        if (x.contains(y) || y.contains(x)) return 65
        // Share of the shorter name found as a common beginning (e.g. "beinseries4" / "beinseri4").
        var prefix = 0
        while (prefix < x.length && prefix < y.length && x[prefix] == y[prefix]) prefix++
        return (prefix * 50) / minOf(x.length, y.length)
    }

    fun normalize(name: String): String {
        var text = name.replace('ı', 'i').replace('İ', 'I')
        // Strip provider prefixes like "TR: ", "TR | ", "[TR]", "(DE)".
        repeat(2) { text = text.replace(prefix, "") }
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "")
        val words = text.lowercase(Locale.ROOT)
            .split(separators)
            .filter { it.isNotEmpty() && it !in qualityWords }
        return words.joinToString("")
    }
}

/**
 * Streams an XMLTV file and keeps only the programmes of the given playlist channels
 * inside a time window, so even very large provider guides fit in memory.
 */
class EpgImporter(
    private val newParser: () -> XmlPullParser,
    val maxProgrammes: Int = 200_000,
    private val maxDescriptionLength: Int = 300,
) {

    fun import(
        input: InputStream,
        channels: List<EpgChannelRef>,
        windowStart: Long,
        windowEnd: Long,
        alreadyLinked: Set<String> = emptySet(),
        /**
         * Matches the user chose by hand (stream URL -> guide channel id); they replace the automatic
         * match, and an empty id means "no guide" for that channel.
         */
        manual: Map<String, String> = emptyMap(),
    ): EpgImportResult {
        val parser = newParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(decompressIfNeeded(input), null)

        val sourceIds = HashMap<String, String>() // lowercase id -> id as written
        val sourceNames = HashMap<String, String>() // normalized display name -> id
        val sourceChannels = ArrayList<EpgSourceChannel>()
        var links: List<EpgLink>? = null
        var wanted: Map<String, String> = emptyMap() // lowercase id -> id
        val programmes = ArrayList<EpgProgramme>()
        var truncated = false

        fun resolveLinks() {
            if (links != null) return
            val resolved = channels
                .filter { it.streamUrl !in alreadyLinked }
                .mapNotNull { channel ->
                    val chosen = manual[channel.streamUrl]
                    val key = if (chosen != null) resolveManual(chosen, sourceIds) else resolve(channel, sourceIds, sourceNames)
                    key?.let { EpgLink(channel.streamUrl, it) }
                }
            links = resolved
            wanted = resolved.associate { it.epgKey.lowercase(Locale.ROOT) to it.epgKey }
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val id = parser.getAttributeValue(null, "id")?.trim().orEmpty()
                        val names = readChannelNames(parser)
                        if (id.isNotEmpty() && links == null) {
                            id.lowercase(Locale.ROOT).let {
                                if (it !in sourceIds) {
                                    sourceIds[it] = id
                                    sourceChannels += EpgSourceChannel(id, names.firstOrNull()?.ifBlank { null } ?: id)
                                }
                            }
                            names.forEach { name ->
                                val key = EpgNames.normalize(name)
                                if (key.isNotEmpty() && key !in sourceNames) sourceNames[key] = id
                            }
                        }
                    }
                    "programme" -> {
                        resolveLinks()
                        val channelAttr = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
                        val key = wanted[channelAttr.lowercase(Locale.ROOT)]
                        val start = XmltvTime.parse(parser.getAttributeValue(null, "start"))
                        val stop = XmltvTime.parse(parser.getAttributeValue(null, "stop"))
                        if (key == null || start == null || stop == null || stop <= start ||
                            stop <= windowStart || start >= windowEnd
                        ) {
                            skipElement(parser)
                        } else {
                            val (title, description) = readProgramme(parser)
                            if (programmes.size < maxProgrammes) {
                                programmes += EpgProgramme(
                                    channelKey = key,
                                    start = start,
                                    stop = stop,
                                    title = title.ifBlank { "—" },
                                    description = description?.take(maxDescriptionLength),
                                )
                            } else {
                                truncated = true
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        resolveLinks()
        return EpgImportResult(links.orEmpty(), programmes, sourceChannels, sourceIds.size, truncated)
    }

    /** A hand-picked guide channel; only this file's own channels count (an empty id: no guide). */
    private fun resolveManual(id: String, sourceIds: Map<String, String>): String? {
        if (id.isBlank()) return null
        sourceIds[id.lowercase(Locale.ROOT)]?.let { return it }
        // Guides without <channel> declarations: trust the id, as for tvg-id.
        return if (sourceIds.isEmpty()) id else null
    }

    private fun resolve(
        channel: EpgChannelRef,
        sourceIds: Map<String, String>,
        sourceNames: Map<String, String>,
    ): String? {
        val epgId = channel.epgId?.trim()?.ifEmpty { null }
        if (epgId != null) {
            sourceIds[epgId.lowercase(Locale.ROOT)]?.let { return it }
            // Some guides omit <channel> declarations entirely; trust tvg-id then.
            if (sourceIds.isEmpty()) return epgId
        }
        val byName = sourceNames[EpgNames.normalize(channel.name)]
        if (byName != null) return byName
        return epgId?.let { sourceNames[EpgNames.normalize(it)] }
    }

    private fun readChannelNames(parser: XmlPullParser): List<String> {
        val names = ArrayList<String>(2)
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == XmlPullParser.START_TAG && parser.name == "display-name") {
                names += readText(parser)
            }
        }
        return names
    }

    /** Returns the first title and description, preferring Turkish, then English, then any language. */
    private fun readProgramme(parser: XmlPullParser): Pair<String, String?> {
        val titles = ArrayList<Pair<String?, String>>(2)
        val descriptions = ArrayList<Pair<String?, String>>(2)
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> titles += parser.getAttributeValue(null, "lang") to readText(parser)
                    "desc" -> descriptions += parser.getAttributeValue(null, "lang") to readText(parser)
                }
            }
        }
        return pick(titles).orEmpty() to pick(descriptions)?.ifBlank { null }
    }

    private fun pick(values: List<Pair<String?, String>>): String? =
        (values.firstOrNull { it.first.equals("tr", true) }
            ?: values.firstOrNull { it.first.equals("en", true) }
            ?: values.firstOrNull())?.second?.trim()

    private fun readText(parser: XmlPullParser): String {
        val text = StringBuilder()
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) text.append(parser.text)
        }
        return text.toString().replace(WHITESPACE, " ").trim()
    }

    private fun skipElement(parser: XmlPullParser) {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
        }
    }

    companion object {
        // Compiled once: readText runs for every title and description of a guide (hundreds of thousands).
        private val WHITESPACE = Regex("\\s+")

        /** Guides are often served as `.xml.gz` without a Content-Encoding header. */
        fun decompressIfNeeded(input: InputStream): InputStream {
            val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
            buffered.mark(2)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
        }
    }
}
