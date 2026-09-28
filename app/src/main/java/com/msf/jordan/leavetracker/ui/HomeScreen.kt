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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.ACCRUAL_X100
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import java.time.YearMonth

@Composable
fun HomeScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit) {
    val data = vm.data
    val today = vm.today()
    val s = Rules.summarize(data, today)
    var showRules by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = data.settings?.name.orEmpty()
        if (name.isNotBlank()) Text("أهلاً $name 👋", style = MaterialTheme.typography.titleMedium)

        // Balance card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الرصيد المتبقي (Remaining)", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showRules = true }) {
                        Icon(Icons.Filled.Info, "شرح طريقة الحساب", tint = Color.White)
                    }
                }
                Text(
                    "${Rules.fmtDays(s.availableX100)} يوم",
                    color = if (s.availableX100 < 0) Color(0xFFFCA5A5) else Color(0xFF34D399),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "بعد خصم كل الإجازات السنوية المسجلة، بما فيها المخططة.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                BalanceLine("المتوقع في سليب ${Rules.monthLabel(s.currentMonth)}", Rules.fmtDays(s.currentSlipX100))
                BalanceLine("سيُخصم في سليب ${Rules.monthLabel(s.currentMonth.plusMonths(1))}", Rules.fmtDays(s.nextSlipDeductionX100))
                if (s.pendingFutureX100 != s.nextSlipDeductionX100) {
                    BalanceLine("مجموع الخصومات المؤجلة للأشهر القادمة", Rules.fmtDays(s.pendingFutureX100))
                }
                BalanceLine("الاستحقاق الشهري", "+${Rules.fmtDays(ACCRUAL_X100)}")
            }
        }

        if (s.availableX100 < 0) {
            IssueBox(com.msf.jordan.leavetracker.logic.Issue(
                com.msf.jordan.leavetracker.logic.IssueLevel.WARNING,
                "رصيدك المتبقي سالب. راجع إجازاتك المسجلة أو تأكد من الرصيد الافتتاحي في الإعدادات.",
            ))
        }

        SectionCard("استخدامك في ${today.year}") {
            LeaveType.entries.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeDot(t)
                    Spacer(Modifier.width(10.dp))
                    Text(t.label, Modifier.weight(1f))
                    Text("${Rules.fmtDays(s.usedThisYear[t] ?: 0)} يوم", fontWeight = FontWeight.Bold)
                }
            }
            Hint("حسب تاريخ بداية الإجازة خلال السنة الحالية.")
        }

        SectionCard("الإجازات القادمة") {
            if (s.upcoming.isEmpty()) {
                Text("لا يوجد إجازات مخططة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Hint("اضغط زر «إضافة إجازة» بالأسفل لتسجيل إجازة جديدة.")
            } else {
                s.upcoming.take(5).forEach { e -> EntryRow(e, onClick = { onOpenEntry(e.id) }, openingMonth = data.settings?.openingMonth) }
            }
        }
        Spacer(Modifier.height(72.dp)) // room for the floating button
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("فهمت") } },
            title = { Text("كيف يُحسب الرصيد؟") },
            text = {
                Text(
                    "• تحصل على +2.08 يوم مع كل سليب شهري.\n" +
                        "• الإجازة السنوية من يوم 1 إلى 15 تُخصم في سليب نفس الشهر.\n" +
                        "• الإجازة السنوية من يوم 16 فما فوق تُخصم في سليب الشهر التالي.\n" +
                        "• الإجازة التي تمتد عبر يوم 15 تُقسم تلقائياً إلى جزأين.\n" +
                        "• باقي الأنواع (مرضية، شخصية، تدريب، بدون أجر، إنسانية) للتوثيق ولا تُخصم من الرصيد السنوي.\n" +
                        "• عطلة نهاية الأسبوع (افتراضياً الجمعة والسبت) لا تُحسب.\n\n" +
                        "إذا اختلف الرقم عن سليبك، حدّث الرصيد الافتتاحي من الإعدادات.",
                )
            },
        )
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
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeDot(e.type, 14)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${e.type.ar} • ${Rules.fmtDays(e.daysX100)} يوم", fontWeight = FontWeight.Bold)
            Text(Rules.rangeText(e.start, e.end), style = MaterialTheme.typography.bodySmall)
            if (e.type.affectsBalance) {
                val slip = Rules.payslipMonth(e.start)
                val covered = openingMonth != null && !slip.isAfter(openingMonth)
                Text(
                    if (covered) "محسوبة ضمن الرصيد الافتتاحي (سليب ${Rules.monthLabel(slip)})" else "تُخصم في سليب: ${Rules.monthLabel(slip)}",
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
