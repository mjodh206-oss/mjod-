package app.majwuda.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Minimal, allocation-free device capability probing used by the assistant before it offers
 * tools to the model.
 */
class DeviceCapabilities(context: Context) {
    private val appContext = context.applicationContext

    fun hasFeature(name: String): Boolean =
        appContext.packageManager.hasSystemFeature(name)

    fun isPackageInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasTelephony(): Boolean = hasFeature(PackageManager.FEATURE_TELEPHONY)
    fun hasMicrophone(): Boolean = hasFeature(PackageManager.FEATURE_MICROPHONE)
    fun hasGps(): Boolean = hasFeature(PackageManager.FEATURE_LOCATION_GPS)
    fun hasFlashlight(): Boolean = hasFeature(PackageManager.FEATURE_CAMERA_FLASH)
    fun hasWifi(): Boolean = hasFeature(PackageManager.FEATURE_WIFI)

    companion object {
        fun osVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        fun deviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }
    }
}
