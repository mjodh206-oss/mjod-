package dev.lasq.fawri.platform

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import dev.lasq.fawri.*
import dev.lasq.fawri.core.*
import java.io.File
import java.security.MessageDigest

object Checks {
    fun unlocked(c: Context) = c.getSystemService(PowerManager::class.java).isInteractive && !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    fun access(c: Context) = c.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo.serviceInfo.packageName == c.packageName && it.resolveInfo.serviceInfo.name == PasteAccess::class.java.name
    }
    fun keyboard(c: Context) = ComponentName.unflattenFromString(Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "") == ComponentName(c,PasteBoard::class.java)
    fun canObserve(c: Context) = Build.VERSION.SDK_INT < 29 || keyboard(c)
    fun notifications(c: Context): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(Alerts.RUN)?.importance != NotificationManager.IMPORTANCE_NONE &&
            (c.app.prefs.value.mode != Mode.CONFIRM || nm.getNotificationChannel(Alerts.ASK)?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    fun reason(c: Context): String? {
        val s = c.app.prefs.value
        return when {
            !c.app.integrity.first -> "توقيع النسخة غير معتمد؛ أوقفنا الخدمة لحماية الحافظة."
            !s.consent -> "اقرأ شرح الصلاحيات وامنح موافقتك أولاً."
            !notifications(c) -> "اسمح بإشعارات الخدمة حتى يبقى زر الإيقاف متاحاً."
            !access(c) -> "فعّل خدمة لصق فوري للتحقق من التطبيقات والحقول المسموحة."
            !canObserve(c) -> "يتطلب النسخ التلقائي على هذا الإصدار اختيار لوحة لصق فوري. السجل اليدوي لا يتطلب ذلك."
            s.allowList && s.allowed.isEmpty() -> "اختر تطبيقاً واحداً على الأقل ضمن التطبيقات المسموحة."
            !unlocked(c) -> "افتح قفل الجهاز لتشغيل الخدمة."
            else -> null
        }
    }
    fun password(input: Int): Boolean = when (input and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> input and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        InputType.TYPE_CLASS_TEXT -> input and InputType.TYPE_MASK_VARIATION in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        else -> false
    }
}
object Integrity {
    @Suppress("DEPRECATION") fun inspect(c: Context): Pair<Boolean, Boolean> {
        val valid = BuildConfig.DEBUG || runCatching {
            val certs = if (Build.VERSION.SDK_INT >= 28) c.packageManager.getPackageInfo(c.packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
                else c.packageManager.getPackageInfo(c.packageName,PackageManager.GET_SIGNATURES).signatures
            certs?.singleOrNull()?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } } == BuildConfig.SIGNER_SHA256
        }.getOrDefault(false)
        val root = listOf("/system/bin/su","/system/xbin/su","/sbin/su","/data/adb/magisk").any { File(it).exists() }
        return valid to root
    }
}
