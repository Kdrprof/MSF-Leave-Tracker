package com.msf.jordan.leavetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.BuildConfig
import com.msf.jordan.leavetracker.data.BackupFormatException
import com.msf.jordan.leavetracker.data.DecodeResult
import com.msf.jordan.leavetracker.data.JsonCodec
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.Reports
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.time.DayOfWeek
import java.time.YearMonth

/** Language switch used on the first screen and in Settings. */
@Composable
fun LanguageSwitch(vm: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LangButton("العربية", vm.arabic, Modifier.weight(1f)) { vm.setLanguage(true) }
        LangButton("English", !vm.arabic, Modifier.weight(1f)) { vm.setLanguage(false) }
    }
}

@Composable
private fun LangButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) FilledTonalButton(onClick = onClick, modifier = modifier) { Text(text, fontWeight = FontWeight.Bold) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

/** Used both for the first-run setup and the settings tab. */
@Composable
fun SettingsForm(vm: AppViewModel, firstRun: Boolean, onSaved: () -> Unit) {
    val current = vm.data.settings
    val today = vm.today()
    val defaultMonth = YearMonth.from(today).minusMonths(1)

    var name by rememberSaveable(current) { mutableStateOf(current?.name.orEmpty()) }
    var balance by rememberSaveable(current) { mutableStateOf(current?.let { Rules.fmtSlip(it.openingBalanceX100) }.orEmpty()) }
    var monthText by rememberSaveable(current) { mutableStateOf((current?.openingMonth ?: defaultMonth).toString()) }
    var weekendText by rememberSaveable(current) {
        mutableStateOf((current?.weekend ?: Rules.DEFAULT_WEEKEND).map { it.value }.sorted().joinToString(","))
    }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmWarnings by remember { mutableStateOf(false) }

    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(defaultMonth)
    val weekend = weekendText.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()
    val v = Rules.validateSettings(Rules.SettingsInput(name, balance, month, weekend), today)

    fun save() {
        val s = v.settings ?: return
        if (vm.saveSettings(s)) {
            vm.toast(tr("تم حفظ البيانات ✔", "Saved ✔"))
            onSaved()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(if (firstRun) tr("إعداد الرصيد (مرة واحدة فقط)", "Set your balance (one time)") else tr("بياناتي والرصيد", "My details & balance")) {
            if (firstRun) {
                Text(tr(
                    "افتح آخر سليب راتب، وفي مربع Paid leave انسخ رقم Remaining. بعدها يحسب التطبيق كل شيء تلقائياً.",
                    "Open your latest payslip and copy «Remaining» from the Paid leave box. The app calculates everything after that.",
                ))
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(tr("اسمك (اختياري)", "Your name (optional)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(tr("يظهر في الرئيسية وفي الكشوفات التي تشاركها.", "Shown on the home screen and on shared statements."))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it.take(8) },
                label = { Text(tr("الرصيد المتبقي (Remaining) في آخر سليب", "«Remaining» on your latest payslip")) },
                placeholder = { Text(tr("مثال: 9.13", "e.g. 9.13")) },
                singleLine = true,
                isError = tried && v.balanceInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(tr(
                "الرقم الأخير في مربع Paid leave (مثل 9.13). مسموح بالكسور والأرقام العربية.",
                "The last number in the Paid leave box (like 9.13). Decimals allowed.",
            ))
            Spacer(Modifier.height(12.dp))

            Text(tr("شهر ذلك السليب", "Month of that payslip"), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text(tr("السابق", "Prev")) }
                Text(
                    "${Tr.monthLabel(month)}\n(${month.monthValue}/${month.year})",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month.isBefore(YearMonth.from(today))) {
                    Text(tr("التالي", "Next"))
                }
            }
            Hint(tr(
                "الإجازات السنوية التابعة لهذا السليب أو قبله محسوبة داخل الرصيد ولن تُخصم مرة ثانية. كل سليب بعده يضيف +2.08.",
                "Holidays belonging to this payslip or earlier are already inside the balance. Each later payslip adds +2.08.",
            ))
        }

        SectionCard(tr("أيام عطلة نهاية الأسبوع", "Weekend days")) {
            val order = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
            order.chunked(4).forEach { row ->
                Row {
                    row.forEach { d ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = d in weekend,
                                onCheckedChange = { on ->
                                    val set = if (on) weekend + d else weekend - d
                                    weekendText = set.map { it.value }.sorted().joinToString(",")
                                },
                            )
                            Text(Tr.dayShort(d), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (row.size < 4) Spacer(Modifier.weight((4 - row.size).toFloat()))
                }
            }
            Hint(tr(
                "في الأردن: الجمعة والسبت. تُستخدم فقط لاقتراح عدد الأيام وتلوين التقويم.",
                "Jordan: Friday & Saturday. Only used to suggest days and color the calendar.",
            ))
        }

        val shown = when {
            tried -> v.issues
            !firstRun -> v.issues.filter { it.level != IssueLevel.ERROR }
            else -> emptyList()
        }
        if (shown.isNotEmpty()) IssuesList(shown)

        Button(
            onClick = {
                tried = true
                when {
                    v.hasErrors -> Unit
                    v.issues.any { it.level == IssueLevel.WARNING } -> confirmWarnings = true
                    else -> save()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (firstRun) tr("ابدأ", "Start") else tr("حفظ البيانات", "Save")) }
    }

    if (confirmWarnings) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text(tr("تأكيد", "Confirm")) },
            text = { Column { v.issues.filter { it.level == IssueLevel.WARNING }.forEach { IssueBox(it) } } },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    save()
                }) { Text(tr("حفظ على أي حال", "Save anyway")) }
            },
            dismissButton = { TextButton(onClick = { confirmWarnings = false }) { Text(tr("رجوع", "Back")) } },
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<DecodeResult?>(null) }
    var confirmReset by remember { mutableIntStateOf(0) }
    val today = vm.today()
    var reportMonthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val reportMonth = runCatching { YearMonth.parse(reportMonthText) }.getOrDefault(YearMonth.from(today))

    fun report(err: String?) {
        if (err != null) vm.toast(err)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val os = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
                os.use { it.write(JsonCodec.encode(vm.data).toByteArray(Charsets.UTF_8)) }
                vm.toast(tr("تم حفظ النسخة الاحتياطية ✔", "Backup saved ✔"))
            } catch (e: Exception) {
                vm.toast(tr("تعذّر حفظ الملف في المكان المختار. جرّب مجلداً آخر.", "Couldn't save there. Try another folder."))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw BackupFormatException(tr("تعذّر فتح الملف.", "Couldn't open the file."))
                if (text.length > 5_000_000) throw BackupFormatException(tr("الملف كبير جداً وليس نسخة احتياطية.", "File is too big to be a backup."))
                pendingImport = JsonCodec.decode(text)
            } catch (e: BackupFormatException) {
                vm.toast(e.message ?: tr("ملف غير صالح", "Invalid file"))
            } catch (e: Exception) {
                vm.toast(tr("تعذّرت قراءة الملف.", "Couldn't read the file."))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(tr("اللغة", "Language")) {
            LanguageSwitch(vm)
        }

        SettingsForm(vm, firstRun = false, onSaved = {})

        SectionCard(tr("مشاركة كشف الإجازات (PDF)", "Share leave statement (PDF)")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { reportMonthText = reportMonth.minusMonths(1).toString() }) { Text(tr("السابق", "Prev")) }
                Text(Tr.monthLabel(reportMonth), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { reportMonthText = reportMonth.plusMonths(1).toString() }) { Text(tr("التالي", "Next")) }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { report(Sharing.shareReport(ctx, Reports.monthly(vm.data, reportMonth, today), reportMonth.toString())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("كشف شهر ${Tr.monthLabel(reportMonth)}", "Statement for ${Tr.monthLabel(reportMonth)}")) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { report(Sharing.shareReport(ctx, Reports.yearly(vm.data, reportMonth.year, today), reportMonth.year.toString())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("كشف سنة ${reportMonth.year} كاملة", "Full year ${reportMonth.year}")) }
            Hint(tr(
                "ملف PDF يُفتح على أي هاتف أو كمبيوتر بدون الحاجة للتطبيق — مناسب لإرساله للمدير.",
                "A PDF that opens on any phone or computer without this app — ready for your manager.",
            ))
        }

        SectionCard(tr("النسخ الاحتياطي (مهم عند تغيير الهاتف)", "Backup (important when changing phones)")) {
            Button(onClick = { exportLauncher.launch(Sharing.backupFileName()) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("حفظ نسخة احتياطية في ملف", "Save a backup file"))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { report(Sharing.shareBackup(ctx, vm.data)) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("إرسال النسخة الاحتياطية (واتساب / إيميل)", "Send backup (WhatsApp / email)"))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("استعادة من نسخة احتياطية", "Restore from backup")) }
            Hint(tr("ستظهر رسالة تأكيد قبل استبدال بياناتك.", "You will be asked to confirm before your data is replaced."))
        }

        SectionCard(tr("مشاركة التطبيق", "Share the app")) {
            Button(onClick = { report(Sharing.shareApp(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("إرسال ملف التطبيق (APK)", "Send the app file (APK)"))
            }
            Hint(tr("عبر واتساب أو البلوتوث أو Nearby Share — يعمل بدون إنترنت.", "Via WhatsApp, Bluetooth or Nearby Share — works offline."))
            if (BuildConfig.RELEASE_URL.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { report(Sharing.shareLink(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("مشاركة رابط التحميل", "Share download link"))
                }
            }
        }

        SectionCard(tr("منطقة الخطر", "Danger zone")) {
            OutlinedButton(onClick = { confirmReset = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("حذف كل البيانات", "Delete all data"), color = MaterialTheme.colorScheme.error)
            }
            Hint(tr("يحذف الرصيد وكل الإجازات من هذا الهاتف. احفظ نسخة احتياطية أولاً.", "Removes the balance and all leaves from this phone. Back up first."))
        }

        Text(
            tr("متتبع الإجازات • الإصدار ", "Leave Tracker • version ") + BuildConfig.VERSION_NAME + "\n" +
                tr("يعمل بالكامل بدون إنترنت. بياناتك محفوظة على هاتفك فقط.", "Works fully offline. Your data stays on your phone."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(80.dp))
    }

    val imp = pendingImport
    if (imp != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(tr("استعادة النسخة الاحتياطية؟", "Restore this backup?")) },
            text = {
                Text(
                    tr(
                        "سيتم استبدال بياناتك الحالية (${vm.data.entries.size} إجازة) ببيانات النسخة (${imp.data.entries.size} إجازة).",
                        "Your current data (${vm.data.entries.size} leaves) will be replaced by the backup (${imp.data.entries.size} leaves).",
                    ) + (if (imp.skipped > 0) tr("\n\nتم تجاهل ${imp.skipped} سجل تالف.", "\n\n${imp.skipped} damaged records skipped.") else ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    if (vm.replaceAll(imp.data)) vm.toast(tr("تمت الاستعادة ✔", "Restored ✔"))
                }) { Text(tr("استبدال", "Replace")) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }

    if (confirmReset > 0) {
        AlertDialog(
            onDismissRequest = { confirmReset = 0 },
            title = { Text(if (confirmReset == 1) tr("حذف كل البيانات؟", "Delete all data?") else tr("تأكيد نهائي", "Final confirmation")) },
            text = {
                Text(
                    if (confirmReset == 1) tr("سيتم حذف الرصيد و${vm.data.entries.size} إجازة.", "Balance and ${vm.data.entries.size} leaves will be deleted.")
                    else tr("لا يمكن التراجع. هل أنت متأكد تماماً؟", "This can't be undone. Are you sure?"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmReset == 1) {
                        confirmReset = 2
                    } else {
                        confirmReset = 0
                        if (vm.resetAll()) vm.toast(tr("تم حذف كل البيانات", "All data deleted"))
                    }
                }) { Text(if (confirmReset == 1) tr("متابعة", "Continue") else tr("حذف نهائي", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = 0 }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}
