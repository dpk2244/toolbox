package com.pushpendratech.toolbox.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** All 12 image tools on-device with Android Bitmap APIs + ExifInterface. Originals are never overwritten by callers (outputs go to new files). */
object ImageTools {

    fun decode(file: File, maxDim: Int = 4096): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts) ?: throw IllegalStateException("Could not read this image. It may be corrupted or an unsupported format.")
    }

    fun save(bitmap: Bitmap, out: File, format: Bitmap.CompressFormat, quality: Int = 90) {
        FileOutputStream(out).use { bitmap.compress(format, quality.coerceIn(1, 100), it) }
        if (out.length() == 0L) throw IllegalStateException("Saving the image failed.")
    }

    fun formatForExt(ext: String): Bitmap.CompressFormat = when (ext.lowercase()) {
        "png" -> Bitmap.CompressFormat.PNG; "webp" -> Bitmap.CompressFormat.WEBP_LOSSY; else -> Bitmap.CompressFormat.JPEG
    }

    // 13 — Compress (re-encode JPEG/WebP at quality; PNG is lossless so quality ignored by Android, noted in UI).
    fun compress(input: File, out: File, quality: Int, format: Bitmap.CompressFormat, maxDim: Int? = null): Pair<Long, Long> {
        var bmp = decode(input)
        if (maxDim != null && (bmp.width > maxDim || bmp.height > maxDim)) {
            val s = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
            if (scaled !== bmp) { bmp.recycle(); bmp = scaled }
        }
        save(bmp, out, format, quality); bmp.recycle()
        return input.length() to out.length()
    }

    // 14 — Resizer: exact pixels or percentage, aspect-lock computed by caller/UI helper below.
    fun resizedDimensions(w: Int, h: Int, targetW: Int?, targetH: Int?, keepAspect: Boolean): Pair<Int, Int> {
        if (targetW != null && targetH != null && !keepAspect) return targetW to targetH
        if (targetW != null) return targetW to (h * targetW.toFloat() / w).roundToInt().coerceAtLeast(1)
        if (targetH != null) return (w * targetH.toFloat() / h).roundToInt().coerceAtLeast(1) to targetH
        throw IllegalArgumentException("Enter a target width or height")
    }
    fun resize(input: File, out: File, width: Int, height: Int, format: Bitmap.CompressFormat) {
        val bmp = decode(input); val scaled = Bitmap.createScaledBitmap(bmp, width, height, true)
        save(scaled, out, format, 92); if (scaled !== bmp) scaled.recycle(); bmp.recycle()
    }

    // 15/16/17 — Format conversion (generic engine; PNG->JPG flattens transparency onto chosen background).
    fun convert(input: File, out: File, format: Bitmap.CompressFormat, quality: Int = 92, background: Int = Color.WHITE) {
        var bmp = decode(input)
        if (format == Bitmap.CompressFormat.JPEG && bmp.hasAlpha()) {
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat); canvas.drawColor(background); canvas.drawBitmap(bmp, 0f, 0f, null)
            bmp.recycle(); bmp = flat
        }
        save(bmp, out, format, quality); bmp.recycle()
    }

    // 18 — Cropper engine: crop fractions (0..1) come from the interactive UI; also supports fixed aspect centring.
    fun crop(input: File, out: File, leftF: Float, topF: Float, rightF: Float, bottomF: Float, format: Bitmap.CompressFormat) {
        require(leftF in 0f..1f && rightF in 0f..1f && topF in 0f..1f && bottomF in 0f..1f && rightF > leftF && bottomF > topF) { "Invalid crop area" }
        val bmp = decode(input)
        val x = (bmp.width * leftF).toInt(); val y = (bmp.height * topF).toInt()
        val w = (bmp.width * (rightF - leftF)).toInt().coerceAtLeast(1); val h = (bmp.height * (bottomF - topF)).toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
        save(cropped, out, format, 95); if (cropped !== bmp) cropped.recycle(); bmp.recycle()
    }

    /** Centre-crop to an aspect ratio (used by passport presets and crop presets). */
    fun cropToAspect(input: File, out: File, aspectW: Float, aspectH: Float, format: Bitmap.CompressFormat) {
        val bmp = decode(input); val target = aspectW / aspectH; val src = bmp.width.toFloat() / bmp.height
        val w: Int; val h: Int
        if (src > target) { h = bmp.height; w = (h * target).toInt() } else { w = bmp.width; h = (w / target).toInt() }
        val cropped = Bitmap.createBitmap(bmp, (bmp.width - w) / 2, (bmp.height - h) / 2, w.coerceAtLeast(1), h.coerceAtLeast(1))
        save(cropped, out, format, 95); if (cropped !== bmp) cropped.recycle(); bmp.recycle()
    }

    // 19 — Rotate
    fun rotate(input: File, out: File, degrees: Float, format: Bitmap.CompressFormat) {
        val bmp = decode(input); val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        save(rotated, out, format, 95); if (rotated !== bmp) rotated.recycle(); bmp.recycle()
    }

    // 20 — Flip
    fun flip(input: File, out: File, horizontal: Boolean, format: Bitmap.CompressFormat) {
        val bmp = decode(input); val m = Matrix().apply { if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        save(flipped, out, format, 95); if (flipped !== bmp) flipped.recycle(); bmp.recycle()
    }

    // 22 — Passport: centre-crop to aspect then resize to exact pixel size at 300 DPI-equivalent presets.
    // Verified common sizes: India passport 2x2 inch (600x600 @300dpi) / 35x45mm (413x531 @300dpi); US 2x2 inch.
    data class PassportPreset(val label: String, val widthPx: Int, val heightPx: Int, val note: String)
    val passportPresets = listOf(
        PassportPreset("35 × 45 mm (India/EU, 300 DPI)", 413, 531, "Common for Indian passport/visa photos"),
        PassportPreset("2 × 2 inch (600 × 600, 300 DPI)", 600, 600, "India passport / US passport & visa"),
        PassportPreset("3.5 × 4.5 cm — same as 35×45mm", 413, 531, "Same physical size, listed separately for clarity"),
        PassportPreset("51 × 51 mm (600 × 600)", 600, 600, "Chinese visa photo size")
    )
    fun passport(input: File, out: File, preset: PassportPreset) {
        val bmp = decode(input); val target = preset.widthPx.toFloat() / preset.heightPx; val src = bmp.width.toFloat() / bmp.height
        val w: Int; val h: Int
        if (src > target) { h = bmp.height; w = (h * target).toInt() } else { w = bmp.width; h = (w / target).toInt() }
        val cropped = Bitmap.createBitmap(bmp, (bmp.width - w) / 2, (bmp.height - h) / 2, w.coerceAtLeast(1), h.coerceAtLeast(1))
        val final = Bitmap.createScaledBitmap(cropped, preset.widthPx, preset.heightPx, true)
        save(final, out, Bitmap.CompressFormat.JPEG, 95)
        if (final !== cropped) final.recycle(); if (cropped !== bmp) cropped.recycle(); bmp.recycle()
    }

    // 23 — Color picker: returns ARGB at normalised coords.
    fun colorAt(bitmap: Bitmap, xF: Float, yF: Float): Int {
        val x = (bitmap.width * xF).toInt().coerceIn(0, bitmap.width - 1); val y = (bitmap.height * yF).toInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }
    fun hexOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    // 24 — EXIF viewer: local only, nothing uploaded.
    fun exifInfo(file: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val bmpOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.absolutePath, bmpOpts)
        if (bmpOpts.outWidth > 0) out["Dimensions"] = "${bmpOpts.outWidth} × ${bmpOpts.outHeight} px"
        out["File size"] = FileUtils.formatSize(file.length())
        try {
            val exif = ExifInterface(file.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_MAKE to "Camera make", ExifInterface.TAG_MODEL to "Camera model",
                ExifInterface.TAG_DATETIME_ORIGINAL to "Taken on", ExifInterface.TAG_FOCAL_LENGTH to "Focal length",
                ExifInterface.TAG_F_NUMBER to "Aperture", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "ISO",
                ExifInterface.TAG_EXPOSURE_TIME to "Exposure", ExifInterface.TAG_IMAGE_WIDTH to "EXIF width",
                ExifInterface.TAG_IMAGE_LENGTH to "EXIF height", ExifInterface.TAG_SOFTWARE to "Software"
            )
            for ((tag, label) in tags) exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { out[label] = it }
            exif.latLong?.let { out["Location"] = "${it[0]}, ${it[1]} (stored in this photo — never uploaded by this app)" }
        } catch (e: Exception) { out["EXIF"] = "No readable EXIF metadata in this file" }
        if (out.size <= 2) out["Note"] = "This image contains little or no EXIF metadata."
        return out
    }
}
