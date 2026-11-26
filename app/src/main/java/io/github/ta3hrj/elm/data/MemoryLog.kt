package io.github.ta3hrj.elm.data

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import io.github.ta3hrj.elm.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Notes the app's memory use every few minutes while the process lives (also in the background), so
 * that slowdowns or closes after long watching can be traced back: a steadily growing number points
 * to a leak, a sudden jump to one action. The file is downloaded from a phone like the error log.
 *
 * One line per sample, e.g.
 * `2026-11-11 22:10:00  up 3h05m  pss 182  java 45/256  native 31  sys 912/1906  low no  threads 48  plays 17  retries 2`
 * (MB; pss is the app's whole share of memory, java the Java heap used/limit, sys the device's free/total;
 * plays are channels and films started by the user, retries automatic reconnects of dropped streams).
 */
object MemoryLog {
    const val FILE = "memory.txt"
    const val INTERVAL_MS = 5 * 60_000L

    /** About ten days of samples at five-minute steps; older lines are dropped. */
    const val KEEP_LINES = 3000

    /** Java heap share above which a warning goes to the error log (once per run). */
    const val HEAP_WARNING = 0.85

    private val plays = AtomicInteger()
    private val retries = AtomicInteger()
    private val warned = AtomicBoolean()
    private val started = AtomicBoolean()

    data class Summary(val samples: Int, val lastPssMb: Int?, val peakPssMb: Int?, val firstPssMb: Int?, val lastLine: String?)

    /** Where the file work runs; set by [start]. */
    @Volatile
    private var scope: CoroutineScope? = null

    /** Starts sampling; called once from the Application. */
    fun start(context: Context, scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        this.scope = scope
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            note(app, startHeader(app))
            while (true) {
                sample(app)
                delay(INTERVAL_MS)
            }
        }
    }

    /** Counts a channel / film start; the count since app start appears in each sample. */
    fun countPlay() {
        plays.incrementAndGet()
    }

    /** Counts an automatic reconnect of a dropped stream (not a channel change by the user). */
    fun countRetry() {
        retries.incrementAndGet()
    }

    /**
     * The system asked the app to free memory; worth seeing next to the samples. Called on the main thread,
     * so the slow part (reading the memory figures, writing the file) runs in the background: these calls
     * come exactly when the device is short of memory, and the screen must not stutter on top of it.
     */
    fun onTrimMemory(context: Context, level: Int) {
        val app = context.applicationContext
        val at = timestamp()
        val work = scope
        if (work == null) {
            Thread { noteTrim(app, level, at) }.start()
        } else {
            work.launch(Dispatchers.IO) { noteTrim(app, level, at) }
        }
    }

    /** Something the player did on its own that is worth seeing next to the samples; written in the background. */
    fun event(context: Context, text: String) {
        val app = context.applicationContext
        val line = "${timestamp()}  ! $text"
        val work = scope
        if (work == null) {
            Thread { runCatching { note(app, line) } }.start()
        } else {
            work.launch(Dispatchers.IO) { runCatching { note(app, line) } }
        }
    }

    private fun noteTrim(context: Context, level: Int, at: String) {
        val name = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "running moderate"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "running low"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "running critical"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> return // just went to the background; not a memory sign
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "background"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "moderate"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "complete"
            else -> "level $level"
        }
        runCatching { note(context, "$at  ! system asks to free memory: $name") }
        runCatching { sample(context) }
    }

    /** One line describing memory now; also written into crash reports. */
    fun snapshot(context: Context): String {
        val runtime = Runtime.getRuntime()
        val javaUsed = (runtime.totalMemory() - runtime.freeMemory()).mb()
        val javaMax = runtime.maxMemory().mb()
        val native = Debug.getNativeHeapAllocatedSize().mb()
        val pss = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024
        val system = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(system)
        val up = (SystemClock.elapsedRealtime() - processStart()) / 60_000
        return "${timestamp()}  up ${up / 60}h${"%02d".format(up % 60)}m  pss $pss  java $javaUsed/$javaMax  native $native" +
            "  sys ${system.availMem.mb()}/${system.totalMem.mb()}  low ${if (system.lowMemory) "yes" else "no"}" +
            "  threads ${Thread.activeCount()}  plays ${plays.get()}  retries ${retries.get()}"
    }

    private fun sample(context: Context) {
        note(context, snapshot(context))
        val runtime = Runtime.getRuntime()
        val share = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / runtime.maxMemory()
        if (share > HEAP_WARNING && warned.compareAndSet(false, true)) {
            CrashLog.record(context, MemoryWarning("Java heap ${(share * 100).toInt()}% full · ${snapshot(context)}"))
        }
    }

    private fun startHeader(context: Context): String {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return "${timestamp()}  === ELM IPTV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started · " +
            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "heap limit ${Runtime.getRuntime().maxMemory().mb()} MB · memory class ${manager?.memoryClass ?: 0} MB" +
            (if (manager?.isLowRamDevice == true) " · low-RAM device" else "") + " ==="
    }

    /** Appends a line and keeps the file at [KEEP_LINES] lines (trimmed in steps so it is not rewritten each time). */
    @Synchronized
    fun note(context: Context, line: String) {
        val file = File(context.filesDir, FILE)
        if (lineCount < 0) lineCount = if (file.exists()) file.useLines { it.count() } else 0
        file.appendText(line + "\n")
        lineCount++
        if (lineCount > KEEP_LINES) {
            val kept = file.readLines().takeLast(KEEP_LINES * 9 / 10)
            file.writeText(kept.joinToString("\n", postfix = "\n"))
            lineCount = kept.size
        }
    }

    /** Lines in the file; -1 until first counted. */
    private var lineCount = -1

    fun text(context: Context): String =
        runCatching { File(context.filesDir, FILE).readText() }.getOrDefault("")

    /** Since the last app start: how many samples, the app's memory first, now and at its highest. */
    fun summary(context: Context): Summary {
        val lines = text(context).lines()
        val run = lines.drop(lines.indexOfLast { it.contains("=== ELM IPTV") }.coerceAtLeast(0))
        val pss = run.mapNotNull { PSS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return Summary(pss.size, pss.lastOrNull(), pss.maxOrNull(), pss.firstOrNull(), run.lastOrNull { PSS.containsMatchIn(it) })
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        lineCount = 0
    }

    private val PSS = Regex("""\bpss (\d+)\b""")

    private fun processStart(): Long =
        if (Build.VERSION.SDK_INT >= 24) Process.getStartElapsedRealtime() else loadedAt

    // Close to the process start: the object is first used while the app starts.
    private val loadedAt = SystemClock.elapsedRealtime()

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())

    private fun Long.mb() = (this / (1024 * 1024)).toInt()

    /** Not a crash: the Java heap was nearly full. Shows in the error log with the memory numbers. */
    class MemoryWarning(message: String) : Exception(message)
}
