package dev.lasq.fawri.platform

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import dev.lasq.fawri.R
import dev.lasq.fawri.app
import dev.lasq.fawri.ui.MainActivity

/** Only installed on API 26–28. A foreground service cannot unlock clipboard access on Android 10+. */
class LegacyMonitor : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 29 || !app.prefs.enabled.value || !app.integrity.first) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = system<NotificationManager>()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "الحفظ التلقائي", NotificationManager.IMPORTANCE_LOW),
        )
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutable)
        val pause =
            PendingIntent.getService(
                this,
                1,
                Intent(this, LegacyMonitor::class.java).setAction("pause"),
                immutable,
            )
        startForeground(
            41,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("لصق فوري يحفظ ما تنسخه")
                .setContentText("الحفظ على جهازك فقط")
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, "إيقاف الحفظ", pause).build())
                .build(),
        )
        if (intent?.action == "pause" || !canShow(this)) {
            app.recorder.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        app.recorder.legacyRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        app.recorder.legacyRunning(false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "archive_legacy"

        private fun canShow(c: Context) =
            c.system<NotificationManager>().let {
                it.areNotificationsEnabled() &&
                    it.getNotificationChannel(CHANNEL)?.importance !=
                        NotificationManager.IMPORTANCE_NONE
            }

        fun ensure(c: Context) {
            if (
                Build.VERSION.SDK_INT >= 29 ||
                    !c.app.prefs.enabled.value ||
                    !c.app.integrity.first ||
                    c.app.recorder.state.value == Recorder.State.INSTANT
            )
                return
            if (!canShow(c)) {
                c.app.recorder.problem.value =
                    "الحفظ عند الفتح متاح. اسمح بإشعار التطبيق لتشغيل الحفظ في الخلفية."
                return
            }
            try {
                c.startForegroundService(Intent(c, LegacyMonitor::class.java))
            } catch (_: IllegalStateException) {
                c.app.recorder.problem.value = "افتح السجل لاستئناف الحفظ في الخلفية."
            }
        }

        fun stop(c: Context) {
            if (Build.VERSION.SDK_INT < 29) c.stopService(Intent(c, LegacyMonitor::class.java))
        }
    }
}
