package com.pushpendratech.toolbox.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.core.ImageTools
import com.pushpendratech.toolbox.core.PdfTools
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PDF_MIME = arrayOf("application/pdf")
private val IMG_MIME = arrayOf("image/*")

@Composable
fun PdfToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        var state by remember { mutableStateOf<RunState>(RunState.Idle) }
        val running = state is RunState.Running
        fun cacheOf(p: PickedFile): File = FileUtils.copyUriToCache(context, p.uri)

        when (tool.id) {
            "pdf_merge" -> {
                val (picked, launch) = rememberMultiFilePicker(PDF_MIME)
                var order by remember(picked) { mutableStateOf(picked) }
                Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select 2 or more PDFs (${order.size} selected)") }
                order.forEachIndexed { i, f ->
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${f.name}", Modifier.weight(1f))
                        TextButton(onClick = { if (i > 0) order = order.toMutableList().also { val t = it[i - 1]; it[i - 1] = it[i]; it[i] = t } }, enabled = i > 0) { Text("↑") }
                        TextButton(onClick = { if (i < order.lastIndex) order = order.toMutableList().also { val t = it[i + 1]; it[i + 1] = it[i]; it[i] = t } }, enabled = i < order.lastIndex) { Text("↓") }
                    } }
                }
                ProcessButton(order.size >= 2, running, {
                    scope.runJob({ state = it }) {
                        val inputs = order.map { cacheOf(it) }
                        val out = FileUtils.newOutput(context, "merged", "pdf"); PdfTools.merge(context, inputs, out)
                        inputs.forEach { FileUtils.cleanupTemp(it) }
                        listOf(out) to "Merged ${order.size} PDFs in your chosen order."
                    }
                }, "Merge PDFs")
            }
            "jpg_to_pdf", "img_to_pdf" -> {
                val (picked, launch) = rememberMultiFilePicker(IMG_MIME)
                var a4 by remember { mutableStateOf(tool.id == "img_to_pdf") }
                Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select images (${picked.size} selected)") }
                picked.forEach { Text("• ${it.name}") }
                Row { Checkbox(a4, { a4 = it }); Text("A4 pages (off = page fits each image)", Modifier.padding(top = 12.dp)) }
                ProcessButton(picked.isNotEmpty(), running, {
                    scope.runJob({ state = it }) {
                        val inputs = picked.map { cacheOf(it) }
                        val out = FileUtils.newOutput(context, "images", "pdf"); PdfTools.imagesToPdf(context, inputs, out, a4)
                        inputs.forEach { FileUtils.cleanupTemp(it) }
                        listOf(out) to "Created a PDF with ${picked.size} image(s)."
                    }
                }, "Create PDF")
            }
            else -> {
                // All remaining PDF tools take a single PDF.
                val (picked, launch) = rememberFilePicker(PDF_MIME)
                Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select PDF") }
                PickedCard(picked)
                var totalPages by remember { mutableStateOf(0) }
                LaunchedEffect(picked) {
                    totalPages = if (picked == null) 0 else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val n = PdfTools.pageCount(f); FileUtils.cleanupTemp(f); n } } catch (e: Exception) { 0 }
                }
                if (totalPages > 0) Text("This PDF has $totalPages page(s).")
                when (tool.id) {
                    "pdf_compress" -> {
                        var preset by remember { mutableStateOf(1) }
                        Text("Preset:"); listOf("Light (images ≤1600px, 80% quality)", "Recommended (≤1200px, 70%)", "Strong (≤900px, 55%)").forEachIndexed { i, l ->
                            Row { RadioButton(preset == i, { preset = i }); Text(l, Modifier.padding(top = 12.dp)) } }
                        NoteCard("Compression re-encodes large embedded images and strips metadata. A scanned/photo PDF shrinks a lot; a text-only PDF may barely change — the result always shows the real before/after sizes, and if the output is larger you'll be told, not misled.")
                        ProcessButton(picked != null, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val out = FileUtils.newOutput(context, "compressed", "pdf")
                                val (o, n) = PdfTools.compress(context, input, out, listOf(1600, 1200, 900)[preset], listOf(0.8f, 0.7f, 0.55f)[preset])
                                FileUtils.cleanupTemp(input)
                                val pct = if (o > 0) (o - n) * 100.0 / o else 0.0
                                listOf(out) to if (n < o) "Original: ${FileUtils.formatSize(o)} → Compressed: ${FileUtils.formatSize(n)} (${String.format("%.1f", pct)}% smaller)."
                                else "Original: ${FileUtils.formatSize(o)} → Output: ${FileUtils.formatSize(n)}. This PDF could not be made smaller (it has few/no large images) — no reduction is claimed."
                            }
                        })
                    }
                    "pdf_split" -> {
                        var spec by remember { mutableStateOf("1-3") }
                        OutlinedTextField(spec, { spec = it }, label = { Text("Pages to split out, e.g. 1-3 or 2,4,6") }, modifier = Modifier.fillMaxWidth())
                        NoteCard("This creates one PDF containing exactly the pages you list (the rest stay only in your original). To split into several files, run it once per range.")
                        ProcessButton(picked != null && totalPages > 0, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val pages = PdfTools.parseRanges(spec, totalPages)
                                val out = FileUtils.newOutput(context, "split", "pdf"); PdfTools.extractPages(context, input, pages, out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Split out page(s) ${pages.joinToString(", ")} into a new PDF."
                            }
                        })
                    }
                    "pdf_extract" -> {
                        var spec by remember { mutableStateOf("1") }
                        OutlinedTextField(spec, { spec = it }, label = { Text("Pages / ranges, e.g. 1-3,5") }, modifier = Modifier.fillMaxWidth())
                        ProcessButton(picked != null && totalPages > 0, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val pages = PdfTools.parseRanges(spec, totalPages)
                                val out = FileUtils.newOutput(context, "extracted", "pdf"); PdfTools.extractPages(context, input, pages, out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Extracted page(s) ${pages.joinToString(", ")}."
                            }
                        })
                    }
                    "pdf_delete_pages" -> {
                        var spec by remember { mutableStateOf("") }
                        OutlinedTextField(spec, { spec = it }, label = { Text("Pages to DELETE, e.g. 2,4-5") }, modifier = Modifier.fillMaxWidth())
                        ProcessButton(picked != null && totalPages > 0 && spec.isNotBlank(), running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val pages = PdfTools.parseRanges(spec, totalPages)
                                val out = FileUtils.newOutput(context, "pages_deleted", "pdf"); PdfTools.deletePages(context, input, pages, out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Deleted page(s) ${pages.joinToString(", ")}. New PDF has ${totalPages - pages.size} page(s)."
                            }
                        })
                    }
                    "pdf_rotate" -> {
                        var degrees by remember { mutableStateOf(90) }; var all by remember { mutableStateOf(true) }; var spec by remember { mutableStateOf("1") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(90, 180, 270).forEach { d -> FilterChip(degrees == d, { degrees = d }, label = { Text("$d°") }) } }
                        Row { Checkbox(all, { all = it }); Text("All pages", Modifier.padding(top = 12.dp)) }
                        if (!all) OutlinedTextField(spec, { spec = it }, label = { Text("Pages, e.g. 1-3,5") }, modifier = Modifier.fillMaxWidth())
                        ProcessButton(picked != null && totalPages > 0, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val pages = if (all) null else PdfTools.parseRanges(spec, totalPages)
                                val out = FileUtils.newOutput(context, "rotated", "pdf"); PdfTools.rotate(context, input, pages, degrees, out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Rotated by $degrees°."
                            }
                        })
                    }
                    "pdf_reorder" -> {
                        var spec by remember(totalPages) { mutableStateOf((1..totalPages).joinToString(",")) }
                        Text("Type the new page order as comma-separated page numbers. Every page must appear exactly once.")
                        OutlinedTextField(spec, { spec = it }, label = { Text("New order, e.g. 3,1,2 for a 3-page PDF") }, modifier = Modifier.fillMaxWidth())
                        ProcessButton(picked != null && totalPages > 0, running, {
                            scope.runJob({ state = it }) {
                                val order = spec.split(",").map { it.trim().toIntOrNull() ?: throw IllegalArgumentException("Order must be numbers separated by commas") }
                                val input = cacheOf(picked!!); val out = FileUtils.newOutput(context, "reordered", "pdf")
                                PdfTools.reorder(context, input, order, out); FileUtils.cleanupTemp(input)
                                listOf(out) to "Pages reordered to: ${order.joinToString(", ")}."
                            }
                        })
                    }
                    "pdf_to_images" -> {
                        var scale by remember { mutableStateOf(2f) }; var all by remember { mutableStateOf(true) }; var spec by remember { mutableStateOf("1") }
                        Row { Checkbox(all, { all = it }); Text("All pages", Modifier.padding(top = 12.dp)) }
                        if (!all) OutlinedTextField(spec, { spec = it }, label = { Text("Pages, e.g. 1-3,5") }, modifier = Modifier.fillMaxWidth())
                        Text("Resolution: ${scale}× (higher = sharper but uses more memory — large PDFs at 3× can run out of memory on low-RAM phones)")
                        Slider(scale, { scale = it }, valueRange = 1f..3f, steps = 3)
                        ProcessButton(picked != null && totalPages > 0, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val pages = if (all) null else PdfTools.parseRanges(spec, totalPages)
                                val outs = PdfTools.pdfToImages(context, input, pages, scale, FileUtils.outputDir(context), "page")
                                FileUtils.cleanupTemp(input); outs to "Exported ${outs.size} page image(s) as PNG. Use Save on each, or Share."
                            }
                        }, "Export images")
                    }
                    "pdf_watermark" -> {
                        var text by remember { mutableStateOf("CONFIDENTIAL") }; var size by remember { mutableStateOf(42f) }
                        var opacity by remember { mutableStateOf(0.25f) }; var angle by remember { mutableStateOf(45f) }
                        OutlinedTextField(text, { text = it }, label = { Text("Watermark text") }, modifier = Modifier.fillMaxWidth())
                        Text("Size: ${size.toInt()}pt"); Slider(size, { size = it }, valueRange = 16f..96f)
                        Text("Opacity: ${(opacity * 100).toInt()}%"); Slider(opacity, { opacity = it }, valueRange = 0.05f..1f)
                        Text("Angle: ${angle.toInt()}°"); Slider(angle, { angle = it }, valueRange = 0f..90f)
                        ProcessButton(picked != null && text.isNotBlank(), running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val out = FileUtils.newOutput(context, "watermarked", "pdf")
                                PdfTools.watermark(context, input, null, text, size, opacity, angle.toDouble(), out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Watermark \"$text\" added to all pages."
                            }
                        })
                    }
                    "pdf_page_numbers" -> {
                        var startAt by remember { mutableStateOf("1") }; var position by remember { mutableStateOf("center") }
                        OutlinedTextField(startAt, { startAt = it.filter { c -> c.isDigit() } }, label = { Text("Start numbering from") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("left", "center", "right", "top").forEach { p -> FilterChip(position == p, { position = p }, label = { Text(p.replaceFirstChar { it.uppercase() }) }) } }
                        ProcessButton(picked != null, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val out = FileUtils.newOutput(context, "numbered", "pdf")
                                PdfTools.pageNumbers(context, input, startAt.toIntOrNull() ?: 1, position, out)
                                FileUtils.cleanupTemp(input); listOf(out) to "Page numbers added starting from ${startAt.ifBlank { "1" }}."
                            }
                        })
                    }
                    "pdf_protect" -> {
                        var pw by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
                        NoteCard("Genuine 128-bit PDF encryption (PDFBox). The protected file opens in Adobe Reader, Chrome and most viewers. Your password is used only in memory for this operation — it is never saved or sent anywhere. If you forget it, the file cannot be recovered by this app.")
                        OutlinedTextField(pw, { pw = it }, label = { Text("Password (min 4 characters)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirm password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        if (pw.isNotEmpty() && pw2.isNotEmpty() && pw != pw2) Text("Passwords do not match.", color = MaterialTheme.colorScheme.error)
                        ProcessButton(picked != null && pw.length >= 4 && pw == pw2, running, {
                            scope.runJob({ state = it }) {
                                val input = cacheOf(picked!!); val out = FileUtils.newOutput(context, "protected", "pdf")
                                PdfTools.protect(context, input, pw, out); FileUtils.cleanupTemp(input)
                                listOf(out) to "PDF encrypted. Test opening it — it should ask for your password."
                            }
                        }, "Encrypt PDF")
                    }
                    else -> Text("Unknown PDF tool: ${tool.id}")
                }
                ResultPanel(state) { state = RunState.Idle }
            }
        }
        // Merge & images-to-pdf render their own flow above; show results for them too:
        if (tool.id == "pdf_merge" || tool.id == "jpg_to_pdf" || tool.id == "img_to_pdf") ResultPanel(state) { state = RunState.Idle }
    }
}

@Composable
fun ImageToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        var state by remember { mutableStateOf<RunState>(RunState.Idle) }
        val running = state is RunState.Running

        // Note: img_to_pdf is routed to PdfToolScreen in MainActivity (shared images->PDF engine), so it never reaches here.
        val (picked, launch) = rememberFilePicker(IMG_MIME)
        Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select image") }
        PickedCard(picked)
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(picked) {
            preview = if (picked == null) null else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val b = ImageTools.decode(f, 1024); FileUtils.cleanupTemp(f); b } } catch (e: Exception) { null }
        }
        preview?.let { Image(it.asImageBitmap(), contentDescription = "Selected image preview", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
        fun inputFile(): File = FileUtils.copyUriToCache(context, picked!!.uri)
        val extOf = picked?.name?.substringAfterLast('.', "jpg")?.lowercase() ?: "jpg"

        when (tool.id) {
            "img_compress" -> {
                var quality by remember { mutableStateOf(75f) }
                Text("Quality: ${quality.toInt()}% (lower = smaller file)"); Slider(quality, { quality = it }, valueRange = 10f..100f)
                NoteCard("The original is never overwritten — a compressed copy is created. PNG is lossless, so PNG inputs are saved as JPEG here to actually shrink.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "compressed", "jpg")
                        val (o, n) = ImageTools.compress(input, out, quality.toInt(), Bitmap.CompressFormat.JPEG)
                        FileUtils.cleanupTemp(input)
                        listOf(out) to if (n < o) "Original: ${FileUtils.formatSize(o)} → Compressed: ${FileUtils.formatSize(n)}." else "Original: ${FileUtils.formatSize(o)} → Output: ${FileUtils.formatSize(n)}. This image was already well-compressed, so no reduction is claimed."
                    }
                })
            }
            "img_resize" -> {
                var w by remember { mutableStateOf("") }; var h by remember { mutableStateOf("") }; var keep by remember { mutableStateOf(true) }; var pct by remember { mutableStateOf("") }
                preview?.let { Text("Current size: ${it.width} × ${it.height}px") }
                OutlinedTextField(w, { w = it.filter { c -> c.isDigit() } }, label = { Text("New width (px)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(h, { h = it.filter { c -> c.isDigit() } }, label = { Text("New height (px)") }, modifier = Modifier.fillMaxWidth())
                Row { Checkbox(keep, { keep = it }); Text("Keep aspect ratio (height/width auto)", Modifier.padding(top = 12.dp)) }
                OutlinedTextField(pct, { pct = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("…or resize by percentage, e.g. 50") }, modifier = Modifier.fillMaxWidth())
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val src = preview ?: throw IllegalStateException("Load an image first")
                        val (tw, th) = if (pct.isNotBlank()) {
                            val f = pct.toFloat() / 100f; (src.width * f).toInt().coerceAtLeast(1) to (src.height * f).toInt().coerceAtLeast(1)
                        } else ImageTools.resizedDimensions(src.width, src.height, w.toIntOrNull(), h.toIntOrNull(), keep)
                        val input = inputFile(); val out = FileUtils.newOutput(context, "resized", if (extOf == "png") "png" else "jpg")
                        ImageTools.resize(input, out, tw, th, ImageTools.formatForExt(out.extension)); FileUtils.cleanupTemp(input)
                        listOf(out) to "Resized to $tw × $th px."
                    }
                })
            }
            "jpg_to_png" -> {
                NoteCard("Converting JPEG → PNG keeps the picture exactly as it looks now, in a lossless container. It cannot restore quality that JPEG compression already removed, and the PNG will usually be larger.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) { val input = inputFile(); val out = FileUtils.newOutput(context, "converted", "png"); ImageTools.convert(input, out, Bitmap.CompressFormat.PNG); FileUtils.cleanupTemp(input); listOf(out) to "Converted to PNG." }
                })
            }
            "png_to_jpg" -> {
                var bg by remember { mutableStateOf(android.graphics.Color.WHITE) }; var quality by remember { mutableStateOf(90f) }
                Text("Background for transparent areas:"); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("White" to android.graphics.Color.WHITE, "Black" to android.graphics.Color.BLACK).forEach { (label, c) -> FilterChip(bg == c, { bg = c }, label = { Text(label) }) } }
                Text("Quality: ${quality.toInt()}%"); Slider(quality, { quality = it }, valueRange = 40f..100f)
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) { val input = inputFile(); val out = FileUtils.newOutput(context, "converted", "jpg"); ImageTools.convert(input, out, Bitmap.CompressFormat.JPEG, quality.toInt(), bg); FileUtils.cleanupTemp(input); listOf(out) to "Converted to JPG (transparency flattened onto your chosen background)." }
                })
            }
            "webp_convert" -> {
                var toWebP by remember(extOf) { mutableStateOf(extOf != "webp") }
                Row { RadioButton(toWebP, { toWebP = true }); Text("To WebP", Modifier.padding(top = 12.dp)); RadioButton(!toWebP, { toWebP = false }); Text("From WebP → PNG", Modifier.padding(top = 12.dp)) }
                Text("Supported on this device: JPEG/PNG → WebP, and WebP → PNG/JPEG via Android's built-in codecs.", style = MaterialTheme.typography.bodySmall)
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile()
                        if (toWebP) { val out = FileUtils.newOutput(context, "converted", "webp"); ImageTools.convert(input, out, Bitmap.CompressFormat.WEBP_LOSSY, 85); FileUtils.cleanupTemp(input); listOf(out) to "Converted to WebP." }
                        else { val out = FileUtils.newOutput(context, "converted", "png"); ImageTools.convert(input, out, Bitmap.CompressFormat.PNG); FileUtils.cleanupTemp(input); listOf(out) to "Converted from WebP to PNG." }
                    }
                })
            }
            "img_crop" -> {
                var aspect by remember { mutableStateOf("Free") }
                var left by remember { mutableStateOf(0.1f) }; var top by remember { mutableStateOf(0.1f) }; var right by remember { mutableStateOf(0.9f) }; var bottom by remember { mutableStateOf(0.9f) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Free", "1:1", "4:3", "3:2", "16:9").forEach { a -> FilterChip(aspect == a, { aspect = a }, label = { Text(a) }) } }
                if (aspect == "Free") {
                    Text("Left ${(left * 100).toInt()}%"); Slider(left, { left = it }, valueRange = 0f..0.95f)
                    Text("Top ${(top * 100).toInt()}%"); Slider(top, { top = it }, valueRange = 0f..0.95f)
                    Text("Right ${(right * 100).toInt()}%"); Slider(right, { right = it }, valueRange = 0.05f..1f)
                    Text("Bottom ${(bottom * 100).toInt()}%"); Slider(bottom, { bottom = it }, valueRange = 0.05f..1f)
                } else Text("The preset crops the largest centred area with that aspect ratio.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "cropped", if (extOf == "png") "png" else "jpg"); val fmt = ImageTools.formatForExt(out.extension)
                        if (aspect == "Free") ImageTools.crop(input, out, left, top, right, bottom, fmt)
                        else { val (aw, ah) = aspect.split(":").map { it.toFloat() }; ImageTools.cropToAspect(input, out, aw, ah, fmt) }
                        FileUtils.cleanupTemp(input); listOf(out) to "Image cropped."
                    }
                })
            }
            "img_rotate" -> {
                var deg by remember { mutableFloatStateOf(90f) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(90f, 180f, 270f).forEach { d -> FilterChip(deg == d, { deg = d }, label = { Text("${d.toInt()}°") }) } }
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) { val input = inputFile(); val out = FileUtils.newOutput(context, "rotated", if (extOf == "png") "png" else "jpg"); ImageTools.rotate(input, out, deg, ImageTools.formatForExt(out.extension)); FileUtils.cleanupTemp(input); listOf(out) to "Rotated ${deg.toInt()}°." }
                })
            }
            "img_flip" -> {
                var horizontal by remember { mutableStateOf(true) }
                Row { RadioButton(horizontal, { horizontal = true }); Text("Horizontal (mirror)", Modifier.padding(top = 12.dp)); RadioButton(!horizontal, { horizontal = false }); Text("Vertical", Modifier.padding(top = 12.dp)) }
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) { val input = inputFile(); val out = FileUtils.newOutput(context, "flipped", if (extOf == "png") "png" else "jpg"); ImageTools.flip(input, out, horizontal, ImageTools.formatForExt(out.extension)); FileUtils.cleanupTemp(input); listOf(out) to if (horizontal) "Flipped horizontally." else "Flipped vertically." }
                })
            }
            "passport_photo" -> {
                var preset by remember { mutableStateOf(ImageTools.passportPresets[0]) }
                ImageTools.passportPresets.forEach { p -> Row { RadioButton(preset == p, { preset = p }); Text(p.label, Modifier.padding(top = 12.dp)) } }
                NoteCard("The face should be centred in the preview — the tool centre-crops and sizes the photo to ${preset.widthPx}×${preset.heightPx}px. ${preset.note}. Photo requirements (background colour, dress, recency) differ by authority — this tool sets size only and does not guarantee acceptance of any official application.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) { val input = inputFile(); val out = FileUtils.newOutput(context, "passport", "jpg"); ImageTools.passport(input, out, preset); FileUtils.cleanupTemp(input); listOf(out) to "Passport-size photo created: ${preset.widthPx}×${preset.heightPx}px." }
                })
            }
            "color_picker" -> {
                var info by remember { mutableStateOf("Tap anywhere on the image below to pick a colour.") }
                var pickedColor by remember { mutableStateOf<Int?>(null) }
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                preview?.let { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = "Tap to pick a colour", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(bmp) {
                            detectTapGestures { offset ->
                                if (boxSize.width > 0) {
                                    val color = ImageTools.colorAt(bmp, offset.x / boxSize.width, offset.y / boxSize.height)
                                    pickedColor = color
                                    info = "HEX: ${ImageTools.hexOf(color)}\nRGB: ${android.graphics.Color.red(color)}, ${android.graphics.Color.green(color)}, ${android.graphics.Color.blue(color)}\nAlpha: ${android.graphics.Color.alpha(color)}"
                                }
                            }
                        })
                    pickedColor?.let { c ->
                        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(c)), modifier = Modifier.fillMaxWidth().height(36.dp)) {}
                    }
                }
                Text(info)
                if (info.startsWith("HEX")) CopyColorButton(info)
                NoteCard("Tip: the picker reads the preview image (downscaled to max 1024px for speed), so values are from that preview resolution.")
            }
            "exif_viewer" -> {
                var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                LaunchedEffect(picked) {
                    info = if (picked == null) emptyMap() else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val m = ImageTools.exifInfo(f); FileUtils.cleanupTemp(f); m } } catch (e: Exception) { mapOf("Error" to (e.message ?: "Could not read metadata")) }
                }
                if (info.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { info.forEach { (k, v) -> Text("$k: $v") } } }
                NoteCard("Metadata is read locally on your phone and never uploaded. Remember: photos you share elsewhere may carry location in EXIF — share a copy via apps that strip it if that matters to you.")
            }
            else -> Text("This image tool opens from its own entry — go back and pick it again. (${tool.id})")
        }
        ResultPanel(state) { state = RunState.Idle }
    }
}

@Composable
private fun CopyColorButton(hexInfo: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(hexInfo.substringBefore("\n").removePrefix("HEX: "))) }, modifier = Modifier.fillMaxWidth()) { Text("Copy HEX") }
}
