package com.amjad.mahloula.ui.panels;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.brain.Advisor;
import com.amjad.mahloula.brain.Content;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.MainActivity;
import com.amjad.mahloula.ui.Panel;
import com.amjad.mahloula.ui.kit.Charts;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.InfoSheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.Calendar;
import java.util.List;

/**
 * الأكاديمية المالية — نسخة احترافية:
 * تقدّمك، أرقامك، خطتك الشخصية، تنبيهات المستشار، 16 درسًا مرقّمًا،
 * 10 أخطاء شائعة، و24 قاعدة عالمية.
 */
public class GuidePanel implements Panel {

    private final Activity a;
    private LinearLayout col;

    public GuidePanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        col = Ui.v(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(col);
        return sv;
    }

    @Override
    public void refresh() {
        if (col == null) return;
        col.removeAllViews();
        col.addView(header());
        col.addView(myNumbers());
        col.addView(scorecard());
        col.addView(envelopesCard());
        col.addView(planCard());
        col.addView(alertsCard());
        col.addView(lessonsCard());
        col.addView(mistakesCard());
        col.addView(rulesCard());
    }

    // ---------- ترويسة الأكاديمية ----------
    private View header() {
        long mask = Prefs.get(a).lessonsDoneMask();
        int done = Long.bitCount(mask);
        LinearLayout card = Ui.v(a);
        card.setBackground(Ui.gradient(a, new int[]{Ui.color(a, R.color.panel_deep), Ui.color(a, R.color.panel_soft)}, 24, 1));
        card.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 20));
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_cap, Ui.color(a, R.color.gold_400), 34));
        LinearLayout tx = Ui.v(a);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 12));
        tx.setLayoutParams(tlp);
        tx.addView(Ui.txt(a, ar() ? "أكاديميتك المالية" : "Your money academy", 19, R.color.on_brand, Typeface.BOLD));
        tx.addView(Ui.txt(a, ar()
                ? Fmt.digits(a, String.valueOf(done)) + " من " + Fmt.digits(a, String.valueOf(Content.LESSONS.size())) + " درسًا • "
                  + Fmt.digits(a, String.valueOf(Content.RULES.size())) + " قاعدة عالمية"
                : done + "/" + Content.LESSONS.size() + " lessons • " + Content.RULES.size() + " global rules",
                13, R.color.on_panel_2));
        head.addView(tx);
        card.addView(head);
        Charts.HBar bar = new Charts.HBar(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 14);
        bar.setLayoutParams(blp);
        bar.set((float) done / Content.LESSONS.size(), R.color.on_panel);
        card.addView(bar);
        return card;
    }

    // ---------- أرقامك ----------
    private View myNumbers() {
        Repo r = Repo.get(a);
        Prefs p = Prefs.get(a);
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        double incomeRef = mt.income > 0 ? mt.income : p.monthlyIncome();
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "أرقامك هذا الشهر" : "Your numbers", 16, R.color.text_primary, Typeface.BOLD));
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 12);
        row.setLayoutParams(rlp);

        int score = Advisor.healthScore(a);
        Charts.Ring ring = new Charts.Ring(a);
        ring.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 104), Ui.dp(a, 104)));
        ring.set(score / 100f, R.color.on_panel,
                Fmt.digits(a, String.valueOf(score)), ar() ? "من 100" : "/100");
        row.addView(ring);

        LinearLayout stats = Ui.v(a);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.setMarginStart(Ui.dp(a, 16));
        stats.setLayoutParams(slp);
        double sr = incomeRef > 0 ? mt.saved / incomeRef * 100 : 0;
        double er = incomeRef > 0 ? mt.expense / incomeRef * 100 : 0;
        double netWorth = r.totalBalance() - r.debtsOwedByMe() + r.debtsOwedToMe();
        stats.addView(statLine(ar() ? "معدل الادخار" : "Savings rate", Fmt.percent(a, sr), sr >= 10 ? R.color.income : R.color.warn));
        stats.addView(statLine(ar() ? "المصروف من الدخل" : "Spend of income", Fmt.percent(a, er), er <= 80 ? R.color.income : R.color.expense));
        stats.addView(statLine(ar() ? "صافي ثروتك" : "Net worth", Fmt.money(a, netWorth), netWorth >= 0 ? R.color.brand_600 : R.color.expense));
        row.addView(stats);
        card.addView(row);
        TextView lbl = Ui.txt(a, Advisor.healthLabel(a, score), 13, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = Ui.dp(a, 10);
        lbl.setLayoutParams(llp);
        card.addView(lbl);
        return card;
    }

    private View statLine(String label, String value, int colorRes) {
        LinearLayout row = Ui.h(a);
        row.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 12, a));
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 6);
        row.setLayoutParams(lp);
        row.addView(Ui.txt(a, label, 13, R.color.text_secondary, Typeface.BOLD));
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        row.addView(Ui.txt(a, value, 14.5f, colorRes, Typeface.BOLD));
        return row;
    }

    // ---------- هذا الشهر مقابل الماضي ----------
    private View scorecard() {
        Repo r = Repo.get(a);
        Calendar now = Calendar.getInstance();
        Calendar prev = (Calendar) now.clone();
        prev.add(Calendar.MONTH, -1);
        Models.MonthTotals mt = r.monthTotals(now);
        Models.MonthTotals pv = r.monthTotals(prev);
        if (pv.expense <= 0 && pv.income <= 0) return new View(a);
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "هذا الشهر مقابل الماضي" : "This month vs last", 16, R.color.text_primary, Typeface.BOLD));
        card.addView(deltaLine(ar() ? "المصروف" : "Spending", mt.expense, pv.expense, true));
        card.addView(deltaLine(ar() ? "الدخل" : "Income", mt.income, pv.income, false));
        card.addView(deltaLine(ar() ? "الادخار" : "Saved", mt.saved, pv.saved, false));
        return card;
    }

    private View deltaLine(String label, double nowV, double prevV, boolean lowerBetter) {
        LinearLayout row = Ui.h(a);
        row.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 12, a));
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 7);
        row.setLayoutParams(lp);
        row.addView(Ui.txt(a, label, 13, R.color.text_secondary, Typeface.BOLD));
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        row.addView(Ui.txt(a, Fmt.amount(a, nowV), 14, R.color.text_primary, Typeface.BOLD));
        if (prevV > 0) {
            double d = (nowV - prevV) / prevV * 100;
            boolean good = lowerBetter ? d <= 0 : d >= 0;
            TextView chip = Ui.txt(a, (d >= 0 ? "▲ " : "▼ ") + Fmt.percent(a, Math.abs(d)),
                    12, good ? R.color.text_primary : R.color.expense, Typeface.BOLD);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.setMarginStart(Ui.dp(a, 8));
            chip.setLayoutParams(clp);
            row.addView(chip);
        }
        return row;
    }

    // ---------- المغلفات المقترحة ----------
    private View envelopesCard() {
        List<com.amjad.mahloula.brain.Advisor.Env> envs = com.amjad.mahloula.brain.Advisor.envelopes(a);
        if (envs.isEmpty()) return new View(a);
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "مغلفاتك المقترحة (من نمط 90 يومًا)" : "Your suggested envelopes", 16, R.color.text_primary, Typeface.BOLD));
        card.addView(Ui.txt(a, ar() ? "سقف مقترح لكل فئة = متوسطك × 0.9" : "Suggested cap per category = your average × 0.9", 12, R.color.text_tertiary));
        for (com.amjad.mahloula.brain.Advisor.Env e : envs) {
            LinearLayout item = Ui.v(a);
            item.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 13, a));
            item.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
            ilp.topMargin = Ui.dp(a, 8);
            item.setLayoutParams(ilp);
            LinearLayout head = Ui.h(a);
            head.addView(Ui.txt(a, Models.cat(e.cat).ar, 13.5f, R.color.text_primary, Typeface.BOLD));
            head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
            boolean over = e.spent > e.cap;
            head.addView(Ui.txt(a, Fmt.amount(a, e.spent) + " / " + Fmt.amount(a, e.cap), 12.5f,
                    over ? R.color.expense : R.color.text_secondary, Typeface.BOLD));
            item.addView(head);
            Charts.HBar bar = new Charts.HBar(a);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(a, 6);
            bar.setLayoutParams(blp);
            bar.set((float) (e.cap > 0 ? e.spent / e.cap : 1f), over ? R.color.expense : R.color.on_panel);
            item.addView(bar);
        }
        return card;
    }

    // ---------- الخطة الشخصية ----------
    private View planCard() {
        List<Advisor.PlanStep> plan = Advisor.personalPlan(a);
        LinearLayout card = UiKit.card(a, 18);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.panel), Ui.color(a, R.color.on_dark), 1.6f, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_sparkle, Ui.color(a, R.color.gold_600), 24));
        TextView t = Ui.txt(a, ar() ? "خطتك الشخصية لهذا الشهر" : "Your plan this month", 16, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 10));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        for (Advisor.PlanStep s : plan) {
            LinearLayout item = Ui.v(a);
            item.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 14, a));
            item.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
            ilp.topMargin = Ui.dp(a, 9);
            item.setLayoutParams(ilp);
            item.addView(Ui.txt(a, s.title, 14.5f, R.color.text_primary, Typeface.BOLD));
            TextView b = Ui.txt(a, s.body, 13, R.color.text_secondary);
            b.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(a, 4);
            b.setLayoutParams(blp);
            item.addView(b);
            card.addView(item);
        }
        return card;
    }

    // ---------- تنبيهات المستشار ----------
    private View alertsCard() {
        List<Advisor.Insight> ins = Advisor.scan(a);
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_bell, Ui.color(a, R.color.warn), 24));
        TextView t = Ui.txt(a, ar() ? "تنبيهات المستشار المالي" : "Advisor alerts", 16, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 10));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        for (Advisor.Insight in : ins) {
            card.addView(safeAlertRow(in));
        }
        return card;
    }

    private LinearLayout safeAlertRow(final Advisor.Insight in) {
        int iconRes;
        switch (in.icon) {
            case "alert": iconRes = R.drawable.ic_alert; break;
            case "trend_down": iconRes = R.drawable.ic_trend_down; break;
            case "bell": iconRes = R.drawable.ic_bell; break;
            case "target": iconRes = R.drawable.ic_target; break;
            case "piggy": iconRes = R.drawable.ic_piggy; break;
            case "pie": iconRes = R.drawable.ic_pie; break;
            case "refresh": iconRes = R.drawable.ic_refresh; break;
            case "handshake": iconRes = R.drawable.ic_handshake; break;
            case "clock": iconRes = R.drawable.ic_clock; break;
            case "swap": iconRes = R.drawable.ic_swap; break;
            case "edit": iconRes = R.drawable.ic_edit; break;
            case "shield": iconRes = R.drawable.ic_shield; break;
            default: iconRes = R.drawable.ic_check_circle; break;
        }
        int tint = in.level == 2 ? R.color.expense : in.level == 1 ? R.color.warn : R.color.brand_600;
        int bgc = in.level == 2 ? R.color.expense_soft : in.level == 1 ? R.color.warn_soft : R.color.brand_50;
        LinearLayout row = Ui.h(a);
        row.setBackground(Ui.round(Ui.color(a, bgc), 15, a));
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
        android.widget.FrameLayout badge = new android.widget.FrameLayout(a);
        badge.setBackground(Ui.round(Ui.color(a, tint), 12, a));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
        ImageView iv = Ui.iconSized(a, iconRes, Color.WHITE, 20);
        android.widget.FrameLayout.LayoutParams ilp = new android.widget.FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
        ilp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ilp);
        badge.addView(iv);
        row.addView(badge);
        LinearLayout mid = Ui.v(a);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.setMarginStart(Ui.dp(a, 11));
        mid.setLayoutParams(mlp);
        mid.addView(Ui.txt(a, in.title, 14.5f, R.color.text_primary, Typeface.BOLD));
        TextView body = Ui.txt(a, in.body, 12.5f, R.color.text_secondary);
        body.setMaxLines(4);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(a, 3);
        body.setLayoutParams(blp);
        mid.addView(body);
        row.addView(mid);
        if (in.action != null && in.actionTab >= 0) {
            row.setOnClickListener(v -> ((MainActivity) a).jump(in.actionTab));
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 9);
        row.setLayoutParams(rlp);
        return row;
    }

    // ---------- الدروس المرقّمة ----------
    private View lessonsCard() {
        long mask = Prefs.get(a).lessonsDoneMask();
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "الدروس — من الصفر إلى الاحتراف" : "Lessons — zero to pro",
                16, R.color.text_primary, Typeface.BOLD));
        card.addView(Ui.txt(a, ar() ? "اضغط أي درس لقراءته وخطوته العملية" : "Tap any lesson to read it",
                12.5f, R.color.text_tertiary));
        for (int i = 0; i < Content.LESSONS.size(); i++) {
            final int idx = i;
            Content.Lesson l = Content.LESSONS.get(i);
            boolean done = (mask & (1L << i)) != 0;
            LinearLayout row = Ui.h(a);
            row.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.surface_2), 14, a), Ui.color(a, R.color.surface_3)));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 8);
            row.setLayoutParams(rlp);

            FrameLayout num = new FrameLayout(a);
            num.setBackground(Ui.round(Ui.color(a, done ? R.color.text_primary : R.color.brand_600), 11, a));
            num.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34)));
            TextView n = Ui.txt(a, Fmt.digits(a, String.valueOf(i + 1)), 14, done ? R.color.on_dark : R.color.on_brand, Typeface.BOLD);
            n.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, -1);
            n.setLayoutParams(nlp);
            num.addView(n);
            row.addView(num);

            LinearLayout mid = Ui.v(a);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
            mlp.setMarginStart(Ui.dp(a, 12));
            mid.setLayoutParams(mlp);
            TextView title = Ui.txt(a, ar() ? l.titleAr : l.titleEn, 14.5f,
                    done ? R.color.text_tertiary : R.color.text_primary, Typeface.BOLD);
            mid.addView(title);
            TextView src = Ui.txt(a, (done ? (ar() ? "✔ مكتمل • " : "✔ done • ") : "") + (ar() ? l.sourceAr : l.sourceEn),
                    12, R.color.text_secondary);
            src.setMaxLines(1);
            mid.addView(src);
            row.addView(mid);

            row.addView(Ui.iconSized(a, done ? R.drawable.ic_check_circle : R.drawable.ic_book,
                    Ui.color(a, done ? R.color.income : R.color.text_tertiary), 20));
            row.setOnClickListener(v -> InfoSheets.lesson(a, idx));
            card.addView(row);
        }
        return card;
    }

    // ---------- الأخطاء الشائعة ----------
    private View mistakesCard() {
        LinearLayout card = UiKit.card(a, 18);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.panel), Ui.color(a, R.color.on_dark), 1.4f, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_alert, Ui.color(a, R.color.expense), 24));
        TextView t = Ui.txt(a, ar() ? "10 أخطاء مالية شائعة — وهل تقع فيها؟" : "10 common money mistakes", 16, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 10));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        for (Content.Mistake m : Content.MISTAKES) {
            LinearLayout item = Ui.v(a);
            item.setBackground(Ui.round(Ui.color(a, R.color.expense_soft), 13, a));
            item.setPadding(Ui.dp(a, 13), Ui.dp(a, 11), Ui.dp(a, 13), Ui.dp(a, 11));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
            ilp.topMargin = Ui.dp(a, 8);
            item.setLayoutParams(ilp);
            item.addView(Ui.txt(a, ar() ? "✘ " + m.ar : "✘ " + m.en, 13.5f, R.color.text_primary, Typeface.BOLD));
            TextView fix = Ui.txt(a, (ar() ? "الصواب: " + m.fixAr : "Fix: " + m.fixEn), 12.5f, R.color.income, Typeface.BOLD);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
            flp.topMargin = Ui.dp(a, 4);
            fix.setLayoutParams(flp);
            item.addView(fix);
            card.addView(item);
        }
        return card;
    }

    // ---------- القواعد العالمية (قائمة أنيقة) ----------
    private View rulesCard() {
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "القواعد العالمية الذهبية" : "Golden global rules", 16, R.color.text_primary, Typeface.BOLD));
        card.addView(Ui.txt(a, ar() ? "اضغط أي قاعدة لشرحها" : "Tap any rule for details", 12.5f, R.color.text_tertiary));
        for (int i = 0; i < Content.RULES.size(); i++) {
            final int idx = i;
            Content.Rule r = Content.RULES.get(idx);
            LinearLayout row = Ui.h(a);
            row.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.surface_2), 13, a), Ui.color(a, R.color.surface_3)));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 7);
            row.setLayoutParams(rlp);
            LinearLayout mid = Ui.v(a);
            mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            mid.addView(Ui.txt(a, ar() ? r.nameAr : r.nameEn, 14, R.color.text_primary, Typeface.BOLD));
            TextView tag = Ui.txt(a, ar() ? r.tagAr : "", 11.5f, R.color.brand_600, Typeface.BOLD);
            mid.addView(tag);
            row.addView(mid);
            row.addView(Ui.iconSized(a, R.drawable.ic_bulb, Ui.color(a, R.color.gold_600), 19));
            row.setOnClickListener(v -> InfoSheets.rule(a, idx));
            card.addView(row);
        }
        return card;
    }

    @Override
    public void onShow() { refresh(); }
}
