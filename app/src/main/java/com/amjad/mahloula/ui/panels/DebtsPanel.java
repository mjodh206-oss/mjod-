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
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.Panel;
import com.amjad.mahloula.ui.kit.Charts;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.Sheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.List;

/** خانة تسديد الديون: عليك ولك، بتتبّع الدفعات والمواعيد. */
public class DebtsPanel implements Panel {

    private final Activity a;
    private LinearLayout list;
    private TextView sumOwe, sumDue, sumNet;
    private int filter = 0;  // 0 الكل، 1 عليّ، 2 لي
    private Runnable segSync;

    public DebtsPanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        FrameLayout root = new FrameLayout(a);
        LinearLayout outer = Ui.v(a);
        outer.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 96));

        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "الديون والتسديد" : "Debts & payments", 21, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(Ui.txt(a, ar() ? "سجلّك الكامل" : "Full ledger", 12, R.color.text_tertiary));
        outer.addView(head);

        // بطاقة الملخص
        LinearLayout sum = UiKit.card(a, 16);
        sum.setBackground(Ui.gradient(a, new int[]{Ui.color(a, R.color.brand_800), Ui.color(a, R.color.brand_600)}, 20, 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(a, 10);
        sum.setLayoutParams(slp);
        LinearLayout row = Ui.h(a);
        sumOwe = sumCell(row, ar() ? "عليّ أنا" : "I owe", R.color.gold_400);
        sumDue = sumCell(row, ar() ? "لي عندي" : "Owed to me", R.color.brand_200);
        sum.addView(row);
        sumNet = Ui.txt(a, "", 13, R.color.on_brand, Typeface.BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(a, 8);
        sumNet.setLayoutParams(nlp);
        sum.addView(sumNet);
        outer.addView(sum);

        // مبدل التصفية
        LinearLayout seg = Ui.h(a);
        LinearLayout.LayoutParams selp = new LinearLayout.LayoutParams(-1, -2);
        selp.topMargin = Ui.dp(a, 12);
        seg.setLayoutParams(selp);
        String[] labels = ar() ? new String[]{"الكل", "عليّ", "لي"} : new String[]{"All", "I owe", "Owed to me"};
        final TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView s = Ui.txt(a, labels[i], 13, R.color.text_secondary, Typeface.BOLD);
            s.setGravity(Gravity.CENTER);
            s.setMinHeight(Ui.dp(a, 40));
            segs[i] = s;
            seg.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
            s.setOnClickListener(v -> { filter = idx; refresh(); });
        }
        Runnable sync = () -> {
            for (int i = 0; i < 3; i++) {
                segs[i].setBackground(i == filter ? Ui.round(Ui.color(a, R.color.brand_600), 12, a) : Ui.round(Ui.color(a, R.color.surface_2), 12, a));
                segs[i].setTextColor(Ui.color(a, i == filter ? R.color.on_brand : R.color.text_secondary));
            }
        };
        segSync = sync;
        sync.run();
        outer.addView(seg);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.topMargin = Ui.dp(a, 10);
        sv.setLayoutParams(svlp);
        list = Ui.v(a);
        sv.addView(list);
        outer.addView(sv);
        root.addView(outer, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout fab = new FrameLayout(a);
        fab.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.brand_600), 18, a), Color.argb(40, 255, 255, 255)));
        fab.setElevation(Ui.dp(a, 6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 58), Ui.dp(a, 58));
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = Ui.dp(a, 18);
        fab.setLayoutParams(flp);
        ImageView plus = Ui.iconSized(a, R.drawable.ic_plus, Color.WHITE, 26);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26));
        plp.gravity = Gravity.CENTER;
        plus.setLayoutParams(plp);
        fab.addView(plus);
        fab.setOnClickListener(v -> Sheets.debt(a, null));
        root.addView(fab);
        return root;
    }

    private TextView sumCell(LinearLayout row, String label, int colorRes) {
        LinearLayout cell = Ui.v(a);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        cell.addView(Ui.txt(a, label, 12, R.color.brand_200));
        TextView v = Ui.txt(a, "0", 19, colorRes, Typeface.BOLD);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = Ui.dp(a, 3);
        v.setLayoutParams(vlp);
        cell.addView(v);
        row.addView(cell);
        return v;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        if (segSync != null) segSync.run();
        list.removeAllViews();
        Repo r = Repo.get(a);
        double owe = r.debtsOwedByMe();
        double due = r.debtsOwedToMe();
        sumOwe.setText(Fmt.amount(a, owe));
        sumDue.setText(Fmt.amount(a, due));
        sumNet.setText((ar() ? "صافي مركزك: " : "Net position: ") + Fmt.money(a, due - owe));

        List<Models.Debt> debts = r.debts();
        boolean any = false;
        for (Models.Debt d : debts) {
            if (filter == 1 && d.isMine()) continue;
            if (filter == 2 && !d.isMine()) continue;
            any = true;
            list.addView(debtCard(d, r));
            View last = list.getChildAt(list.getChildCount() - 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 10);
            last.setLayoutParams(lp);
        }
        if (!any) {
            list.addView(UiKit.empty(a, R.drawable.ic_handshake,
                    ar() ? "لا ديون مسجلة" : "No debts logged",
                    ar() ? "سجّل ديونك عليك ولك لتتبّع التسديد والمواعيد" : "Log debts you owe and debts owed to you"));
        }
    }

    private View debtCard(final Models.Debt d, final Repo r) {
        double paid = r.debtPaid(d.id);
        double left = Math.max(0, d.total - paid);
        boolean closed = left <= 0.001;
        LinearLayout card = UiKit.card(a, 15);
        card.setBackground(Ui.roundStroke(a, Ui.color(a, R.color.surface),
                closed ? Ui.color(a, R.color.income) : Ui.color(a, R.color.outline), closed ? 1.6f : 1, 18));

        LinearLayout head = Ui.h(a);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.round(Ui.color(a, d.isMine() ? R.color.income_soft : R.color.expense_soft), 12, a));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
        ImageView iv = Ui.iconSized(a, d.isMine() ? R.drawable.ic_trend_up : R.drawable.ic_trend_down,
                Ui.color(a, d.isMine() ? R.color.income : R.color.expense), 20);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
        ilp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ilp);
        badge.addView(iv);
        head.addView(badge);
        LinearLayout mid = Ui.v(a);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.setMarginStart(Ui.dp(a, 10));
        mid.setLayoutParams(mlp);
        mid.addView(Ui.txt(a, d.person, 15, R.color.text_primary, Typeface.BOLD));
        if (!Ui.empty(d.title)) {
            TextView t = Ui.txt(a, d.title, 12.5f, R.color.text_secondary);
            mid.addView(t);
        }
        head.addView(mid);
        TextView amt = Ui.txt(a, Fmt.amount(a, d.total), 16, R.color.text_primary, Typeface.BOLD);
        head.addView(amt);
        card.addView(head);

        Charts.HBar bar = new Charts.HBar(a);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(a, 10);
        bar.setLayoutParams(blp);
        bar.set((float) (d.total > 0 ? paid / d.total : 0), closed ? R.color.income : (d.isMine() ? R.color.expense : R.color.income));
        card.addView(bar);

        LinearLayout foot = Ui.h(a);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(a, 8);
        foot.setLayoutParams(flp);
        String status;
        int statusColor;
        if (closed) {
            status = ar() ? "✔ مُسدّد بالكامل" : "✔ Fully paid";
            statusColor = R.color.income;
        } else if (d.due > 0) {
            long days = (d.due - System.currentTimeMillis()) / 86400000L;
            if (days < 0) {
                status = (ar() ? "متأخر " + Fmt.digits(a, String.valueOf(-days)) + " يوم" : "Overdue " + (-days) + "d");
                statusColor = R.color.expense;
            } else {
                status = (ar() ? "يستحق خلال " + Fmt.digits(a, String.valueOf(days)) + " يوم" : "Due in " + days + "d");
                statusColor = days <= 7 ? R.color.warn : R.color.text_secondary;
            }
        } else {
            status = ar() ? "بدون موعد" : "No due date";
            statusColor = R.color.text_tertiary;
        }
        foot.addView(Ui.txt(a, status, 12.5f, statusColor, Typeface.BOLD));
        foot.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        foot.addView(Ui.txt(a, (ar() ? "سُدّد " : "paid ") + Fmt.amount(a, paid)
                + (ar() ? " • تبقّى " : " • left ") + Fmt.amount(a, left), 12, R.color.text_secondary));
        card.addView(foot);

        LinearLayout actions = Ui.h(a);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(a, 10);
        actions.setLayoutParams(alp);
        if (!closed) {
            TextView payBtn = UiKit.button(a, ar() ? "تسديد دفعة" : "Log payment", R.drawable.ic_coins, true);
            payBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.2f));
            payBtn.setOnClickListener(v -> Sheets.pay(a, d));
            actions.addView(payBtn);
        }
        TextView histBtn = UiKit.button(a, ar() ? "السجل" : "History", R.drawable.ic_clock, false);
        histBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 0.8f));
        histBtn.setOnClickListener(v -> historySheet(d, r));
        actions.addView(histBtn);
        TextView editBtn = UiKit.button(a, ar() ? "تعديل" : "Edit", R.drawable.ic_edit, false);
        editBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 0.8f));
        editBtn.setOnClickListener(v -> Sheets.debt(a, d));
        actions.addView(editBtn);
        card.addView(actions);
        return card;
    }

    private void historySheet(Models.Debt d, Repo r) {
        boolean ar = ar();
        UiKit.Sheet sh = UiKit.sheet(a, (ar ? "سجل تسديدات: " : "Payments: ") + d.person);
        List<Models.DebtPay> pays = r.debtPays(d.id);
        if (pays.isEmpty()) {
            sh.content.addView(Ui.txt(a, ar ? "لا دفعات مسجلة بعد." : "No payments logged yet.", 13.5f, R.color.text_secondary));
        }
        for (final Models.DebtPay p : pays) {
            LinearLayout row = UiKit.row(a, R.drawable.ic_coins, R.color.income, R.color.income_soft,
                    Fmt.money(a, p.amount),
                    Fmt.fullDate(a, p.ts) + (Ui.empty(p.note) ? "" : " • " + p.note),
                    null, 0);
            row.setOnLongClickListener(v -> {
                Sheets.confirm(a, ar ? "حذف الدفعة؟" : "Delete payment?", "", () -> r.deleteDebtPay(p.id));
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 6);
            row.setLayoutParams(lp);
            sh.content.addView(row);
        }
        TextView add = UiKit.sheetButton(sh, ar ? "+ دفعة جديدة" : "+ New payment", false);
        add.setOnClickListener(v -> { sh.dismiss(); Sheets.pay(a, d); });
        sh.show();
    }

    @Override
    public void onShow() { refresh(); }
}
