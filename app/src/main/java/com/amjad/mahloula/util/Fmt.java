package com.amjad.mahloula.util;

import android.content.Context;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** أدوات التنسيق: مبالغ، تواريخ، أرقام — بعربية سليمة. */
public final class Fmt {

    private Fmt() {}

    private static final String AR_DIGITS = "٠١٢٣٤٥٦٧٨٩";

    public static Locale locale(Context c) {
        return Prefs.get(c).language().equals("en") ? Locale.ENGLISH : new Locale("ar");
    }

    /** يحوّل الأرقام إلى أرقام عربية مشرقية عند تفعيل الخيار. */
    public static String digits(Context c, String s) {
        if (!Prefs.get(c).easternDigits()) return s;
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') b.append(AR_DIGITS.charAt(ch - '0'));
            else b.append(ch);
        }
        return b.toString();
    }

    /** تنسيق مبلغ: فواصل آلاف + homeless decimals. */
    public static String amount(Context c, double v) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(locale(c));
        DecimalFormat f = new DecimalFormat("#,##0.###", sy);
        f.setMaximumFractionDigits(2);
        String s = f.format(Math.abs(v) < 0.005 ? 0 : v);
        return digits(c, s);
    }

    public static String money(Context c, double v) {
        return amount(c, v) + " " + Prefs.get(c).currency();
    }

    public static String signedMoney(Context c, double v, boolean income) {
        return (income ? "+" : "−") + money(c, Math.abs(v));
    }

    public static String percent(Context c, double p) {
        return digits(c, String.valueOf(Math.round(p))) + "٪";
    }

    // ------------------------- التواريخ -------------------------

    public static Calendar startOfMonth(Calendar any) {
        Calendar cal = (Calendar) any.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    public static Calendar endOfMonth(Calendar any) {
        Calendar cal = startOfMonth(any);
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.MILLISECOND, -1);
        return cal;
    }

    public static Calendar startOfWeek(Calendar c) {
        Calendar w = (Calendar) c.clone();
        w.set(Calendar.DAY_OF_WEEK, w.getFirstDayOfWeek());
        w.set(Calendar.HOUR_OF_DAY, 0);
        w.set(Calendar.MINUTE, 0);
        w.set(Calendar.SECOND, 0);
        w.set(Calendar.MILLISECOND, 0);
        return w;
    }

    public static Calendar startOfDay(Calendar any) {
        Calendar cal = (Calendar) any.clone();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    public static boolean sameDay(long a, long b) {
        return startOfDay(Calendar.getInstance()).getTimeInMillis() >= 0
                && dayKey(a) == dayKey(b);
    }

    public static long dayKey(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return startOfDay(c).getTimeInMillis();
    }

    public static String monthName(Context c, Calendar cal) {
        SimpleDateFormat f = new SimpleDateFormat("MMMM yyyy", locale(c));
        return f.format(cal.getTime());
    }

    public static String dayMonth(Context c, long ts) {
        SimpleDateFormat f = new SimpleDateFormat("d MMMM", locale(c));
        return digits(c, f.format(new Date(ts)));
    }

    public static String fullDate(Context c, long ts) {
        SimpleDateFormat f = new SimpleDateFormat("EEEE، d MMMM yyyy", locale(c));
        return digits(c, f.format(new Date(ts)));
    }

    public static String shortDate(Context c, long ts) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd", locale(c));
        return digits(c, f.format(new Date(ts)));
    }

    public static String time(Context c, long ts) {
        SimpleDateFormat f = new SimpleDateFormat("h:mm a", locale(c));
        return digits(c, f.format(new Date(ts)));
    }

    /** تاريخ نسبي ودود: اليوم / أمس / قبل n يوم / تاريخ كامل. */
    public static String relative(Context c, long ts) {
        Calendar today = startOfDay(Calendar.getInstance());
        Calendar that = startOfDay(Calendar.getInstance());
        that.setTimeInMillis(ts);
        long diff = (today.getTimeInMillis() - that.getTimeInMillis()) / 86400000L;
        Locale l = locale(c);
        if (diff == 0) return l.getLanguage().equals("ar") ? "اليوم" : "Today";
        if (diff == 1) return l.getLanguage().equals("ar") ? "أمس" : "Yesterday";
        if (diff < 7) {
            return l.getLanguage().equals("ar")
                    ? "قبل " + digits(c, String.valueOf(diff)) + " أيام"
                    : diff + " days ago";
        }
        return dayMonth(c, ts);
    }

    /** اسم يوم الأسبوع مختصرًا. */
    public static String weekdayShort(Context c, long ts) {
        SimpleDateFormat f = new SimpleDateFormat("EEE", locale(c));
        return f.format(new Date(ts));
    }

    public static String monthKey(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1);
    }
}
