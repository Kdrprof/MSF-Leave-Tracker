package com.msf.jordan.leavetracker.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Pure business logic (no Android imports) so it can be unit-tested on the JVM.
 * All day amounts are stored as Int hundredths (x100) to avoid floating-point drift:
 * 2.08 days = 208, half a day = 50.
 */

const val ACCRUAL_X100 = 208          // +2.08 days every payslip month
const val MAX_NOTE_LENGTH = 200
const val MIN_BALANCE_X100 = -3000    // -30 days
const val MAX_BALANCE_X100 = 10000    // 100 days

enum class LeaveType(
    val key: String,
    val en: String,
    val ar: String,
    val colorHex: Long,
    val affectsBalance: Boolean,
    val hint: String,
) {
    HOLIDAY("holiday", "Holiday", "إجازة سنوية", 0xFF10B981, true,
        "تُخصم من رصيدك السنوي وتظهر في سليب الراتب حسب قاعدة يوم 15."),
    SICK("sick", "Sick", "إجازة مرضية", 0xFFEF4444, false,
        "لا تُخصم من الرصيد السنوي. احتفظ بالتقرير الطبي إن وُجد."),
    PERSONAL("personal", "Personal", "إجازة شخصية", 0xFF3B82F6, false,
        "لا تُخصم من الرصيد السنوي في هذا التطبيق (للتوثيق فقط)."),
    TRAINING("training", "Training", "إجازة تدريب", 0xFF8B5CF6, false,
        "لا تُخصم من الرصيد السنوي (للتوثيق فقط)."),
    UNPAID("unpaid", "Unpaid", "إجازة بدون أجر", 0xFFF97316, false,
        "لا تُخصم من الرصيد السنوي، لكنها قد تُخصم من قيمة الراتب."),
    COMPASSIONATE("compassionate", "Compassionate", "إجازة إنسانية / مواساة", 0xFF334155, false,
        "لا تُخصم من الرصيد السنوي (للتوثيق فقط).");

    val label: String get() = "$ar ($en)"

    companion object {
        fun fromKey(key: String?): LeaveType? = entries.firstOrNull { it.key == key }
    }
}

enum class DurationKind { HALF, ONE, MULTI }

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
    val openingBalanceX100: Int,
    val openingMonth: YearMonth,
    val weekend: Set<DayOfWeek>,
)

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
    val duration: DurationKind,
    val start: LocalDate,
    val end: LocalDate,
    /** null = automatic (working days). Only used for MULTI. */
    val manualDaysX100: Int?,
    val note: String,
)

data class LeavePart(val start: LocalDate, val end: LocalDate, val daysX100: Int)

enum class IssueLevel { ERROR, WARNING, INFO }

data class Issue(val level: IssueLevel, val text: String)

data class ValidationResult(val issues: List<Issue>, val parts: List<LeavePart>) {
    val hasErrors: Boolean get() = issues.any { it.level == IssueLevel.ERROR }
    val warnings: List<Issue> get() = issues.filter { it.level == IssueLevel.WARNING }
}

data class MonthRow(
    val month: YearMonth,
    val accruedX100: Int,
    val deductedX100: Int,
    val closingX100: Int,
    val projected: Boolean,
)

data class Summary(
    val currentMonth: YearMonth,
    /** Expected balance on the current month's payslip. */
    val currentSlipX100: Int,
    /** Holiday days already recorded that will be deducted on future payslips. */
    val pendingFutureX100: Int,
    /** Days deducted on next month's payslip (subset of pendingFuture). */
    val nextSlipDeductionX100: Int,
    /** Real remaining balance after every recorded holiday. */
    val availableX100: Int,
    val rows: List<MonthRow>,
    val usedThisYear: Map<LeaveType, Int>,
    val upcoming: List<LeaveEntry>,
)

object Rules {

    val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    private val arLocale: Locale = Locale.forLanguageTag("ar-JO")
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // ---------- HR rules ----------

    /** 1–15 → same month's payslip, 16+ → next month's payslip. */
    fun payslipMonth(date: LocalDate): YearMonth {
        val ym = YearMonth.from(date)
        return if (date.dayOfMonth <= 15) ym else ym.plusMonths(1)
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

    /** Splits a date range into consecutive groups that belong to the same payslip month. */
    fun splitByPayslip(start: LocalDate, end: LocalDate, weekend: Set<DayOfWeek>): List<LeavePart> {
        if (end.isBefore(start)) return emptyList()
        val parts = mutableListOf<LeavePart>()
        var groupStart = start
        var d = start
        while (!d.isAfter(end)) {
            val next = d.plusDays(1)
            val closeGroup = next.isAfter(end) || payslipMonth(next) != payslipMonth(d)
            if (closeGroup) {
                val wd = workingDays(groupStart, d, weekend)
                if (wd > 0) parts += LeavePart(groupStart, d, wd * 100)
                groupStart = next
            }
            d = next
        }
        return parts
    }

    // ---------- Planning ----------

    fun plan(draft: LeaveDraft, weekend: Set<DayOfWeek>): List<LeavePart> = when (draft.duration) {
        DurationKind.HALF -> listOf(LeavePart(draft.start, draft.start, 50))
        DurationKind.ONE -> listOf(LeavePart(draft.start, draft.start, 100))
        DurationKind.MULTI -> {
            if (draft.end.isBefore(draft.start)) emptyList()
            else if (spansPayslips(draft) && draft.type.affectsBalance) splitByPayslip(draft.start, draft.end, weekend)
            else {
                val days = draft.manualDaysX100 ?: (workingDays(draft.start, draft.end, weekend) * 100)
                if (days > 0) listOf(LeavePart(draft.start, draft.end, days)) else emptyList()
            }
        }
    }

    fun spansPayslips(draft: LeaveDraft): Boolean =
        draft.duration == DurationKind.MULTI && !draft.end.isBefore(draft.start) &&
            payslipMonth(draft.start) != payslipMonth(draft.end)

    /** Makes sure no stored holiday entry crosses a payslip boundary (used on import/load). */
    fun normalize(entries: List<LeaveEntry>, weekend: Set<DayOfWeek>): List<LeaveEntry> {
        val out = mutableListOf<LeaveEntry>()
        for (e in entries) {
            if (e.type.affectsBalance && payslipMonth(e.start) != payslipMonth(e.end)) {
                splitByPayslip(e.start, e.end, weekend).forEachIndexed { i, p ->
                    out += e.copy(id = if (i == 0) e.id else "${e.id}-$i", start = p.start, end = p.end, daysX100 = p.daysX100)
                }
            } else out += e
        }
        return out
    }

    fun inferDuration(e: LeaveEntry): DurationKind = when {
        e.start == e.end && e.daysX100 == 50 -> DurationKind.HALF
        e.start == e.end && e.daysX100 == 100 -> DurationKind.ONE
        else -> DurationKind.MULTI
    }

    // ---------- Validation ----------

    fun validate(
        draft: LeaveDraft,
        settings: AppSettings?,
        entries: List<LeaveEntry>,
        today: LocalDate,
    ): ValidationResult {
        val issues = mutableListOf<Issue>()
        fun err(t: String) { issues += Issue(IssueLevel.ERROR, t) }
        fun warn(t: String) { issues += Issue(IssueLevel.WARNING, t) }
        fun info(t: String) { issues += Issue(IssueLevel.INFO, t) }

        if (settings == null) {
            err("أكمل الإعداد الأولي (الرصيد الافتتاحي) قبل تسجيل الإجازات.")
            return ValidationResult(issues, emptyList())
        }
        val weekend = settings.weekend
        val start = draft.start
        val end = if (draft.duration == DurationKind.MULTI) draft.end else draft.start

        // Dates
        if (draft.duration == DurationKind.MULTI) {
            if (end.isBefore(start)) {
                err("تاريخ النهاية قبل تاريخ البداية. اختر تاريخ نهاية مساوياً أو بعد ${fmtDate(start)}.")
                return ValidationResult(issues, emptyList())
            }
            val cal = calendarDays(start, end)
            if (cal > 366) {
                err("المدة أطول من سنة ($cal يوم). تأكد من التواريخ.")
                return ValidationResult(issues, emptyList())
            }
            if (start == end) info("اخترت يوماً واحداً فقط. يمكنك استخدام خيار «يوم واحد» أو «نصف يوم».")
            val wd = workingDays(start, end, weekend)
            if (draft.manualDaysX100 == null && wd == 0) {
                err("كل الأيام المختارة عطلة نهاية أسبوع، لا يوجد أيام عمل لخصمها.")
            }
            if (draft.manualDaysX100 != null) {
                val m = draft.manualDaysX100
                if (spansPayslips(draft) && draft.type.affectsBalance) {
                    err("لا يمكن تعديل عدد الأيام يدوياً لإجازة سنوية تمتد عبر يوم 15. سجّلها كإجازتين: قبل 15 وبعده.")
                } else if (m <= 0) {
                    err("عدد الأيام يجب أن يكون أكبر من صفر.")
                } else if (m % 50 != 0) {
                    err("عدد الأيام يجب أن يكون بمضاعفات النصف: 0.5 أو 1 أو 1.5 ...")
                } else if (m > cal * 100) {
                    err("عدد الأيام (${fmtDays(m)}) أكبر من عدد أيام الفترة ($cal يوم).")
                } else if (m != wd * 100) {
                    warn("أدخلت ${fmtDays(m)} يوم بينما أيام العمل في الفترة = $wd. تأكد (مثلاً بسبب عطلة رسمية).")
                }
            }
        } else if (start.dayOfWeek in weekend) {
            warn("التاريخ ${fmtDate(start)} يوافق ${dayName(start)} وهو عطلة نهاية أسبوع. هل أنت متأكد؟")
        }

        // Note
        if (draft.note.length > MAX_NOTE_LENGTH) err("الملاحظة أطول من $MAX_NOTE_LENGTH حرف (${draft.note.length}).")

        // Date sanity
        if (start.isAfter(today.plusDays(365))) warn("تاريخ البداية بعد أكثر من سنة من اليوم. تأكد من السنة.")
        if (start.isBefore(today.minusYears(2))) warn("تاريخ البداية قبل أكثر من سنتين. تأكد من السنة.")

        if (issues.any { it.level == IssueLevel.ERROR }) return ValidationResult(issues, emptyList())

        val parts = plan(draft, weekend)
        if (parts.isEmpty()) {
            err("لا يوجد أيام صالحة للحفظ في هذه الفترة.")
            return ValidationResult(issues, emptyList())
        }

        // Overlaps
        val others = entries.filter { it.id != draft.editingId }
        for (o in others) {
            val overlaps = !(end.isBefore(o.start) || start.isAfter(o.end))
            if (!overlaps) continue
            val bothHalfSameDay = draft.duration == DurationKind.HALF && o.start == o.end && o.daysX100 == 50 && o.start == start
            if (bothHalfSameDay) {
                info("يوجد نصف يوم آخر مسجّل في نفس التاريخ (${o.type.ar}). المجموع = يوم كامل.")
            } else {
                err("تتعارض مع إجازة مسجلة: ${o.type.ar} ${rangeText(o.start, o.end)}. عدّل الإجازة القديمة أو غيّر التواريخ.")
            }
        }
        if (issues.any { it.level == IssueLevel.ERROR }) return ValidationResult(issues, emptyList())

        // Holiday-specific payslip info
        if (draft.type.affectsBalance) {
            if (parts.size > 1) {
                val lines = parts.joinToString("، ") { "${rangeText(it.start, it.end)} = ${fmtDays(it.daysX100)} يوم ← سليب ${monthLabel(payslipMonth(it.start))}" }
                info("الإجازة تمتد عبر يوم 15، لذلك ستُقسم تلقائياً إلى ${parts.size} أجزاء: $lines")
            } else {
                info("ستُخصم ${fmtDays(parts[0].daysX100)} يوم في سليب شهر ${monthLabel(payslipMonth(parts[0].start))}.")
            }
            val covered = parts.filter { !payslipMonth(it.start).isAfter(settings.openingMonth) }
            if (covered.isNotEmpty()) {
                warn("جزء من هذه الإجازة يعود لسليب ${monthLabel(payslipMonth(covered[0].start))} أو قبله، وهو محسوب مسبقاً في الرصيد الافتتاحي، لذلك لن يُخصم مرة ثانية.")
            }
            val before = summarize(AppData(settings, others), today).availableX100
            val newDeduction = parts.filter { payslipMonth(it.start).isAfter(settings.openingMonth) }.sumOf { it.daysX100 }
            val after = before - newDeduction
            if (newDeduction > 0 && after < 0) {
                warn("رصيدك سيصبح سالباً (${fmtDays(after)} يوم). رصيدك الحالي ${fmtDays(before)} يوم. تأكد من موافقة الموارد البشرية.")
            }
        } else {
            info(draft.type.hint)
        }

        if (draft.type == LeaveType.SICK && parts.sumOf { it.daysX100 } > 200) {
            info("الإجازة المرضية الطويلة عادةً تحتاج تقريراً طبياً. احتفظ بنسخة منه.")
        }
        if (start.isAfter(today)) info("هذه إجازة مخططة (مستقبلية)، وتُحسب فوراً في الرصيد المتبقي.")

        return ValidationResult(issues, parts)
    }

    data class SettingsInput(
        val name: String,
        val openingBalanceText: String,
        val openingMonth: YearMonth,
        val weekend: Set<DayOfWeek>,
    )

    data class SettingsValidation(val issues: List<Issue>, val settings: AppSettings?) {
        val hasErrors: Boolean get() = issues.any { it.level == IssueLevel.ERROR }
    }

    fun validateSettings(input: SettingsInput, today: LocalDate): SettingsValidation {
        val issues = mutableListOf<Issue>()
        val name = input.name.trim()
        if (name.length > 60) issues += Issue(IssueLevel.ERROR, "الاسم طويل جداً (الحد 60 حرف).")
        val bal = parseDaysX100(input.openingBalanceText)
        when {
            input.openingBalanceText.isBlank() -> issues += Issue(IssueLevel.ERROR, "اكتب الرصيد الافتتاحي كما يظهر في آخر سليب راتب (مثال: 12.5).")
            bal == null -> issues += Issue(IssueLevel.ERROR, "الرصيد يجب أن يكون رقماً مثل 12 أو 12.5 أو 12.08 (بحد أقصى خانتين بعد الفاصلة).")
            bal < MIN_BALANCE_X100 || bal > MAX_BALANCE_X100 ->
                issues += Issue(IssueLevel.ERROR, "الرصيد (${fmtDays(bal)}) غير منطقي. المسموح بين ${fmtDays(MIN_BALANCE_X100)} و ${fmtDays(MAX_BALANCE_X100)} يوم.")
            bal < 0 -> issues += Issue(IssueLevel.WARNING, "الرصيد الافتتاحي سالب. تأكد من الرقم في السليب.")
            bal > 4500 -> issues += Issue(IssueLevel.WARNING, "الرصيد أعلى من 45 يوم، وهو مرتفع. تأكد من الرقم.")
        }
        val current = YearMonth.from(today)
        if (input.openingMonth.isAfter(current)) {
            issues += Issue(IssueLevel.ERROR, "شهر السليب لا يمكن أن يكون في المستقبل. اختر شهر آخر سليب استلمته.")
        } else if (input.openingMonth.isBefore(current.minusMonths(24))) {
            issues += Issue(IssueLevel.WARNING, "شهر السليب قديم (أكثر من سنتين). الأفضل استخدام آخر سليب لديك.")
        }
        if (input.weekend.size > 3) issues += Issue(IssueLevel.ERROR, "لا يمكن اختيار أكثر من 3 أيام عطلة أسبوعية.")
        if (input.weekend.isEmpty()) issues += Issue(IssueLevel.WARNING, "لم تختر أي يوم عطلة أسبوعية؛ كل الأيام ستُحسب أيام عمل.")

        val ok = issues.none { it.level == IssueLevel.ERROR } && bal != null
        return SettingsValidation(
            issues,
            if (ok) AppSettings(name, bal!!, input.openingMonth, input.weekend) else null,
        )
    }

    // ---------- Balance ----------

    fun summarize(data: AppData, today: LocalDate): Summary {
        val current = YearMonth.from(today)
        val s = data.settings
        val usedThisYear = LeaveType.entries.associateWith { t ->
            data.entries.filter { it.type == t && it.start.year == today.year }.sumOf { it.daysX100 }
        }
        val upcoming = data.entries.filter { it.start.isAfter(today) }.sortedBy { it.start }
        if (s == null) {
            return Summary(current, 0, 0, 0, 0, emptyList(), usedThisYear, upcoming)
        }

        val deductions = HashMap<YearMonth, Int>()
        for (e in data.entries) {
            if (!e.type.affectsBalance) continue
            val m = payslipMonth(e.start)
            if (!m.isAfter(s.openingMonth)) continue // already reflected in the opening balance
            deductions[m] = (deductions[m] ?: 0) + e.daysX100
        }

        val lastDeductionMonth = deductions.keys.maxOrNull()
        var lastMonth = current
        if (lastDeductionMonth != null && lastDeductionMonth.isAfter(lastMonth)) lastMonth = lastDeductionMonth

        val rows = mutableListOf<MonthRow>()
        var closing = s.openingBalanceX100
        var currentSlip = s.openingBalanceX100
        var m = s.openingMonth.plusMonths(1)
        while (!m.isAfter(lastMonth)) {
            val projected = m.isAfter(current)
            val ded = deductions[m] ?: 0
            closing += ACCRUAL_X100 - ded
            rows += MonthRow(m, ACCRUAL_X100, ded, closing, projected)
            if (!projected) currentSlip = closing
            m = m.plusMonths(1)
        }

        val pendingFuture = deductions.filterKeys { it.isAfter(current) }.values.sum()
        val nextDeduction = deductions[current.plusMonths(1)] ?: 0
        return Summary(
            currentMonth = current,
            currentSlipX100 = currentSlip,
            pendingFutureX100 = pendingFuture,
            nextSlipDeductionX100 = nextDeduction,
            availableX100 = currentSlip - pendingFuture,
            rows = rows,
            usedThisYear = usedThisYear,
            upcoming = upcoming,
        )
    }

    // ---------- Parsing & formatting ----------

    /** Accepts Arabic/Persian digits and "," or "٫" as decimal separator. Returns hundredths or null. */
    fun parseDaysX100(raw: String): Int? {
        val sb = StringBuilder()
        for (ch in raw.trim()) {
            sb.append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    ',', '٫', '،' -> '.'
                    '−' -> '-'
                    else -> ch
                }
            )
        }
        val s = sb.toString()
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

    fun fmtDate(d: LocalDate): String = d.format(dateFmt)

    fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, arLocale)

    fun monthName(m: YearMonth): String = m.month.getDisplayName(TextStyle.FULL, arLocale)

    fun monthLabel(m: YearMonth): String = "${monthName(m)} ${m.year}"

    fun rangeText(start: LocalDate, end: LocalDate): String =
        if (start == end) fmtDate(start) else "${fmtDate(start)} ← ${fmtDate(end)}"
}
