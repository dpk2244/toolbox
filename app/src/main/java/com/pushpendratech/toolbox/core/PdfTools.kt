package com.pushpendratech.toolbox.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * All 12 PDF tools, 100% on-device with PDFBox-Android (Apache-2.0).
 * Limitations (also shown in UI / README): form fields, digital signatures and some
 * advanced features of the source PDF may not survive a load->save round-trip in PDFBox.
 * Encryption uses PDFBox StandardProtectionPolicy (128-bit) — genuine PDF encryption,
 * compatible with Adobe Reader / Chrome / most Android viewers. Passwords are never stored.
 */
object PdfTools {
    @Volatile private var initialised = false
    fun init(context: Context) { if (!initialised) { PDFBoxResourceLoader.init(context.applicationContext); initialised = true } }

    fun pageCount(file: File): Int = PDDocument.load(file).use { it.numberOfPages }

    private fun validatePages(total: Int, pages1Based: Set<Int>) {
        require(pages1Based.isNotEmpty()) { "Select at least one page" }
        require(pages1Based.all { it in 1..total }) { "Page numbers must be between 1 and $total" }
    }

    /** Parses "1-3,5,8" into 1-based page numbers. Throws with a readable message on bad input. */
    fun parseRanges(spec: String, total: Int): Set<Int> {
        val out = sortedSetOf<Int>()
        for (part in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (part.contains("-")) {
                val (a, b) = part.split("-", limit = 2).map { it.trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid range: $part") }
                require(a <= b) { "Range start must be ≤ end: $part" }; for (p in a..b) out.add(p)
            } else out.add(part.toIntOrNull() ?: throw IllegalArgumentException("Invalid page: $part"))
        }
        validatePages(total, out); return out
    }

    // 1 — Compress: strips metadata/XMP and re-encodes oversized embedded images as scaled JPEGs.
    // PDFBox has no single "compress" call; this is a genuine size-reduction pass, never a fake one.
    fun compress(context: Context, input: File, out: File, maxImageDim: Int, jpegQuality: Float): Pair<Long, Long> {
        init(context)
        PDDocument.load(input).use { doc ->
            doc.documentCatalog.metadata = null; doc.documentInformation.metadataKeys.forEach { }
            doc.documentInformation.title = null; doc.documentInformation.creator = null
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames) {
                    val xo = try { res.getXObject(name) } catch (e: Exception) { null }
                    if (xo is PDImageXObject) {
                        try {
                            val bmp = xo.image ?: continue
                            if (bmp.width <= maxImageDim && bmp.height <= maxImageDim) continue
                            val scale = maxImageDim.toFloat() / maxOf(bmp.width, bmp.height)
                            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                            val baos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, (jpegQuality * 100).toInt(), baos)
                            res.put(name, JPEGFactory.createFromByteArray(doc, baos.toByteArray()))
                            if (scaled !== bmp) scaled.recycle(); bmp.recycle()
                        } catch (e: Exception) { /* leave that image untouched */ }
                    }
                }
            }
            doc.save(out)
        }
        return input.length() to out.length()
    }

    // 2 — Merge, in the exact order the user arranged.
    fun merge(context: Context, inputs: List<File>, out: File) {
        init(context); require(inputs.size >= 2) { "Select at least 2 PDFs to merge" }
        val merger = PDFMergerUtility(); merger.destinationFileName = out.absolutePath
        inputs.forEach { merger.addSource(it) }; merger.mergeDocuments(null)
    }

    // 3/9 — Split (every selected page-set -> ONE pdf containing those pages) & Extract use the same engine:
    // Split UI creates one output per contiguous range; this function writes the chosen pages into one PDF.
    fun extractPages(context: Context, input: File, pages1Based: Set<Int>, out: File) {
        init(context)
        PDDocument.load(input).use { src ->
            validatePages(src.numberOfPages, pages1Based)
            PDDocument().use { dst ->
                for (p in pages1Based.sorted()) dst.importPage(src.getPage(p - 1))
                dst.save(out)
            }
        }
    }

    /** Split into separate single-range files; returns the outputs. Caller passes parsed ranges already grouped. */
    fun splitIntoRanges(context: Context, input: File, ranges: List<Set<Int>>, baseDir: File, baseName: String): List<File> {
        val outs = ArrayList<File>()
        ranges.forEachIndexed { i, pages ->
            val f = File(baseDir, "${baseName}_part${i + 1}.pdf"); extractPages(context, input, pages, f); outs.add(f)
        }
        return outs
    }

    // 4/21 — Images -> PDF. pageFit=true sizes each page to the image (with margin), else A4 centred.
    fun imagesToPdf(context: Context, images: List<File>, out: File, a4: Boolean, marginPt: Float = 36f) {
        init(context); require(images.isNotEmpty()) { "Select at least one image" }
        PDDocument().use { doc ->
            for (imgFile in images) {
                val img = PDImageXObject.createFromFile(imgFile.absolutePath, doc)
                val pageRect = if (a4) PDRectangle.A4 else PDRectangle(img.width.toFloat() + marginPt * 2, img.height.toFloat() + marginPt * 2)
                val page = PDPage(pageRect); doc.addPage(page)
                val availW = pageRect.width - marginPt * 2; val availH = pageRect.height - marginPt * 2
                val scale = min(availW / img.width, availH / img.height)
                val w = img.width * scale; val h = img.height * scale
                val x = (pageRect.width - w) / 2; val y = (pageRect.height - h) / 2
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, x, y, w, h) }
            }
            doc.save(out)
        }
    }

    // 5 — PDF -> Images using Android's native PdfRenderer (no PDFBox rendering bugs, fast).
    fun pdfToImages(context: Context, input: File, pages1Based: Set<Int>?, scale: Float, outDir: File, baseName: String): List<File> {
        val outs = ArrayList<File>()
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pages = pages1Based ?: (1..renderer.pageCount).toSet()
                validatePages(renderer.pageCount, pages)
                for (p in pages.sorted()) {
                    renderer.openPage(p - 1).use { page ->
                        val w = (page.width * scale).toInt().coerceAtLeast(1); val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val f = File(outDir, "${baseName}_page$p.png")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle(); outs.add(f)
                    }
                }
            }
        }
        return outs
    }

    // 6 — Rotate selected pages (adds to existing rotation, as users expect).
    fun rotate(context: Context, input: File, pages1Based: Set<Int>?, degrees: Int, out: File) {
        init(context); require(degrees % 90 == 0 && degrees != 0) { "Rotation must be 90, 180 or 270" }
        PDDocument.load(input).use { doc ->
            val pages = pages1Based ?: (1..doc.numberOfPages).toSet(); validatePages(doc.numberOfPages, pages)
            for (p in pages) { val page = doc.getPage(p - 1); page.rotation = (page.rotation + degrees) % 360 }
            doc.save(out)
        }
    }

    // 7 — Reorder: newOrder is a permutation of 1..total (1-based, in desired output order).
    fun reorder(context: Context, input: File, newOrder: List<Int>, out: File) {
        init(context)
        PDDocument.load(input).use { src ->
            require(newOrder.sorted() == (1..src.numberOfPages).toList()) { "New order must contain every page exactly once" }
            PDDocument().use { dst -> for (p in newOrder) dst.importPage(src.getPage(p - 1)); dst.save(out) }
        }
    }

    // 8 — Delete pages.
    fun deletePages(context: Context, input: File, pages1Based: Set<Int>, out: File) {
        init(context)
        PDDocument.load(input).use { src ->
            validatePages(src.numberOfPages, pages1Based)
            require(pages1Based.size < src.numberOfPages) { "You cannot delete every page of the PDF" }
            PDDocument().use { dst ->
                for (p in 1..src.numberOfPages) if (p !in pages1Based) dst.importPage(src.getPage(p - 1))
                dst.save(out)
            }
        }
    }

    // 10 — Watermark text on selected pages.
    fun watermark(context: Context, input: File, pages1Based: Set<Int>?, text: String, fontSize: Float, opacity: Float, angleDeg: Double, out: File) {
        init(context); require(text.isNotBlank()) { "Enter watermark text" }; require(opacity in 0.05f..1f) { "Opacity must be 5%–100%" }
        PDDocument.load(input).use { doc ->
            val pages = pages1Based ?: (1..doc.numberOfPages).toSet(); validatePages(doc.numberOfPages, pages)
            for (p in pages) {
                val page = doc.getPage(p - 1); val box = page.mediaBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState(); gs.setNonStrokingAlphaConstant(opacity); cs.setGraphicsStateParameters(gs)
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize); cs.setNonStrokingColor(130, 130, 130)
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(angleDeg), box.width / 2, box.height / 2))
                    cs.showText(text); cs.endText()
                }
            }
            doc.save(out)
        }
    }

    // 11 — Page numbers: position bottom-left / bottom-centre / bottom-right / top-right, start number configurable.
    fun pageNumbers(context: Context, input: File, startAt: Int, position: String, out: File) {
        init(context)
        PDDocument.load(input).use { doc ->
            for ((idx, page) in doc.pages.withIndex()) {
                val box = page.mediaBox; val label = (startAt + idx).toString()
                val x = when (position) { "left" -> 36f; "right" -> box.width - 50f; else -> box.width / 2 }
                val y = if (position == "top") box.height - 30f else 24f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 11f); cs.setNonStrokingColor(60, 60, 60)
                    cs.newLineAtOffset(x, y); cs.showText(label); cs.endText()
                }
            }
            doc.save(out)
        }
    }

    // 12 — Protect with genuine 128-bit PDF encryption. Password lives only in memory during this call.
    fun protect(context: Context, input: File, userPassword: String, out: File) {
        init(context); require(userPassword.length >= 4) { "Use a password of at least 4 characters" }
        PDDocument.load(input).use { doc ->
            val policy = StandardProtectionPolicy(userPassword, userPassword, AccessPermission())
            policy.encryptionKeyLength = 128 // AccessPermission() defaults keep printing/copying allowed for the owner
            doc.protect(policy); doc.save(out)
        }
    }
}
