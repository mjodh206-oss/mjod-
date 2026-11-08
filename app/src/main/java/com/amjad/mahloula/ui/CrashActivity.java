package com.amjad.mahloula.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.util.Ui;

/** شاشة تقرير العطل: تظهر بدل حوار النظام الغامض، مع زر نسخ التقرير. */
public class CrashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean ar = !com.amjad.mahloula.util.Prefs.get(this).language().equals("en");
        String trace = getIntent() != null ? getIntent().getStringExtra("trace") : null;
        if (trace == null) trace = com.amjad.mahloula.util.CrashGuard.lastReport(this);
        if (trace == null) trace = "(لا يوجد تقرير)";

        LinearLayout root = Ui.v(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 36), Ui.dp(this, 20), Ui.dp(this, 24));

        root.addView(Ui.txt(this, ar ? "حدث عطل غير متوقع 🛠" : "Unexpected crash 🛠",
                22, R.color.text_primary, Typeface.BOLD));
        TextView sub = Ui.txt(this, ar
                ? "اعتذارنا لك. انسخ التقرير أرسله للمطوّر أمجد حبيب وسيُصلح فورًا، ثم اضغط متابعة لفتح التطبيق من جديد."
                : "Sorry! Copy this report and send it to the developer Amjad Habib; then continue to relaunch.",
                14, R.color.text_secondary);
        sub.setLineSpacing(0, 1.35f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 8);
        sub.setLayoutParams(slp);
        root.addView(sub);

        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.round(Ui.color(this, R.color.surface_2), 14, this));
        sv.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.topMargin = Ui.dp(this, 14);
        sv.setLayoutParams(svlp);
        TextView body = Ui.txt(this, trace, 11, R.color.text_secondary);
        body.setTextDirection(TextView.TEXT_DIRECTION_LTR);
        sv.addView(body);
        root.addView(sv);

        final String finalTrace = trace;
        TextView copy = UiKit.button(this, ar ? "نسخ التقرير 📋" : "Copy report 📋", 0, true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(this, 14);
        copy.setLayoutParams(clp);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("mahloula-crash", finalTrace));
            Ui.toast(this, ar ? "نُسخ التقرير ✔" : "Report copied ✔");
        });
        root.addView(copy);

        TextView relaunch = UiKit.button(this, ar ? "إعادة فتح مَحلولة" : "Relaunch Mahloula", 0, false);
        relaunch.setBackground(Ui.round(Ui.color(this, R.color.surface_3), 15, this));
        relaunch.setTextColor(Ui.color(this, R.color.text_primary));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(this, 10);
        relaunch.setLayoutParams(rlp);
        relaunch.setOnClickListener(v -> {
            startActivity(Intent2());
            finish();
        });
        root.addView(relaunch);
        setContentView(root);
    }

    private android.content.Intent Intent2() {
        return new android.content.Intent(this, SplashActivity.class);
    }
}
