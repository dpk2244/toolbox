package com.pushpendratech.toolbox.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * On-device AI tools (Google ML Kit, bundled models — no server, no API key,
 * no uploads). Every operation runs fully on the phone.
 *
 * Threading: ML Kit process() calls are asynchronous (their own threads) and
 * return a Task; we bridge Task -> coroutine with awaitTask(). Detector
 * objects are created per call and closed after use.
 */
object AiTools {

    /** Bridge a Google Play services / ML Kit Task into a coroutine. */
    suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { if (cont.isActive) cont.resumeWithException(IllegalStateException("The operation was cancelled.")) }
    }

    /** Decode a photo with sampling + physical EXIF rotation, so ML Kit sees it upright. */
    fun loadBitmap(file: File, maxDim: Int = 2048): Bitmap {
        var bmp = ImageTools.decode(file, maxDim)
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) { bmp.recycle(); bmp = rotated }
        }
        return bmp
    }

    enum class BgMode { TRANSPARENT, WHITE, BLURRED }

    /** Selfie segmentation: separate the person from the background. Best for portraits/people photos. */
    suspend fun removeBackground(context: Context, input: File, out: File, mode: BgMode) {
        val bmp = loadBitmap(input)
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            val mask = segmenter.process(InputImage.fromBitmap(bmp, 0)).awaitTask()
            val mw = mask.width; val mh = mask.height
            require(mw == bmp.width && mh == bmp.height) { "Segmentation returned an unexpected size." }
            val buffer = mask.buffer
            buffer.rewind()
            val result = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val src = IntArray(bmp.width * bmp.height)
            bmp.getPixels(src, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val dst = IntArray(src.size)
            when (mode) {
                BgMode.TRANSPARENT -> {
                    for (i in src.indices) {
                        val conf = buffer.float.coerceIn(0f, 1f)
                        val a = (conf * 255).roundToInt()
                        dst[i] = (a shl 24) or (src[i] and 0x00FFFFFF)
                    }
                }
                BgMode.WHITE -> {
                    for (i in src.indices) {
                        val conf = buffer.float.coerceIn(0f, 1f)
                        dst[i] = blend(src[i], Color.WHITE, conf)
                    }
                }
                BgMode.BLURRED -> {
                    val blurred = boxBlur(bmp, radius = 18)
                    val bg = IntArray(src.size)
                    blurred.getPixels(bg, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    for (i in src.indices) {
                        val conf = buffer.float.coerceIn(0f, 1f)
                        dst[i] = blend(src[i], bg[i], conf)
                    }
                    blurred.recycle()
                }
            }
            result.setPixels(dst, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            ImageTools.save(result, out, Bitmap.CompressFormat.PNG)
            result.recycle()
        } finally {
            segmenter.close()
            bmp.recycle()
        }
    }

    enum class OcrScript { LATIN, DEVANAGARI }

    /** OCR: extract text from a photo. Returns the full recognized text (may be empty). */
    suspend fun recognizeText(input: File, script: OcrScript): String {
        val bmp = loadBitmap(input, maxDim = 2600)
        val recognizer = when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
        try {
            val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).awaitTask()
            return result.text.trim()
        } finally {
            recognizer.close()
            bmp.recycle()
        }
    }

    /** Face detection + privacy blur. Returns (facesFound) after writing the blurred copy to [out]. */
    suspend fun blurFaces(input: File, out: File): Int {
        val bmp = loadBitmap(input)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            val faces = detector.process(InputImage.fromBitmap(bmp, 0)).awaitTask()
            if (faces.isEmpty()) return 0
            val working = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(working)
            for (face in faces) {
                val b = face.boundingBox
                val left = b.left.coerceIn(0, bmp.width - 1); val top = b.top.coerceIn(0, bmp.height - 1)
                val right = b.right.coerceIn(left + 1, bmp.width); val bottom = b.bottom.coerceIn(top + 1, bmp.height)
                val w = right - left; val h = bottom - top
                if (w < 8 || h < 8) continue
                val region = Bitmap.createBitmap(working, left, top, w, h)
                val radius = (maxOf(w, h) / 14).coerceIn(6, 40)
                val blurredRegion = boxBlur(region, radius)
                canvas.drawBitmap(blurredRegion, left.toFloat(), top.toFloat(), null)
                region.recycle(); blurredRegion.recycle()
            }
            ImageTools.save(working, out, Bitmap.CompressFormat.JPEG, 92)
            working.recycle()
            return faces.size
        } finally {
            detector.close()
            bmp.recycle()
        }
    }

    /** Image labeling: what is in this photo? Returns (label, confidence 0..1) sorted by confidence. */
    suspend fun labelImage(input: File): List<Pair<String, Float>> {
        val bmp = loadBitmap(input)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val labels = labeler.process(InputImage.fromBitmap(bmp, 0)).awaitTask()
            return labels.map { it.text to it.confidence }.sortedByDescending { it.second }
        } finally {
            labeler.close()
            bmp.recycle()
        }
    }

    /** Barcode/QR scan from a still photo. Returns (format name, value) pairs. */
    suspend fun scanBarcodes(input: File): List<Pair<String, String>> {
        val bmp = loadBitmap(input, maxDim = 2600)
        val scanner = BarcodeScanning.getClient()
        try {
            val codes = scanner.process(InputImage.fromBitmap(bmp, 0)).awaitTask()
            return codes.map { code ->
                val format = when (code.format) {
                    Barcode.FORMAT_QR_CODE -> "QR Code"
                    Barcode.FORMAT_EAN_13 -> "EAN-13"
                    Barcode.FORMAT_EAN_8 -> "EAN-8"
                    Barcode.FORMAT_UPC_A -> "UPC-A"
                    Barcode.FORMAT_UPC_E -> "UPC-E"
                    Barcode.FORMAT_CODE_128 -> "Code 128"
                    Barcode.FORMAT_CODE_39 -> "Code 39"
                    Barcode.FORMAT_CODE_93 -> "Code 93"
                    Barcode.FORMAT_CODABAR -> "Codabar"
                    Barcode.FORMAT_ITF -> "ITF"
                    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
                    Barcode.FORMAT_PDF417 -> "PDF417"
                    Barcode.FORMAT_AZTEC -> "Aztec"
                    else -> "Barcode"
                }
                format to (code.rawValue ?: code.displayValue ?: "")
            }.filter { it.second.isNotBlank() }
        } finally {
            scanner.close()
            bmp.recycle()
        }
    }

    /** Language identification (BCP-47 tag, or "und" when unsure). Fully offline, bundled model. */
    suspend fun identifyLanguage(text: String): String {
        val client = LanguageIdentification.getClient()
        try {
            return client.identifyLanguage(text).awaitTask()
        } finally {
            client.close()
        }
    }

    /** Create a translator client for a language pair (tags like "en", "hi"). Caller must close it. */
    fun translator(sourceTag: String, targetTag: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(sourceTag) ?: sourceTag)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(targetTag) ?: targetTag)
            .build()
        return Translation.getClient(options)
    }

    /**
     * Download the language pack if needed (one-time per pair, needs internet,
     * ~30 MB per language), then translate. After the download, translation is
     * fully offline. [onDownloading] is called right before a download starts
     * only when the model is missing — checked via a lightweight translate of
     * an empty string is unreliable, so callers show the note during first use.
     */
    suspend fun translate(client: Translator, text: String): String {
        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder().build()
        client.downloadModelIfNeeded(conditions).awaitTask()
        return client.translate(text).awaitTask()
    }

    /** out = fg blended over bg by confidence: result = fg*conf + bg*(1-conf), per channel, opaque. */
    private fun blend(fg: Int, bg: Int, conf: Float): Int {
        val r = (Color.red(fg) * conf + Color.red(bg) * (1 - conf)).roundToInt()
        val g = (Color.green(fg) * conf + Color.green(bg) * (1 - conf)).roundToInt()
        val b = (Color.blue(fg) * conf + Color.blue(bg) * (1 - conf)).roundToInt()
        return Color.rgb(r, g, b)
    }

    /** Two-pass box blur over a copy of [src]. Radius in pixels (clamped). */
    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 60)
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        // horizontal pass: px -> tmp
        for (y in 0 until h) {
            var sr = 0L; var sg = 0L; var sb = 0L; var sa = 0L
            val row = y * w
            for (x in -r..r) {
                val c = px[row + x.coerceIn(0, w - 1)]
                sr += Color.red(c); sg += Color.green(c); sb += Color.blue(c); sa += Color.alpha(c)
            }
            val n = (2 * r + 1).toLong()
            for (x in 0 until w) {
                tmp[row + x] = Color.argb((sa / n).toInt(), (sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())
                val sub = px[row + (x - r).coerceIn(0, w - 1)]
                val add = px[row + (x + r + 1).coerceIn(0, w - 1)]
                sr += Color.red(add) - Color.red(sub); sg += Color.green(add) - Color.green(sub)
                sb += Color.blue(add) - Color.blue(sub); sa += Color.alpha(add) - Color.alpha(sub)
            }
        }
        // vertical pass: tmp -> out
        for (x in 0 until w) {
            var sr = 0L; var sg = 0L; var sb = 0L; var sa = 0L
            for (y in -r..r) {
                val c = tmp[y.coerceIn(0, h - 1) * w + x]
                sr += Color.red(c); sg += Color.green(c); sb += Color.blue(c); sa += Color.alpha(c)
            }
            val n = (2 * r + 1).toLong()
            for (y in 0 until h) {
                out[y * w + x] = Color.argb((sa / n).toInt(), (sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())
                val sub = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                val add = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                sr += Color.red(add) - Color.red(sub); sg += Color.green(add) - Color.green(sub)
                sb += Color.blue(add) - Color.blue(sub); sa += Color.alpha(add) - Color.alpha(sub)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
