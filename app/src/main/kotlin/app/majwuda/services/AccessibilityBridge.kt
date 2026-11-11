package app.majwuda.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import app.majwuda.core.ArabicText
import app.majwuda.core.NLog
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Indirection between tools (plain objects) and the live accessibility service. The service
 * registers itself here on connect and clears itself on destroy. Methods return null/false and
 * never throw when the service is down.
 */
object AccessibilityBridge {

    @Volatile
    private var service: AccessibilityService? = null

    @Volatile
    var lastError: String? = null
        private set

    val isConnected: Boolean get() = service != null

    fun register(instance: AccessibilityService) {
        service = instance
        lastError = null
        NLog.i("A11y", "service connected")
    }

    fun unregister(instance: AccessibilityService) {
        if (service === instance) {
            service = null
            NLog.i("A11y", "service disconnected")
        }
    }

    private fun requireService(): AccessibilityService? = service

    fun globalBack(): Boolean = runCatching { requireService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true }.getOrDefault(false)
    fun globalHome(): Boolean = runCatching { requireService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true }.getOrDefault(false)
    fun globalRecents(): Boolean = runCatching { requireService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true }.getOrDefault(false)
    fun globalNotifications(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 27) requireService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true else false
    }.getOrDefault(false)
    fun globalQuickSettings(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 21) requireService()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) == true else false
    }.getOrDefault(false)

    /** Collects visible text from the foreground window. */
    fun readVisibleText(maxChars: Int = 2400): ScreenRead? {
        val svc = requireService() ?: return null
        val root = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return ScreenRead("", null, emptyList())
        val builder = StringBuilder()
        val nodes = ArrayList<String>()
        collect(root, builder, nodes, maxChars)
        val focused = try {
            svc.rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            null
        }
        return ScreenRead(builder.toString().trim(), focused, nodes)
    }

    data class ScreenRead(val text: String, val appPackage: String?, val lines: List<String>)

    private fun collect(node: AccessibilityNodeInfo, out: StringBuilder, lines: MutableList<String>, maxChars: Int) {
        if (out.length >= maxChars) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val interesting = text.ifEmpty { description }
        if (interesting.isNotBlank() && interesting.length <= 300) {
            out.append(interesting).append('\n')
            lines += interesting
        }
        for (i in 0 until node.childCount) {
            if (out.length >= maxChars) return
            val child = node.getChild(i) ?: continue
            try {
                collect(child, out, lines, maxChars)
            } finally {
                child.recycle()
            }
        }
    }

    /** Taps the (normalized) text that best matches [query]. Returns the label it tapped. */
    fun tapText(query: String): String? {
        val svc = requireService() ?: return null
        val normalizedQuery = ArabicText.normalize(query)
        if (normalizedQuery.isBlank()) return null
        val target = findBestMatch(svc.rootInActiveWindow, normalizedQuery) ?: return null
        try {
            var node: AccessibilityNodeInfo? = target
            while (node != null) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return target.text?.toString()?.trim() ?: target.contentDescription?.toString()?.trim() ?: query
                }
                node = node.parent
            }
            return null
        } finally {
            target.recycle()
        }
    }

    private fun findBestMatch(root: AccessibilityNodeInfo?, normalizedQuery: String): AccessibilityNodeInfo? {
        root ?: return null
        var best: Pair<AccessibilityNodeInfo, Double>? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }
            if (text.isNotBlank()) {
                val score = ArabicText.matchScore(normalizedQuery, text)
                if (score > 0.5 && (best == null || score > best!!.second)) {
                    best = node to score
                    if (score >= 0.98) break
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return best?.first
    }

    /** Scrolls the first scrollable container, or swipes the screen vertically. */
    fun scroll(direction: String): Boolean {
        val svc = requireService() ?: return false
        val root = svc.rootInActiveWindow ?: return false
        val target = findScrollable(root)
        if (target != null) {
            val action = if (direction == "down" || direction == "up_content") {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val ok = try {
                target.performAction(action)
            } catch (_: Exception) {
                false
            }
            target.recycle()
            if (ok) return true
        }
        return swipeVertical(svc, direction)
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
            if (!node.isScrollable) node.recycle()
        }
        return null
    }

    private fun swipeVertical(svc: AccessibilityService, direction: String): Boolean {
        return if (Build.VERSION.SDK_INT >= 24) {
            val display = svc.resources.displayMetrics
            val fromY: Float
            val toY: Float
            if (direction == "up" || direction == "down_content") {
                fromY = display.heightPixels * 0.72f
                toY = display.heightPixels * 0.28f
            } else {
                fromY = display.heightPixels * 0.28f
                toY = display.heightPixels * 0.72f
            }
            val path = android.graphics.Path().apply {
                moveTo(display.widthPixels / 2f, fromY)
                lineTo(display.widthPixels / 2f, toY)
            }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            svc.dispatchGesture(gesture, null, null)
        } else {
            false
        }
    }

    /** Types [text] into the currently focused editable field. */
    fun typeIntoFocused(text: String): Boolean {
        val svc = requireService() ?: return false
        val root = svc.rootInActiveWindow ?: return false
        val target = findFocusedEditable(root) ?: return false
        return try {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (_: Exception) {
            false
        } finally {
            target.recycle()
        }
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = ArrayDeque<AccessibilityNodeInfo>()
        candidates.add(root)
        var firstEditable: AccessibilityNodeInfo? = null
        while (candidates.isNotEmpty()) {
            val node = candidates.removeFirst()
            if (node.isFocused && node.isEditable) return node
            if (node.isEditable && firstEditable == null) firstEditable = node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { candidates.add(it) }
            }
        }
        return firstEditable
    }

    /** Captures a screenshot via the accessibility service (API 30+). */
    fun captureScreenshotTo(file: File): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val svc = requireService() ?: return false
        val future = CompletableFuture<Bitmap?>()
        try {
            svc.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                { runnable -> runnable.run() },
                object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            future.complete(copy)
                        } catch (t: Throwable) {
                            NLog.e("A11y", "screenshot decode failed", t)
                            future.complete(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        NLog.w("A11y", "screenshot failed with code $errorCode")
                        future.complete(null)
                    }
                },
            )
            val bitmap = future.get(6, TimeUnit.SECONDS) ?: return false
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            bitmap.recycle()
            return true
        } catch (t: Throwable) {
            NLog.e("A11y", "screenshot capture failed", t)
            return false
        }
    }
}
