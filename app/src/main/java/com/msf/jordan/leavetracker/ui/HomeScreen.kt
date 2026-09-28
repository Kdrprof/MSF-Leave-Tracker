package com.msf.jordan.leavetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.ACCRUAL_X100
import com.msf.jordan.leavetracker.logic.Issue
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.time.YearMonth

@Composable
fun HomeScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit, onOpenSettings: () -> Unit) {
    val data = vm.data
    val today = vm.today()
    val s = Rules.summarize(data, today)
    val prefs = vm.homePrefs
    val month = s.currentMonth
    var showRules by remember { mutableStateOf(false) }
    val color = when {
        s.monthEndX100 < 0 -> Color(0xFFFCA5A5)
        s.monthEndX100 < 300 -> Color(0xFFFCD34D)
        else -> BalanceGreen
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- The main number: balance at the end of the current month ----
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Navy)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("رصيدك المتبقي", "Your remaining balance"),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showRules = true }) {
                        Icon(Icons.Filled.Info, tr("كيف يُحسب؟", "How is it calculated?"), tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Rules.fmtDays(s.monthEndX100), color = color, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("يوم", "days"), color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    tr("حتى نهاية ${Tr.monthLabel(month)}", "Until the end of ${Tr.monthLabel(month)}"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr("(يشمل إجازاتك السنوية المسجلة حتى نهاية ${Tr.monthName(month)})", "(includes your holidays recorded up to the end of ${Tr.monthName(month)})"),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                if (s.laterScheduledX100 > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("إجازات مجدولة بعد ${Tr.monthName(month)}: ${daysText(s.laterScheduledX100)} (غير مخصومة هنا)", "Holidays planned after ${Tr.monthName(month)}: ${daysText(s.laterScheduledX100)} (not deducted here)"),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        val st = data.settings
        if (st != null && !st.hasSlipDetails) {
            IssueBox(Issue(IssueLevel.WARNING, tr(
                "حدّث بيانات آخر سليب من الإعدادات ليطابق الحساب سليبك.",
                "Update your latest payslip in Settings so the numbers match.",
            )))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(tr("تحديث بيانات السليب", "Update payslip details")) }
        }
        if (s.monthEndX100 < 0) {
            IssueBox(Issue(IssueLevel.WARNING, tr("رصيدك سالب. راجع إجازاتك المسجلة.", "Your balance is negative. Check your leaves.")))
        }

        // ---- Payslips ----
        val prevRow = s.row(month.minusMonths(1))
        if (prefs.previousSlip && prevRow != null) {
            SlipCard(
                tr("آخر سليب: ", "Last payslip: ") + Tr.monthLabel(prevRow.month), tr("صادر", "Issued"), prevRow,
                note = if (prefs.slipDetails) Rules.slipTimeline(data.entries, prevRow.month) else null,
            )
        }
        if (prefs.upcomingSlips) {
            s.row(month)?.let {
                SlipCard(tr("سليب ", "Payslip ") + Tr.monthLabel(it.month), tr("متوقع", "Expected"), it.copy(projected = true), highlight = true,
                    note = if (prefs.slipDetails) Rules.slipTimeline(data.entries, it.month) else null)
            }
            s.row(month.plusMonths(1))?.let {
                SlipCard(tr("سليب ", "Payslip ") + Tr.monthLabel(it.month), tr("متوقع", "Expected"), it,
                    note = if (prefs.slipDetails) Rules.slipTimeline(data.entries, it.month) else null)
            }
        }

        // ---- Optional sections (Settings → Home screen) ----
        if (prefs.totals) {
            SectionCard(tr("مجموع إجازاتك في ${today.year}", "Your leaves in ${today.year}")) {
                TypeTotalRows(s.usedThisYear, hideZero = true)
                if (s.totalThisYearX100 == 0) Text(tr("لا يوجد إجازات بعد.", "No leaves yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(tr("المجموع", "Total"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(daysText(s.totalThisYearX100), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (prefs.thisMonth) {
            val list = data.entries.filter { YearMonth.from(it.start) == month }
            SectionCard(tr("إجازات ${Tr.monthLabel(month)}", "Leaves in ${Tr.monthLabel(month)}")) {
                if (list.isEmpty()) Text(tr("لا يوجد.", "None."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else TypeTotalRows(Rules.totalsByType(list), hideZero = true)
            }
        }
        if (prefs.upcoming) {
            SectionCard(tr("الإجازات القادمة", "Upcoming leaves")) {
                if (s.upcoming.isEmpty()) Text(tr("لا يوجد.", "None."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else s.upcoming.take(5).forEach { e -> EntryRow(e, onClick = { onOpenEntry(e.id) }, openingMonth = st?.openingMonth) }
            }
        }

        TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("⚙️ اختر ما يظهر هنا", "⚙️ Choose what shows here"))
        }
        Spacer(Modifier.height(72.dp))
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text(tr("فهمت", "Got it")) } },
            title = { Text(tr("كيف يُحسب الرصيد؟", "How is it calculated?")) },
            text = {
                Text(
                    tr(
                        "الرقم = رصيدك المتوقع في نهاية الشهر الحالي:\n\n" +
                            "• يبدأ من «المتبقي» في آخر سليب.\n" +
                            "• يضيف 2.08 عن كل شهر.\n" +
                            "• يخصم الإجازات السنوية المسجلة حتى نهاية هذا الشهر فقط.\n" +
                            "• الإجازات المجدولة للأشهر القادمة لا تُخصم هنا.",
                        "The number = your expected balance at the end of this month:\n\n" +
                            "• Starts from «Remaining» on your latest payslip.\n" +
                            "• Adds 2.08 for each month.\n" +
                            "• Deducts holidays recorded up to the end of this month only.\n" +
                            "• Holidays planned in later months are not deducted here.",
                    ),
                )
            },
        )
    }
}

@Composable
fun TypeTotalRows(totals: Map<LeaveType, Int>, hideZero: Boolean = false) {
    LeaveType.entries.filter { !hideZero || (totals[it] ?: 0) != 0 }.forEach { t ->
        val v = totals[t] ?: 0
        val big = t == LeaveType.HOLIDAY
        Row(Modifier.fillMaxWidth().padding(vertical = if (big) 7.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeDot(t, if (big) 16 else 12)
            Spacer(Modifier.width(10.dp))
            Text(
                t.label,
                Modifier.weight(1f),
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
                color = if (big) t.color() else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                daysText(v),
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (big) t.color() else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun EntryRow(
    e: LeaveEntry,
    onClick: () -> Unit,
    openingMonth: YearMonth? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val big = e.type == LeaveType.HOLIDAY
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeDot(e.type, if (big) 16 else 14)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${e.type.title} • ${daysText(e.daysX100)}",
                fontWeight = FontWeight.Bold,
                fontSize = if (big) 17.sp else 15.sp,
                color = if (big) e.type.color() else MaterialTheme.colorScheme.onSurface,
            )
            Text(Rules.rangeText(e.start, e.end), style = MaterialTheme.typography.bodySmall)
            if (e.type.onPayslip) {
                val slip = Rules.payslipMonth(e.start)
                val covered = openingMonth != null && !slip.isAfter(openingMonth)
                Text(
                    if (covered) tr("ضمن الرصيد الافتتاحي (سليب ${Tr.monthLabel(slip)})", "Inside opening balance (${Tr.monthLabel(slip)} payslip)")
                    else tr("تُخصم في سليب ${Tr.monthLabel(slip)}", "Deducted on ${Tr.monthLabel(slip)} payslip"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (e.note.isNotBlank()) {
                Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        if (trailing != null) trailing()
    }
}
