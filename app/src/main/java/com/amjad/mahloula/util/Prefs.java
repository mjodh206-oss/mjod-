package com.amjad.mahloula.util;

import android.content.Context;
import android.content.SharedPreferences;

/** تفضيلات التطبيق — مصدر الحقيقة الوحيد للإعدادات. */
public final class Prefs {

    private static Prefs sInstance;

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("mahloula_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (sInstance == null) sInstance = new Prefs(c);
        return sInstance;
    }

    // ---------- أول تشغيل ----------
    public boolean firstRun() { return sp.getBoolean("first_run", true); }
    public void firstRunDone() { sp.edit().putBoolean("first_run", false).apply(); }

    // ---------- العملة والأرقام ----------
    public String currency() { return "ل.س"; }  // مثبّتة: الليرة السورية فقط
    public void currency(String v) { /* مثبّتة على ل.س */ }

    public boolean easternDigits() { return sp.getBoolean("eastern_digits", true); }
    public void easternDigits(boolean v) { sp.edit().putBoolean("eastern_digits", v).apply(); }

    // ---------- الميزانية والدخل ----------
    public double monthlyBudget() { return sp.getFloat("monthly_budget", 0f); }
    public void monthlyBudget(double v) { sp.edit().putFloat("monthly_budget", (float) v).apply(); }

    public double monthlyIncome() { return sp.getFloat("monthly_income", 0f); }
    public void monthlyIncome(double v) { sp.edit().putFloat("monthly_income", (float) v).apply(); }

    // ---------- الأمان ----------
    public boolean lockEnabled() { return sp.getBoolean("lock_enabled", false); }
    public void lockEnabled(boolean v) { sp.edit().putBoolean("lock_enabled", v).apply(); }

    public boolean biometricEnabled() { return sp.getBoolean("bio_enabled", true); }
    public void biometricEnabled(boolean v) { sp.edit().putBoolean("bio_enabled", v).apply(); }

    /** بصمة فقط بدون رمز احتياطي (حسب طلب المستخدم). */
    public boolean biometricOnly() { return sp.getBoolean("bio_only", true); }
    public void biometricOnly(boolean v) { sp.edit().putBoolean("bio_only", v).apply(); }

    public String pinHash() { return sp.getString("pin_hash", null); }
    public void pinHash(String v) { sp.edit().putString("pin_hash", v).apply(); }

    public String pinSalt() { return sp.getString("pin_salt", null); }
    public void pinSalt(String v) { sp.edit().putString("pin_salt", v).apply(); }

    public boolean hasPin() { return pinHash() != null; }

    // ---------- المظهر ----------
    /** 0 = تلقائي، 1 = فاتح، 2 = داكن */
    public int themeMode() { return 1; }  // مثبّت: فاتح دائمًا
    public void themeMode(int v) { /* مثبتة */ }

    /** ar / en / system */
    public String language() { return java.util.Locale.getDefault().getLanguage().equals("en") ? "en" : "ar"; }  // لغة النظام فقط
    public void language(String v) { /* تتبع النظام */ }

    // ---------- الخصوصية ----------
    public boolean hideBalance() { return sp.getBoolean("hide_balance", false); }
    public void hideBalance(boolean v) { sp.edit().putBoolean("hide_balance", v).apply(); }

    // ---------- التنبيهات ----------
    public boolean soundOn() { return sp.getBoolean("sound_on", true); }
    public void soundOn(boolean v) { sp.edit().putBoolean("sound_on", v).apply(); }

    public int lastWarnMonth() { return sp.getInt("last_warn_month", 0); }
    public void lastWarnMonth(int v) { sp.edit().putInt("last_warn_month", v).apply(); }

    public long lastPaydayDay() { return sp.getLong("last_payday_day", 0L); }
    public void lastPaydayDay(long v) { sp.edit().putLong("last_payday_day", v).apply(); }

    public long lastDailyDay() { return sp.getLong("last_daily_day", 0L); }
    public void lastDailyDay(long v) { sp.edit().putLong("last_daily_day", v).apply(); }

    public String lastAlertSig() { return sp.getString("alert_sig", ""); }
    public void lastAlertSig(String v) { sp.edit().putString("alert_sig", v).apply(); }

    public boolean remindersOn() { return sp.getBoolean("reminders_on", true); }
    public void remindersOn(boolean v) { sp.edit().putBoolean("reminders_on", v).apply(); }

    public int reminderHour() { return sp.getInt("reminder_hour", 21); }
    public void reminderHour(int v) { sp.edit().putInt("reminder_hour", v).apply(); }

    public boolean alertsOn() { return sp.getBoolean("alerts_on", true); }
    public void alertsOn(boolean v) { sp.edit().putBoolean("alerts_on", v).apply(); }

    // ---------- الدليل التعليمي ----------
    public int lessonProgress() { return sp.getInt("lessons_done", 0); }
    public void lessonProgress(int v) { sp.edit().putInt("lessons_done", v).apply(); }

    /** قناع الأقسام المفتوحة في الأكاديمية (كل قسم = بت). */
    public long guideOpenMask() { return sp.getLong("guide_open_mask", 0L); }
    public void guideOpenMask(long v) { sp.edit().putLong("guide_open_mask", v).apply(); }

    public long lessonsDoneMask() { return sp.getLong("lessons_mask", 0L); }
    public void lessonsDoneMask(long v) { sp.edit().putLong("lessons_mask", v).apply(); }

    // ---------- عملة افتراضية حسب بلد الجهاز ----------
    private String defaultCurrency() {
        String country;
        try {
            country = java.util.Locale.getDefault().getCountry().toUpperCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            country = "";
        }
        switch (country) {
            case "SY": return "ل.س";
            case "SA": return "ر.س";
            case "AE": return "د.إ";
            case "KW": return "د.ك";
            case "QA": return "ر.ق";
            case "BH": return "د.ب";
            case "OM": return "ر.ع";
            case "JO": return "د.أ";
            case "EG": return "ج.م";
            case "MA": return "د.م";
            case "DZ": return "د.ج";
            case "TN": return "د.ت";
            case "LY": return "د.ل";
            case "IQ": return "د.ع";
            case "YE": return "ر.ي";
            case "SD": return "ج.س";
            case "TR": return "₺";
            case "PK": return "₨";
            case "IN": return "₹";
            case "GB": return "£";
            case "EU": case "DE": case "FR": case "ES": case "IT": return "€";
            default: return "ل.س"; // عملة المستخدم الافتراضية: الليرة السورية
        }
    }
}
