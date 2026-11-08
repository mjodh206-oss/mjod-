package com.amjad.mahloula.util;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * الصندوق الأسود: يلتقط أي انهيار غير متوقع، يحفظ التقرير في ملف،
 * ويفتح شاشة تعرضه مع زر نسخ — ليصلنا سبب أي عطل بدقة بدل تخمينه.
 */
public final class CrashGuard implements Thread.UncaughtExceptionHandler {

    private final Context app;
    private final Thread.UncaughtExceptionHandler prev;

    private CrashGuard(Context app) {
        this.app = app;
        this.prev = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context app) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashGuard(app));
    }

    public static File reportFile(Context c) {
        return new File(c.getFilesDir(), "last_crash.txt");
    }

    public static String lastReport(Context c) {
        File f = reportFile(c);
        if (!f.exists()) return null;
        try {
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int got = 0;
            while (got < b.length) {
                int r = in.read(b, got, b.length - got);
                if (r <= 0) break;
                got += r;
            }
            in.close();
            return new String(b, 0, got, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("مَحلولة crash report — " + new java.util.Date());
            pw.println("thread: " + t.getName());
            e.printStackTrace(pw);
            String trace = sw.toString();
            PrintWriter out = new PrintWriter(reportFile(app), "UTF-8");
            out.write(trace);
            out.close();
            Intent i = new Intent(app, com.amjad.mahloula.ui.CrashActivity.class);
            i.putExtra("trace", trace);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            app.startActivity(i);
        } catch (Throwable ignored) {
        }
        if (prev != null) {
            try { prev.uncaughtException(t, e); } catch (Throwable ignored) {}
        }
        Process.killProcess(Process.myPid());
        System.exit(10);
    }
}
