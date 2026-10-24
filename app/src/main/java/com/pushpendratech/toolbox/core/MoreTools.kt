package com.pushpendratech.toolbox.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.time.Period
import java.util.Base64
import java.util.EnumMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v7 expansion batch (non-PDF): image extras, text extras, calculator extras.
 * Same honesty rules as the rest of the app: real work only, achieved values
 * are reported as measured, never rounded into a claim.
 */
object MoreImageTools {

    // ---------- Image to Exact KB ----------
    data class ExactKbResult(
        val achievedBytes: Long, val targetBytes: Long, val reachedTarget: Boolean,
        val alreadySmaller: Boolean, val qualityUsed: Int, val scalePercent: Int
    )

    private fun encodeJpeg(bmp: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    /** Highest JPEG quality (5..100) whose encoding fits [targetBytes]; null if even q=5 is too big. */
    private fun searchQuality(bmp: Bitmap, targetBytes: Long): Pair<ByteArray, Int>? {
        var lo = 5; var hi = 100; var best: Pair<ByteArray, Int>? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val bytes = encodeJpeg(bmp, mid)
            if (bytes.size <= targetBytes) { best = bytes to mid; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    /**
     * Re-encodes as JPEG, searching quality first and then progressively
     * downscaling (100% -> 75% -> 56% -> 42% -> 31% -> 25%) until the output
     * is <= targetKb. If the source file already fits, it is copied untouched
     * and [ExactKbResult.alreadySmaller] says so. If the target is physically
     * unreachable at sane dimensions, the smallest version found is saved and
     * [ExactKbResult.reachedTarget] is false — no fake success.
     */
    fun compressToTargetKb(input: File, out: File, targetKb: Int): ExactKbResult {
        require(targetKb >= 5) { "Target must be at least 5 KB" }
        val targetBytes = targetKb * 1024L
        val originalBytes = input.length()
        if (originalBytes <= targetBytes) {
            input.copyTo(out, overwrite = true)
            return ExactKbResult(originalBytes, targetBytes, reachedTarget = true, alreadySmaller = true, qualityUsed = 100, scalePercent = 100)
        }
        val src = ImageTools.decode(input)
        var scalePct = 100
        var current: Bitmap = src
        try {
            while (true) {
                val found = searchQuality(current, targetBytes)
                if (found != null) {
                    out.writeBytes(found.first)
                    return ExactKbResult(found.first.size.toLong(), targetBytes, reachedTarget = true, alreadySmaller = false, qualityUsed = found.second, scalePercent = scalePct)
                }
                if (scalePct <= 25) break
                scalePct = (scalePct * 0.75).toInt().coerceAtLeast(25)
                val nw = (src.width * scalePct / 100).coerceAtLeast(64)
                val nh = (src.height * scalePct / 100).coerceAtLeast(64)
                val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
                if (current !== src) current.recycle()
                current = scaled
            }
            // Unreachable target: save the smallest encoding we could produce.
            val smallest = encodeJpeg(current, 5)
            out.writeBytes(smallest)
            return ExactKbResult(smallest.size.toLong(), targetBytes, reachedTarget = false, alreadySmaller = false, qualityUsed = 5, scalePercent = scalePct)
        } finally {
            if (current !== src) current.recycle()
            src.recycle()
        }
    }

    // ---------- Collage Maker ----------
    enum class CollageLayout(val label: String) { GRID("Grid"), STRIP_HORIZONTAL("Horizontal strip"), STRIP_VERTICAL("Vertical strip") }

    /** Lays 2–9 images into a fixed canvas, centre-cropping each into its cell. Returns canvas (width, height). */
    fun makeCollage(inputs: List<File>, out: File, layout: CollageLayout, format: Bitmap.CompressFormat): Pair<Int, Int> {
        require(inputs.size in 2..9) { "Pick between 2 and 9 images" }
        val n = inputs.size
        val (cols, rows) = when (layout) {
            CollageLayout.GRID -> { val c = ceil(sqrt(n.toDouble())).toInt(); c to ceil(n.toDouble() / c).toInt() }
            CollageLayout.STRIP_HORIZONTAL -> n to 1
            CollageLayout.STRIP_VERTICAL -> 1 to n
        }
        val (canvasW, canvasH) = when (layout) {
            CollageLayout.GRID -> 2048 to 2048
            CollageLayout.STRIP_HORIZONTAL -> 2048 to 1024
            CollageLayout.STRIP_VERTICAL -> 1024 to 2048
        }
        val cellW = canvasW / cols; val cellH = canvasH / rows
        val canvasBmp = Bitmap.createBitmap(cols * cellW, rows * cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.rgb(28, 28, 28))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        inputs.forEachIndexed { i, file ->
            val bmp = ImageTools.decode(file, 1024)
            try {
                val cx = (i % cols) * cellW; val cy = (i / cols) * cellH
                val scale = max(cellW.toFloat() / bmp.width, cellH.toFloat() / bmp.height)
                val sw = (cellW / scale).toInt().coerceIn(1, bmp.width); val sh = (cellH / scale).toInt().coerceIn(1, bmp.height)
                val sx = (bmp.width - sw) / 2; val sy = (bmp.height - sh) / 2
                canvas.drawBitmap(bmp, Rect(sx, sy, sx + sw, sy + sh), Rect(cx, cy, cx + cellW, cy + cellH), paint)
            } finally { bmp.recycle() }
        }
        ImageTools.save(canvasBmp, out, format, 92)
        canvasBmp.recycle()
        return (cols * cellW) to (rows * cellH)
    }

    // ---------- EXIF Remover ----------
    data class ExifStripResult(val fieldsFound: List<String>, val orientationDegrees: Int, val orientationFlipped: Boolean)

    /**
     * Writes a metadata-free copy: pixels are decoded and re-encoded, so no
     * EXIF/GPS survives by construction. The EXIF orientation IS honoured
     * first (baked into the pixels), otherwise the copy would display
     * sideways once its orientation tag is gone.
     */
    fun stripExif(input: File, out: File, format: Bitmap.CompressFormat): ExifStripResult {
        val before = ImageTools.exifInfo(input)
        val fields = before.keys.filter { it !in setOf("Dimensions", "File size", "Note") }
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try { orientation = ExifInterface(input.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (e: Exception) { /* default normal */ }
        val m = Matrix(); var deg = 0; var flip = false
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> { m.postRotate(90f); deg = 90 }
            ExifInterface.ORIENTATION_ROTATE_180 -> { m.postRotate(180f); deg = 180 }
            ExifInterface.ORIENTATION_ROTATE_270 -> { m.postRotate(270f); deg = 270 }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { m.postScale(-1f, 1f); flip = true }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.postScale(1f, -1f); flip = true }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f); deg = 90; flip = true }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f); deg = 270; flip = true }
        }
        val bmp = ImageTools.decode(input)
        val fixed = if (deg != 0 || flip) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) else bmp
        ImageTools.save(fixed, out, format, 95)
        if (fixed !== bmp) fixed.recycle()
        bmp.recycle()
        return ExifStripResult(fields, deg, flip)
    }

    // ---------- Photo Watermark (text stamp) ----------
    enum class StampPosition(val label: String) { TOP_LEFT("Top left"), TOP_RIGHT("Top right"), CENTER("Center"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right") }

    /** Stamps [text] onto the image. sizePercent = text height as % of image width; opacityPercent 10..100. */
    fun stampText(input: File, out: File, text: String, position: StampPosition, sizePercent: Float, opacityPercent: Int, darkText: Boolean, format: Bitmap.CompressFormat) {
        require(text.isNotBlank()) { "Enter the watermark text" }
        val src = ImageTools.decode(input)
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true); src.recycle()
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkText) Color.BLACK else Color.WHITE
            alpha = (opacityPercent.coerceIn(10, 100) * 255 / 100)
            textSize = bmp.width * (sizePercent.coerceIn(2f, 14f) / 100f)
            setShadowLayer(textSize * 0.06f, 0f, 0f, if (darkText) Color.WHITE else Color.BLACK)
        }
        val pad = bmp.width * 0.03f
        val textW = paint.measureText(text)
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent
        val x = when (position) {
            StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> pad
            StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> bmp.width - pad - textW
            StampPosition.CENTER -> (bmp.width - textW) / 2f
        }
        val baseline = when (position) {
            StampPosition.TOP_LEFT, StampPosition.TOP_RIGHT -> pad - fm.ascent
            StampPosition.BOTTOM_LEFT, StampPosition.BOTTOM_RIGHT -> bmp.height - pad - fm.descent
            StampPosition.CENTER -> (bmp.height - textH) / 2f - fm.ascent
        }
        canvas.drawText(text, x.coerceAtLeast(0f), baseline, paint)
        ImageTools.save(bmp, out, format, 95)
        bmp.recycle()
    }
}

/** Real colour-matrix filters. The same 20-float matrices drive the live Compose preview. */
object PhotoFilters {
    class Filter(val name: String, val matrix: FloatArray)

    private fun fromCm(block: ColorMatrix.() -> Unit): FloatArray = ColorMatrix().apply(block).array
    private fun contrast(c: Float): FloatArray { val t = (1f - c) * 128f; return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f) }
    private fun compose(vararg mats: FloatArray): FloatArray {
        val result = ColorMatrix(mats[0])
        for (i in 1 until mats.size) result.postConcat(ColorMatrix(mats[i]))
        return result.array
    }
    private val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    val all: List<Filter> = listOf(
        Filter("B&W", fromCm { setSaturation(0f) }),
        Filter("Sepia", SEPIA),
        Filter("Vintage", compose(SEPIA, fromCm { setSaturation(0.8f) }, contrast(0.92f))),
        Filter("Cool", fromCm { setScale(0.88f, 1.0f, 1.15f, 1f) }),
        Filter("Warm", fromCm { setScale(1.15f, 1.03f, 0.85f, 1f) }),
        Filter("Vivid", compose(fromCm { setSaturation(1.5f) }, contrast(1.08f))),
        Filter("Fade", compose(fromCm { setSaturation(0.7f) }, contrast(0.8f))),
        Filter("Invert", floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
    )

    fun apply(src: Bitmap, filter: Filter): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.matrix)) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}

object MoreTextTools {

    // ---------- Password Generator ----------
    data class PasswordResult(val password: String, val entropyBits: Int, val strengthLabel: String)

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?"

    fun generatePassword(length: Int, upper: Boolean, lower: Boolean, digits: Boolean, symbols: Boolean): PasswordResult {
        require(length in 8..64) { "Length must be between 8 and 64" }
        val pools = ArrayList<String>()
        if (lower) pools.add(LOWER); if (upper) pools.add(UPPER); if (digits) pools.add(DIGITS); if (symbols) pools.add(SYMBOLS)
        require(pools.isNotEmpty()) { "Select at least one character type" }
        val random = SecureRandom()
        val combined = pools.joinToString("")
        val chars = ArrayList<Char>(length)
        // Guarantee at least one character from every selected pool, then fill and shuffle.
        for (pool in pools) chars.add(pool[random.nextInt(pool.length)])
        while (chars.size < length) chars.add(combined[random.nextInt(combined.length)])
        for (i in chars.lastIndex downTo 1) { val j = random.nextInt(i + 1); val t = chars[i]; chars[i] = chars[j]; chars[j] = t }
        val entropy = (length * log2(combined.length.toDouble())).roundToInt()
        val label = when { entropy < 40 -> "Weak"; entropy < 60 -> "Fair"; entropy < 80 -> "Strong"; else -> "Excellent" }
        return PasswordResult(chars.joinToString(""), entropy, label)
    }

    // ---------- Text Encrypt / Decrypt (AES-256-GCM, PBKDF2-HmacSHA256, 120k rounds) ----------
    private const val CRYPTO_ITERATIONS = 120_000

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, CRYPTO_ITERATIONS, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    /** Output layout (Base64 of): 16-byte salt | 12-byte GCM IV | ciphertext+tag. Nothing is stored anywhere. */
    fun encryptText(plain: String, password: String): String {
        require(plain.isNotEmpty()) { "Enter the text to encrypt" }
        require(password.length >= 4) { "Password must be at least 4 characters" }
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(salt + iv + ct)
    }

    fun decryptText(encoded: String, password: String): String {
        require(password.length >= 4) { "Password must be at least 4 characters" }
        val packed = try { Base64.getMimeDecoder().decode(encoded.trim()) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("This doesn't look like encrypted text produced by this tool.") }
        require(packed.size > 16 + 12 + 16) { "The encrypted text is too short or damaged." }
        val salt = packed.copyOfRange(0, 16); val iv = packed.copyOfRange(16, 28); val ct = packed.copyOfRange(28, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return try { String(cipher.doFinal(ct), Charsets.UTF_8) }
        catch (e: Exception) { throw IllegalArgumentException("Wrong password — or the encrypted text was changed or corrupted. Nothing was recovered.") }
    }

    // ---------- QR & Barcode Generator (ZXing core) ----------
    fun barcodeBitmap(text: String, format: BarcodeFormat, width: Int, height: Int): Bitmap {
        val value = text.trim()
        require(value.isNotEmpty()) { "Enter text, a link or a number first" }
        if (format == BarcodeFormat.EAN_13) {
            require(value.all { it.isDigit() } && (value.length == 12 || value.length == 13)) {
                "EAN-13 needs 12 digits (the check digit is added for you) or a complete, valid 13-digit code"
            }
        }
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8"); put(EncodeHintType.MARGIN, 1)
        }
        val matrix = try { MultiFormatWriter().encode(value, format, width, height, hints) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Could not create this code: ${e.message}") }
        val pixels = IntArray(width * height) { i -> if (matrix.get(i % width, i / width)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    }

    // ---------- Lorem Ipsum ----------
    private val LOREM_WORDS = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod",
        "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "enim", "ad", "minim",
        "veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi", "aliquip", "ex", "ea",
        "commodo", "consequat", "duis", "aute", "irure", "in", "reprehenderit", "voluptate", "velit", "esse",
        "cillum", "fugiat", "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident",
        "sunt", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum", "perspiciatis",
        "unde", "omnis", "iste", "natus", "error", "voluptatem", "accusantium", "doloremque", "laudantium",
        "totam", "rem", "aperiam", "eaque", "ipsa", "quae", "ab", "illo", "inventore", "veritatis", "quasi",
        "architecto", "beatae", "vitae", "dicta", "explicabo", "nemo", "ipsam", "quia", "voluptas", "aspernatur",
        "odit", "aut", "fugit", "consequuntur", "magni", "dolores", "eos", "ratione", "sequi", "nesciunt",
        "neque", "porro", "quisquam", "dolorem", "numquam", "eius", "modi", "tempora", "incidunt", "magnam",
        "quaerat", "minima", "veniam", "nostrum", "exercitationem", "ullam", "corporis", "suscipit",
        "laboriosam", "aliquid", "ex", "commodi", "consequatur", "autem", "vel", "eum", "iure", "quam",
        "nihil", "molestiae", "illum", "quo", "voluptas", "assumenda", "est", "omnis", "repellendus",
        "temporibus", "autem", "quibusdam", "et", "aut", "officiis", "debitis", "aut", "rerum",
        "necessitatibus", "saepe", "eveniet", "et", "voluptates", "repudiandae", "sint", "et", "molestiae",
        "non", "recusandae", "itaque", "earum", "rerum", "hic", "tenetur", "a", "sapiente", "delectus"
    )

    fun loremIpsum(paragraphs: Int, classicStart: Boolean): String {
        require(paragraphs in 1..20) { "Paragraphs must be between 1 and 20" }
        val random = SecureRandom()
        fun sentence(wordCount: Int): String {
            val words = (1..wordCount).map { LOREM_WORDS[random.nextInt(LOREM_WORDS.size)] }
            return words.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
        }
        val out = ArrayList<String>()
        for (p in 0 until paragraphs) {
            val sentences = ArrayList<String>()
            val sentenceCount = 4 + random.nextInt(4)
            for (s in 0 until sentenceCount) {
                if (p == 0 && s == 0 && classicStart) {
                    sentences.add("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
                } else sentences.add(sentence(5 + random.nextInt(9)))
            }
            out.add(sentences.joinToString(" "))
        }
        return out.joinToString("\n\n")
    }
}

object MoreCalculators {

    // ---------- BMI ----------
    data class BmiResult(val bmi: Double, val category: String, val healthyMinKg: Double, val healthyMaxKg: Double)

    /** Standard WHO adult categories. healthyMin/Max = weights at BMI 18.5 and 24.9 for this height. */
    fun bmi(weightKg: Double, heightCm: Double): BmiResult {
        require(weightKg in 2.0..650.0) { "Enter a weight between 2 and 650 kg" }
        require(heightCm in 50.0..300.0) { "Enter a height between 50 and 300 cm" }
        val m = heightCm / 100.0
        val value = weightKg / (m * m)
        val category = when { value < 18.5 -> "Underweight"; value < 25.0 -> "Normal (healthy)"; value < 30.0 -> "Overweight"; else -> "Obese" }
        return BmiResult(value, category, 18.5 * m * m, 24.9 * m * m)
    }

    fun heightCmFromFtIn(feet: Int, inches: Double): Double {
        require(feet in 0..9 && inches in 0.0..11.99) { "Enter feet (0–9) and inches (0–11.9)" }
        return feet * 30.48 + inches * 2.54
    }

    // ---------- SIP ----------
    data class SipResult(val invested: Double, val estimatedGain: Double, val maturityValue: Double)

    /** FV of a monthly SIP paid at the START of each month (annuity due): FV = P × [((1+i)^n − 1)/i] × (1+i), i = annual rate/12. An estimate, not a promise. */
    fun sip(monthly: Double, annualRatePercent: Double, years: Int): SipResult {
        require(monthly > 0) { "Monthly investment must be more than 0" }
        require(annualRatePercent in 0.0..100.0) { "Expected return must be between 0% and 100%" }
        require(years in 1..60) { "Duration must be between 1 and 60 years" }
        val n = years * 12
        val invested = monthly * n
        if (annualRatePercent == 0.0) return SipResult(invested, 0.0, invested)
        val i = annualRatePercent / 1200.0
        val fv = monthly * (((1 + i).pow(n) - 1) / i) * (1 + i)
        return SipResult(invested, fv - invested, fv)
    }

    // ---------- Tip ----------
    data class TipResult(val tipAmount: Double, val total: Double, val perPerson: Double, val tipPerPerson: Double)

    fun tip(bill: Double, tipPercent: Double, people: Int): TipResult {
        require(bill >= 0) { "Bill cannot be negative" }
        require(tipPercent in 0.0..100.0) { "Tip must be between 0% and 100%" }
        require(people >= 1) { "At least 1 person" }
        val tipAmt = bill * tipPercent / 100.0
        val total = bill + tipAmt
        return TipResult(tipAmt, total, total / people, tipAmt / people)
    }

    // ---------- Date Difference ----------
    data class DateDiffResult(val years: Int, val months: Int, val days: Int, val totalDays: Long, val orderSwapped: Boolean)

    /** Calendar-accurate breakdown via java.time.Period (leap years handled). If [to] is before [from], the span is still returned positive and [orderSwapped] says so. */
    fun dateDifference(from: LocalDate, to: LocalDate): DateDiffResult {
        val swapped = to.isBefore(from)
        val start = if (swapped) to else from
        val end = if (swapped) from else to
        val p = Period.between(start, end)
        return DateDiffResult(p.years, p.months, p.days, end.toEpochDay() - start.toEpochDay(), swapped)
    }
}
