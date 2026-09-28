package com.msf.jordan.leavetracker.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.msf.jordan.leavetracker.BuildConfig
import com.msf.jordan.leavetracker.data.JsonCodec
import com.msf.jordan.leavetracker.logic.AppData
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Report
import com.msf.jordan.leavetracker.logic.Reports
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.io.File
import java.time.LocalDate

/** All sharing works offline: files go to the app cache and are handed to any app (WhatsApp, email, Bluetooth…). */
object Sharing {

    const val APK_NAME = "Leave-Tracker.apk"

    private fun shareDir(ctx: Context): File = File(ctx.cacheDir, "share").apply { mkdirs() }

    private fun uriFor(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

    private fun launch(ctx: Context, intent: Intent, title: String): String? = try {
        ctx.startActivity(Intent.createChooser(intent, title))
        null
    } catch (e: ActivityNotFoundException) {
        tr("لا يوجد تطبيق على الهاتف يدعم المشاركة.", "No app on this phone can share this.")
    } catch (e: Exception) {
        tr("تعذّرت المشاركة.", "Sharing failed.")
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
        val out = File(shareDir(ctx), APK_NAME)
        File(ctx.applicationInfo.sourceDir).copyTo(out, overwrite = true)
        val i = fileIntent(ctx, out, "application/vnd.android.package-archive")
        if (BuildConfig.RELEASE_URL.isNotBlank()) {
            i.putExtra(Intent.EXTRA_TEXT, tr("تطبيق متتبع الإجازات. آخر إصدار: ", "Leave Tracker app. Latest version: ") + BuildConfig.RELEASE_URL)
        }
        launch(ctx, i, tr("مشاركة التطبيق", "Share the app"))
    } catch (e: Exception) {
        tr("تعذّر تجهيز ملف التطبيق للمشاركة.", "Could not prepare the app file.")
    }

    fun shareLink(ctx: Context): String? {
        if (BuildConfig.RELEASE_URL.isBlank()) return tr("رابط التحميل غير متوفر في هذه النسخة.", "No download link in this build.")
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tr("حمّل تطبيق متتبع الإجازات (مجاني):\n", "Download the Leave Tracker app (free):\n") + BuildConfig.RELEASE_URL)
        }
        return launch(ctx, i, tr("مشاركة رابط التحميل", "Share download link"))
    }

    fun shareBackup(ctx: Context, data: AppData): String? = try {
        val out = File(shareDir(ctx), backupFileName())
        out.writeText(JsonCodec.encode(data))
        launch(ctx, fileIntent(ctx, out, "application/json"), tr("إرسال النسخة الاحتياطية", "Send backup"))
    } catch (e: Exception) {
        tr("تعذّر إنشاء النسخة الاحتياطية.", "Could not create the backup.")
    }

    fun backupFileName(): String = "leave-backup-${LocalDate.now()}.json"

    /** Monthly / yearly statement as a PDF (opens on any phone or computer without this app). */
    fun shareReport(ctx: Context, report: Report, fileTag: String): String? = try {
        val out = File(shareDir(ctx), "Leave-Report-$fileTag.pdf")
        PdfReport.write(report, out)
        val i = fileIntent(ctx, out, "application/pdf")
        i.putExtra(Intent.EXTRA_SUBJECT, report.title)
        i.putExtra(Intent.EXTRA_TEXT, Reports.toText(report))
        launch(ctx, i, tr("مشاركة الكشف", "Share statement"))
    } catch (e: Exception) {
        tr("تعذّر إنشاء ملف PDF.", "Could not create the PDF.")
    }
}

/** Draws a [Report] on A4 pages with Android's built-in PDF engine (Arabic shaping via StaticLayout). */
private object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40

    fun write(r: Report, file: File) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M.toFloat()
        }

        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            isFakeBoldText = bold
            this.color = color
        }

        fun text(s: String, p: TextPaint, gapAfter: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, W - 2 * M)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (Tr.arabic) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .build()
            if (y + layout.height > H - M) newPage()
            val c = page!!.canvas
            c.save()
            c.translate(M.toFloat(), y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        fun line() {
            if (y + 8 > H - M) newPage()
            page!!.canvas.drawLine(M.toFloat(), y, (W - M).toFloat(), y, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += 8f
        }

        newPage()
        text(r.title, paint(20f, true, Color.rgb(30, 58, 138)), 6f)
        if (r.employee.isNotBlank()) text(tr("الموظف: ", "Employee: ") + r.employee, paint(12f))
        text(tr("تاريخ الإصدار: ", "Generated: ") + Rules.fmtDate(r.generatedOn), paint(11f, color = Color.DKGRAY), 10f)
        text(
            tr("الرصيد المتبقي حتى نهاية الشهر الحالي: ", "Remaining balance at the end of this month: ") + Rules.fmtDays(r.availableX100) + tr(" يوم", " days"),
            paint(16f, true, Color.rgb(16, 185, 129)),
            10f,
        )
        line()
        text(tr("المجموع حسب النوع", "Totals by type"), paint(14f, true))
        LeaveType.entries.forEach { t ->
            val v = r.totals[t] ?: 0
            text("• ${t.label}: ${Rules.fmtDays(v)}", paint(if (t == LeaveType.HOLIDAY) 13f else 12f, t == LeaveType.HOLIDAY, if (t == LeaveType.HOLIDAY) Color.rgb(16, 185, 129) else Color.BLACK), 2f)
        }
        text(tr("المجموع الكلي: ", "Grand total: ") + Rules.fmtDays(r.totalX100), paint(13f, true), 10f)

        if (r.ledger.isNotEmpty()) {
            line()
            text(tr("الإجازة السنوية في السليب (Paid leave)", "Paid leave on the payslip"), paint(14f, true))
            text(tr("الشهر — السابق / المحتسب / المكتسب / المتبقي", "Month — Previous / Accounted / Acquired / Remaining"), paint(10f, color = Color.DKGRAY), 2f)
            r.ledger.forEach {
                text(
                    "${Tr.monthLabel(it.month)} — ${Rules.fmtSlip(it.previousX100)} / ${Rules.fmtSlip(it.accountedX100)} / ${Rules.fmtSlip(it.acquiredX100)} / ${Rules.fmtSlip(it.remainingX100)}" +
                        if (it.projected) tr(" (متوقع)", " (expected)") else "",
                    paint(12f),
                    2f,
                )
            }
            y += 8f
        }

        line()
        text(tr("التفاصيل", "Details"), paint(14f, true))
        if (r.entries.isEmpty()) text(tr("لا يوجد إجازات في هذه الفترة.", "No leaves in this period."), paint(12f))
        r.entries.forEach { e ->
            val base = "${Rules.rangeText(e.start, e.end)} — ${e.type.title} — ${Rules.fmtDays(e.daysX100)}"
            text(if (e.note.isBlank()) base else "$base — ${e.note}", paint(12f, e.type == LeaveType.HOLIDAY), 3f)
        }
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
