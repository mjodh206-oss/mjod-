package dev.lasq.fawri.platform

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.*
import dev.lasq.fawri.app
import dev.lasq.fawri.core.*

data class Focus(val field: Field, val editor: Boolean)
/** No event text, node text, labels, selection, ancestors, or window-tree traversal. */
class PasteAccess : AccessibilityService() {
    companion object { var instance: PasteAccess? = null; private set }
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && app.active.value) Monitor.instance?.focusChanged()
    }
    @Suppress("DEPRECATION") fun <T> focused(block: (Focus, AccessibilityNodeInfo?) -> T): T? {
        if (!app.active.value || !Checks.unlocked(this)) return null
        val root = rootInActiveWindow ?: return null
        var node: AccessibilityNodeInfo? = null
        try {
            val pkg = root.packageName?.toString() ?: return null
            if (!Rules.permits(pkg,packageName,app.prefs.value) || root.isPassword || PasteBoard.privateEditor == pkg) return null
            node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node != null && (node.packageName?.toString()!=pkg || node.isPassword || Checks.password(node.inputType))) return null
            val editable = node != null && node.isFocused && node.isVisibleToUser && node.isEnabled && node.isEditable && node.actionList.any { it.id==AccessibilityNodeInfo.ACTION_PASTE }
            return block(Focus(Field(pkg,root.windowId,node?.viewIdResourceName,node?.hashCode() ?: 0),editable),node)
        } finally { if (node !== root) node?.recycle(); root.recycle() }
    }
    override fun onInterrupt() { Monitor.instance?.cancelTicket() }
    override fun onDestroy() { instance = null; Monitor.instance?.halt(false); super.onDestroy() }
}
