package com.msf.jordan.leavetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.logic.Issue
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.LeaveDraft
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.MAX_NOTE_LENGTH
import com.msf.jordan.leavetracker.logic.ParsedForm
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.tr
import java.time.LocalDate

private enum class PickTarget { START, END }

/**
 * Add / edit a leave. [scan] pre-fills the fields from a photographed request form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveEditorScreen(
    vm: AppViewModel,
    entryId: String?,
    scan: ParsedForm?,
    snackbar: SnackbarHostState,
    onClose: (saved: Boolean) -> Unit,
) {
    val existing = remember(entryId) { entryId?.let { id -> vm.data.entries.firstOrNull { it.id == id } } }
    val today = vm.today()
    val settings = vm.data.settings
    val weekend = settings?.weekend ?: Rules.DEFAULT_WEEKEND

    // Initial values (to detect unsaved changes)
    val init = remember(entryId, scan) {
        val t = existing?.type ?: scan?.type ?: LeaveType.HOLIDAY
        val s = existing?.start ?: scan?.start ?: today
        val e = existing?.end ?: scan?.end?.takeIf { !it.isBefore(s) } ?: s
        val d = existing?.daysX100 ?: scan?.daysX100 ?: Rules.suggestedDaysX100(s, e, weekend)
        listOf(t.key, s.toEpochDay().toString(), e.toEpochDay().toString(), Rules.fmtDays(d), existing?.note.orEmpty())
    }

    var typeKey by rememberSaveable { mutableStateOf(init[0]) }
    var startDay by rememberSaveable { mutableStateOf(init[1].toLong()) }
    var endDay by rememberSaveable { mutableStateOf(init[2].toLong()) }
    var daysInput by rememberSaveable { mutableStateOf(init[3]) }
    /** false = the days field follows the dates automatically. */
    var daysTyped by rememberSaveable { mutableStateOf(existing != null || scan?.daysX100 != null) }
    var note by rememberSaveable { mutableStateOf(init[4]) }

    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var confirmWarnings by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    val type = LeaveType.fromKey(typeKey) ?: LeaveType.HOLIDAY
    val start = LocalDate.ofEpochDay(startDay)
    val end = LocalDate.ofEpochDay(endDay)
    val days = Rules.parseDaysX100(daysInput)
    val suggested = Rules.suggestedDaysX100(start, end, weekend)

    fun autoDays(s: LocalDate, e: LocalDate) {
        if (!daysTyped) daysInput = Rules.fmtDays(Rules.suggestedDaysX100(s, e, weekend))
    }

    val draft = LeaveDraft(entryId, type, start, end, days, note)
    val result = Rules.validate(draft, settings, vm.data.entries, today)
    val hasErrors = result.hasErrors

    val dirty = typeKey != init[0] || startDay != init[1].toLong() || endDay != init[2].toLong() ||
        daysInput != init[3] || note != init[4] || (scan != null && existing == null)

    fun tryClose() {
        if (dirty) confirmDiscard = true else onClose(false)
    }

    fun doSave() {
        if (days != null && vm.saveLeave(entryId, type, start, end, days, note)) onClose(true)
    }

    BackHandler { tryClose() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) tr("إضافة إجازة", "Add leave") else tr("تعديل إجازة", "Edit leave")) },
                navigationIcon = { IconButton(onClick = { tryClose() }) { Icon(Icons.Filled.Close, tr("إغلاق", "Close")) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (scan != null && existing == null) {
                val missing = buildList {
                    if (scan.type == null) add(tr("النوع", "type"))
                    if (scan.start == null) add(tr("التاريخ", "date"))
                    if (scan.daysX100 == null) add(tr("عدد الأيام", "days"))
                }
                IssueBox(Issue(
                    if (missing.isEmpty()) IssueLevel.INFO else IssueLevel.WARNING,
                    if (missing.isEmpty()) tr(
                        "تمت قراءة النموذج. راجع كل الحقول قبل الحفظ.",
                        "Form read. Please review every field before saving.",
                    ) else tr(
                        "لم أتمكن من قراءة: ${missing.joinToString("، ")}. أدخلها يدوياً وراجع الباقي.",
                        "Couldn't read: ${missing.joinToString(", ")}. Enter it manually and review the rest.",
                    ),
                ))
            }

            SectionCard(
                tr("1. نوع الإجازة", "1. Leave type"),
                help = tr(
                    "اختر نفس النوع المكتوب في نموذج الطلب.\n\n• السنوية (Holiday): الوحيدة التي تُخصم من الرصيد وتظهر في السليب.\n• باقي الأنواع: تُحسب في المجاميع والسجل والتقويم فقط.",
                    "Pick the same type as on the request form.\n\n• Holiday: the only type deducted from the balance and shown on the payslip.\n• Other types: counted in totals, history and calendar only.",
                ),
            ) {
                TypeSelector(type) { typeKey = it.key }
                Spacer(Modifier.height(6.dp))
                Text(type.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(
                tr("2. التاريخ", "2. Dates"),
                help = tr(
                    "«من» = أول يوم إجازة، و«إلى» = آخر يوم إجازة (وليس يوم العودة).\n\nليوم واحد أو نصف يوم اجعلهما نفس التاريخ.\n\nالإجازة السنوية تُحسب في سليب الشهر حسب «من»: من 1 إلى 15 ← سليب نفس الشهر، من 16 فما فوق ← سليب الشهر التالي.",
                    "«From» = first day off, «To» = last day off (not the return day).\n\nFor one day or half a day keep them the same.\n\nA Holiday goes to a payslip by its «From» date: 1–15 → same month, 16+ → next month.",
                ),
            ) {
                DateField(tr("من (أول يوم إجازة)", "From (first day off)"), start) { picking = PickTarget.START }
                Spacer(Modifier.height(10.dp))
                DateField(tr("إلى (آخر يوم إجازة)", "To (last day off)"), end) { picking = PickTarget.END }
            }

            SectionCard(
                tr("3. عدد الأيام", "3. Number of days"),
                help = tr(
                    "اكتب نفس العدد المكتوب في نموذج الطلب (Numbers of days requested): 0.5 أو 1 أو أكثر، بمضاعفات النصف.\n\nزر «حسب التواريخ» يحسب أيام العمل بين التاريخين بدون الجمعة والسبت، ويمكنك تعديله إذا صادفت عطلة رسمية.",
                    "Type the same number as on the request form (Numbers of days requested): 0.5, 1 or more, in halves.\n\n«By dates» counts working days between the dates without weekends; change it if there was a public holiday.",
                ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    QuickDays(tr("نصف يوم", "Half day"), "0.5", daysInput) {
                        daysInput = it
                        daysTyped = true
                        if (endDay != startDay) endDay = startDay
                    }
                    QuickDays(tr("يوم", "1 day"), "1", daysInput) {
                        daysInput = it
                        daysTyped = true
                        if (endDay != startDay) endDay = startDay
                    }
                    if (start != end && suggested > 0) {
                        QuickDays(tr("حسب التواريخ", "By dates") + " (${Rules.fmtDays(suggested)})", Rules.fmtDays(suggested), daysInput) {
                            daysInput = it
                            daysTyped = false
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = daysInput,
                    onValueChange = {
                        daysInput = it.take(6)
                        daysTyped = true
                    },
                    label = { Text(tr("عدد الأيام", "Days")) },
                    placeholder = { Text(tr("مثال: 0.5 أو 1 أو 2.5", "e.g. 0.5, 1 or 2.5")) },
                    singleLine = true,
                    isError = days == null || (days % 50 != 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("أيام العمل في الفترة: ${Rules.fmtDays(suggested)}", "Working days in the period: ${Rules.fmtDays(suggested)}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionCard(
                tr("4. ملاحظة (اختياري)", "4. Note (optional)"),
                help = tr("أي تفاصيل تساعدك على التذكّر، مثل رقم الطلب. تظهر في السجل والبحث وكشف PDF.", "Anything that helps you remember, like the request number. Shown in history, search and the PDF."),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    label = { Text(tr("ملاحظة", "Note")) },
                    placeholder = { Text(tr("مثال: سفر عائلي / رقم الطلب", "e.g. family trip / request no.")) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    supportingText = { Text("${note.length}/$MAX_NOTE_LENGTH") },
                )
            }

            if (result.issues.isNotEmpty()) {
                SectionCard(
                    tr("المراجعة قبل الحفظ", "Check before saving"),
                    help = tr("🔴 أحمر = خطأ يمنع الحفظ.\n🟠 برتقالي = تنبيه، يمكنك الحفظ بعد التأكيد.\n🔵 أزرق = معلومة: في أي سليب ستُخصم الإجازة.", "🔴 Red = error, can't save.\n🟠 Orange = warning, save after confirming.\n🔵 Blue = info: which payslip it goes to."),
                ) { IssuesList(result.issues) }
            }
            if (hasErrors && triedSave) {
                Text(
                    tr("لا يمكن الحفظ قبل تصحيح الأخطاء باللون الأحمر.", "Fix the red errors before saving."),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = {
                    triedSave = true
                    when {
                        hasErrors -> Unit
                        result.warnings.isNotEmpty() -> confirmWarnings = true
                        else -> doSave()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (hasErrors || days == null) tr("حفظ (يوجد أخطاء)", "Save (has errors)")
                    else tr("حفظ • ", "Save • ") + daysText(days),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picking) {
        PickTarget.START -> DatePickDialog(start, onDismiss = { picking = null }) { d ->
            val length = endDay - startDay
            startDay = d.toEpochDay()
            endDay = startDay + length.coerceAtLeast(0)
            autoDays(LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay))
        }
        PickTarget.END -> DatePickDialog(if (end.isBefore(start)) start else end, onDismiss = { picking = null }) { d ->
            endDay = d.toEpochDay()
            autoDays(start, d)
        }
        null -> Unit
    }

    if (confirmWarnings) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text(tr("تنبيه قبل الحفظ", "Please confirm")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    result.warnings.forEach { IssueBox(it) }
                    Spacer(Modifier.height(6.dp))
                    Text(tr("هل تريد الحفظ على أي حال؟", "Save anyway?"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    doSave()
                }) { Text(tr("حفظ على أي حال", "Save anyway")) }
            },
            dismissButton = { TextButton(onClick = { confirmWarnings = false }) { Text(tr("رجوع للتعديل", "Back")) } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("تجاهل التغييرات؟", "Discard changes?")) },
            text = { Text(tr("لم تحفظ بعد. إذا خرجت الآن ستُفقد التغييرات.", "Not saved yet. Leaving now will lose your changes.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose(false)
                }) { Text(tr("خروج بدون حفظ", "Discard"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("متابعة", "Keep editing")) } },
        )
    }
}

@Composable
private fun QuickDays(label: String, value: String, current: String, onPick: (String) -> Unit) {
    val selected = Rules.parseDaysX100(current) == Rules.parseDaysX100(value)
    if (selected) {
        FilledTonalButton(onClick = { onPick(value) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onPick(value) }) { Text(label) }
    }
}
