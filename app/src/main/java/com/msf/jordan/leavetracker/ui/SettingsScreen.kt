package com.msf.jordan.leavetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
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
    var previous by rememberSaveable(current) {
        mutableStateOf(current?.slipPreviousX100?.let { Rules.fmtSlip(it) } ?: current?.let { Rules.fmtSlip(it.openingBalanceX100) }.orEmpty())
    }
    var accounted by rememberSaveable(current) { mutableStateOf(current?.slipAccountedX100?.let { Rules.fmtSlip(it) }.orEmpty()) }
    var monthText by rememberSaveable(current) { mutableStateOf((current?.openingMonth ?: defaultMonth).toString()) }
    var weekendText by rememberSaveable(current) {
        mutableStateOf((current?.weekend ?: Rules.DEFAULT_WEEKEND).map { it.value }.sorted().joinToString(","))
    }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmWarnings by remember { mutableStateOf(false) }

    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(defaultMonth)
    val weekend = weekendText.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()
    val v = Rules.validateSettings(Rules.SettingsInput(name, previous, accounted, month, weekend), today)

    fun save() {
        val s = v.settings ?: return
        if (vm.saveSettings(s)) {
            vm.toast(tr("تم حفظ البيانات ✔ وتحدّثت الحسابات", "Saved ✔ — balance recalculated"))
            onSaved()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(
            if (firstRun) tr("انسخ آخر سليب راتب (مرة واحدة)", "Copy your latest payslip (one time)") else tr("آخر سليب راتب (المرجع)", "Latest payslip (reference)"),
            help = tr(
                "افتح آخر سليب راتب استلمته، وابحث عن مربع Paid leave، وانسخ منه:\n• Previous balance ← الرصيد السابق\n• Accounted this month ← المحتسب هذا الشهر\n\nالتطبيق يحسب «المتبقي» تلقائياً (السابق − المحتسب + 2.08). طابقه مع خانة Remaining في السليب؛ إذا تطابق فكل حساباتك بعدها ستكون صحيحة.\n\nالإجازات السنوية التابعة لهذا السليب أو قبله لن تُخصم مرة ثانية.",
                "Open your latest payslip, find the Paid leave box and copy:\n• Previous balance\n• Accounted this month\n\nThe app computes «Remaining» (Previous − Accounted + 2.08). Compare it with Remaining on the payslip; if it matches, everything after it will be correct.\n\nHolidays belonging to this payslip or earlier won't be deducted again.",
            ),
        ) {
            FieldHeader(tr("شهر السليب", "Payslip month"), tr("الشهر المكتوب على السليب الذي تنسخ منه الأرقام (مثلاً سليب آب 2026).", "The month printed on the payslip you copy from (e.g. August 2026)."))
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
            Spacer(Modifier.height(12.dp))

            FieldHeader(
                tr("الرصيد السابق (Previous balance)", "Previous balance"),
                tr(
                    "أول رقم في مربع Paid leave، وهو الرصيد المُرحَّل من سليب الشهر الذي قبله. مثال: سليب آب 2026 ← 9.55 (هو متبقي سليب تموز).",
                    "First number in the Paid leave box: the balance carried from the previous month's payslip. Example: Aug 2026 → 9.55 (July's Remaining).",
                ),
            )
            OutlinedTextField(
                value = previous,
                onValueChange = { previous = it.take(8) },
                placeholder = { Text("9.55") },
                singleLine = true,
                isError = tried && v.previousInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            FieldHeader(
                tr("المحتسب هذا الشهر (Accounted this month)", "Accounted this month"),
                tr(
                    "الرقم الثاني: الإجازات السنوية التي يبدأ أول يوم فيها من 16 الشهر الماضي حتى 15 شهر السليب. مثال: سليب آب ← إجازات 16 تموز حتى 15 آب = 2.50. اكتب 0 إذا لا يوجد.",
                    "Second number: holidays whose first day is from the 16th of last month to the 15th of the payslip month. Example: Aug payslip → 16 Jul–15 Aug = 2.50. Type 0 if none.",
                ),
            )
            OutlinedTextField(
                value = accounted,
                onValueChange = { accounted = it.take(6) },
                placeholder = { Text("2.50") },
                singleLine = true,
                isError = tried && v.accountedInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Live result — must equal «Remaining» on the payslip
            val rem = v.remainingX100
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tr("المتبقي المحسوب (Remaining)", "Calculated Remaining"), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (rem == null) "—" else Rules.fmtSlip(rem),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    tr("= السابق − المحتسب + 2.08 • يجب أن يساوي خانة Remaining في سليبك", "= Previous − Accounted + 2.08 • must equal Remaining on your payslip"),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        SectionCard(
            tr("بياناتي", "My details"),
            help = tr("اسمك يظهر في الرئيسية وفي كشوفات PDF التي تشاركها. اختياري.", "Your name appears on the home screen and shared PDFs. Optional."),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(tr("اسمك (اختياري)", "Your name (optional)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(
            tr("أيام عطلة نهاية الأسبوع", "Weekend days"),
            help = tr("في الأردن: الجمعة والسبت. تُستخدم فقط لاقتراح عدد أيام العمل وتلوين التقويم، ولا تغيّر الأيام التي تكتبها بنفسك.", "Jordan: Friday & Saturday. Only used to suggest working days and color the calendar; never changes the days you type."),
        ) {
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

        SectionCard(
            tr("ما يظهر في الصفحة الرئيسية", "Home screen"),
            help = tr("اختر الأقسام التي تريد رؤيتها تحت رصيدك. الرصيد المتبقي يظهر دائماً.", "Pick the sections shown under your balance. The remaining balance is always shown."),
        ) {
            val p = vm.homePrefs
            PrefSwitch(tr("آخر سليب راتب (الشهر السابق)", "Last payslip (previous month)"), p.previousSlip) { vm.updateHomePrefs(p.copy(previousSlip = it)) }
            PrefSwitch(tr("سليب هذا الشهر والشهر القادم (متوقع)", "This & next month payslips (expected)"), p.upcomingSlips) { vm.updateHomePrefs(p.copy(upcomingSlips = it)) }
            PrefSwitch(tr("تفاصيل السليب (الفترة والإجازات المحتسبة)", "Payslip details (period & counted leaves)"), p.slipDetails) { vm.updateHomePrefs(p.copy(slipDetails = it)) }
            PrefSwitch(tr("مجموع إجازاتي هذه السنة", "My totals this year"), p.totals) { vm.updateHomePrefs(p.copy(totals = it)) }
            PrefSwitch(tr("إجازات هذا الشهر", "This month's leaves"), p.thisMonth) { vm.updateHomePrefs(p.copy(thisMonth = it)) }
            PrefSwitch(tr("الإجازات القادمة", "Upcoming leaves"), p.upcoming) { vm.updateHomePrefs(p.copy(upcoming = it)) }
        }

        SettingsForm(vm, firstRun = false, onSaved = {})

        SectionCard(
            tr("مشاركة كشف الإجازات (PDF)", "Share leave statement (PDF)"),
            help = tr("ملف PDF فيه الرصيد، مجموع كل نوع، مربعات السليب، وكل الإجازات بتواريخها. يُفتح على أي هاتف أو كمبيوتر بدون التطبيق — مناسب لإرساله للمدير.", "A PDF with your balance, totals per type, payslip boxes and every leave with dates. Opens anywhere without this app — ready for your manager."),
        ) {
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
        }

        SectionCard(
            tr("النسخ الاحتياطي", "Backup"),
            help = tr("بياناتك محفوظة على هذا الهاتف فقط. احفظ نسخة احتياطية أو أرسلها لنفسك، وعند تغيير الهاتف استخدم «استعادة». ستظهر رسالة تأكيد قبل استبدال بياناتك.", "Your data lives on this phone only. Save or send yourself a backup; on a new phone use «Restore». You'll confirm before anything is replaced."),
        ) {
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
        }

        SectionCard(
            tr("مشاركة التطبيق", "Share the app"),
            help = tr("يرسل ملف التطبيق نفسه (APK) لزميلك عبر واتساب أو البلوتوث أو Nearby Share — يعمل بدون إنترنت.", "Sends the app file (APK) to a colleague via WhatsApp, Bluetooth or Nearby Share — works offline."),
        ) {
            Button(onClick = { report(Sharing.shareApp(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("إرسال ملف التطبيق (APK)", "Send the app file (APK)"))
            }
            if (BuildConfig.RELEASE_URL.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { report(Sharing.shareLink(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("مشاركة رابط التحميل", "Share download link"))
                }
            }
        }

        SectionCard(
            tr("منطقة الخطر", "Danger zone"),
            help = tr("يحذف الرصيد وكل الإجازات من هذا الهاتف نهائياً. احفظ نسخة احتياطية أولاً.", "Permanently removes the balance and all leaves from this phone. Back up first."),
        ) {
            OutlinedButton(onClick = { confirmReset = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("حذف كل البيانات", "Delete all data"), color = MaterialTheme.colorScheme.error)
            }
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

@Composable
private fun PrefSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
