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
import java.util.Calendar;

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

        sh.content.addView(UiKit.label(a, ar ? "العملة: الليرة السورية (ل.س) — مثبتة" : "Currency: Syrian pound (fixed)"));

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
        sh.content.addView(UiKit.label(a, ar
                ? "مثبّت: ألوان مَحلولة الرسمية (أبيض/أسود/برتقالي) بلا خيارات، واللغة تتبع لغة نظام جهازك تلقائيًا."
                : "Fixed: official Mahloula colors (white/black/orange); language follows your system automatically."));

        Switch sound = switchRow(a, ar ? "صوت التنبيهات والإشعارات" : "Alert & notification sounds", p.soundOn());
        sound.setOnCheckedChangeListener((v, on) -> { p.soundOn(on); Notifier.recreateChannels(a); });
        sh.content.addView(sound);

        sh.content.addView(UiKit.sectionTitleIn(a, ar ? "👛 المحافظ والحسابات" : "👛 Wallets & accounts"));
        TextView walletsBtn = UiKit.button(a, ar ? "إدارة المحافظ والأرصدة الافتتاحية" : "Manage wallets & opening balances", R.drawable.ic_wallet, false);
        walletsBtn.setOnClickListener(v -> { sh.dismiss(); wallets(a); });
        sh.content.addView(walletsBtn);

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
        TextView name = Ui.txt(a, a.getString(R.string.app_name) + "  •  v2.1.0", 18, R.color.text_primary, Typeface.BOLD);
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
                ? "تم تأليف وتطوير هذا التطبيق بواسطة أمجد حبيب — إصدار 2.1.0"
                : "Authored & developed by Amjad Habib — version 1.1.0", 12.5f, R.color.text_tertiary);
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




    // ---------------- حاسبة النمو المركّب ----------------
    public static void compound(Activity a) {
        final boolean ar = ar(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "📈 حاسبة النمو المركّب" : "Compound growth calculator");
        sh.content.addView(Ui.txt(a, ar
                ? "لو ادخرت مبلغًا ثابتًا شهريًا بعائد سنوي مركّب — كم يصبح؟"
                : "Fixed monthly saving at compound annual return — what does it become?", 13, R.color.text_secondary));
        final TextView amt = UiKit.field(a, ar ? "المبلغ الشهري (ل.س)" : "Monthly amount", true);
        amt.setText("10000");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(a, 10);
        amt.setLayoutParams(alp);
        sh.content.addView(amt);
        final int[] years = {5};
        final int[] rate = {7};
        final TextView res = Ui.txt(a, "", 14, R.color.text_primary, Typeface.BOLD);
        res.setLineSpacing(0, 1.4f);
        res.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 14, a));
        res.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        final Runnable[] rec = new Runnable[1];
        sh.content.addView(UiKit.label(a, ar ? "المدة" : "Years"));
        UiKit.HorizontalScroll yrow = UiKit.chipRow(a, new String[]{"1", "3", "5", "10", "20"}, 2, i -> { years[0] = new int[]{1, 3, 5, 10, 20}[i]; rec[0].run(); });
        sh.content.addView(yrow);
        sh.content.addView(UiKit.label(a, ar ? "العائد السنوي" : "Annual return"));
        UiKit.HorizontalScroll rrow = UiKit.chipRow(a, new String[]{"5%", "7%", "10%"}, 1, i -> { rate[0] = new int[]{5, 7, 10}[i]; rec[0].run(); });
        sh.content.addView(rrow);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 12);
        res.setLayoutParams(rlp);
        sh.content.addView(res);
        rec[0] = () -> {
            double m = 0;
            try { m = Double.parseDouble(amt.getText().toString().trim()); } catch (Exception ignored) {}
            double fv = com.amjad.mahloula.brain.Advisor.compoundFV(m, years[0], rate[0]);
            double contributed = m * years[0] * 12;
            res.setText((ar ? "بعد " : "After ") + years[0] + (ar ? " سنة:\n" : " years:\n")
                    + (ar ? "• ما ستودعه: " : "• Contributed: ") + Fmt.money(a, contributed) + "\n"
                    + (ar ? "• القيمة المركّبة: " : "• Compound value: ") + Fmt.money(a, fv) + "\n"
                    + (ar ? "• نماء المال وحده: " : "• Growth alone: ") + Fmt.money(a, fv - contributed));
        };
        rec[0].run();
        sh.show();
    }

    // ---------------- مساعد: اسأل المستشار ----------------
    public static void simulatorCoach(Activity a) {
        final boolean ar = ar(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "💬 اسأل المستشار" : "Ask the advisor");
        sh.content.addView(Ui.txt(a, ar
                ? "اختر سؤالك — يجيبك المدرّب بأرقامك الحقيقية لا بنصائح عامة."
                : "Pick a question — answered with your real numbers.", 13, R.color.text_secondary));
        final int[] topic = {0};
        final TextView amount = UiKit.field(a, ar ? "مبلغ اختياري (لسؤال الشراء)" : "Optional amount (buy question)", true);
        final TextView ans = Ui.txt(a, "", 13.5f, R.color.text_primary);
        ans.setLineSpacing(0, 1.45f);
        ans.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 14, a));
        ans.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        final Runnable[] rec = new Runnable[1];
        String[] qs = ar ? new String[]{"أوفر أكثر؟", "ديوني: أبدأ؟", "راتبي قليل", "أشتري غالي؟", "أين مالي؟", "عادة التسجيل"}
                         : new String[]{"Save more?", "Debts: start?", "Low income", "Big buy?", "Where's money?", "Logging habit"};
        UiKit.HorizontalScroll qrow = UiKit.chipRow(a, qs, 0, i -> { topic[0] = i; rec[0].run(); });
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, -2);
        qlp.topMargin = Ui.dp(a, 10);
        qrow.setLayoutParams(qlp);
        sh.content.addView(qrow);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(a, 10);
        amount.setLayoutParams(alp);
        sh.content.addView(amount);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 10);
        ans.setLayoutParams(rlp);
        sh.content.addView(ans);
        rec[0] = () -> {
            double amt = 0;
            try { amt = Double.parseDouble(amount.getText().toString().trim()); } catch (Exception ignored) {}
            ans.setText(com.amjad.mahloula.brain.Advisor.coachAnswer(a, topic[0], amt));
        };
        rec[0].run();
        sh.show();
    }

    // ---------------- سيموليتر التوفير ----------------
    public static void simulator(Activity a) {
        final boolean ar = ar(a);
        final com.amjad.mahloula.data.Repo r = com.amjad.mahloula.data.Repo.get(a);
        final com.amjad.mahloula.util.Prefs p = com.amjad.mahloula.util.Prefs.get(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "🧮 سيموليتر التوفير" : "Savings simulator");
        sh.content.addView(Ui.txt(a, ar
                ? "اختر نسبة خفض وفئة، وشاهد الأثر فورًا على توقع شهرك ووسادتك وأهدافك — بلا التزام حقيقي."
                : "Pick a cut % and category; see the impact instantly.", 13, R.color.text_secondary));
        sh.content.addView(UiKit.label(a, ar ? "نسبة الخفض" : "Cut percent"));
        final int[] cut = {10};
        final int[] catSel = {0};
        final java.util.List<com.amjad.mahloula.brain.Advisor.Env> envs = com.amjad.mahloula.brain.Advisor.envelopes(a);
        final TextView result = Ui.txt(a, "", 13.5f, R.color.text_primary, Typeface.BOLD);
        result.setLineSpacing(0, 1.4f);
        result.setBackground(Ui.round(Ui.color(a, R.color.surface_3), 14, a));
        result.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        final Runnable[] rec = new Runnable[1];
        UiKit.HorizontalScroll crow = UiKit.chipRow(a, new String[]{"5%", "10%", "15%", "25%"}, 1, i -> {
            cut[0] = new int[]{5, 10, 15, 25}[i];
            rec[0].run();
        });
        sh.content.addView(crow);
        String[] names = new String[Math.max(1, envs.size())];
        for (int i = 0; i < envs.size(); i++) names[i] = com.amjad.mahloula.data.Models.cat(envs.get(i).cat).ar;
        if (envs.isEmpty()) names[0] = ar ? "كل المصروف" : "All spending";
        sh.content.addView(UiKit.label(a, ar ? "الفئة" : "Category"));
        UiKit.HorizontalScroll catrow = UiKit.chipRow(a, names, 0, i -> { catSel[0] = i; rec[0].run(); });
        sh.content.addView(catrow);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(a, 12);
        result.setLayoutParams(rlp);
        sh.content.addView(result);
        rec[0] = () -> {
            Calendar now = Calendar.getInstance();
            com.amjad.mahloula.data.Models.MonthTotals mt = r.monthTotals(now);
            int day = now.get(Calendar.DAY_OF_MONTH);
            int dim = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            int left = dim - day;
            double catSpent = envs.isEmpty() ? mt.expense : (catSel[0] < envs.size() ? envs.get(catSel[0]).spent : mt.expense);
            double future = catSpent * left / Math.max(1, day);
            double saving = future * cut[0] / 100.0;
            double proj = com.amjad.mahloula.brain.Advisor.forecastMonth(a);
            double newProj = Math.max(0, proj - saving);
            double budget = p.monthlyBudget();
            StringBuilder sb = new StringBuilder();
            sb.append(ar ? "التوفير المتوقع بقية الشهر: " : "Expected saving rest of month: ").append(Fmt.money(a, saving)).append('\n');
            sb.append(ar ? "توقع نهاية الشهر: " : "Projected month-end: ").append(Fmt.money(a, proj))
              .append(" ← ").append(Fmt.money(a, newProj));
            if (budget > 0) sb.append(newProj <= budget ? (ar ? "  ✔ ضمن السقف" : "  ✔ within cap") : (ar ? "  ⚠️ فوق السقف" : "  ⚠️ over cap"));
            sb.append('\n');
            double incRef = mt.income > 0 ? mt.income : p.monthlyIncome();
            if (incRef > 0) {
                double srNew = (mt.saved + saving) / incRef * 100;
                sb.append(ar ? "معدل الادخار يصبح: " : "Savings rate becomes: ").append(Fmt.percent(a, srNew)).append('\n');
            }
            double avg90 = r.avgDaily(now.getTimeInMillis() - 90L * 86400000L, now.getTimeInMillis());
            if (avg90 > 0) sb.append(ar ? "وسادتك تكسب يوميًا: " : "Cushion gains daily: ").append(Fmt.money(a, saving / Math.max(1, left)));
            java.util.List<com.amjad.mahloula.data.Models.Goal> goals = r.goals();
            for (com.amjad.mahloula.data.Models.Goal g : goals) {
                if (g.due > 0 && g.target > g.saved) {
                    double monthlyOld = Math.max(mt.saved, 0);
                    double monthlyNew = monthlyOld + saving;
                    if (monthlyNew > 0) {
                        double mNew = (g.target - g.saved) / monthlyNew;
                        sb.append('\n').append(ar ? "هدف «" : "Goal '").append(g.title)
                          .append(ar ? "» يُبلغ في " : "' reached in ").append(Fmt.digits(a, String.valueOf((int) Math.ceil(mNew))))
                          .append(ar ? " شهرًا بدل " : " months instead of ")
                          .append(monthlyOld > 0 ? Fmt.digits(a, String.valueOf((int) Math.ceil((g.target - g.saved) / monthlyOld))) : "∞");
                    }
                    break;
                }
            }
            result.setText(sb.toString());
        };
        rec[0].run();
        sh.show();
    }

    // ---------------- المحافظ ----------------
    public static void wallets(Activity a) {
        boolean ar = ar(a);
        com.amjad.mahloula.data.Repo r = com.amjad.mahloula.data.Repo.get(a);
        UiKit.Sheet sh = UiKit.sheet(a, ar ? "المحافظ والحسابات" : "Wallets & accounts");
        for (final com.amjad.mahloula.data.Models.Wallet w : r.wallets()) {
            LinearLayout row = UiKit.row(a, w.icon(), R.color.brand_600, R.color.brand_100,
                    w.name + (w.def ? (ar ? "  (الرئيسية)" : "  (main)") : ""),
                    ar ? "الرصيد الحالي" : "Current balance",
                    com.amjad.mahloula.util.Fmt.amount(a, r.walletBalance(w.id)), R.color.text_primary);
            row.setOnClickListener(v -> { sh.dismiss(); Sheets.wallet(a, w); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 8);
            row.setLayoutParams(lp);
            sh.content.addView(row);
        }
        TextView add = UiKit.sheetButton(sh, ar ? "+ محفظة جديدة" : "+ New wallet", false);
        add.setOnClickListener(v -> { sh.dismiss(); Sheets.wallet(a, null); });
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
