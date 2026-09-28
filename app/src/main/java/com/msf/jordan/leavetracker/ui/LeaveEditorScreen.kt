package com.msf.jordan.leavetracker.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.logic.DurationKind
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.LeaveDraft
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.MAX_NOTE_LENGTH
import com.msf.jordan.leavetracker.logic.Rules
import java.time.LocalDate

private enum class PickTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveEditorScreen(
    vm: AppViewModel,
    entryId: String?,
    snackbar: SnackbarHostState,
    onClose: (saved: Boolean) -> Unit,
) {
    val existing = remember(entryId) { entryId?.let { id -> vm.data.entries.firstOrNull { it.id == id } } }
    val today = vm.today()
    val settings = vm.data.settings
    val weekend = settings?.weekend ?: Rules.DEFAULT_WEEKEND

    // Initial values (used to detect unsaved changes)
    val initType = existing?.type?.key ?: LeaveType.HOLIDAY.key
    val initDuration = (existing?.let { Rules.inferDuration(it) } ?: DurationKind.ONE).name
    val initStart = (existing?.start ?: today).toEpochDay()
    val initEnd = (existing?.end ?: today).toEpochDay()
    val initManual = existing != null && Rules.inferDuration(existing) == DurationKind.MULTI &&
        existing.daysX100 != Rules.workingDays(existing.start, existing.end, weekend) * 100
    val initManualText = if (initManual && existing != null) Rules.fmtDays(existing.daysX100) else ""
    val initNote = existing?.note.orEmpty()

    var typeKey by rememberSaveable { mutableStateOf(initType) }
    var durationName by rememberSaveable { mutableStateOf(initDuration) }
    var startDay by rememberSaveable { mutableStateOf(initStart) }
    var endDay by rememberSaveable { mutableStateOf(initEnd) }
    var manualOn by rememberSaveable { mutableStateOf(initManual) }
    var manualText by rememberSaveable { mutableStateOf(initManualText) }
    var note by rememberSaveable { mutableStateOf(initNote) }

    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var confirmWarnings by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showErrorsHint by remember { mutableStateOf(false) }

    val type = LeaveType.fromKey(typeKey) ?: LeaveType.HOLIDAY
    val duration = runCatching { DurationKind.valueOf(durationName) }.getOrDefault(DurationKind.ONE)
    val start = LocalDate.ofEpochDay(startDay)
    val end = LocalDate.ofEpochDay(endDay)

    val baseDraft = LeaveDraft(
        editingId = entryId,
        type = type,
        duration = duration,
        start = start,
        end = end,
        manualDaysX100 = null,
        note = note,
    )
    val spansAndLocked = Rules.spansPayslips(baseDraft) && type.affectsBalance
    val manualActive = duration == DurationKind.MULTI && manualOn && !spansAndLocked
    val manualParsed = if (manualActive) Rules.parseDaysX100(manualText) else null
    val draft = baseDraft.copy(manualDaysX100 = manualParsed)
    val result = Rules.validate(draft, settings, vm.data.entries, today)
    val issues = buildList {
        if (manualActive && manualParsed == null) {
            add(com.msf.jordan.leavetracker.logic.Issue(IssueLevel.ERROR, "اكتب عدد الأيام رقماً صحيحاً مثل 3 أو 2.5."))
        }
        addAll(result.issues)
    }
    val hasErrors = issues.any { it.level == IssueLevel.ERROR }

    val dirty = typeKey != initType || durationName != initDuration || startDay != initStart ||
        (duration == DurationKind.MULTI && endDay != initEnd) || manualOn != initManual ||
        manualText != initManualText || note != initNote

    fun tryClose() {
        if (dirty) confirmDiscard = true else onClose(false)
    }

    fun doSave() {
        if (vm.saveLeave(entryId, type, result.parts, note)) onClose(true)
    }

    BackHandler { tryClose() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "إضافة إجازة" else "تعديل إجازة") },
                navigationIcon = {
                    IconButton(onClick = { tryClose() }) { Icon(Icons.Filled.Close, "إغلاق") }
                },
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
            // 1. Type
            SectionCard("1. نوع الإجازة") {
                TypeSelector(type) { typeKey = it.key }
                Hint(type.hint)
            }

            // 2. Duration
            SectionCard("2. المدة") {
                DurationOption("نصف يوم (0.5)", "لإجازة صباحية أو مسائية فقط.", duration == DurationKind.HALF) {
                    durationName = DurationKind.HALF.name
                }
                DurationOption("يوم واحد (1)", "يوم عمل كامل.", duration == DurationKind.ONE) {
                    durationName = DurationKind.ONE.name
                }
                DurationOption("عدة أيام", "اختر تاريخ البداية والنهاية، ويحسب التطبيق أيام العمل تلقائياً.", duration == DurationKind.MULTI) {
                    durationName = DurationKind.MULTI.name
                    if (endDay < startDay) endDay = startDay
                }
            }

            // 3. Dates
            SectionCard("3. التاريخ") {
                DateField(if (duration == DurationKind.MULTI) "تاريخ البداية" else "التاريخ", start) { picking = PickTarget.START }
                if (duration == DurationKind.MULTI) {
                    Spacer(Modifier.height(10.dp))
                    DateField("تاريخ النهاية (آخر يوم إجازة)", end) { picking = PickTarget.END }
                    Hint("اضغط على الحقل لفتح التقويم. تاريخ النهاية هو آخر يوم تكون فيه في إجازة وليس يوم العودة.")
                    if (!end.isBefore(start)) {
                        val wd = Rules.workingDays(start, end, weekend)
                        val cal = Rules.calendarDays(start, end)
                        Spacer(Modifier.height(8.dp))
                        Text("أيام العمل المحسوبة: $wd يوم (من أصل $cal يوم تقويمي)", fontWeight = FontWeight.Bold)
                        Hint("لا تُحسب أيام عطلة نهاية الأسبوع.")
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !spansAndLocked) { manualOn = !manualOn },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = manualOn && !spansAndLocked, onCheckedChange = { manualOn = it }, enabled = !spansAndLocked)
                        Text("تعديل عدد الأيام يدوياً")
                    }
                    if (spansAndLocked) {
                        Hint("غير متاح هنا لأن الإجازة السنوية تمتد عبر يوم 15 وسيتم تقسيمها تلقائياً.")
                    } else if (manualOn) {
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it.take(6) },
                            label = { Text("عدد الأيام") },
                            placeholder = { Text("مثال: 2.5") },
                            singleLine = true,
                            isError = manualParsed == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Hint("استخدمه فقط إذا كانت في الفترة عطلة رسمية أو نصف يوم. المسموح: مضاعفات 0.5.")
                    } else {
                        Hint("فعّله إذا صادفت الفترة عطلة رسمية (مثل عيد) لا يجب خصمها.")
                    }
                }
            }

            // 4. Note
            SectionCard("4. ملاحظة (اختياري)") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    label = { Text("ملاحظة") },
                    placeholder = { Text("مثال: سفر عائلي / رقم الطلب") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    supportingText = { Text("${note.length}/$MAX_NOTE_LENGTH") },
                )
                Hint("تظهر في السجل فقط لمساعدتك على التذكّر.")
            }

            // Checks
            if (issues.isNotEmpty()) {
                SectionCard("المراجعة قبل الحفظ") { IssuesList(issues) }
            }

            if (hasErrors && showErrorsHint) {
                Text(
                    "لا يمكن الحفظ قبل تصحيح الأخطاء المشار إليها باللون الأحمر.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = {
                    when {
                        hasErrors -> showErrorsHint = true
                        result.warnings.isNotEmpty() -> confirmWarnings = true
                        else -> doSave()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                val total = result.parts.sumOf { it.daysX100 }
                Text(if (hasErrors) "حفظ (يوجد أخطاء)" else "حفظ • ${Rules.fmtDays(total)} يوم")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picking) {
        PickTarget.START -> DatePickDialog(start, onDismiss = { picking = null }) { d ->
            val shift = d.toEpochDay() - startDay
            startDay = d.toEpochDay()
            // keep the same length when moving the start date
            if (duration == DurationKind.MULTI) endDay = (endDay + shift).coerceAtLeast(startDay)
        }
        PickTarget.END -> DatePickDialog(if (end.isBefore(start)) start else end, onDismiss = { picking = null }) { d ->
            endDay = d.toEpochDay()
        }
        null -> Unit
    }

    if (confirmWarnings) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text("تنبيه قبل الحفظ") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    result.warnings.forEach { IssueBox(it) }
                    Spacer(Modifier.height(6.dp))
                    Text("هل تريد الحفظ على أي حال؟")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    doSave()
                }) { Text("حفظ على أي حال") }
            },
            dismissButton = { TextButton(onClick = { confirmWarnings = false }) { Text("رجوع للتعديل") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("تجاهل التغييرات؟") },
            text = { Text("لم تحفظ التغييرات بعد. إذا خرجت الآن ستُفقد.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose(false)
                }) { Text("خروج بدون حفظ", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("متابعة التعديل") } },
        )
    }
}

@Composable
private fun DurationOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
