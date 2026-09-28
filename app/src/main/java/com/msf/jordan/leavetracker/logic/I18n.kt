package com.msf.jordan.leavetracker.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Minimal two-language support (Arabic / English) usable from pure Kotlin.
 * The UI sets [arabic] before composing; every text goes through [tr].
 */
object Tr {
    @Volatile
    var arabic: Boolean = true

    // Jordanian (Levantine) month names — used in Jordan instead of «سبتمبر/أكتوبر…».
    private val arMonths = listOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول",
    )
    private val arDays = mapOf(
        DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء",
        DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
        DayOfWeek.SATURDAY to "السبت",
        DayOfWeek.SUNDAY to "الأحد",
    )

    fun monthName(m: YearMonth): String =
        if (arabic) arMonths[m.monthValue - 1] else m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** e.g. «أيلول 2026 (9)» / «September 2026». */
    fun monthLabel(m: YearMonth): String =
        if (arabic) "${arMonths[m.monthValue - 1]} ${m.year}" else "${monthName(m)} ${m.year}"

    fun dayName(d: DayOfWeek): String =
        if (arabic) arDays.getValue(d) else d.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun dayShort(d: DayOfWeek): String =
        if (arabic) arDays.getValue(d).removePrefix("ال").take(3) else d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun dayName(d: LocalDate): String = dayName(d.dayOfWeek)
}

fun tr(ar: String, en: String): String = if (Tr.arabic) ar else en
