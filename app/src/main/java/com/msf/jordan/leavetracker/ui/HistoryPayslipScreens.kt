package com.msf.jordan.leavetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules

@Composable
fun HistoryScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit, onDeleted: (LeaveEntry) -> Unit) {
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<LeaveEntry?>(null) }
    val filter = LeaveType.fromKey(filterKey)
    val list = vm.data.entries
        .filter { filter == null || it.type == filter }
        .sortedByDescending { it.start }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill("الكل", null, filter == null) { filterKey = null }
            LeaveType.entries.forEach { t -> FilterPill(t.ar, t.color(), filter == t) { filterKey = t.key } }
        }
        val total = list.sumOf { it.daysX100 }
        Text(
            "${list.size} سجل • المجموع ${Rules.fmtDays(total)} يوم",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Hint("اضغط على أي إجازة لتعديلها، أو على أيقونة الحذف لحذفها.", Modifier.padding(horizontal = 16.dp))
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("لا يوجد سجلات بعد.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("سجّل أول إجازة من زر «إضافة إجازة».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(list, key = { it.id }) { e ->
                    EntryRow(
                        e,
                        onClick = { onOpenEntry(e.id) },
                        openingMonth = vm.data.settings?.openingMonth,
                        trailing = {
                            IconButton(onClick = { toDelete = e }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    val pending = toDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف الإجازة؟") },
            text = { Text("${pending.type.ar} • ${Rules.rangeText(pending.start, pending.end)} • ${Rules.fmtDays(pending.daysX100)} يوم\n\nيمكنك التراجع مباشرة بعد الحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    vm.delete(pending.id)?.let(onDeleted)
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun FilterPill(text: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun PayslipScreen(vm: AppViewModel) {
    val s = Rules.summarize(vm.data, vm.today())
    val settings = vm.data.settings
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("كشف السليب الشهري", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint("طابق عمود «الرصيد» مع سليب راتبك. إذا اختلف الرقم، حدّث الرصيد الافتتاحي وشهره من الإعدادات.")
            if (settings != null) {
                Hint("الرصيد الافتتاحي: ${Rules.fmtDays(settings.openingBalanceX100)} يوم (سليب ${Rules.monthLabel(settings.openingMonth)}).")
            }
            Spacer(Modifier.height(12.dp))
            PayslipRow("الشهر", "+ مكتسب", "− مخصوم", "الرصيد", header = true, highlight = false)
            HorizontalDivider()
        }
        if (s.rows.isEmpty()) {
            item {
                Text(
                    "لا يوجد أشهر بعد شهر الرصيد الافتتاحي حتى الآن.",
                    Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(s.rows.reversed(), key = { it.month.toString() }) { r ->
            PayslipRow(
                Rules.monthLabel(r.month) + if (r.projected) "\n(متوقع)" else "",
                "+" + Rules.fmtDays(r.accruedX100),
                if (r.deductedX100 == 0) "0" else "−" + Rules.fmtDays(r.deductedX100),
                Rules.fmtDays(r.closingX100),
                header = false,
                highlight = r.month == s.currentMonth,
            )
            HorizontalDivider()
        }
        item {
            Spacer(Modifier.height(8.dp))
            Hint("الأشهر «المتوقعة» تشمل استحقاق +2.08 المستقبلي. أما «الرصيد المتبقي» في الرئيسية فلا يحسب أي استحقاق مستقبلي (أكثر أماناً).")
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PayslipRow(month: String, acc: String, ded: String, bal: String, header: Boolean, highlight: Boolean) {
    val w = if (header || highlight) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(month, Modifier.weight(1.6f), fontWeight = w, style = MaterialTheme.typography.bodyMedium)
        Text(acc, Modifier.weight(1f), fontWeight = w, color = if (header) Color.Unspecified else Color(0xFF10B981))
        Text(ded, Modifier.weight(1f), fontWeight = w, color = if (header) Color.Unspecified else MaterialTheme.colorScheme.error)
        Text(bal, Modifier.weight(1f), fontWeight = FontWeight.Bold)
    }
}
