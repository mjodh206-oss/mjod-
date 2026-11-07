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
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.ui.sheets.Sheets;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** خانة المسحوبات: سجل كامل بالبحث والتصفية وتبديل الأشهر. */
public class TxPanel implements Panel {

    private final Activity a;
    private LinearLayout col;
    private LinearLayout list;
    private TextView monthLabel, totalLabel;
    private Calendar month = Calendar.getInstance();
    private int kindFilter = 0;      // 0 مصروف، 1 دخل، 2 الكل
    private Integer catFilter = null;
    private String query = null;
    private TextView searchField;
    private Runnable segSync;

    public TxPanel(Activity a) { this.a = a; }

    private boolean ar() { return !Prefs.get(a).language().equals("en"); }

    @Override
    public View build() {
        FrameLayout root = new FrameLayout(a);
        LinearLayout outer = Ui.v(a);
        outer.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 96));

        // ترويسة
        LinearLayout head = Ui.h(a);
        head.addView(Ui.txt(a, ar() ? "المسحوبات والدخول" : "Spending & income", 21, R.color.text_primary, Typeface.BOLD));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 0, 1f));
        ImageView filter = Ui.iconSized(a, R.drawable.ic_filter, Ui.color(a, R.color.text_secondary), 22);
        filter.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        filter.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.surface), 12, a), Ui.color(a, R.color.surface_3)));
        filter.setOnClickListener(v -> filterSheet());
        head.addView(filter);
        outer.addView(head);

        // بحث
        searchField = UiKit.field(a, ar() ? "ابحث في الملاحظات…" : "Search notes…", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(a, 10);
        searchField.setLayoutParams(slp);
        searchField.setOnEditorActionListener((v, act, ev) -> { query = searchField.getText().toString(); refresh(); return true; });
        outer.addView(searchField);

        // مبدل النوع
        LinearLayout seg = Ui.h(a);
        LinearLayout.LayoutParams selp = new LinearLayout.LayoutParams(-1, -2);
        selp.topMargin = Ui.dp(a, 10);
        seg.setLayoutParams(selp);
        String[] labels = ar() ? new String[]{"مسحوبات", "دخول", "الكل"} : new String[]{"Expenses", "Income", "All"};
        final TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView s = Ui.txt(a, labels[i], 13, R.color.text_secondary, Typeface.BOLD);
            s.setGravity(Gravity.CENTER);
            s.setMinHeight(Ui.dp(a, 40));
            segs[i] = s;
            seg.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
            s.setOnClickListener(v -> { kindFilter = idx; refresh(); });
        }
        Runnable syncSeg = () -> {
            for (int i = 0; i < 3; i++) {
                segs[i].setBackground(i == kindFilter
                        ? Ui.round(Ui.color(a, R.color.brand_600), 12, a)
                        : Ui.round(Ui.color(a, R.color.surface_2), 12, a));
                segs[i].setTextColor(Ui.color(a, i == kindFilter ? R.color.on_brand : R.color.text_secondary));
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) segs[i].getLayoutParams();
                lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
            }
        };
        segSync = syncSeg;
        syncSeg.run();
        outer.addView(seg);

        // مبدل الشهر
        LinearLayout mrow = Ui.h(a);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = Ui.dp(a, 12);
        mrow.setLayoutParams(mlp);
        ImageView prev = Ui.iconSized(a, R.drawable.ic_back, Ui.color(a, R.color.text_secondary), 20);
        prev.setRotation(180);
        prev.setOnClickListener(v -> { month.add(Calendar.MONTH, -1); refresh(); });
        mrow.addView(prev);
        monthLabel = Ui.txt(a, "", 14.5f, R.color.text_primary, Typeface.BOLD);
        monthLabel.setGravity(Gravity.CENTER);
        mrow.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView next = Ui.iconSized(a, R.drawable.ic_back, Ui.color(a, R.color.text_secondary), 20);
        next.setOnClickListener(v -> {
            if (month.before(Calendar.getInstance())) { month.add(Calendar.MONTH, 1); refresh(); }
        });
        mrow.addView(next);
        outer.addView(mrow);

        totalLabel = Ui.txt(a, "", 13, R.color.text_secondary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(a, 8);
        totalLabel.setLayoutParams(tlp);
        outer.addView(totalLabel);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.topMargin = Ui.dp(a, 8);
        sv.setLayoutParams(svlp);
        list = Ui.v(a);
        sv.addView(list);
        outer.addView(sv);

        root.addView(outer, new FrameLayout.LayoutParams(-1, -1));

        // زر عائم
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
        fab.setOnClickListener(v -> Sheets.tx(a, null, kindFilter == 1 ? 1 : 0));
        root.addView(fab);
        return root;
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
        totalLabel.setText((ar() ? "صافي الفترة: " : "Period net: ") + Fmt.money(a, total));

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
                        12, R.color.text_tertiary, Typeface.BOLD);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
                dlp.topMargin = Ui.dp(a, 12);
                dlp.bottomMargin = Ui.dp(a, 4);
                dh.setLayoutParams(dlp);
                list.addView(dh);
            }
            list.addView(txRow(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 6);
            list.getChildAt(list.getChildCount() - 1).setLayoutParams(lp);
        }
    }

    private View txRow(final Models.Tx t) {
        Models.Cat c = Models.cat(t.cat);
        String sub = c.ar + (t.method != null && !t.method.isEmpty() ? " • " + t.method : "")
                + " • " + Fmt.time(a, t.ts);
        LinearLayout row = UiKit.row(a, c.icon, c.color,
                t.isExpense() ? R.color.expense_soft : R.color.income_soft,
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

    private void filterSheet() {
        boolean ar = ar();
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "تصفية السجل" : "Filter history");
        sh.content.addView(UiKit.label(a, ar ? "التصنيف" : "Category"));
        String[] names = new String[Models.CATS.size() + 1];
        names[0] = ar ? "كل التصنيفات" : "All categories";
        for (int i = 0; i < Models.CATS.size(); i++) names[i + 1] = Models.CATS.get(i).ar;
        int sel = catFilter == null ? 0 : catFilter;
        sh.content.addView(UiKit.chipRow(a, names, sel, i -> catFilter = i == 0 ? null : Models.CATS.get(i - 1).id));
        TextView apply = UiKit.sheetButton(sh, ar ? "تطبيق التصفية" : "Apply filter", false);
        apply.setOnClickListener(v -> { sh.dismiss(); refresh(); });
        TextView reset = UiKit.sheetButton(sh, ar ? "إعادة التعيين" : "Reset", false);
        reset.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 15, a));
        reset.setTextColor(Ui.color(a, R.color.text_primary));
        reset.setOnClickListener(v -> { catFilter = null; query = null; if (searchField != null) searchField.setText(""); sh.dismiss(); refresh(); });
        sh.show();
    }

    @Override
    public void onShow() {
        refresh();
    }
}
