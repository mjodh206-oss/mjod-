package app.majwuda.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.majwuda.data.db.MajwudaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schedules, reschedules and cancels reminder alarms. Everything survives reboot
 * (see [BootReceiver]) and app updates.
 */
class ReminderScheduler(private val context: Context, private val db: MajwudaDatabase) {

    suspend fun scheduleById(id: Long) {
        val reminder = db.reminderDao().get(id) ?: return
        schedule(reminder.requestCode, reminder.id, effectiveTime(reminder))
    }

    suspend fun cancelById(id: Long) {
        db.reminderDao().get(id)?.let { cancel(it.requestCode, it.id) }
    }

    /** Re-arms every pending reminder (used after boot and app update). */
    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val pending = db.reminderDao().nextPendingAfter(0L)
        pending.forEach { schedule(it.requestCode, it.id, effectiveTime(it)) }
    }

    private fun effectiveTime(reminder: app.majwuda.data.db.ReminderEntity): Long =
        maxOf(reminder.dueAt, reminder.snoozedUntil ?: 0L).coerceAtLeast(System.currentTimeMillis() + 1_000L)

    private fun schedule(requestCode: Int, reminderId: Long, atMillis: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        val canExact = if (Build.VERSION.SDK_INT >= 31) {
            try {
                alarm.canScheduleExactAlarms()
            } catch (_: Throwable) {
                false
            }
        } else {
            true
        }
        try {
            if (canExact) {
                if (Build.VERSION.SDK_INT >= 23) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                } else {
                    @Suppress("DEPRECATION")
                    alarm.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
                }
            } else {
                if (Build.VERSION.SDK_INT >= 23) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
                }
            }
        } catch (_: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
                }
            } catch (_: Exception) {
                // Nothing else we can do; the reminder will not fire.
            }
        }
    }

    private fun cancel(requestCode: Int, reminderId: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        pending.cancel()
    }
}
