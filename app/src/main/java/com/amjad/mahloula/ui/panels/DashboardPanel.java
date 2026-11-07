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

/** الخانة الرئيسية: نبض مالك كاملًا في شاشة واحدة. */
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
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 24));
        scroll.addView(col);
        return scroll;
    }

    @Override
    public void refresh() {
        if (col == null) return;
        col.removeAllViews();
        Repo r = Repo.get(a);
        Prefs p = Prefs.get(a);
        Calendar month = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(month);

        col.addView(header(r, mt));
        col.addView(summaryCard(mt, month));
        col.addView(budgetCard(mt));
        col.addView(catsCard(month));
        col.addView(goalsCard(r));
        col.addView(advisorCard());
        col.addView(quickActions());
    }

    // ---------------- الترويسة ----------------

    private View header(Repo r, Models.MonthTotals mt) {
        LinearLayout card = Ui.v(a);
        card.setBackground(Ui.gradient(a, new int[]{
                Ui.color(a, R.color.brand_800), Ui.color(a, R.color.brand_600)}, 24, 1));
        card.setPadding(Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 20));

        LinearLayout top = Ui.h(a);
        LinearLayout greet = Ui.v(a);
        greet.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView hello = Ui.txt(a, ar() ? "أهلًا بك 👋" : "Hello 👋", 13.5f, R.color.brand_200);
        greet.addView(hello);
        TextView date = Ui.txt(a, Fmt.fullDate(a, System.currentTimeMillis()), 12, R.color.brand_300);
        greet.addView(date);
        top.addView(greet);

        ImageView gear = Ui.iconSized(a, R.drawable.ic_gear, Ui.color(a, R.color.brand_100), 24);
        gear.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        gear.setBackground(Ui.ripple(a, Ui.round(Color.argb(30, 255, 255, 255), 12, a), Color.argb(40, 255, 255, 255)));
        gear.setOnClickListener(v -> InfoSheets.settings(a));
        top.addView(gear);
        card.addView(top);

        TextView balLabel = Ui.txt(a, ar() ? "إجمالي رصيدك" : "Total balance", 13, R.color.brand_200);
        LinearLayout.LayoutParams bllp = new LinearLayout.LayoutParams(-2, -2);
        bllp.topMargin = Ui.dp(a, 16);
        balLabel.setLayoutParams(bllp);
        card.addView(balLabel);

        LinearLayout balRow = Ui.h(a);
        TextView bal = Ui.txt(a, Fmt.money(a, r.totalBalance()), 32, R.color.on_brand, Typeface.BOLD);
        balRow.addView(bal);
        balRow.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));

        Charts.Ring ring = new Charts.Ring(a);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 74));
        ring.setLayoutParams(rlp);
        int score = Advisor.healthScore(a);
        int ringColor = score >= 70 ? R.color.gold_400 : score >= 45 ? R.color.brand_300 : R.color.expense;
        ring.set(score / 100f, ringColor, Fmt.digits(a, String.valueOf(score)), ar() ? "صحة مالية" : "health");
        balRow.addView(ring);
        card.addView(balRow);

        TextView hint = Ui.txt(a, Advisor.healthLabel(a, score), 12, R.color.brand_200);
        card.addView(hint);

        // محافظ مصغّرة
        List<Models.Wallet> ws = r.wallets();
        if (ws.size() > 1) {
            UiKit.HorizontalScroll hs = new UiKit.HorizontalScroll(a);
            LinearLayout row = Ui.h(a);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.topMargin = Ui.dp(a, 12);
            hs.setLayoutParams(hlp);
            for (Models.Wallet w : ws) {
                LinearLayout chip = Ui.h(a);
                chip.setBackground(Ui.round(Color.argb(35, 255, 255, 255), 12, a));
                chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
                clp.setMarginEnd(Ui.dp(a, 8));
                chip.setLayoutParams(clp);
                chip.addView(Ui.txt(a, w.name + ": ", 11.5f, R.color.brand_100));
                chip.addView(Ui.txt(a, Fmt.amount(a, r.walletBalance(w.id)), 12, R.color.on_brand, Typeface.BOLD));
                row.addView(chip);
            }
            hs.addView(row);
            card.addView(hs);
        }

        TextView addWallet = Ui.txt(a, ar() ? "+ إضافة محفظة/حساب" : "+ Add wallet/account", 12, R.color.gold_400, Typeface.BOLD);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(-2, -2);
        awlp.topMargin = Ui.dp(a, 8);
        addWallet.setLayoutParams(awlp);
        addWallet.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        addWallet.setOnClickListener(v -> Sheets.wallet(a, null));
        card.addView(addWallet);
        return card;
    }

    // ---------------- ملخص الشهر ----------------

    private View summaryCard(Models.MonthTotals mt, Calendar month) {
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);

        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, Fmt.monthName(a, month), 15, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(Ui.txt(a, ar() ? "ملخص الشهر" : "Month summary", 12, R.color.text_tertiary));
        card.addView(head);

        LinearLayout nums = Ui.h(a);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(a, 10);
        nums.setLayoutParams(nlp);
        nums.addView(numCell(ar() ? "دخل" : "Income", mt.income, R.color.income, R.drawable.ic_trend_up));
        nums.addView(numCell(ar() ? "مصروف" : "Spent", mt.expense, R.color.expense, R.drawable.ic_trend_down));
        nums.addView(numCell(ar() ? "الصافي" : "Net", mt.net(), mt.net() >= 0 ? R.color.brand_600 : R.color.expense, R.drawable.ic_wallet));
        card.addView(nums);

        Charts.Bars bars = new Charts.Bars(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 84));
        blp.topMargin = Ui.dp(a, 12);
        bars.setLayoutParams(blp);
        bars.setData(Repo.get(a).dailySeries(month));
        card.addView(bars);
        card.addView(Charts.caption(a, ar() ? "مصروفك يومًا بيوم خلال الشهر" : "Your day-by-day spending this month"));
        return card;
    }

    private View numCell(String label, double v, int colorRes, int icon) {
        LinearLayout cell = Ui.v(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        cell.setLayoutParams(lp);
        LinearLayout lh = Ui.h(a);
        lh.addView(Ui.iconSized(a, icon, Ui.color(a, colorRes), 15));
        TextView lt = Ui.txt(a, label, 12, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams ltlp = new LinearLayout.LayoutParams(-2, -2);
        ltlp.setMarginStart(Ui.dp(a, 5));
        lt.setLayoutParams(ltlp);
        lh.addView(lt);
        cell.addView(lh);
        TextView val = Ui.txt(a, Fmt.amount(a, v), 17, colorRes, Typeface.BOLD);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = Ui.dp(a, 4);
        val.setLayoutParams(vlp);
        cell.addView(val);
        return cell;
    }

    // ---------------- الميزانية ----------------

    private View budgetCard(Models.MonthTotals mt) {
        Prefs p = Prefs.get(a);
        double budget = p.monthlyBudget();
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        if (budget <= 0) {
            LinearLayout row = Ui.h(a);
            row.addView(Ui.iconSized(a, R.drawable.ic_target, Ui.color(a, R.color.gold_600), 26));
            LinearLayout tx = Ui.v(a);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.setMarginStart(Ui.dp(a, 10));
            tx.setLayoutParams(tlp);
            tx.addView(Ui.txt(a, ar() ? "ضع سقفًا لمصروفك الشهري" : "Set your monthly spending cap", 14.5f, R.color.text_primary, Typeface.BOLD));
            TextView s = Ui.txt(a, ar() ? "بدون سقف لا تعمل تنبيهات الحماية." : "Without a cap, protection alerts can't work.", 12.5f, R.color.text_secondary);
            tx.addView(s);
            row.addView(tx);
            card.addView(row);
            TextView btn = UiKit.button(a, ar() ? "ضبط الميزانية" : "Set budget", 0, true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(a, 10);
            btn.setLayoutParams(blp);
            btn.setOnClickListener(v -> InfoSheets.settings(a));
            card.addView(btn);
            return card;
        }
        double ratio = mt.expense / budget;
        int colRes = ratio >= 1 ? R.color.expense : ratio >= 0.8 ? R.color.warn : R.color.brand_500;
        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "ميزانية الشهر" : "Monthly budget", 14.5f, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(Ui.txt(a, Fmt.percent(a, ratio * 100), 15, colRes, Typeface.BOLD));
        card.addView(head);
        Charts.HBar bar = new Charts.HBar(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 10);
        bar.setLayoutParams(blp);
        bar.set((float) ratio, colRes);
        card.addView(bar);
        LinearLayout foot = Ui.h(a);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(a, 8);
        foot.setLayoutParams(flp);
        foot.addView(Ui.txt(a, (ar() ? "أُنفق: " : "Spent: ") + Fmt.money(a, mt.expense), 12.5f, R.color.text_secondary));
        foot.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        foot.addView(Ui.txt(a, (ar() ? "تبقّى: " : "Left: ") + Fmt.money(a, Math.max(0, budget - mt.expense)), 12.5f, colRes, Typeface.BOLD));
        card.addView(foot);
        return card;
    }

    // ---------------- الفئات ----------------

    private View catsCard(Calendar month) {
        Repo r = Repo.get(a);
        List<Models.CatTotal> cats = r.catTotals(
                Fmt.startOfMonth(month).getTimeInMillis(), Fmt.endOfMonth(month).getTimeInMillis(), 0);
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "أين يذهب مصروفك؟" : "Where does money go?", 15, R.color.text_primary, Typeface.BOLD));
        if (cats.isEmpty()) {
            TextView s = Ui.txt(a, ar() ? "لا مصاريف مسجلة هذا الشهر بعد." : "No expenses logged this month yet.", 13, R.color.text_secondary);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(a, 8);
            s.setLayoutParams(slp);
            card.addView(s);
            return card;
        }
        double total = 0;
        for (Models.CatTotal c : cats) total += c.total;
        LinearLayout body = Ui.h(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 10);
        body.setLayoutParams(blp);
        Charts.Donut donut = new Charts.Donut(a);
        donut.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 118), Ui.dp(a, 118)));
        donut.setData(cats, total);
        FrameLayout dw = new FrameLayout(a);
        dw.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 128), Ui.dp(a, 128)));
        dw.addView(donut, new FrameLayout.LayoutParams(Ui.dp(a, 118), Ui.dp(a, 118), Gravity.CENTER));
        TextView center = Ui.txt(a, Fmt.amount(a, total), 14, R.color.text_primary, Typeface.BOLD);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        center.setLayoutParams(clp);
        dw.addView(center);
        body.addView(dw);
        LinearLayout legend = Ui.v(a);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
        llp.setMarginStart(Ui.dp(a, 12));
        legend.setLayoutParams(llp);
        int shown = Math.min(5, cats.size());
        for (int i = 0; i < shown; i++) {
            View row = Charts.legendRow(a, cats.get(i), total);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 5);
            row.setLayoutParams(rlp);
            legend.addView(row);
        }
        body.addView(legend);
        card.addView(body);
        return card;
    }

    // ---------------- الأهداف ----------------

    private View goalsCard(Repo r) {
        List<Models.Goal> goals = r.goals();
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "أهداف الادخار" : "Savings goals", 15, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView add = Ui.txt(a, ar() ? "+ هدف" : "+ Goal", 13, R.color.brand_600, Typeface.BOLD);
        add.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        add.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.brand_50), 10, a), Ui.color(a, R.color.brand_200)));
        add.setOnClickListener(v -> Sheets.goal(a, null));
        head.addView(add);
        card.addView(head);
        if (goals.isEmpty()) {
            TextView s = Ui.txt(a, ar()
                    ? "لا أهداف بعد. ابدأ بهدف «صندوق الطوارئ» — توصية عالمية: 3 أشهر مصاريف."
                    : "No goals yet. Start with an emergency fund — 3 months of expenses.", 13, R.color.text_secondary);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(a, 8);
            s.setLayoutParams(slp);
            card.addView(s);
            return card;
        }
        for (Models.Goal g : goals) {
            LinearLayout item = Ui.v(a);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
            ilp.topMargin = Ui.dp(a, 10);
            item.setLayoutParams(ilp);
            LinearLayout row = Ui.h(a);
            row.addView(Ui.txt(a, g.title, 13.5f, R.color.text_primary, Typeface.BOLD));
            row.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
            row.addView(Ui.txt(a, Fmt.amount(a, g.saved) + " / " + Fmt.amount(a, g.target), 12.5f, R.color.text_secondary, Typeface.BOLD));
            item.addView(row);
            Charts.HBar bar = new Charts.HBar(a);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(a, 6);
            bar.setLayoutParams(blp);
            bar.set((float) (g.target > 0 ? g.saved / g.target : 0), g.saved >= g.target ? R.color.income : R.color.brand_500);
            item.addView(bar);
            item.setOnClickListener(v -> Sheets.goal(a, g));
            card.addView(item);
        }
        return card;
    }

    // ---------------- المستشار ----------------

    private View advisorCard() {
        List<Advisor.Insight> ins = Advisor.scan(a);
        LinearLayout card = UiKit.card(a, 16);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.surface), Ui.color(a, R.color.gold_400), 1.2f, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_sparkle, Ui.color(a, R.color.gold_600), 22));
        TextView t = Ui.txt(a, ar() ? "مستشارك المالي ينبهك" : "Your money advisor", 15, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 8));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        int shown = Math.min(3, ins.size());
        for (int i = 0; i < shown; i++) {
            final Advisor.Insight in = ins.get(i);
            card.addView(insightRow(in, i == shown - 1));
        }
        return card;
    }

    private View insightRow(final Advisor.Insight in, boolean last) {
        int iconRes = iconByName(in.icon);
        int tint = in.level == 2 ? R.color.expense : in.level == 1 ? R.color.warn : R.color.brand_600;
        int bgc = in.level == 2 ? R.color.expense_soft : in.level == 1 ? R.color.warn_soft : R.color.brand_50;
        LinearLayout row = UiKit.row(a, iconRes, tint, bgc, in.title, in.body, null, 0);
        LinearLayout mid = (LinearLayout) row.getChildAt(1);
        TextView sub = (TextView) mid.getChildAt(1);
        sub.setMaxLines(3);
        if (in.action != null && in.actionTab >= 0) {
            TextView act = Ui.txt(a, in.action + " ←", 12.5f, R.color.brand_600, Typeface.BOLD);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
            alp.topMargin = Ui.dp(a, 4);
            act.setLayoutParams(alp);
            mid.addView(act);
            row.setOnClickListener(v -> ((MainActivity) a).jump(in.actionTab));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 8);
        row.setLayoutParams(lp);
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

    // ---------------- إجراءات سريعة ----------------

    private View quickActions() {
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 14);
        row.setLayoutParams(lp);
        row.addView(qa(R.drawable.ic_receipt, ar() ? "مسحوب" : "Expense", R.color.expense, () -> Sheets.tx(a, null, 0)));
        row.addView(qa(R.drawable.ic_trend_up, ar() ? "دخل" : "Income", R.color.income, () -> Sheets.tx(a, null, 1)));
        row.addView(qa(R.drawable.ic_handshake, ar() ? "دين" : "Debt", R.color.debt, () -> Sheets.debt(a, null)));
        row.addView(qa(R.drawable.ic_save_disk, ar() ? "نسخة" : "Backup", R.color.info, () -> InfoSheets.backup(a)));
        return row;
    }

    private View qa(int icon, String label, int colorRes, Runnable run) {
        LinearLayout b = Ui.v(a);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        b.setLayoutParams(lp);
        b.setBackground(Ui.ripple(a, Ui.roundStroke(a, Ui.color(a, R.color.surface), Ui.color(a, R.color.outline), 1, 16),
                Ui.color(a, R.color.surface_3)));
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.round(Ui.color(a, colorRes), 12, a));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        ImageView iv = Ui.iconSized(a, icon, Color.WHITE, 20);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
        ilp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ilp);
        badge.addView(iv);
        b.addView(badge);
        TextView t = Ui.txt(a, label, 12, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(a, 6);
        t.setLayoutParams(tlp);
        b.addView(t);
        b.setOnClickListener(v -> run.run());
        return b;
    }
}
