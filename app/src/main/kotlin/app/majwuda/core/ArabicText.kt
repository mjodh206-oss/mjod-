package app.majwuda.core

/**
 * Arabic-aware text utilities used for fuzzy matching of contacts and apps, spoken yes/no
 * detection and wake-phrase spotting. Pure Kotlin, no Android dependencies.
 */
object ArabicText {

    private const val ALEF = '\u0627'
    private const val YEH = '\u064A'
    private const val HEH = '\u0647'
    private const val WAW = '\u0648'

    /**
     * Canonical form for comparisons: lower-cased, diacritics and tatweel removed, alef/yeh/teh
     * marbuta variants unified, Arabic-Indic digits converted, punctuation collapsed to spaces.
     */
    fun normalize(input: String): String {
        val out = StringBuilder(input.length)
        var lastWasSpace = true
        for (raw in input) {
            val ch: Char = when (raw) {
                '\u0623', '\u0625', '\u0622', '\u0671' -> ALEF          // أ إ آ ٱ
                '\u0649' -> YEH                                         // ى
                '\u0629' -> HEH                                         // ة
                '\u0624' -> WAW                                         // ؤ
                '\u0626' -> YEH                                         // ئ
                in '\u0660'..'\u0669' -> '0' + (raw - '\u0660')        // ٠-٩
                in '\u06F0'..'\u06F9' -> '0' + (raw - '\u06F0')        // ۰-۹
                else -> raw
            }
            when {
                isDiacritic(ch) -> Unit
                ch.isLetterOrDigit() -> {
                    out.append(ch.lowercaseChar())
                    lastWasSpace = false
                }
                else -> if (!lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return out.toString().trim()
    }

    private fun isDiacritic(ch: Char): Boolean =
        ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' ||
            ch in '\u0610'..'\u061A' || ch in '\u06D6'..'\u06ED'

    /** Removes the Arabic definite article so that "الوالد" matches "والد". */
    private fun stripArticle(token: String): String =
        if (token.length > 3 && token.startsWith("\u0627\u0644")) token.substring(2) else token

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** 0..1 similarity of two already-normalized strings. */
    fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    /**
     * How well [candidate] (a contact or app name) answers the spoken [query]; 0 = unrelated,
     * 1 = exact. Tolerates spelling variants, missing articles and partial names.
     */
    fun matchScore(query: String, candidate: String): Double {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val qTokens = q.split(' ').map(::stripArticle)
        val cTokens = c.split(' ').map(::stripArticle)
        if (qTokens == cTokens) return 0.98
        if (c.startsWith("$q ") || q.startsWith("$c ")) return 0.93
        if (qTokens.all { qt -> cTokens.any { it == qt } }) return 0.9
        if (c.contains(q) && q.length >= 3) return 0.84
        val fuzzyAll = qTokens.all { qt ->
            cTokens.any { ct ->
                (qt.length >= 3 && ct.startsWith(qt)) || similarity(ct, qt) >= 0.78
            }
        }
        if (fuzzyAll) return 0.8
        val whole = similarity(q, c)
        return if (whole >= 0.7) whole * 0.85 else whole * 0.5
    }

    private val AFFIRMATIVE = setOf(
        "نعم", "اي", "ايه", "ايوه", "ايوا", "اه", "اجل", "اكيد", "طبعا", "تمام", "ماشي", "موافق",
        "ارسل", "ارسلها", "ارسله", "ابعت", "ابعتها", "ابعته", "ابعث", "ابعثها", "اتصل", "نفذ", "نفذها",
        "يلا", "اوكي", "اوك", "صح", "مزبوط", "مضبوط", "بلي", "تفضل", "افعل", "اعمل", "اعملها", "كمل",
        "اكمل", "متاكد", "سوي", "سويها", "زين", "طيب", "حاضر", "احذف", "احذفها", "امسح", "امسحها",
        "ok", "okay", "yes", "yeah", "yep", "sure", "send", "confirm", "go",
    )

    private val NEGATIVE = setOf(
        "لا", "لاء", "لاا", "كلا", "الغ", "الغاء", "الغي", "الغيها", "الغه", "وقف", "توقف", "كنسل",
        "بلاش", "انس", "انسي", "تراجع", "بطلت", "مش", "مو", "ليس",
        "no", "nope", "cancel", "stop", "don", "dont",
    )

    private val NEGATIVE_PHRASES = listOf("ما بدي", "ما بدنا", "ما ابي", "ما ابغي", "ما اريد", "مو لازم")

    /** true = spoken confirmation, false = refusal, null = neither could be recognised. */
    fun confirmation(spoken: String): Boolean? {
        val normalized = normalize(spoken)
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        if (tokens.any { it in NEGATIVE }) return false
        if (NEGATIVE_PHRASES.any { normalized.contains(it) }) return false
        if (tokens.any { it in AFFIRMATIVE }) return true
        return null
    }

    data class WakeMatch(val remainder: String)

    private val WAKE_PREFIXES = setOf("يا", "هاي", "هي", "مرحبا", "اهلا", "hey", "ok", "okay", "hi")

    /**
     * Looks for one of the assistant [names] near the start of [spoken]. Returns the rest of
     * the utterance (the command, possibly empty) when the wake phrase is present.
     */
    fun findWakePhrase(spoken: String, names: List<String>): WakeMatch? {
        val rawTokens = spoken.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return null
        val normTokens = rawTokens.map(::normalize)
        val normNames = names.map(::normalize).filter { it.isNotEmpty() }
        if (normNames.isEmpty()) return null
        // The name must be one of the first three words ("يا مجودة ...", "مرحبا يا مجودة ...").
        val limit = minOf(normTokens.size, 3)
        for (i in 0 until limit) {
            val token = normTokens[i]
            if (token.isEmpty()) continue
            val hit = normNames.any { name -> token == name || (name.length >= 4 && similarity(token, name) >= 0.75) }
            if (!hit) continue
            val leadOk = (0 until i).all { normTokens[it].isEmpty() || normTokens[it] in WAKE_PREFIXES }
            if (!leadOk) continue
            return WakeMatch(rawTokens.drop(i + 1).joinToString(" ").trim())
        }
        return null
    }

    /** True when the text is predominantly written in Latin script. */
    fun isMostlyLatin(text: String): Boolean {
        var latin = 0
        var arabic = 0
        for (ch in text) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '\u00C0'..'\u024F' -> latin++
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> arabic++
            }
        }
        return latin >= 3 && latin > arabic * 2
    }

    /** Converts Western digits to Arabic-Indic digits for display. */
    fun toArabicDigits(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) out.append(if (ch in '0'..'9') '\u0660' + (ch - '0') else ch)
        return out.toString()
    }
}
