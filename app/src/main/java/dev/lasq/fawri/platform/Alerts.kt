package dev.lasq.fawri.platform

import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import dev.lasq.fawri.R
import dev.lasq.fawri.ui.MainActivity

object Alerts {
    const val RUN = "local_status"
    const val ASK = "local_confirm"
    const val SERVICE = 31
    const val PROMPT = 32
    const val PAUSED = 33

    fun setup(c: Context) {
        c.system<NotificationManager>()
            .createNotificationChannels(
                listOf(
                    NotificationChannel(RUN, "حالة الخدمة", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(ASK, "تأكيد اللصق", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "زر لصق دون إظهار أي نص من الحافظة"
                    },
                ),
            )
    }

    fun open(c: Context) =
        PendingIntent.getActivity(
            c,
            0,
            Intent(c, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun action(c: Context, a: String, token: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            0,
            Intent(c, Control::class.java)
                .setAction(a)
                .setData(Uri.parse("lasq://local/$a/$token"))
                .putExtra("token", token),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun base(c: Context, ch: String) =
        Notification.Builder(c, ch)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(Color.rgb(99, 74, 222))
            .setContentIntent(open(c))
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setLocalOnly(true)

    fun running(c: Context): Notification =
        base(c, RUN)
            .setContentTitle("لصق فوري قيد التشغيل")
            .setContentText("يعمل فقط في التطبيقات التي سمحت بها.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "إيقاف", action(c, "stop")).build())
            .build()

    fun paused(c: Context) {
        c.system<NotificationManager>()
            .notify(
                PAUSED,
                base(c, RUN)
                    .setContentTitle("توقفت خدمة لصق فوري")
                    .setContentText("لا تتم مراقبة الحافظة الآن.")
                    .addAction(
                        Notification.Action.Builder(
                                null,
                                "تشغيل",
                                if (c.devLocked()) open(c) else action(c, "toggle"),
                            )
                            .build(),
                    )
                    .build(),
            )
    }

    private fun Context.devLocked() = (applicationContext as dev.lasq.fawri.LasqApp).prefs.value.lock

    fun ask(c: Context, token: Long) {
        c.system<NotificationManager>()
            .notify(
                PROMPT,
                base(c, ASK)
                    .setContentTitle("لصق في الحقل المحدد؟")
                    .setContentText("اضغط للصق العنصر المنسوخ. لن نعرض محتواه هنا.")
                    .setTimeoutAfter(45_000)
                    .setOnlyAlertOnce(true)
                    .addAction(
                        Notification.Action.Builder(null, "الصق", action(c, "confirm", token)).build(),
                    )
                    .build(),
            )
    }

    fun hide(c: Context, id: Int) {
        c.system<NotificationManager>().cancel(id)
    }
}
