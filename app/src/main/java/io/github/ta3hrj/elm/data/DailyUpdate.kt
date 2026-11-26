package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ta3hrj.elm.container
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Updates channels and the TV guide of every playlist once a day, also when the app is not opened, so
 * series recordings pick up new episodes and the guide is current when the app starts. Films and series
 * are left to the app (their lists are large and only needed while browsing).
 */
class DailyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val playlists = container.repository.observePlaylists().first()
        var failed = 0
        for (playlist in playlists) {
            runCatching {
                container.repository.refresh(playlist.id)
                container.repository.applyAdultLock(playlist.id, container.parentalLock.hasPin())
                // Newly locked categories leave no trace in "recently watched" / "continue watching".
                container.database.lists().purgePrivateRecent(playlist.id)
                container.vod.purgePrivateContinue(playlist.id).forEach { container.watchNext.remove(it) }
            }.onFailure { failed++ }
            runCatching { container.guide.refreshNow(playlist.id) }.onFailure { failed++ }
        }
        runCatching { container.seriesRecording.apply() }
        runCatching { container.repository.fillSearchIndex() }
        // A run where everything failed (no internet at that moment) is tried again later the same day.
        return if (playlists.isNotEmpty() && failed >= playlists.size * 2) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "daily_update"

        /** Turns the daily update on or off (kept across restarts by WorkManager). */
        fun schedule(context: Context, enabled: Boolean) {
            // Without WorkManager (it failed to start) the app still works; the daily update just does not run.
            val work = runCatching { WorkManager.getInstance(context) }
                .onFailure { CrashLog.record(context, it) }
                .getOrNull() ?: return
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(24, TimeUnit.HOURS, 4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
