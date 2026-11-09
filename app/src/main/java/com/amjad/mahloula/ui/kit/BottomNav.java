package com.amjad.mahloula.ui.kit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/** شريط تنقّل سفلي مصنوع يدويًا بمؤشر متحرك. */
public final class BottomNav extends LinearLayout {

    public interface OnTab { void onTab(int index); }

    private final int[] icons = {R.drawable.ic_home, R.drawable.ic_receipt, R.drawable.ic_handshake,
            R.drawable.ic_note, R.drawable.ic_cap};
    private final ImageView[] ivs = new ImageView[5];
    private final TextView[] tvs = new TextView[5];
    private final View[] inds = new View[5];
    private int current = 0;
    private OnTab listener;

    public BottomNav(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.roundStroke(c, Ui.color(c, R.color.nav_bar), Ui.color(c, R.color.outline), 0.6f, 0));
        setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 8));
        if (android.os.Build.VERSION.SDK_INT >= 21) setElevation(Ui.dp(c, 12));
        boolean ar = !Prefs.get(c).language().equals("en");
        String[] labels = ar
                ? new String[]{"الرئيسية", "مسحوبات", "الديون", "ملاحظات", "تعليمي"}
                : new String[]{"Home", "Spending", "Debts", "Notes", "Academy"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout item = Ui.v(c);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams lp = new LayoutParams(0, -2, 1f);
            item.setLayoutParams(lp);
            item.setPadding(0, Ui.dp(c, 4), 0, 0);

            ivs[i] = Ui.iconSized(c, icons[i], Ui.color(c, R.color.text_tertiary), 26);
            item.addView(ivs[i]);

            tvs[i] = Ui.txt(c, labels[i], 11.5f, R.color.text_tertiary, Typeface.BOLD);
            LayoutParams tlp = new LayoutParams(-2, -2);
            tlp.topMargin = Ui.dp(c, 3);
            tvs[i].setLayoutParams(tlp);
            item.addView(tvs[i]);

            inds[i] = new View(c);
            inds[i].setBackground(Ui.round(Ui.color(c, R.color.brand_600), 3, c));
            LayoutParams ilp = new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 3.5f));
            ilp.topMargin = Ui.dp(c, 4);
            inds[i].setLayoutParams(ilp);
            inds[i].setVisibility(INVISIBLE);
            item.addView(inds[i]);

            item.setBackground(Ui.ripple(c, Ui.round(Color.TRANSPARENT, 14, c), Ui.color(c, R.color.surface_3)));
            item.setOnClickListener(v -> select(idx, true));
            addView(item);
        }
        select(0, false);
    }

    public void setListener(OnTab l) { listener = l; }

    public void select(int idx, boolean notify) {
        current = idx;
        for (int i = 0; i < 5; i++) {
            boolean on = i == idx;
            ivs[i].getDrawable().mutate().setTint(Ui.color(getContext(), on ? R.color.brand_600 : R.color.text_tertiary));
            tvs[i].setTextColor(Ui.color(getContext(), on ? R.color.text_primary : R.color.text_tertiary));
            if (on) {
                inds[i].setVisibility(VISIBLE);
                ivs[i].animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).start();
            } else {
                inds[i].setVisibility(INVISIBLE);
                ivs[i].animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
        }
        if (notify && listener != null) listener.onTab(idx);
    }

    public int current() { return current; }
}
