package io.github.ta3hrj.elm.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore of playlists (with their logins), favorites, hidden and locked categories,
 * hidden channels, the parental PIN and settings, as a JSON file.
 *
 * Files are written to the app's folder on every storage volume (reachable with adb or a file
 * manager) and, on Android 10+, also to Downloads/ELM IPTV.
 */
class Backup(private val context: Context, private val db: AppDatabase, private val http: OkHttpClient) {

    data class Location(val label: String, val file: File?)

    fun backupDirs(): List<File> =
        context.getExternalFilesDirs("backups").filterNotNull().ifEmpty { listOf(File(context.filesDir, "backups")) }

    /** Existing backup files, newest first. */
    fun listBackups(): List<File> = backupDirs()
        .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".json") }?.toList().orEmpty() }
        .plus(publicDownloads()?.listFiles { f -> f.name.endsWith(".json") }?.toList().orEmpty())
        .distinctBy { it.name + it.length() }
        .sortedByDescending { it.lastModified() }

    /** Saves a backup; with a [password] the file is encrypted (see [BackupCrypto]). */
    suspend fun export(password: String? = null): List<String> = withContext(Dispatchers.IO) {
        val plain = build().toString(2)
        val json = if (password.isNullOrEmpty()) plain else BackupCrypto.encrypt(plain, password)
        val name = "elm-iptv-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".json"
        val written = ArrayList<String>()
        backupDirs().firstOrNull()?.let { dir ->
            dir.mkdirs()
            File(dir, name).writeText(json)
            written += File(dir, name).path
        }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ELM IPTV")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    written += "Download/ELM IPTV/$name"
                }
            }
        }
        prune()
        written
    }

    /** Deletes a backup by file name, in the app folder and in Downloads/ELM IPTV. */
    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        for (dir in backupDirs() + listOfNotNull(publicDownloads())) {
            val file = File(dir, name)
            if (file.exists() && file.delete()) deleted = true
        }
        if (Build.VERSION.SDK_INT >= 29) {
            // Files this installation wrote to Downloads; others are not visible and stay.
            runCatching {
                val removed = context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "/ELM IPTV%"),
                )
                if (removed > 0) deleted = true
            }
        }
        deleted
    }

    /** Keeps the newest [KEEP] backups; older ones would only pile up. */
    private suspend fun prune() {
        listBackups().map { it.name }.distinct().drop(KEEP).forEach { delete(it) }
    }

    suspend fun restoreFile(file: File, password: String? = null): String = restore(withContext(Dispatchers.IO) { file.readText() }, password)

    /** Restores backup text received another way (e.g. from a phone over the home network). */
    suspend fun restoreText(text: String, password: String? = null): String = restore(text, password)

    /** The current backup as JSON text, without saving a file. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) { build().toString(2) }

    /** A file chosen with the system file picker (it can read backups a previous installation left in Downloads). */
    suspend fun restoreUri(uri: android.net.Uri, password: String? = null): String = restore(
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw PlaylistException(tx("Dosya okunamadı.", "The file could not be read."))
        },
        password,
    )

    suspend fun restoreUrl(url: String, password: String? = null): String {
        val text = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url.trim()).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlaylistException(tx("Yedek indirilemedi: HTTP ", "Backup download failed: HTTP ") + response.code)
                response.body!!.string()
            }
        }
        return restore(text, password)
    }

    private suspend fun build(): JSONObject {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val playlists = JSONArray()
        val profiles = db.profiles().all()
        for (p in db.playlists().observeAll().first()) {
            val flags = db.flags().observeGroupFlags(p.id).first()
            val hidden = db.flags().observeHiddenChannels(p.id).first()
            // The main profile's data stays at the top level, as in backups made before profiles existed.
            val entry = personalData(JSONObject(), p.id, ProfileManager.MAIN_ID)
                .put("name", p.name).put("type", p.type).put("url", p.url)
                .put("username", p.username).put("password", p.password)
                .put("userAgent", p.userAgent).put("liveFormat", p.liveFormat).put("epgUrl", p.epgUrl)
                .put("groupFlags", JSONArray(flags.map {
                    JSONObject().put("scope", it.scope).put("name", it.name).put("locked", it.locked).put("hidden", it.hidden)
                }))
                .put("hiddenChannels", JSONArray(hidden.map { JSONObject().put("streamUrl", it.streamUrl).put("name", it.name) }))
                .put("epgOverrides", JSONArray(db.epg().overrides(p.id).map { JSONObject().put("streamUrl", it.streamUrl).put("epgKey", it.epgKey) }))
            val others = profiles.filter { it.id != ProfileManager.MAIN_ID }
                .map { personalData(JSONObject().put("profile", it.id), p.id, it.id) }
            if (others.isNotEmpty()) entry.put("profileData", JSONArray(others))
            playlists.put(entry)
        }
        val settings = JSONObject()
        for (key in SETTING_KEYS) prefs.all[key]?.let { settings.put(key, it) }
        return JSONObject()
            .put("format", "elm-iptv-backup")
            .put("version", 2)
            .put("createdAt", System.currentTimeMillis())
            .put("profiles", JSONArray(profiles.map {
                JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("kids", it.kids)
                    .put("allowedLive", it.allowedLive).put("allowedMovies", it.allowedMovies).put("allowedSeries", it.allowedSeries)
            }))
            .put("playlists", playlists)
            .put("settings", settings)
    }

    /** Favorites, own lists and watchlist of one profile. */
    private suspend fun personalData(target: JSONObject, playlistId: Long, profileId: Long): JSONObject {
        val favorites = db.channels().observeFavoriteUrls(playlistId, profileId).first()
        val watchlist = db.library().watchlist(playlistId, profileId).map { JSONObject().put("kind", it.kind).put("providerId", it.providerId) }
        val ownLists = db.lists().lists(playlistId, profileId).map { list ->
            JSONObject().put("name", list.name).put("channels", JSONArray(db.lists().itemUrls(list.id)))
        }
        return target
            .put("favorites", JSONArray(favorites))
            .put("favoriteLists", JSONArray(ownLists))
            .put("watchlist", JSONArray(watchlist))
    }

    private suspend fun restorePersonalData(o: JSONObject, playlistId: Long, profileId: Long) {
        o.optJSONArray("favorites")?.let { a ->
            for (j in 0 until a.length()) db.channels().addFavorite(FavoriteEntity(profileId, playlistId, a.getString(j)))
        }
        o.optJSONArray("watchlist")?.let { a ->
            for (j in 0 until a.length()) {
                val w = a.getJSONObject(j)
                db.library().addToWatchlist(WatchlistEntity(profileId, playlistId, w.getString("kind"), w.getString("providerId")))
            }
        }
        o.optJSONArray("favoriteLists")?.let { a ->
            val lists = db.lists()
            for (j in 0 until a.length()) {
                val l = a.getJSONObject(j)
                val name = l.optString("name").ifBlank { continue }
                val listId = lists.lists(playlistId, profileId).firstOrNull { it.name == name }?.id
                    ?: lists.insertList(
                        FavoriteListEntity(profileId = profileId, playlistId = playlistId, name = name, position = lists.nextPosition(playlistId, profileId)),
                    )
                val urls = l.optJSONArray("channels") ?: continue
                for (k in 0 until urls.length()) lists.addItem(FavoriteListItemEntity(listId, urls.getString(k)))
            }
        }
    }

    /** Creates the backup's profiles that do not exist yet (same name and kind); returns old id -> local id. */
    private suspend fun restoreProfiles(root: JSONObject): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        result[ProfileManager.MAIN_ID] = ProfileManager.MAIN_ID
        val list = root.optJSONArray("profiles") ?: return result
        val existing = db.profiles().all()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val oldId = o.optLong("id")
            if (oldId == ProfileManager.MAIN_ID) continue
            val name = o.optString("name")
            val kids = o.optBoolean("kids")
            val local = existing.firstOrNull { it.id != ProfileManager.MAIN_ID && it.name == name && it.kids == kids }?.id
                ?: db.profiles().insert(
                    ProfileEntity(
                        name = name, color = o.optInt("color", ProfileManager.DEFAULT_COLOR), kids = kids,
                        allowedLive = o.optNullableString("allowedLive"),
                        allowedMovies = o.optNullableString("allowedMovies"),
                        allowedSeries = o.optNullableString("allowedSeries"),
                        position = existing.size + i,
                    ),
                )
            result[oldId] = local
        }
        return result
    }

    /** Adds playlists that do not exist yet and merges favorites, flags and hidden channels. */
    /** @throws BackupPasswordNeeded for an encrypted backup without [password]. */
    private suspend fun restore(encrypted: String, password: String?): String {
        val text = when {
            !BackupCrypto.isEncrypted(encrypted) -> encrypted
            password.isNullOrEmpty() -> throw BackupPasswordNeeded()
            else -> withContext(Dispatchers.Default) { BackupCrypto.decrypt(encrypted, password) }
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null || root.optString("format") != "elm-iptv-backup") {
            throw PlaylistException(tx("Bu dosya bir ELM IPTV yedeği değil.", "This file is not an ELM IPTV backup."))
        }
        val profileIds = restoreProfiles(root)
        val existing = db.playlists().observeAll().first()
        val list = root.optJSONArray("playlists") ?: JSONArray()
        var added = 0
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val type = o.optString("type")
            val url = o.optString("url")
            val user = o.optNullableString("username")
            val match = existing.firstOrNull { it.type == type && it.url == url && it.username == user }
            val id = match?.id ?: db.playlists().insert(
                PlaylistEntity(
                    name = o.optString("name").ifBlank { url },
                    type = type,
                    url = url,
                    username = user,
                    password = o.optNullableString("password"),
                    userAgent = o.optNullableString("userAgent"),
                    liveFormat = o.optString("liveFormat", "ts"),
                    epgUrl = o.optNullableString("epgUrl"),
                ),
            ).also { added++ }
            restorePersonalData(o, id, ProfileManager.MAIN_ID)
            o.optJSONArray("profileData")?.let { a ->
                for (j in 0 until a.length()) {
                    val data = a.getJSONObject(j)
                    val profileId = profileIds[data.optLong("profile")] ?: continue
                    restorePersonalData(data, id, profileId)
                }
            }
            o.optJSONArray("groupFlags")?.let { a ->
                for (j in 0 until a.length()) {
                    val f = a.getJSONObject(j)
                    db.flags().saveGroupFlag(GroupFlagEntity(id, f.getString("scope"), f.getString("name"), f.optBoolean("locked"), f.optBoolean("hidden")))
                }
            }
            o.optJSONArray("epgOverrides")?.let { a ->
                for (j in 0 until a.length()) {
                    val e = a.getJSONObject(j)
                    db.epg().saveOverride(EpgOverrideEntity(id, e.getString("streamUrl"), e.optString("epgKey")))
                }
            }
            o.optJSONArray("hiddenChannels")?.let { a ->
                for (j in 0 until a.length()) {
                    val h = a.getJSONObject(j)
                    db.flags().hideChannel(HiddenChannelEntity(id, h.getString("streamUrl"), h.optString("name")))
                }
            }
        }
        root.optJSONObject("settings")?.let { settings ->
            val editor = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            for (key in SETTING_KEYS) {
                when (val value = settings.opt(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
        }
        return tx("Geri yüklendi: $added yeni liste. Yeni listeler şimdi indiriliyor.", "Restored: $added new playlists. They are downloading now.")
    }

    private fun publicDownloads(): File? = runCatching {
        @Suppress("DEPRECATION")
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ELM IPTV").takeIf { it.canRead() }
    }.getOrNull()

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    companion object {
        const val KEEP = 10

        /** Settings carried in a backup (the PIN hash and salt included, so locks keep working). */
        val SETTING_KEYS = listOf(
            "resume_last_channel", "resize_mode", "pin_salt", "pin_hash", "language", "home_view",
            "channel_order", "group_order", "subtitle_size", "subtitle_color", "subtitle_background",
            "auto_refresh_hours", "protect_settings", "adult_lock", "frame_rate_matching", "buffer_size", "idle_hours",
            "ask_profile_on_start", "ui_size", "vod_order", "background_update", "audio_delay_ms",
        )
    }
}
