package com.amjad.mahloula.notify;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/**
 * إنذار الـ90٪: ملصق تحذير بملء الشاشة يظهر 5 ثوانٍ ثم يختفي،
 * مع رنة خطر مصممة خصيصًا وتعليق صوتي عربي، وإشعار خارجي بقناة إنذار مستقلة.
 * يعمل مرة واحدة كل شهر كحد أقصى.
 */
public final class Warn90 {

    private Warn90() {}

    public static boolean crossed(double before, double after, double budget) {
        return budget > 0 && before < budget * 0.9 && after >= budget * 0.9;
    }

    public static void fire(Activity a) {
        Prefs p = Prefs.get(a);
        java.util.Calendar now = java.util.Calendar.getInstance();
        int ym = now.get(java.util.Calendar.YEAR) * 12 + now.get(java.util.Calendar.MONTH);
        if (p.lastWarnMonth() == ym) return;
        p.lastWarnMonth(ym);
        boolean ar = !p.language().equals("en");

        // ---------- الملصق بملء الشاشة ----------
        final android.view.ViewGroup root = a.findViewById(android.R.id.content);
        FrameLayout ov = new FrameLayout(a);
        ov.setBackgroundColor(0xF2140A04);
        LinearLayout col = Ui.v(a);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(a, 26), Ui.dp(a, 46), Ui.dp(a, 26), Ui.dp(a, 40));

        ImageView poster = new ImageView(a);
        poster.setImageResource(R.drawable.warn_poster);
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        poster.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(poster);

        TextView title = Ui.txt(a, ar ? "وصلت لصرف عالي جدًا!" : "Very high spending!",
                29, R.color.on_dark, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(a, 10);
        title.setLayoutParams(tlp);
        col.addView(title);

        TextView body = Ui.txt(a, ar
                ? "تجاوزت ٩٠٪ من ميزانيتك الشهرية.\nأوقف الصرف غير الضروري الآن، وراجع خطتك في خانة تعلّم."
                : "You passed 90% of your monthly budget.\nStop non-essentials now and review your plan.",
                15.5f, R.color.brand_200);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.35f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(a, 8);
        body.setLayoutParams(blp);
        col.addView(body);

        TextView hint = Ui.txt(a, ar ? "يُغلق تلقائيًا بعد ٥ ثوانٍ" : "Auto-closes in 5 seconds",
                12, R.color.text_tertiary);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = Ui.dp(a, 10);
        hint.setLayoutParams(hlp);
        col.addView(hint);

        ov.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        ov.setAlpha(0f);
        ov.animate().alpha(1f).setDuration(220).start();
        ov.postDelayed(() -> ov.animate().alpha(0f).setDuration(420)
                .withEndAction(() -> root.removeView(ov)).start(), 5000);

        // ---------- رنة الخطر ثم التعليق الصوتي ----------
        try {
            MediaPlayer alarm = MediaPlayer.create(a, R.raw.alarm);
            if (alarm != null) {
                alarm.setOnCompletionListener(m -> {
                    m.release();
                    try {
                        MediaPlayer voice = MediaPlayer.create(a, R.raw.warn_voice);
                        if (voice != null) {
                            voice.setOnCompletionListener(MediaPlayer::release);
                            voice.start();
                        }
                    } catch (Throwable ignored) {}
                });
                alarm.start();
            }
        } catch (Throwable ignored) {}

        // ---------- إشعار خارجي بقناة الإنذار ----------
        Notifier.showAlarm(a,
                ar ? "🚨 تجاوزت ٩٠٪ من ميزانيتك" : "🚨 90% of budget crossed",
                ar ? "صرفك الشهرية بلغ مستوى خطرًا عاليًا. أوقف غير الضروري الآن وراجع الخطة."
                   : "Monthly spending reached a high-risk level. Stop non-essentials now.");
    }
}
