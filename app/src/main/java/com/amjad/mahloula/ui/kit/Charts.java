package com.amjad.mahloula.ui.kit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Ui;

import java.util.List;

/** رسوم بيانية مرسومة يدويًا: دونات الفئات، أعمدة الأيام، حلقة تقدم. */
public final class Charts {

    private Charts() {}

    // ---------------- دونات الفئات ----------------

    public static class Donut extends View {
        private List<Models.CatTotal> data;
        private double total;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float sweep = 0f;

        public Donut(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        public void setData(List<Models.CatTotal> d, double total) {
            this.data = d;
            this.total = total;
            sweep = 0f;
            animateSweep();
            invalidate();
        }

        private void animateSweep() {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 360f);
            a.setDuration(700);
            a.addUpdateListener(an -> {
                sweep = (Float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (data == null || data.isEmpty() || total <= 0) return;
            float w = getWidth();
            float h = getHeight();
            float stroke = Math.min(w, h) * 0.19f;
            paint.setStrokeWidth(stroke);
            float pad = stroke / 2 + Ui.dp(getContext(), 2);
            rect.set(pad, pad, w - pad, h - pad);
            float start = -90f;
            float budget = sweep;
            for (Models.CatTotal ct : data) {
                float ang = (float) (ct.total / total * 360f);
                float draw = Math.min(ang, Math.max(0, budget));
                if (draw <= 0) break;
                paint.setColor(Ui.color(getContext(), Models.cat(ct.cat).color));
                paint.setStyle(Paint.Style.STROKE);
                cv.drawArc(rect, start, draw - 1.2f, false, paint);
                start += ang;
                budget -= ang;
                if (budget <= 0) break;
            }
        }
    }

    // ---------------- أعمدة يومية ----------------

    public static class Bars extends View {
        private List<Models.DayPoint> data;
        private double max = 1;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;
        private int highlightIndex = -1;

        public Bars(Context c) { super(c); }

        public void setData(List<Models.DayPoint> d) {
            this.data = d;
            max = 1;
            if (d != null) for (Models.DayPoint p : d) max = Math.max(max, p.value);
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(600);
            a.addUpdateListener(an -> {
                progress = (Float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        public void setHighlight(int idx) { highlightIndex = idx; invalidate(); }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (data == null || data.isEmpty()) return;
            float w = getWidth();
            float h = getHeight();
            float gap = Ui.dp(getContext(), 3);
            float bw = (w - gap * (data.size() - 1)) / data.size();
            float base = h - Ui.dp(getContext(), 2);
            for (int i = 0; i < data.size(); i++) {
                double v = data.get(i).value;
                float bh = (float) (v / max * (h - Ui.dp(getContext(), 6)) * progress);
                float x = i * (bw + gap);
                boolean hl = i == highlightIndex;
                paint.setColor(v <= 0
                        ? Ui.color(getContext(), R.color.surface)
                        : (hl ? Ui.color(getContext(), R.color.expense) : Ui.color(getContext(), R.color.text_primary)));
                float rr = Math.min(bw / 2, Ui.dp(getContext(), 3));
                cv.drawRoundRect(x, base - Math.max(bh, Ui.dp(getContext(), 2)), x + bw, base, rr, rr, paint);
            }
        }
    }

    // ---------------- حلقة تقدم (مؤشر الصحة / الأهداف) ----------------

    public static class Ring extends View {
        private float value = 0f;      // 0..1
        private int ringColor = 0;
        private String centerTop = "", centerBottom = "";
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public Ring(Context c) {
            super(c);
            bg.setStyle(Paint.Style.STROKE);
            fg.setStyle(Paint.Style.STROKE);
            fg.setStrokeCap(Paint.Cap.ROUND);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        public void set(float value, int colorRes, String top, String bottom) {
            this.ringColor = Ui.color(getContext(), colorRes);
            this.centerTop = top;
            this.centerBottom = bottom;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, value);
            a.setDuration(800);
            a.addUpdateListener(an -> {
                this.value = (Float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight();
            float stroke = Math.min(w, h) * 0.11f;
            bg.setStrokeWidth(stroke);
            fg.setStrokeWidth(stroke);
            float pad = stroke / 2 + Ui.dp(getContext(), 1);
            rect.set(pad, pad, w - pad, h - pad);
            bg.setColor(Ui.color(getContext(), R.color.surface));
            cv.drawArc(rect, 0, 360, false, bg);
            fg.setColor(ringColor);
            cv.drawArc(rect, -90, value * 360, false, fg);
            txt.setColor(Ui.color(getContext(), R.color.text_primary));
            txt.setTypeface(Ui.font(getContext(), Typeface.BOLD));
            txt.setTextSize(Math.min(w, h) * 0.30f);
            cv.drawText(centerTop, w / 2, h / 2 + Math.min(w, h) * 0.03f, txt);
            txt.setColor(Ui.color(getContext(), R.color.text_secondary));
            txt.setTypeface(Ui.font(getContext(), Typeface.NORMAL));
            txt.setTextSize(Math.min(w, h) * 0.115f);
            cv.drawText(centerBottom, w / 2, h / 2 + Math.min(w, h) * 0.20f, txt);
        }
    }

    // ---------------- شريط تقدم أفقي ----------------

    public static class HBar extends View {
        private float value = 0f;
        private int colorRes = R.color.brand_500;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HBar(Context c) { super(c); }

        public void set(float v, int colorRes) {
            this.colorRes = colorRes;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, Math.min(1f, v));
            a.setDuration(650);
            a.addUpdateListener(an -> {
                value = (Float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(Ui.dp(getContext(), 9), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight();
            float r = h / 2;
            bg.setColor(Ui.color(getContext(), R.color.surface));
            cv.drawRoundRect(0, 0, w, h, r, r, bg);
            fg.setColor(Ui.color(getContext(), colorRes));
            cv.drawRoundRect(0, 0, Math.max(h, w * value), h, r, r, fg);
        }
    }

    /** سطر وسيلة إيضاح للرسم الدائري. */
    public static View legendRow(Context c, Models.CatTotal ct, double total) {
        android.widget.LinearLayout row = Ui.h(c);
        View dot = new View(c);
        dot.setBackground(Ui.round(Ui.color(c, Models.cat(ct.cat).color), 5, c));
        android.widget.LinearLayout.LayoutParams dlp = new android.widget.LinearLayout.LayoutParams(Ui.dp(c, 11), Ui.dp(c, 11));
        dot.setLayoutParams(dlp);
        row.addView(dot);
        android.widget.TextView name = Ui.txt(c, Models.cat(ct.cat).ar, 13, R.color.text_primary, Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams nlp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        nlp.setMarginStart(Ui.dp(c, 8));
        name.setLayoutParams(nlp);
        row.addView(name);
        android.widget.TextView pct = Ui.txt(c, Fmt.percent(c, ct.total / total * 100), 12.5f, R.color.text_secondary, Typeface.BOLD);
        row.addView(pct);
        android.widget.TextView val = Ui.txt(c, Fmt.amount(c, ct.total), 13, R.color.text_primary, Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams vlp = new android.widget.LinearLayout.LayoutParams(-2, -2);
        vlp.setMarginStart(Ui.dp(c, 10));
        val.setLayoutParams(vlp);
        row.addView(val);
        return row;
    }

    public static TextView caption(Context c, String s) {
        TextView t = Ui.txt(c, s, 11.5f, R.color.text_tertiary);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        return t;
    }
}
