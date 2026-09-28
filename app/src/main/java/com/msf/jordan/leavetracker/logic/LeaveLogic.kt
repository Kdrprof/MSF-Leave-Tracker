package com.msf.jordan.leavetracker.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
 * Pure business logic (no Android imports) so it can be unit-tested on the JVM.
 * All day amounts are Int hundredths (x100) to avoid floating-point drift:
 * 2.08 days = 208, half a day = 50.
 */

const val ACCRUAL_X100 = 208          // +2.08 days acquired on every payslip
const val MAX_NOTE_LENGTH = 200
const val MIN_BALANCE_X100 = -3000    // -30 days
const val MAX_BALANCE_X100 = 10000    // 100 days

enum class LeaveType(
    val key: String,
    val en: String,
    val ar: String,
    val colorHex: Long,
    /** Only Holiday (annual / paid leave) is deducted on the payslip. */
    val onPayslip: Boolean,
) {
    HOLIDAY("holiday", "Holiday", "إجازة سنوية", 0xFF10B981, true),
    SICK("sick", "Sick", "إجازة مرضية", 0xFFEF4444, false),
    PERSONAL("personal", "Personal", "إجازة شخصية", 0xFF3B82F6, false),
    TRAINING("training", "Training", "إجازة تدريب", 0xFF8B5CF6, false),
    UNPAID("unpaid", "Unpaid", "إجازة بدون أجر", 0xFFF97316, false),
    COMPASSIONATE("compassionate", "Compassionate", "إجازة إنسانية", 0xFF111827, false);

    /** Name in the current language. */
    val title: String get() = if (Tr.arabic) ar else en

    /** Both names in Arabic mode, e.g. «إجازة سنوية (Holiday)». */
    val label: String get() = if (Tr.arabic) "$ar ($en)" else en

    val hint: String
        get() = when (this) {
            HOLIDAY -> tr(
                "تُخصم من رصيدك السنوي وتظهر في سليب الراتب حسب قاعدة يوم 15.",
                "Deducted from your annual balance and shown on the payslip (15th cut-off rule).",
            )
            UNPAID -> tr(
                "تُحسب في مجموع إجازاتك ولا تُخصم من الرصيد السنوي، لكنها قد تُخصم من قيمة الراتب.",
                "Counted in your totals, not deducted from the annual balance (may reduce salary).",
            )
            SICK -> tr(
                "تُحسب في مجموع إجازاتك ولا تُخصم من الرصيد السنوي. احتفظ بالتقرير الطبي.",
                "Counted in your totals, not deducted from the annual balance. Keep the medical note.",
            )
            else -> tr(
                "تُحسب في مجموع إجازاتك ولا تظهر في سليب الراتب.",
                "Counted in your totals; not shown on the payslip.",
            )
        }

    companion object {
        fun fromKey(key: String?): LeaveType? = entries.firstOrNull { it.key == key }
    }
}

data class LeaveEntry(
    val id: String,
    val type: LeaveType,
    val start: LocalDate,
    val end: LocalDate,
    val daysX100: Int,
    val note: String,
    val createdAt: Long,
)

data class AppSettings(
    val name: String,
    /** «Remaining» on the payslip of [openingMonth] (= previous − accounted + 2.08). */
    val openingBalanceX100: Int,
    val openingMonth: YearMonth,
    val weekend: Set<DayOfWeek>,
    /** «Previous balance» printed on that payslip (null for data saved by older versions). */
    val slipPreviousX100: Int? = null,
    /** «Accounted this month» printed on that payslip. */
    val slipAccountedX100: Int? = null,
) {
    /** True when the reference payslip was entered box by box (current setup screen). */
    val hasSlipDetails: Boolean get() = slipPreviousX100 != null && slipAccountedX100 != null
}

data class AppData(
    val settings: AppSettings?,
    val entries: List<LeaveEntry>,
) {
    companion object {
        val EMPTY = AppData(null, emptyList())
    }
}

data class LeaveDraft(
    val editingId: String?,
    val type: LeaveType,
    val start: LocalDate,
    val end: LocalDate,
    /** null = the typed number could not be read. */
    val daysX100: Int?,
    val note: String,
)

enum class IssueLevel { ERROR, WARNING, INFO }

data class Issue(val level: IssueLevel, val text: String)

data class ValidationResult(val issues: List<Issue>) {
    val hasErrors: Boolean get() = issues.any { it.level == IssueLevel.ERROR }
    val warnings: List<Issue> get() = issues.filter { it.level == IssueLevel.WARNING }
}

/** One payslip, exactly like the «Paid leave» box on the payslip. */
data class LedgerRow(
    val month: YearMonth,
    val previousX100: Int,
    val accountedX100: Int,
    val acquiredX100: Int,
    val remainingX100: Int,
    val projected: Boolean,
)

data class Summary(
    val currentMonth: YearMonth,
    /** Expected «Remaining» on this month's payslip. */
    val currentSlipX100: Int,
    /** Holiday days recorded that will be deducted on future payslips. */
    val pendingFutureX100: Int,
    /** Days to be deducted on next month's payslip. */
    val nextSlipDeductionX100: Int,
    /** Real remaining balance after every recorded holiday (no future accrual). */
    val availableX100: Int,
    val rows: List<LedgerRow>,
    val usedThisYear: Map<LeaveType, Int>,
    val totalThisYearX100: Int,
    val upcoming: List<LeaveEntry>,
    /** Holidays recorded in the app that belong to the reference payslip (to compare with its «Accounted»). */
    val recordedOnOpeningSlipX100: Int = 0,
) {
    fun row(month: YearMonth): LedgerRow? = rows.firstOrNull { it.month == month }
}

object Rules {

    val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // ---------- HR rules ----------

    /**
     * Payslip that a Holiday belongs to, decided by its FIRST day and never split:
     * day 1–15 → same month's payslip, day 16+ → next month's payslip.
     */
    fun payslipMonth(start: LocalDate): YearMonth {
        val ym = YearMonth.from(start)
        return if (start.dayOfMonth <= 15) ym else ym.plusMonths(1)
    }

    fun calendarDays(start: LocalDate, end: LocalDate): Long =
        if (end.isBefore(start)) 0 else ChronoUnit.DAYS.between(start, end) + 1

    fun workingDays(start: LocalDate, end: LocalDate, weekend: Set<DayOfWeek>): Int {
        if (end.isBefore(start)) return 0
        var d = start
        var count = 0
        while (!d.isAfter(end)) {
            if (d.dayOfWeek !in weekend) count++
            d = d.plusDays(1)
        }
        return count
    }

    /** Suggested number of days for a date range (1 for a single day). */
    fun suggestedDaysX100(start: LocalDate, end: LocalDate, weekend: Set<DayOfWeek>): Int {
        if (end.isBefore(start)) return 0
        if (start == end) return 100
        return workingDays(start, end, weekend) * 100
    }

    // ---------- Validation ----------

    fun validate(draft: LeaveDraft, settings: AppSettings?, entries: List<LeaveEntry>, today: LocalDate): ValidationResult {
        val issues = mutableListOf<Issue>()
        fun err(t: String) { issues += Issue(IssueLevel.ERROR, t) }
        fun warn(t: String) { issues += Issue(IssueLevel.WARNING, t) }
        fun info(t: String) { issues += Issue(IssueLevel.INFO, t) }

        if (settings == null) {
            err(tr("أكمل الإعداد الأولي (الرصيد) قبل تسجيل الإجازات.", "Complete the first setup (balance) before adding leaves."))
            return ValidationResult(issues)
        }
        val start = draft.start
        val end = draft.end
        if (end.isBefore(start)) {
            err(tr(
                "تاريخ «إلى» قبل تاريخ «من». اختر تاريخاً مساوياً أو بعد ${fmtDate(start)}.",
                "«To» date is before «From» date. Pick ${fmtDate(start)} or later.",
            ))
            return ValidationResult(issues)
        }
        val cal = calendarDays(start, end)
        if (cal > 366) {
            err(tr("المدة أطول من سنة ($cal يوم). تأكد من التواريخ.", "Period is longer than a year ($cal days). Check the dates."))
            return ValidationResult(issues)
        }
        val days = draft.daysX100
        when {
            days == null -> err(tr("اكتب عدد الأيام رقماً صحيحاً مثل 0.5 أو 1 أو 2.5.", "Type the number of days, e.g. 0.5, 1 or 2.5."))
            days <= 0 -> err(tr("عدد الأيام يجب أن يكون أكبر من صفر.", "Number of days must be more than zero."))
            days % 50 != 0 -> err(tr("عدد الأيام يجب أن يكون بمضاعفات النصف: 0.5، 1، 1.5 …", "Days must be in halves: 0.5, 1, 1.5 …"))
            days > cal * 100 -> err(tr(
                "عدد الأيام (${fmtDays(days)}) أكبر من عدد أيام الفترة ($cal يوم).",
                "Days (${fmtDays(days)}) are more than the days in the period ($cal).",
            ))
        }
        if (draft.note.length > MAX_NOTE_LENGTH) {
            err(tr("الملاحظة أطول من $MAX_NOTE_LENGTH حرف.", "Note is longer than $MAX_NOTE_LENGTH characters."))
        }
        if (days == null || issues.any { it.level == IssueLevel.ERROR }) return ValidationResult(issues)

        // Overlaps — two half days on the same date are allowed.
        for (o in entries) {
            if (o.id == draft.editingId) continue
            if (end.isBefore(o.start) || start.isAfter(o.end)) continue
            val sameSingleDay = start == end && o.start == o.end && o.start == start
            if (sameSingleDay && days + o.daysX100 <= 100) {
                info(tr(
                    "يوجد ${fmtDays(o.daysX100)} يوم (${o.type.ar}) في نفس التاريخ. المجموع = ${fmtDays(days + o.daysX100)} يوم.",
                    "${fmtDays(o.daysX100)} day (${o.type.en}) already on this date. Total = ${fmtDays(days + o.daysX100)}.",
                ))
            } else {
                err(tr(
                    "تتعارض مع إجازة مسجلة: ${o.type.ar} ${rangeText(o.start, o.end)}. عدّل القديمة أو غيّر التواريخ.",
                    "Overlaps an existing leave: ${o.type.en} ${rangeText(o.start, o.end)}. Edit it or change the dates.",
                ))
            }
        }
        if (issues.any { it.level == IssueLevel.ERROR }) return ValidationResult(issues)

        // Sanity warnings
        val wd = workingDays(start, end, settings.weekend)
        if (start == end) {
            if (start.dayOfWeek in settings.weekend) {
                warn(tr(
                    "التاريخ ${fmtDate(start)} يوافق يوم ${Tr.dayName(start)} وهو عطلة نهاية أسبوع. هل أنت متأكد؟",
                    "${fmtDate(start)} is a ${Tr.dayName(start)} (weekend). Are you sure?",
                ))
            }
        } else if (days != wd * 100) {
            warn(tr(
                "كتبت ${fmtDays(days)} يوم، بينما أيام العمل في هذه الفترة = $wd (بدون عطلة نهاية الأسبوع). تأكد أنه نفس الرقم في نموذج الطلب.",
                "You typed ${fmtDays(days)} days; working days in this period = $wd (weekends excluded). Make sure it matches the request form.",
            ))
        }
        if (start.isAfter(today.plusDays(365))) warn(tr("التاريخ بعد أكثر من سنة من اليوم. تأكد من السنة.", "Date is more than a year ahead. Check the year."))
        if (start.isBefore(today.minusYears(2))) warn(tr("التاريخ قبل أكثر من سنتين. تأكد من السنة.", "Date is more than 2 years ago. Check the year."))

        if (draft.type.onPayslip) {
            val slip = payslipMonth(start)
            if (payslipMonth(end) != slip) {
                info(tr(
                    "كامل الإجازة (${fmtDays(days)} يوم) تُحسب في سليب ${Tr.monthLabel(slip)} حسب تاريخ أول يوم، ولا تُقسم.",
                    "The whole leave (${fmtDays(days)} days) is counted on the ${Tr.monthLabel(slip)} payslip by its first day — not split.",
                ))
            } else {
                info(tr(
                    "ستُخصم ${fmtDays(days)} يوم في سليب ${Tr.monthLabel(slip)}.",
                    "${fmtDays(days)} days will be deducted on the ${Tr.monthLabel(slip)} payslip.",
                ))
            }
            if (!slip.isAfter(settings.openingMonth)) {
                warn(tr(
                    "هذه الإجازة تعود لسليب ${Tr.monthLabel(slip)}، وهو محسوب مسبقاً في رصيدك الافتتاحي، لذلك لن تُخصم مرة ثانية (تُحفظ في السجل فقط).",
                    "This leave belongs to the ${Tr.monthLabel(slip)} payslip, already inside your opening balance, so it won't be deducted again (kept in history only).",
                ))
            } else {
                val others = entries.filter { it.id != draft.editingId }
                val before = summarize(AppData(settings, others), today).availableX100
                val after = before - days
                if (after < 0) {
                    warn(tr(
                        "رصيدك سيصبح سالباً (${fmtDays(after)} يوم). رصيدك الحالي ${fmtDays(before)} يوم.",
                        "Your balance will become negative (${fmtDays(after)}). Current balance ${fmtDays(before)}.",
                    ))
                }
            }
        } else {
            info(draft.type.hint)
        }
        if (start.isAfter(today)) {
            info(tr("إجازة مخططة (مستقبلية)، وتُحسب فوراً في الرصيد المتبقي.", "Planned (future) leave — counted in the remaining balance right away."))
        }
        return ValidationResult(issues)
    }

    data class SettingsInput(
        val name: String,
        /** «Previous balance» box of the payslip. */
        val previousText: String,
        /** «Accounted this month» box of the payslip. */
        val accountedText: String,
        val openingMonth: YearMonth,
        val weekend: Set<DayOfWeek>,
    )

    data class SettingsValidation(
        val issues: List<Issue>,
        val settings: AppSettings?,
        val previousInvalid: Boolean,
        val accountedInvalid: Boolean,
        /** Live «Remaining» = previous − accounted + 2.08 (null while a box is invalid). */
        val remainingX100: Int?,
    ) {
        val hasErrors: Boolean get() = issues.any { it.level == IssueLevel.ERROR }
    }

    fun validateSettings(input: SettingsInput, today: LocalDate): SettingsValidation {
        val issues = mutableListOf<Issue>()
        val name = input.name.trim()
        if (name.length > 60) issues += Issue(IssueLevel.ERROR, tr("الاسم طويل جداً (الحد 60 حرف).", "Name is too long (max 60)."))

        val prev = parseDaysX100(input.previousText)
        var prevInvalid = false
        when {
            input.previousText.isBlank() -> {
                prevInvalid = true
                issues += Issue(IssueLevel.ERROR, tr(
                    "اكتب «الرصيد السابق» (Previous balance) كما في السليب، مثال: 9.55.",
                    "Type «Previous balance» from the payslip, e.g. 9.55.",
                ))
            }
            prev == null -> {
                prevInvalid = true
                issues += Issue(IssueLevel.ERROR, tr(
                    "«الرصيد السابق» يجب أن يكون رقماً مثل 9 أو 9.55 (خانتين بعد الفاصلة كحد أقصى).",
                    "«Previous balance» must be a number like 9 or 9.55 (max 2 decimals).",
                ))
            }
            prev < MIN_BALANCE_X100 || prev > MAX_BALANCE_X100 -> {
                prevInvalid = true
                issues += Issue(IssueLevel.ERROR, tr(
                    "«الرصيد السابق» (${fmtDays(prev)}) غير منطقي. المسموح بين ${fmtDays(MIN_BALANCE_X100)} و ${fmtDays(MAX_BALANCE_X100)}.",
                    "«Previous balance» (${fmtDays(prev)}) is not realistic (${fmtDays(MIN_BALANCE_X100)} to ${fmtDays(MAX_BALANCE_X100)}).",
                ))
            }
        }

        val acc = if (input.accountedText.isBlank()) 0 else parseDaysX100(input.accountedText)
        var accInvalid = false
        when {
            acc == null -> {
                accInvalid = true
                issues += Issue(IssueLevel.ERROR, tr(
                    "«المحتسب هذا الشهر» يجب أن يكون رقماً مثل 0 أو 2.50.",
                    "«Accounted this month» must be a number like 0 or 2.50.",
                ))
            }
            acc < 0 || acc > 3100 -> {
                accInvalid = true
                issues += Issue(IssueLevel.ERROR, tr(
                    "«المحتسب هذا الشهر» يجب أن يكون بين 0 و 31.",
                    "«Accounted this month» must be between 0 and 31.",
                ))
            }
            acc % 50 != 0 -> issues += Issue(IssueLevel.WARNING, tr(
                "عادةً يكون «المحتسب» بمضاعفات النصف (مثل 2.50). تأكد من الرقم.",
                "«Accounted» is usually in halves (like 2.50). Please double-check.",
            ))
        }

        val remaining = if (prev != null && !prevInvalid && acc != null && !accInvalid) prev - acc + ACCRUAL_X100 else null
        if (remaining != null) {
            if (remaining < 0) issues += Issue(IssueLevel.WARNING, tr("«المتبقي» سالب. تأكد من أرقام السليب.", "«Remaining» is negative. Check the payslip numbers."))
            if (remaining > 4500) issues += Issue(IssueLevel.WARNING, tr("«المتبقي» أعلى من 45 يوم. تأكد من الأرقام.", "«Remaining» is above 45 days. Please double-check."))
        }

        val current = YearMonth.from(today)
        if (input.openingMonth.isAfter(current)) {
            issues += Issue(IssueLevel.ERROR, tr("شهر السليب لا يمكن أن يكون في المستقبل.", "Payslip month can't be in the future."))
        } else if (input.openingMonth.isBefore(current.minusMonths(24))) {
            issues += Issue(IssueLevel.WARNING, tr("شهر السليب قديم (أكثر من سنتين). الأفضل استخدام آخر سليب.", "Payslip month is over 2 years old. Use your latest payslip."))
        }
        if (input.weekend.size > 3) issues += Issue(IssueLevel.ERROR, tr("لا يمكن اختيار أكثر من 3 أيام عطلة.", "Pick at most 3 weekend days."))
        if (input.weekend.isEmpty()) issues += Issue(IssueLevel.WARNING, tr("لم تختر أي يوم عطلة أسبوعية.", "No weekend day selected."))

        val ok = issues.none { it.level == IssueLevel.ERROR } && remaining != null
        return SettingsValidation(
            issues = issues,
            settings = if (ok) AppSettings(name, remaining!!, input.openingMonth, input.weekend, prev, acc) else null,
            previousInvalid = prevInvalid,
            accountedInvalid = accInvalid,
            remainingX100 = remaining,
        )
    }

    // ---------- Totals & balance ----------

    fun totalsByType(entries: List<LeaveEntry>): Map<LeaveType, Int> =
        LeaveType.entries.associateWith { t -> entries.filter { it.type == t }.sumOf { it.daysX100 } }

    /** Calendar-month totals (by the leave's first day), newest first. */
    fun totalsByMonth(entries: List<LeaveEntry>): List<Pair<YearMonth, Map<LeaveType, Int>>> =
        entries.groupBy { YearMonth.from(it.start) }
            .toSortedMap(compareByDescending { it })
            .map { (m, list) -> m to totalsByType(list) }

    fun summarize(data: AppData, today: LocalDate): Summary {
        val current = YearMonth.from(today)
        val s = data.settings
        val thisYear = data.entries.filter { it.start.year == today.year }
        val usedThisYear = totalsByType(thisYear)
        val totalThisYear = thisYear.sumOf { it.daysX100 }
        val upcoming = data.entries.filter { it.start.isAfter(today) }.sortedBy { it.start }
        if (s == null) return Summary(current, 0, 0, 0, 0, emptyList(), usedThisYear, totalThisYear, upcoming)

        val deductions = HashMap<YearMonth, Int>()
        var recordedOnOpening = 0
        for (e in data.entries) {
            if (!e.type.onPayslip) continue
            val m = payslipMonth(e.start)
            if (m == s.openingMonth) recordedOnOpening += e.daysX100
            if (!m.isAfter(s.openingMonth)) continue // already inside the reference payslip
            deductions[m] = (deductions[m] ?: 0) + e.daysX100
        }
        // Always show at least next month's expected payslip.
        var lastMonth = current.plusMonths(1)
        deductions.keys.maxOrNull()?.let { if (it.isAfter(lastMonth)) lastMonth = it }

        val rows = mutableListOf<LedgerRow>()
        if (s.hasSlipDetails) {
            // The reference payslip itself, exactly as printed.
            rows += LedgerRow(s.openingMonth, s.slipPreviousX100!!, s.slipAccountedX100!!, ACCRUAL_X100, s.openingBalanceX100, false)
        }
        var remaining = s.openingBalanceX100
        var currentSlip = s.openingBalanceX100
        var m = s.openingMonth.plusMonths(1)
        while (!m.isAfter(lastMonth)) {
            val projected = m.isAfter(current)
            val ded = deductions[m] ?: 0
            val prev = remaining
            remaining = prev - ded + ACCRUAL_X100
            rows += LedgerRow(m, prev, ded, ACCRUAL_X100, remaining, projected)
            if (!projected) currentSlip = remaining
            m = m.plusMonths(1)
        }
        val pendingFuture = deductions.filterKeys { it.isAfter(current) }.values.sum()
        return Summary(
            currentMonth = current,
            currentSlipX100 = currentSlip,
            pendingFutureX100 = pendingFuture,
            nextSlipDeductionX100 = deductions[current.plusMonths(1)] ?: 0,
            availableX100 = currentSlip - pendingFuture,
            rows = rows,
            usedThisYear = usedThisYear,
            totalThisYearX100 = totalThisYear,
            upcoming = upcoming,
            recordedOnOpeningSlipX100 = recordedOnOpening,
        )
    }

    /** Leaves on each date of [year] (for the calendar). Weekend days inside a multi-day range are skipped. */
    fun calendarMarks(entries: List<LeaveEntry>, year: Int, weekend: Set<DayOfWeek>): Map<LocalDate, List<LeaveEntry>> {
        val out = HashMap<LocalDate, MutableList<LeaveEntry>>()
        for (e in entries) {
            if (e.end.year < year || e.start.year > year) continue
            var d = e.start
            while (!d.isAfter(e.end)) {
                val skip = e.start != e.end && d.dayOfWeek in weekend
                if (d.year == year && !skip) out.getOrPut(d) { mutableListOf() } += e
                d = d.plusDays(1)
            }
        }
        return out
    }

    fun matchesSearch(e: LeaveEntry, query: String): Boolean {
        val q = normalizeDigits(query).trim().lowercase()
        if (q.isEmpty()) return true
        val hay = listOf(
            e.type.ar, e.type.en, e.note, fmtDate(e.start), fmtDate(e.end), fmtDays(e.daysX100),
            Tr.monthLabel(YearMonth.from(e.start)), e.start.toString(),
        ).joinToString(" ").lowercase()
        return q.split(Regex("\\s+")).all { hay.contains(it) }
    }

    // ---------- Parsing & formatting ----------

    fun normalizeDigits(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            sb.append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    '٫', '،' -> '.'
                    '−' -> '-'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /** Accepts Arabic digits, "," or "٫" as decimal separator, and "½". Returns hundredths or null. */
    fun parseDaysX100(raw: String): Int? {
        var s = normalizeDigits(raw.trim()).replace(',', '.')
        s = if (s == "½") "0.5" else s.replace("½", ".5")
        if (s.startsWith(".")) s = "0$s"
        if (!Regex("^-?\\d{1,4}(\\.\\d{0,2})?$").matches(s)) return null
        val neg = s.startsWith("-")
        val body = if (neg) s.substring(1) else s
        val intPart = body.substringBefore('.').toInt()
        val frac = body.substringAfter('.', "").padEnd(2, '0').take(2).toInt()
        val v = intPart * 100 + frac
        return if (neg) -v else v
    }

    fun fmtDays(x100: Int): String {
        val neg = x100 < 0
        val a = kotlin.math.abs(x100)
        val i = a / 100
        val f = a % 100
        val body = when {
            f == 0 -> "$i"
            f % 10 == 0 -> "$i.${f / 10}"
            else -> "$i.${f.toString().padStart(2, '0')}"
        }
        return if (neg) "-$body" else body
    }

    /** Always two decimals, like the payslip (9.13, 2.50). */
    fun fmtSlip(x100: Int): String {
        val neg = x100 < 0
        val a = kotlin.math.abs(x100)
        val body = "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
        return if (neg) "-$body" else body
    }

    fun fmtDate(d: LocalDate): String = d.format(dateFmt)

    fun rangeText(start: LocalDate, end: LocalDate): String =
        if (start == end) fmtDate(start) else "${fmtDate(start)} → ${fmtDate(end)}"
}
