package com.msf.jordan.leavetracker.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msf.jordan.leavetracker.data.Repository
import com.msf.jordan.leavetracker.logic.AppData
import com.msf.jordan.leavetracker.logic.AppSettings
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.ParsedForm
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

data class HomePrefs(
    val previousSlip: Boolean,
    val upcomingSlips: Boolean,
    val slipDetails: Boolean,
    val totals: Boolean,
    val thisMonth: Boolean,
    val upcoming: Boolean,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app.filesDir)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var arabic by mutableStateOf(prefs.getString("lang", null)?.let { it == "ar" } ?: (Locale.getDefault().language == "ar"))
        private set

    var data by mutableStateOf(AppData.EMPTY)
        private set

    /** One-shot message shown in a snackbar. */
    var message by mutableStateOf<String?>(null)
        private set

    var refreshing by mutableStateOf(false)
        private set

    /** Increases on every refresh so screens recompute "today" and totals. */
    var refreshTick by mutableIntStateOf(0)
        private set

    /** What the home screen shows (Settings → Home screen). */
    var homePrefs by mutableStateOf(loadHomePrefs())
        private set

    private fun loadHomePrefs() = HomePrefs(
        previousSlip = prefs.getBoolean("home_prev_slip", true),
        upcomingSlips = prefs.getBoolean("home_next_slips", false),
        slipDetails = prefs.getBoolean("home_slip_details", false),
        totals = prefs.getBoolean("home_totals", false),
        thisMonth = prefs.getBoolean("home_this_month", false),
        upcoming = prefs.getBoolean("home_upcoming", false),
    )

    fun updateHomePrefs(p: HomePrefs) {
        homePrefs = p
        prefs.edit()
            .putBoolean("home_prev_slip", p.previousSlip)
            .putBoolean("home_next_slips", p.upcomingSlips)
            .putBoolean("home_slip_details", p.slipDetails)
            .putBoolean("home_totals", p.totals)
            .putBoolean("home_this_month", p.thisMonth)
            .putBoolean("home_upcoming", p.upcoming)
            .apply()
    }

    /** Result of a scanned form waiting to open in the editor. */
    var pendingScan by mutableStateOf<ParsedForm?>(null)

    init {
        Tr.arabic = arabic
        val r = repo.load()
        data = r.data
        message = r.warning
    }

    fun today(): LocalDate = LocalDate.now()

    fun setLanguage(ar: Boolean) {
        Tr.arabic = ar
        arabic = ar
        prefs.edit().putString("lang", if (ar) "ar" else "en").apply()
    }

    fun consumeMessage() {
        message = null
    }

    fun toast(text: String) {
        message = text
    }

    /** Pull-to-refresh: re-reads the saved file and recalculates everything. */
    fun refresh() {
        if (refreshing) return
        refreshing = true
        val r = repo.load()
        data = r.data
        refreshTick++
        if (r.warning != null) message = r.warning
        viewModelScope.launch {
            delay(500)
            refreshing = false
        }
    }

    private fun commit(newData: AppData): Boolean {
        val sorted = newData.copy(entries = newData.entries.sortedWith(compareBy<LeaveEntry> { it.start }.thenBy { it.createdAt }))
        return if (repo.save(sorted)) {
            data = sorted
            true
        } else {
            message = tr(
                "تعذّر الحفظ في ذاكرة الهاتف. تأكد من وجود مساحة كافية ثم حاول مجدداً.",
                "Could not save to the phone storage. Free some space and try again.",
            )
            false
        }
    }

    fun saveSettings(s: AppSettings): Boolean = commit(data.copy(settings = s))

    fun saveLeave(editingId: String?, type: LeaveType, start: LocalDate, end: LocalDate, daysX100: Int, note: String): Boolean {
        val old = data.entries.firstOrNull { it.id == editingId }
        val entry = LeaveEntry(
            id = editingId ?: UUID.randomUUID().toString(),
            type = type,
            start = start,
            end = end,
            daysX100 = daysX100,
            note = note.trim(),
            createdAt = old?.createdAt ?: System.currentTimeMillis(),
        )
        return commit(data.copy(entries = data.entries.filter { it.id != editingId } + entry))
    }

    fun delete(id: String): LeaveEntry? {
        val e = data.entries.firstOrNull { it.id == id } ?: return null
        return if (commit(data.copy(entries = data.entries.filter { it.id != id }))) e else null
    }

    fun restore(e: LeaveEntry) {
        if (data.entries.none { it.id == e.id }) commit(data.copy(entries = data.entries + e))
    }

    fun replaceAll(newData: AppData): Boolean = commit(newData)

    fun resetAll(): Boolean = commit(AppData.EMPTY)
}
