package com.amjad.mahloula.ui.panels;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
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

/** الخانة التعليمية: دروس، قواعد عالمية، خطتك الشخصية، وتنبيهات المستشار. */
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
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(col);
        return sv;
    }

    @Override
    public void refresh() {
        if (col == null) return;
        col.removeAllViews();
        Prefs p = Prefs.get(a);

        col.addView(header());
        col.addView(myNumbers());
        col.addView(planCard());
        col.addView(alertsCard());
        col.addView(lessonsCard());
        col.addView(rulesCard());
    }

    private View header() {
        LinearLayout card = UiKit.card(a, 16);
        card.setBackground(Ui.gradient(a, new int[]{Ui.color(a, R.color.brand_800), Ui.color(a, R.color.brand_500)}, 22, 1));
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_cap, Ui.color(a, R.color.gold_400), 30));
        LinearLayout tx = Ui.v(a);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 12));
        tx.setLayoutParams(tlp);
        tx.addView(Ui.txt(a, ar() ? "أكاديميتك المالية" : "Your money academy", 18, R.color.on_brand, Typeface.BOLD));
        long mask = Prefs.get(a).lessonsDoneMask();
        int done = Long.bitCount(mask);
        tx.addView(Ui.txt(a, ar()
                ? Fmt.digits(a, String.valueOf(done)) + " من " + Fmt.digits(a, String.valueOf(Content.LESSONS.size())) + " درسًا مكتملًا"
                : done + " of " + Content.LESSONS.size() + " lessons done", 12.5f, R.color.brand_200));
        head.addView(tx);
        card.addView(head);
        Charts.HBar bar = new Charts.HBar(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 12);
        bar.setLayoutParams(blp);
        bar.set((float) done / Content.LESSONS.size(), R.color.gold_400);
        card.addView(bar);
        return card;
    }

    private View myNumbers() {
        Repo r = Repo.get(a);
        Prefs p = Prefs.get(a);
        Calendar now = Calendar.getInstance();
        Models.MonthTotals mt = r.monthTotals(now);
        double incomeRef = mt.income > 0 ? mt.income : p.monthlyIncome();
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "أرقامك هذا الشهر" : "Your numbers this month", 15, R.color.text_primary, Typeface.BOLD));
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 10);
        row.setLayoutParams(rlp);

        int score = Advisor.healthScore(a);
        Charts.Ring ring = new Charts.Ring(a);
        ring.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 96), Ui.dp(a, 96)));
        ring.set(score / 100f, score >= 70 ? R.color.income : score >= 45 ? R.color.gold_500 : R.color.expense,
                Fmt.digits(a, String.valueOf(score)), ar() ? "من 100" : "/100");
        row.addView(ring);

        LinearLayout stats = Ui.v(a);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.setMarginStart(Ui.dp(a, 14));
        stats.setLayoutParams(slp);
        double sr = incomeRef > 0 ? mt.saved / incomeRef * 100 : 0;
        double er = incomeRef > 0 ? mt.expense / incomeRef * 100 : 0;
        double netWorth = r.totalBalance() - r.debtsOwedByMe() + r.debtsOwedToMe();
        stats.addView(statLine(ar() ? "معدل الادخار" : "Savings rate", Fmt.percent(a, sr), sr >= 10 ? R.color.income : R.color.warn));
        stats.addView(statLine(ar() ? "المصروف من الدخل" : "Spend of income", Fmt.percent(a, er), er <= 80 ? R.color.income : R.color.expense));
        stats.addView(statLine(ar() ? "صافي ثروتك" : "Net worth", Fmt.money(a, netWorth), netWorth >= 0 ? R.color.brand_600 : R.color.expense));
        row.addView(stats);
        card.addView(row);
        TextView lbl = Ui.txt(a, Advisor.healthLabel(a, score), 12.5f, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = Ui.dp(a, 8);
        lbl.setLayoutParams(llp);
        card.addView(lbl);
        return card;
    }

    private View statLine(String label, String value, int colorRes) {
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 6);
        row.setLayoutParams(lp);
        row.addView(Ui.txt(a, label, 12.5f, R.color.text_secondary));
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        row.addView(Ui.txt(a, value, 13.5f, colorRes, Typeface.BOLD));
        return row;
    }

    private View planCard() {
        List<Advisor.PlanStep> plan = Advisor.personalPlan(a);
        LinearLayout card = UiKit.card(a, 16);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.surface), Ui.color(a, R.color.gold_400), 1.2f, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_sparkle, Ui.color(a, R.color.gold_600), 22));
        TextView t = Ui.txt(a, ar() ? "خطتك الشخصية لهذا الشهر" : "Your personal plan this month", 15, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 8));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        TextView sub = Ui.txt(a, ar()
                ? "مبنية على بياناتك الحقيقية + أفضل الممارسات العالمية"
                : "Built from your real data + global best practices", 12, R.color.text_tertiary);
        card.addView(sub);
        for (Advisor.PlanStep s : plan) {
            LinearLayout item = Ui.v(a);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
            ilp.topMargin = Ui.dp(a, 10);
            item.setLayoutParams(ilp);
            item.addView(Ui.txt(a, s.title, 14, R.color.text_primary, Typeface.BOLD));
            TextView b = Ui.txt(a, s.body, 12.5f, R.color.text_secondary);
            b.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(a, 3);
            b.setLayoutParams(blp);
            item.addView(b);
            card.addView(item);
        }
        return card;
    }

    private View alertsCard() {
        List<Advisor.Insight> ins = Advisor.scan(a);
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        LinearLayout head = Ui.h(a);
        head.addView(Ui.iconSized(a, R.drawable.ic_bell, Ui.color(a, R.color.warn), 22));
        TextView t = Ui.txt(a, ar() ? "تنبيهات المستشار المالي" : "Advisor alerts", 15, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(Ui.dp(a, 8));
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);
        for (Advisor.Insight in : ins) {
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
            LinearLayout row = UiKit.row(a, iconRes, tint, bgc, in.title, in.body, null, 0);
            ((TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1)).setMaxLines(4);
            if (in.action != null && in.actionTab >= 0) {
                row.setOnClickListener(v -> ((MainActivity) a).jump(in.actionTab));
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 8);
            row.setLayoutParams(rlp);
            card.addView(row);
        }
        return card;
    }

    private View lessonsCard() {
        long mask = Prefs.get(a).lessonsDoneMask();
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "الدروس — كيف تدير مصاريفك كالمحترفين" : "Lessons — manage money like the pros",
                15, R.color.text_primary, Typeface.BOLD));
        for (int i = 0; i < Content.LESSONS.size(); i++) {
            final int idx = i;
            Content.Lesson l = Content.LESSONS.get(i);
            boolean done = (mask & (1L << i)) != 0;
            int iconRes;
            switch (l.icon) {
                case "search": iconRes = R.drawable.ic_search; break;
                case "pie": iconRes = R.drawable.ic_pie; break;
                case "piggy": iconRes = R.drawable.ic_piggy; break;
                case "shield": iconRes = R.drawable.ic_shield; break;
                case "handshake": iconRes = R.drawable.ic_handshake; break;
                case "clock": iconRes = R.drawable.ic_clock; break;
                case "trend_up": iconRes = R.drawable.ic_trend_up; break;
                case "coins": iconRes = R.drawable.ic_coins; break;
                case "wallet": iconRes = R.drawable.ic_wallet; break;
                case "chart_line": iconRes = R.drawable.ic_chart_line; break;
                case "bulb": iconRes = R.drawable.ic_bulb; break;
                default: iconRes = R.drawable.ic_calendar; break;
            }
            LinearLayout row = UiKit.row(a, iconRes, R.color.brand_600, R.color.brand_50,
                    ar() ? l.titleAr : l.titleEn,
                    (done ? (ar() ? "✔ مكتمل • " : "✔ done • ") : "") + (ar() ? l.sourceAr : l.sourceEn),
                    null, 0);
            TextView sub = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
            sub.setMaxLines(2);
            if (done) {
                TextView title = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(0);
                title.setTextColor(Ui.color(a, R.color.text_tertiary));
            }
            row.setOnClickListener(v -> InfoSheets.lesson(a, idx));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 8);
            row.setLayoutParams(rlp);
            card.addView(row);
        }
        return card;
    }

    private View rulesCard() {
        LinearLayout card = UiKit.card(a, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        card.addView(Ui.txt(a, ar() ? "القواعد العالمية الذهبية" : "Golden global rules", 15, R.color.text_primary, Typeface.BOLD));
        card.addView(Ui.txt(a, ar()
                ? "خلاصة أشهر منهجيات إدارة المال في العالم — اضغط أي قاعدة للتفاصيل"
                : "The world's most famous money frameworks — tap any rule", 12, R.color.text_tertiary));
        UiKit.HorizontalScroll hs = new UiKit.HorizontalScroll(a);
        LinearLayout row = Ui.h(a);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.dp(a, 10);
        hs.setLayoutParams(hlp);
        for (int i = 0; i < Content.RULES.size(); i++) {
            final int idx = i;
            Content.Rule r = Content.RULES.get(idx);
            LinearLayout c = UiKit.cardStroke(a, 13);
            c.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 190), -2));
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) c.getLayoutParams();
            clp.setMarginEnd(Ui.dp(a, 8));
            c.addView(Ui.txt(a, ar() ? r.nameAr : r.nameEn, 14, R.color.brand_600, Typeface.BOLD));
            TextView b = Ui.txt(a, ar() ? r.bodyAr : r.bodyEn, 12.5f, R.color.text_secondary);
            b.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(a, 5);
            b.setLayoutParams(blp);
            c.addView(b);
            TextView tag = Ui.txt(a, ar() ? r.tagAr : "", 11, R.color.text_tertiary, Typeface.BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.topMargin = Ui.dp(a, 8);
            tag.setLayoutParams(tlp);
            c.addView(tag);
            c.setOnClickListener(v -> InfoSheets.rule(a, idx));
            row.addView(c);
        }
        hs.addView(row);
        card.addView(hs);
        return card;
    }

    @Override
    public void onShow() { refresh(); }
}
