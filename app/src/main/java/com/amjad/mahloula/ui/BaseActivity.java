package com.amjad.mahloula.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import com.amjad.mahloula.R;
import com.amjad.mahloula.util.Prefs;

import java.util.Locale;

/** أساس كل الشاشات: لغة، وضع ليلي، واتجاه RTL مضبوط يدويًا. */
public abstract class BaseActivity extends Activity {

    private String appliedLang;
    private int appliedNight;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(applyLocale(base));
    }

    public static Context applyLocale(Context base) {
        String lang = Prefs.get(base).language();
        Locale target;
        if (lang.equals("en")) target = Locale.ENGLISH;
        else if (lang.equals("system")) target = Locale.getDefault();
        else target = new Locale("ar");
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(target);
        cfg.setLayoutDirection(target);
        return base.createConfigurationContext(cfg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeMode(this);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);
        appliedLang = Prefs.get(this).language();
        appliedNight = nightValue(this);
        getWindow().getDecorView().setLayoutDirection(
                Prefs.get(this).language().equals("en") ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs p = Prefs.get(this);
        if (!p.language().equals(appliedLang) || nightValue(this) != appliedNight) {
            recreate();
        }
    }

    public static void applyThemeMode(Context c) {
        int mode = Prefs.get(c).themeMode();
        android.app.UiModeManager um = (android.app.UiModeManager) c.getSystemService(UI_MODE_SERVICE);
        if (um == null) return;
        try {
            if (mode == 1) um.setNightMode(android.app.UiModeManager.MODE_NIGHT_NO);
            else if (mode == 2) um.setNightMode(android.app.UiModeManager.MODE_NIGHT_YES);
            else um.setNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO);
        } catch (Exception ignored) {}
    }

    public static int nightValue(Context c) {
        return c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    public boolean isNight() {
        return nightValue(this) == Configuration.UI_MODE_NIGHT_YES;
    }

    protected void darkBars(boolean darkBg) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            View dec = getWindow().getDecorView();
            int f = dec.getSystemUiVisibility();
            if (darkBg || isNight()) f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            dec.setSystemUiVisibility(f);
        }
    }
}
