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
                BalanceLine(tr("خصومات قادمة مسجلة", "Recorded future deductions"), Rules.fmtSlip(s.pendingFutureX100))
                BalanceLine(tr("المكتسب شهرياً", "Acquired every month"), "+" + Rules.fmtSlip(ACCRUAL_X100))
            }
        }

        val st = data.settings
        if (st != null && !st.hasSlipDetails) {
            // Data from the first version: only one number was saved, which is ambiguous.
            IssueBox(Issue(IssueLevel.WARNING, tr(
                "مهم: حدّث بيانات السليب في الإعدادات. أصبح التطبيق يطلب «الرصيد السابق» و«المحتسب هذا الشهر» من السليب ليطابق حسابه سليبك تماماً.",
                "Important: update your payslip details in Settings. The app now asks for «Previous balance» and «Accounted this month» so it matches your payslip exactly.",
            )))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(tr("تحديث بيانات السليب", "Update payslip details")) }
        }
        if (st != null && st.hasSlipDetails && s.recordedOnOpeningSlipX100 != st.slipAccountedX100) {
            IssueBox(Issue(IssueLevel.INFO, tr(
                "للتحقق: الإجازات السنوية المسجلة في التطبيق لسليب ${Tr.monthLabel(st.openingMonth)} = ${Rules.fmtSlip(s.recordedOnOpeningSlipX100)}، والسليب يقول ${Rules.fmtSlip(st.slipAccountedX100 ?: 0)}. الحساب يعتمد رقم السليب، فلا مشكلة إن لم تسجّل إجازات ذلك الشهر.",
                "Check: holidays recorded for the ${Tr.monthLabel(st.openingMonth)} payslip = ${Rules.fmtSlip(s.recordedOnOpeningSlipX100)}, the payslip says ${Rules.fmtSlip(st.slipAccountedX100 ?: 0)}. The payslip number is used, so it's fine if you didn't record that month.",
            )))
        }

        // Payslips: previous (issued) + this month + next month (expected)
        val prevRow = s.row(s.currentMonth.minusMonths(1))
        val curRow = s.row(s.currentMonth)
        val nextRow = s.row(s.currentMonth.plusMonths(1))
        if (prevRow != null || curRow != null || nextRow != null) {
            SectionCard(
                tr("سليبات الراتب (الإجازة السنوية)", "Payslips (paid leave)"),
                help = tr(
                    "نفس مربع Paid leave في سليبك.\n\n• الرصيد السابق: المتبقي من السليب الذي قبله.\n• المحتسب هذا الشهر: الإجازات السنوية التي تبدأ من 16 الشهر الماضي حتى 15 هذا الشهر.\n• المكتسب: 2.08 ثابت.\n• المتبقي = السابق − المحتسب + المكتسب.\n\n«صادر» = سليب استلمته. «متوقع» = حسب إجازاتك المسجلة.",
                    "Same as the Paid leave box on your payslip.\n\n• Previous balance: Remaining of the payslip before.\n• Accounted: holidays starting 16th of last month to 15th of this month.\n• Acquired: fixed 2.08.\n• Remaining = Previous − Accounted + Acquired.\n\n«Issued» = a payslip you received. «Expected» = from your recorded leaves.",
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (prevRow != null) SlipCard(tr("الشهر السابق: ", "Last month: ") + Tr.monthLabel(prevRow.month), tr("صادر", "Issued"), prevRow, note = Rules.slipTimeline(data.entries, prevRow.month))
                    if (curRow != null) SlipCard(tr("هذا الشهر: ", "This month: ") + Tr.monthLabel(curRow.month), tr("متوقع", "Expected"), curRow.copy(projected = true), highlight = true, note = Rules.slipTimeline(data.entries, curRow.month))
                    if (nextRow != null) SlipCard(tr("الشهر القادم: ", "Next month: ") + Tr.monthLabel(nextRow.month), tr("متوقع", "Expected"), nextRow, note = Rules.slipTimeline(data.entries, nextRow.month))
                }
            }
        }

        if (s.availableX100 < 0) {
            IssueBox(Issue(IssueLevel.WARNING, tr(
                "رصيدك سالب. راجع الإجازات المسجلة أو الرصيد الافتتاحي في الإعدادات.",
                "Your balance is negative. Check your leaves or the opening balance in Settings.",
            )))
        }

        SectionCard(
            tr("مجموع إجازاتك في ${today.year}", "Your leaves in ${today.year}"),
            help = tr(
                "مجموع كل نوع حسب تاريخ أول يوم في الإجازة. الإجازة السنوية فقط تُخصم من الرصيد، وباقي الأنواع للتوثيق والمجاميع.",
                "Totals per type by the leave's first day. Only Holiday is deducted from the balance; other types are for records and totals.",
            ),
        ) {
            TypeTotalRows(s.usedThisYear)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("المجموع الكلي", "Grand total"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(daysText(s.totalThisYearX100), fontWeight = FontWeight.Bold)
            }
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
                Text(tr("اضغط «إضافة إجازة» بالأسفل للإدخال اليدوي أو لتصوير نموذج الطلب.", "Tap «Add leave» below to type it in or scan the request form."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
