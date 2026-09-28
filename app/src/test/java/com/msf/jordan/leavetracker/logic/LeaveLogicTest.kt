package com.msf.jordan.leavetracker.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LeaveLogicTest {

    private val weekend = Rules.DEFAULT_WEEKEND
    private val today = LocalDate.of(2026, 9, 28) // Monday
    private val settings = AppSettings("", 1000, YearMonth.of(2026, 8), weekend)

    private fun entry(id: String, type: LeaveType, s: String, e: String, x100: Int) =
        LeaveEntry(id, type, LocalDate.parse(s), LocalDate.parse(e), x100, "", 0L)

    private fun draft(type: LeaveType, s: String, e: String = s, days: Int? = 100, editing: String? = null) =
        LeaveDraft(editing, type, LocalDate.parse(s), LocalDate.parse(e), days, "")

    @Test fun cutoffRuleByFirstDayNoSplit() {
        assertEquals(YearMonth.of(2026, 9), Rules.payslipMonth(LocalDate.of(2026, 9, 1)))
        assertEquals(YearMonth.of(2026, 9), Rules.payslipMonth(LocalDate.of(2026, 9, 15)))
        assertEquals(YearMonth.of(2026, 10), Rules.payslipMonth(LocalDate.of(2026, 9, 16)))
        assertEquals(YearMonth.of(2027, 1), Rules.payslipMonth(LocalDate.of(2026, 12, 31)))
        // 13 → 17 Sep: whole leave goes to the September payslip, not split
        val data = AppData(settings, listOf(entry("a", LeaveType.HOLIDAY, "2026-09-13", "2026-09-17", 500)))
        val s = Rules.summarize(data, today)
        assertEquals(500, s.rows.first { it.month == YearMonth.of(2026, 9) }.accountedX100)
        assertEquals(0, s.nextSlipDeductionX100)
    }

    @Test fun ledgerMatchesRealPayslip() {
        // Payslip: previous 9.55, accounted 2.50, acquired 2.08, remaining 9.13
        val st = AppSettings("", 955, YearMonth.of(2026, 7), weekend)
        val data = AppData(st, listOf(
            entry("a", LeaveType.HOLIDAY, "2026-07-20", "2026-07-21", 200), // → Aug slip
            entry("b", LeaveType.HOLIDAY, "2026-08-03", "2026-08-03", 50),  // → Aug slip
            entry("c", LeaveType.SICK, "2026-08-05", "2026-08-05", 100),    // never on slip
        ))
        val row = Rules.summarize(data, LocalDate.of(2026, 8, 20)).row(YearMonth.of(2026, 8))!!
        assertEquals(YearMonth.of(2026, 8), row.month)
        assertEquals("9.55", Rules.fmtSlip(row.previousX100))
        assertEquals("2.50", Rules.fmtSlip(row.accountedX100))
        assertEquals("2.08", Rules.fmtSlip(row.acquiredX100))
        assertEquals("9.13", Rules.fmtSlip(row.remainingX100))
    }

    @Test fun workingDays() {
        assertEquals(3, Rules.workingDays(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28), weekend))
        assertEquals(0, Rules.workingDays(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), weekend))
        assertEquals(100, Rules.suggestedDaysX100(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25), weekend))
    }

    @Test fun balanceNoFloatingDrift() {
        val data = AppData(settings, listOf(
            entry("a", LeaveType.HOLIDAY, "2026-09-10", "2026-09-10", 100),
            entry("b", LeaveType.HOLIDAY, "2026-09-20", "2026-09-21", 200),
            entry("d", LeaveType.HOLIDAY, "2026-08-05", "2026-08-05", 100), // inside opening balance
        ))
        val s = Rules.summarize(data, today)
        assertEquals(1000 - 100 + 208, s.currentSlipX100)
        assertEquals(200, s.nextSlipDeductionX100)
        assertEquals(908, s.availableX100)
        val y = Rules.summarize(AppData(settings.copy(openingBalanceX100 = 0, openingMonth = YearMonth.of(2025, 9)), emptyList()), today)
        assertEquals("24.96", Rules.fmtDays(y.currentSlipX100))
    }

    @Test fun totalsForAllTypes() {
        val list = listOf(
            entry("a", LeaveType.HOLIDAY, "2026-09-10", "2026-09-10", 50),
            entry("b", LeaveType.SICK, "2026-09-14", "2026-09-15", 200),
            entry("c", LeaveType.SICK, "2026-08-03", "2026-08-03", 100),
        )
        val t = Rules.totalsByType(list)
        assertEquals(300, t[LeaveType.SICK])
        assertEquals(50, t[LeaveType.HOLIDAY])
        val months = Rules.totalsByMonth(list)
        assertEquals(YearMonth.of(2026, 9), months.first().first)
        assertEquals(350, Rules.summarize(AppData(settings, list), today).totalThisYearX100)
    }

    @Test fun parsing() {
        assertEquals(1250, Rules.parseDaysX100("12.5"))
        assertEquals(913, Rules.parseDaysX100("9,13"))
        assertEquals(1250, Rules.parseDaysX100("١٢٫٥"))
        assertEquals(50, Rules.parseDaysX100("½"))
        assertEquals(50, Rules.parseDaysX100(".5"))
        assertEquals(700, Rules.parseDaysX100(" 7. "))
        assertNull(Rules.parseDaysX100("12.555"))
        assertNull(Rules.parseDaysX100("abc"))
        assertNull(Rules.parseDaysX100(""))
        assertEquals("2.50", Rules.fmtSlip(250))
        assertEquals("-1.5", Rules.fmtDays(-150))
    }

    @Test fun validationErrors() {
        assertTrue(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-10", "2026-10-05"), settings, emptyList(), today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.SICK, "2026-10-04", "2026-10-06", days = 130), settings, emptyList(), today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.SICK, "2026-10-04", "2026-10-05", days = 300), settings, emptyList(), today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.SICK, "2026-10-04", days = null), settings, emptyList(), today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.SICK, "2026-10-04", days = 150), settings, emptyList(), today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-04"), null, emptyList(), today).hasErrors)
        // half day and form-entered days are accepted
        assertFalse(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-04", days = 50), settings, emptyList(), today).hasErrors)
        val v = Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-04", "2026-10-08", days = 450), settings, emptyList(), today)
        assertFalse(v.hasErrors)
        assertTrue(v.warnings.isNotEmpty()) // differs from 5 working days
    }

    @Test fun overlaps() {
        val existing = listOf(entry("x", LeaveType.HOLIDAY, "2026-10-04", "2026-10-06", 300), entry("h", LeaveType.SICK, "2026-10-11", "2026-10-11", 50))
        assertTrue(Rules.validate(draft(LeaveType.SICK, "2026-10-05"), settings, existing, today).hasErrors)
        assertFalse(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-04", "2026-10-07", days = 400, editing = "x"), settings, existing, today).hasErrors)
        assertFalse(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-11", days = 50), settings, existing, today).hasErrors)
        assertTrue(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-11", days = 100), settings, existing, today).hasErrors)
    }

    @Test fun warnings() {
        assertTrue(Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-02"), settings, emptyList(), today).warnings.isNotEmpty()) // Friday
        val neg = Rules.validate(draft(LeaveType.HOLIDAY, "2026-10-04", "2026-11-12", days = 2900), settings, emptyList(), today)
        assertTrue(neg.warnings.isNotEmpty())
    }

    @Test fun settingsValidation() {
        fun v(prev: String, acc: String = "0", m: YearMonth = YearMonth.of(2026, 8)) =
            Rules.validateSettings(Rules.SettingsInput("Khader", prev, acc, m, weekend), today)
        val ok = v("9.55", "2.50")
        assertFalse(ok.hasErrors)
        assertEquals(913, ok.remainingX100)
        assertEquals(913, ok.settings!!.openingBalanceX100)
        assertEquals(955, ok.settings!!.slipPreviousX100)
        assertEquals(250, ok.settings!!.slipAccountedX100)
        assertTrue(v("").previousInvalid)
        assertTrue(v("9.55", "abc").accountedInvalid)
        assertTrue(v("12.555").hasErrors)
        assertTrue(v("500").hasErrors)
        assertTrue(v("10", "0", YearMonth.of(2026, 11)).hasErrors)
    }

    /** Khader's real case: August 2026 payslip 9.55 / 2.50 / 2.08 / 9.13 and leaves 16/07, 03/08 (½), 04/08. */
    @Test fun realAugust2026Payslip() {
        val st = Rules.validateSettings(Rules.SettingsInput("", "9.55", "2.50", YearMonth.of(2026, 8), weekend), today).settings!!
        val data = AppData(st, listOf(
            entry("a", LeaveType.HOLIDAY, "2026-07-16", "2026-07-16", 100),
            entry("b", LeaveType.HOLIDAY, "2026-08-03", "2026-08-03", 50),
            entry("c", LeaveType.HOLIDAY, "2026-08-04", "2026-08-04", 100),
        ))
        val s = Rules.summarize(data, today) // 28 Sep 2026
        val aug = s.row(YearMonth.of(2026, 8))!!
        assertEquals(listOf("9.55", "2.50", "2.08", "9.13"),
            listOf(aug.previousX100, aug.accountedX100, aug.acquiredX100, aug.remainingX100).map { Rules.fmtSlip(it) })
        assertEquals(250, s.recordedOnOpeningSlipX100) // matches the payslip's «Accounted»
        val sep = s.row(YearMonth.of(2026, 9))!!
        assertEquals("9.13", Rules.fmtSlip(sep.previousX100))
        assertEquals("11.21", Rules.fmtSlip(sep.remainingX100))
        assertEquals(1121, s.availableX100)
        val oct = s.row(YearMonth.of(2026, 10))!!    // next month always shown (expected)
        assertTrue(oct.projected)
        assertEquals("13.29", Rules.fmtSlip(oct.remainingX100))
    }

    @Test fun calendarMarksSkipWeekendInsideRange() {
        val marks = Rules.calendarMarks(listOf(entry("a", LeaveType.HOLIDAY, "2026-09-24", "2026-09-28", 300)), 2026, weekend)
        assertEquals(3, marks.size)
        assertFalse(marks.containsKey(LocalDate.of(2026, 9, 25)))
    }

    @Test fun search() {
        val e = LeaveEntry("a", LeaveType.SICK, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 100, "flu", 0L)
        assertTrue(Rules.matchesSearch(e, "sick"))
        assertTrue(Rules.matchesSearch(e, "مرضية"))
        assertTrue(Rules.matchesSearch(e, "05/08"))
        assertTrue(Rules.matchesSearch(e, "flu"))
        assertFalse(Rules.matchesSearch(e, "holiday"))
    }

    @Test fun i18nMonthsAreJordanian() {
        Tr.arabic = true
        assertEquals("أيلول 2026", Tr.monthLabel(YearMonth.of(2026, 9)))
        Tr.arabic = false
        assertEquals("September 2026", Tr.monthLabel(YearMonth.of(2026, 9)))
        Tr.arabic = true
    }

    // ---------- scanned form ----------

    @Test fun parserReadsTheMsfForm() {
        val ocr = """
            LEAVE REQUEST FORM
            INFORMATION TO BE FILLED IN BY APPLICANT:
            Name:
            Khader Adel Al-Hmaimat
            Position:
            Specialized physiotherapist
            Dept:
            physio department
            From/ To (Duration):
            5/8/26
            Numbers of days requested:
            I day
            Employee Number
            452
            Type of leave (Holiday, Sick
            Personal, Training, Unpaid
            Compassionate):
            Sick
            PLEASE DO NOT GIVE REASON FOR SICK LEAVE
            Date of request: 5/8/26
        """.trimIndent()
        val p = FormParser.parse(ocr, today)
        assertEquals(LeaveType.SICK, p.type)
        assertEquals(LocalDate.of(2026, 8, 5), p.start)
        assertEquals(LocalDate.of(2026, 8, 5), p.end)
        assertEquals(100, p.daysX100)
    }

    @Test fun parserRangesAndHalfDays() {
        val p = FormParser.parse("From/To: 20/9/26 - 24/9/26\n3 days\nHoliday", today)
        assertEquals(LocalDate.of(2026, 9, 20), p.start)
        assertEquals(LocalDate.of(2026, 9, 24), p.end)
        assertEquals(300, p.daysX100)
        assertEquals(LeaveType.HOLIDAY, p.type)

        val q = FormParser.parse("7-9/10/2026\nhalf day\nHoliclay", today)
        assertEquals(LocalDate.of(2026, 10, 7), q.start)
        assertEquals(LocalDate.of(2026, 10, 9), q.end)
        assertEquals(50, q.daysX100)
        assertEquals(LeaveType.HOLIDAY, q.type)

        val r = FormParser.parse("nothing useful here", today)
        assertEquals(0, r.foundCount)
    }
}
