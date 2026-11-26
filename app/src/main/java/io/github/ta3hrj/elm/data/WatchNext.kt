package io.github.ta3hrj.elm.data

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import io.github.ta3hrj.elm.ui.MainActivity

/**
 * "Continue watching" row on the Android TV / Google TV home screen (Android 8+).
 * Fire TV does not show this row for sideloaded apps; there every call quietly does nothing.
 */
class WatchNext(private val context: Context) {
    private val prefs = context.getSharedPreferences("watch_next", Context.MODE_PRIVATE)

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= 26 &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            !Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

    /** Takes an entry off the Android TV home screen. */
    fun remove(key: String) {
        if (!supported) return
        runCatching {
            val existing = prefs.getLong(key, -1L)
            if (existing >= 0) context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(existing), null, null)
            prefs.edit().remove(key).apply()
        }
    }

    /**
     * Adds or updates an unfinished film/episode; [positionMs] = 0 or finished removes it.
     * The builder setters are public API; lint misreads their @RestrictTo base class (known tvprovider issue).
     */
    @SuppressLint("RestrictedApi")
    fun update(key: String, title: String, subtitle: String, playUri: Uri, positionMs: Long, durationMs: Long) {
        if (!supported) return
        runCatching {
            val finished = durationMs > 0 && durationMs - positionMs < 4 * 60_000L
            val existing = prefs.getLong(key, -1L)
            if (finished || positionMs <= 0) {
                if (existing >= 0) context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(existing), null, null)
                prefs.edit().remove(key).apply()
                return
            }
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(title)
                .setDescription(subtitle)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setLastPlaybackPositionMillis(positionMs.toInt())
                .setDurationMillis(durationMs.toInt())
                .setIntent(Intent(Intent.ACTION_VIEW, playUri, context, MainActivity::class.java))
                .setInternalProviderId(key)
                .build()
            if (existing >= 0) {
                context.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(existing), program.toContentValues(), null, null)
            } else {
                context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
                    ?.let { prefs.edit().putLong(key, ContentUris.parseId(it)).apply() }
            }
        }
    }
}
