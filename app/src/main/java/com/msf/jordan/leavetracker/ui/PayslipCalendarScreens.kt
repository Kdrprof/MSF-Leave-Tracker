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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("الإجازة السنوية في سليب الراتب", "Paid leave on the payslip"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                HelpIcon(
                    tr("كيف تُقرأ هذه الصفحة؟", "How to read this page"),
                    tr(
                        "كل بطاقة = سليب شهر، بنفس خانات مربع Paid leave.\n\nالمتبقي = السابق − المحتسب + المكتسب (2.08).\n\nالمحتسب = الإجازات السنوية التي يبدأ أول يوم فيها من 16 الشهر الماضي حتى 15 هذا الشهر، وتُحسب كاملة بدون تقسيم.\n\nأول بطاقة (المرجع) منقولة من سليبك الورقي كما أدخلتها في الإعدادات. الإجازات الأخرى (مرضية، شخصية…) لا تظهر هنا.",
                        "Each card = one month's payslip, same boxes as Paid leave.\n\nRemaining = Previous − Accounted + Acquired (2.08).\n\nAccounted = holidays whose first day is from the 16th of last month to the 15th of this month, counted whole.\n\nThe reference card is copied from your paper payslip (Settings). Other leave types never appear here.",
                    ),
                )
            }
        }
        if (settings != null && !settings.hasSlipDetails) {
            item(key = "upgrade") {
                IssueBox(com.msf.jordan.leavetracker.logic.Issue(com.msf.jordan.leavetracker.logic.IssueLevel.WARNING, tr(
                    "حدّث بيانات السليب من الإعدادات (الرصيد السابق + المحتسب) حتى تطابق الأرقام سليبك.",
                    "Update the payslip details in Settings (Previous + Accounted) so the numbers match your payslip.",
                )))
            }
        }
        if (s.rows.isEmpty()) {
            item(key = "empty") {
                Text(tr("لا يوجد بيانات بعد.", "No data yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(s.rows.reversed(), key = { it.month.toString() }) { r ->
            val isRef = settings != null && r.month == settings.openingMonth
            val badge = when {
                isRef -> tr("مرجع من سليبك", "From your payslip")
                r.month.isAfter(s.currentMonth.minusMonths(1)) -> tr("متوقع", "Expected")
                else -> tr("صادر", "Issued")
            }
            SlipCard(
                Tr.monthLabel(r.month),
                badge,
                if (r.month.isAfter(s.currentMonth.minusMonths(1))) r.copy(projected = true) else r,
                highlight = r.month == s.currentMonth,
                note = Rules.slipTimeline(vm.data.entries, r.month) +
                    if (isRef && settings!!.hasSlipDetails && s.recordedOnOpeningSlipX100 != settings.slipAccountedX100) tr(
                        "\n⚠️ رقم «المحتسب» منقول من سليبك (${Rules.fmtSlip(settings.slipAccountedX100 ?: 0)})، والمسجّل في التطبيق لهذه الفترة = ${Rules.fmtSlip(s.recordedOnOpeningSlipX100)}",
                        "\n⚠️ «Accounted» is copied from your payslip (${Rules.fmtSlip(settings.slipAccountedX100 ?: 0)}); recorded in the app for this period = ${Rules.fmtSlip(s.recordedOnOpeningSlipX100)}",
                    ) else "",
            )
        }
        item(key = "foot") { Spacer(Modifier.height(80.dp)) }
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
                FieldHeader(
                    tr("المرجع (مجموع السنة)", "Legend (year totals)"),
                    tr("كل لون = نوع إجازة، والرقم = مجموع أيامه في هذه السنة. نصف اليوم يظهر بلون أفتح مع ½. اضغط على أي يوم ملوّن لرؤية الإجازة وتعديلها.",
                        "Each color = a leave type; the number = its total days this year. Half days are lighter with ½. Tap a colored day to see and edit the leave."),
                )
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
