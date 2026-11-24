package dev.lasq.fawri.platform

import android.app.KeyguardManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.text.InputType
import dev.lasq.fawri.*
import dev.lasq.fawri.core.*
import java.io.File
import java.security.MessageDigest

inline fun <reified T : Any> Context.system(): T = requireNotNull(getSystemService(T::class.java))

object Checks {
    fun unlocked(c: Context) =
        c.system<PowerManager>().isInteractive && !c.system<KeyguardManager>().isKeyguardLocked

    fun keyboard(c: Context) =
        ComponentName.unflattenFromString(
            Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "",
        ) == ComponentName(c, PasteBoard::class.java)

    fun password(input: Int): Boolean =
        when (input and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER ->
                input and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            InputType.TYPE_CLASS_TEXT ->
                input and InputType.TYPE_MASK_VARIATION in
                    setOf(
                        InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    )
            else -> false
        }
}

object Integrity {
    @Suppress("DEPRECATION")
    fun inspect(c: Context): Pair<Boolean, Boolean> {
        val valid =
            BuildConfig.DEBUG ||
                runCatching {
                        val certs =
                            if (Build.VERSION.SDK_INT >= 28)
                                c.packageManager
                                    .getPackageInfo(
                                        c.packageName,
                                        PackageManager.GET_SIGNING_CERTIFICATES,
                                    )
                                    .signingInfo
                                    ?.apkContentsSigners
                            else
                                c.packageManager
                                    .getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES)
                                    .signatures
                        certs?.singleOrNull()?.let {
                            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(
                                "",
                            ) { b ->
                                "%02x".format(b)
                            }
                        } == BuildConfig.SIGNER_SHA256
                    }
                    .getOrDefault(false)
        val root =
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/adb/magisk").any {
                File(it).exists()
            }
        return valid to root
    }
}
