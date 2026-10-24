package com.pushpendratech.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.AiTools
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.core.PdfTools
import com.pushpendratech.toolbox.core.PdfTools2
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PDF_MIME2 = arrayOf("application/pdf")
private val IMG_MIME2 = arrayOf("image/*")

/**
 * Screens for the 23 v7 PDF tools (ids pdf_compress_kb … pdf_fill_form).
 * Wiring: route these ids to this composable exactly like PdfToolScreen —
 * signature mirrors PdfToolScreen(tool, isFav, onToggleFav, onBack).
 * Every body follows the PdfImageScreens patterns: rememberFilePicker,
 * PickedCard, ProcessButton, scope.runJob, ResultPanel. Tools with inherent
 * limits carry an honest NoteCard.
 */
@Composable
fun PdfToolScreen2(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        when (tool.id) {
            "pdf_compress_kb" -> CompressKbBody()
            "pdf_unlock" -> UnlockBody()
            "pdf_to_text" -> PdfToTextBody()
            "pdf_ocr_text" -> PdfOcrBody()
            "pdf_text_to_pdf" -> TextToPdfBody()
            "pdf_to_word" -> PdfToWordBody()
            "pdf_extract_images" -> ExtractImagesBody()
            "pdf_info" -> PdfInfoBody()
            "pdf_edit_metadata" -> EditMetadataBody()
            "pdf_remove_metadata" -> RemoveMetadataBody()
            "pdf_resize_pages" -> ResizePagesBody()
            "pdf_crop" -> CropBody()
            "pdf_grayscale" -> GrayscaleBody()
            "pdf_sign" -> SignBody()
            "pdf_add_image" -> AddImageBody()
            "pdf_header_footer" -> HeaderFooterBody()
            "pdf_nup" -> NUpBody()
            "pdf_flatten" -> FlattenBody()
            "pdf_repair" -> RepairBody()
            "pdf_reverse" -> ReverseBody()
            "pdf_insert_blank" -> InsertBlankBody()
            "pdf_split_size" -> SplitSizeBody()
            "pdf_fill_form" -> FillFormBody()
            else -> Text("Unknown PDF tool: ${tool.id}")
        }
    }
}

/** Page count of the picked PDF (0 = not loaded / could not be read). Mirrors PdfImageScreens. */
@Composable
private fun rememberPdfPageCount(picked: PickedFile?): Int {
    val context = LocalContext.current
    var total by remember { mutableStateOf(0) }
    LaunchedEffect(picked) {
        total = if (picked == null) 0 else try {
            withContext(Dispatchers.IO) {
                val f = FileUtils.copyUriToCache(context, picked.uri)
                val n = PdfTools.pageCount(f)
                FileUtils.cleanupTemp(f)
                n
            }
        } catch (e: Exception) { 0 }
    }
    return total
}

@Composable
private fun CornerChips(selected: String, onSelect: (String) -> Unit) {
    Text("Position on the page:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "bottom-right" to "Bottom right", "bottom-left" to "Bottom left",
            "top-right" to "Top right", "top-left" to "Top left"
        ).forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, label = { Text(label) }) }
    }
}

// ---------- 1. Compress PDF to KB ----------

@Composable
private fun CompressKbBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var target by remember { mutableStateOf("200") }
    Text("Target size (form uploads often ask for 100–500 KB):")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(100, 200, 500, 1024).forEach { kb ->
            FilterChip(target == kb.toString(), { target = kb.toString() }, label = { Text("$kb KB") })
        }
    }
    OutlinedTextField(target, { target = it.filter { c -> c.isDigit() } }, label = { Text("Target size in KB") }, modifier = Modifier.fillMaxWidth())
    NoteCard("To reach an exact size, pages are rebuilt as images and quality is lowered step by step — so text in the result is NOT selectable/copyable, and pages get softer at very small targets. If the target is impossible for this PDF, you get the smallest possible file and the real size is shown — never a fake claim.")
    ProcessButton(picked != null && target.toIntOrNull() != null, running, {
        scope.runJob({ state = it }) {
            val kb = target.toIntOrNull() ?: throw IllegalArgumentException("Enter the target size in KB")
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "compressed_${kb}kb", "pdf")
            val (o, n) = PdfTools2.compressToKb(context, input, out, kb)
            FileUtils.cleanupTemp(input)
            listOf(out) to when {
                o <= kb * 1024L -> "The original (${FileUtils.formatSize(o)}) was already under ${kb} KB — an unchanged copy was made."
                n <= kb * 1024L -> "Done: ${FileUtils.formatSize(o)} → ${FileUtils.formatSize(n)} (target ${kb} KB met)."
                else -> "Smallest possible for this PDF: ${FileUtils.formatSize(n)} — still above your ${kb} KB target, so no success is claimed. Try removing pages first, or a bigger target."
            }
        }
    }, "Compress to target KB")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 2. Unlock PDF ----------

@Composable
private fun UnlockBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var pw by remember { mutableStateOf("") }
    NoteCard("This removes the password from a PDF when you KNOW the password (or the PDF only has copy/print restrictions with no open-password). It cannot crack or recover a forgotten password — a wrong password will simply fail, and the error will be shown.")
    OutlinedTextField(pw, { pw = it }, label = { Text("Current password (leave empty if none)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "unlocked", "pdf")
            PdfTools2.unlock(context, input, pw, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Protection removed — this copy opens without a password. Your original file is untouched."
        }
    }, "Unlock PDF")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 3. PDF to Text ----------

@Composable
private fun PdfToTextBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Extracts the text that is really stored in the PDF. Scanned/image PDFs have no stored text — for those, use 'Scanned PDF to Text (OCR)' instead.")
    var extracted by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    ProcessButton(picked != null, running, {
        state = RunState.Running
        scope.launch {
            try {
                val input = FileUtils.copyUriToCache(context, picked!!.uri)
                val text = withContext(Dispatchers.IO) { PdfTools2.pdfToText(context, input) }
                FileUtils.cleanupTemp(input)
                if (text.isBlank()) {
                    extracted = null
                    state = RunState.Failed("No extractable text was found. This looks like a scanned/image PDF — try 'Scanned PDF to Text (OCR)'.")
                } else {
                    extracted = text
                    val out = FileUtils.newOutput(context, "pdf_text", "txt")
                    out.writeText(text)
                    state = RunState.Done(listOf(out), "Extracted ${text.length} characters — full text below.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { state = RunState.Failed("${e.javaClass.simpleName}: ${e.message ?: "Text extraction failed on this PDF."}") }
        }
    }, "Extract text")
    extracted?.let { text ->
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text("Extracted text") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp))
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy text") }
    }
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 4. Scanned PDF to Text (OCR) ----------

@Composable
private fun PdfOcrBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var script by remember { mutableStateOf(AiTools.OcrScript.LATIN) }
    Text("Scanned text is mostly:")
    Row {
        RadioButton(script == AiTools.OcrScript.LATIN, { script = AiTools.OcrScript.LATIN }); Text("English / Latin", Modifier.padding(top = 12.dp))
        RadioButton(script == AiTools.OcrScript.DEVANAGARI, { script = AiTools.OcrScript.DEVANAGARI }); Text("Hindi (देवनागरी)", Modifier.padding(top = 12.dp))
    }
    NoteCard("For scanned PDFs: each page is read with on-device OCR (Google ML Kit) — nothing is uploaded. Long PDFs are read up to the first 30 pages. Clear, straight scans work best; handwriting usually does not read well.")
    var extracted by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    ProcessButton(picked != null, running, {
        state = RunState.Running
        scope.launch {
            try {
                val input = FileUtils.copyUriToCache(context, picked!!.uri)
                val text = withContext(Dispatchers.IO) { PdfTools2.ocrPdf(context, input, script) }
                FileUtils.cleanupTemp(input)
                if (text.isBlank()) {
                    extracted = null
                    state = RunState.Failed("No readable text was found on these pages. Try a clearer scan, or the other language option.")
                } else {
                    extracted = text
                    val out = FileUtils.newOutput(context, "scanned_pdf_text", "txt")
                    out.writeText(text)
                    state = RunState.Done(listOf(out), "OCR finished — ${text.length} characters, full text below.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { state = RunState.Failed("${e.javaClass.simpleName}: ${e.message ?: "OCR failed on this PDF."}") }
        }
    }, "Read scanned PDF")
    extracted?.let { text ->
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text("Recognized text") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp))
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy text") }
    }
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 5. Text to PDF ----------

@Composable
private fun TextToPdfBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    var text by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(11f) }
    OutlinedTextField(text, { text = it }, label = { Text("Type or paste your text") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 320.dp))
    Text("Font size: ${fontSize.toInt()}pt"); Slider(fontSize, { fontSize = it }, valueRange = 9f..18f)
    NoteCard("Creates a clean A4 PDF with word-wrapping and as many pages as needed. The built-in font covers English/Latin letters — Hindi (Devanagari) text cannot be printed by it and will fail with an error instead of producing a broken PDF.")
    ProcessButton(text.isNotBlank(), running, {
        scope.runJob({ state = it }) {
            val out = FileUtils.newOutput(context, "text_document", "pdf")
            PdfTools2.textToPdf(context, text, out, fontSize)
            listOf(out) to "PDF created from your text."
        }
    }, "Create PDF")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 6. PDF to Word ----------

@Composable
private fun PdfToWordBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Creates a real Word file (.docx) from the PDF's text. Formatting is BASIC — paragraphs of text only; tables, columns, images and exact layout are not rebuilt. Scanned PDFs have no stored text and will fail here (use the OCR tool on them first).")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "document", "docx")
            PdfTools2.pdfToWord(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Word file created. Open it in Word / Google Docs / WPS to check and format it."
        }
    }, "Convert to Word")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 7. Extract Images from PDF ----------

@Composable
private fun ExtractImagesBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Pulls out the image files embedded inside the PDF (logos, photos, scanned pages). Images are decoded and saved as PNG — the original encoding is not preserved. Use Save on each image below.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val outs = PdfTools2.extractImages(context, input, FileUtils.outputDir(context), "extracted")
            FileUtils.cleanupTemp(input)
            outs to "Extracted ${outs.size} image(s) as PNG."
        }
    }, "Extract images")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 8. PDF Info & Metadata ----------

@Composable
private fun PdfInfoBody() {
    val context = LocalContext.current
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(picked) {
        info = if (picked == null) emptyMap() else try {
            withContext(Dispatchers.IO) {
                val f = FileUtils.copyUriToCache(context, picked.uri)
                val m = PdfTools2.pdfInfo(context, f)
                FileUtils.cleanupTemp(f)
                m
            }
        } catch (e: Exception) { mapOf("Error" to "${e.javaClass.simpleName}: ${e.message ?: "Could not read this PDF"}") }
    }
    if (info.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            info.forEach { (k, v) -> Text("$k: $v") }
        }
    }
    NoteCard("Everything is read locally on your phone — the file is never uploaded anywhere.")
}

// ---------- 9. Edit Metadata ----------

@Composable
private fun EditMetadataBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var readOnlyInfo by remember { mutableStateOf("") }
    LaunchedEffect(picked) {
        if (picked != null) {
            try {
                val meta = withContext(Dispatchers.IO) {
                    val f = FileUtils.copyUriToCache(context, picked.uri)
                    val m = PdfTools2.readMetadata(context, f)
                    FileUtils.cleanupTemp(f)
                    m
                }
                title = meta.title ?: ""; author = meta.author ?: ""
                subject = meta.subject ?: ""; keywords = meta.keywords ?: ""
                readOnlyInfo = listOfNotNull(
                    meta.creator?.let { "Creator (app that made it): $it" },
                    meta.producer?.let { "Producer: $it" }
                ).joinToString("\n")
            } catch (e: Exception) { readOnlyInfo = "Could not read current metadata: ${e.message ?: "unknown error"}" }
        }
    }
    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(author, { author = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(keywords, { keywords = it }, label = { Text("Keywords (comma separated)") }, modifier = Modifier.fillMaxWidth())
    if (readOnlyInfo.isNotBlank()) Text(readOnlyInfo, style = MaterialTheme.typography.bodySmall)
    NoteCard("Changes are saved to a COPY — your original PDF is untouched. Clearing a field and saving removes that entry.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "metadata_edited", "pdf")
            PdfTools2.editMetadata(context, input, title, author, subject, keywords, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Metadata updated in the copy."
        }
    }, "Save with new metadata")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 10. Remove Metadata ----------

@Composable
private fun RemoveMetadataBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Removes the hidden document information — title, author, subject, keywords, creator/producer entries, dates and XMP metadata — from a COPY of the PDF. Page content is not changed. This does not remove text or images printed on the pages themselves.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "metadata_removed", "pdf")
            PdfTools2.removeMetadata(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Metadata removed from the copy. Check it with 'PDF Info & Metadata'."
        }
    }, "Remove metadata")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 11. Resize Pages ----------

@Composable
private fun ResizePagesBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var sizeName by remember { mutableStateOf("A4") }
    Text("New page size:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("A4", "Letter", "A5").forEach { s -> FilterChip(sizeName == s, { sizeName = s }, label = { Text(s) }) }
    }
    NoteCard("Every page is scaled to fit the new size and centred — nothing is cut off, and text stays sharp (pages are not turned into images). Landscape pages get the landscape version of the chosen size automatically.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "resized_pages", "pdf")
            val target = when (sizeName) {
                "Letter" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER
                "A5" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A5
                else -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
            }
            PdfTools2.resizePages(context, input, target, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "All pages resized to $sizeName."
        }
    }, "Resize pages")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 12. Crop PDF Pages ----------

@Composable
private fun CropBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var left by remember { mutableStateOf("0") }
    var right by remember { mutableStateOf("0") }
    var top by remember { mutableStateOf("0") }
    var bottom by remember { mutableStateOf("0") }
    val numFilter: (String) -> String = { s -> s.filter { c -> c.isDigit() || c == '.' } }
    Text("Margins to trim, in millimetres (applied to every page):")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(left, { left = numFilter(it) }, label = { Text("Left mm") }, modifier = Modifier.weight(1f))
        OutlinedTextField(right, { right = numFilter(it) }, label = { Text("Right mm") }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(top, { top = numFilter(it) }, label = { Text("Top mm") }, modifier = Modifier.weight(1f))
        OutlinedTextField(bottom, { bottom = numFilter(it) }, label = { Text("Bottom mm") }, modifier = Modifier.weight(1f))
    }
    NoteCard("Cropping sets the visible page area — like in every PDF editor, the trimmed-off content is hidden, not deleted from the file, and could be recovered with other tools. To truly remove content, extract the pages you need into a new PDF instead.")
    val anyMargin = listOf(left, right, top, bottom).any { (it.toFloatOrNull() ?: 0f) > 0f }
    ProcessButton(picked != null && anyMargin, running, {
        scope.runJob({ state = it }) {
            fun mm(s: String): Float = (s.toFloatOrNull() ?: 0f) * 72f / 25.4f
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "cropped", "pdf")
            PdfTools2.cropPages(context, input, mm(left), mm(bottom), mm(right), mm(top), out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Pages cropped by your margins."
        }
    }, "Crop pages")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 13. Grayscale PDF ----------

@Composable
private fun GrayscaleBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Converts the whole PDF to black & white — handy before printing to save colour ink. Pages are rebuilt as images, so text in the result is NOT selectable and file size can go up or down; the real before/after sizes are shown.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "grayscale", "pdf")
            val (o, n) = PdfTools2.grayscale(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Grayscale PDF created. Original: ${FileUtils.formatSize(o)} → Output: ${FileUtils.formatSize(n)}."
        }
    }, "Convert to grayscale")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 14. Sign PDF ----------

@Composable
private fun SignBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    val totalPages = rememberPdfPageCount(picked)
    if (totalPages > 0) Text("This PDF has $totalPages page(s).")
    val (img, launchImg) = rememberFilePicker(IMG_MIME2)
    Button(onClick = launchImg, modifier = Modifier.fillMaxWidth()) { Text("Select signature image (photo of your sign)") }
    PickedCard(img)
    var pageChoice by remember { mutableStateOf("last") }
    Text("Sign on:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("first" to "First page", "last" to "Last page", "custom" to "Page no.").forEach { (v, l) ->
            FilterChip(pageChoice == v, { pageChoice = v }, label = { Text(l) })
        }
    }
    var customPage by remember { mutableStateOf("") }
    if (pageChoice == "custom") OutlinedTextField(customPage, { customPage = it.filter { c -> c.isDigit() } }, label = { Text("Page number") }, modifier = Modifier.fillMaxWidth())
    var corner by remember { mutableStateOf("bottom-right") }
    CornerChips(corner) { corner = it }
    var widthPct by remember { mutableStateOf(0.25f) }
    Text("Signature width: ${(widthPct * 100).toInt()}% of page width"); Slider(widthPct, { widthPct = it }, valueRange = 0.1f..0.45f)
    NoteCard("This stamps your signature IMAGE onto the page — it looks like a signature but is NOT a digital/cryptographic signature and does not prove identity or lock the document. A PNG with a transparent background looks best; a plain photo of your sign on white paper also works.")
    ProcessButton(picked != null && img != null && totalPages > 0, running, {
        scope.runJob({ state = it }) {
            val pages = when (pageChoice) {
                "first" -> setOf(1)
                "last" -> setOf(totalPages)
                else -> setOf(customPage.toIntOrNull() ?: throw IllegalArgumentException("Enter the page number to sign"))
            }
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val signature = FileUtils.copyUriToCache(context, img!!.uri)
            val out = FileUtils.newOutput(context, "signed", "pdf")
            PdfTools2.stampImage(context, input, signature, pages, corner, widthPct, out)
            FileUtils.cleanupTemp(input, signature)
            listOf(out) to "Signature stamped on page ${pages.first()}. Open the copy and check the placement before sharing it."
        }
    }, "Sign PDF")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 15. Add Image to PDF ----------

@Composable
private fun AddImageBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    val (img, launchImg) = rememberFilePicker(IMG_MIME2)
    Button(onClick = launchImg, modifier = Modifier.fillMaxWidth()) { Text("Select image (logo / photo)") }
    PickedCard(img)
    var allPages by remember { mutableStateOf(true) }
    Text("Stamp on:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(allPages, { allPages = true }, label = { Text("All pages") })
        FilterChip(!allPages, { allPages = false }, label = { Text("First page only") })
    }
    var corner by remember { mutableStateOf("bottom-right") }
    CornerChips(corner) { corner = it }
    var widthPct by remember { mutableStateOf(0.2f) }
    Text("Image width: ${(widthPct * 100).toInt()}% of page width"); Slider(widthPct, { widthPct = it }, valueRange = 0.08f..0.5f)
    NoteCard("The image is drawn on top of the page content (like a stamp/logo). PNG or JPG works best; a PNG with transparency blends in cleanly.")
    ProcessButton(picked != null && img != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val image = FileUtils.copyUriToCache(context, img!!.uri)
            val out = FileUtils.newOutput(context, "image_stamped", "pdf")
            PdfTools2.stampImage(context, input, image, if (allPages) null else setOf(1), corner, widthPct, out)
            FileUtils.cleanupTemp(input, image)
            listOf(out) to if (allPages) "Image stamped on every page." else "Image stamped on the first page."
        }
    }, "Add image")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 16. Header & Footer ----------

@Composable
private fun HeaderFooterBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var header by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    OutlinedTextField(header, { header = it }, label = { Text("Header text (top centre, every page)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(footer, { footer = it }, label = { Text("Footer text (bottom centre, every page)") }, modifier = Modifier.fillMaxWidth())
    NoteCard("Text is printed in a standard font at 10pt in grey, centred. English/Latin letters only — the built-in font cannot print Devanagari.")
    ProcessButton(picked != null && (header.isNotBlank() || footer.isNotBlank()), running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "header_footer", "pdf")
            PdfTools2.headerFooter(context, input, header, footer, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Header/footer added to all pages."
        }
    }, "Add header & footer")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 17. PDF N-Up ----------

@Composable
private fun NUpBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    val totalPages = rememberPdfPageCount(picked)
    if (totalPages > 0) Text("This PDF has $totalPages page(s).")
    var perSheet by remember { mutableStateOf(4) }
    Text("Pages per sheet:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(2, 4, 6).forEach { n -> FilterChip(perSheet == n, { perSheet = n }, label = { Text("$n-up") }) }
    }
    NoteCard("Puts several pages side-by-side on A4 sheets — great for printing study notes with less paper. 2-up uses landscape A4; 4-up and 6-up use portrait A4. Text stays sharp (pages are not turned into images).")
    ProcessButton(picked != null && totalPages > 0, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "nup_$perSheet", "pdf")
            PdfTools2.nUp(context, input, perSheet, out)
            FileUtils.cleanupTemp(input)
            val sheets = (totalPages + perSheet - 1) / perSheet
            listOf(out) to "$totalPages page(s) laid out on $sheets sheet(s), $perSheet per sheet."
        }
    }, "Create N-up PDF")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 18. Flatten PDF ----------

@Composable
private fun FlattenBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Flattening bakes filled form values into the pages, so the form can no longer be edited — useful before submitting a filled form. Only form fields are flattened; links and sticky-note annotations stay as they are. A PDF without a form will honestly say there is nothing to flatten.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "flattened", "pdf")
            PdfTools2.flatten(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Form flattened — values are now part of the pages."
        }
    }, "Flatten PDF")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 19. Repair PDF ----------

@Composable
private fun RepairBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    NoteCard("Best-effort repair: the PDF is re-read with a tolerant parser (which can recover from broken cross-reference tables and minor corruption) and saved as a clean new file. If the file is too damaged to open at all, you'll be told — no fake 'repaired' file is produced.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "repaired", "pdf")
            val pages = PdfTools2.repair(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Recovered $pages page(s) into a clean copy. Open it and check all pages before deleting your original."
        }
    }, "Try to repair")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 20. Reverse Page Order ----------

@Composable
private fun ReverseBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    val totalPages = rememberPdfPageCount(picked)
    if (totalPages > 0) Text("This PDF has $totalPages page(s) — they will come out in the order ${totalPages}…1.")
    ProcessButton(picked != null && totalPages > 1, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "reversed", "pdf")
            PdfTools2.reverseOrder(context, input, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Page order reversed."
        }
    }, "Reverse pages")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 21. Insert Blank Page ----------

@Composable
private fun InsertBlankBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    val totalPages = rememberPdfPageCount(picked)
    if (totalPages > 0) Text("This PDF has $totalPages page(s).")
    var mode by remember { mutableStateOf(1) }
    Text("Insert the blank page:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "At start", 1 to "At end", 2 to "After page…").forEach { (v, l) ->
            FilterChip(mode == v, { mode = v }, label = { Text(l) })
        }
    }
    var afterPage by remember { mutableStateOf("") }
    if (mode == 2) OutlinedTextField(afterPage, { afterPage = it.filter { c -> c.isDigit() } }, label = { Text("Insert after page number (1–$totalPages)") }, modifier = Modifier.fillMaxWidth())
    NoteCard("The blank page takes the same size as its neighbouring page.")
    ProcessButton(picked != null && totalPages > 0 && (mode != 2 || afterPage.toIntOrNull() != null), running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "blank_inserted", "pdf")
            PdfTools2.insertBlankPage(context, input, mode, afterPage.toIntOrNull() ?: 0, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Blank page inserted. New PDF has ${totalPages + 1} page(s)."
        }
    }, "Insert blank page")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 22. Split PDF by Size ----------

@Composable
private fun SplitSizeBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var mb by remember { mutableStateOf("5") }
    OutlinedTextField(mb, { mb = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Maximum size per part (MB)") }, modifier = Modifier.fillMaxWidth())
    NoteCard("Splits the PDF into sequential parts that each stay under your size limit — part sizes are measured from the real saved files, not guessed. One single page that is already bigger than the limit cannot be cut and becomes its own (larger) part; you'll see its real size below.")
    ProcessButton(picked != null && (mb.toDoubleOrNull() ?: 0.0) >= 0.1, running, {
        scope.runJob({ state = it }) {
            val maxBytes = ((mb.toDoubleOrNull() ?: 0.0) * 1024 * 1024).toLong()
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val outs = PdfTools2.splitBySize(context, input, maxBytes, FileUtils.outputDir(context), "split")
            FileUtils.cleanupTemp(input)
            outs to "Split into ${outs.size} part(s). Each part's real size is listed below — Save the ones you need."
        }
    }, "Split by size")
    ResultPanel(state) { state = RunState.Idle }
}

// ---------- 23. Fill PDF Form ----------

@Composable
private fun FillFormBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(PDF_MIME2)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
    PickedCard(picked)
    var fields by remember { mutableStateOf<List<PdfTools2.PdfFormField>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var flattenAfter by remember { mutableStateOf(false) }
    LaunchedEffect(picked) {
        fields = null; loadError = null; values = emptyMap()
        if (picked != null) {
            try {
                val list = withContext(Dispatchers.IO) {
                    val f = FileUtils.copyUriToCache(context, picked.uri)
                    val r = PdfTools2.listFormFields(context, f)
                    FileUtils.cleanupTemp(f)
                    r
                }
                fields = list
                values = list.filter { it.isText }.associate { it.name to it.value }
            } catch (e: Exception) {
                loadError = "${e.javaClass.simpleName}: ${e.message ?: "Could not read this PDF's form."}"
            }
        }
    }
    when {
        loadError != null -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Text(loadError!!, Modifier.padding(12.dp))
        }
        picked != null && fields == null -> Text("Reading form fields…")
        fields != null && fields!!.isEmpty() -> NoteCard("This PDF has NO fillable form fields — there is nothing to fill here. Many PDFs (especially scans and printouts) are flat: they look like forms but contain no real fields. This tool only works on PDFs with actual form fields.")
        fields != null -> {
            val textFields = fields!!.filter { it.isText }
            val otherFields = fields!!.filter { !it.isText }
            if (textFields.isEmpty()) {
                NoteCard("This form has fields, but none of them are TEXT fields (only checkboxes / choices / signatures), which this tool does not fill.")
            } else {
                Text("Fill the fields:", style = MaterialTheme.typography.titleSmall)
                textFields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.name] ?: "",
                        onValueChange = { v -> values = values + (field.name to v) },
                        label = { Text(field.name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row { Checkbox(flattenAfter, { flattenAfter = it }); Text("Flatten after filling (values become un-editable)", Modifier.padding(top = 12.dp)) }
            }
            otherFields.forEach { f ->
                Text("${f.name} — ${f.type} (shown for information, not editable here)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    ProcessButton(picked != null && fields?.any { it.isText } == true, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "form_filled", "pdf")
            val filled = PdfTools2.fillForm(context, input, values, flattenAfter, out)
            FileUtils.cleanupTemp(input)
            listOf(out) to if (flattenAfter) "$filled field(s) filled and flattened into the pages." else "$filled field(s) filled. The copy stays fillable in other PDF apps."
        }
    }, "Save filled form")
    ResultPanel(state) { state = RunState.Idle }
}
