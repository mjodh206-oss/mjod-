package com.amjad.mahloula.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يستقبل التذكير اليومي وإعادة التشغيل لإعادة الجدولة. */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? "" : String.valueOf(intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Notifier.ensureChannel(c);
            if (com.amjad.mahloula.util.Prefs.get(c).remindersOn()) Notifier.scheduleDaily(c);
            return;
        }
        Notifier.postDaily(c);
        if (com.amjad.mahloula.util.Prefs.get(c).remindersOn()) Notifier.scheduleDaily(c);
    }
}
