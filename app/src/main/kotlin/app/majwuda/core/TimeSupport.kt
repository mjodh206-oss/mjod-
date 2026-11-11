package app.majwuda.core

import java.text.Normalizer
import java.util.Locale
import java.util.TimeZone

/**
 * Time, date, clock-time and duration parsing shared by the tools that reason about "when".
 * Pure Kotlin and timezone-injectable so it can be unit tested.
 */
object TimeSupport {

    /** Lower-cased Arabic text folding hamza variants onto the bare letter. */
    fun fold(text: String): String {
        val lowered = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).lowercase(Locale("ar"))
        val sb = StringBuilder(lowered.length)
        for (ch in lowered) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                'ٔ' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }

    /** Arabic and English words for digits, used when numbers are spelled out. */
    private val WORD_NUMBERS: Map<String, Int> = mapOf(
        "واحد" to 1, "وحده" to 1, "واحدة" to 1, "حد" to 1, "one" to 1,
        "اثنين" to 2, "اتنين" to 2, "تنا" to 2, "ثنتين" to 2, "ثنتان" to 2, "two" to 2,
        "ثلاثه" to 3, "ثلاثة" to 3, "تلاتة" to 3, "ثلاث" to 3, "تلات" to 3, "ثلاثة" to 3, "three" to 3,
        "اربعه" to 4, "اربعة" to 4, "اربع" to 4, "أربعة" to 4, "four" to 4,
        "خمسه" to 5, "خمسة" to 5, "خمس" to 5, "five" to 5,
        "سته" to 6, "ستة" to 6, "ست" to 6, "ستة" to 6, "six" to 6,
        "سبعه" to 7, "سبعة" to 7, "سبع" to 7, "seven" to 7,
        "ثمانيه" to 8, "ثمانية" to 8, "تمانيه" to 8, "ثمان" to 8, "تمان" to 8, "eight" to 8,
        "تسعه" to 9, "تسعة" to 9, "تسع" to 9, "nine" to 9,
        "عشره" to 10, "عشرة" to 10, "عشر" to 10, "ten" to 10,
        "احد عشر" to 11, "احدى عشر" to 11, "احدعشر" to 11, "احدعشره" to 11,
        "اثنا عشر" to 12, "اثني عشر" to 12, "اتنعش" to 12, "اثنعشر" to 12,
        "ثلاثة عشر" to 13, "تلاتة عشر" to 13, "اربعة عشر" to 14, "خمسة عشر" to 15, "ستة عشر" to 16,
        "سبعة عشر" to 17, "ثمانية عشر" to 18, "تسعة عشر" to 19, "عشرين" to 20, "واحد وعشرين" to 21,
        "اتنين وعشرين" to 22, "اثنين وعشرين" to 22, "ثلاثة وعشرين" to 23,
    )

    private val DAYS_AR: Map<String, Int> = mapOf(
        "الاحد" to 7, "احد" to 7,
        "الاثنين" to 1, "اثنين" to 1, "اثنى" to 1, "اتنين" to 1,
        "الثلاثاء" to 2, "ثلاثاء" to 2,
        "الاربعاء" to 3, "الاربع" to 3, "اربعاء" to 3,
        "الخميس" to 4, "خميس" to 4,
        "الجمعة" to 5, "جمعه" to 5, "جمعة" to 5,
        "السبت" to 6, "سبت" to 6,
    )

    private val DAYS_EN: Map<String, Int> = mapOf(
        "sunday" to 1, "monday" to 2, "tuesday" to 3, "wednesday" to 4,
        "thursday" to 5, "friday" to 6, "saturday" to 7,
    )

    private val MONTHS_AR: Map<String, Int> = mapOf(
        "يناير" to 1, "كانون الثاني" to 1, "كانون ثاني" to 1,
        "فبراير" to 2, "شباط" to 2,
        "مارس" to 3, "اذار" to 3, "آذار" to 3,
        "ابريل" to 4, "نيسان" to 4,
        "مايو" to 5, "ايار" to 5, "أيار" to 5,
        "يونيو" to 6, "حزيران" to 6,
        "يوليو" to 7, "تموز" to 7,
        "اغسطس" to 8, "آب" to 8,
        "سبتمبر" to 9, "ايلول" to 9,
        "اكتوبر" to 10, "تشرين الاول" to 10, "تشرين اول" to 10,
        "نوفمبر" to 11, "تشرين الثاني" to 11, "تشرين ثاني" to 11,
        "ديسمبر" to 12, "كانون الاول" to 12, "كانون اول" to 12,
    )

    private val MONTHS_EN: Map<String, Int> = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )

    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Converts Arabic-Indic / Extended Arabic digits to ASCII. */
    fun asciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val a = ARABIC_DIGITS.indexOf(ch)
            val p = PERSIAN_DIGITS.indexOf(ch)
            when {
                a >= 0 -> sb.append('0' + a)
                p >= 0 -> sb.append('0' + p)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Sets [hour], [minute] and optionally [second] on [base]. */
    private fun at(base: java.time.LocalDateTime, hour: Int, minute: Int, second: Int = 0): java.time.LocalDateTime =
        base.withHour(hour).withMinute(minute).withSecond(second).withNano(0)

    /**
     * Parses a clock time found in free Arabic/English text, e.g. "الساعة 7 ونص الصبح",
     * "9:30 مساء", "سبع الصباح", "seven thirty pm". Returns an (hour, minute) pair in 24h time.
     */
    fun parseClockTime(rawText: String): Pair<Int, Int>? {
        var text = asciiDigits(fold(rawText))
        var hour: Int? = null
        var minute = 0

        // hh:mm or h:mm
        val clock = Regex("""(\d{1,2})[:.٫,\-](\d{2})""").find(text)
        if (clock != null) {
            hour = clock.groupValues[1].toInt()
            minute = clock.groupValues[2].toInt()
        } else {
            // "الساعة 7" / "الساعة ٧" / "7 الصبح" / "سبع الساعه"
            val hourWord = Regex("""(?:الساعه|الساعات|الساعة|ساعة|ساعه|pukul|saat|at)\s*([\p{L}]+|\d{1,2})""").find(text)
            val standalone = Regex("""(?:^|\s)(\d{1,2})(?=\s|$)""").find(text)
            hour = when {
                hourWord != null -> wordToNumber(hourWord.groupValues[1]) ?: hourWord.groupValues[1].toIntOrNull()
                standalone != null -> standalone.groupValues[1].toIntOrNull()
                else -> null
            }
            when {
                text.contains("و ربع") || text.contains("وربع") || text.contains("والربع") || text.contains(" و ربع ") -> minute = 15
                text.contains("و نص") || text.contains("ونص") || text.contains("و نصف") || text.contains("ونصف") || text.contains("والنصف") -> minute = 30
                text.contains("و ثلث") || text.contains("وثلث") || text.contains("والثلث") -> minute = 20
                text.contains("عشره دقائق") || text.contains("عشر دقائق") || text.contains("و عشر") -> minute = 10
                text.contains("خمس دقائق") || text.contains("و خمس") -> minute = 5
                text.contains("ربع الا") || text.contains("الا ربع") || text.contains("الا ربع") -> {
                    hour = hour?.let { (it + 23) % 24 }; minute = 45
                }
                text.contains("نص الا") || text.contains("الا نص") || text.contains("الا نصف") -> {
                    hour = hour?.let { (it + 23) % 24 }; minute = 30
                }
                text.contains("ثلث الا") || text.contains("الا ثلث") -> {
                    hour = hour?.let { (it + 23) % 24 }; minute = 40
                }
                text.contains("خمس الا") || text.contains("الا خمس") -> {
                    hour = hour?.let { (it + 23) % 24 }; minute = 55
                }
                text.contains("الا عشر") -> {
                    hour = hour?.let { (it + 23) % 24 }; minute = 50
                }
                else -> {
                    val tail = Regex("""(\d{1,2})\s*(?:دقيقه|دقيقة|دقائق|دقيقه|minutes|mins|min)""").find(text)
                    if (tail != null) minute = tail.groupValues[1].toIntOrNull() ?: 0
                }
            }
        }
        if (hour == null) {
            // bare word hour: "سبع الصباح"
            val bare = Regex("""^(\d{1,2}|[\p{L}]+)\s*(صباحا|صباح|صبح|صباحا|فجرا|فجر|طهر|ظهرا|ظهر|عصرا|عصر|مساء|مساء|ليلا|ليل|morning|am|pm|evening|night)$""").find(text)
            hour = bare?.let { wordToNumber(it.groupValues[1]) ?: it.groupValues[1].toIntOrNull() }
        }
        hour = hour ?: return null
        if (minute !in 0..59 || hour !in 0..23) return null

        val morning = text.contains(Regex("""صباح|صبح|فجر|morning|\bam\b"""))
        val noon = text.contains(Regex("""ظهر|ظهرا|طهر|noon|midday"""))
        val afternoon = text.contains(Regex("""عصر"""))
        val evening = text.contains(Regex("""مساء|عصر|evening|\bpm\b"""))
        val night = text.contains(Regex("""ليل"""))
        when {
            morning -> Unit
            night -> if (hour == 12) hour = 0 else if (hour in 1..11) hour += 12
            noon -> if (hour < 12) hour += 12
            afternoon -> if (hour < 12) hour += 12
            evening -> if (hour < 12) hour += 12
        }
        if (morning && hour == 12) hour = 0
        return hour to minute
    }

    fun wordToNumber(word: String): Int? {
        val clean = fold(word)
        WORD_NUMBERS[clean]?.let { return it }
        clean.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Resolves a natural-language date phrase ("today", "غدا", "الثلاثاء الجاي",
     * "٣ مارس") to a local date. Returns null when nothing date-like is present.
     */
    fun parseDatePhrase(rawText: String, now: java.time.LocalDateTime): java.time.LocalDate? {
        val text = asciiDigits(fold(rawText))
        val today = now.toLocalDate()
        when {
            text.contains("اليوم") || Regex("""\btoday\b""").containsMatchIn(text) -> return today
            text.contains("بكره") || text.contains("بكرة") || text.contains("غدا") || text.contains("غداً") || Regex("""\btomorrow\b""").containsMatchIn(text) -> return today.plusDays(1)
            text.contains("بعد بكره") || text.contains("بعد بكرة") || text.contains("بعد غدا") || Regex("""\bday after tomorrow\b""").containsMatchIn(text) -> return today.plusDays(2)
            text.contains("امس") || text.contains("امبارح") || Regex("""\byesterday\b""").containsMatchIn(text) -> return today.minusDays(1)
        }
        Regex("""بعد\s+(\d{1,2})\s*(يوم|ايام|أيام)""").find(text)?.let {
            return today.plusDays(it.groupValues[1].toLong())
        }
        Regex("""after\s+(\d{1,2})\s+days?""").find(text)?.let {
            return today.plusDays(it.groupValues[1].toLong())
        }
        // Day name in Arabic or English ("يوم الثلاثاء", "الثلاثاء", "next friday", "الجمعة الجايه")
        val nextWeek = text.contains("الجاي") || text.contains("القادم") || text.contains("القادمه") || text.contains("الجايه") || text.contains("القادمة") || text.contains("next")
        for ((name, target) in DAYS_AR + DAYS_EN) {
            if (Regex("(^|\\s)" + Regex.escape(name) + "(\\s|$)").containsMatchIn(text)) {
                var date = today
                var delta = ((target - today.dayOfWeek.value) + 7) % 7
                if (delta == 0) {
                    if (nextWeek) delta = 7 else return today
                } else if (nextWeek && delta < 7 && text.contains("next")) {
                    // "next Friday" — if it is this week later, keep delta; if the phrase means the same week, leave it
                }
                return today.plusDays(delta.toLong())
            }
        }
        // "١٢ مارس" / "12 march" / "مارس ١٢"
        for ((name, month) in MONTHS_AR + MONTHS_EN) {
            val esc = Regex.escape(name)
            Regex("""(\d{1,2})(?:st|nd|rd|th)?\s*(?:/|-|من)?\s*(?:من|of)?\s*($esc)""").find(text)?.let {
                val day = it.groupValues[1].toInt()
                val candidate = safeDate(today.year, month, day)
                return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
            Regex("""($esc)\s*(\d{1,2})(?:st|nd|rd|th)?""").find(text)?.let {
                val day = it.groupValues[2].toInt()
                val candidate = safeDate(today.year, month, day)
                return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }
        // dd/mm/yyyy or yyyy-mm-dd
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(text)?.let {
            return safeDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})""").find(text)?.let {
            val year = it.groupValues[3].toInt().let { y -> if (y < 100) 2000 + y else y }
            return safeDate(year, it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        Regex("""(\d{1,2})[/-](\d{1,2})(?![/-]\d)""").find(text)?.let {
            val candidate = safeDate(today.year, it.groupValues[2].toInt(), it.groupValues[1].toInt())
            return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): java.time.LocalDate =
        java.time.LocalDate.of(year, month.coerceIn(1, 12), day.coerceIn(1, 31).coerceAtMost(java.time.LocalDate.of(year, month.coerceIn(1, 12), 1).lengthOfMonth()))

    /**
     * Parses text such as "بكره الساعة 7 الصبح" into a [java.time.LocalDateTime]. Falls back to
     * [defaultHour]/[defaultMinute] on the resolved day when only a time or only a date is found.
     */
    fun parseDateTimePhrase(
        rawText: String,
        now: java.time.LocalDateTime,
        defaultHour: Int = 9,
        defaultMinute: Int = 0,
    ): java.time.LocalDateTime? {
        val text = asciiDigits(fold(rawText))
        val explicitDate = parseDatePhrase(rawText, now)
        val clock = parseClockTime(rawText)
        val relative = Regex("""بعد\s*(\d+)\s*(ساعه|ساعة|ساعات|hour|hours|hrs)""").find(text)

        if (relative != null) {
            return now.plusHours(relative.groupValues[1].toLong())
        }
        if (explicitDate != null) {
            val (h, m) = clock ?: (defaultHour to defaultMinute)
            return at(explicitDate.atTime(h, m), h, m)
        }
        if (text.contains("بكره") || text.contains("غدا")) {
            val (h, m) = clock ?: (defaultHour to defaultMinute)
            return at(now.toLocalDate().plusDays(1).atTime(h, m), h, m)
        }
        if (clock != null) {
            val (h, m) = clock
            var candidate = at(now.toLocalDate().atTime(h, m), h, m)
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
            return candidate
        }
        return null
    }

    /** Parses durations like "10 دقائق", "ساعتين", "90 minutes" / "ساعتين". */
    fun parseDuration(rawText: String): java.time.Duration? {
        val text = asciiDigits(fold(rawText))
        var total = java.time.Duration.ZERO
        Regex("""(\d{1,4})\s*(ساعه|ساعة|ساعات|hours?|hrs?)""").find(text)?.let {
            total = total.plusHours(it.groupValues[1].toLong())
        }
        Regex("""(\d{1,4})\s*(دقيقه|دقيقة|دقائق|mins?|minutes?)""").find(text)?.let {
            total = total.plusMinutes(it.groupValues[1].toLong())
        }
        Regex("""(\d{1,4})\s*(ثواني|ثانيه|ثانية|seconds?|secs?)""").find(text)?.let {
            total = total.plusSeconds(it.groupValues[1].toLong())
        }
        Regex("""(\d{1,4})\s*(ايام|أيام|يوم|days?)""").find(text)?.let {
            total = total.plusDays(it.groupValues[1].toLong())
        }
        if (total.isZero) {
            when {
                text.contains("ساعتين") -> total = total.plusHours(2)
                text.contains("ساعه") || text.contains("ساعة") -> total = total.plusHours(1)
                text.contains("نص ساعه") -> total = total.plusMinutes(30)
                text.contains("ربع ساعه") -> total = total.plusMinutes(15)
                text.contains("نصف ساعه") -> total = total.plusMinutes(30)
                text.contains("دقيقتين") -> total = total.plusMinutes(2)
                text.contains("عشر دقايق") -> total = total.plusMinutes(10)
            }
        }
        return total.takeUnless { it.isZero }
    }

    fun defaultTimeZone(): TimeZone = TimeZone.getDefault()
}
