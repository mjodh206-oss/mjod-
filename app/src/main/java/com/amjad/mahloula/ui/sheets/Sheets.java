package com.amjad.mahloula.ui.sheets;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.data.Models;
import com.amjad.mahloula.data.Repo;
import com.amjad.mahloula.ui.MainActivity;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.util.Calendar;
import java.util.List;

/** شيتات الإدخال: مصروف/دخل، دين، تسديد، ملاحظة، محفظة، هدف. */
public final class Sheets {

    private Sheets() {}

    public static boolean ar(Activity a) {
        return !Prefs.get(a).language().equals("en");
    }

    // ---------------- مصروف / دخل ----------------

    public static void tx(Activity a, Models.Tx edit, int defaultKind) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        final Models.Tx t = edit != null ? edit : new Models.Tx();
        if (edit == null) {
            t.kind = defaultKind;
            t.ts = System.currentTimeMillis();
            Models.Wallet dw = r.defaultWallet();
            t.wallet = dw == null ? 1 : dw.id;
            t.cat = 1;
        }
        final UiKit.Sheet sh = UiKit.sheet(a, edit == null
                ? (t.kind == 0 ? (ar ? "مسحوب جديد" : "New expense") : (ar ? "دخل جديد" : "New income"))
                : (ar ? "تعديل العملية" : "Edit entry"));

        // مبدّل نوع
        LinearLayout kindRow = Ui.h(a);
        final TextView expBtn = segBtn(a, ar ? "مصروف −" : "Expense −", true);
        final TextView incBtn = segBtn(a, ar ? "دخل +" : "Income +", false);
        kindRow.addView(expBtn);
        kindRow.addView(incBtn);
        sh.content.addView(kindRow);
        Runnable syncKind = () -> {
            expBtn.setBackground(Ui.round(t.kind == 0 ? Ui.color(a, R.color.expense) : Ui.color(a, R.color.surface_2), 12, a));
            expBtn.setTextColor(Ui.color(a, t.kind == 0 ? R.color.on_brand : R.color.text_secondary));
            incBtn.setBackground(Ui.round(t.kind == 1 ? Ui.color(a, R.color.income) : Ui.color(a, R.color.surface_2), 12, a));
            incBtn.setTextColor(Ui.color(a, t.kind == 1 ? R.color.on_brand : R.color.text_secondary));
        };
        expBtn.setOnClickListener(v -> { t.kind = 0; syncKind.run(); });
        incBtn.setOnClickListener(v -> { t.kind = 1; syncKind.run(); });
        syncKind.run();

        // المبلغ
        sh.content.addView(UiKit.label(a, ar ? "المبلغ (" + Prefs.get(a).currency() + ")" : "Amount (" + Prefs.get(a).currency() + ")"));
        final TextView amount = UiKit.field(a, ar ? "0.00" : "0.00", true);
        amount.setTextSize(22);
        amount.setTypeface(Ui.font(a, Typeface.BOLD));
        amount.setGravity(Gravity.CENTER);
        if (edit != null) amount.setText(Fmt.amount(a, edit.amount));
        sh.content.addView(amount);

        // التصنيف
        sh.content.addView(UiKit.label(a, ar ? "التصنيف" : "Category"));
        final int[] catSel = {t.cat};
        LinearLayout catWrap = Ui.v(a);
        sh.content.addView(catWrap);
        final Runnable[] renderCatsHolder = new Runnable[1];
        Runnable renderCats = () -> {
            catWrap.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < Models.CATS.size(); i++) {
                final Models.Cat c = Models.CATS.get(i);
                if (i % 4 == 0) {
                    row = Ui.h(a);
                    row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                    catWrap.addView(row);
                }
                final int idx = c.id;
                LinearLayout tile = Ui.v(a);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
                tlp.setMargins(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
                tile.setLayoutParams(tlp);
                FrameLayout badge = new FrameLayout(a);
                badge.setBackground(Ui.round(Ui.color(a, c.color), 12, a));
                badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
                ImageView iv = Ui.iconSized(a, c.icon, Color.WHITE, 20);
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
                ilp.gravity = Gravity.CENTER;
                iv.setLayoutParams(ilp);
                badge.addView(iv);
                tile.addView(badge);
                TextView nm = Ui.txt(a, c.ar, 10.5f, R.color.text_secondary, Typeface.BOLD);
                nm.setGravity(Gravity.CENTER);
                nm.setMaxLines(1);
                tile.addView(nm);
                boolean sel = catSel[0] == idx;
                tile.setBackground(sel
                        ? Ui.roundStroke(a, Ui.color(a, R.color.brand_50), Ui.color(a, R.color.brand_500), 1.6f, 14)
                        : Ui.round(Ui.color(a, R.color.surface_2), 14, a));
                tile.setOnClickListener(v -> { catSel[0] = idx; renderCatsHolder[0].run(); Ui.tick(a); });
                row.addView(tile);
            }
        };
        renderCatsHolder[0] = renderCats;
        renderCats.run();

        // المحفظة
        sh.content.addView(UiKit.label(a, ar ? "المحفظة / الحساب" : "Wallet / account"));
        final List<Models.Wallet> wallets = r.wallets();
        String[] wNames = new String[wallets.size()];
        int wSel = 0;
        for (int i = 0; i < wallets.size(); i++) {
            wNames[i] = wallets.get(i).name;
            if (wallets.get(i).id == t.wallet) wSel = i;
        }
        final int[] walletSel = {wSel};
        sh.content.addView(UiKit.chipRow(a, wNames, wSel, i -> walletSel[0] = i));

        // طريقة الدفع + التاريخ + ملاحظة
        if (true) {
            sh.content.addView(UiKit.label(a, ar ? "طريقة الدفع" : "Payment method"));
            String[] methods = ar ? Models.PAY_METHODS_AR : Models.PAY_METHODS_EN;
            int mSel = 0;
            for (int i = 0; i < methods.length; i++) if (methods[i].equals(t.method)) mSel = i;
            final int[] mSelF = {mSel};
            sh.content.addView(UiKit.chipRow(a, methods, mSel, i -> mSelF[0] = i));
            sh.content.setTag(R.id.tag_method, mSelF);
        }

        sh.content.addView(UiKit.label(a, ar ? "التاريخ" : "Date"));
        final TextView dateBtn = UiKit.field(a, "", false);
        dateBtn.setFocusable(false);
        final long[] ts = {t.ts};
        Runnable syncDate = () -> dateBtn.setText(Fmt.fullDate(a, ts[0]) + "  •  " + Fmt.time(a, ts[0]));
        syncDate.run();
        dateBtn.setOnClickListener(v -> pickDate(a, ts[0], picked -> { ts[0] = picked; syncDate.run(); }));
        sh.content.addView(dateBtn);

        sh.content.addView(UiKit.label(a, ar ? "ملاحظة (اختياري)" : "Note (optional)"));
        final TextView note = UiKit.field(a, ar ? "مثال: غداء عمل، بنزين…" : "e.g. lunch, fuel…", false);
        if (edit != null) note.setText(edit.note);
        sh.content.addView(note);

        TextView save = UiKit.sheetButton(sh, ar ? "حفظ العملية ✔" : "Save entry ✔", false);
        save.setOnClickListener(v -> {
            double amt;
            try {
                amt = Double.parseDouble(amount.getText().toString().trim());
            } catch (Exception e) {
                amt = 0;
            }
            if (amt <= 0) {
                Ui.toast(a, ar ? "أدخل مبلغًا صحيحًا أولًا" : "Enter a valid amount first");
                return;
            }
            t.amount = amt;
            t.cat = catSel[0];
            t.wallet = wallets.get(walletSel[0]).id;
            Object tag = sh.content.getTag(R.id.tag_method);
            if (tag instanceof int[]) {
                String[] methods = ar ? Models.PAY_METHODS_AR : Models.PAY_METHODS_EN;
                t.method = methods[((int[]) tag)[0]];
            }
            t.note = note.getText().toString().trim();
            t.ts = ts[0];
            final Runnable doSave = () -> {
                if (edit == null) r.addTx(t);
                else r.updateTx(t);
                sh.dismiss();
                java.util.List<com.amjad.mahloula.brain.Advisor.Insight> g =
                        com.amjad.mahloula.brain.Advisor.guardTx(a, t);
                if (!g.isEmpty()) {
                    com.amjad.mahloula.brain.Advisor.Insight first = g.get(0);
                    com.amjad.mahloula.notify.Notifier.show(a, "⚠️ " + first.title, first.body);
                    UiKit.snack(a, "⚠️ " + first.title, ar ? "عرض التنبيه" : "View",
                            () -> { if (a instanceof MainActivity) ((MainActivity) a).jump(0); });
                } else {
                    UiKit.snack(a, ar ? "تم الحفظ في سجلّك ✔" : "Saved to your log ✔", null, null);
                }
            };
            if (edit == null && t.isExpense()) {
                java.util.Calendar c0 = java.util.Calendar.getInstance();
                c0.setTimeInMillis(ts[0]);
                c0.set(java.util.Calendar.HOUR_OF_DAY, 0);
                c0.set(java.util.Calendar.MINUTE, 0);
                c0.set(java.util.Calendar.SECOND, 0);
                boolean dup = false;
                for (Models.Tx x : r.txRange(c0.getTimeInMillis(), ts[0] + 60000L, 0, t.cat, null)) {
                    if (Math.abs(x.amount - t.amount) < 0.001 && Math.abs(x.ts - ts[0]) < 600000L) { dup = true; break; }
                }
                if (dup) {
                    confirm(a, ar ? "عملية مكررة؟" : "Duplicate entry?",
                            ar ? "يوجد مصروف بنفس المبلغ والتصنيف خلال آخر 10 دقائق. حفظ على أي حال؟"
                               : "Same amount & category within 10 minutes. Save anyway?",
                            doSave);
                    return;
                }
            }
            doSave.run();
        });

        if (edit != null) {
            TextView del = UiKit.sheetButton(sh, ar ? "حذف العملية" : "Delete entry", true);
            del.setOnClickListener(v -> confirm(a, ar ? "حذف العملية؟" : "Delete entry?",
                    ar ? "ستُحذف نهائيًا من سجلّك." : "It will be removed from your log permanently.",
                    () -> { r.deleteTx(edit.id); sh.dismiss(); }));
        }
        sh.show();
    }

    private static TextView segBtn(Activity a, String label, boolean first) {
        TextView b = Ui.txt(a, label, 14, R.color.text_secondary, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(a, 44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(Ui.dp(a, first ? 0 : 4), 0, Ui.dp(a, first ? 4 : 0), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------- دين ----------------

    public static void debt(Activity a, Models.Debt edit) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        final Models.Debt d = edit != null ? edit : new Models.Debt();
        if (edit == null) d.created = System.currentTimeMillis();
        UiKit.Sheet sh = UiKit.sheet(a, edit == null ? (ar ? "دين جديد" : "New debt") : (ar ? "تعديل الدين" : "Edit debt"));

        LinearLayout kindRow = Ui.h(a);
        final TextView oweBtn = segBtn(a, ar ? "عليّ أنا" : "I owe", true);
        final TextView dueBtn = segBtn(a, ar ? "لي عندي" : "Owed to me", false);
        kindRow.addView(oweBtn);
        kindRow.addView(dueBtn);
        sh.content.addView(kindRow);
        Runnable sync = () -> {
            oweBtn.setBackground(Ui.round(d.kind == 0 ? Ui.color(a, R.color.expense) : Ui.color(a, R.color.surface_2), 12, a));
            oweBtn.setTextColor(Ui.color(a, d.kind == 0 ? R.color.on_brand : R.color.text_secondary));
            dueBtn.setBackground(Ui.round(d.kind == 1 ? Ui.color(a, R.color.income) : Ui.color(a, R.color.surface_2), 12, a));
            dueBtn.setTextColor(Ui.color(a, d.kind == 1 ? R.color.on_brand : R.color.text_secondary));
        };
        oweBtn.setOnClickListener(v -> { d.kind = 0; sync.run(); });
        dueBtn.setOnClickListener(v -> { d.kind = 1; sync.run(); });
        sync.run();

        sh.content.addView(UiKit.label(a, ar ? "الشخص / الجهة" : "Person / entity"));
        final TextView person = UiKit.field(a, ar ? "مثال: أبو خالد، بنك…" : "e.g. bank, friend…", false);
        person.setText(d.person);
        sh.content.addView(person);

        sh.content.addView(UiKit.label(a, ar ? "عنوان الدين" : "Debt title"));
        final TextView title = UiKit.field(a, ar ? "مثال: قسط سيارة، سلفة…" : "e.g. car installment…", false);
        title.setText(d.title);
        sh.content.addView(title);

        sh.content.addView(UiKit.label(a, ar ? "المبلغ الكلي" : "Total amount"));
        final TextView total = UiKit.field(a, "0.00", true);
        if (edit != null) total.setText(Fmt.amount(a, d.total));
        sh.content.addView(total);

        sh.content.addView(UiKit.label(a, ar ? "موعد السداد (اختياري)" : "Due date (optional)"));
        final TextView dueBtn2 = UiKit.field(a, ar ? "بدون موعد" : "No due date", false);
        dueBtn2.setFocusable(false);
        final long[] due = {d.due};
        Runnable syncDue = () -> dueBtn2.setText(due[0] == 0 ? (ar ? "بدون موعد" : "No due date") : Fmt.fullDate(a, due[0]));
        syncDue.run();
        dueBtn2.setOnClickListener(v -> pickDate(a, due[0] == 0 ? System.currentTimeMillis() : due[0], p -> { due[0] = p; syncDue.run(); }));
        sh.content.addView(dueBtn2);

        sh.content.addView(UiKit.label(a, ar ? "ملاحظة" : "Note"));
        final TextView noteF = UiKit.field(a, "", false);
        noteF.setText(d.note);
        sh.content.addView(noteF);

        TextView save = UiKit.sheetButton(sh, ar ? "حفظ الدين ✔" : "Save debt ✔", false);
        save.setOnClickListener(v -> {
            double tot;
            try { tot = Double.parseDouble(total.getText().toString().trim()); } catch (Exception e) { tot = 0; }
            if (tot <= 0 || Ui.empty(person.getText().toString())) {
                Ui.toast(a, ar ? "أكمل الشخص والمبلغ" : "Fill person and amount");
                return;
            }
            d.person = person.getText().toString().trim();
            d.title = title.getText().toString().trim();
            d.total = tot;
            d.due = due[0];
            d.note = noteF.getText().toString().trim();
            if (edit == null) r.addDebt(d); else r.updateDebt(d);
            sh.dismiss();
        });
        if (edit != null) {
            TextView del = UiKit.sheetButton(sh, ar ? "حذف الدين وكل تسديداته" : "Delete debt & payments", true);
            del.setOnClickListener(v -> confirm(a, ar ? "حذف الدين؟" : "Delete debt?",
                    ar ? "سيُحذف مع كل سجل تسديداته." : "It and all its payments will be removed.",
                    () -> { r.deleteDebt(edit.id); sh.dismiss(); }));
        }
        sh.show();
    }

    // ---------------- تسديد ----------------

    public static void pay(Activity a, Models.Debt d) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        double left = d.total - r.debtPaid(d.id);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "تسديد دفعة — " + d.person : "Log payment — " + d.person);
        TextView info = Ui.txt(a, ar ? "المتبقي قبل هذه الدفعة: " + Fmt.money(a, left) : "Remaining before: " + Fmt.money(a, left),
                13, R.color.text_secondary);
        sh.content.addView(info);
        sh.content.addView(UiKit.label(a, ar ? "مبلغ الدفعة" : "Payment amount"));
        final TextView amt = UiKit.field(a, Fmt.amount(a, left), true);
        sh.content.addView(amt);
        sh.content.addView(UiKit.label(a, ar ? "ملاحظة (اختياري)" : "Note (optional)"));
        final TextView note = UiKit.field(a, "", false);
        sh.content.addView(note);
        TextView save = UiKit.sheetButton(sh, ar ? "تسجيل التسديد ✔" : "Log payment ✔", false);
        save.setOnClickListener(v -> {
            double x;
            try { x = Double.parseDouble(amt.getText().toString().trim()); } catch (Exception e) { x = 0; }
            if (x <= 0) { Ui.toast(a, ar ? "مبلغ غير صالح" : "Invalid amount"); return; }
            r.addDebtPay(d.id, x, note.getText().toString().trim());
            sh.dismiss();
            double newLeft = d.total - r.debtPaid(d.id);
            UiKit.snack(a, newLeft <= 0
                    ? (ar ? "🎉 أغلقت هذا الدين بالكامل!" : "🎉 Debt fully closed!")
                    : (ar ? "سُجّلت الدفعة — المتبقي " + Fmt.money(a, newLeft) : "Payment logged — left " + Fmt.money(a, newLeft)),
                    null, null);
        });
        sh.show();
    }

    // ---------------- ملاحظة ----------------

    public static final int[] NOTE_COLORS = {R.color.gold_500, R.color.brand_500, R.color.info, R.color.expense, R.color.cat_home, R.color.cat_family};

    public static void note(Activity a, Models.Note edit) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        final Models.Note n = edit != null ? edit : new Models.Note();
        UiKit.Sheet sh = UiKit.sheet(a, edit == null ? (ar ? "ملاحظة جديدة" : "New note") : (ar ? "تعديل الملاحظة" : "Edit note"));
        sh.content.addView(UiKit.label(a, ar ? "العنوان" : "Title"));
        final TextView title = UiKit.field(a, ar ? "مثال: قائمة الشهر، أفكار…" : "e.g. monthly list…", false);
        title.setText(n.title);
        sh.content.addView(title);
        sh.content.addView(UiKit.label(a, ar ? "النص" : "Body"));
        final TextView body = UiKit.area(a, ar ? "اكتب هنا…" : "Write here…");
        body.setText(n.body);
        sh.content.addView(body);
        sh.content.addView(UiKit.label(a, ar ? "اللون" : "Color"));
        LinearLayout colors = Ui.h(a);
        final int[] sel = {n.color};
        final android.view.View[] swatches = new android.view.View[NOTE_COLORS.length];
        for (int i = 0; i < NOTE_COLORS.length; i++) {
            final int idx = i;
            android.view.View sw = new android.view.View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34));
            lp.setMarginEnd(Ui.dp(a, 10));
            sw.setLayoutParams(lp);
            swatches[i] = sw;
            sw.setOnClickListener(v -> {
                sel[0] = idx;
                for (int k = 0; k < swatches.length; k++) {
                    swatches[k].setBackground(Ui.roundStroke(a, Ui.color(a, NOTE_COLORS[k]),
                            k == idx ? Ui.color(a, R.color.text_primary) : Color.TRANSPARENT, k == idx ? 2.5f : 0, 12));
                }
            });
            colors.addView(sw);
        }
        swatches[sel[0]].performClick();
        sh.content.addView(colors);

        TextView save = UiKit.sheetButton(sh, ar ? "حفظ الملاحظة ✔" : "Save note ✔", false);
        save.setOnClickListener(v -> {
            n.title = title.getText().toString().trim();
            n.body = body.getText().toString().trim();
            n.color = sel[0];
            if (edit == null) r.addNote(n); else r.updateNote(n);
            sh.dismiss();
        });
        if (edit != null) {
            TextView del = UiKit.sheetButton(sh, ar ? "حذف الملاحظة" : "Delete note", true);
            del.setOnClickListener(v -> confirm(a, ar ? "حذف الملاحظة؟" : "Delete note?", "", () -> { r.deleteNote(edit.id); sh.dismiss(); }));
        }
        sh.show();
    }

    // ---------------- محفظة ----------------

    public static void wallet(Activity a, Models.Wallet edit) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        final Models.Wallet w = edit != null ? edit : new Models.Wallet();
        UiKit.Sheet sh = UiKit.sheet(a, edit == null ? (ar ? "محفظة جديدة" : "New wallet") : (ar ? "تعديل المحفظة" : "Edit wallet"));
        sh.content.addView(UiKit.label(a, ar ? "الاسم" : "Name"));
        final TextView name = UiKit.field(a, ar ? "مثال: كاش، بنك عربي…" : "e.g. cash, bank…", false);
        name.setText(w.name);
        sh.content.addView(name);
        sh.content.addView(UiKit.label(a, ar ? "النوع" : "Type"));
        String[] kinds = ar ? new String[]{"نقد", "بطاقة", "بنك", "محفظة إلكترونية"}
                : new String[]{"Cash", "Card", "Bank", "E-wallet"};
        final int[] kSel = {w.kind};
        sh.content.addView(UiKit.chipRow(a, kinds, w.kind, i -> kSel[0] = i));
        sh.content.addView(UiKit.label(a, ar ? "الرصيد الافتتاحي" : "Opening balance"));
        final TextView init = UiKit.field(a, "0", true);
        if (edit != null) init.setText(Fmt.amount(a, w.initial));
        sh.content.addView(init);
        TextView save = UiKit.sheetButton(sh, ar ? "حفظ ✔" : "Save ✔", false);
        save.setOnClickListener(v -> {
            if (Ui.empty(name.getText().toString())) { Ui.toast(a, ar ? "اكتب اسمًا" : "Enter a name"); return; }
            w.name = name.getText().toString().trim();
            w.kind = kSel[0];
            try { w.initial = Double.parseDouble(init.getText().toString().trim()); } catch (Exception e) { w.initial = 0; }
            if (edit == null) r.addWallet(w); else r.updateWallet(w);
            sh.dismiss();
        });
        sh.show();
    }

    // ---------------- هدف ----------------

    public static void goal(Activity a, Models.Goal edit) {
        boolean ar = ar(a);
        Repo r = Repo.get(a);
        final Models.Goal g = edit != null ? edit : new Models.Goal();
        UiKit.Sheet sh = UiKit.sheet(a, edit == null ? (ar ? "هدف ادخار جديد" : "New savings goal") : (ar ? "تعديل الهدف" : "Edit goal"));
        sh.content.addView(UiKit.label(a, ar ? "اسم الهدف" : "Goal name"));
        final TextView title = UiKit.field(a, ar ? "مثال: صندوق طوارئ، سفر…" : "e.g. emergency fund…", false);
        title.setText(g.title);
        sh.content.addView(title);
        sh.content.addView(UiKit.label(a, ar ? "المبلغ المستهدف" : "Target amount"));
        final TextView target = UiKit.field(a, "0", true);
        if (edit != null) target.setText(Fmt.amount(a, g.target));
        sh.content.addView(target);
        sh.content.addView(UiKit.label(a, ar ? "المدّخر حتى الآن" : "Saved so far"));
        final TextView saved = UiKit.field(a, "0", true);
        if (edit != null) saved.setText(Fmt.amount(a, g.saved));
        sh.content.addView(saved);
        TextView save = UiKit.sheetButton(sh, ar ? "حفظ الهدف ✔" : "Save goal ✔", false);
        save.setOnClickListener(v -> {
            double tg, sv;
            try { tg = Double.parseDouble(target.getText().toString().trim()); } catch (Exception e) { tg = 0; }
            try { sv = Double.parseDouble(saved.getText().toString().trim()); } catch (Exception e) { sv = 0; }
            if (tg <= 0 || Ui.empty(title.getText().toString())) { Ui.toast(a, ar ? "أكمل الاسم والهدف" : "Fill name and target"); return; }
            g.title = title.getText().toString().trim();
            g.target = tg;
            g.saved = sv;
            if (edit == null) r.addGoal(g); else r.updateGoal(g);
            sh.dismiss();
        });
        if (edit != null) {
            TextView del = UiKit.sheetButton(sh, ar ? "حذف الهدف" : "Delete goal", true);
            del.setOnClickListener(v -> confirm(a, ar ? "حذف الهدف؟" : "Delete goal?", "", () -> { r.deleteGoal(edit.id); sh.dismiss(); }));
        }
        sh.show();
    }

    // ---------------- أدوات ----------------

    public interface DatePick { void onPick(long ts); }

    public static void pickDate(Activity a, long initial, DatePick cb) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(initial);
        new DatePickerDialog(a, (vp, y, m, d) -> {
            Calendar pick = Calendar.getInstance();
            pick.set(y, m, d, 12, 0, 0);
            cb.onPick(pick.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    public static void confirm(Activity a, String title, String msg, Runnable onYes) {
        boolean ar = ar(a);
        UiKit.Sheet sh = UiKit.sheetCompact(a, title);
        if (msg != null && !msg.isEmpty()) {
            TextView m = Ui.txt(a, msg, 14, R.color.text_secondary);
            sh.content.addView(m);
        }
        TextView yes = UiKit.sheetButton(sh, ar ? "نعم، متابعة" : "Yes, continue", true);
        yes.setOnClickListener(v -> { sh.dismiss(); onYes.run(); });
        TextView no = UiKit.sheetButton(sh, ar ? "إلغاء" : "Cancel", false);
        no.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 15, a));
        no.setTextColor(Ui.color(a, R.color.text_primary));
        no.setOnClickListener(v -> sh.dismiss());
        sh.show();
    }
}
