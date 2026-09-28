package com.msf.jordan.leavetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.ui.AppTheme
import com.msf.jordan.leavetracker.ui.AppViewModel
import com.msf.jordan.leavetracker.ui.HistoryScreen
import com.msf.jordan.leavetracker.ui.HomeScreen
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
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot(vm)
                    }
                }
            }
        }
    }
}

private enum class Tab(val title: String) { HOME("الرئيسية"), HISTORY("السجل"), PAYSLIP("السليب"), SETTINGS("الإعدادات") }

private const val NEW_ENTRY = "__new__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: AppViewModel) {
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tab = runCatching { Tab.valueOf(tabName) }.getOrDefault(Tab.HOME)

    // One-shot messages from the ViewModel
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            // Show first, clear afterwards: clearing first would restart (and cancel) this effect.
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.consumeMessage()
        }
    }

    fun onDeleted(e: LeaveEntry) {
        scope.launch {
            val r = snackbar.showSnackbar(
                "تم حذف ${e.type.ar} (${Rules.fmtDays(e.daysX100)} يوم)",
                actionLabel = "تراجع",
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) vm.restore(e)
        }
    }

    val editId = editing
    // First run: ask for the opening balance before anything else.
    if (vm.data.settings == null) {
        Scaffold(
            topBar = { AppBar("مرحباً بك في متتبع الإجازات") },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text("خطوة واحدة فقط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "التطبيق يعمل بدون إنترنت ويحفظ بياناتك على هاتفك فقط. لديك نسخة احتياطية؟ أكمل الإعداد ثم استعدها من «الإعدادات».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsForm(vm, firstRun = true, onSaved = { tabName = Tab.HOME.name })
                Spacer(Modifier.height(32.dp))
            }
        }
    } else if (editId != null) {
        LeaveEditorScreen(vm, entryId = if (editId == NEW_ENTRY) null else editId, snackbar = snackbar) { saved ->
            editing = null
            if (saved) vm.toast("تم الحفظ ✔")
        }
    } else {
        MainScaffold(vm, tab, snackbar, onTab = { tabName = it.name }, onEdit = { editing = it }, onDeleted = { onDeleted(it) })
    }
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    tab: Tab,
    snackbar: SnackbarHostState,
    onTab: (Tab) -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: (LeaveEntry) -> Unit,
) {
    Scaffold(
        topBar = { AppBar(if (tab == Tab.HOME) "MSF Leave Tracker" else tab.title) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.HISTORY) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(NEW_ENTRY) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("إضافة إجازة") },
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
                                    Tab.HISTORY -> Icons.Filled.DateRange
                                    Tab.PAYSLIP -> Icons.Filled.AccountBox
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = t.title,
                            )
                        },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm, onOpenEntry = onEdit)
                Tab.HISTORY -> HistoryScreen(vm, onOpenEntry = onEdit, onDeleted = onDeleted)
                Tab.PAYSLIP -> PayslipScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(title: String) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
