package com.amjad.mahloula.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.lock.Security;
import com.amjad.mahloula.notify.Notifier;
import com.amjad.mahloula.ui.kit.PinPad;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/** الإعداد الأول: جولة ميزات + تفعيل البصمة + العملة والميزانية. */
public class OnboardingActivity extends BaseActivity {

    private Switch bioSwitch, bioOnlySwitch, pinSwitch;
    private LinearLayout pinBlock;
    private PinPad pad1, pad2;
    private String firstPin;
    private TextView budgetField, incomeField;
    private int currencyIdx = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs p = Prefs.get(this);
        boolean ar = !p.language().equals("en");
        boolean canBio = Security.hasBiometric(this);

        LinearLayout root = Ui.v(this);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = Ui.v(this);
        col.setPadding(Ui.dp(this, 22), Ui.dp(this, 40), Ui.dp(this, 22), Ui.dp(this, 30));

        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getDrawable(R.drawable.logo_mark));
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));
        col.addView(logo);

        TextView title = Ui.txt(this, ar ? "أهلًا بك في مَحلولة 👋" : "Welcome to Mahloula 👋",
                26, R.color.text_primary, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 14);
        title.setLayoutParams(tlp);
        col.addView(title);

        TextView sub = Ui.txt(this, ar
                ? "مصروفك الشخصي بإدارة محترفة: تتبع، تنبيهات ذكية، خطط ادخار عالمية — وكل بياناتك على جهازك، بدون إنترنت."
                : "Professional personal money management: tracking, smart alerts, global saving plans — all data stays on your device, offline.",
                14.5f, R.color.text_secondary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 8);
        sub.setLayoutParams(slp);
        col.addView(sub);

        // بطاقات الميزات
        int[][] feats = {
                {R.drawable.ic_receipt, R.string.app_name},
        };
        String[][] featData = ar ? new String[][]{
                {"خانّة المسحوبات", "سجّل كل مصروف بتصنيف وطريقة دفع، وراقب الفئات برسوم حيّة.", "ic_receipt"},
                {"خانّة تسديد الديون", "ديونك عليك ولك، بتتبّع التسديدات والمواعيد وتنبيهات التأخر.", "ic_handshake"},
                {"خانّة الملاحظات", "أفكارك وقوائمك المالية المثبّتة، بألوان وبحث فوري.", "ic_note"},
                {"خانّة تعليمي", "12 درسًا و18 قاعدة عالمية: من 50/30/20 إلى كرة الثلج وقاعدة 72.", "ic_cap"},
                {"مستشار ينبهك", "يفحص صرفك الحقيقي: تجاوز ميزانية، مصروف شاذ، تسريبات متكررة — وينبهك فورًا.", "ic_bulb"},
                {"بصمة وأمان", "التطبيق لا يفتح إلا ببصمتك، وبدون أي اتصال بالإنترنت.", "ic_fingerprint"},
        } : new String[][]{
                {"Spending tab", "Log every expense with category & method; live category charts.", "ic_receipt"},
                {"Debts tab", "Track what you owe and what's owed to you, with payments & due alerts.", "ic_handshake"},
                {"Notes tab", "Pinned money notes & lists with colors and instant search.", "ic_note"},
                {"Academy tab", "12 lessons & 18 global rules: 50/30/20, snowball, rule of 72…", "ic_cap"},
                {"Advisor alerts", "Scans your real spending: budget breach, anomalies, recurring leaks.", "ic_bulb"},
                {"Fingerprint security", "The app opens only with your fingerprint; fully offline.", "ic_fingerprint"},
        };
        for (String[] f : featData) {
            LinearLayout card = UiKit.card(this, 14);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.topMargin = Ui.dp(this, 10);
            card.setLayoutParams(clp);
            LinearLayout row = Ui.h(this);
            int iconRes = iconByName(f[2]);
            ImageView iv = Ui.iconSized(this, iconRes, Ui.color(this, R.color.brand_600), 26);
            row.addView(iv);
            LinearLayout tx = Ui.v(this);
            LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp2.setMarginStart(Ui.dp(this, 12));
            tx.setLayoutParams(tlp2);
            tx.addView(Ui.txt(this, f[0], 15, R.color.text_primary, Typeface.BOLD));
            TextView body = Ui.txt(this, f[1], 13, R.color.text_secondary);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(this, 3);
            body.setLayoutParams(blp);
            tx.addView(body);
            row.addView(tx);
            card.addView(row);
            col.addView(card);
        }

        // بطاقة الأمان
        LinearLayout secCard = UiKit.card(this, 16);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1, -2);
        sclp.topMargin = Ui.dp(this, 18);
        secCard.setLayoutParams(sclp);
        secCard.addView(Ui.txt(this, ar ? "🔐 أمان التطبيق" : "🔐 App security", 16, R.color.text_primary, Typeface.BOLD));

        bioSwitch = switchRow(col.getContext(), ar ? "فتح التطبيق بالبصمة" : "Open app with fingerprint", canBio);
        secCard.addView(bioSwitch);
        bioOnlySwitch = switchRow(this, ar ? "بصمة فقط (بدون رمز احتياطي)" : "Fingerprint only (no backup PIN)", true);
        secCard.addView(bioOnlySwitch);
        pinSwitch = switchRow(this, ar ? "رمز PIN احتياطي (4 أرقام)" : "Backup PIN (4 digits)", !canBio);
        secCard.addView(pinSwitch);

        pinBlock = Ui.v(this);
        pinBlock.setVisibility(android.view.View.GONE);
        pinBlock.addView(Ui.txt(this, ar ? "اختر رمزًا ثم أكّده:" : "Choose a PIN then confirm:", 13, R.color.text_secondary));
        pad1 = new PinPad(this, 4);
        pinBlock.addView(pad1);
        TextView confirmLabel = Ui.txt(this, ar ? "أكّد الرمز:" : "Confirm PIN:", 13, R.color.text_secondary);
        confirmLabel.setVisibility(android.view.View.GONE);
        pinBlock.addView(confirmLabel);
        pad2 = new PinPad(this, 4);
        pad2.setVisibility(android.view.View.GONE);
        pinBlock.addView(pad2);
        secCard.addView(pinBlock);
        col.addView(secCard);

        wirePinPads(ar, confirmLabel);

        // بطاقة المال
        LinearLayout moneyCard = UiKit.card(this, 16);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = Ui.dp(this, 12);
        moneyCard.setLayoutParams(mlp);
        moneyCard.addView(Ui.txt(this, ar ? "💰 إعدادات سريعة (اختيارية)" : "💰 Quick settings (optional)", 16, R.color.text_primary, Typeface.BOLD));

        moneyCard.addView(UiKit.label(this, ar ? "العملة" : "Currency"));
        String[] curLabels = {"ل.س", "$", "ر.س", "د.إ", "ج.م", "د.أ", "€", "د.م", "د.ج", "د.ك", "ر.ق"};
        UiKit.HorizontalScroll curRow = UiKit.chipRow(this, curLabels, 0, i -> currencyIdx = i);
        moneyCard.addView(curRow);

        moneyCard.addView(UiKit.label(this, ar ? "ميزانيتك الشهرية (سقف الصرف)" : "Monthly budget (spending cap)"));
        budgetField = UiKit.field(this, ar ? "مثال: 1500" : "e.g. 1500", true);
        moneyCard.addView(budgetField);

        moneyCard.addView(UiKit.label(this, ar ? "دخلك الشهري التقريبي" : "Approximate monthly income"));
        incomeField = UiKit.field(this, ar ? "مثال: 2000" : "e.g. 2000", true);
        moneyCard.addView(incomeField);
        col.addView(moneyCard);

        TextView start = UiKit.button(this, ar ? "ابدأ إدارة مصاريفك 🚀" : "Start managing money 🚀", 0, true);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-1, -2);
        stlp.topMargin = Ui.dp(this, 22);
        start.setLayoutParams(stlp);
        start.setOnClickListener(v -> finishSetup());
        col.addView(start);

        TextView credit = Ui.txt(this, ar ? "تم تأليف وتطوير هذا التطبيق بواسطة أمجد حبيب" : "Authored & developed by Amjad Habib",
                12, R.color.text_tertiary);
        credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(-2, -2);
        crlp.topMargin = Ui.dp(this, 16);
        credit.setLayoutParams(crlp);
        col.addView(credit);

        sv.addView(col);
        root.addView(sv);
        setContentView(root);
    }

    private int iconByName(String n) {
        switch (n) {
            case "ic_handshake": return R.drawable.ic_handshake;
            case "ic_note": return R.drawable.ic_note;
            case "ic_cap": return R.drawable.ic_cap;
            case "ic_bulb": return R.drawable.ic_bulb;
            case "ic_fingerprint": return R.drawable.ic_fingerprint;
            default: return R.drawable.ic_receipt;
        }
    }

    private Switch switchRow(android.content.Context c, String label, boolean checked) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setChecked(checked);
        s.setTextSize(14.5f);
        s.setTypeface(Ui.font(c, Typeface.BOLD));
        s.setTextColor(Ui.color(c, R.color.text_primary));
        s.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
        s.setTrackTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.brand_300)));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.brand_600)));
        return s;
    }

    private void wirePinPads(boolean ar, TextView confirmLabel) {
        pinSwitch.setOnCheckedChangeListener((v, on) ->
                pinBlock.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE));
        pad1.setListener(new PinPad.Listener() {
            @Override
            public void onComplete(String pin) {
                firstPin = pin;
                pad1.setVisibility(android.view.View.GONE);
                confirmLabel.setVisibility(android.view.View.VISIBLE);
                pad2.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onChanged(int len) {}
        });
        pad2.setListener(new PinPad.Listener() {
            @Override
            public void onComplete(String pin) {
                if (firstPin != null && firstPin.equals(pin)) {
                    Security.setupPin(OnboardingActivity.this, pin);
                    Ui.toast(OnboardingActivity.this, ar ? "تم حفظ الرمز الاحتياطي ✔" : "Backup PIN saved ✔");
                    pad2.clear();
                } else {
                    Ui.toast(OnboardingActivity.this, ar ? "الرمزان غير متطابقين" : "PINs don't match");
                    firstPin = null;
                    pad1.clear();
                    pad2.clear();
                    pad1.setVisibility(android.view.View.VISIBLE);
                    pad2.setVisibility(android.view.View.GONE);
                    confirmLabel.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onChanged(int len) {}
        });
    }

    private void finishSetup() {
        Prefs p = Prefs.get(this);
        boolean canBio = Security.hasBiometric(this);
        boolean bioOn = bioSwitch.isChecked() && canBio;
        boolean pinSet = p.hasPin() || pinSwitch.isChecked();
        p.lockEnabled(bioOn || (pinSwitch.isChecked() && firstPin != null) || p.hasPin());
        p.biometricEnabled(bioOn);
        p.biometricOnly(bioOn && bioOnlySwitch.isChecked());
        if (bioOn && bioOnlySwitch.isChecked() && !p.hasPin()) {
            // بصمة فقط: لا رمز
        }
        String[] curValues = {"ل.س", "$", "ر.س", "د.إ", "ج.م", "د.أ", "€", "د.م", "د.ج", "د.ك", "ر.ق"};
        p.currency(curValues[currencyIdx]);
        try {
            double budget = Double.parseDouble(budgetField.getText().toString().trim());
            p.monthlyBudget(budget);
        } catch (Exception ignored) {}
        try {
            double inc = Double.parseDouble(incomeField.getText().toString().trim());
            p.monthlyIncome(inc);
        } catch (Exception ignored) {}
        p.firstRunDone();
        Notifier.ensureChannel(this);
        if (p.remindersOn()) Notifier.scheduleDaily(this);
        startActivity(new Intent(this, p.lockEnabled() ? LockActivity.class : MainActivity.class));
        finish();
    }
}
