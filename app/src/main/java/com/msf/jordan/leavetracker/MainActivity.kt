package com.msf.jordan.leavetracker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import com.msf.jordan.leavetracker.ui.AppTheme
import com.msf.jordan.leavetracker.ui.AppViewModel
import com.msf.jordan.leavetracker.ui.CalendarScreen
import com.msf.jordan.leavetracker.ui.FormScanner
import com.msf.jordan.leavetracker.ui.Hint
import com.msf.jordan.leavetracker.ui.HistoryScreen
import com.msf.jordan.leavetracker.ui.HomeScreen
import com.msf.jordan.leavetracker.ui.LanguageSwitch
import com.msf.jordan.leavetracker.ui.LeaveEditorScreen
import com.msf.jordan.leavetracker.ui.PayslipScreen
import com.msf.jordan.leavetracker.ui.SettingsForm
import com.msf.jordan.leavetracker.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val arabic = vm.arabic
            Tr.arabic = arabic
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // Re-create every screen when the language changes so all texts switch at once.
                        key(arabic) { AppRoot(vm) }
                    }
                }
            }
        }
    }
}

private enum class Tab { HOME, HISTORY, CALENDAR, PAYSLIP, SETTINGS }

private fun Tab.title(): String = when (this) {
    Tab.HOME -> tr("الرئيسية", "Home")
    Tab.HISTORY -> tr("السجل", "History")
    Tab.CALENDAR -> tr("التقويم", "Calendar")
    Tab.PAYSLIP -> tr("السليب", "Payslip")
    Tab.SETTINGS -> tr("الإعدادات", "Settings")
}

private const val NEW_ENTRY = "__new__"

@Composable
private fun AppRoot(vm: AppViewModel) {
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val tab = runCatching { Tab.valueOf(tabName) }.getOrDefault(Tab.HOME)

    // One-shot messages from the ViewModel. Show first, clear after (clearing first would cancel this effect).
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.consumeMessage()
        }
    }

    fun runScan(uri: Uri) {
        scanning = true
        scope.launch {
            try {
                val parsed = FormScanner.scan(ctx, uri, vm.today())
                if (parsed.foundCount == 0) {
                    vm.toast(tr(
                        "لم أستطع قراءة النموذج. جرّب صورة أوضح بإضاءة جيدة، أو أدخل الإجازة يدوياً.",
                        "Couldn't read the form. Try a clearer, well-lit photo, or enter the leave manually.",
                    ))
                } else {
                    vm.pendingScan = parsed
                    editing = NEW_ENTRY
                }
            } catch (e: Exception) {
                vm.toast(tr("تعذّرت قراءة الصورة. أدخل الإجازة يدوياً.", "Couldn't read the image. Enter the leave manually."))
            } finally {
                scanning = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) runScan(Uri.parse(u))
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runScan(uri)
    }

    fun onDeleted(e: LeaveEntry) {
        scope.launch {
            val r = snackbar.showSnackbar(
                tr("تم حذف ${e.type.ar} (${Rules.fmtDays(e.daysX100)} يوم)", "Deleted ${e.type.en} (${Rules.fmtDays(e.daysX100)})"),
                actionLabel = tr("تراجع", "Undo"),
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) vm.restore(e)
        }
    }

    val editId = editing
    if (vm.data.settings == null) {
        FirstRun(vm, snackbar) { tabName = Tab.HOME.name }
    } else if (editId != null) {
        LeaveEditorScreen(
            vm,
            entryId = if (editId == NEW_ENTRY) null else editId,
            scan = if (editId == NEW_ENTRY) vm.pendingScan else null,
            snackbar = snackbar,
        ) { saved ->
            editing = null
            vm.pendingScan = null
            if (saved) vm.toast(tr("تم الحفظ ✔ وتحدّثت الحسابات", "Saved ✔ — totals updated"))
        }
    } else {
        MainScaffold(
            vm, tab, snackbar,
            onTab = { tabName = it.name },
            onEdit = { editing = it },
            onAdd = { showAddMenu = true },
            onDeleted = { onDeleted(it) },
        )
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text(tr("إضافة إجازة", "Add leave")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        showAddMenu = false
                        vm.pendingScan = null
                        editing = NEW_ENTRY
                    }, modifier = Modifier.fillMaxWidth()) { Text(tr("✍️  إدخال يدوي", "✍️  Enter manually")) }
                    OutlinedButton(onClick = {
                        showAddMenu = false
                        try {
                            val u = FormScanner.newPhotoUri(ctx)
                            photoUri = u.toString()
                            cameraLauncher.launch(u)
                        } catch (e: Exception) {
                            vm.toast(tr("لا يوجد تطبيق كاميرا متاح.", "No camera app available."))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(tr("📷  تصوير نموذج الطلب", "📷  Photograph the form")) }
                    OutlinedButton(onClick = {
                        showAddMenu = false
                        try {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (e: Exception) {
                            vm.toast(tr("تعذّر فتح المعرض.", "Couldn't open the gallery."))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(tr("🖼️  رفع صورة النموذج", "🖼️  Upload a form photo")) }
                    Hint(tr(
                        "القراءة تتم على الهاتف بالذكاء الاصطناعي بدون إنترنت. صوّر النموذج كاملاً بإضاءة جيدة، ثم راجع البيانات قبل الحفظ.",
                        "Reading happens on the phone with on-device AI, no internet. Capture the whole form in good light, then review before saving.",
                    ))
                }
            },
            confirmButton = { TextButton(onClick = { showAddMenu = false }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }

    if (scanning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("جاري قراءة النموذج…", "Reading the form…")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(tr("على الهاتف بدون إنترنت. لحظات فقط.", "On the phone, offline. A few seconds."))
                }
            },
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstRun(vm: AppViewModel, snackbar: SnackbarHostState, onDone: () -> Unit) {
    Scaffold(
        topBar = { AppBar(tr("مرحباً بك في متتبع الإجازات", "Welcome to Leave Tracker"), vm) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanguageSwitch(vm)
            Text(tr("خطوة واحدة فقط", "Just one step"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                tr(
                    "التطبيق يعمل بدون إنترنت ويحفظ بياناتك على هاتفك فقط. لديك نسخة احتياطية؟ أكمل الإعداد ثم استعدها من «الإعدادات».",
                    "The app works offline and keeps your data on this phone only. Have a backup? Finish setup, then restore it from Settings.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsForm(vm, firstRun = true, onSaved = onDone)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    vm: AppViewModel,
    tab: Tab,
    snackbar: SnackbarHostState,
    onTab: (Tab) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onDeleted: (LeaveEntry) -> Unit,
) {
    Scaffold(
        topBar = { AppBar(if (tab == Tab.HOME) tr("متتبع الإجازات", "Leave Tracker") else tab.title(), vm) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.HISTORY || tab == Tab.CALENDAR) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(tr("إضافة إجازة", "Add leave")) },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.HISTORY -> Icons.Filled.List
                                    Tab.CALENDAR -> Icons.Filled.DateRange
                                    Tab.PAYSLIP -> Icons.Filled.AccountBox
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = t.title(),
                            )
                        },
                        label = { Text(t.title(), fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        // Pull down from the top on any tab to reload and recalculate everything.
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            // refreshTick forces a full recalculation of the visible screen after refresh / edits.
            key(vm.refreshTick) {
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(vm, onOpenEntry = onEdit, onOpenSettings = { onTab(Tab.SETTINGS) })
                        Tab.HISTORY -> HistoryScreen(vm, onOpenEntry = onEdit, onDeleted = onDeleted)
                        Tab.CALENDAR -> CalendarScreen(vm, onOpenEntry = onEdit)
                        Tab.PAYSLIP -> PayslipScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(title: String, vm: AppViewModel) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        actions = {
            // Quick language switch
            TextButton(onClick = { vm.setLanguage(!vm.arabic) }) {
                Text(if (vm.arabic) "EN" else "ع", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
