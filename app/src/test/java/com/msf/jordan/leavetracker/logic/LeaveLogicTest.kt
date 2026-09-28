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
    private val today = LocalDate.of(2026, 9, 28)
    private val settings = AppSettings("", 1000, YearMonth.of(2026, 8), weekend)

    private fun entry(id: String, type: LeaveType, s: String, e: String, x100: Int) =
        LeaveEntry(id, type, LocalDate.parse(s), LocalDate.parse(e), x100, "", 0L)

    private fun draft(type: LeaveType, dur: DurationKind, s: String, e: String = s, manual: Int? = null, editing: String? = null) =
        LeaveDraft(editing, type, dur, LocalDate.parse(s), LocalDate.parse(e), manual, "")

    @Test fun cutoffRule() {
        assertEquals(YearMonth.of(2026, 9), Rules.payslipMonth(LocalDate.of(2026, 9, 1)))
        assertEquals(YearMonth.of(2026, 9), Rules.payslipMonth(LocalDate.of(2026, 9, 15)))
        assertEquals(YearMonth.of(2026, 10), Rules.payslipMonth(LocalDate.of(2026, 9, 16)))
        assertEquals(YearMonth.of(2027, 1), Rules.payslipMonth(LocalDate.of(2026, 12, 31)))
    }

    @Test fun workingDaysSkipWeekend() {
        // 2026-09-24 Thu .. 2026-09-28 Mon → Thu, Sun, Mon = 3 (Fri/Sat skipped)
        assertEquals(3, Rules.workingDays(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28), weekend))
        assertEquals(0, Rules.workingDays(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), weekend))
        assertEquals(0, Rules.workingDays(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 27), weekend))
    }

    @Test fun splitAcross15th() {
        // 13 Sep (Sun) .. 17 Sep (Thu) 2026
        val parts = Rules.splitByPayslip(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 17), weekend)
        assertEquals(2, parts.size)
        assertEquals(300, parts[0].daysX100) // 13,14,15
        assertEquals(200, parts[1].daysX100) // 16,17
        assertEquals(YearMonth.of(2026, 10), Rules.payslipMonth(parts[1].start))
    }

    @Test fun accrualAndDeduction() {
        val data = AppData(settings, listOf(
            entry("a", LeaveType.HOLIDAY, "2026-09-10", "2026-09-10", 100), // Sep slip
            entry("b", LeaveType.HOLIDAY, "2026-09-20", "2026-09-21", 200), // Oct slip
            entry("c", LeaveType.SICK, "2026-09-01", "2026-09-01", 100),     // ignored
            entry("d", LeaveType.HOLIDAY, "2026-08-05", "2026-08-05", 100),  // Aug slip = opening, ignored
        ))
        val s = Rules.summarize(data, today)
        assertEquals(1000 + 208 - 100, s.currentSlipX100) // 11.08
        assertEquals(200, s.pendingFutureX100)
        assertEquals(200, s.nextSlipDeductionX100)
        assertEquals(908, s.availableX100)
        assertEquals(2, s.rows.size)
        assertTrue(s.rows[1].projected)
        // no floating drift over 12 months
        val y = Rules.summarize(AppData(settings.copy(openingBalanceX100 = 0, openingMonth = YearMonth.of(2025, 9)), emptyList()), today)
        assertEquals(12 * 208, y.currentSlipX100)
        assertEquals("24.96", Rules.fmtDays(y.currentSlipX100))
    }

    @Test fun parsing() {
        assertEquals(1250, Rules.parseDaysX100("12.5"))
        assertEquals(1208, Rules.parseDaysX100("12,08"))
        assertEquals(1250, Rules.parseDaysX100("١٢٫٥"))
        assertEquals(-300, Rules.parseDaysX100("-3"))
        assertEquals(700, Rules.parseDaysX100(" 7. "))
        assertNull(Rules.parseDaysX100("12.555"))
        assertNull(Rules.parseDaysX100("abc"))
        assertNull(Rules.parseDaysX100(""))
        assertNull(Rules.parseDaysX100("."))
        assertEquals("0.5", Rules.fmtDays(50))
        assertEquals("2.08", Rules.fmtDays(208))
        assertEquals("-1.5", Rules.fmtDays(-150))
    }

    @Test fun validationErrors() {
        val v1 = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-10", "2026-10-05"), settings, emptyList(), today)
        assertTrue(v1.hasErrors)
        val v2 = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-02", "2026-10-03"), settings, emptyList(), today)
        assertTrue(v2.hasErrors) // Fri+Sat only
        val v3 = Rules.validate(draft(LeaveType.SICK, DurationKind.MULTI, "2026-10-04", "2026-10-06", manual = 130), settings, emptyList(), today)
        assertTrue(v3.hasErrors) // not multiple of 0.5
        val v4 = Rules.validate(draft(LeaveType.SICK, DurationKind.MULTI, "2026-10-04", "2026-10-05", manual = 300), settings, emptyList(), today)
        assertTrue(v4.hasErrors) // more than calendar days
        val v5 = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-13", "2026-10-18", manual = 300), settings, emptyList(), today)
        assertTrue(v5.hasErrors) // manual across 15th
        val v6 = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.ONE, "2026-10-04"), null, emptyList(), today)
        assertTrue(v6.hasErrors) // no settings
    }

    @Test fun overlaps() {
        val existing = listOf(entry("x", LeaveType.HOLIDAY, "2026-10-04", "2026-10-06", 300), entry("h", LeaveType.SICK, "2026-10-11", "2026-10-11", 50))
        assertTrue(Rules.validate(draft(LeaveType.SICK, DurationKind.ONE, "2026-10-05"), settings, existing, today).hasErrors)
        // editing same entry is not a conflict
        assertFalse(Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-04", "2026-10-07", editing = "x"), settings, existing, today).hasErrors)
        // two half days on same date allowed
        assertFalse(Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.HALF, "2026-10-11"), settings, existing, today).hasErrors)
        // full day over a half day is a conflict
        assertTrue(Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.ONE, "2026-10-11"), settings, existing, today).hasErrors)
    }

    @Test fun warningsAndSplitPlan() {
        val weekendDay = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.ONE, "2026-10-02"), settings, emptyList(), today)
        assertFalse(weekendDay.hasErrors)
        assertTrue(weekendDay.warnings.isNotEmpty())
        val split = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-13", "2026-10-18"), settings, emptyList(), today)
        assertFalse(split.hasErrors)
        assertEquals(2, split.parts.size)
        assertEquals(400, split.parts.sumOf { it.daysX100 }) // 13,14,15 | 18 ; 16,17 = Fri,Sat
        val negative = Rules.validate(draft(LeaveType.HOLIDAY, DurationKind.MULTI, "2026-10-04", "2026-11-12"), settings, emptyList(), today)
        assertTrue(negative.warnings.any { it.text.contains("سالب") })
        val sickSplit = Rules.validate(draft(LeaveType.SICK, DurationKind.MULTI, "2026-10-13", "2026-10-18"), settings, emptyList(), today)
        assertEquals(1, sickSplit.parts.size) // only Holiday is split
    }

    @Test fun settingsValidation() {
        fun v(t: String, m: YearMonth = YearMonth.of(2026, 8)) =
            Rules.validateSettings(Rules.SettingsInput("Khader", t, m, weekend), today)
        assertFalse(v("12.5").hasErrors)
        assertTrue(v("").hasErrors)
        assertTrue(v("12.555").hasErrors)
        assertTrue(v("500").hasErrors)
        assertTrue(v("10", YearMonth.of(2026, 11)).hasErrors)
        assertEquals(1250, v("12.5").settings!!.openingBalanceX100)
    }

    @Test fun normalizeSplitsImportedHoliday() {
        val list = Rules.normalize(listOf(entry("a", LeaveType.HOLIDAY, "2026-10-13", "2026-10-18", 500)), weekend)
        assertEquals(2, list.size)
        assertEquals(setOf("a", "a-1"), list.map { it.id }.toSet())
    }
}
