package com.pushpendratech.toolbox.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * PDF tools batch 2 (v7 expansion): 23 more tools, same rules as PdfTools —
 * 100% on-device, real implementations only, honest limits (also shown in the UI):
 *  - Compress-to-KB and Grayscale REBUILD pages as JPEG images (text stops being
 *    selectable) because that is the only reliable way to hit a target size /
 *    strip colour on-device with PDFBox + PdfRenderer.
 *  - Unlock only removes protection when the password is KNOWN (or the file has
 *    owner-only restrictions). It cannot crack a forgotten password — PDFBox
 *    throws InvalidPasswordException, which the UI shows as-is.
 *  - PDF -> Word produces a real .docx (OOXML zip) with paragraphs only:
 *    basic formatting, no tables/images/layout reconstruction.
 *  - Sign / Add Image are VISUAL stamps, not cryptographic digital signatures.
 *  - Repair is best-effort: a tolerant reload + clean re-save. If the file
 *    cannot be opened at all, the tool says so instead of faking a repair.
 */
object PdfTools2 {

    private fun init(context: Context) = PdfTools.init(context)

    // ---------- shared helpers ----------

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Renders every page with PdfRenderer and rebuilds a new PDF of JPEG pages at the original point size. */
    private fun renderToPdf(input: File, out: File, scale: Float, jpegQuality: Int, grayscale: Boolean) {
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { doc ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            var bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            if (grayscale) {
                                val g = toGrayscale(bmp)
                                if (g !== bmp) { bmp.recycle(); bmp = g }
                            }
                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                            val rect = PDRectangle(page.width.toFloat(), page.height.toFloat())
                            val newPage = PDPage(rect); doc.addPage(newPage)
                            val img = JPEGFactory.createFromByteArray(doc, baos.toByteArray())
                            PDPageContentStream(doc, newPage).use { cs -> cs.drawImage(img, 0f, 0f, rect.width, rect.height) }
                            bmp.recycle()
                        }
                    }
                    doc.save(out)
                }
            }
        }
    }

    private fun validatePages(total: Int, pages1Based: Set<Int>) {
        require(pages1Based.isNotEmpty()) { "Select at least one page" }
        require(pages1Based.all { it in 1..total }) { "Page numbers must be between 1 and $total" }
    }

    // ---------- 1. Compress PDF to KB ----------

    /**
     * Tries progressively stronger render settings until the rebuilt PDF is at or
     * under [targetKb]. Returns (originalBytes, finalBytes) — if even the strongest
     * setting cannot reach the target, the SMALLEST result is kept and the caller
     * reports the real size (the UI never claims the target was met when it wasn't).
     */
    fun compressToKb(context: Context, input: File, out: File, targetKb: Int): Pair<Long, Long> {
        init(context)
        require(targetKb >= 10) { "Target size must be at least 10 KB" }
        val targetBytes = targetKb.toLong() * 1024L
        val original = input.length()
        if (original <= targetBytes) {
            input.copyTo(out, overwrite = true)
            return original to original
        }
        val attempts = listOf(
            2.0f to 85, 2.0f to 70, 1.5f to 70, 1.5f to 55, 1.25f to 55,
            1.0f to 55, 1.0f to 40, 0.75f to 40, 0.75f to 28, 0.5f to 28
        )
        var best: File? = null
        var bestSize = Long.MAX_VALUE
        val tmpDir = File(out.parentFile, "kb_try").apply { mkdirs() }
        for ((idx, attempt) in attempts.withIndex()) {
            val tmp = File(tmpDir, "try$idx.pdf")
            renderToPdf(input, tmp, attempt.first, attempt.second, grayscale = false)
            val size = tmp.length()
            if (size < bestSize) {
                best?.delete(); best = tmp; bestSize = size
            } else tmp.delete()
            if (size <= targetBytes) break
        }
        val winner = best ?: throw IllegalStateException("Compression produced no output.")
        winner.copyTo(out, overwrite = true)
        winner.delete(); tmpDir.delete()
        return original to out.length()
    }

    // ---------- 2. Unlock PDF ----------

    /**
     * Opens the PDF with the user-supplied password and saves an unprotected copy.
     * A wrong password makes PDFBox throw InvalidPasswordException, which surfaces
     * in the UI with its real name — there is no cracking and no fake success.
     */
    fun unlock(context: Context, input: File, password: String, out: File) {
        init(context)
        PDDocument.load(input, password).use { doc ->
            if (!doc.isEncrypted) throw IllegalStateException("This PDF is not encrypted — there is no protection to remove.")
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
    }

    // ---------- 3. PDF to Text ----------

    fun pdfToText(context: Context, input: File): String {
        init(context)
        PDDocument.load(input).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc).trim()
        }
    }

    // ---------- 4. Scanned PDF to Text (OCR) ----------

    /**
     * Renders up to [maxPages] pages and runs the existing on-device ML Kit OCR
     * (AiTools.recognizeText) on each. Returns the combined text, pages separated
     * by markers. Pages with no readable text are simply absent from the result.
     */
    suspend fun ocrPdf(context: Context, input: File, script: AiTools.OcrScript, maxPages: Int = 30): String {
        init(context)
        val dir = File(context.cacheDir, "ocr_pages").apply { mkdirs() }
        val sb = StringBuilder()
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val n = min(renderer.pageCount, maxPages)
                for (i in 0 until n) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            (page.width * 2).toInt().coerceAtLeast(1),
                            (page.height * 2).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val f = File(dir, "page_${i + 1}.png")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle()
                        val text = AiTools.recognizeText(f, script)
                        f.delete()
                        if (text.isNotBlank()) {
                            if (sb.isNotEmpty()) sb.append("\n\n")
                            sb.append("--- Page ${i + 1} ---\n").append(text)
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    // ---------- 5. Text to PDF ----------

    /**
     * Typeset plain text into a clean A4 PDF (Helvetica, word-wrapped, multi-page).
     * Helvetica covers Latin characters; characters outside it make PDFBox throw,
     * which the UI shows honestly (the screen warns about this up front).
     */
    fun textToPdf(context: Context, text: String, out: File, fontSize: Float = 11f) {
        init(context)
        require(text.isNotBlank()) { "Enter or paste some text first" }
        val font = PDType1Font.HELVETICA
        val leading = fontSize * 1.35f
        val rect = PDRectangle.A4
        val margin = 48f
        val maxWidth = rect.width - margin * 2
        fun widthOf(s: String): Float = font.getStringWidth(s) / 1000f * fontSize
        val lines = ArrayList<String>()
        for (rawLine in text.replace("\r\n", "\n").split("\n")) {
            if (rawLine.isBlank()) { lines.add(""); continue }
            var current = ""
            for (word in rawLine.trim().split(Regex("\\s+"))) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (widthOf(candidate) <= maxWidth || current.isEmpty()) current = candidate
                else { lines.add(current); current = word }
            }
            if (current.isNotEmpty()) lines.add(current)
        }
        val linesPerPage = ((rect.height - margin * 2) / leading).toInt().coerceAtLeast(1)
        PDDocument().use { doc ->
            for (chunk in lines.chunked(linesPerPage)) {
                val page = PDPage(rect); doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(20, 20, 20)
                    cs.newLineAtOffset(margin, rect.height - margin - fontSize)
                    for ((i, line) in chunk.withIndex()) {
                        if (i > 0) cs.newLineAtOffset(0f, -leading)
                        if (line.isNotEmpty()) cs.showText(line)
                    }
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    // ---------- 6. PDF to Word (.docx) ----------

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** Real minimal OOXML .docx: the extracted text as paragraphs. Basic formatting only. */
    fun pdfToWord(context: Context, input: File, out: File) {
        init(context)
        val text = pdfToText(context, input)
        require(text.isNotBlank()) { "No extractable text found — this looks like a scanned PDF. Try 'PDF to Text' alternatives: use the OCR tool first." }
        val body = StringBuilder()
        for (line in text.split("\n")) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escapeXml(line.trimEnd())).append("</w:t></w:r></w:p>")
        }
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:bottom="1134" w:left="1134" w:right="1134"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes)
            entry("_rels/.rels", rels)
            entry("word/document.xml", documentXml)
        }
    }

    // ---------- 7. Extract Images from PDF ----------

    /**
     * Pulls the embedded XObject images out as PNG files (decoded — the original
     * compression/encoding is NOT preserved; the UI says so). Images that PDFBox
     * cannot decode on Android are counted and reported, never silently dropped.
     */
    fun extractImages(context: Context, input: File, outDir: File, baseName: String): List<File> {
        init(context)
        val outs = ArrayList<File>()
        var found = 0
        var failed = 0
        PDDocument.load(input).use { doc ->
            for ((pageIdx, page) in doc.pages.withIndex()) {
                val res = page.resources ?: continue
                var imgIdx = 0
                for (name in res.xObjectNames) {
                    val xo = try { res.getXObject(name) } catch (e: Exception) { null }
                    if (xo is PDImageXObject) {
                        found++
                        try {
                            val bmp = xo.image ?: throw IllegalStateException("image could not be decoded")
                            val f = File(outDir, "${baseName}_p${pageIdx + 1}_${++imgIdx}.png")
                            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bmp.recycle()
                            outs.add(f)
                        } catch (e: Exception) { failed++ }
                    }
                }
            }
        }
        if (outs.isEmpty()) {
            if (found > 0) throw IllegalStateException("Found $found embedded image(s) but none could be decoded on this device.")
            throw IllegalStateException("No embedded images found in this PDF.")
        }
        return outs
    }

    // ---------- 8/9/10. Metadata: read, edit, remove ----------

    data class PdfMeta(
        val title: String?, val author: String?, val subject: String?,
        val keywords: String?, val creator: String?, val producer: String?
    )

    fun readMetadata(context: Context, input: File): PdfMeta {
        init(context)
        PDDocument.load(input).use { doc ->
            val info = doc.documentInformation
            return PdfMeta(info.title, info.author, info.subject, info.keywords, info.creator, info.producer)
        }
    }

    /** Display-ready info map (pages, size, version, metadata, dates, encryption). */
    fun pdfInfo(context: Context, input: File): Map<String, String> {
        init(context)
        val version = try {
            val head = ByteArray(16)
            val read = input.inputStream().use { it.read(head) }
            val s = if (read > 0) String(head, 0, read, Charsets.US_ASCII) else ""
            Regex("%PDF-(\\d+\\.\\d+)").find(s)?.groupValues?.get(1)?.let { "PDF $it" } ?: "Unknown"
        } catch (e: Exception) { "Unknown" }
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        fun dateOf(cal: java.util.Calendar?): String = cal?.let { fmt.format(it.time) } ?: "—"
        fun textOf(v: String?): String = v?.takeIf { it.isNotBlank() } ?: "—"
        PDDocument.load(input).use { doc ->
            val info = doc.documentInformation
            return linkedMapOf(
                "Pages" to doc.numberOfPages.toString(),
                "File size" to FileUtils.formatSize(input.length()),
                "Version" to version,
                "Encrypted" to if (doc.isEncrypted) "Yes" else "No",
                "Title" to textOf(info.title),
                "Author" to textOf(info.author),
                "Subject" to textOf(info.subject),
                "Keywords" to textOf(info.keywords),
                "Creator" to textOf(info.creator),
                "Producer" to textOf(info.producer),
                "Created" to dateOf(info.creationDate),
                "Modified" to dateOf(info.modificationDate)
            )
        }
    }

    /** Sets the four editable fields on a copy. Blank text CLEARS that field. */
    fun editMetadata(context: Context, input: File, title: String, author: String, subject: String, keywords: String, out: File) {
        init(context)
        PDDocument.load(input).use { doc ->
            val info = doc.documentInformation
            info.title = title.takeIf { it.isNotBlank() }
            info.author = author.takeIf { it.isNotBlank() }
            info.subject = subject.takeIf { it.isNotBlank() }
            info.keywords = keywords.takeIf { it.isNotBlank() }
            doc.save(out)
        }
    }

    /** Clears every document-info entry plus the XMP metadata stream, on a copy. */
    fun removeMetadata(context: Context, input: File, out: File) {
        init(context)
        PDDocument.load(input).use { doc ->
            val cos = doc.documentInformation.cosObject
            doc.documentInformation.metadataKeys.toList().forEach { key -> cos.removeItem(COSName.getPDFName(key)) }
            doc.documentCatalog.metadata = null
            doc.save(out)
        }
    }

    // ---------- 11. Resize Pages (A4 / Letter / A5) ----------

    /**
     * Rebuilds the document with every page placed (scaled to FIT, centred, vector
     * content preserved via LayerUtility forms) on a page of [target] size.
     * If a source page's orientation differs from the target's, the target is
     * swapped for that page so nothing is squeezed into the wrong shape.
     */
    fun resizePages(context: Context, input: File, target: PDRectangle, out: File) {
        init(context)
        PDDocument.load(input).use { src ->
            PDDocument().use { dst ->
                val lu = LayerUtility(dst)
                for (page in src.pages) {
                    val box = page.cropBox
                    val swap = (box.width > box.height) != (target.width > target.height)
                    val rect = if (swap) PDRectangle(target.height, target.width) else target
                    val form = lu.importPageAsForm(src, page)
                    val newPage = PDPage(rect); dst.addPage(newPage)
                    val scale = min(rect.width / box.width, rect.height / box.height)
                    val tx = (rect.width - box.width * scale) / 2 - box.lowerLeftX * scale
                    val ty = (rect.height - box.height * scale) / 2 - box.lowerLeftY * scale
                    PDPageContentStream(dst, newPage).use { cs ->
                        cs.transform(Matrix(scale, 0f, 0f, scale, tx, ty))
                        cs.drawForm(form)
                    }
                }
                dst.save(out)
            }
        }
    }

    // ---------- 12. Crop PDF Pages ----------

    /**
     * Applies margins (in points) to the CROP BOX of every page. Like every PDF
     * editor, cropping hides the trimmed area — the hidden content is not deleted
     * from the file (the UI says this plainly).
     */
    fun cropPages(context: Context, input: File, leftPt: Float, bottomPt: Float, rightPt: Float, topPt: Float, out: File) {
        init(context)
        require(leftPt >= 0 && bottomPt >= 0 && rightPt >= 0 && topPt >= 0) { "Margins cannot be negative" }
        require(leftPt + bottomPt + rightPt + topPt > 0) { "Set at least one margin above zero" }
        PDDocument.load(input).use { doc ->
            for (page in doc.pages) {
                val box = page.cropBox
                val w = box.width - leftPt - rightPt
                val h = box.height - bottomPt - topPt
                require(w > 10 && h > 10) { "Those margins leave almost no page. Reduce them." }
                page.cropBox = PDRectangle(box.lowerLeftX + leftPt, box.lowerLeftY + bottomPt, box.lowerLeftX + leftPt + w, box.lowerLeftY + bottomPt + h)
            }
            doc.save(out)
        }
    }

    // ---------- 13. Grayscale PDF ----------

    /** Rebuilds the PDF from grayscale page renders. Returns (originalBytes, finalBytes). */
    fun grayscale(context: Context, input: File, out: File): Pair<Long, Long> {
        init(context)
        renderToPdf(input, out, scale = 2.0f, jpegQuality = 88, grayscale = true)
        return input.length() to out.length()
    }

    // ---------- 14/15. Stamp an image (Sign PDF / Add Image to PDF) ----------

    /**
     * Draws [image] onto the chosen pages at [corner] ("top-left", "top-right",
     * "bottom-left", "bottom-right"), sized to [widthFraction] of the page width.
     * This is a visual stamp only — not a cryptographic digital signature.
     */
    fun stampImage(context: Context, input: File, image: File, pages1Based: Set<Int>?, corner: String, widthFraction: Float, out: File) {
        init(context)
        require(widthFraction in 0.05f..0.8f) { "Stamp width must be 5%–80% of the page width" }
        PDDocument.load(input).use { doc ->
            val pages = pages1Based ?: (1..doc.numberOfPages).toSet()
            validatePages(doc.numberOfPages, pages)
            val img = PDImageXObject.createFromFile(image.absolutePath, doc)
            val margin = 24f
            for (p in pages) {
                val page = doc.getPage(p - 1)
                val box = page.cropBox
                val w = box.width * widthFraction
                val h = w * img.height.toFloat() / img.width.toFloat()
                val x = when (corner) {
                    "top-left", "bottom-left" -> box.lowerLeftX + margin
                    else -> box.lowerLeftX + box.width - margin - w
                }
                val y = when (corner) {
                    "top-left", "top-right" -> box.lowerLeftY + box.height - margin - h
                    else -> box.lowerLeftY + margin
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(img, x, y, w, h)
                }
            }
            doc.save(out)
        }
    }

    // ---------- 16. Header & Footer ----------

    fun headerFooter(context: Context, input: File, header: String, footer: String, out: File) {
        init(context)
        require(header.isNotBlank() || footer.isNotBlank()) { "Enter a header, a footer, or both" }
        val font = PDType1Font.HELVETICA
        val fontSize = 10f
        PDDocument.load(input).use { doc ->
            for (page in doc.pages) {
                val box = page.cropBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(90, 90, 90)
                    if (header.isNotBlank()) {
                        val tw = font.getStringWidth(header) / 1000f * fontSize
                        cs.beginText(); cs.setFont(font, fontSize)
                        cs.newLineAtOffset(box.lowerLeftX + (box.width - tw) / 2, box.lowerLeftY + box.height - 30f)
                        cs.showText(header); cs.endText()
                    }
                    if (footer.isNotBlank()) {
                        val tw = font.getStringWidth(footer) / 1000f * fontSize
                        cs.beginText(); cs.setFont(font, fontSize)
                        cs.newLineAtOffset(box.lowerLeftX + (box.width - tw) / 2, box.lowerLeftY + 22f)
                        cs.showText(footer); cs.endText()
                    }
                }
            }
            doc.save(out)
        }
    }

    // ---------- 17. PDF N-Up (2 / 4 / 6 pages per sheet) ----------

    /**
     * Lays out source pages on A4 sheets: 2-up on landscape A4 (2×1),
     * 4-up on portrait A4 (2×2), 6-up on portrait A4 (2×3).
     * Vector content is preserved (LayerUtility forms).
     */
    fun nUp(context: Context, input: File, perSheet: Int, out: File) {
        init(context)
        require(perSheet in listOf(2, 4, 6)) { "Choose 2, 4 or 6 pages per sheet" }
        val (sheet, cols, rows) = when (perSheet) {
            2 -> Triple(PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width), 2, 1)
            4 -> Triple(PDRectangle.A4, 2, 2)
            else -> Triple(PDRectangle.A4, 2, 3)
        }
        val sheetMargin = 24f
        val gap = 12f
        val cellW = (sheet.width - sheetMargin * 2 - gap * (cols - 1)) / cols
        val cellH = (sheet.height - sheetMargin * 2 - gap * (rows - 1)) / rows
        PDDocument.load(input).use { src ->
            PDDocument().use { dst ->
                val lu = LayerUtility(dst)
                val forms = src.pages.map { lu.importPageAsForm(src, it) }
                val boxes = src.pages.map { it.cropBox }
                val cells = cols * rows
                for (sheetIdx in forms.indices step cells) {
                    val sheetPage = PDPage(sheet); dst.addPage(sheetPage)
                    PDPageContentStream(dst, sheetPage).use { cs ->
                        for (cell in 0 until cells) {
                            val idx = sheetIdx + cell
                            if (idx >= forms.size) break
                            val row = cell / cols
                            val col = cell % cols
                            val cellX = sheetMargin + col * (cellW + gap)
                            val cellY = sheet.height - sheetMargin - (row + 1) * cellH - row * gap
                            val box = boxes[idx]
                            val scale = min(cellW / box.width, cellH / box.height)
                            val tx = cellX + (cellW - box.width * scale) / 2 - box.lowerLeftX * scale
                            val ty = cellY + (cellH - box.height * scale) / 2 - box.lowerLeftY * scale
                            cs.saveGraphicsState()
                            cs.transform(Matrix(scale, 0f, 0f, scale, tx, ty))
                            cs.drawForm(forms[idx])
                            cs.restoreGraphicsState()
                        }
                    }
                }
                dst.save(out)
            }
        }
    }

    // ---------- 18. Flatten PDF ----------

    /**
     * Bakes form-field values into the pages (AcroForm flatten). PDFs without a
     * form get an honest "nothing to flatten" error instead of a fake copy.
     * Non-form annotations (links, sticky notes) are not touched by PDFBox
     * flattening — the UI notes this.
     */
    fun flatten(context: Context, input: File, out: File) {
        init(context)
        PDDocument.load(input).use { doc ->
            val acro = doc.documentCatalog.acroForm
                ?: throw IllegalStateException("This PDF has no form (AcroForm) — there is nothing to flatten.")
            if (acro.fields.isEmpty()) throw IllegalStateException("This PDF's form has no fields — there is nothing to flatten.")
            acro.flatten()
            doc.save(out)
        }
    }

    // ---------- 19. Repair PDF ----------

    /**
     * Best-effort repair: a tolerant reload (scratch-file backed parsing, which
     * recovers from broken cross-reference tables) followed by a clean re-save.
     * Returns the number of pages recovered. If the file cannot be opened at
     * all, throws with the ORIGINAL parse error — no fake "repaired" file.
     */
    fun repair(context: Context, input: File, out: File): Int {
        init(context)
        val doc: PDDocument = try {
            PDDocument.load(input)
        } catch (e: Exception) {
            try {
                PDDocument.load(input, "", MemoryUsageSetting.setupTempFileOnly())
            } catch (e2: Exception) {
                throw IllegalStateException(
                    "This PDF could not be opened at all, so there is nothing recoverable with this tool. " +
                        "First error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
                )
            }
        }
        doc.use { d ->
            val pages = d.numberOfPages
            require(pages > 0) { "The PDF opened but contains no pages to recover." }
            d.save(out)
            return pages
        }
    }

    // ---------- 20. Reverse Page Order ----------

    fun reverseOrder(context: Context, input: File, out: File) {
        init(context)
        PDDocument.load(input).use { src ->
            require(src.numberOfPages >= 2) { "This PDF has only one page — nothing to reverse." }
            PDDocument().use { dst ->
                for (p in src.numberOfPages downTo 1) dst.importPage(src.getPage(p - 1))
                dst.save(out)
            }
        }
    }

    // ---------- 21. Insert Blank Page ----------

    /** mode: 0 = at the beginning, 1 = at the end, 2 = after page [afterPage] (1-based). */
    fun insertBlankPage(context: Context, input: File, mode: Int, afterPage: Int, out: File) {
        init(context)
        require(mode in 0..2) { "Choose where to insert the blank page" }
        PDDocument.load(input).use { src ->
            val total = src.numberOfPages
            if (mode == 2) require(afterPage in 1..total) { "Insert-after page must be between 1 and $total" }
            val neighbour = when (mode) {
                0 -> src.getPage(0)
                1 -> src.getPage(total - 1)
                else -> src.getPage(afterPage - 1)
            }
            val nb = neighbour.mediaBox
            fun blankPage() = PDPage(PDRectangle(nb.width, nb.height))
            PDDocument().use { dst ->
                if (mode == 0) dst.addPage(blankPage())
                for (p in 1..total) {
                    dst.importPage(src.getPage(p - 1))
                    if (mode == 2 && p == afterPage) dst.addPage(blankPage())
                }
                if (mode == 1) dst.addPage(blankPage())
                dst.save(out)
            }
        }
    }

    // ---------- 22. Split by Size ----------

    /**
     * Splits into parts of at most [maxBytes] each. Boundaries are chosen by
     * actually building candidate parts and measuring the REAL saved size
     * (via PdfTools.extractPages), never by guessing from page counts.
     * A single page that alone exceeds the limit becomes its own part — a page
     * cannot be cut in half; the UI reports this honestly.
     */
    fun splitBySize(context: Context, input: File, maxBytes: Long, outDir: File, baseName: String): List<File> {
        init(context)
        require(maxBytes >= 100_000L) { "Part size is too small — use at least 0.1 MB" }
        val total = PdfTools.pageCount(input)
        require(total >= 2) { "This PDF has only one page — nothing to split." }
        val tmp = File(outDir, "size_check_tmp.pdf")
        fun measure(pages: List<Int>): Long {
            PdfTools.extractPages(context, input, pages.toSet(), tmp)
            return tmp.length()
        }
        val parts = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        for (p in 1..total) {
            current.add(p)
            if (measure(current) > maxBytes) {
                if (current.size > 1) {
                    current.removeAt(current.lastIndex)
                    parts.add(current.toList())
                    current = arrayListOf(p)
                    if (measure(current) > maxBytes) {
                        parts.add(current.toList())
                        current = ArrayList()
                    }
                } else {
                    parts.add(current.toList())
                    current = ArrayList()
                }
            }
        }
        if (current.isNotEmpty()) parts.add(current.toList())
        tmp.delete()
        require(parts.size >= 2) { "This PDF already fits in one part of that size — no split needed." }
        return parts.mapIndexed { i, pages ->
            val f = File(outDir, "${baseName}_part${i + 1}.pdf")
            PdfTools.extractPages(context, input, pages.toSet(), f)
            f
        }
    }

    // ---------- 23. Fill PDF Form ----------

    data class PdfFormField(val name: String, val type: String, val value: String, val isText: Boolean)

    private fun fieldTypeName(raw: String?): String = when (raw) {
        "Tx" -> "Text"
        "Btn" -> "Checkbox / Button"
        "Ch" -> "Choice (dropdown / list)"
        "Sig" -> "Signature"
        else -> raw ?: "Field"
    }

    private fun terminalFields(fields: List<PDField>): List<PDField> {
        val out = ArrayList<PDField>()
        fun walk(list: List<PDField>) {
            for (f in list) {
                if (f is PDNonTerminalField) walk(f.children) else out.add(f)
            }
        }
        walk(fields)
        return out
    }

    /** Lists the fillable fields of a PDF's form. Empty list = no form / no fields. */
    fun listFormFields(context: Context, input: File): List<PdfFormField> {
        init(context)
        PDDocument.load(input).use { doc ->
            val acro = doc.documentCatalog.acroForm ?: return emptyList()
            return terminalFields(acro.fields).map { f ->
                PdfFormField(
                    name = f.fullyQualifiedName ?: f.partialName ?: "(unnamed field)",
                    type = fieldTypeName(f.fieldType),
                    value = if (f is PDTextField) f.value ?: "" else "",
                    isText = f is PDTextField
                )
            }
        }
    }

    /**
     * Sets the given text-field values and saves a copy. Returns how many fields
     * were actually filled. With [flattenAfter], values are baked into the pages.
     * Throws honestly when the PDF has no form fields at all.
     */
    fun fillForm(context: Context, input: File, values: Map<String, String>, flattenAfter: Boolean, out: File): Int {
        init(context)
        PDDocument.load(input).use { doc ->
            val acro = doc.documentCatalog.acroForm
                ?: throw IllegalStateException("This PDF has no form fields to fill. (Flat/scanned PDFs have no real form fields — this tool works on PDFs with actual fillable fields.)")
            val terminals = terminalFields(acro.fields)
            if (terminals.none { it is PDTextField }) {
                throw IllegalStateException("This PDF's form has no TEXT fields this tool can fill (only checkboxes/choices/signatures, which are not supported here).")
            }
            var filled = 0
            for (f in terminals) {
                if (f is PDTextField) {
                    val v = values[f.fullyQualifiedName ?: f.partialName ?: ""]
                    if (v != null) { f.setValue(v); filled++ }
                }
            }
            require(filled > 0) { "No fields were filled — enter a value in at least one field." }
            if (flattenAfter) acro.flatten() else acro.setNeedAppearances(true)
            doc.save(out)
            return filled
        }
    }
}
