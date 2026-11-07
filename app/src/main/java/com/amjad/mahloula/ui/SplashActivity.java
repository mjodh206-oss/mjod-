package com.amjad.mahloula.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.amjad.mahloula.R;
import com.amjad.mahloula.lock.Security;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/** شاشة الإقلاع: الشعار + نسب التطوير. */
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkBars(true);
        LinearLayout root = Ui.v(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 32), Ui.dp(this, 80), Ui.dp(this, 32), Ui.dp(this, 40));

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageDrawable(getDrawable(R.drawable.logo_mark));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 128));
        logo.setLayoutParams(llp);
        root.addView(logo);

        android.widget.TextView name = Ui.txt(this, getString(R.string.app_name), 34, R.color.on_brand, Typeface.BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(this, 18);
        name.setLayoutParams(nlp);
        name.setGravity(Gravity.CENTER);
        root.addView(name);

        android.widget.TextView tag = Ui.txt(this, getString(R.string.app_tagline), 15, R.color.brand_200);
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);

        android.view.View space = new android.view.View(this);
        root.addView(space, new LinearLayout.LayoutParams(0, 0, 1f));

        android.widget.TextView credit = Ui.txt(this, "تم تأليف وتطوير هذا التطبيق بواسطة أمجد حبيب", 12.5f, R.color.brand_200);
        credit.setGravity(Gravity.CENTER);
        root.addView(credit);
        android.widget.TextView ver = Ui.txt(this, "الإصدار 1.0.1", 11, R.color.brand_300);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = Ui.dp(this, 4);
        ver.setLayoutParams(vlp);
        root.addView(ver);

        setContentView(root);

        logo.setScaleX(0.6f); logo.setScaleY(0.6f); logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650).start();
        name.setAlpha(0f); name.setTranslationY(Ui.dp(this, 16));
        name.animate().alpha(1f).translationY(0).setStartDelay(250).setDuration(500).start();
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(450).setDuration(500).start();

        new Handler(Looper.getMainLooper()).postDelayed(this::route, 1400);
    }

    private void route() {
        Prefs p = Prefs.get(this);
        Intent next;
        if (p.firstRun()) next = new Intent(this, OnboardingActivity.class);
        else if (p.lockEnabled() && !Security.sessionValid()
                && (Security.hasBiometric(this) || p.hasPin())) next = new Intent(this, LockActivity.class);
        else next = new Intent(this, MainActivity.class);
        startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
