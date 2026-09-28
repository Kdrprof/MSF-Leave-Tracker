package com.msf.jordan.leavetracker.logic

import java.time.LocalDate

/** What could be read from a photographed leave request form. Every field may be missing. */
data class ParsedForm(
    val type: LeaveType?,
    val start: LocalDate?,
    val end: LocalDate?,
    val daysX100: Int?,
) {
    val foundCount: Int get() = listOf(type, start, daysX100).count { it != null }
}

/**
 * Turns the text recognised on the paper «Leave Request Form» into leave fields.
 * Works with handwriting mistakes: d/m/yy dates, «1 day», «½ day», «Sick», «Holiday»…
 * The result is always shown to the user for review before saving.
 */
object FormParser {

    private val typeWords: List<Pair<String, LeaveType>> = listOf(
        "holiday" to LeaveType.HOLIDAY, "annual" to LeaveType.HOLIDAY, "vacation" to LeaveType.HOLIDAY,
        "paid" to LeaveType.HOLIDAY,
        "sick" to LeaveType.SICK,
        "personal" to LeaveType.PERSONAL,
        "training" to LeaveType.TRAINING,
        "unpaid" to LeaveType.UNPAID,
        "compassionate" to LeaveType.COMPASSIONATE, "compassion" to LeaveType.COMPASSIONATE,
        "سنوية" to LeaveType.HOLIDAY, "سنويه" to LeaveType.HOLIDAY,
        "مرضية" to LeaveType.SICK, "مرضيه" to LeaveType.SICK,
        "شخصية" to LeaveType.PERSONAL, "شخصيه" to LeaveType.PERSONAL,
        "تدريب" to LeaveType.TRAINING,
        "أجر" to LeaveType.UNPAID, "اجر" to LeaveType.UNPAID,
        "إنسانية" to LeaveType.COMPASSIONATE, "انسانية" to LeaveType.COMPASSIONATE, "مواساة" to LeaveType.COMPASSIONATE,
    )

    private val dateRe = Regex("(?<!\\d)(\\d{1,2})\\s*[/.\\-]\\s*(\\d{1,2})\\s*[/.\\-]\\s*(\\d{2,4})(?!\\d)")
    private val rangeRe = Regex("(?<![\\d/.])(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{2,4})(?!\\d)")
    private val halfRe = Regex("(?i)(half\\s*(a\\s*)?day|1\\s*/\\s*2\\s*day|½|0?[.,]5\\s*day|نصف\\s*يوم)")
    private val daysRe = Regex("(?i)(?<![\\d/])(\\d{1,2}(?:[.,]5)?)\\s*(days?|dys?|d\\b|يوم|ايام|أيام)")

    fun parse(text: String, today: LocalDate): ParsedForm {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedForm(
            type = findType(lines),
            start = null, end = null, daysX100 = null,
        ).let { base ->
            val (s, e) = findDates(lines, today)
            base.copy(start = s, end = e, daysX100 = findDays(lines))
        }
    }

    // ---------- type ----------

    private fun findType(lines: List<String>): LeaveType? {
        for (line in lines) {
            val low = line.lowercase()
            // Skip the printed labels that list every type or mention the word "sick" as instructions.
            if ("type of leave" in low || "reason" in low || "please" in low || "(holiday" in low) continue
            // Printed labels end with ":" or contain brackets; printed headings are ALL CAPS.
            if (line.any { it == ':' || it == '(' || it == ')' }) continue
            val letters = line.filter { it.isLetter() && it.code < 128 }
            if (letters.length > 6 && letters == letters.uppercase() && line.trim().contains(' ')) continue
            val found = wordsOf(line).mapNotNull { matchType(it) }.distinct()
            if (found.size == 1) return found.first()
        }
        return null
    }

    private fun wordsOf(line: String): List<String> =
        line.split(Regex("[^\\p{L}\\d]+"))
            .filter { it.length >= 3 }
            .map { w ->
                w.lowercase().map { c ->
                    when (c) { '5' -> 's'; '0' -> 'o'; '1' -> 'l'; else -> c }
                }.joinToString("")
            }

    private fun matchType(word: String): LeaveType? {
        typeWords.firstOrNull { it.first == word }?.let { return it.second }
        if (word.length >= 4) {
            typeWords.firstOrNull { (k, _) -> k.length >= 4 && levenshtein(k, word) <= if (k.length >= 7) 2 else 1 }
                ?.let { return it.second }
        }
        return null
    }

    // ---------- dates ----------

    private data class Found(val start: LocalDate, val end: LocalDate, val isRequest: Boolean)

    private fun findDates(lines: List<String>, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        val found = mutableListOf<Found>()
        for (raw in lines) {
            val line = fixDigits(raw)
            val isRequest = "request" in raw.lowercase() || "طلب" in raw
            var rest = line
            rangeRe.findAll(line).forEach { m ->
                val (d1, d2, mo, y) = m.destructured
                val a = makeDate(d1, mo, y, today)
                val b = makeDate(d2, mo, y, today)
                if (a != null && b != null && !b.isBefore(a)) {
                    found += Found(a, b, isRequest)
                    rest = rest.replace(m.value, " ")
                }
            }
            dateRe.findAll(rest).forEach { m ->
                val (d, mo, y) = m.destructured
                makeDate(d, mo, y, today)?.let { found += Found(it, it, isRequest) }
            }
        }
        val leave = found.filter { !it.isRequest }.ifEmpty { found }
        if (leave.isEmpty()) return null to null
        leave.firstOrNull { it.start != it.end }?.let { return it.start to it.end }
        val first = leave[0].start
        val second = leave.getOrNull(1)?.start
        // Two different dates close together → From / To. Otherwise only the first is used.
        return if (second != null && second.isAfter(first) && Rules.calendarDays(first, second) <= 31 && leave.size <= 3 &&
            (leave.size == 2 || leave[2].start == second || leave[2].start == first)
        ) first to second else first to first
    }

    private fun fixDigits(s: String): String =
        Rules.normalizeDigits(s)
            .replace(Regex("(?<=[\\d/.\\-])[Oo]|[Oo](?=[\\d/])"), "0")
            .replace(Regex("(?<=[\\d/.\\-])[Il|]|[Il|](?=[\\d/])"), "1")

    private fun makeDate(d: String, m: String, y: String, today: LocalDate): LocalDate? {
        val year = y.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: return null
        if (year < today.year - 2 || year > today.year + 1) return null
        return try {
            LocalDate.of(year, m.toInt(), d.toInt())
        } catch (e: Exception) {
            null
        }
    }

    // ---------- days ----------

    private fun findDays(lines: List<String>): Int? {
        for (raw in lines) {
            val line = fixDigits(raw).replace(Regex("(?i)(^|\\s)[Il|](\\s*days?\\b)"), "$1" + "1$2")
            if (halfRe.containsMatchIn(line)) return 50
            val m = daysRe.find(line) ?: continue
            val v = Rules.parseDaysX100(m.groupValues[1]) ?: continue
            if (v in 50..9900 && v % 50 == 0) return v
        }
        return null
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
