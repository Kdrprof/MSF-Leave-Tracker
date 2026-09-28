package com.msf.jordan.leavetracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.msf.jordan.leavetracker.logic.FormParser
import com.msf.jordan.leavetracker.logic.ParsedForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Reads a photographed leave request form fully on the phone (Google ML Kit, bundled model — no internet).
 * The photo is tried in all four orientations and the best reading wins.
 */
object FormScanner {

    /** A fresh file + content Uri for the camera app to write the photo into. */
    fun newPhotoUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "scan").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest photo
        val file = File(dir, "form-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    }

    suspend fun scan(ctx: Context, uri: Uri, today: LocalDate): ParsedForm = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(ctx, uri) ?: throw IllegalStateException("image")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            var best = ParsedForm(null, null, null, null)
            for (rotation in intArrayOf(0, 90, 270, 180)) {
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, rotation))).text
                val parsed = FormParser.parse(text, today)
                if (parsed.foundCount > best.foundCount) best = parsed
                if (best.foundCount == 3) break
            }
            best
        } finally {
            recognizer.close()
        }
    }

    /** Decodes at most ~2000px and applies the EXIF rotation so the text is upright when possible. */
    private fun loadBitmap(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        if (degrees == 0) return raw
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }
}
