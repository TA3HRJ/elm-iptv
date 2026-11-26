package io.github.ta3hrj.elm.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.github.ta3hrj.elm.core.tx

/**
 * Opens a stream in another installed player (VLC, MX Player, Kodi…). The User-Agent goes along in the
 * extras those players read, since many providers refuse other User-Agents.
 */
object ExternalPlayer {
    /** The intent that opens the stream in another player, or null if none is installed. */
    fun intent(context: Context, url: String, title: String, userAgent: String, positionMs: Long = 0): Intent? {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(url), "video/*")
            .putExtra("title", title)
            // MX Player: alternating header names and values.
            .putExtra("headers", arrayOf("User-Agent", userAgent))
            // VLC
            .putExtra("http-user-agent", userAgent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (positionMs > 0) {
            intent.putExtra("position", positionMs.toInt()) // MX Player and VLC
            intent.putExtra("from_start", false)
        }
        val handlers = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }.filter { it.activityInfo.packageName != context.packageName }
        return when (handlers.size) {
            0 -> null
            1 -> intent.setClassName(handlers[0].activityInfo.packageName, handlers[0].activityInfo.name)
            else -> Intent.createChooser(intent, tx("Harici oynatıcı", "External player")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val NOT_FOUND: String get() = tx("Harici oynatıcı bulunamadı. VLC veya MX Player kurun.", "No external player found. Install VLC or MX Player.")

    /** Returns null on success, otherwise a message for the user. */
    fun open(context: Context, url: String, title: String, userAgent: String, positionMs: Long = 0): String? {
        val intent = intent(context, url, title, userAgent, positionMs) ?: return NOT_FOUND
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            tx("Harici oynatıcı açılamadı.", "Could not open the external player.")
        } catch (e: SecurityException) {
            tx("Harici oynatıcı açılamadı.", "Could not open the external player.")
        }
    }
}
