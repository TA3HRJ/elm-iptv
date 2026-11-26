package io.github.ta3hrj.elm.data

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.ta3hrj.elm.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crashes on the device so they can be read in Settings without a computer.
 * Release builds are obfuscated; the mapping.txt attached to each GitHub release turns the
 * stack trace back into readable names (retrace).
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 10
    private const val PREF_UNSEEN = "crash_unseen"

    /** [version]: the app version that wrote the report (null for unreadable reports). */
    data class Entry(val file: File, val time: Long, val summary: String, val version: String? = null) {
        /** Written by an older version: the problem may already be fixed. */
        val fromOlderVersion: Boolean get() = version != null && version != BuildConfig.VERSION_NAME
    }

    /** Installs the handler that writes a crash report before the process ends. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Notes an error the app survived (it shows in the error log with "app kept running"), for problems
     * in background work that should not close the app but are worth seeing.
     */
    fun record(context: Context, error: Throwable) {
        runCatching { write(context, Thread.currentThread(), error, fatal = false) }
    }

    private fun write(context: Context, thread: Thread, error: Throwable, fatal: Boolean = true) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val text = buildString {
            appendLine("ELM IPTV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(now)))
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            if (!fatal) appendLine("(uygulama kapanmadı / app kept running)")
            runCatching { appendLine("Memory: " + MemoryLog.snapshot(context)) }
            appendLine()
            append(Log.getStackTraceString(error))
        }
        // The report is sent on by people: logins that an exception quotes (in an address) are hidden.
        File(dir, "crash-$now.txt").writeText(io.github.ta3hrj.elm.core.Redact.text(text))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        // commit(): the process is about to die, apply() might not get written.
        if (fatal) context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_UNSEEN, true).commit()
    }

    fun entries(context: Context): List<Entry> =
        File(context.filesDir, DIR).listFiles().orEmpty()
            .filter { it.name.startsWith("crash-") }
            .sortedByDescending { it.name }
            .map { file ->
                val time = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: file.lastModified()
                val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
                val summary = lines.firstOrNull { it.contains("Exception") || it.contains("Error") || it.contains("Java heap") }?.trim() ?: file.name
                // First line: "ELM IPTV 0.9.12 (27)".
                val version = lines.firstOrNull()?.let { VERSION_LINE.find(it)?.groupValues?.get(1) }
                Entry(file, time, summary.take(160), version)
            }

    private val VERSION_LINE = Regex("""^ELM IPTV (\S+) \(""")

    fun clear(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
        markSeen(context)
    }

    /** True once after a crash, so the next start can mention it. */
    fun hasUnseen(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(PREF_UNSEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_UNSEEN, false).apply()
    }
}
