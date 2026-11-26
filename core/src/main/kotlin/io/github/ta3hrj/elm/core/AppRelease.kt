package io.github.ta3hrj.elm.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** The newest published app version, as the update server's `latest.json` describes it. */
data class AppRelease(
    /** e.g. "0.9.20". */
    val version: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 of the APK; the download is refused when it does not match. */
    val sha256: String,
    /** The release text, shortened for a dialog. */
    val notes: String,
) {
    companion object {
        /**
         * Reads the update server's `latest.json`:
         * `{"version":"0.9.29","apk":"ELM-IPTV-0.9.29.apk","size":7000000,"sha256":"…","notes":"…"}`.
         * `apk` may be relative to [manifestUrl]. Null when something required is missing or wrong.
         */
        fun parse(json: String, manifestUrl: String): AppRelease? {
            val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
            val version = root.string("version")?.removePrefix("v")?.takeIf { Versions.parse(it) != null } ?: return null
            val apk = root.string("apk")?.takeIf { it.endsWith(".apk") && !it.contains("-debug") } ?: return null
            val apkUrl = manifestUrl.toHttpUrlOrNull()?.resolve(apk) ?: return null
            if (!apkUrl.isHttps && manifestUrl.toHttpUrlOrNull()?.isHttps == true) return null
            val sha256 = root.string("sha256")?.lowercase()?.takeIf { SHA256.matches(it) } ?: return null
            return AppRelease(
                version = version,
                apkUrl = apkUrl.toString(),
                apkName = apkUrl.pathSegments.lastOrNull().orEmpty(),
                apkSize = root["size"]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() } ?: 0L,
                sha256 = sha256,
                notes = root.string("notes").orEmpty().trim().take(600),
            )
        }

        /** Reads [manifestUrl]. Null when there is no answer (no network, server down) or it cannot be read. */
        suspend fun fetchLatest(client: OkHttpClient, manifestUrl: String): AppRelease? =
            withContext(Dispatchers.IO) {
                val url = manifestUrl.toHttpUrlOrNull() ?: return@withContext null
                // No cached copy: a new release must show up as soon as it is published.
                val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) null else parse(response.body?.string().orEmpty(), url.toString())
                    }
                }.getOrNull()
            }

        private val SHA256 = Regex("[0-9a-f]{64}")

        private fun JsonObject.string(key: String): String? = (this[key])?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    }
}

/** Version names such as "0.9.19" or "1.0", compared number by number. */
object Versions {
    fun parse(name: String): List<Int>? {
        val parts = name.trim().removePrefix("v").substringBefore('-').split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** True when [candidate] is newer than [current] ("1.0" is newer than "0.9.19"; "1.0" equals "1.0.0"). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
