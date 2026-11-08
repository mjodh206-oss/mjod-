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
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.MainActivity;
import com.amjad.mahloula.ui.Panel;
import com.amjad.mahloula.ui.kit.Charts;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.InfoSheets;
import com.amjad.mahloula.ui.sheets.Sheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.Calendar;
import java.util.List;

/**
 * الرئيسية — نسخة مبسّطة عالية الوضوح:
 * رصيدك، صحة مالية، ملخص الشهر، الميزانية، أكبر فئتين، تنبيهان، وأزرار واضحة.
 */
public class DashboardPanel implements Panel {

    private final Activity a;
    private ScrollView scroll;
    private LinearLayout col;

    public DashboardPanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        col = Ui.v(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 26));
        scroll.addView(col);
        return scroll;
    }

    @Override
    public void refresh() {
        if (col == null) return;
        col.removeAllViews();
        Repo r = Repo.get(a);
        Calendar month = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(month);

        col.addView(header(r));
        col.addView(summaryCard(r, mt, month));
        col.addView(budgetCard(mt));
        col.addView(topCatsCard(r, month));
        col.addView(advisorCard());
        col.addView(quickActions());
    }

    // ---------- الترويسة: رصيد + صحة + خصوصية ----------
    private View header(Repo r) {
        Prefs p = Prefs.get(a);
        LinearLayout card = Ui.v(a);
        card.setBackground(Ui.gradient(a, new int[]{
                Ui.color(a, R.color.brand_900), Ui.color(a, R.color.brand_600)}, 26, 1));
        card.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 22));

        LinearLayout top = Ui.h(a);
        LinearLayout greet = Ui.v(a);
        greet.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        greet.addView(Ui.txt(a, a.getString(R.string.app_name), 24, R.color.on_brand, Typeface.BOLD));
        greet.addView(Ui.txt(a, (ar() ? "أهلًا بك • " : "Welcome • ") + Fmt.dayMonth(a, System.currentTimeMillis()), 12.5f, R.color.brand_100));
        top.addView(greet);

        ImageView eye = Ui.iconSized(a, p.hideBalance() ? R.drawable.ic_eye_off : R.drawable.ic_eye,
                Ui.color(a, R.color.brand_100), 24);
        eye.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        eye.setBackground(Ui.ripple(a, Ui.round(Color.argb(35, 255, 255, 255), 12, a), Color.argb(45, 255, 255, 255)));
        eye.setOnClickListener(v -> { p.hideBalance(!p.hideBalance()); refresh(); });
        top.addView(eye);

        ImageView gear = Ui.iconSized(a, R.drawable.ic_gear, Ui.color(a, R.color.brand_100), 24);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36));
        glp.setMarginStart(Ui.dp(a, 8));
        gear.setLayoutParams(glp);
        gear.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        gear.setBackground(Ui.ripple(a, Ui.round(Color.argb(35, 255, 255, 255), 12, a), Color.argb(45, 255, 255, 255)));
        gear.setOnClickListener(v -> InfoSheets.settings(a));
        top.addView(gear);
        card.addView(top);

        TextView balLabel = Ui.txt(a, ar() ? "رصيدك الكلي" : "Total balance", 13.5f, R.color.brand_200, Typeface.BOLD);
        LinearLayout.LayoutParams bllp = new LinearLayout.LayoutParams(-2, -2);
        bllp.topMargin = Ui.dp(a, 18);
        balLabel.setLayoutParams(bllp);
        card.addView(balLabel);

        LinearLayout balRow = Ui.h(a);
        String balTxt = p.hideBalance() ? "••••••" : Fmt.money(a, r.totalBalance());
        TextView bal = Ui.txt(a, balTxt, 34, R.color.on_brand, Typeface.BOLD);
        balRow.addView(bal);
        balRow.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));

        int score = Advisor.healthScore(a);
        Charts.Ring ring = new Charts.Ring(a);
        ring.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 82), Ui.dp(a, 82)));
        ring.set(score / 100f, score >= 70 ? R.color.gold_400 : score >= 45 ? R.color.brand_300 : R.color.expense,
                Fmt.digits(a, String.valueOf(score)), ar() ? "صحتك المالية" : "health");
        balRow.addView(ring);
        card.addView(balRow);
        card.addView(Ui.txt(a, Advisor.healthLabel(a, score), 13, R.color.brand_100, Typeface.BOLD));
        return card;
    }

    // ---------- ملخص الشهر: 3 أرقام + مقارنة + أعمدة ----------
    private View summaryCard(Repo r, Models.MonthTotals mt, Calendar month) {
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, Fmt.monthName(a, month), 16.5f, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        // مقارنة بالشهر الماضي
        Calendar prev = (Calendar) month.clone();
        prev.add(Calendar.MONTH, -1);
        double prevExp = r.monthTotals(prev).expense;
        if (prevExp > 0 && mt.expense > 0) {
            double delta = (mt.expense - prevExp) / prevExp * 100;
            TextView chip = Ui.txt(a, (delta > 0 ? "▲ " : "▼ ") + Fmt.percent(a, Math.abs(delta)),
                    12.5f, delta > 0 ? R.color.expense : R.color.income, Typeface.BOLD);
            chip.setBackground(Ui.round(Ui.color(a, delta > 0 ? R.color.expense_soft : R.color.income_soft), 10, a));
            chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 10), Ui.dp(a, 5));
            head.addView(chip);
        }
        card.addView(head);
        card.addView(Ui.txt(a, ar() ? "مقارنة مصروفك بالشهر الماضي" : "vs last month's spending",
                12, R.color.text_tertiary));

        LinearLayout nums = Ui.h(a);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(a, 14);
        nums.setLayoutParams(nlp);
        nums.addView(numCell(ar() ? "دخل الشهر" : "Income", mt.income, R.color.income, R.drawable.ic_trend_up));
        nums.addView(numCell(ar() ? "مصروف الشهر" : "Spent", mt.expense, R.color.expense, R.drawable.ic_trend_down));
        nums.addView(numCell(ar() ? "الصافي" : "Net", mt.net(), mt.net() >= 0 ? R.color.brand_600 : R.color.expense, R.drawable.ic_wallet));
        card.addView(nums);

        Charts.Bars bars = new Charts.Bars(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 92));
        blp.topMargin = Ui.dp(a, 14);
        bars.setLayoutParams(blp);
        bars.setData(r.dailySeries(month));
        card.addView(bars);
        card.addView(Charts.caption(a, ar() ? "مصروفك يومًا بيوم" : "Day-by-day spending"));
        return card;
    }

    private View numCell(String label, double v, int colorRes, int icon) {
        LinearLayout cell = Ui.v(a);
        cell.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 16, a));
        cell.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
        cell.setLayoutParams(lp);
        LinearLayout lh = Ui.h(a);
        lh.addView(Ui.iconSized(a, icon, Ui.color(a, colorRes), 16));
        TextView lt = Ui.txt(a, label, 12, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams ltlp = new LinearLayout.LayoutParams(-2, -2);
        ltlp.setMarginStart(Ui.dp(a, 6));
        lt.setLayoutParams(ltlp);
        lh.addView(lt);
        cell.addView(lh);
        TextView val = Ui.txt(a, Fmt.amount(a, v), 19, colorRes, Typeface.BOLD);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = Ui.dp(a, 6);
        val.setLayoutParams(vlp);
        cell.addView(val);
        return cell;
    }

    // ---------- الميزانية ----------
    private View budgetCard(Models.MonthTotals mt) {
        Prefs p = Prefs.get(a);
        double budget = p.monthlyBudget();
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        if (budget <= 0) {
            LinearLayout row = Ui.h(a);
            row.addView(Ui.iconSized(a, R.drawable.ic_target, Ui.color(a, R.color.gold_600), 30));
            LinearLayout tx = Ui.v(a);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.setMarginStart(Ui.dp(a, 12));
            tx.setLayoutParams(tlp);
            tx.addView(Ui.txt(a, ar() ? "ضع سقفًا لمصروفك الشهري" : "Set your monthly cap", 15.5f, R.color.text_primary, Typeface.BOLD));
            tx.addView(Ui.txt(a, ar() ? "بدونه لا تعمل تنبيهات الحماية" : "Without it, protection alerts can't work", 13, R.color.text_secondary));
            row.addView(tx);
            card.addView(row);
            TextView btn = UiKit.button(a, ar() ? "ضبط الميزانية الآن" : "Set budget now", 0, true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(a, 12);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(v -> InfoSheets.settings(a));
            card.addView(btn);
            return card;
        }
        double ratio = mt.expense / budget;
        int colRes = ratio >= 1 ? R.color.expense : ratio >= 0.8 ? R.color.warn : R.color.income;
        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "ميزانية الشهر" : "Monthly budget", 15.5f, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(Ui.txt(a, Fmt.percent(a, ratio * 100), 17, colRes, Typeface.BOLD));
        card.addView(head);
        Charts.HBar bar = new Charts.HBar(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 12);
        bar.setLayoutParams(blp);
        bar.set((float) ratio, colRes);
        card.addView(bar);
        LinearLayout foot = Ui.h(a);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(a, 10);
        foot.setLayoutParams(flp);
        foot.addView(Ui.txt(a, (ar() ? "أُنفق: " : "Spent: ") + Fmt.money(a, mt.expense), 13.5f, R.color.text_secondary, Typeface.BOLD));
        foot.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        foot.addView(Ui.txt(a, (ar() ? "تبقّى: " : "Left: ") + Fmt.money(a, Math.max(0, budget - mt.expense)), 13.5f, colRes, Typeface.BOLD));
        card.addView(foot);
        return card;
    }

    // ---------- أكبر فئتين فقط (تبسيط) ----------
    private View topCatsCard(Repo r, Calendar month) {
        List<Models.CatTotal> cats = r.catTotals(
                Fmt.startOfMonth(month).getTimeInMillis(), Fmt.endOfMonth(month).getTimeInMillis(), 0);
        if (cats.isEmpty()) return new View(a);
        double total = 0;
        for (Models.CatTotal c : cats) total += c.total;
        LinearLayout card = UiKit.card(a, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "أين يذهب مالك؟" : "Where money goes", 15.5f, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView more = Ui.txt(a, ar() ? "التفاصيل ←" : "Details ←", 13, R.color.brand_600, Typeface.BOLD);
        more.setOnClickListener(v -> ((MainActivity) a).jump(1));
        head.addView(more);
        card.addView(head);
        int shown = Math.min(2, cats.size());
        for (int i = 0; i < shown; i++) {
            View row = Charts.legendRow(a, cats.get(i), total);
            row.setBackground(Ui.round(Ui.color(a, R.color.surface_2), 14, a));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 8);
            row.setLayoutParams(rlp);
            card.addView(row);
        }
        return card;
    }

    // ---------- تنبيهان + عرض الكل ----------
    private View advisorCard() {
        List<Advisor.Insight> ins = Advisor.scan(a);
        LinearLayout card = UiKit.card(a, 18);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.surface), Ui.color(a, R.color.gold_500), 1.6f, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_sparkle, Ui.color(a, R.color.gold_600), 24));
        TextView t = Ui.txt(a, ar() ? "مستشارك ينبهك" : "Advisor alerts", 15.5f, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 10));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        int shown = Math.min(2, ins.size());
        for (int i = 0; i < shown; i++) {
            card.addView(insightRow(ins.get(i)));
        }
        if (ins.size() > 2) {
            TextView all = Ui.txt(a, ar()
                    ? "عرض كل التنبيهات والخطط (" + Fmt.digits(a, String.valueOf(ins.size())) + ")"
                    : "See all alerts & plans (" + ins.size() + ")", 13.5f, R.color.brand_600, Typeface.BOLD);
            all.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 2));
            all.setOnClickListener(v -> ((MainActivity) a).jump(4));
            card.addView(all);
        }
        return card;
    }

    private View insightRow(final Advisor.Insight in) {
        LinearLayout row = safeAlertRow(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 10);
        row.setLayoutParams(lp);
        return row;
    }

    /** صف تنبيه مبني يدويًا — بدون أي افتراضات عن بنية جاهزة. */
    private LinearLayout safeAlertRow(final Advisor.Insight in) {
        int iconRes = iconByName(in.icon);
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
        body.setMaxLines(3);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(a, 3);
        body.setLayoutParams(blp);
        mid.addView(body);
        row.addView(mid);
        if (in.action != null && in.actionTab >= 0) {
            row.setOnClickListener(v -> ((MainActivity) a).jump(in.actionTab));
        }
        return row;
    }

    private int iconByName(String n) {
        switch (n) {
            case "alert": return R.drawable.ic_alert;
            case "trend_down": return R.drawable.ic_trend_down;
            case "bell": return R.drawable.ic_bell;
            case "target": return R.drawable.ic_target;
            case "piggy": return R.drawable.ic_piggy;
            case "pie": return R.drawable.ic_pie;
            case "refresh": return R.drawable.ic_refresh;
            case "handshake": return R.drawable.ic_handshake;
            case "clock": return R.drawable.ic_clock;
            case "swap": return R.drawable.ic_swap;
            case "edit": return R.drawable.ic_edit;
            case "shield": return R.drawable.ic_shield;
            case "check_circle": return R.drawable.ic_check_circle;
            default: return R.drawable.ic_sparkle;
        }
    }

    // ---------- 4 أزرار كبيرة واضحة ----------
    private View quickActions() {
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 16);
        row.setLayoutParams(lp);
        row.addView(qa(R.drawable.ic_receipt, ar() ? "سجّل مصروفًا" : "Add expense", R.color.expense, () -> Sheets.tx(a, null, 0)));
        row.addView(qa(R.drawable.ic_trend_up, ar() ? "سجّل دخلًا" : "Add income", R.color.income, () -> Sheets.tx(a, null, 1)));
        row.addView(qa(R.drawable.ic_handshake, ar() ? "الديون" : "Debts", R.color.debt, () -> ((MainActivity) a).jump(2)));
        row.addView(qa(R.drawable.ic_cap, ar() ? "تعلّم" : "Learn", R.color.brand_600, () -> ((MainActivity) a).jump(4)));
        return row;
    }

    private View qa(int icon, String label, int colorRes, Runnable run) {
        LinearLayout b = Ui.v(a);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        b.setLayoutParams(lp);
        b.setBackground(Ui.ripple(a, Ui.roundStroke(a, Ui.color(a, R.color.surface), Ui.color(a, R.color.outline_strong), 1.2f, 18),
                Ui.color(a, R.color.surface_3)));
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.round(Ui.color(a, colorRes), 14, a));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
        ImageView iv = Ui.iconSized(a, icon, Color.WHITE, 23);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 23), Ui.dp(a, 23));
        ilp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ilp);
        badge.addView(iv);
        b.addView(badge);
        TextView t = Ui.txt(a, label, 12.5f, R.color.text_primary, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(a, 8);
        t.setLayoutParams(tlp);
        b.addView(t);
        b.setOnClickListener(v -> run.run());
        return b;
    }
}
