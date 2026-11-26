package io.github.ta3hrj.elm.reminders

import io.github.ta3hrj.elm.core.tx

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.AppDatabase
import io.github.ta3hrj.elm.data.ReminderEntity
import io.github.ta3hrj.elm.ui.timeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Programme reminders: stored in the database and fired by AlarmManager one minute before the start. */
class Reminders(private val context: Context, db: AppDatabase) {
    private val dao = db.reminders()
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Keys ("streamUrl|start") of the playlist's reminders, for marking programmes in the guide. */
    fun observeKeys(playlistId: Long): Flow<Set<String>> =
        dao.observeForPlaylist(playlistId).map { list -> list.mapTo(HashSet()) { key(it.streamUrl, it.start) } }

    /** Adds or removes a reminder; returns true if it is now set. */
    suspend fun toggle(playlistId: Long, streamUrl: String, channelName: String, title: String, start: Long, stop: Long): Boolean {
        val existing = dao.find(playlistId, streamUrl, start)
        if (existing != null) {
            dao.delete(existing.id)
            cancel(existing.id)
            return false
        }
        val reminder = ReminderEntity(
            playlistId = playlistId,
            streamUrl = streamUrl,
            channelName = channelName,
            title = title,
            start = start,
            stop = stop,
        )
        val id = dao.insert(reminder)
        if (id > 0) schedule(reminder.copy(id = id))
        return true
    }

    /** Re-creates alarms after a reboot or app update and drops reminders that have passed. */
    suspend fun rescheduleAll() {
        dao.deleteOlderThan(System.currentTimeMillis() - 5 * 60_000L)
        dao.all().forEach { schedule(it) }
    }

    suspend fun get(id: Long) = dao.get(id)

    suspend fun remove(id: Long) {
        dao.delete(id)
        cancel(id)
    }

    private fun schedule(reminder: ReminderEntity) {
        val at = maxOf(reminder.start - LEAD_MS, System.currentTimeMillis() + 1_000)
        val intent = pendingIntent(reminder.id)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        when {
            Build.VERSION.SDK_INT >= 23 && exactAllowed -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            Build.VERSION.SDK_INT >= 23 -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else -> alarms.setExact(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun cancel(id: Long) = alarms.cancel(pendingIntent(id))

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or immutable(),
    )

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val CHANNEL_ID = "reminders"
        private const val LEAD_MS = 60_000L

        fun key(streamUrl: String, start: Long) = "$streamUrl|$start"

        fun immutable() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }
}

/** Fires when a reminder is due: shows the pop-up and a notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        val container = context.container
        container.appScope.launch {
            try {
                val reminder = container.reminders.get(id) ?: return@launch
                val popup = ReminderActivity.intent(context, reminder.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Allowed while the app is in front (and always before Android 10); the notification covers the rest.
                runCatching { context.startActivity(popup) }
                notify(context, reminder, popup)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, reminder: ReminderEntity, popup: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(Reminders.CHANNEL_ID, tx("Program hatırlatıcıları", "Programme reminders"), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val content = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            popup,
            PendingIntent.FLAG_UPDATE_CURRENT or Reminders.immutable(),
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, Reminders.CHANNEL_ID)
        } else {
            android.app.Notification.Builder(context).setPriority(android.app.Notification.PRIORITY_HIGH)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(tx("Başlıyor: ${reminder.title}", "Starting: ${reminder.title}"))
            .setContentText("${reminder.channelName} · ${timeRange(reminder.start, reminder.stop)}")
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(reminder.id.toInt(), notification) }
    }
}

/** Alarms are cleared on reboot; this puts them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                context.container.reminders.rescheduleAll()
                context.container.recordings.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
