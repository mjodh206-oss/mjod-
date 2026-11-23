package dev.lasq.fawri.core

import java.net.URI
import java.util.Locale

const val MAX_TEXT = 24_000
const val DAY = 86_400_000L
const val CREDIT = "تنفيذ وتطوير: أمجد حبيب"

enum class Mode(val label: String) {
    AUTO("تلقائي فوري"),
    CONFIRM("بتأكيد قبل اللصق"),
    ARCHIVE("إضافة للسجل فقط"),
}

enum class Kind(val label: String) {
    TEXT("نص"),
    LINK("رابط"),
    NUMBER("رقم"),
}

enum class Theme(val label: String) {
    SYSTEM("حسب النظام"),
    LIGHT("فاتح"),
    DARK("داكن"),
}

enum class Group(val label: String) {
    NONE("بلا مجموعة"),
    WORK("شغل"),
    PERSONAL("شخصي"),
}

data class Options(
    val intro: Boolean = false,
    val consent: Boolean = false,
    val mode: Mode = Mode.AUTO,
    val allowList: Boolean = true,
    val allowed: Set<String> = emptySet(),
    val denied: Set<String> = emptySet(),
    val types: Set<Kind> = Kind.entries.toSet(),
    val trim: Boolean = false,
    val tracking: Boolean = false,
    val delayMs: Long = 0,
    val days: Int = 7,
    val clearSeconds: Int = 0,
    val lock: Boolean = false,
    val theme: Theme = Theme.SYSTEM,
    val locale: String = "ar",
)

object Rules {
    // Android does not publish a complete list of financial or confidential apps. Default is opt-in.
    private val sensitiveNames =
        listOf(
            "bank",
            "wallet",
            "password",
            "passwd",
            "keypass",
            "keepass",
            "enpass",
            "safeincloud",
            "dashlane",
            "lastpass",
            "bitwarden",
            "onepassword",
            "agilebits",
            "authenticator",
            "authy",
            "proton.pass",
            "revolut",
            "transferwise",
            "paypal",
            "1money",
            "microsoft.credential",
            "nordpass",
            "gpay",
            "google.android.apps.nbu.paisa",
            "credit",
            "paytm",
            "keyguard",
            "systemui",
            "packageinstaller",
            "permissioncontroller",
        )

    fun restricted(pkg: String, own: String): Boolean =
        pkg.isBlank() ||
            pkg == own ||
            pkg == "android" ||
            pkg == "com.android.settings" ||
            sensitiveNames.any { it in pkg.lowercase(Locale.ROOT) }

    fun permits(pkg: String, own: String, o: Options) =
        !restricted(pkg, own) && (if (o.allowList) pkg in o.allowed else pkg !in o.denied)

    private fun link(s: String) = runCatching {
        val u = URI(s)
        u.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !u.host.isNullOrEmpty()
    }
        .getOrDefault(false)

    fun kind(s: String): Kind =
        when {
            link(s.trim()) -> Kind.LINK
            s.trim().isNotEmpty() && s.trim().all(Char::isDigit) -> Kind.NUMBER
            else -> Kind.TEXT
        }

    private val trackingKeys = setOf("fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid")

    private fun cleanUrl(raw: String): String {
        if (!link(raw)) return raw
        val beforeFragment = raw.substringBefore('#')
        val q = beforeFragment.indexOf('?')
        if (q < 0) return raw
        val query =
            beforeFragment
                .substring(q + 1)
                .split('&')
                .filterNot {
                    val key = it.substringBefore('=').lowercase(Locale.ROOT)
                    key.startsWith("utm_") || key in trackingKeys
                }
                .joinToString("&")
        return beforeFragment.substring(0, q) +
            (if (query.isEmpty()) "" else "?$query") +
            raw.removePrefix(beforeFragment)
    }

    fun prepare(raw: String, o: Options): String? {
        if (raw.isBlank() || raw.length > MAX_TEXT || '\u0000' in raw || kind(raw) !in o.types)
            return null
        // Preserve paragraphs. Do not rewrite escapes, fragments or the order of non-tracking
        // parameters.
        val s = if (o.trim) raw.trim().replace(Regex("[\\p{Zs}\\t]+"), " ") else raw
        return if (o.tracking && kind(s) == Kind.LINK) cleanUrl(s.trim()) else s
    }
}

data class Field(val pkg: String, val window: Int, val viewId: String?, val node: Int = 0) {
    // With an ambiguous source, never guess that another field in the same package is safe.
    fun distinctFrom(source: Field) =
        pkg != source.pkg || (viewId != null && source.viewId != null && viewId != source.viewId)
}

data class Ticket(
    val id: Long,
    val stamp: Long,
    val text: String,
    val source: Field,
    val deadline: Long,
)

/** Only the newest copy may be delivered. All calls are confined to the main thread. */
class DeliveryGate {
    var current: Ticket? = null
        private set

    private var sequence = 0L

    fun offer(stamp: Long, text: String, source: Field, now: Long): Ticket =
        Ticket(++sequence, stamp, text, source, now + 45_000L).also { current = it }

    fun eligible(id: Long, target: Field, stamp: Long, now: Long): Boolean =
        current?.let {
            it.id == id && it.stamp == stamp && now < it.deadline && target.distinctFrom(it.source)
        } == true

    fun take(id: Long, target: Field, stamp: Long, now: Long): Ticket? =
        if (eligible(id, target, stamp, now)) current.also { clear() } else null

    fun clear() {
        current = null
    }
}
