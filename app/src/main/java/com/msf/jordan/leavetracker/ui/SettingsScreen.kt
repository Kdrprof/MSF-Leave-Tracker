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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.BuildConfig
import com.msf.jordan.leavetracker.data.BackupFormatException
import com.msf.jordan.leavetracker.data.DecodeResult
import com.msf.jordan.leavetracker.data.JsonCodec
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.Rules
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Used both for the first-run setup and the settings tab. */
@Composable
fun SettingsForm(vm: AppViewModel, firstRun: Boolean, onSaved: () -> Unit) {
    val current = vm.data.settings
    val today = vm.today()
    val defaultMonth = YearMonth.from(today).minusMonths(1)

    var name by rememberSaveable(current) { mutableStateOf(current?.name.orEmpty()) }
    var balance by rememberSaveable(current) { mutableStateOf(current?.let { Rules.fmtDays(it.openingBalanceX100) }.orEmpty()) }
    var monthText by rememberSaveable(current) { mutableStateOf((current?.openingMonth ?: defaultMonth).toString()) }
    var weekendText by rememberSaveable(current) {
        mutableStateOf((current?.weekend ?: Rules.DEFAULT_WEEKEND).map { it.value }.sorted().joinToString(","))
    }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmWarnings by remember { mutableStateOf(false) }

    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(defaultMonth)
    val weekend = weekendText.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()
    val v = Rules.validateSettings(Rules.SettingsInput(name, balance, month, weekend), today)
    val balanceError = v.issues.firstOrNull { it.level == IssueLevel.ERROR && it.text.contains("رصيد", ignoreCase = true) }

    fun save() {
        val s = v.settings ?: return
        if (vm.saveSettings(s)) {
            vm.toast("تم حفظ الإعدادات")
            onSaved()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(if (firstRun) "إعداد الرصيد (مرة واحدة فقط)" else "الرصيد الافتتاحي") {
            if (firstRun) {
                Text("خذ آخر سليب راتب استلمته واكتب رصيد الإجازات السنوية الظاهر فيه. سيحسب التطبيق كل شيء بعدها تلقائياً.")
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("اسمك (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("يظهر في الشاشة الرئيسية وفي الملخص الذي تشاركه.")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it.take(8) },
                label = { Text("رصيد الإجازات في آخر سليب (يوم)") },
                placeholder = { Text("مثال: 12.5") },
                singleLine = true,
                isError = tried && balanceError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("انسخه كما هو من السليب (Annual leave balance). مسموح بالكسور مثل 12.08، ويمكن استخدام الأرقام العربية.")
            Spacer(Modifier.height(12.dp))

            Text("شهر ذلك السليب", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("السابق") }
                Text(
                    "${Rules.monthLabel(month)}\n(${month.monthValue}/${month.year})",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { monthText = month.plusMonths(1).toString() },
                    enabled = month.isBefore(YearMonth.from(today)),
                ) { Text("التالي") }
            }
            Hint("الإجازات السنوية التي تعود لهذا السليب أو قبله تُعتبر محسوبة داخل الرصيد ولن تُخصم مرة ثانية. كل سليب بعده يضيف +2.08.")
        }

        SectionCard("أيام عطلة نهاية الأسبوع") {
            val ar = Locale.forLanguageTag("ar-JO")
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
                            Text(d.getDisplayName(TextStyle.SHORT, ar), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (row.size < 4) Spacer(Modifier.weight((4 - row.size).toFloat()))
                }
            }
            Hint("الافتراضي في الأردن: الجمعة والسبت. هذه الأيام لا تُحسب عند اختيار «عدة أيام».")
        }

        if (tried || !firstRun) {
            val shown = if (tried) v.issues else v.issues.filter { it.level != IssueLevel.ERROR }
            if (shown.isNotEmpty()) IssuesList(shown)
        }

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
        ) { Text(if (firstRun) "ابدأ" else "حفظ الإعدادات") }
    }

    if (confirmWarnings) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text("تأكيد") },
            text = { Column { v.issues.filter { it.level == IssueLevel.WARNING }.forEach { IssueBox(it) } } },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    save()
                }) { Text("حفظ على أي حال") }
            },
            dismissButton = { TextButton(onClick = { confirmWarnings = false }) { Text("رجوع") } },
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<DecodeResult?>(null) }
    var confirmReset by remember { mutableStateOf(0) }

    fun report(err: String?) {
        if (err != null) vm.toast(err)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val os = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
                os.use { it.write(JsonCodec.encode(vm.data).toByteArray(Charsets.UTF_8)) }
                vm.toast("تم حفظ النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                vm.toast("تعذّر حفظ الملف في المكان المختار. جرّب مجلداً آخر.")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw BackupFormatException("تعذّر فتح الملف.")
                if (text.length > 5_000_000) throw BackupFormatException("الملف كبير جداً وليس نسخة احتياطية.")
                pendingImport = JsonCodec.decode(text)
            } catch (e: BackupFormatException) {
                vm.toast(e.message ?: "ملف غير صالح")
            } catch (e: Exception) {
                vm.toast("تعذّرت قراءة الملف.")
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
        SettingsForm(vm, firstRun = false, onSaved = {})

        SectionCard("النسخ الاحتياطي (مهم عند تغيير الهاتف)") {
            Button(onClick = { exportLauncher.launch(Sharing.backupFileName()) }, modifier = Modifier.fillMaxWidth()) {
                Text("حفظ نسخة احتياطية في ملف")
            }
            Hint("يحفظ ملفاً صغيراً في الهاتف أو Google Drive. لا يحتاج إنترنت إذا حفظته على الهاتف.")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { report(Sharing.shareBackup(ctx, vm.data)) }, modifier = Modifier.fillMaxWidth()) {
                Text("إرسال النسخة الاحتياطية (واتساب / إيميل)")
            }
            Hint("أرسلها لنفسك لتحتفظ بها خارج الهاتف.")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("استعادة من نسخة احتياطية") }
            Hint("اختر ملف msf-leave-backup-….json. ستظهر لك رسالة تأكيد قبل الاستبدال.")
        }

        SectionCard("المشاركة") {
            Button(onClick = { report(Sharing.shareApp(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                Text("مشاركة ملف التطبيق (APK)")
            }
            Hint("يرسل التطبيق نفسه لزميلك عبر واتساب أو البلوتوث أو Nearby Share — يعمل بدون إنترنت.")
            if (BuildConfig.RELEASE_URL.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { report(Sharing.shareLink(ctx)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("مشاركة رابط التحميل")
                }
                Hint("رابط دائم لآخر إصدار.")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { report(Sharing.shareSummary(ctx, vm.data)) }, modifier = Modifier.fillMaxWidth()) {
                Text("مشاركة ملخص رصيدي كنص")
            }
            Hint("مفيد لإرساله للموارد البشرية أو للمدير المباشر.")
        }

        SectionCard("منطقة الخطر") {
            OutlinedButton(onClick = { confirmReset = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text("حذف كل البيانات", color = MaterialTheme.colorScheme.error)
            }
            Hint("يحذف الرصيد وكل الإجازات من هذا الهاتف. احفظ نسخة احتياطية أولاً.")
        }

        Text(
            "MSF Leave Tracker • الإصدار ${BuildConfig.VERSION_NAME}\nيعمل بالكامل بدون إنترنت. بياناتك محفوظة على هاتفك فقط.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(80.dp))
    }

    val imp = pendingImport
    if (imp != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("استعادة النسخة الاحتياطية؟") },
            text = {
                Text(
                    buildString {
                        append("سيتم استبدال بياناتك الحالية (${vm.data.entries.size} إجازة) ")
                        append("ببيانات النسخة (${imp.data.entries.size} إجازة).")
                        if (imp.data.settings == null) append("\n\nتنبيه: النسخة لا تحتوي على إعدادات الرصيد، ستحتاج لإدخالها.")
                        if (imp.skipped > 0) append("\n\nتنبيه: تم تجاهل ${imp.skipped} سجل تالف في الملف.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    if (vm.replaceAll(imp.data)) vm.toast("تمت الاستعادة بنجاح")
                }) { Text("استبدال") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("إلغاء") } },
        )
    }

    if (confirmReset > 0) {
        AlertDialog(
            onDismissRequest = { confirmReset = 0 },
            title = { Text(if (confirmReset == 1) "حذف كل البيانات؟" else "تأكيد نهائي") },
            text = {
                Text(
                    if (confirmReset == 1) "سيتم حذف الرصيد و${vm.data.entries.size} إجازة من هذا الهاتف."
                    else "لا يمكن التراجع عن هذه الخطوة. هل أنت متأكد تماماً؟",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmReset == 1) {
                        confirmReset = 2
                    } else {
                        confirmReset = 0
                        if (vm.resetAll()) vm.toast("تم حذف كل البيانات")
                    }
                }) { Text(if (confirmReset == 1) "متابعة" else "حذف نهائي", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = 0 }) { Text("إلغاء") } },
        )
    }
}
