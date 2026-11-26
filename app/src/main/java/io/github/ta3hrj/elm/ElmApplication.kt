package io.github.ta3hrj.elm

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.PlaylistLoader
import io.github.ta3hrj.elm.data.AppDatabase
import io.github.ta3hrj.elm.data.AppSettings
import io.github.ta3hrj.elm.data.CrashLog
import io.github.ta3hrj.elm.data.Backup
import io.github.ta3hrj.elm.data.WatchNext
import io.github.ta3hrj.elm.data.GuideRepository
import io.github.ta3hrj.elm.data.ParentalLock
import io.github.ta3hrj.elm.data.ProfileManager
import io.github.ta3hrj.elm.data.VodRepository
import io.github.ta3hrj.elm.recordings.Recordings
import io.github.ta3hrj.elm.recordings.SeriesRecording
import io.github.ta3hrj.elm.reminders.Reminders
import io.github.ta3hrj.elm.data.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ElmApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        applyLanguage(this)
        container = AppContainer(this)
        io.github.ta3hrj.elm.data.DailyUpdateWorker.schedule(this, container.settings.backgroundUpdate.value)
        io.github.ta3hrj.elm.data.MemoryLog.start(this, container.appScope)
        // Alarms do not survive app updates or force-stops; put reminders back on every start.
        container.appScope.launch {
            container.reminders.rescheduleAll()
            container.recordings.rescheduleAll()
            runCatching { container.seriesRecording.apply() }
            runCatching { container.repository.fillSearchIndex() }
            if (!container.prefs.getBoolean(PREF_OWN_ORDER_SEEDED, false)) {
                runCatching { container.repository.seedOwnOrder() }
                container.prefs.edit().putBoolean(PREF_OWN_ORDER_SEEDED, true).apply()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        io.github.ta3hrj.elm.data.MemoryLog.onTrimMemory(this, level)
    }

    /**
     * Channel logos and posters use the same HTTP client, so they carry the same neutral User-Agent, but
     * with short timeouts: a dead logo server should give way to the initials quickly.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            container.http.newBuilder()
                .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
        // Many logo servers forbid caching; logos rarely change, so they are kept anyway and the lists
        // show them at once on the next start (and without a connection).
        .respectCacheHeaders(false)
        // Coil sizes its memory cache from the large-heap class (a fifth of 384 MB on a Fire TV stick), far
        // more than logos and posters need on a device that often has only 300 MB free while a stream plays.
        // Pictures that drop out come back from the disk cache.
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES).build() }
        .crossfade(true)
        .build()
}

/** Simple manual dependency container shared by the activities. */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        // Requests without an explicit User-Agent (e.g. images) get the neutral default instead of "okhttp/x.y".
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("User-Agent", PlaylistRepository.DEFAULT_USER_AGENT).build())
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val database: AppDatabase = AppDatabase.create(context)

    /** Lives as long as the process; used for work that must outlive a screen (guide downloads). */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // Background work runs while the user watches: an unexpected error there is written to the
            // error log instead of closing the app in the middle of a programme.
            kotlinx.coroutines.CoroutineExceptionHandler { _, error -> CrashLog.record(context.applicationContext, error) },
    )

    private val loader = PlaylistLoader(http, PlaylistRepository.DEFAULT_USER_AGENT)

    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val settings = AppSettings(prefs)

    val parentalLock = ParentalLock(prefs)

    val profiles = ProfileManager(database, prefs, parentalLock, appScope)

    val repository = PlaylistRepository(database, loader, settings, profiles)

    val guide = GuideRepository(database, loader, appScope, settings, http)

    val vod = VodRepository(database, http, loader, appScope, profiles)

    val reminders = Reminders(context.applicationContext, database)

    val recordings = Recordings(context.applicationContext, database)

    val seriesRecording = SeriesRecording(database, recordings, repository).also { series ->
        guide.onUpdated = { series.apply() }
    }

    val backup = Backup(context.applicationContext, database, http)

    val backupTransfer = io.github.ta3hrj.elm.data.BackupTransfer(context.applicationContext, backup, appScope)

    val watchNext = WatchNext(context.applicationContext)

    val updater = io.github.ta3hrj.elm.data.AppUpdater(context.applicationContext, http, prefs)
}

val Context.container: AppContainer get() = (applicationContext as ElmApplication).container

const val PREF_LANGUAGE = "language"
private const val IMAGE_MEMORY_CACHE_BYTES = 32 * 1024 * 1024
private const val PREF_OWN_ORDER_SEEDED = "own_order_seeded"

/** Applies the saved language choice (Sistem / Türkçe / English) to all texts. */
fun applyLanguage(context: Context) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val choice = runCatching { Lang.Choice.valueOf(prefs.getString(PREF_LANGUAGE, null) ?: "SYSTEM") }.getOrDefault(Lang.Choice.SYSTEM)
    Lang.apply(choice)
}
