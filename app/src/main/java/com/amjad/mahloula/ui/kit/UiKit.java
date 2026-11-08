package com.amjad.mahloula.ui.kit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.util.Ui;

/** مكوّنات «مَترل» مصنوعة يدويًا: بطاقة، شريحة، زر، حقل، شيت سفلي، سناك بار. */
public final class UiKit {

    private UiKit() {}

    // ---------------- بطاقة ----------------

    public static LinearLayout card(Context c, int padDp) {
        LinearLayout l = Ui.v(c);
        l.setBackground(Ui.round(Ui.color(c, R.color.surface), 20, c));
        l.setPadding(Ui.dp(c, padDp), Ui.dp(c, padDp), Ui.dp(c, padDp), Ui.dp(c, padDp));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            l.setElevation(Ui.dp(c, 1.5f));
        }
        return l;
    }

    public static LinearLayout cardStroke(Context c, int padDp) {
        LinearLayout l = card(c, padDp);
        l.setBackground(Ui.roundStroke(c, Ui.color(c, R.color.surface), Ui.color(c, R.color.outline), 1, 20));
        if (android.os.Build.VERSION.SDK_INT >= 21) l.setElevation(0);
        return l;
    }

    // ---------------- عنوان قسم ----------------

    public static TextView sectionTitle(Context c, String s) {
        TextView t = Ui.txt(c, s, 15, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView sectionTitleIn(Context c, String s) {
        TextView t = Ui.txt(c, s, 14, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(c, 16);
        lp.bottomMargin = Ui.dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    // ---------------- زر أساسي ----------------

    public static TextView button(Context c, String label, int iconRes, boolean filled) {
        TextView b = Ui.txt(c, label, 14.5f, filled ? R.color.on_brand : R.color.brand_600, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(c, 48));
        b.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));
        if (filled) {
            GradientDrawable g = Ui.gradient(c, new int[]{
                    Ui.color(c, R.color.brand_500), Ui.color(c, R.color.brand_700)}, 14, 1);
            b.setBackground(Ui.ripple(c, g, Color.argb(40, 255, 255, 255)));
        } else {
            b.setBackground(Ui.ripple(c,
                    Ui.roundStroke(c, Ui.color(c, R.color.brand_50), Ui.color(c, R.color.brand_300), 1, 14),
                    Ui.color(c, R.color.brand_200)));
        }
        if (iconRes != 0) {
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            b.setCompoundDrawablePadding(Ui.dp(c, 8));
            b.getCompoundDrawablesRelative();
            tintCompounds(b, filled ? Ui.color(c, R.color.on_brand) : Ui.color(c, R.color.brand_600));
        }
        return b;
    }

    private static void tintCompounds(TextView b, int color) {
        android.graphics.drawable.Drawable[] ds = b.getCompoundDrawablesRelative();
        for (int i = 0; i < ds.length; i++) if (ds[i] != null) { ds[i] = ds[i].mutate(); ds[i].setTint(color); }
        b.setCompoundDrawablesRelative(ds[0], ds[1], ds[2], ds[3]);
    }

    // ---------------- حقل إدخال ----------------

    public static EditText field(Context c, String hint, boolean number) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTypeface(Ui.font(c, Typeface.NORMAL));
        e.setTextColor(Ui.color(c, R.color.text_primary));
        e.setHintTextColor(Ui.color(c, R.color.text_tertiary));
        e.setBackground(Ui.roundStroke(c, Ui.color(c, R.color.surface_2), Ui.color(c, R.color.outline), 1.2f, 14));
        e.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        e.setMinHeight(Ui.dp(c, 48));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSingleLine(true);
        return e;
    }

    public static EditText area(Context c, String hint) {
        EditText e = field(c, hint, false);
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return e;
    }

    public static TextView label(Context c, String s) {
        TextView t = Ui.txt(c, s, 12.5f, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 10), 0, Ui.dp(c, 6));
        t.setLayoutParams(lp);
        return t;
    }

    // ---------------- شريحة اختيار ----------------

    public interface ChipGroupListener { void onPick(int index); }

    public static HorizontalScroll chipRow(Context c, String[] labels, int selected, ChipGroupListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final HorizontalScroll hs = new HorizontalScroll(c);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        final TextView[] chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView ch = Ui.txt(c, labels[i], 13, R.color.text_secondary, Typeface.BOLD);
            ch.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 8));
            ch.setLayoutParams(lp);
            chips[i] = ch;
            ch.setOnClickListener(v -> {
                styleChips(c, chips, idx);
                if (l != null) l.onPick(idx);
            });
            row.addView(ch);
        }
        styleChips(c, chips, selected);
        hs.addView(row);
        return hs;
    }

    private static void styleChips(Context c, TextView[] chips, int selected) {
        for (int i = 0; i < chips.length; i++) {
            if (i == selected) {
                chips[i].setBackground(Ui.round(Ui.color(c, R.color.brand_600), 12, c));
                chips[i].setTextColor(Ui.color(c, R.color.on_brand));
            } else {
                chips[i].setBackground(Ui.roundStroke(c, Ui.color(c, R.color.surface_2),
                        Ui.color(c, R.color.outline), 1, 12));
                chips[i].setTextColor(Ui.color(c, R.color.text_secondary));
            }
        }
    }

    /** ScrollView أفقي بقياس كامل العرض. */
    public static class HorizontalScroll extends android.widget.HorizontalScrollView {
        public HorizontalScroll(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
        }
    }

    // ---------------- حالة فارغة ----------------

    public static LinearLayout empty(Context c, int iconRes, String title, String sub) {
        LinearLayout l = Ui.v(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(Ui.dp(c, 24), Ui.dp(c, 40), Ui.dp(c, 24), Ui.dp(c, 40));
        ImageView iv = Ui.iconSized(c, iconRes, Ui.color(c, R.color.text_tertiary), 64);
        iv.setAlpha(0.55f);
        l.addView(iv);
        TextView t = Ui.txt(c, title, 16, R.color.text_primary, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(c, 14);
        t.setLayoutParams(lp);
        l.addView(t);
        if (sub != null) {
            TextView s = Ui.txt(c, sub, 13.5f, R.color.text_secondary);
            s.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
            lp2.topMargin = Ui.dp(c, 6);
            s.setLayoutParams(lp2);
            l.addView(s);
        }
        return l;
    }

    // ---------------- صف قائمة عام ----------------

    /** يقبل معرّف مورد (0x7f…) أو لونًا خامًا (ARGB) — لا ينهار أبدًا. */
    private static int col(Context c, int v) {
        return (v >>> 24) == 0x7f ? Ui.color(c, v) : v;
    }

    public static LinearLayout row(Context c, int iconRes, int iconTint, int iconBg,
                                   String title, String sub, String trailing, int trailingColor) {
        LinearLayout row = Ui.h(c);
        row.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        row.setBackground(Ui.ripple(c, Ui.round(Ui.color(c, R.color.surface), 18, c),
                Ui.color(c, R.color.surface_3)));

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.round(col(c, iconBg), 13, c));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
        badge.setLayoutParams(blp);
        ImageView iv = Ui.iconSized(c, iconRes, col(c, iconTint), 22);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        ilp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ilp);
        badge.addView(iv);
        row.addView(badge);

        LinearLayout mid = Ui.v(c);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.setMarginStart(Ui.dp(c, 12));
        mid.setLayoutParams(mlp);
        TextView t = Ui.txt(c, title, 14.5f, R.color.text_primary, Typeface.BOLD);
        t.setMaxLines(1);
        mid.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.txt(c, sub, 12.5f, R.color.text_secondary);
            s.setMaxLines(1);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(c, 2);
            s.setLayoutParams(slp);
            mid.addView(s);
        }
        row.addView(mid);

        if (trailing != null) {
            TextView tr = Ui.txt(c, trailing, 14.5f, trailingColor, Typeface.BOLD);
            row.addView(tr);
        }
        return row;
    }

    // ---------------- شيت سفلي ----------------

    public static class Sheet {
        public final Dialog dialog;
        public final LinearLayout content;
        private final TextView titleView;

        Sheet(Context c, String title) { this(c, title, 0.86f); }

        Sheet(Context c, String title, float heightPct) {
            boolean night = (c.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            dialog = new Dialog(c, night
                    ? android.R.style.Theme_Material_NoActionBar
                    : android.R.style.Theme_Material_Light_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.BOTTOM);
                w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            LinearLayout root = Ui.v(c);
            GradientDrawable bg = Ui.round(Ui.color(c, R.color.surface), 26, c);
            bg.setCornerRadii(new float[]{Ui.dp(c, 26), Ui.dp(c, 26), Ui.dp(c, 26), Ui.dp(c, 26), 0, 0, 0, 0});
            root.setBackground(bg);
            root.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 24));

            View handle = new View(c);
            handle.setBackground(Ui.round(Ui.color(c, R.color.outline_strong), 3, c));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 4.5f));
            hlp.gravity = Gravity.CENTER_HORIZONTAL;
            hlp.topMargin = Ui.dp(c, 8);
            handle.setLayoutParams(hlp);
            root.addView(handle);

            titleView = Ui.txt(c, title, 17, R.color.text_primary, Typeface.BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.topMargin = Ui.dp(c, 14);
            tlp.bottomMargin = Ui.dp(c, 6);
            titleView.setLayoutParams(tlp);
            root.addView(titleView);

            content = Ui.v(c);
            ScrollView sv = new ScrollView(c);
            sv.setFillViewport(true);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(content);
            LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
            root.addView(sv, svlp);

            dialog.setContentView(root);
            if (w != null) {
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * heightPct));
            }
        }

        public void setTitle(String t) { titleView.setText(t); }

        public void show() {
            dialog.show();
            View v = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
            if (v != null) {
                v.setTranslationY(Ui.dp(v.getContext(), 60));
                v.setAlpha(0.6f);
                v.animate().translationY(0).alpha(1f).setDuration(240)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        }

        public void dismiss() { dialog.dismiss(); }
    }

    public static Sheet sheet(Context c, String title) {
        return new Sheet(c, title);
    }

    public static Sheet sheetCompact(Context c, String title) {
        return new Sheet(c, title, 0.62f);
    }

    /** زر عريض داخل الشيت. */
    public static TextView sheetButton(Sheet sh, String label, boolean danger) {
        TextView b = Ui.txt(sh.content.getContext(), label, 15, danger ? R.color.on_brand : R.color.on_brand, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(sh.content.getContext(), 50));
        int col = danger ? R.color.expense : R.color.brand_600;
        b.setBackground(Ui.ripple(sh.content.getContext(),
                Ui.round(Ui.color(sh.content.getContext(), col), 15, sh.content.getContext()),
                Color.argb(35, 255, 255, 255)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(sh.content.getContext(), 16);
        b.setLayoutParams(lp);
        sh.content.addView(b);
        return b;
    }

    // ---------------- سناك بار ----------------

    public static void snack(Activity a, String msg, String actionLabel, Runnable action) {
        ViewGroup root = a.findViewById(android.R.id.content);
        TextView tv = Ui.txt(a, msg, 13.5f, R.color.on_brand, Typeface.NORMAL);
        tv.setBackground(Ui.round(Ui.color(a, R.color.brand_800), 14, a));
        tv.setPadding(Ui.dp(a, 16), Ui.dp(a, 13), Ui.dp(a, 16), Ui.dp(a, 13));
        if (actionLabel != null) {
            LinearLayout row = Ui.h(a);
            row.setBackground(Ui.round(Ui.color(a, R.color.brand_800), 14, a));
            row.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
            tv.setBackground(null);
            tv.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
            row.addView(tv);
            TextView act = Ui.txt(a, actionLabel, 13.5f, R.color.gold_400, Typeface.BOLD);
            act.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            act.setBackground(Ui.ripple(a, Ui.round(Color.TRANSPARENT, 10, a), Color.argb(30, 255, 255, 255)));
            act.setOnClickListener(v -> {
                root.removeView(row.getParent() instanceof View ? (View) row.getParent() : row);
                if (action != null) action.run();
            });
            row.addView(act);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Ui.dp(a, 90);
            row.setLayoutParams(lp);
            row.setElevation(Ui.dp(a, 8));
            root.addView(row);
            row.setAlpha(0f);
            row.animate().alpha(1f).setDuration(200).start();
            row.postDelayed(() -> row.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> root.removeView(row)).start(), 4200);
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Ui.dp(a, 90);
            tv.setLayoutParams(lp);
            tv.setElevation(Ui.dp(a, 8));
            root.addView(tv);
            tv.setAlpha(0f);
            tv.animate().alpha(1f).setDuration(200).start();
            tv.postDelayed(() -> tv.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> root.removeView(tv)).start(), 3000);
        }
    }
}
