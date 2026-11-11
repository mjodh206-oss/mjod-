package app.majwuda.tools

import android.content.Context
import app.majwuda.data.AppCatalog
import app.majwuda.data.CalendarRepository
import app.majwuda.data.ContactRepository
import app.majwuda.data.CurrencyClient
import app.majwuda.data.DeviceCapabilities
import app.majwuda.data.SettingsRepository
import app.majwuda.data.SmsReader
import app.majwuda.data.WeatherClient
import app.majwuda.data.db.MajwudaDatabase
import app.majwuda.services.ReminderScheduler

/** Everything a tool handler may touch. One instance is created per app process. */
class ToolContext(
    val context: Context,
    val db: MajwudaDatabase,
    val settings: SettingsRepository,
    val contacts: ContactRepository = ContactRepository(context),
    val apps: AppCatalog = AppCatalog(context),
    val sms: SmsReader = SmsReader(context),
    val calendar: CalendarRepository = CalendarRepository(context),
    val weather: WeatherClient = WeatherClient(),
    val currency: CurrencyClient = CurrencyClient(),
    val capabilities: DeviceCapabilities = DeviceCapabilities(context),
    val reminders: ReminderScheduler = ReminderScheduler(context, db),
)

/** Builds the live environment snapshot for the tool registry. */
fun environmentOf(context: Context, settings: app.majwuda.data.AssistantSettings, notificationAccess: Boolean, accessibilityOn: Boolean): ToolEnvironment =
    ToolEnvironment(
        hasContacts = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        canCall = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        canSendSms = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        canReadSms = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        canReadCalendar = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        canWriteCalendar = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        hasNotificationAccess = notificationAccess,
        hasAccessibility = accessibilityOn,
        canSetAlertPolicy = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.isNotificationPolicyAccessGranted == true,
        canWriteSettings = android.provider.Settings.System.canWrite(context),
        screenControlEnabled = settings.screenControlEnabled,
        notificationReadEnabled = settings.notificationReadEnabled,
        wakePhraseEnabled = settings.wakePhraseEnabled,
        selectedVoice = settings.voiceName,
        language = settings.language,
    )

/** True when مجودة is an enabled notification listener. */
fun notificationListenerEnabled(context: android.content.Context): Boolean {
    return try {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        flat.split(":").any { it.contains(context.packageName, ignoreCase = true) }
    } catch (_: Exception) {
        false
    }
}

/** True when مجودة's accessibility service is enabled. */
fun accessibilityServiceEnabled(context: android.content.Context): Boolean {
    return try {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        flat.split(":").any { it.contains(context.packageName, ignoreCase = true) }
    } catch (_: Exception) {
        false
    }
}
