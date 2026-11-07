package com.amjad.mahloula.lock;

import android.content.Context;

import com.amjad.mahloula.util.Prefs;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** إدارة القفل: بصمة + رمز احتياطي مجزّأ بملح عشوائي. */
public final class Security {

    private Security() {}

    private static long sessionUntil = 0L;

    /** يجزّ الرمز بـ SHA-256 مع ملح. */
    public static String hash(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(pin.hashCode());
        }
    }

    public static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void setupPin(Context c, String pin) {
        Prefs p = Prefs.get(c);
        String salt = newSalt();
        p.pinSalt(salt);
        p.pinHash(hash(pin, salt));
    }

    public static boolean checkPin(Context c, String pin) {
        Prefs p = Prefs.get(c);
        String salt = p.pinSalt();
        String h = p.pinHash();
        return salt != null && h != null && h.equals(hash(pin, salt));
    }

    public static void unlockSession() {
        sessionUntil = System.currentTimeMillis() + 15000L; // سماحية تنقّل قصيرة
    }

    public static boolean sessionValid() {
        return System.currentTimeMillis() < sessionUntil;
    }

    public static void lockNow() {
        sessionUntil = 0L;
    }

    /** هل الجهاز يدعم البصمة فعليًا؟ */
    @SuppressWarnings("deprecation")
    public static boolean hasBiometric(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.hardware.biometrics.BiometricManager bm =
                        (android.hardware.biometrics.BiometricManager) c.getSystemService(Context.BIOMETRIC_SERVICE);
                if (bm == null) return false;
                int st = bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG);
                return st == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.hardware.fingerprint.FingerprintManager fm =
                        (android.hardware.fingerprint.FingerprintManager) c.getSystemService(Context.FINGERPRINT_SERVICE);
                return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
            }
            // أندرويد 7/7.1: قفل النظام الرسمي (بصمة/نمط) عبر Keyguard
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                android.app.KeyguardManager km =
                        (android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
                return km != null && km.isKeyguardSecure();
            }
        } catch (Exception ignored) {}
        return false;
    }
}
