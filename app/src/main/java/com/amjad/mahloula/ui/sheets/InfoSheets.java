package com.amjad.mahloula.ui.sheets;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.app.TimePickerDialog;

import com.amjad.mahloula.R;
import com.amjad.mahloula.brain.Content;
import com.amjad.mahloula.data.Backup;
import com.amjad.mahloula.lock.Security;
import com.amjad.mahloula.notify.Notifier;
import com.amjad.mahloula.ui.kit.PinPad;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.util.Fmt;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

import java.io.File;

/** شيتات المعلومات: الإعدادات، حول التطبيق، النسخ الاحتياطي، الدرس، القاعدة. */
public final class InfoSheets {

    private InfoSheets() {}

    private static boolean ar(Activity a) { return Sheets.ar(a); }

    // ---------------- الإعدادات ----------------

    public static void settings(Activity a) {
        boolean ar = ar(a);
        Prefs p = Prefs.get(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "الإعدادات" : "Settings");

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "💰 المال" : "💰 Money"));

        sh.content.addView(UiKit.label(a, ar ? "العملة" : "Currency"));
        String[] curs = {"$", "ر.س", "د.إ", "ج.م", "د.أ", "€", "£", "د.م", "د.ج", "د.ت", "د.ك", "ر.ق", "د.ب", "ر.ع", "د.ع", "₺", "ر.ي", "₹"};
        int sel = 0;
        for (int i = 0; i < curs.length; i++) if (curs[i].equals(p.currency())) sel = i;
        sh.content.addView(UiKit.chipRow(a, curs, sel, i -> p.currency(curs[i])));

        sh.content.addView(UiKit.label(a, ar ? "الميزانية الشهرية (سقف الصرف)" : "Monthly budget (spending cap)"));
        TextView budget = UiKit.field(a, "0", true);
        budget.setText(p.monthlyBudget() > 0 ? Fmt.amount(a, p.monthlyBudget()) : "");
        sh.content.addView(budget);
        sh.content.addView(UiKit.label(a, ar ? "الدخل الشهري التقريبي" : "Approximate monthly income"));
        TextView income = UiKit.field(a, "0", true);
        income.setText(p.monthlyIncome() > 0 ? Fmt.amount(a, p.monthlyIncome()) : "");
        sh.content.addView(income);
        TextView saveMoney = UiKit.button(a, ar ? "حفظ الإعدادات المالية" : "Save money settings", 0, true);
        saveMoney.setOnClickListener(v -> {
            try { p.monthlyBudget(Double.parseDouble(budget.getText().toString().trim())); } catch (Exception ignored) {}
            try { p.monthlyIncome(Double.parseDouble(income.getText().toString().trim())); } catch (Exception ignored) {}
            Ui.toast(a, ar ? "تم الحفظ ✔" : "Saved ✔");
        });
        sh.content.addView(saveMoney);

        Switch digits = switchRow(a, ar ? "أرقام عربية مشرقية (١٢٣)" : "Eastern Arabic digits (١٢٣)", p.easternDigits());
        digits.setOnCheckedChangeListener((v, on) -> p.easternDigits(on));
        sh.content.addView(digits);

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "🔐 الأمان" : "🔐 Security"));
        Switch lock = switchRow(a, ar ? "قفل التطبيق عند الفتح" : "Lock app on open", p.lockEnabled());
        sh.content.addView(lock);
        Switch bio = switchRow(a, ar ? "فتح بالبصمة" : "Fingerprint unlock", p.biometricEnabled());
        sh.content.addView(bio);
        Switch bioOnly = switchRow(a, ar ? "بصمة فقط بدون رمز احتياطي" : "Fingerprint only (no backup PIN)", p.biometricOnly());
        sh.content.addView(bioOnly);
        TextView pinBtn = UiKit.button(a, p.hasPin()
                ? (ar ? "تغيير رمز PIN الاحتياطي" : "Change backup PIN")
                : (ar ? "إنشاء رمز PIN احتياطي" : "Create backup PIN"), R.drawable.ic_lock, false);
        sh.content.addView(pinBtn);
        pinBtn.setOnClickListener(v -> setupPinFlow(sh, a));
        lock.setOnCheckedChangeListener((v, on) -> {
            p.lockEnabled(on);
            if (on && !Security.hasBiometric(a) && !p.hasPin()) setupPinFlow(sh, a);
        });
        bio.setOnCheckedChangeListener((v, on) -> p.biometricEnabled(on));
        bioOnly.setOnCheckedChangeListener((v, on) -> p.biometricOnly(on));

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "🔔 التنبيهات" : "🔔 Alerts"));
        Switch alerts = switchRow(a, ar ? "تنبيهات المستشار المالي (تجاوز/شذوذ/تسريبات)" : "Advisor alerts (breach/anomaly/leaks)", p.alertsOn());
        alerts.setOnCheckedChangeListener((v, on) -> p.alertsOn(on));
        sh.content.addView(alerts);
        Switch rem = switchRow(a, ar ? "تذكير يومي بتسجيل المصاريف" : "Daily spending reminder", p.remindersOn());
        sh.content.addView(rem);
        TextView remTime = UiKit.button(a, (ar ? "موعد التذكير: " : "Reminder time: ")
                + Fmt.digits(a, String.format("%02d:00", p.reminderHour())), R.drawable.ic_clock, false);
        sh.content.addView(remTime);
        remTime.setOnClickListener(v -> new TimePickerDialog(a, (tp, h, m) -> {
            p.reminderHour(h);
            remTime.setText((ar ? "موعد التذكير: " : "Reminder time: ") + Fmt.digits(a, String.format("%02d:00", h)));
            Notifier.scheduleDaily(a);
        }, p.reminderHour(), 0, true).show());
        rem.setOnCheckedChangeListener((v, on) -> {
            p.remindersOn(on);
            if (on) Notifier.scheduleDaily(a); else Notifier.cancelDaily(a);
        });

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "🎨 المظهر واللغة" : "🎨 Look & language"));
        sh.content.addView(UiKit.label(a, ar ? "السمة" : "Theme"));
        String[] themes = ar ? new String[]{"تلقائي (النظام)", "فاتح", "داكن"} : new String[]{"System", "Light", "Dark"};
        sh.content.addView(UiKit.chipRow(a, themes, p.themeMode(), i -> {
            p.themeMode(i);
            sh.dismiss();
            a.recreate();
        }));
        sh.content.addView(UiKit.label(a, ar ? "اللغة" : "Language"));
        String[] langs = ar ? new String[]{"العربية", "English", "لغة النظام"} : new String[]{"Arabic", "English", "System"};
        int lSel = p.language().equals("ar") ? 0 : p.language().equals("en") ? 1 : 2;
        sh.content.addView(UiKit.chipRow(a, langs, lSel, i -> {
            p.language(i == 0 ? "ar" : i == 1 ? "en" : "system");
            sh.dismiss();
            a.recreate();
        }));

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "🗄 البيانات" : "🗄 Data"));
        TextView backupBtn = UiKit.button(a, ar ? "نسخ احتياطي واستعادة" : "Backup & restore", R.drawable.ic_save_disk, false);
        backupBtn.setOnClickListener(v -> { sh.dismiss(); backup(a); });
        sh.content.addView(backupBtn);
        TextView wipe = UiKit.button(a, ar ? "مسح كل البيانات" : "Erase all data", R.drawable.ic_trash, true);
        wipe.setBackground(Ui.ripple(a, Ui.round(Ui.color(a, R.color.expense), 15, a), android.graphics.Color.argb(30, 255, 255, 255)));
        wipe.setOnClickListener(v -> Sheets.confirm(a, ar ? "مسح كل البيانات؟" : "Erase everything?",
                ar ? "ستُحذف كل العمليات والديون والملاحظات والأهداف نهائيًا." : "All entries, debts, notes and goals will be deleted.",
                () -> {
                    for (com.amjad.mahloula.data.Models.Tx t : com.amjad.mahloula.data.Repo.get(a).allTx())
                        com.amjad.mahloula.data.Repo.get(a).deleteTx(t.id);
                    for (com.amjad.mahloula.data.Models.Debt d : com.amjad.mahloula.data.Repo.get(a).debts())
                        com.amjad.mahloula.data.Repo.get(a).deleteDebt(d.id);
                    for (com.amjad.mahloula.data.Models.Note n : com.amjad.mahloula.data.Repo.get(a).notes(null))
                        com.amjad.mahloula.data.Repo.get(a).deleteNote(n.id);
                    for (com.amjad.mahloula.data.Models.Goal g : com.amjad.mahloula.data.Repo.get(a).goals())
                        com.amjad.mahloula.data.Repo.get(a).deleteGoal(g.id);
                    sh.dismiss();
                    Ui.toast(a, ar ? "تم المسح" : "Erased");
                }));
        sh.content.addView(wipe);

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "ℹ️ حول" : "ℹ️ About"));
        TextView about = UiKit.button(a, ar ? "عن مَحلولة والمطوّر" : "About Mahloula & developer", R.drawable.ic_info, false);
        about.setOnClickListener(v -> { sh.dismiss(); about(a); });
        sh.content.addView(about);

        sh.show();
    }

    private static void setupPinFlow(UiKit.Sheet sh, Activity a) {
        boolean ar = ar(a);
        UiKit.Sheet pin = UiKit.sheet(a, ar ? "رمز PIN احتياطي (4 أرقام)" : "Backup PIN (4 digits)");
        pin.content.addView(Ui.txt(a, ar ? "اختر رمزًا ثم أكّده في اللوحة الثانية." : "Choose a PIN, then confirm below.", 13.5f, R.color.text_secondary));
        PinPad p1 = new PinPad(a, 4);
        pin.content.addView(p1);
        TextView l2 = Ui.txt(a, ar ? "أكّد الرمز:" : "Confirm:", 13, R.color.text_secondary);
        l2.setVisibility(android.view.View.GONE);
        pin.content.addView(l2);
        PinPad p2 = new PinPad(a, 4);
        p2.setVisibility(android.view.View.GONE);
        pin.content.addView(p2);
        final String[] first = {null};
        p1.setListener(new PinPad.Listener() {
            @Override public void onComplete(String s) {
                first[0] = s;
                p1.setVisibility(android.view.View.GONE);
                l2.setVisibility(android.view.View.VISIBLE);
                p2.setVisibility(android.view.View.VISIBLE);
            }
            @Override public void onChanged(int len) {}
        });
        p2.setListener(new PinPad.Listener() {
            @Override public void onComplete(String s) {
                if (s.equals(first[0])) {
                    Security.setupPin(a, s);
                    Ui.toast(a, ar ? "تم حفظ الرمز ✔" : "PIN saved ✔");
                    pin.dismiss();
                } else {
                    Ui.toast(a, ar ? "غير متطابقين" : "Mismatch");
                    p1.clear(); p2.clear();
                    p1.setVisibility(android.view.View.VISIBLE);
                    p2.setVisibility(android.view.View.GONE);
                    l2.setVisibility(android.view.View.GONE);
                }
            }
            @Override public void onChanged(int len) {}
        });
        pin.show();
    }

    private static Switch switchRow(Activity a, String label, boolean checked) {
        Switch s = new Switch(a);
        s.setText(label);
        s.setChecked(checked);
        s.setTextSize(14);
        s.setTypeface(Ui.font(a, Typeface.BOLD));
        s.setTextColor(Ui.color(a, R.color.text_primary));
        s.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
        s.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.color(a, R.color.brand_300)));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.color(a, R.color.brand_600)));
        return s;
    }

    // ---------------- النسخ الاحتياطي ----------------

    public static final int REQ_SAVE = 61;
    public static final int REQ_OPEN = 62;

    public static void backup(Activity a) {
        boolean ar = ar(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "نسخ احتياطي واستعادة" : "Backup & restore");
        sh.content.addView(Ui.txt(a, ar
                ? "نسخك تُحفظ كملف JSON على جهازك. يمكنك نسخه لأي مكان (بلوتوث، واتساب، درايف…) واستعادته anytime."
                : "Backups are JSON files on your device. Copy them anywhere and restore anytime.", 13.5f, R.color.text_secondary));

        TextView exp = UiKit.button(a, ar ? "إنشاء نسخة احتياطية الآن" : "Create backup now", R.drawable.ic_download, true);
        exp.setOnClickListener(v -> {
            try {
                File f = Backup.export(a);
                shareFile(a, f, "application/json");
                sh.dismiss();
            } catch (Exception e) {
                Ui.toast(a, ar ? "تعذّر إنشاء النسخة" : "Backup failed");
            }
        });
        sh.content.addView(exp);

        TextView csv = UiKit.button(a, ar ? "تصدير العمليات CSV (لـ Excel)" : "Export transactions CSV (Excel)", R.drawable.ic_bars, false);
        csv.setOnClickListener(v -> {
            try {
                File f = Backup.exportCsv(a);
                shareFile(a, f, "text/csv");
                sh.dismiss();
            } catch (Exception e) {
                Ui.toast(a, ar ? "تعذّر التصدير" : "Export failed");
            }
        });
        sh.content.addView(csv);

        TextView imp = UiKit.button(a, ar ? "استعادة من ملف نسخة" : "Restore from backup file", R.drawable.ic_upload, false);
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            a.startActivityForResult(i, REQ_OPEN);
            sh.dismiss();
        });
        sh.content.addView(imp);

        // نسخ محفوظة داخليًا
        File[] files = Backup.backupsDir(a).listFiles();
        if (files != null && files.length > 0) {
            sh.content.addView(UiKit.sectionTitleIn(a, ar ? "نسخ على هذا الجهاز" : "Backups on this device"));
            for (File f : files) {
                LinearLayout row = UiKit.row(a, R.drawable.ic_save_disk, R.color.brand_600, R.color.brand_100,
                        f.getName(), Fmt.fullDate(a, f.lastModified()), null, 0);
                row.setOnClickListener(v -> Sheets.confirm(a, ar ? "استعادة هذه النسخة؟" : "Restore this backup?",
                        ar ? "ستُستبدل بياناتك الحالية ببيانات النسخة." : "Current data will be replaced.",
                        () -> {
                            try {
                                Backup.restore(a, new java.io.FileInputStream(f));
                                Ui.toast(a, ar ? "تمت الاستعادة ✔" : "Restored ✔");
                                sh.dismiss();
                            } catch (Exception e) {
                                Ui.toast(a, ar ? "ملف غير صالح" : "Invalid file");
                            }
                        }));
                sh.content.addView(row);
            }
        }
        sh.show();
    }

    public static void shareFile(Activity a, File f, String mime) {
        Uri uri = Uri.parse("content://com.amjad.mahloula.files/" + f.getName());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, f.getName()));
    }

    // ---------------- حول ----------------

    public static void about(Activity a) {
        boolean ar = ar(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "عن مَحلولة" : "About Mahloula");
        android.widget.ImageView logo = new android.widget.ImageView(a);
        logo.setImageDrawable(a.getDrawable(R.drawable.logo_mark));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(a, 88), Ui.dp(a, 88));
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        logo.setLayoutParams(llp);
        sh.content.addView(logo);
        TextView name = Ui.txt(a, a.getString(R.string.app_name) + "  •  v1.0.0", 18, R.color.text_primary, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(a, 8);
        name.setLayoutParams(nlp);
        sh.content.addView(name);
        TextView tag = Ui.txt(a, a.getString(R.string.app_tagline), 13.5f, R.color.text_secondary);
        tag.setGravity(Gravity.CENTER);
        sh.content.addView(tag);

        LinearLayout credit = UiKit.card(a, 16);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(a, 16);
        credit.setLayoutParams(clp);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        credit.addView(Ui.txt(a, ar ? "✍️ التأليف والتطوير" : "✍️ Authorship & development", 14, R.color.text_secondary, Typeface.BOLD));
        TextView dev = Ui.txt(a, ar ? "أمجد حبيب" : "Amjad Habib", 22, R.color.brand_600, Typeface.BOLD);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(a, 4);
        dev.setLayoutParams(dlp);
        credit.addView(dev);
        TextView line = Ui.txt(a, ar
                ? "تم تأليف وتطوير هذا التطبيق بواسطة أمجد حبيب — إصدار 1.0.0"
                : "Authored & developed by Amjad Habib — version 1.0.0", 12.5f, R.color.text_tertiary);
        line.setGravity(Gravity.CENTER);
        credit.addView(line);
        sh.content.addView(credit);

        String[][] facts = ar ? new String[][]{
                {"📴 بدون إنترنت", "كل البيانات على جهازك فقط. التطبيق لا يطلب إذن اتصال إطلاقًا."},
                {"🔐 الخصوصية", "بصمة إصبع + رمز مجزّأ SHA-256. لا إعلانات ولا تتبّع."},
                {"🧠 المستشار المالي", "محرك قواعد يفحص صرفك الحقيقي ويولّد تنبيهات وخططًا."},
                {"🎓 الأكاديمية", "12 درسًا و18 قاعدة من أفضل الممارسات العالمية (50/30/20، كرة الثلج، قاعدة 72…)."},
        } : new String[][]{
                {"📴 Offline-first", "All data stays on device; the app never requests internet permission."},
                {"🔐 Privacy", "Fingerprint + SHA-256-hashed PIN. No ads, no tracking."},
                {"🧠 Advisor engine", "Rule engine scans your real spending for alerts & plans."},
                {"🎓 Academy", "12 lessons & 18 rules from global best practices."},
        };
        for (String[] f : facts) {
            LinearLayout c2 = UiKit.cardStroke(a, 14);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
            lp2.topMargin = Ui.dp(a, 8);
            c2.setLayoutParams(lp2);
            c2.addView(Ui.txt(a, f[0], 14, R.color.text_primary, Typeface.BOLD));
            TextView b2 = Ui.txt(a, f[1], 12.5f, R.color.text_secondary);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(a, 3);
            b2.setLayoutParams(blp);
            c2.addView(b2);
            sh.content.addView(c2);
        }
        sh.show();
    }

    // ---------------- درس ----------------

    public static void lesson(Activity a, int idx) {
        boolean ar = ar(a);
        Content.Lesson l = Content.LESSONS.get(idx);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? l.titleAr : l.titleEn);
        TextView intro = Ui.txt(a, ar ? l.introAr : l.introEn, 14.5f, R.color.text_secondary);
        intro.setLineSpacing(0, 1.35f);
        sh.content.addView(intro);

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "✦ خلاصات عملية" : "✦ Practical takeaways"));
        for (String tip : (ar ? l.tipsAr : l.tipsEn)) {
            LinearLayout row = Ui.h(a);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(a, 6);
            row.setLayoutParams(rlp);
            TextView bullet = Ui.txt(a, "•", 16, R.color.brand_500, Typeface.BOLD);
            row.addView(bullet);
            TextView t = Ui.txt(a, tip, 13.5f, R.color.text_primary);
            t.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.setMarginStart(Ui.dp(a, 8));
            t.setLayoutParams(tlp);
            row.addView(t);
            sh.content.addView(row);
        }

        LinearLayout act = UiKit.card(a, 14);
        act.setBackground(Ui.round(Ui.color(a, R.color.gold_100), 16, a));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(a, 14);
        act.setLayoutParams(alp);
        act.addView(Ui.txt(a, ar ? "🎯 خطوتك الآن" : "🎯 Your action now", 13, R.color.gold_600, Typeface.BOLD));
        TextView at = Ui.txt(a, ar ? l.actionAr : l.actionEn, 13.5f, R.color.text_primary);
        LinearLayout.LayoutParams atlp = new LinearLayout.LayoutParams(-2, -2);
        atlp.topMargin = Ui.dp(a, 4);
        at.setLayoutParams(atlp);
        act.addView(at);
        sh.content.addView(act);

        TextView src = Ui.txt(a, (ar ? "المصدر: " : "Source: ") + (ar ? l.sourceAr : l.sourceEn), 11.5f, R.color.text_tertiary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(a, 12);
        src.setLayoutParams(slp);
        sh.content.addView(src);

        long mask = Prefs.get(a).lessonsDoneMask();
        boolean done = (mask & (1L << idx)) != 0;
        TextView mark = UiKit.sheetButton(sh, done
                ? (ar ? "✔ درس مكتمل — اضغط لإلغاء الإكمال" : "✔ Completed — tap to unmark")
                : (ar ? "أكملت الدرس ✔" : "I completed this lesson ✔"), false);
        mark.setOnClickListener(v -> {
            long m = Prefs.get(a).lessonsDoneMask();
            m = done ? (m & ~(1L << idx)) : (m | (1L << idx));
            Prefs.get(a).lessonsDoneMask(m);
            Prefs.get(a).lessonProgress(Long.bitCount(m));
            sh.dismiss();
        });
        sh.show();
    }

    // ---------------- قاعدة ----------------

    public static void rule(Activity a, int idx) {
        boolean ar = ar(a);
        Content.Rule r = Content.RULES.get(idx);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? r.nameAr : r.nameEn);
        TextView tag = Ui.txt(a, ar ? r.tagAr : "", 12, R.color.brand_600, Typeface.BOLD);
        sh.content.addView(tag);
        TextView body = Ui.txt(a, ar ? r.bodyAr : r.bodyEn, 15, R.color.text_primary);
        body.setLineSpacing(0, 1.4f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(a, 8);
        body.setLayoutParams(blp);
        sh.content.addView(body);
        sh.show();
    }
}
