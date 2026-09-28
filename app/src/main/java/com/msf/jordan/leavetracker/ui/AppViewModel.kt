package com.msf.jordan.leavetracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.msf.jordan.leavetracker.data.Repository
import com.msf.jordan.leavetracker.logic.AppData
import com.msf.jordan.leavetracker.logic.AppSettings
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeavePart
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import java.time.LocalDate
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app.filesDir)

    var data by mutableStateOf(AppData.EMPTY)
        private set

    /** One-shot message shown in a snackbar. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        val r = repo.load()
        data = r.data
        message = r.warning
    }

    fun today(): LocalDate = LocalDate.now()

    fun consumeMessage() {
        message = null
    }

    fun toast(text: String) {
        message = text
    }

    private fun commit(newData: AppData): Boolean {
        val sorted = newData.copy(entries = newData.entries.sortedWith(compareBy<LeaveEntry> { it.start }.thenBy { it.createdAt }))
        return if (repo.save(sorted)) {
            data = sorted
            true
        } else {
            message = "تعذّر الحفظ في ذاكرة الهاتف. تأكد من وجود مساحة كافية ثم حاول مجدداً."
            false
        }
    }

    fun saveSettings(s: AppSettings): Boolean {
        // If the weekend changed, re-check stored holidays never cross a payslip boundary.
        val entries = Rules.normalize(data.entries, s.weekend)
        return commit(AppData(s, entries))
    }

    fun saveLeave(editingId: String?, type: LeaveType, parts: List<LeavePart>, note: String): Boolean {
        if (parts.isEmpty()) return false
        val now = System.currentTimeMillis()
        val kept = data.entries.filter { it.id != editingId }
        val created = parts.mapIndexed { i, p ->
            LeaveEntry(
                id = if (i == 0 && editingId != null) editingId else UUID.randomUUID().toString(),
                type = type,
                start = p.start,
                end = p.end,
                daysX100 = p.daysX100,
                note = note.trim(),
                createdAt = now + i,
            )
        }
        return commit(data.copy(entries = kept + created))
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
