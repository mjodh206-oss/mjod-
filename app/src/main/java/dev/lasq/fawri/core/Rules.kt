package dev.lasq.fawri.core

import java.nio.ByteBuffer
import java.security.MessageDigest

const val CREDIT = "تنفيذ وتطوير: أمجد حبيب"
// Below Android's binder and SQLite cursor-window limits. Never truncate a user's text.
const val MAX_TEXT = 192 * 1024

/** A copy event, not a content-based history item: copying identical text again is intentional. */
data class CopyEvent(val stamp: Long, val parts: List<String>) {
    init {
        require(
            stamp > 0 &&
                parts.isNotEmpty() &&
                parts.all { it.isNotEmpty() } &&
                parts.sumOf { it.length.toLong() } <= MAX_TEXT,
        )
    }

    fun fingerprint(): String {
        val hash = MessageDigest.getInstance("SHA-256")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            hash.update(bytes)
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Additional protection only when Android provides editor metadata to our selected keyboard. */
fun confidentialPackage(name: String?): Boolean {
    val pkg = name.orEmpty().lowercase(java.util.Locale.ROOT)
    return listOf(
            "bank",
            "wallet",
            "password",
            "passwd",
            "keypass",
            "keepass",
            "bitwarden",
            "lastpass",
            "agilebits",
            "authenticator",
            "authy",
            "dashlane",
            "enpass",
            "paypal",
            "revolut",
            "proton.pass",
            "nordpass",
        )
        .any { it in pkg }
}
