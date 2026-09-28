package com.msf.jordan.leavetracker.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.msf.jordan.leavetracker.BuildConfig
import com.msf.jordan.leavetracker.data.JsonCodec
import com.msf.jordan.leavetracker.logic.AppData
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import java.io.File
import java.time.LocalDate

/** All sharing works offline: files are copied to the app cache and handed to any app (WhatsApp, Bluetooth, Nearby Share, Email...). */
object Sharing {

    private fun shareDir(ctx: Context): File = File(ctx.cacheDir, "share").apply { mkdirs() }

    private fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

    private fun launch(ctx: Context, intent: Intent, title: String): String? = try {
        ctx.startActivity(Intent.createChooser(intent, title))
        null
    } catch (_: ActivityNotFoundException) {
        "لا يوجد تطبيق على الهاتف يدعم المشاركة."
    } catch (e: Exception) {
        "تعذّرت المشاركة: ${e.message ?: "خطأ غير معروف"}"
    }

    private fun fileIntent(ctx: Context, file: File, mime: String): Intent {
        val uri = uriFor(ctx, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Shares the installed APK itself — no internet needed. Returns an error message or null. */
    fun shareApp(ctx: Context): String? = try {
        val src = File(ctx.applicationInfo.sourceDir)
        val out = File(shareDir(ctx), "MSF-Leave-Tracker.apk")
        src.copyTo(out, overwrite = true)
        val i = fileIntent(ctx, out, "application/vnd.android.package-archive")
        if (BuildConfig.RELEASE_URL.isNotBlank()) {
            i.putExtra(Intent.EXTRA_TEXT, "تطبيق MSF Leave Tracker لتتبع الإجازات. رابط آخر إصدار: ${BuildConfig.RELEASE_URL}")
        }
        launch(ctx, i, "مشاركة التطبيق")
    } catch (e: Exception) {
        "تعذّر تجهيز ملف التطبيق للمشاركة."
    }

    fun shareLink(ctx: Context): String? {
        if (BuildConfig.RELEASE_URL.isBlank()) return "رابط التحميل غير متوفر في هذه النسخة. استخدم «مشاركة ملف التطبيق»."
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "حمّل تطبيق MSF Leave Tracker (مجاني):\n${BuildConfig.RELEASE_URL}")
        }
        return launch(ctx, i, "مشاركة رابط التحميل")
    }

    fun shareBackup(ctx: Context, data: AppData): String? = try {
        val out = File(shareDir(ctx), backupFileName())
        out.writeText(JsonCodec.encode(data))
        launch(ctx, fileIntent(ctx, out, "application/json"), "إرسال النسخة الاحتياطية")
    } catch (e: Exception) {
        "تعذّر إنشاء النسخة الاحتياطية."
    }

    fun shareSummary(ctx: Context, data: AppData): String? {
        val today = LocalDate.now()
        val s = Rules.summarize(data, today)
        val sb = StringBuilder()
        sb.append("ملخص الإجازات")
        data.settings?.name?.takeIf { it.isNotBlank() }?.let { sb.append(" - ").append(it) }
        sb.append("\nبتاريخ ").append(Rules.fmtDate(today)).append("\n\n")
        sb.append("الرصيد المتبقي: ").append(Rules.fmtDays(s.availableX100)).append(" يوم\n")
        sb.append("المتوقع في سليب ").append(Rules.monthLabel(s.currentMonth)).append(": ").append(Rules.fmtDays(s.currentSlipX100)).append("\n")
        sb.append("خصم مؤجل للأشهر القادمة: ").append(Rules.fmtDays(s.pendingFutureX100)).append("\n\n")
        sb.append("الاستخدام في ").append(today.year).append(":\n")
        LeaveType.entries.forEach { t ->
            sb.append("• ").append(t.ar).append(": ").append(Rules.fmtDays(s.usedThisYear[t] ?: 0)).append(" يوم\n")
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        return launch(ctx, i, "مشاركة الملخص")
    }

    fun backupFileName(): String = "msf-leave-backup-${LocalDate.now()}.json"
}
