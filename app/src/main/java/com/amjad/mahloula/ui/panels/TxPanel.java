package com.amjad.mahloula.ui.panels;

import android.app.Activity;
import android.content.Intent;
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
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.Sheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.Calendar;
import java.util.List;

/** المسحوبات — أبسط وأوضح: مبدل، شهر، قائمة، وزران في الأعلى (تصفية + مشاركة). */
public class TxPanel implements Panel {

    private final Activity a;
    private LinearLayout list;
    private TextView monthLabel, totalLabel;
    private Calendar month = Calendar.getInstance();
    private int kindFilter = 0;      // 0 مصروف، 1 دخل، 2 الكل
    private Integer catFilter = null;
    private String query = null;
    private Runnable segSync;

    public TxPanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        FrameLayout root = new FrameLayout(a);
        LinearLayout outer = Ui.v(a);
        outer.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 96));

        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "المسحوبات" : "Spending", 22, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        head.addView(iconBtn(R.drawable.ic_share, v -> shareMonth()));
        head.addView(iconBtn(R.drawable.ic_filter, v -> filterSheet()));
        outer.addView(head);

        // مبدل النوع
        LinearLayout seg = Ui.h(a);
        LinearLayout.LayoutParams selp = new LinearLayout.LayoutParams(-1, -2);
        selp.topMargin = Ui.dp(a, 12);
        seg.setLayoutParams(selp);
        seg.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 14, a));
        seg.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        String[] labels = ar() ? new String[]{"مسحوبات", "دخول", "الكل"} : new String[]{"Expenses", "Income", "All"};
        final TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView s = Ui.txt(a, labels[i], 14, R.color.text_secondary, Typeface.BOLD);
            s.setGravity(Gravity.CENTER);
            s.setMinHeight(Ui.dp(a, 42));
            segs[i] = s;
            seg.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
            s.setOnClickListener(v -> { kindFilter = idx; refresh(); });
        }
        segSync = () -> {
            for (int i = 0; i < 3; i++) {
                segs[i].setBackground(i == kindFilter
                        ? Ui.round(Ui.color(a, R.color.surface), 11, a)
                        : Ui.round(Color.TRANSPARENT, 11, a));
                segs[i].setTextColor(Ui.color(a, i == kindFilter ? R.color.text_primary : R.color.text_secondary));
            }
        };
        segSync.run();
        outer.addView(seg);

        // مبدل الشهر
        LinearLayout mrow = Ui.h(a);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = Ui.dp(a, 14);
        mrow.setLayoutParams(mlp);
        mrow.addView(iconBtn(R.drawable.ic_back, v -> { month.add(Calendar.MONTH, -1); refresh(); }, true));
        monthLabel = Ui.txt(a, "", 15.5f, R.color.text_primary, Typeface.BOLD);
        monthLabel.setGravity(Gravity.CENTER);
        mrow.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        mrow.addView(iconBtn(R.drawable.ic_back, v -> {
            if (month.before(Calendar.getInstance())) { month.add(Calendar.MONTH, 1); refresh(); }
        }, false));
        outer.addView(mrow);

        totalLabel = Ui.txt(a, "", 13.5f, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(a, 10);
        totalLabel.setLayoutParams(tlp);
        outer.addView(totalLabel);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.topMargin = Ui.dp(a, 6);
        sv.setLayoutParams(svlp);
        list = Ui.v(a);
        sv.addView(list);
        outer.addView(sv);
        root.addView(outer, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout fab = new FrameLayout(a);
        fab.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.text_primary), 20, a), Color.argb(40, 255, 255, 255)));
        fab.setElevation(Ui.dp(a, 7));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 62), Ui.dp(a, 62));
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = Ui.dp(a, 18);
        fab.setLayoutParams(flp);
        ImageView plus = Ui.iconSized(a, R.drawable.ic_plus, Color.WHITE, 28);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28));
        plp.gravity = Gravity.CENTER;
        plus.setLayoutParams(plp);
        fab.addView(plus);
        fab.setOnClickListener(v -> Sheets.tx(a, null, kindFilter == 1 ? 1 : 0));
        root.addView(fab);
        return root;
    }

    private ImageView iconBtn(int res, View.OnClickListener l) { return iconBtn(res, l, false); }

    private ImageView iconBtn(int res, View.OnClickListener l, boolean flip) {
        ImageView iv = Ui.iconSized(a, res, Ui.color(a, R.color.text_secondary), 22);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42));
        lp.setMarginStart(Ui.dp(a, 6));
        iv.setLayoutParams(lp);
        iv.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        iv.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.surface), 13, a), Ui.color(a, R.color.surface_3)));
        if (flip) iv.setRotation(180);
        iv.setOnClickListener(l);
        return iv;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        if (segSync != null) segSync.run();
        monthLabel.setText(Fmt.monthName(a, month));
        list.removeAllViews();
        Repo r = Repo.get(a);
        Integer kind = kindFilter == 2 ? null : kindFilter;
        List<Models.Tx> txs = r.txRange(Fmt.startOfMonth(month).getTimeInMillis(),
                Fmt.endOfMonth(month).getTimeInMillis(), kind, catFilter, query);
        double total = 0;
        for (Models.Tx t : txs) total += t.isExpense() ? -t.amount : t.amount;
        totalLabel.setText((ar() ? "صافي الفترة: " : "Period net: ") + Fmt.money(a, total)
                + (query != null || catFilter != null ? (ar() ? "  (مصفّى)" : "  (filtered)") : ""));

        if (txs.isEmpty()) {
            list.addView(UiKit.empty(a, R.drawable.ic_receipt,
                    ar() ? "لا عمليات في هذا الشهر" : "No entries this month",
                    ar() ? "اضغط + لتسجيل أول مصروف أو دخل" : "Tap + to log your first entry"));
            return;
        }
        long lastDay = 0;
        for (Models.Tx t : txs) {
            long day = Fmt.dayKey(t.ts);
            if (day != lastDay) {
                lastDay = day;
                TextView dh = Ui.txt(a, Fmt.relative(a, day) + "  •  " + Fmt.dayMonth(a, day),
                        12.5f, R.color.text_tertiary, Typeface.BOLD);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
                dlp.topMargin = Ui.dp(a, 14);
                dlp.bottomMargin = Ui.dp(a, 4);
                dh.setLayoutParams(dlp);
                list.addView(dh);
            }
            list.addView(txRow(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 7);
            list.getChildAt(list.getChildCount() - 1).setLayoutParams(lp);
        }
    }

    private View txRow(final Models.Tx t) {
        Models.Cat c = Models.cat(t.cat);
        String sub = c.ar + (t.method != null && !t.method.isEmpty() ? " • " + t.method : "")
                + " • " + Fmt.time(a, t.ts);
        LinearLayout row = UiKit.row(a, c.icon, Color.WHITE, c.color,
                t.note != null && !t.note.isEmpty() ? t.note : c.ar,
                sub,
                Fmt.signedMoney(a, t.amount, !t.isExpense()),
                t.isExpense() ? R.color.expense : R.color.income);
        row.setOnClickListener(v -> Sheets.tx(a, t, t.kind));
        row.setOnLongClickListener(v -> {
            Sheets.confirm(a, ar() ? "حذف العملية؟" : "Delete entry?",
                    (ar() ? "سيُحذف: " : "Will delete: ") + Fmt.money(a, t.amount),
                    () -> Repo.get(a).deleteTx(t.id));
            return true;
        });
        return row;
    }

    /** مشاركة ملخص الشهر نصًا — إضافة احترافية سريعة. */
    private void shareMonth() {
        Repo r = Repo.get(a);
        Models.MonthTotals mt = r.monthTotals(month);
        StringBuilder sb = new StringBuilder();
        sb.append(ar() ? "📊 ملخص " : "📊 Summary ").append(Fmt.monthName(a, month)).append(" — مَحلولة\n");
        sb.append(ar() ? "دخل: " : "Income: ").append(Fmt.money(a, mt.income)).append('\n');
        sb.append(ar() ? "مصروف: " : "Spent: ").append(Fmt.money(a, mt.expense)).append('\n');
        sb.append(ar() ? "الصافي: " : "Net: ").append(Fmt.money(a, mt.net())).append('\n');
        List<Models.CatTotal> cats = r.catTotals(Fmt.startOfMonth(month).getTimeInMillis(),
                Fmt.endOfMonth(month).getTimeInMillis(), 0);
        if (!cats.isEmpty()) {
            sb.append(ar() ? "\nأكثر الفئات:\n" : "\nTop categories:\n");
            int n = Math.min(3, cats.size());
            for (int i = 0; i < n; i++) {
                sb.append("• ").append(Models.cat(cats.get(i).cat).ar).append(": ")
                        .append(Fmt.money(a, cats.get(i).total)).append('\n');
            }
        }
        sb.append(ar() ? "\nتم تأليف وتطوير هذا التطبيق بواسطة أمجد حبيب" : "\nBy Amjad Habib");
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        a.startActivity(Intent.createChooser(i, ar() ? "مشاركة الملخص" : "Share summary"));
    }

    private void filterSheet() {
        boolean ar = ar();
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "بحث وتصفية" : "Search & filter");
        sh.content.addView(UiKit.label(a, ar ? "ابحث في الملاحظات" : "Search notes"));
        final TextView q = UiKit.field(a, ar ? "مثال: بنزين…" : "e.g. fuel…", false);
        q.setText(query == null ? "" : query);
        sh.content.addView(q);
        sh.content.addView(UiKit.label(a, ar ? "التصنيف" : "Category"));
        String[] names = new String[Models.CATS.size() + 1];
        names[0] = ar ? "كل التصنيفات" : "All categories";
        for (int i = 0; i < Models.CATS.size(); i++) names[i + 1] = Models.CATS.get(i).ar;
        int sel = catFilter == null ? 0 : catFilter;
        final Integer[] pick = {catFilter};
        sh.content.addView(UiKit.chipRow(a, names, sel, i -> pick[0] = i == 0 ? null : Models.CATS.get(i - 1).id));
        TextView apply = UiKit.sheetButton(sh, ar ? "تطبيق" : "Apply", false);
        apply.setOnClickListener(v -> {
            query = q.getText().toString().trim();
            if (query.isEmpty()) query = null;
            catFilter = pick[0];
            sh.dismiss();
            refresh();
        });
        TextView reset = UiKit.sheetButton(sh, ar ? "إعادة التعيين" : "Reset", false);
        reset.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 15, a));
        reset.setTextColor(Ui.color(a, R.color.text_primary));
        reset.setOnClickListener(v -> { catFilter = null; query = null; sh.dismiss(); refresh(); });
        sh.show();
    }

    @Override
    public void onShow() { refresh(); }
}
