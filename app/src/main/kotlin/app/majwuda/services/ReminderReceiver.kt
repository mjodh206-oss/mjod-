package app.majwuda.services

import app.majwuda.MajwudaApp
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.majwuda.R
import app.majwuda.data.db.MajwudaDatabase
import app.majwuda.data.db.Recurrence
import app.majwuda.data.db.ReminderEntity
import app.majwuda.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Fires reminder notifications and handles their actions. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = MajwudaDatabase.get(context)
                val reminder = db.reminderDao().get(reminderId) ?: return@launch
                when (action) {
                    ACTION_FIRE -> fire(context, db, reminder)
                    ACTION_DONE -> markDone(db, reminder)
                    ACTION_SNOOZE -> snooze(context, db, reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(context: Context, db: MajwudaDatabase, reminder: ReminderEntity) {
        postNotification(context, reminder)
        // Recurrence: schedule the next occurrence instead of closing the series.
        val recurrence = try {
            Recurrence.valueOf(reminder.recurrence)
        } catch (_: Exception) {
            Recurrence.NONE
        }
        if (recurrence != Recurrence.NONE) {
            val zone = ZoneId.systemDefault()
            val next: Long = when (recurrence) {
                Recurrence.DAILY -> reminder.dueAt + 86_400_000L
                Recurrence.WEEKLY -> reminder.dueAt + 7 * 86_400_000L
                Recurrence.MONTHLY -> Instant.ofEpochMilli(reminder.dueAt).atZone(zone).plusMonths(1).toInstant().toEpochMilli()
                Recurrence.YEARLY -> Instant.ofEpochMilli(reminder.dueAt).atZone(zone).plusYears(1).toInstant().toEpochMilli()
                Recurrence.NONE -> reminder.dueAt
            }
            db.reminderDao().delete(reminder)
            val requestCode = Random.nextInt(1, 900_000_000)
            db.reminderDao().insert(
                reminder.copy(id = 0, dueAt = next, snoozedUntil = null, done = false, doneAt = null, requestCode = requestCode, createdAt = System.currentTimeMillis()),
            )
            ReminderScheduler(context, db).rescheduleAll()
        }
    }

    private suspend fun markDone(db: MajwudaDatabase, reminder: ReminderEntity) {
        db.reminderDao().markDone(reminder.id, System.currentTimeMillis())
    }

    private suspend fun snooze(context: Context, db: MajwudaDatabase, reminder: ReminderEntity) {
        val until = System.currentTimeMillis() + 10 * 60_000L
        db.reminderDao().snooze(reminder.id, until)
        ReminderScheduler(context, db).scheduleById(reminder.id)
    }

    private fun postNotification(context: Context, reminder: ReminderEntity) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val fullIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, reminder.requestCode, fullIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun actionIntent(action: String, offset: Int): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            }
            return PendingIntent.getBroadcast(
                context,
                reminder.requestCode * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val whenText = DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(reminder.dueAt))
        val notification = NotificationCompat.Builder(context, MajwudaApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText("${reminder.title} · $whenText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(reminder.body, reminder.title).joinToString(" — ")))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_reminder_done), actionIntent(ACTION_DONE, 1))
            .addAction(0, context.getString(R.string.notif_reminder_snooze), actionIntent(ACTION_SNOOZE, 2))
            .build()
        manager.notify(reminder.id.toInt().coerceAtLeast(1), notification)
    }

    companion object {
        const val ACTION_FIRE = "app.majwuda.action.REMINDER_FIRE"
        const val ACTION_DONE = "app.majwuda.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.majwuda.action.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

/** Re-arms pending reminders after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                ReminderScheduler(context, MajwudaDatabase.get(context)).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
