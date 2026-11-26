package io.github.ta3hrj.elm.desktop.data

import io.github.ta3hrj.elm.core.PlaylistSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A playlist the user added. Logins stay on this computer, in a file only the user can read. */
@Serializable
data class SavedPlaylist(
    val id: Long,
    val name: String,
    /** [TYPE_M3U] or [TYPE_XTREAM]. */
    val type: String,
    /** M3U: address or local file path. */
    val url: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String? = null,
    /** A guide (XMLTV) address of the user's own; empty uses what the playlist announces. */
    val guideUrl: String = "",
    /** Xtream live format: "ts" or "m3u8". */
    val liveFormat: String = "ts",
) {
    val isXtream: Boolean get() = type == TYPE_XTREAM

    fun source(): PlaylistSource = if (isXtream) {
        PlaylistSource.Xtream(server, username, password, userAgent?.ifBlank { null }, liveFormat)
    } else {
        PlaylistSource.M3u(url, userAgent?.ifBlank { null })
    }

    companion object {
        const val TYPE_M3U = "m3u"
        const val TYPE_XTREAM = "xtream"
    }
}

@Serializable
data class DesktopSettings(
    /** "system", "tr" or "en". */
    val language: String = "system",
    val activePlaylistId: Long = 0,
    val volume: Int = 80,
    /** VLC network cache in milliseconds: more rides out a weak connection, less starts faster. */
    val networkCacheMs: Int = 3_000,
    /** Let VLC use the graphics card for decoding. */
    val hardwareDecoding: Boolean = true,
    /** Hide categories that look like adult content. */
    val hideAdult: Boolean = true,
)

/** Everything the user chose, in one small file. Channel lists and the guide are caches beside it. */
@Serializable
data class AppData(
    val playlists: List<SavedPlaylist> = emptyList(),
    val settings: DesktopSettings = DesktopSettings(),
    /** Playlist id → favorite stream addresses, in the user's order. */
    val favorites: Map<Long, List<String>> = emptyMap(),
    /** Playlist id → recently watched stream addresses, newest first. */
    val recent: Map<Long, List<String>> = emptyMap(),
    /** Film / episode address → position in milliseconds, to continue where it stopped. */
    val resume: Map<String, Long> = emptyMap(),
) {
    fun favoritesOf(playlistId: Long): List<String> = favorites[playlistId].orEmpty()
    fun recentOf(playlistId: Long): List<String> = recent[playlistId].orEmpty()
}

/**
 * Files of the desktop app: `%APPDATA%\ELM IPTV` on Windows, `$XDG_CONFIG_HOME/elm-iptv` (usually
 * `~/.config/elm-iptv`) on Linux; caches under `cache/`.
 */
class Storage(val dir: File) {
    val cacheDir: File = File(dir, "cache")
    private val dataFile = File(dir, "elm-iptv.json")

    init {
        dir.mkdirs()
        cacheDir.mkdirs()
        ownerOnly(dir)
    }

    fun load(): AppData = runCatching {
        if (!dataFile.isFile) AppData() else json.decodeFromString(AppData.serializer(), dataFile.readText())
    }.getOrElse {
        // A damaged file is kept for inspection and the app starts empty instead of not at all.
        runCatching { dataFile.copyTo(File(dir, "elm-iptv.broken.json"), overwrite = true) }
        AppData()
    }

    @Synchronized
    fun save(data: AppData) = writeAtomically(dataFile, json.encodeToString(AppData.serializer(), data).toByteArray())

    fun <T> writeCache(name: String, serializer: KSerializer<T>, value: T) {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.encodeToString(serializer, value).toByteArray()) }
        }.toByteArray()
        writeAtomically(File(cacheDir, name), bytes)
    }

    fun <T> readCache(name: String, serializer: KSerializer<T>): T? {
        val file = File(cacheDir, name)
        if (!file.isFile) return null
        return runCatching {
            GZIPInputStream(file.inputStream().buffered()).use { json.decodeFromString(serializer, it.readBytes().decodeToString()) }
        }.getOrNull()
    }

    fun cacheAge(name: String, now: Long = System.currentTimeMillis()): Long? =
        File(cacheDir, name).takeIf { it.isFile }?.let { now - it.lastModified() }

    fun deleteCaches(prefix: String) {
        cacheDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    fun clearCaches() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Written next to the target and moved over it: a crash never leaves half a file. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        ownerOnly(tmp)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** On Linux the files hold logins: readable by the user only. Windows profile folders are private already. */
    private fun ownerOnly(file: File) {
        runCatching {
            val perms = if (file.isDirectory) {
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            } else {
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            }
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun defaultDir(env: (String) -> String? = System::getenv, home: String = System.getProperty("user.home")): File {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            return if (windows) {
                File(env("APPDATA")?.ifBlank { null } ?: File(home, "AppData/Roaming").path, "ELM IPTV")
            } else {
                File(env("XDG_CONFIG_HOME")?.ifBlank { null } ?: File(home, ".config").path, "elm-iptv")
            }
        }
    }
}
