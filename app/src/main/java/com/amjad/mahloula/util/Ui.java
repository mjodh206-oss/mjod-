package com.amjad.mahloula.util;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.amjad.mahloula.R;

import java.util.HashMap;
import java.util.Map;


/** عدة الشغل لكل واجهات مَحلولة: خطوط، ألوان، أيقونات، بطاقات، حركات. */
public final class Ui {

    private Ui() {}

    private static final Map<String, Typeface> FONTS = new HashMap<>();

    // ---------------- قياسات ----------------

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int sp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().scaledDensity);
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    // ---------------- الخطوط ----------------

    public static Typeface font(Context c, int style) {
        String key;
        String asset;
        switch (style) {
            case Typeface.BOLD:
                key = "b"; asset = "fonts/plex_arabic_Bold.ttf"; break;
            case 3: // semi-bold
                key = "sb"; asset = "fonts/plex_arabic_SemiBold.ttf"; break;
            case 2: // medium
                key = "m"; asset = "fonts/plex_arabic_Medium.ttf"; break;
            default:
                key = "r"; asset = "fonts/plex_arabic_Regular.ttf"; break;
        }
        Typeface t = FONTS.get(key);
        if (t == null) {
            try {
                t = Typeface.createFromAsset(c.getAssets(), asset);
            } catch (Exception e) {
                t = Typeface.DEFAULT;
            }
            FONTS.put(key, t);
        }
        return t;
    }

    public static final int STYLE_SEMI_BOLD = 3;

    /** TextView جاهز: خط عربي، حجم، لون، وزن. */
    public static TextView txt(Context c, String s, float sizeSp, int colorRes, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color(c, colorRes));
        t.setTypeface(font(c, style));
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    public static TextView txt(Context c, String s, float sizeSp, int colorRes) {
        return txt(c, s, sizeSp, colorRes, Typeface.NORMAL);
    }

    // ---------------- أيقونات ----------------

    public static ImageView icon(Context c, int drawableRes, int tintRes, int sizeDp) {
        ImageView iv = new ImageView(c);
        Drawable d = c.getDrawable(drawableRes);
        if (d != null) {
            d = d.mutate();
            d.setTint(color(c, tintRes));
        }
        iv.setImageDrawable(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    public static ImageView iconSized(Context c, int drawableRes, int tintColor, int sizeDp) {
        ImageView iv = new ImageView(c);
        Drawable d = c.getDrawable(drawableRes);
        if (d != null) {
            d = d.mutate();
            d.setTint(tintColor);
        }
        iv.setImageDrawable(d);
        return iv;
    }

    // ---------------- خلفيات ----------------

    public static GradientDrawable round(int fillColor, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fillColor);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(Context c, int fillColor, int strokeColor, float strokeDp, int radiusDp) {
        GradientDrawable g = round(fillColor, radiusDp, c);
        g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static GradientDrawable gradient(Context c, int[] colors, int radiusDp, int angle) {
        GradientDrawable g = new GradientDrawable(
                angle == 0 ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static RippleDrawable ripple(Context c, Drawable base, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null);
    }

    public static View clickable(Context c, View v, int rippleColor) {
        Drawable bg = v.getBackground();
        if (bg == null) bg = round(color(c, android.R.color.transparent), 12, c);
        v.setBackground(ripple(c, bg, rippleColor));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    // ---------------- هزاز وتنبيهات ----------------

    public static void tick(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(18);
        } catch (Exception ignored) {}
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------------- حركات ----------------

    public static void fadeSlideIn(View v, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 14));
        v.animate().alpha(1f).translationY(0).setStartDelay(delayMs).setDuration(320)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    // ---------------- تخطيطات مساعدة ----------------

    public static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lpw(int w, int h, int marginDp, Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        int m = dp(c, marginDp);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    public static LinearLayout.LayoutParams lpwm(int w, int h, int l, int t, int r, int b, Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return lp;
    }

    public static FrameLayout frame(Context c) {
        return new FrameLayout(c);
    }

    public static void statusBarColor(Activity a, int colorRes, boolean lightIcons) {
        a.getWindow().setStatusBarColor(color(a, colorRes));
        if (Build.VERSION.SDK_INT >= 23) {
            View dec = a.getWindow().getDecorView();
            int flags = dec.getSystemUiVisibility();
            if (lightIcons) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            dec.setSystemUiVisibility(flags);
        }
    }

    public static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static boolean empty(String s) { return TextUtils.isEmpty(s == null ? null : s.trim()); }
}
