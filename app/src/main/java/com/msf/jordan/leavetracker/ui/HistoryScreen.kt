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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.time.YearMonth

@Composable
fun HistoryScreen(vm: AppViewModel, onOpenEntry: (String) -> Unit, onDeleted: (LeaveEntry) -> Unit) {
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<LeaveEntry?>(null) }
    val filter = LeaveType.fromKey(filterKey)
    val list = vm.data.entries
        .filter { (filter == null || it.type == filter) && Rules.matchesSearch(it, query) }
        .sortedByDescending { it.start }
    val grouped = list.groupBy { YearMonth.from(it.start) }.toSortedMap(compareByDescending { it })
    val openingMonth = vm.data.settings?.openingMonth

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "search") {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, tr("مسح", "Clear")) }
                    }
                },
                label = { Text(tr("بحث", "Search")) },
                placeholder = { Text(tr("نوع، تاريخ (05/08)، شهر، ملاحظة…", "Type, date (05/08), month, note…")) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(tr("الكل", "All"), null, filter == null) { filterKey = null }
                LeaveType.entries.forEach { t -> FilterPill(t.title, t.color(), filter == t) { filterKey = t.key } }
            }
            Text(
                tr("${list.size} سجل • المجموع ${daysText(list.sumOf { it.daysX100 })}", "${list.size} records • total ${daysText(list.sumOf { it.daysX100 })}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hint(tr("اضغط على أي إجازة لتعديلها، أو أيقونة الحذف لحذفها.", "Tap a leave to edit it, or the bin icon to delete it."))
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (vm.data.entries.isEmpty()) tr("لا يوجد سجلات بعد.", "No records yet.") else tr("لا توجد نتائج.", "No results."),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        grouped.forEach { (month, entries) ->
            item(key = "h-$month") {
                MonthHeader(month, entries)
            }
            items(entries, key = { it.id }) { e ->
                EntryRow(
                    e,
                    onClick = { onOpenEntry(e.id) },
                    openingMonth = openingMonth,
                    trailing = {
                        Row {
                            IconButton(onClick = { onOpenEntry(e.id) }) { Icon(Icons.Filled.Edit, tr("تعديل", "Edit")) }
                            IconButton(onClick = { toDelete = e }) {
                                Icon(Icons.Filled.Delete, tr("حذف", "Delete"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(90.dp)) }
    }

    val pending = toDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("حذف الإجازة؟", "Delete this leave?")) },
            text = {
                Text(
                    "${pending.type.title} • ${Rules.rangeText(pending.start, pending.end)} • ${daysText(pending.daysX100)}\n\n" +
                        tr("يمكنك التراجع مباشرة بعد الحذف.", "You can undo right after deleting."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    vm.delete(pending.id)?.let(onDeleted)
                }) { Text(tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, entries: List<LeaveEntry>) {
    val totals = Rules.totalsByType(entries)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Tr.monthLabel(month), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                tr("المجموع ", "Total ") + daysText(entries.sumOf { it.daysX100 }),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LeaveType.entries.filter { (totals[it] ?: 0) > 0 }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeDot(t, 10)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${t.title} ${Rules.fmtDays(totals[t] ?: 0)}",
                        fontSize = if (t == LeaveType.HOLIDAY) 14.sp else 12.sp,
                        fontWeight = if (t == LeaveType.HOLIDAY) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
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
