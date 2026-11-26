package io.github.ta3hrj.elm.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.ta3hrj.elm.BuildConfig
import io.github.ta3hrj.elm.core.AppRelease
import io.github.ta3hrj.elm.core.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * "A new version is out": reads `latest.json` on the update server, downloads its APK, checks its
 * SHA-256, that it is the same app signed with the same key and newer, and hands it to the system
 * installer.
 *
 * The server address comes from the build ([BuildConfig.UPDATE_URL], set by CI from `ELM_UPDATE_URL`);
 * builds without it never check.
 */
class AppUpdater(
    private val context: Context,
    private val http: OkHttpClient,
    private val prefs: SharedPreferences,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    /** Where `latest.json` is; empty in builds made without an update server. */
    private val manifestUrl: String = BuildConfig.UPDATE_URL,
    /** Debug builds are a separate app ("ELM IPTV (test)"); they are not updated from releases. */
    val enabled: Boolean = !BuildConfig.DEBUG && manifestUrl.isNotBlank(),
) {
    sealed interface Download {
        data class Ready(val file: File) : Download
        data class Failed(val reason: String) : Download
    }

    sealed interface Check {
        data class Newer(val release: AppRelease) : Check
        data object UpToDate : Check
        /** No answer: no network, or the update server is down. */
        data object Unreachable : Check
    }

    /** Compares the latest published release with this app. */
    suspend fun check(): Check {
        if (!enabled) return Check.Unreachable
        val release = AppRelease.fetchLatest(http, manifestUrl) ?: return Check.Unreachable
        prefs.edit().putLong(PREF_CHECKED_AT, System.currentTimeMillis()).apply()
        return if (Versions.isNewer(release.version, currentVersion)) Check.Newer(release) else Check.UpToDate
    }

    /** At most once a day on its own; [AppUpdater.check] directly for the Settings button. */
    fun dueForCheck(now: Long = System.currentTimeMillis()): Boolean =
        enabled && now - prefs.getLong(PREF_CHECKED_AT, 0L) > CHECK_EVERY_MS

    val lastCheckedAt: Long get() = prefs.getLong(PREF_CHECKED_AT, 0L)

    /** "Later" on the start-up question: asked again for this version only after a few days. */
    fun postpone(version: String) {
        prefs.edit().putString(PREF_POSTPONED_VERSION, version).putLong(PREF_POSTPONED_AT, System.currentTimeMillis()).apply()
    }

    fun postponed(version: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getString(PREF_POSTPONED_VERSION, null) == version && now - prefs.getLong(PREF_POSTPONED_AT, 0L) < POSTPONE_MS

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): Download = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ELM-IPTV-${release.version}.apk")
        val result = runCatching {
            http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching Download.Failed("HTTP ${response.code}")
                val body = response.body ?: return@runCatching Download.Failed("empty")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                var done = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (release.apkSize > 0 && done != release.apkSize) return@runCatching Download.Failed("size $done / ${release.apkSize}")
                if (digest.digest().toHex() != release.sha256) return@runCatching Download.Failed("checksum")
                verify(file)?.let { return@runCatching Download.Failed(it) }
                Download.Ready(file)
            }
        }.getOrElse { Download.Failed(it.message ?: it.javaClass.simpleName) }
        if (result is Download.Failed) file.delete()
        result
    }

    /** Null when [file] is a newer build of this app signed with the same key; otherwise why not. */
    fun verify(file: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "not an app package"
        if (archive.packageName != context.packageName) return "another app (${archive.packageName})"
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (versionCode(archive) <= versionCode(installed)) return "not newer"
        if (signatures(archive).isEmpty() || signatures(archive) != signatures(installed)) return "signed with another key"
        return null
    }

    /** Whether Android lets this app start an installation (Android 8+: "Install unknown apps"). */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the system installer for [file]; false when no installer could be opened. */
    fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens the screen where "Install unknown apps" is allowed for this app, where the device has one. */
    fun openInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val exact = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(exact) }.isSuccess
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun signatures(info: PackageInfo): Set<String> {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return raw.orEmpty().map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).toHex() }.toSet()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val DIR = "updates"
        private const val PREF_CHECKED_AT = "update_checked_at"
        private const val PREF_POSTPONED_VERSION = "update_postponed_version"
        private const val PREF_POSTPONED_AT = "update_postponed_at"
        private const val CHECK_EVERY_MS = 20L * 60 * 60 * 1000
        private const val POSTPONE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
