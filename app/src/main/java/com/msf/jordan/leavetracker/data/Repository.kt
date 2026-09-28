package com.msf.jordan.leavetracker.data

import com.msf.jordan.leavetracker.logic.AppData
import com.msf.jordan.leavetracker.logic.AppSettings
import com.msf.jordan.leavetracker.logic.LeaveEntry
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.MAX_BALANCE_X100
import com.msf.jordan.leavetracker.logic.MAX_NOTE_LENGTH
import com.msf.jordan.leavetracker.logic.MIN_BALANCE_X100
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class BackupFormatException(message: String) : Exception(message)

data class DecodeResult(val data: AppData, val skipped: Int)

object JsonCodec {
    private const val APP_ID = "msf-leave-tracker"
    private const val VERSION = 1

    fun encode(data: AppData): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        data.settings?.let { s ->
            val o = JSONObject()
            o.put("name", s.name)
            o.put("openingBalanceX100", s.openingBalanceX100)
            o.put("openingMonth", s.openingMonth.toString())
            val w = JSONArray()
            s.weekend.sortedBy { it.value }.forEach { w.put(it.value) }
            o.put("weekend", w)
            root.put("settings", o)
        }
        val arr = JSONArray()
        for (e in data.entries) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("type", e.type.key)
            o.put("start", e.start.toString())
            o.put("end", e.end.toString())
            o.put("daysX100", e.daysX100)
            o.put("note", e.note)
            o.put("createdAt", e.createdAt)
            arr.put(o)
        }
        root.put("entries", arr)
        return root.toString(2)
    }

    /** Throws [BackupFormatException] if the file is not a valid backup. Invalid single entries are skipped. */
    fun decode(text: String): DecodeResult {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupFormatException(tr("الملف ليس نسخة احتياطية صالحة.", "This file is not a valid backup."))
        }
        if (root.optString("app") != APP_ID) throw BackupFormatException(tr("هذا الملف ليس نسخة احتياطية من هذا التطبيق.", "This file is not a backup from this app."))
        if (root.optInt("version", 0) > VERSION) throw BackupFormatException(tr("النسخة من إصدار أحدث. حدّث التطبيق أولاً.", "Backup is from a newer version. Update the app first."))

        var settings: AppSettings? = null
        val so = root.optJSONObject("settings")
        if (so != null) {
            try {
                val bal = so.getInt("openingBalanceX100")
                if (bal < MIN_BALANCE_X100 || bal > MAX_BALANCE_X100) throw IllegalArgumentException()
                val month = YearMonth.parse(so.getString("openingMonth"))
                val wArr = so.optJSONArray("weekend")
                val weekend = mutableSetOf<DayOfWeek>()
                if (wArr != null) {
                    for (i in 0 until wArr.length()) {
                        val v = wArr.optInt(i, -1)
                        if (v in 1..7) weekend += DayOfWeek.of(v)
                    }
                } else weekend += Rules.DEFAULT_WEEKEND
                settings = AppSettings(so.optString("name", "").take(60), bal, month, weekend)
            } catch (e: Exception) {
                throw BackupFormatException(tr("إعدادات النسخة الاحتياطية تالفة.", "Backup settings are damaged."))
            }
        }

        val entries = mutableListOf<LeaveEntry>()
        var skipped = 0
        val ids = HashSet<String>()
        val arr = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val type = LeaveType.fromKey(o.getString("type")) ?: throw IllegalArgumentException()
                val start = LocalDate.parse(o.getString("start"))
                val end = LocalDate.parse(o.getString("end"))
                val days = o.getInt("daysX100")
                if (end.isBefore(start)) throw IllegalArgumentException()
                if (days <= 0 || days % 50 != 0 || days > Rules.calendarDays(start, end) * 100) throw IllegalArgumentException()
                var id = o.optString("id", "")
                if (id.isBlank() || id in ids) id = java.util.UUID.randomUUID().toString()
                ids += id
                entries += LeaveEntry(id, type, start, end, days, o.optString("note", "").take(MAX_NOTE_LENGTH), o.optLong("createdAt", 0L))
            } catch (e: Exception) {
                skipped++
            }
        }
        return DecodeResult(AppData(settings, entries.sortedBy { it.start }), skipped)
    }
}

data class LoadResult(val data: AppData, val warning: String?)

/** Offline storage in the app's private folder. Writes are atomic with a .bak copy of the previous save. */
class Repository(dir: File) {
    private val main = File(dir, "leave_data.json")
    private val tmp = File(dir, "leave_data.json.tmp")
    private val bak = File(dir, "leave_data.json.bak")

    fun load(): LoadResult {
        if (!main.exists() && !bak.exists()) return LoadResult(AppData.EMPTY, null)
        try {
            if (main.exists()) {
                val r = JsonCodec.decode(main.readText())
                val w = if (r.skipped > 0) tr("تم تجاهل ${r.skipped} سجل تالف.", "${r.skipped} damaged records were skipped.") else null
                return LoadResult(r.data, w)
            }
        } catch (_: Exception) {
            // fall through to backup
        }
        try {
            if (bak.exists()) {
                val r = JsonCodec.decode(bak.readText())
                if (main.exists()) main.renameTo(File(main.parentFile, "leave_data.corrupt.${System.currentTimeMillis()}.json"))
                save(r.data)
                return LoadResult(r.data, tr("كان ملف البيانات تالفاً، وتمت استعادة آخر نسخة سليمة تلقائياً.", "Data file was damaged; the last good copy was restored."))
            }
        } catch (_: Exception) {
        }
        if (main.exists()) main.renameTo(File(main.parentFile, "leave_data.corrupt.${System.currentTimeMillis()}.json"))
        return LoadResult(AppData.EMPTY, tr("تعذّرت قراءة البيانات. إن كان لديك نسخة احتياطية استعدها من الإعدادات.", "Could not read saved data. Restore a backup from Settings if you have one."))
    }

    fun save(data: AppData): Boolean = try {
        tmp.writeText(JsonCodec.encode(data))
        if (main.exists()) main.copyTo(bak, overwrite = true)
        if (!tmp.renameTo(main)) {
            tmp.copyTo(main, overwrite = true)
            tmp.delete()
        }
        true
    } catch (_: Exception) {
        false
    }
}
