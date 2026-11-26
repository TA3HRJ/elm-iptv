package io.github.ta3hrj.elm.recordings

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.core.StreamRecorder
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.AppDatabase
import io.github.ta3hrj.elm.data.RecordingEntity
import io.github.ta3hrj.elm.data.RecordingStatus
import io.github.ta3hrj.elm.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules and manages recordings. Each recording is limited to [MAX_MINUTES] minutes and is
 * written by [RecordingService] to the storage volume with the most free space.
 */
class Recordings(private val context: Context, db: AppDatabase) {
    private val dao = db.recordings()
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    /**
     * Schedules a recording; [start] in the past means "now". Returns null with a reason if it cannot.
     */
    suspend fun schedule(
        playlistId: Long,
        channelName: String,
        title: String,
        streamUrl: String,
        userAgent: String,
        start: Long,
        stop: Long,
    ): Pair<RecordingEntity?, String?> {
        val now = System.currentTimeMillis()
        val begin = maxOf(start, now)
        val end = minOf(stop, begin + MAX_MINUTES * 60_000L)
        if (end <= begin) return null to tx("Bu program bitmiş.", "This programme has ended.")
        dao.overlapping(streamUrl, begin, end)?.let { return null to tx("Bu kanal için bu saatlerde zaten bir kayıt var.", "A recording of this channel already covers that time.") }
        val recording = RecordingEntity(
            playlistId = playlistId,
            channelName = channelName,
            title = title,
            streamUrl = streamUrl,
            userAgent = userAgent,
            start = begin,
            stop = end,
            status = RecordingStatus.SCHEDULED,
        )
        val id = dao.insert(recording)
        val saved = recording.copy(id = id)
        if (begin <= now + 5_000) RecordingService.start(context, id) else setAlarm(saved)
        return saved to null
    }

    /** No scheduled or running recording covers that time on the channel. */
    suspend fun isFree(streamUrl: String, start: Long, stop: Long): Boolean = dao.overlapping(streamUrl, start, stop) == null

    /** Cancels a scheduled recording or stops a running one (the part recorded so far is kept). */
    suspend fun cancel(id: Long) {
        val recording = dao.get(id) ?: return
        when (recording.status) {
            RecordingStatus.SCHEDULED -> {
                alarms.cancel(pendingIntent(id))
                dao.update(recording.copy(status = RecordingStatus.CANCELLED))
            }
            RecordingStatus.RECORDING -> RecordingService.stop(context, id)
        }
    }

    suspend fun delete(id: Long) {
        val recording = dao.get(id) ?: return
        if (recording.status == RecordingStatus.SCHEDULED) alarms.cancel(pendingIntent(id))
        if (recording.status == RecordingStatus.RECORDING) RecordingService.stop(context, id)
        recording.filePath?.let { runCatching { File(it).delete() } }
        dao.delete(id)
    }

    /** Puts alarms back after a reboot/update; recordings interrupted by a restart are marked failed. */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (recording in dao.active()) {
            when {
                recording.status == RecordingStatus.RECORDING && !RecordingService.isRunning(recording.id) ->
                    dao.update(recording.copy(status = if (recording.sizeBytes > 0) RecordingStatus.DONE else RecordingStatus.FAILED,
                        error = tx("Kayıt cihaz yeniden başlatılınca kesildi.", "The recording stopped when the device restarted.")))
                recording.status == RecordingStatus.SCHEDULED && recording.stop <= now ->
                    dao.update(recording.copy(status = RecordingStatus.FAILED, error = tx("Kayıt zamanı cihaz kapalıyken geçti.", "The recording time passed while the device was off.")))
                recording.status == RecordingStatus.SCHEDULED && recording.start <= now -> RecordingService.start(context, recording.id)
                recording.status == RecordingStatus.SCHEDULED -> setAlarm(recording)
            }
        }
    }

    private fun setAlarm(recording: RecordingEntity) {
        val intent = pendingIntent(recording.id)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recording.start, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recording.start, intent)
        }
    }

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE + id.toInt(),
        Intent(context, RecordingAlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or Reminders.immutable(),
    )

    /** Storage folders for recordings (internal and removable/USB), the one with most free space first. */
    fun storageDirs(): List<File> =
        context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES).filterNotNull()
            .map { File(it, "ELM IPTV") }
            .ifEmpty { listOf(File(context.filesDir, "recordings")) }
            .sortedByDescending { freeBytes(it) }

    fun freeBytes(dir: File): Long = runCatching {
        dir.mkdirs()
        StatFs(dir.path).availableBytes
    }.getOrElse { 0L }

    companion object {
        const val MAX_MINUTES = 180
        /** Guide times are rarely exact: start a minute early, stop two minutes late. */
        const val START_MARGIN_MS = 60_000L
        const val END_MARGIN_MS = 2 * 60_000L
        val DURATION_CHOICES = listOf(15, 30, 60, 90, 120, 180)
        const val EXTRA_ID = "recording_id"
        private const val REQUEST_BASE = 1_000_000
    }
}

class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Recordings.EXTRA_ID, -1)
        if (id >= 0) RecordingService.start(context, id)
    }
}

/** Foreground service that writes running recordings to disk. */
class RecordingService : Service() {
    private val cancelled: MutableSet<Long> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(Recordings.EXTRA_ID, -1) ?: -1
        goForeground(tx("Kayıt hazırlanıyor…", "Preparing recording…"))
        when (intent?.action) {
            ACTION_STOP -> cancelled += id
            else -> if (id >= 0 && !jobs.containsKey(id)) begin(id)
        }
        if (jobs.isEmpty()) stopSelfSafely()
        return START_NOT_STICKY
    }

    private fun begin(id: Long) {
        val container = applicationContext.container
        val dao = container.database.recordings()
        jobs[id] = container.appScope.launch {
            val recording = dao.get(id)
            if (recording == null || recording.status != RecordingStatus.SCHEDULED) {
                finish(id)
                return@launch
            }
            val dir = container.recordings.storageDirs().first().apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(recording.start))
            val file = File(dir, "${safeName(recording.channelName)}_${safeName(recording.title)}_$stamp.ts")
            var current = recording.copy(status = RecordingStatus.RECORDING, filePath = file.path)
            dao.update(current)
            updateNotification()
            val result = withContext(Dispatchers.IO) {
                FileOutputStream(file).buffered(256 * 1024).use { out ->
                    var lastSaved = 0L
                    StreamRecorder(container.http).record(
                        url = recording.streamUrl,
                        userAgent = recording.userAgent,
                        out = out,
                        until = recording.stop,
                        isCancelled = { id in cancelled },
                        onProgress = { bytes ->
                            if (bytes - lastSaved > 8 * 1024 * 1024) {
                                lastSaved = bytes
                                current = current.copy(sizeBytes = bytes)
                                container.appScope.launch { dao.update(current) }
                            }
                        },
                    )
                }
            }
            val size = file.length()
            dao.update(
                current.copy(
                    status = if (size > 0) RecordingStatus.DONE else RecordingStatus.FAILED,
                    sizeBytes = size,
                    stop = if (id in cancelled) System.currentTimeMillis() else recording.stop,
                    error = result.error ?: if (size == 0L) tx("Yayından veri alınamadı.", "No data received from the stream.") else null,
                ),
            )
            if (size == 0L) file.delete()
            finish(id)
        }
    }

    private fun finish(id: Long) {
        jobs.remove(id)
        cancelled -= id
        if (jobs.isEmpty()) stopSelfSafely() else updateNotification()
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(tx("Kaydediliyor: ${jobs.size} kayıt", "Recording: ${jobs.size}")))
    }

    private fun goForeground(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tx("Kayıtlar", "Recordings"), NotificationManager.IMPORTANCE_LOW),
            )
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_stat_reminder).setContentTitle("ELM IPTV").setContentText(text).setOngoing(true).build()
    }

    private val unsafeChars = Regex("[^\\p{L}\\p{N}._ -]")
    private fun safeName(text: String) = text.replace(unsafeChars, "").trim().take(40).ifEmpty { "recording" }

    companion object {
        private const val CHANNEL_ID = "recordings"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "io.github.ta3hrj.elm.STOP_RECORDING"
        private val jobs = ConcurrentHashMap<Long, Job>()

        fun isRunning(id: Long) = jobs.containsKey(id)

        /** A recording is being written: previews must not open a second connection that could cost it its stream. */
        fun anyRunning() = jobs.isNotEmpty()

        fun start(context: Context, id: Long) {
            val intent = Intent(context, RecordingService::class.java).putExtra(Recordings.EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context, id: Long) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP).putExtra(Recordings.EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
