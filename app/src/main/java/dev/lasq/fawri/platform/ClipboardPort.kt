package dev.lasq.fawri.platform

import android.content.*
import android.os.PersistableBundle
import dev.lasq.fawri.app
import dev.lasq.fawri.core.*

object ClipboardPort {
    private fun own(c: Context) = c.packageName + ".ARCHIVE_RECALL"

    const val PRIVATE = "android.content.extra.IS_SENSITIVE"

    /** Snapshot only real text. Never coerce a content URI, follow an intent, or read editor text. */
    fun read(c: Context, privateEditor: Boolean): CopyEvent? {
        val cm = c.system<ClipboardManager>()
        val desc = cm.primaryClipDescription ?: return null
        val stamp = desc.timestamp
        if (stamp <= 0) return null
        if (privateEditor || desc.extras?.getBoolean(PRIVATE) == true) {
            c.app.prefs.privateStamp = stamp
            return null
        }
        fun eligible(d: ClipDescription) =
            d.hasMimeType("text/*") &&
                d.timestamp == stamp &&
                d.extras?.getBoolean(own(c)) != true &&
                d.extras?.getBoolean(PRIVATE) != true
        if (stamp == c.app.prefs.privateStamp || !eligible(desc)) return null
        val clip = cm.primaryClip ?: return null
        if (!eligible(clip.description)) return null
        val parts = ArrayList<String>()
        var length = 0L
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            if (item.uri != null || item.intent != null) continue
            val text = item.text ?: continue
            if (text.isEmpty()) continue
            length += text.length
            if (length > MAX_TEXT) throw TextTooLarge()
            parts += text.toString()
        }
        return if (parts.isEmpty()) null else CopyEvent(stamp, parts)
    }

    /** Only called by the explicit Copy button in the archive; never by the listener. */
    fun copy(c: Context, text: String) {
        val clip = ClipData.newPlainText("لصق فوري", text)
        clip.description.extras = PersistableBundle().apply { putBoolean(own(c), true) }
        c.system<ClipboardManager>().setPrimaryClip(clip)
    }

    class TextTooLarge : IllegalArgumentException()
}
