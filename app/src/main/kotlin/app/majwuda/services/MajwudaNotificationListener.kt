package app.majwuda.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.majwuda.core.NLog

/**
 * Opt-in notification access: lets مجودة read recent notifications aloud and reply to messages
 * (through the owning app) when you ask. Nothing is stored on disk; notifications live in a
 * small in-memory ring buffer only.
 */
class MajwudaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        NotificationStore.publish(
            NotificationEntry(
                id = sbn.id,
                packageName = sbn.packageName,
                appLabel = label,
                title = title,
                text = text,
                postedAt = sbn.postTime,
                isClearable = sbn.isClearable,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationStore.remove(sbn.id, sbn.packageName)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NLog.i("Notif", "listener connected")
        try {
            activeNotifications?.forEach { sbn -> onNotificationPosted(sbn) }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationStore.removeAll()
    }
}
