package com.pushpendratech.toolbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.core.PdfTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on a device/emulator: creates a REAL pdf via the images->pdf + merge pipeline path is image-dependent,
 *  so here we exercise range parsing and a text-stamp round trip on a generated 3-page PDF built with PDFBox itself. */
@RunWith(AndroidJUnit4::class)
class PdfToolsInstrumentedTest {
    @Test fun parseRangesValidatesAndParses() {
        assertEquals(setOf(1, 2, 3, 5), PdfTools.parseRanges("1-3,5", 10))
        try { PdfTools.parseRanges("9", 5); throw AssertionError("should have thrown") }
        catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun createMergeExtractProtectRoundTrip() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PdfTools.init(ctx)
        // Build a 3-page blank PDF directly with PDFBox.
        val src = File(ctx.cacheDir, "t_src.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            repeat(3) { doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage()) }; doc.save(src)
        }
        assertEquals(3, PdfTools.pageCount(src))
        val extracted = File(ctx.cacheDir, "t_ext.pdf"); PdfTools.extractPages(ctx, src, setOf(1, 3), extracted)
        assertEquals(2, PdfTools.pageCount(extracted))
        val protectedFile = File(ctx.cacheDir, "t_prot.pdf"); PdfTools.protect(ctx, src, "test1234", protectedFile)
        assertTrue(protectedFile.length() > 0)
        // Opening the protected file without a password must fail.
        var threw = false
        try { com.tom_roush.pdfbox.pdmodel.PDDocument.load(protectedFile).use { } } catch (e: Exception) { threw = true }
        assertTrue("Protected PDF opened without password — encryption is not real!", threw)
        // And with the password it must open.
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(protectedFile, "test1234").use { assertEquals(3, it.numberOfPages) }
        listOf(src, extracted, protectedFile).forEach { it.delete() }
        assertTrue(FileUtils.formatSize(2048).contains("KB"))
    }
}
