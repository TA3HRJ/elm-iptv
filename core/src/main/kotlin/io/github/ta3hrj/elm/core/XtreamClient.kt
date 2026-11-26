package io.github.ta3hrj.elm.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal client for the Xtream Codes `player_api.php` API used by most IPTV providers.
 *
 * Provider responses are inconsistent (numbers sent as strings, nulls, empty arrays instead of
 * objects), so fields are read leniently instead of through strict data classes.
 */
class XtreamClient(
    private val http: OkHttpClient,
    private val source: PlaylistSource.Xtream,
    private val userAgent: String,
) {
    val baseUrl: HttpUrl = normalizeServer(source.server)
        ?: throw PlaylistException(tx("Geçersiz sunucu adresi: ", "Invalid server address: ") + source.server)

    suspend fun account(): XtreamAccount {
        val root = getJson(null) as? JsonObject
            ?: throw PlaylistException(tx("Sunucu beklenmeyen bir yanıt verdi.", "The server sent an unexpected response."))
        val info = root["user_info"] as? JsonObject
        if (info == null || info.string("auth") == "0") {
            throw PlaylistException(tx("Kullanıcı adı veya şifre hatalı.", "Wrong username or password."))
        }
        val server = root["server_info"] as? JsonObject
        return XtreamAccount(
            username = info.string("username") ?: source.username,
            status = info.string("status"),
            expiresAt = info.string("exp_date")?.toLongOrNull(),
            maxConnections = info.string("max_connections")?.toIntOrNull(),
            activeConnections = info.string("active_cons")?.toIntOrNull(),
            serverTimezone = server?.string("timezone")?.trim()?.ifEmpty { null },
            createdAt = info.string("created_at")?.toLongOrNull(),
            message = info.string("message")?.trim()?.ifEmpty { null },
            trial = info.string("is_trial") == "1",
        )
    }

    /** Category id to display name, in the provider's order. */
    suspend fun liveCategories(): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        (getJson("get_live_categories") as? JsonArray)?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = obj.string("category_id") ?: return@forEach
            result[id] = obj.string("category_name")?.trim()?.ifEmpty { null } ?: UNGROUPED
        }
        return result
    }

    suspend fun liveChannels(): List<Channel> {
        val categories = liveCategories()
        val categoryOrder = categories.keys.withIndex().associate { it.value to it.index }
        data class Entry(val order: Int, val num: Int, val channel: Channel)

        val entries = streamArray("get_live_streams") { obj ->
            val streamId = obj.string("stream_id") ?: return@streamArray null
            val categoryId = obj.string("category_id")
            val hasArchive = obj.string("tv_archive") == "1"
            Entry(
                order = categoryOrder[categoryId] ?: Int.MAX_VALUE,
                num = obj.string("num")?.toIntOrNull() ?: Int.MAX_VALUE,
                channel = Channel(
                    name = obj.string("name")?.trim().orEmpty().ifEmpty { tx("Kanal ", "Channel ") + streamId },
                    group = categories[categoryId] ?: UNGROUPED,
                    streamUrl = liveStreamUrl(streamId),
                    logo = obj.string("stream_icon")?.trim()?.ifEmpty { null },
                    epgId = obj.string("epg_channel_id")?.trim()?.ifEmpty { null },
                    providerId = streamId,
                    catchupDays = if (hasArchive) obj.string("tv_archive_duration")?.toIntOrNull() ?: 0 else 0,
                    catchupType = if (hasArchive) Catchup.TYPE_XTREAM else null,
                    number = obj.string("num")?.toIntOrNull()?.takeIf { it > 0 },
                ),
            )
        } ?: throw PlaylistException(tx("Kanal listesi alınamadı.", "Could not get the channel list."))
        return entries.sortedWith(compareBy({ it.order }, { it.num })).map { it.channel }
    }

    suspend fun movies(): List<VodItem> = vodList(
        categoriesAction = "get_vod_categories",
        itemsAction = "get_vod_streams",
        kind = VodKind.MOVIE,
        idField = "stream_id",
        posterField = "stream_icon",
    )

    suspend fun series(): List<VodItem> = vodList(
        categoriesAction = "get_series_categories",
        itemsAction = "get_series",
        kind = VodKind.SERIES,
        idField = "series_id",
        posterField = "cover",
    )

    suspend fun movieInfo(streamId: String): MovieInfo {
        val root = getJson("get_vod_info", "vod_id" to streamId) as? JsonObject
            ?: throw PlaylistException(tx("Film bilgisi alınamadı.", "Could not get film details."))
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        return MovieInfo(
            plot = info.text("plot", "description"),
            cast = info.text("cast", "actors"),
            director = info.text("director"),
            genre = info.text("genre"),
            releaseDate = info.text("releasedate", "release_date"),
            duration = info.text("duration"),
            rating = info.text("rating"),
            poster = info.text("movie_image", "cover_big"),
            backdrop = info.backdrop(),
        )
    }

    suspend fun seriesInfo(seriesId: String): SeriesInfo {
        val root = getJson("get_series_info", "series_id" to seriesId) as? JsonObject
            ?: throw PlaylistException(tx("Dizi bilgisi alınamadı.", "Could not get series details."))
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        // "episodes" is an object keyed by season number (sometimes an array of arrays).
        val episodesBySeason = when (val episodes = root["episodes"]) {
            is JsonObject -> episodes.values.toList()
            is JsonArray -> episodes.toList()
            else -> emptyList()
        }
        val episodes = episodesBySeason.flatMap { (it as? JsonArray)?.toList().orEmpty() }.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val details = obj["info"] as? JsonObject
            val season = obj.string("season")?.toIntOrNull() ?: 1
            val number = obj.string("episode_num")?.toIntOrNull() ?: 0
            Episode(
                id = id,
                season = season,
                number = number,
                title = obj.text("title") ?: tx("Bölüm ", "Episode ") + number,
                containerExtension = obj.text("container_extension") ?: "mp4",
                plot = details?.text("plot"),
                duration = details?.text("duration"),
                image = details?.text("movie_image", "cover_big"),
            )
        }
        return SeriesInfo(
            plot = info.text("plot"),
            cast = info.text("cast"),
            genre = info.text("genre"),
            releaseDate = info.text("releaseDate", "release_date"),
            rating = info.text("rating"),
            poster = info.text("cover"),
            backdrop = info.backdrop(),
            seasons = episodes.groupBy { it.season }.toSortedMap().mapValues { (_, list) -> list.sortedBy { it.number } },
        )
    }

    fun movieUrl(streamId: String, extension: String?): String =
        mediaUrl("movie", "$streamId.${extension?.ifBlank { null } ?: "mp4"}")

    fun episodeUrl(episodeId: String, extension: String?): String =
        mediaUrl("series", "$episodeId.${extension?.ifBlank { null } ?: "mp4"}")

    private fun mediaUrl(kind: String, file: String): String =
        baseUrl.newBuilder()
            .addPathSegment(kind)
            .addPathSegment(source.username)
            .addPathSegment(source.password)
            .addPathSegment(file)
            .build()
            .toString()

    private suspend fun vodList(
        categoriesAction: String,
        itemsAction: String,
        kind: VodKind,
        idField: String,
        posterField: String,
    ): List<VodItem> {
        val categories = LinkedHashMap<String, String>()
        (getJson(categoriesAction) as? JsonArray)?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = obj.string("category_id") ?: return@forEach
            categories[id] = obj.string("category_name")?.trim()?.ifEmpty { null } ?: UNGROUPED
        }
        val order = categories.keys.withIndex().associate { it.value to it.index }
        val items = streamArray(itemsAction) { obj ->
            val id = obj.string(idField) ?: return@streamArray null
            val categoryId = obj.string("category_id")
            Triple(
                order[categoryId] ?: Int.MAX_VALUE,
                obj.string("num")?.toIntOrNull() ?: Int.MAX_VALUE,
                VodItem(
                    kind = kind,
                    providerId = id,
                    name = obj.string("name")?.trim().orEmpty().ifEmpty { id },
                    category = categories[categoryId] ?: UNGROUPED,
                    poster = obj.text(posterField),
                    rating = obj.text("rating")?.takeIf { it != "0" && it != "0.0" },
                    year = obj.text("year") ?: obj.text("releaseDate", "release_date")?.take(4),
                    containerExtension = obj.text("container_extension"),
                    added = obj.string("added")?.toLongOrNull() ?: obj.string("last_modified")?.toLongOrNull(),
                ),
            )
        } ?: return emptyList()
        return items.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    /**
     * Every guide entry the server keeps for one live stream, including past days and whether each
     * one can be played from the archive (`get_simple_data_table`). Titles come base64-encoded.
     */
    suspend fun archiveListings(streamId: String): List<ArchiveListing> {
        val root = getJson("get_simple_data_table", "stream_id" to streamId) as? JsonObject ?: return emptyList()
        val listings = root["epg_listings"] as? JsonArray ?: return emptyList()
        return listings.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val start = obj.string("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val stop = obj.string("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            ArchiveListing(
                start = start * 1000,
                stop = stop * 1000,
                title = decodeBase64Text(obj.string("title")) ?: return@mapNotNull null,
                description = decodeBase64Text(obj.string("description")),
                hasArchive = obj.string("has_archive") == "1",
            )
        }.sortedBy { it.start }
    }

    /** Archive (timeshift) address of a live channel, starting at [start] for [durationMinutes]. */
    fun timeshiftUrl(streamId: String, start: Long, durationMinutes: Int, timezone: String?): String =
        Catchup.xtreamTimeshiftUrl(baseUrl.toString(), source.username, source.password, streamId, start, durationMinutes, timezone)

    /** Full XMLTV guide for the account. */
    fun xmltvUrl(): String =
        baseUrl.newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", source.username)
            .addQueryParameter("password", source.password)
            .build()
            .toString()

    fun liveStreamUrl(streamId: String): String =
        baseUrl.newBuilder()
            .addPathSegment("live")
            .addPathSegment(source.username)
            .addPathSegment(source.password)
            .addPathSegment("$streamId.${source.liveFormat}")
            .build()
            .toString()

    /**
     * Reads a JSON array response one element at a time and keeps only what [map] makes of it, so a
     * list of tens of thousands of films never sits in memory as one big text plus its whole JSON tree
     * (too much for TV sticks). Returns null when the server answers with something other than an array
     * (providers send `{}` or an error object for empty lists).
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T : Any> streamArray(action: String, map: (JsonObject) -> T?): List<T>? = withContext(Dispatchers.IO) {
        http.newCall(request(action)).execute().use { response ->
            checkStatus(response.code, response.isSuccessful)
            val input = java.io.BufferedInputStream(response.body?.byteStream() ?: return@withContext null, 64 * 1024)
            if (firstSignificantByte(input) != '['.code) return@withContext null
            val result = ArrayList<T>()
            try {
                json.decodeToSequence(input, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED).forEach { element ->
                    (element as? JsonObject)?.let(map)?.let(result::add)
                }
            } catch (e: Exception) {
                throw PlaylistException(tx("Sunucu geçerli bir Xtream yanıtı vermedi.", "The server did not send a valid Xtream response."), e)
            }
            result
        }
    }

    /** Skips whitespace and a byte-order mark; returns the next byte without consuming it (-1 at the end). */
    private fun firstSignificantByte(input: java.io.BufferedInputStream): Int {
        while (true) {
            input.mark(1)
            val c = input.read()
            if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code || c == 0xEF || c == 0xBB || c == 0xBF) continue
            input.reset()
            return c
        }
    }

    private fun request(action: String?, vararg params: Pair<String, String>): Request {
        val url = baseUrl.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", source.username)
            .addQueryParameter("password", source.password)
            .apply { if (action != null) addQueryParameter("action", action) }
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return Request.Builder().url(url).header("User-Agent", userAgent).build()
    }

    private fun checkStatus(code: Int, successful: Boolean) {
        if (code == 401 || code == 403) {
            throw PlaylistException(tx("Sunucu erişimi reddetti (HTTP $code). Bilgileri kontrol edin.", "The server refused access (HTTP $code). Check your details."))
        }
        if (!successful) throw PlaylistException(tx("Sunucu hatası: HTTP ", "Server error: HTTP ") + code)
    }

    private suspend fun getJson(action: String?, vararg params: Pair<String, String>): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request(action, *params)).execute().use { response ->
            checkStatus(response.code, response.isSuccessful)
            val body = response.body?.string().orEmpty()
            try {
                json.parseToJsonElement(body)
            } catch (e: Exception) {
                throw PlaylistException(tx("Sunucu geçerli bir Xtream yanıtı vermedi.", "The server did not send a valid Xtream response."), e)
            }
        }
    }

    companion object {
        private val json = Json { isLenient = true }

        /**
         * Accepts what users typically paste: `host:port`, `http://host:port/`,
         * or a full `.../player_api.php?...` / `.../get.php?...` link.
         */
        fun normalizeServer(input: String): HttpUrl? {
            var text = input.trim()
            if (text.isEmpty()) return null
            if (!text.contains("://")) text = "http://$text"
            val url = text.toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
            if (segments.lastOrNull()?.endsWith(".php") == true) segments.removeAt(segments.lastIndex)
            val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
            segments.forEach { builder.addPathSegment(it) }
            if (segments.isNotEmpty()) builder.addPathSegment("")
            return builder.build()
        }
    }
}

/** One entry of a stream's archive guide. Times are epoch milliseconds. */
data class ArchiveListing(val start: Long, val stop: Long, val title: String, val description: String?, val hasArchive: Boolean)

/** Xtream sends guide texts base64-encoded; plain text is passed through. */
internal fun decodeBase64Text(value: String?): String? {
    val text = value?.trim()?.ifEmpty { null } ?: return null
    val decoded = runCatching { okio.ByteString.Companion.run { text.decodeBase64() }?.utf8() }.getOrNull()
    return (decoded ?: text).trim().ifEmpty { null }
}

private fun JsonObject.string(key: String): String? = when (val value = this[key]) {
    null, is JsonNull -> null
    is JsonPrimitive -> value.content
    else -> null
}

/** First non-blank string among [keys]. */
private fun JsonObject.text(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> string(key)?.trim()?.takeIf { it.isNotEmpty() && it != "null" } }

/** `backdrop_path` is usually an array of URLs, sometimes a single string. */
private fun JsonObject.backdrop(): String? = when (val value = this["backdrop_path"]) {
    is JsonArray -> value.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim()?.ifEmpty { null } }
    is JsonPrimitive -> value.content.trim().ifEmpty { null }
    else -> null
}
