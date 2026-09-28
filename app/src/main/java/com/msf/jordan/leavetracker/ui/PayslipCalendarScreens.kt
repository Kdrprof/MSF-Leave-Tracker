package com.msf.jordan.leavetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ---------------- Payslip (Holiday only) ----------------

@Composable
fun PayslipScreen(vm: AppViewModel) {
    val s = Rules.summarize(vm.data, vm.today())
    val settings = vm.data.settings
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item(key = "head") {
            Spacer(Modifier.height(12.dp))
            Text(tr("الإجازة السنوية في سليب الراتب", "Paid leave on the payslip"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint(tr(
                "نفس خانات السليب (Paid leave). الإجازة السنوية (Holiday) فقط تظهر هنا. طابق خانة «المتبقي» مع سليبك.",
                "Same boxes as the payslip (Paid leave). Only Holiday appears here. Compare «Remaining» with your payslip.",
            ))
            if (settings != null) {
                Hint(tr(
                    "البداية: ${Rules.fmtSlip(settings.openingBalanceX100)} (خانة Remaining في سليب ${Tr.monthLabel(settings.openingMonth)}).",
                    "Start: ${Rules.fmtSlip(settings.openingBalanceX100)} («Remaining» on the ${Tr.monthLabel(settings.openingMonth)} payslip).",
                ))
            }
            Spacer(Modifier.height(12.dp))
            SlipRow(
                tr("الشهر", "Month"),
                tr("السابق\nPrevious", "Previous\nbalance"),
                tr("المحتسب\nAccounted", "Accounted\nthis month"),
                tr("المكتسب\nAcquired", "Acquired\nthis month"),
                tr("المتبقي\nRemaining", "Remaining"),
                header = true, highlight = false, projected = false,
            )
            HorizontalDivider()
        }
        if (s.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("لا يوجد سليب بعد شهر الرصيد الافتتاحي حتى الآن.", "No payslip after the opening month yet."),
                    Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(s.rows.reversed(), key = { it.month.toString() }) { r ->
            SlipRow(
                Tr.monthLabel(r.month) + if (r.projected) tr("\n(متوقع)", "\n(expected)") else "",
                Rules.fmtSlip(r.previousX100),
                Rules.fmtSlip(r.accountedX100),
                Rules.fmtSlip(r.acquiredX100),
                Rules.fmtSlip(r.remainingX100),
                header = false,
                highlight = r.month == s.currentMonth,
                projected = r.projected,
            )
            HorizontalDivider()
        }
        item(key = "foot") {
            Spacer(Modifier.height(8.dp))
            Hint(tr(
                "المتبقي = السابق − المحتسب + المكتسب (2.08). الأشهر «المتوقعة» فيها إجازات مخططة.",
                "Remaining = Previous − Accounted + Acquired (2.08). «Expected» months contain planned leaves.",
            ))
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SlipRow(month: String, prev: String, acc: String, acq: String, rem: String, header: Boolean, highlight: Boolean, projected: Boolean) {
    val w = if (header || highlight) FontWeight.Bold else FontWeight.Normal
    val size = if (header) 11.sp else 14.sp
    val alpha = if (projected) 0.6f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        Text(month, Modifier.weight(1.5f), fontWeight = w, fontSize = if (header) 11.sp else 13.sp, color = c)
        Text(prev, Modifier.weight(1f), fontWeight = w, fontSize = size, textAlign = TextAlign.Center, color = c)
        Text(acc, Modifier.weight(1f), fontWeight = w, fontSize = size, textAlign = TextAlign.Center,
            color = if (header) c else MaterialTheme.colorScheme.error.copy(alpha = alpha))
        Text(acq, Modifier.weight(1f), fontWeight = w, fontSize = size, textAlign = TextAlign.Center,
            color = if (header) c else Color(0xFF10B981).copy(alpha = alpha))
        Text(rem, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = size, textAlign = TextAlign.Center, color = c)
    }
}

// ---------------- Year calendar ----------------

@Composable
fun CalendarScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit) {
    val today = vm.today()
    var year by rememberSaveable { mutableStateOf(today.year) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val weekend = vm.data.settings?.weekend ?: Rules.DEFAULT_WEEKEND
    val marks = Rules.calendarMarks(vm.data.entries, year, weekend)
    val yearEntries = vm.data.entries.filter { it.start.year == year || it.end.year == year }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item(key = "top") {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { year-- }) { Text(tr("السابقة", "Prev")) }
                Text("$year", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { year++ }) { Text(tr("التالية", "Next")) }
            }
            Spacer(Modifier.height(8.dp))
            // Legend with the year's totals per type
            val totals = Rules.totalsByType(vm.data.entries.filter { it.start.year == year })
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(tr("المرجع (مجموع السنة)", "Legend (year totals)"), fontWeight = FontWeight.Bold)
                LeaveType.entries.chunked(2).forEach { row ->
                    Row {
                        row.forEach { t ->
                            Row(Modifier.weight(1f).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                TypeDot(t, 12)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${t.title}: ${Rules.fmtDays(totals[t] ?: 0)}",
                                    fontSize = if (t == LeaveType.HOLIDAY) 14.sp else 12.sp,
                                    fontWeight = if (t == LeaveType.HOLIDAY) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                Hint(tr("اضغط على أي يوم ملوّن لرؤية تفاصيله.", "Tap a colored day to see its details."))
            }
            Spacer(Modifier.height(8.dp))
        }
        items((1..12).toList(), key = { "m-$year-$it" }) { m ->
            MonthGrid(YearMonth.of(year, m), marks, weekend, today) { selected = it }
        }
        item(key = "end") {
            if (yearEntries.isEmpty()) {
                Text(tr("لا يوجد إجازات في $year.", "No leaves in $year."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    val day = selected
    if (day != null) {
        val list = marks[day].orEmpty().distinctBy { it.id }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${Rules.fmtDate(day)} • ${Tr.dayName(day)}") },
            text = {
                Column {
                    if (list.isEmpty()) Text(tr("لا يوجد إجازة في هذا اليوم.", "No leave on this day."))
                    list.forEach { e ->
                        EntryRow(e, onClick = {
                            selected = null
                            onOpenEntry(e.id)
                        }, openingMonth = vm.data.settings?.openingMonth)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(tr("إغلاق", "Close")) } },
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    marks: Map<LocalDate, List<LeaveEntry>>,
    weekend: Set<DayOfWeek>,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
) {
    // Week starts on Sunday (as in Jordan).
    val order = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    val first = month.atDay(1)
    val lead = order.indexOf(first.dayOfWeek)
    val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(Tr.monthLabel(month), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        Row {
            order.forEach { d ->
                Text(
                    Tr.dayShort(d),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = if (d in weekend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 1.dp)) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    if (d == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val es = marks[d].orEmpty()
                        val type = es.firstOrNull()?.type
                        val half = es.size == 1 && es[0].start == es[0].end && es[0].daysX100 == 50
                        val bg = when {
                            type == null -> Color.Transparent
                            half -> type.color().copy(alpha = 0.45f)
                            else -> type.color()
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(bg, RoundedCornerShape(6.dp))
                                .border(
                                    if (d == today) 2.dp else 0.dp,
                                    if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable(enabled = es.isNotEmpty()) { onDay(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}" + if (half) "½" else "",
                                fontSize = 11.sp,
                                fontWeight = if (type != null) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    type != null -> Color.White
                                    d.dayOfWeek in weekend -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
