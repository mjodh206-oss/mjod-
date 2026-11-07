package com.amjad.mahloula.ui;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amjad.mahloula.R;
import com.amjad.mahloula.lock.Security;
import com.amjad.mahloula.ui.kit.PinPad;
import com.amjad.mahloula.ui.kit.UiKit;
import com.amjad.mahloula.util.Prefs;
import com.amjad.mahloula.util.Ui;

/** بوابة مَحلولة: بصمة الإصبع أولًا، والرمز احتياطًا إن سمح المستخدم. */
public class LockActivity extends BaseActivity {

    private CancellationSignal cancelSignal;
    private PinPad pinPad;
    private TextView status;
    private boolean finishing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs p = Prefs.get(this);
        boolean ar = !p.language().equals("en");
        darkBars(true);

        LinearLayout root = Ui.v(this);
        root.setBackground(Ui.gradient(this, new int[]{
                Ui.color(this, R.color.brand_900), Ui.color(this, R.color.brand_700)}, 0, 1));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 56), Ui.dp(this, 28), Ui.dp(this, 28));

        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getDrawable(R.drawable.logo_mark));
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));
        root.addView(logo);

        TextView title = Ui.txt(this, getString(R.string.app_name), 26, R.color.on_brand, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(this, 10);
        title.setLayoutParams(tlp);
        root.addView(title);

        status = Ui.txt(this, ar ? "افتح التطبيق ببصمة إصبعك" : "Unlock with your fingerprint",
                14, R.color.brand_200);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(this, 6);
        status.setLayoutParams(slp);
        root.addView(status);

        // زر البصمة
        FrameLayout bioBtn = new FrameLayout(this);
        bioBtn.setBackground(Ui.ripple(this,
                Ui.roundStroke(this, Color.argb(30, 255, 255, 255), Color.argb(90, 255, 255, 255), 1.5f, 32),
                Color.argb(40, 255, 255, 255)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92));
        blp.topMargin = Ui.dp(this, 26);
        bioBtn.setLayoutParams(blp);
        ImageView fp = Ui.iconSized(this, R.drawable.ic_fingerprint, Ui.color(this, R.color.brand_200), 46);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        flp.gravity = Gravity.CENTER;
        fp.setLayoutParams(flp);
        bioBtn.addView(fp);
        root.addView(bioBtn);
        bioBtn.setOnClickListener(v -> promptBiometric());

        // نبض حول زر البصمة
        bioBtn.post(() -> pulse(bioBtn));

        boolean canBio = Security.hasBiometric(this);
        boolean showPin = !p.biometricOnly() || !canBio;

        if (showPin) {
            TextView pinTitle = Ui.txt(this, p.hasPin()
                            ? (ar ? "أو أدخل رمزك المكوّن من 4 أرقام" : "or enter your 4-digit PIN")
                            : (ar ? "أدخل رمزك المكوّن من 4 أرقام" : "enter your 4-digit PIN"),
                    12.5f, R.color.brand_200);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.topMargin = Ui.dp(this, 24);
            pinTitle.setLayoutParams(plp);
            root.addView(pinTitle);

            pinPad = new PinPad(this, 4);
            root.addView(pinPad);
            pinPad.setListener(new PinPad.Listener() {
                @Override
                public void onComplete(String pin) {
                    if (Security.checkPin(LockActivity.this, pin)) succeed();
                    else {
                        status.setText(ar ? "رمز غير صحيح، حاول مجددًا" : "Wrong PIN, try again");
                        pinPad.reject();
                        Ui.tick(LockActivity.this);
                    }
                }

                @Override
                public void onChanged(int len) {
                }
            });
        } else {
            TextView only = Ui.txt(this, ar
                            ? "هذا التطبيق محمي بالبصمة فقط، حسب إعداداتك.\nإن لم تستجب المستشعر، سجّل بصمتك من إعدادات النظام."
                            : "This app is fingerprint-only, per your settings.\nIf the sensor doesn't respond, enroll a fingerprint in system settings.",
                    12.5f, R.color.brand_200);
            only.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
            olp.topMargin = Ui.dp(this, 28);
            only.setLayoutParams(olp);
            root.addView(only);
        }

        root.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView credit = Ui.txt(this, ar ? "تأليف وتطوير: أمجد حبيب" : "By Amjad Habib", 11.5f, R.color.brand_300);
        root.addView(credit);

        setContentView(root);

        if (!canBio && !p.hasPin()) {
            // لا بصمة ولا رمز مضبوطان: لا معنى للقفل
            succeed();
            return;
        }
        if (canBio) bioBtn.postDelayed(this::promptBiometric, 450);
        else if (!showPin) {
            systemCredentialFallback();
        }
    }

    private void pulse(android.view.View v) {
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(900)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(900)
                        .withEndAction(() -> {
                            if (!isFinishing()) pulse(v);
                        }).start()).start();
    }

    private void promptBiometric() {
        if (Build.VERSION.SDK_INT < 28) {
            systemCredentialFallback();
            return;
        }
        boolean ar = !Prefs.get(this).language().equals("en");
        cancelSignal = new CancellationSignal();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle(ar ? "فتح مَحلولة" : "Unlock Mahloula")
                .setSubtitle(ar ? "ضع إصبعك على المستشعر" : "Touch the sensor")
                .setNegativeButton(ar ? "إلغاء" : "Cancel", getMainExecutor(), (d, w) -> { })
                .build();
        prompt.authenticate(cancelSignal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                succeed();
            }

            @Override
            public void onAuthenticationError(int code, CharSequence msg) {
                if (code == 7 || code == 9) { // ERROR_LOCKOUT / ERROR_LOCKOUT_PERMANENT
                    status.setText(ar ? "المستشعر مقفل مؤقتًا من النظام" : "Sensor temporarily locked by system");
                }
            }

            @Override
            public void onAuthenticationFailed() {
                status.setText(ar ? "لم تُطابق البصمة، حاول مجددًا" : "Fingerprint not recognized");
                Ui.tick(LockActivity.this);
            }
        });
    }

    private static final int REQ_SYSTEM_LOCK = 911;

    private void systemCredentialFallback() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardSecure()) {
            succeed();
            return;
        }
        boolean ar = !Prefs.get(this).language().equals("en");
        Intent i = km.createConfirmDeviceCredentialIntent(
                ar ? "فتح مَحلولة" : "Unlock Mahloula",
                ar ? "استخدم بصمتك أو قفل جهازك" : "Use fingerprint or device lock");
        startActivityForResult(i, REQ_SYSTEM_LOCK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SYSTEM_LOCK) {
            if (res == RESULT_OK) succeed();
        }
    }

    private void succeed() {
        if (finishing) return;
        finishing = true;
        Security.unlockSession();
        Ui.tick(this);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        // لا خروج من البوابة إلا بفتحها
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (cancelSignal != null) cancelSignal.cancel();
        super.onDestroy();
    }
}
