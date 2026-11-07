package com.amjad.mahloula.ui.kit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.util.Ui;

/** لوحة رمز PIN: دوائر حالة + لوحة أرقام 3×4 بحركات ولمسات احترافية. */
public final class PinPad extends LinearLayout {

    public interface Listener {
        void onComplete(String pin);
        void onChanged(int len);
    }

    private final StringBuilder sb = new StringBuilder();
    private final int len;
    private final TextView[] dots;
    private Listener listener;

    public PinPad(Context c, int length) {
        super(c);
        this.len = length;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout dotsRow = Ui.h(c);
        dotsRow.setGravity(Gravity.CENTER);
        dots = new TextView[length];
        for (int i = 0; i < length; i++) {
            TextView d = new TextView(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15));
            lp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
            d.setLayoutParams(lp);
            d.setBackground(Ui.roundStroke(c, Color.TRANSPARENT, Ui.color(c, R.color.text_tertiary), 1.6f, 8));
            dotsRow.addView(d);
            dots[i] = d;
        }
        addView(dotsRow);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.topMargin = Ui.dp(c, 22);
        grid.setLayoutParams(glp);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            TextView key = new TextView(c);
            key.setGravity(Gravity.CENTER);
            key.setTypeface(Ui.font(c, Typeface.BOLD));
            key.setTextSize(22);
            GridLayout.LayoutParams klp = new GridLayout.LayoutParams();
            klp.width = Ui.dp(c, 74);
            klp.height = Ui.dp(c, 62);
            klp.setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            key.setLayoutParams(klp);
            if (k.isEmpty()) {
                key.setClickable(false);
            } else {
                key.setTextColor(Ui.color(c, R.color.text_primary));
                key.setBackground(Ui.ripple(c, Ui.round(Ui.color(c, R.color.surface_2), 18, c),
                        Ui.color(c, R.color.surface_3)));
                key.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (k.equals("⌫")) {
                        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                    } else if (sb.length() < len) {
                        sb.append(k);
                    }
                    render();
                    if (listener != null) listener.onChanged(sb.length());
                    if (sb.length() == len && listener != null) {
                        listener.onComplete(sb.toString());
                    }
                });
            }
            key.setText(k);
            grid.addView(key);
        }
        addView(grid);
    }

    public void setListener(Listener l) { listener = l; }

    public String current() { return sb.toString(); }

    public void clear() {
        sb.setLength(0);
        render();
    }

    /** اهتزاز رفض + تفريغ. */
    public void reject() {
        animate().translationX(Ui.dp(getContext(), 10)).setDuration(60)
                .withEndAction(() -> animate().translationX(-Ui.dp(getContext(), 10)).setDuration(60)
                        .withEndAction(() -> animate().translationX(0).setDuration(60).start()).start()).start();
        clear();
    }

    private void render() {
        for (int i0 = 0; i0 < len; i0++) {
            final int i = i0;
            boolean on = i < sb.length();
            dots[i].setBackground(on
                    ? Ui.round(Ui.color(getContext(), R.color.brand_500), 8, getContext())
                    : Ui.roundStroke(getContext(), Color.TRANSPARENT,
                    Ui.color(getContext(), R.color.text_tertiary), 1.6f, 8));
            if (on) {
                dots[i].animate().scaleX(1.25f).scaleY(1.25f).setDuration(90)
                        .withEndAction(() -> dots[i].animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
            }
        }
    }
}
