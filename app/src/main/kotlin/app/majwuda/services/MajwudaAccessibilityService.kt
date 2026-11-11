package app.majwuda.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in screen understanding & control: lets Majwuda read what is on screen, tap elements,
 * type into focused fields and take screenshots — only when the user turns it on in Settings
 * and only while acting on the user's explicit request.
 */
class MajwudaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridge.unregister(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.unregister(this)
        super.onDestroy()
    }
}
