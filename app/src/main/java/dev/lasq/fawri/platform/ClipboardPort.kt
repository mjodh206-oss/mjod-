package dev.lasq.fawri.platform

import android.content.*
import android.os.*
import dev.lasq.fawri.app
import dev.lasq.fawri.core.MAX_TEXT

object ClipboardPort {
    private const val OWN = "dev.lasq.fawri.LOCAL_COPY"
    private const val PRIVATE = "android.content.extra.IS_SENSITIVE"

    data class Sample(val stamp: Long, val text: String)

    private fun manager(c: Context) = c.system<ClipboardManager>()

    fun unchangedPublic(c: Context, stamp: Long): Boolean {
        val d = manager(c).primaryClipDescription ?: return false
        return d.timestamp == stamp &&
            d.extras?.getBoolean(PRIVATE) != true &&
            d.extras?.getBoolean(OWN) != true &&
            d.hasMimeType("text/*")
    }

    fun read(c: Context, ignoreOwn: Boolean): Sample? {
        val cm = manager(c)
        val desc = cm.primaryClipDescription ?: return null
        fun allowed(d: ClipDescription) =
            d.hasMimeType("text/*") &&
                d.extras?.getBoolean(PRIVATE) != true &&
                !(ignoreOwn && d.extras?.getBoolean(OWN) == true)
        if (!allowed(desc)) return null
        val clip = cm.primaryClip ?: return null
        if (
            clip.itemCount != 1 ||
                !allowed(clip.description) ||
                clip.description.timestamp != desc.timestamp
        )
            return null
        val item = clip.getItemAt(0)
        if (item.uri != null || item.intent != null) return null
        val text = item.text ?: return null
        if (text.length !in 1..MAX_TEXT || text.isBlank()) return null
        return Sample(desc.timestamp, text.toString())
    }

    fun put(c: Context, text: String): Long {
        require(text.length in 1..MAX_TEXT)
        val clip = ClipData.newPlainText("لصق فوري", text)
        clip.description.extras = PersistableBundle().apply { putBoolean(OWN, true) }
        val cm = manager(c)
        cm.setPrimaryClip(clip)
        val stamp = cm.primaryClipDescription?.timestamp ?: 0
        remember(c, stamp)
        return stamp
    }

    private fun meta(c: Context) = c.getSharedPreferences("expiry", Context.MODE_PRIVATE)

    fun remember(c: Context, stamp: Long) {
        if (stamp > 0) meta(c).edit().putLong("stamp", stamp).apply()
    }

    fun due(c: Context): Long? {
        val stamp = meta(c).getLong("stamp", 0)
        val sec = c.app.prefs.value.clearSeconds
        return if (sec > 0 && stamp > 0) stamp + sec * 1000L else null
    }

    fun forget(c: Context) {
        meta(c).edit().clear().apply()
    }

    fun expire(c: Context, foreground: Boolean = false, force: Boolean = false) {
        if (!force && (due(c) ?: return) > System.currentTimeMillis()) return
        if ((!foreground && !Checks.canObserve(c)) || !Checks.unlocked(c)) return
        val cm = manager(c)
        val stamp = meta(c).getLong("stamp", 0)
        if (stamp <= 0) return
        val current = cm.primaryClipDescription ?: return
        // Never erase a newer clipboard in order to expire an older one.
        if (current.timestamp == stamp) {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        forget(c)
    }
}
