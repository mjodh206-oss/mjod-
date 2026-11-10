package com.amjad.mahloula.notify;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.amjad.mahloula.R;
import com.amjad.mahloula.brain.Advisor;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.MainActivity;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;

import java.util.Calendar;
import java.util.List;

/** تذكيرات وتنبيهات محلية 100% — بدون أي خادم. */
public final class Notifier {

    private Notifier() {}

    public static final String CHANNEL = "mahloula_v3";

    public static final String CHANNEL_ALARM = "mahloula_alarm_v1";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                Prefs.get(c).language().equals("en") ? "Mahloula alerts & reminders" : "تنبيهات مَحلولة وتذكيراتها",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(Prefs.get(c).language().equals("en")
                ? "A gentle daily nudge to log your spending"
                : "تنبيه يومي لطيف لتسجيل مصاريفك ومتابعة ميزانيتك");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 320, 140, 320, 140, 520});
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.enableLights(true);
        ch.setLightColor(0xFFF25C05);
        if (Prefs.get(c).soundOn()) {
            android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(android.net.Uri.parse(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + c.getPackageName() + "/" + R.raw.chime), aa);
        }
        nm.createNotificationChannel(ch);
        if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
            NotificationChannel al = new NotificationChannel(CHANNEL_ALARM,
                    Prefs.get(c).language().equals("en") ? "90% budget danger alarm" : "إنذار خطر: ٩٠٪ من الميزانية",
                    NotificationManager.IMPORTANCE_HIGH);
            al.setDescription(Prefs.get(c).language().equals("en")
                    ? "Fires once a month when spending crosses 90% of budget"
                    : "يعمل مرة شهريًا عند تجاوز ٩٠٪ من الميزانية");
            al.enableVibration(true);
            al.setVibrationPattern(new long[]{0, 500, 120, 500, 120, 700});
            al.setBypassDnd(true);
            al.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            al.enableLights(true);
            al.setLightColor(0xFFE11D3C);
            if (Prefs.get(c).soundOn()) {
                android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                al.setSound(android.net.Uri.parse(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
                        + "://" + c.getPackageName() + "/" + R.raw.alarm), aa);
            }
            nm.createNotificationChannel(al);
        }
    }

    /** إشعار إنذار الخطر (قناة مستقلة برنة الصفير). */
    public static void showAlarm(Context c, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2003, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL_ALARM)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(android.net.Uri.parse(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + c.getPackageName() + "/" + R.raw.alarm));
        }
        nm.notify(778, b.build());
    }

    /** يعيد بناء القنوات لتطبيق تغيير الصوت فورًا. */
    public static void recreateChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.deleteNotificationChannel(CHANNEL);
                nm.deleteNotificationChannel(CHANNEL_ALARM);
            }
        }
        ensureChannel(c);
    }

    public static void scheduleDaily(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("com.amjad.mahloula.DAILY");
        PendingIntent pi = PendingIntent.getBroadcast(c, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, Prefs.get(c).reminderHour());
        at.set(Calendar.MINUTE, 0);
        at.set(Calendar.SECOND, 0);
        if (at.before(Calendar.getInstance())) at.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    public static void cancelDaily(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** يبني نص التذكير اليومي الذكي من بياناتك الحقيقية. */
    public static void postDaily(Context c) {
        if (!Prefs.get(c).remindersOn()) return;
        ensureChannel(c);
        boolean ar = !Prefs.get(c).language().equals("en");
        Repo r = Repo.get(c);
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        long todayStart = Fmt.startOfDay(now).getTimeInMillis();
        double today = 0;
        int count = 0;
        for (Models.Tx t : r.txRange(todayStart, System.currentTimeMillis(), 0, null, null)) {
            today += t.amount;
            count++;
        }
        String title, body;
        if (count == 0) {
            title = ar ? "مَحلولة: يومك المالي بانتظار تسجيلك ✍️" : "Mahloula: your day awaits logging ✍️";
            body = ar ? "لم تسجل أي مصروف اليوم. دقيقة واحدة الآن تحفظ دقة ميزانيتك ومؤشر صحتك المالية."
                    : "No expense logged today. One minute now keeps your budget accurate.";
        } else {
            title = ar ? "سجلت اليوم " + Fmt.money(c, today) : "Logged today: " + Fmt.money(c, today);
            double budget = Prefs.get(c).monthlyBudget();
            if (budget > 0) {
                double left = budget - mt.expense;
                body = ar ? "مصروف الشهر حتى الآن " + Fmt.money(c, mt.expense) + " — المتبقي من ميزانيتك " + Fmt.money(c, Math.max(0, left)) + "."
                        : "Month spend " + Fmt.money(c, mt.expense) + " — budget left " + Fmt.money(c, Math.max(0, left)) + ".";
            } else {
                body = ar ? "أحسنت على الالتزام بالتسجيل. افتح التطبيق لمراجعة فئاتك." : "Nice consistency. Open the app to review categories.";
            }
        }
        List<Advisor.Insight> ins = Advisor.scan(c);
        for (Advisor.Insight in : ins) {
            if (in.level == 2) {
                body = (ar ? "⚠️ تنبيه مهم: " : "⚠️ Alert: ") + in.title + " — " + body;
                break;
            }
        }
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            double week = 0;
            for (Models.Tx t : r.txRange(now.getTimeInMillis() - 7L * 86400000L, now.getTimeInMillis(), 0, null, null))
                week += t.amount;
            body = (ar ? "📅 ملخص الأسبوع: صرفت " + Fmt.money(c, week) + ". " : "📅 Week: " + Fmt.money(c, week) + ". ") + body;
            body = body + (ar ? "\n🎯 مهمة أسبوعك: " + Advisor.dailyCoach(c).title : "\n🎯 Weekly mission: " + Advisor.dailyCoach(c).title);
        }
        show(c, title, body);
    }


    public static void show(Context c, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2002, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_piggy)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (Prefs.get(c).soundOn()) {
            if (Build.VERSION.SDK_INT < 26) {
                b.setSound(android.net.Uri.parse(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
                        + "://" + c.getPackageName() + "/" + R.raw.chime));
            }
            b.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
        }
        nm.notify(777, b.build());
    }
}
