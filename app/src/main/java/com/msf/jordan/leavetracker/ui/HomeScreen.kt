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
fun HomeScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit) {
    val data = vm.data
    val today = vm.today()
    val s = Rules.summarize(data, today)
    var showRules by remember { mutableStateOf(false) }
    val remainingColor = when {
        s.availableX100 < 0 -> Color(0xFFFCA5A5)
        s.availableX100 < 300 -> Color(0xFFFCD34D)
        else -> BalanceGreen
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = data.settings?.name.orEmpty()
        if (name.isNotBlank()) Text(tr("أهلاً $name 👋", "Hi $name 👋"), style = MaterialTheme.typography.titleMedium)

        // Remaining balance — the main number of the app
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Navy)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("رصيد الإجازة السنوية المتبقي", "Remaining annual leave"),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showRules = true }) {
                        Icon(Icons.Filled.Info, tr("شرح طريقة الحساب", "How it is calculated"), tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Rules.fmtDays(s.availableX100), color = remainingColor, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("يوم", "days"), color = remainingColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    tr("حتى اليوم، بعد خصم كل الإجازات السنوية المسجلة.", "As of today, after every recorded holiday."),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                BalanceLine(tr("المتوقع في سليب ${Tr.monthLabel(s.currentMonth)}", "Expected on ${Tr.monthLabel(s.currentMonth)} payslip"), Rules.fmtSlip(s.currentSlipX100))
                BalanceLine(tr("سيُخصم في سليب ${Tr.monthLabel(s.currentMonth.plusMonths(1))}", "To deduct on ${Tr.monthLabel(s.currentMonth.plusMonths(1))} payslip"), Rules.fmtSlip(s.nextSlipDeductionX100))
                if (s.pendingFutureX100 != s.nextSlipDeductionX100) {
                    BalanceLine(tr("مجموع الخصومات المؤجلة", "All future deductions"), Rules.fmtSlip(s.pendingFutureX100))
                }
                BalanceLine(tr("المكتسب شهرياً", "Acquired every month"), "+" + Rules.fmtSlip(ACCRUAL_X100))
            }
        }

        if (s.availableX100 < 0) {
            IssueBox(Issue(IssueLevel.WARNING, tr(
                "رصيدك سالب. راجع الإجازات المسجلة أو الرصيد الافتتاحي في الإعدادات.",
                "Your balance is negative. Check your leaves or the opening balance in Settings.",
            )))
        }

        SectionCard(tr("مجموع إجازاتك في ${today.year}", "Your leaves in ${today.year}")) {
            TypeTotalRows(s.usedThisYear)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("المجموع الكلي", "Grand total"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(daysText(s.totalThisYearX100), fontWeight = FontWeight.Bold)
            }
            Hint(tr("حسب تاريخ أول يوم في الإجازة. الإجازة السنوية فقط تُخصم من الرصيد.", "By the leave's first day. Only Holiday is deducted from the balance."))
        }

        SectionCard(tr("هذا الشهر: ${Tr.monthLabel(YearMonth.from(today))}", "This month: ${Tr.monthLabel(YearMonth.from(today))}")) {
            val month = data.entries.filter { YearMonth.from(it.start) == YearMonth.from(today) }
            if (month.isEmpty()) {
                Text(tr("لا يوجد إجازات هذا الشهر.", "No leaves this month."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TypeTotalRows(Rules.totalsByType(month), hideZero = true)
            }
        }

        SectionCard(tr("الإجازات القادمة", "Upcoming leaves")) {
            if (s.upcoming.isEmpty()) {
                Text(tr("لا يوجد إجازات مخططة.", "No planned leaves."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Hint(tr("اضغط «إضافة إجازة» بالأسفل للإدخال اليدوي أو لتصوير نموذج الطلب.", "Tap «Add leave» below to type it in or scan the request form."))
            } else {
                s.upcoming.take(5).forEach { e -> EntryRow(e, onClick = { onOpenEntry(e.id) }, openingMonth = data.settings?.openingMonth) }
            }
        }
        Spacer(Modifier.height(80.dp))
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text(tr("فهمت", "Got it")) } },
            title = { Text(tr("كيف يُحسب الرصيد؟", "How is the balance calculated?")) },
            text = {
                Text(
                    tr(
                        "• كل سليب يضيف +2.08 يوم.\n" +
                            "• الإجازة السنوية التي يبدأ أول يوم فيها بين 1 و15 تُخصم في سليب نفس الشهر.\n" +
                            "• إذا بدأت يوم 16 أو بعده تُخصم في سليب الشهر التالي.\n" +
                            "• الإجازة تُحسب كاملة في سليب واحد ولا تُقسم.\n" +
                            "• باقي الأنواع تُحسب في المجاميع والسجل ولا تظهر في السليب.\n\n" +
                            "إذا اختلف الرقم عن سليبك، عدّل الرصيد الافتتاحي من الإعدادات.",
                        "• Every payslip adds +2.08 days.\n" +
                            "• A Holiday whose first day is 1–15 is deducted on the same month's payslip.\n" +
                            "• If it starts on the 16th or later, it goes to next month's payslip.\n" +
                            "• A leave is counted whole on one payslip — never split.\n" +
                            "• Other types are counted in totals and history, not on the payslip.\n\n" +
                            "If the number differs from your payslip, edit the opening balance in Settings.",
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
private fun BalanceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
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
