package com.amjad.mahloula;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.amjad.mahloula.notify.Notifier;
import com.amjad.mahloula.util.Prefs;

/** تطبيق مَحلولة — إدارة مصاريفك الشخصية باحترافية، بدون إنترنت وبصمة أمان. */
public class MahloulaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        com.amjad.mahloula.util.CrashGuard.install(this);
        Notifier.ensureChannel(this);
        if (Prefs.get(this).remindersOn()) Notifier.scheduleDaily(this);
    }
}
