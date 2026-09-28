package com.msf.jordan.leavetracker.logic

import java.time.LocalDate
import java.time.YearMonth

/** A monthly or yearly statement, rendered to PDF / text for sharing with a manager. */
data class Report(
    val title: String,
    val employee: String,
    val generatedOn: LocalDate,
    val entries: List<LeaveEntry>,
    val totals: Map<LeaveType, Int>,
    val totalX100: Int,
    val ledger: List<LedgerRow>,
    val availableX100: Int,
)

object Reports {

    fun monthly(data: AppData, month: YearMonth, today: LocalDate): Report {
        val list = data.entries.filter { YearMonth.from(it.start) == month }.sortedBy { it.start }
        val summary = Rules.summarize(data, today)
        return Report(
            title = tr("كشف إجازات شهر ${Tr.monthLabel(month)}", "Leave statement — ${Tr.monthLabel(month)}"),
            employee = data.settings?.name.orEmpty(),
            generatedOn = today,
            entries = list,
            totals = Rules.totalsByType(list),
            totalX100 = list.sumOf { it.daysX100 },
            ledger = summary.rows.filter { it.month == month },
            availableX100 = summary.monthEndX100,
        )
    }

    fun yearly(data: AppData, year: Int, today: LocalDate): Report {
        val list = data.entries.filter { it.start.year == year }.sortedBy { it.start }
        val summary = Rules.summarize(data, today)
        return Report(
            title = tr("كشف إجازات سنة $year", "Leave statement — $year"),
            employee = data.settings?.name.orEmpty(),
            generatedOn = today,
            entries = list,
            totals = Rules.totalsByType(list),
            totalX100 = list.sumOf { it.daysX100 },
            ledger = summary.rows.filter { it.month.year == year },
            availableX100 = summary.monthEndX100,
        )
    }

    /** Plain-text version (for WhatsApp / email body). */
    fun toText(r: Report): String = buildString {
        appendLine(r.title)
        if (r.employee.isNotBlank()) appendLine(tr("الموظف: ", "Employee: ") + r.employee)
        appendLine(tr("تاريخ الإصدار: ", "Generated: ") + Rules.fmtDate(r.generatedOn))
        appendLine()
        appendLine(tr("الرصيد المتبقي حتى نهاية الشهر الحالي: ", "Remaining balance at the end of this month: ") + Rules.fmtDays(r.availableX100) + tr(" يوم", " days"))
        appendLine()
        appendLine(tr("المجموع حسب النوع:", "Totals by type:"))
        LeaveType.entries.forEach { t ->
            appendLine("• ${t.title}: ${Rules.fmtDays(r.totals[t] ?: 0)}")
        }
        appendLine(tr("• المجموع الكلي: ", "• Grand total: ") + Rules.fmtDays(r.totalX100))
        if (r.ledger.isNotEmpty()) {
            appendLine()
            appendLine(tr("الإجازة السنوية في السليب (سابق / محتسب / مكتسب / متبقي):", "Paid leave on payslip (previous / accounted / acquired / remaining):"))
            r.ledger.forEach {
                appendLine("${Tr.monthLabel(it.month)}: ${Rules.fmtSlip(it.previousX100)} / ${Rules.fmtSlip(it.accountedX100)} / ${Rules.fmtSlip(it.acquiredX100)} / ${Rules.fmtSlip(it.remainingX100)}")
            }
        }
        appendLine()
        appendLine(tr("التفاصيل:", "Details:"))
        if (r.entries.isEmpty()) appendLine(tr("لا يوجد إجازات في هذه الفترة.", "No leaves in this period."))
        r.entries.forEach {
            append("• ${Rules.rangeText(it.start, it.end)} — ${it.type.title} — ${Rules.fmtDays(it.daysX100)}")
            if (it.note.isNotBlank()) append(" — ${it.note}")
            appendLine()
        }
    }
}
