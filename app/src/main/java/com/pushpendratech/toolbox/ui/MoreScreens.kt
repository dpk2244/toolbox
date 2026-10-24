package com.pushpendratech.toolbox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.core.ImageTools
import com.pushpendratech.toolbox.core.MediaTools
import com.pushpendratech.toolbox.core.MoreCalculators
import com.pushpendratech.toolbox.core.MoreImageTools
import com.pushpendratech.toolbox.core.MoreMediaTools
import com.pushpendratech.toolbox.core.MoreTextTools
import com.pushpendratech.toolbox.core.PhotoFilters
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * v7 expansion batch screens (non-PDF, 20 tools). Central wiring is one line
 * in MainActivity's tool route, BEFORE the category dispatch:
 *   tool.id in MORE_TOOL_IDS -> MoreToolScreen(tool, isFav, toggle, back)
 * Same invocation pattern as the other per-category screens.
 */
val MORE_TOOL_IDS: Set<String> = setOf(
    "img_exact_kb", "img_batch_compress", "img_collage", "img_filters", "img_exif_remover", "img_watermark",
    "video_speed", "video_rotate_flip", "video_crop_aspect",
    "audio_recorder", "audio_compress", "audio_fade",
    "password_generator", "qr_generator", "text_encrypt", "lorem_ipsum",
    "calc_bmi", "calc_sip", "calc_tip", "calc_date_diff"
)

@Composable
fun MoreToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        when (tool.id) {
            "img_exact_kb" -> ExactKbBody()
            "img_batch_compress" -> BatchCompressBody()
            "img_collage" -> CollageBody()
            "img_filters" -> FiltersBody()
            "img_exif_remover" -> ExifRemoverBody()
            "img_watermark" -> ImageWatermarkBody()
            "video_speed" -> VideoSpeedBody()
            "video_rotate_flip" -> VideoRotateFlipBody()
            "video_crop_aspect" -> VideoCropBody()
            "audio_recorder" -> VoiceRecorderBody()
            "audio_compress" -> AudioCompressBody()
            "audio_fade" -> AudioFadeBody()
            "password_generator" -> PasswordGeneratorBody()
            "qr_generator" -> QrGeneratorBody()
            "text_encrypt" -> TextEncryptBody()
            "lorem_ipsum" -> LoremBody()
            "calc_bmi" -> BmiBody()
            "calc_sip" -> SipBody()
            "calc_tip" -> TipBody()
            "calc_date_diff" -> DateDiffBody()
            else -> Text("Unknown tool: ${tool.id}")
        }
    }
}

// ---------- shared private helpers (mirrors of TextCalcScreens' file-private ones) ----------
private fun fmtMore(v: Double): String = if (v.isFinite() && v == v.toLong().toDouble()) String.format("%,.0f", v) else String.format("%,.2f", v)

@Composable
private fun MoreNumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == '-' }) }, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun MoreCopyButton(text: String, label: String = "Copy result") {
    val clipboard = LocalClipboardManager.current
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, enabled = text.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun MoreOutputBox(text: String) {
    if (text.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(12.dp)) }
}

@Composable
private fun ResultCard(result: String, error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (result.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(result, Modifier.padding(12.dp)) }
}

/** Picker button + picked card + preview (same pattern as ImageToolScreen). Returns (picked, preview). */
@Composable
private fun SingleImageHeader(): Pair<PickedFile?, Bitmap?> {
    val context = LocalContext.current
    val (picked, launch) = rememberFilePicker(arrayOf("image/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select image") }
    PickedCard(picked)
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(picked) {
        preview = if (picked == null) null else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val b = ImageTools.decode(f, 1024); FileUtils.cleanupTemp(f); b } } catch (e: Exception) { null }
    }
    preview?.let { Image(it.asImageBitmap(), contentDescription = "Selected image preview", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
    return picked to preview
}

// ================= IMAGE =================

@Composable
private fun ExactKbBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, _) = SingleImageHeader()
    var kbText by remember { mutableStateOf("50") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(20, 50, 100, 200, 500).forEach { kb -> FilterChip(kbText == kb.toString(), { kbText = kb.toString() }, label = { Text("$kb KB") }) }
    }
    MoreNumField("Target size (KB)", kbText) { kbText = it }
    NoteCard("Made for form uploads (SSC, railway, exam portals that demand \"photo under 50 KB\" etc.). The photo is brought under your target first by lowering JPEG quality, then by reducing dimensions if needed. The result always shows the REAL achieved size — if the target is impossible, you'll be told instead of misled.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val kb = kbText.filter { it.isDigit() }.toIntOrNull() ?: throw IllegalArgumentException("Enter a target size in KB")
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val before = input.length()
            val out = FileUtils.newOutput(context, "exact_kb", "jpg")
            val r = MoreImageTools.compressToTargetKb(input, out, kb)
            FileUtils.cleanupTemp(input)
            val msg = when {
                r.alreadySmaller -> "This photo is already ${FileUtils.formatSize(r.achievedBytes)} — under your $kb KB target. An untouched copy was saved (no quality lost)."
                r.reachedTarget -> "Done: ${FileUtils.formatSize(before)} → ${FileUtils.formatSize(r.achievedBytes)} (target ≤ $kb KB), at JPEG quality ${r.qualityUsed} and ${r.scalePercent}% of the original dimensions."
                else -> "Could not reach ≤ $kb KB: the smallest version possible is ${FileUtils.formatSize(r.achievedBytes)} (quality ${r.qualityUsed}, ${r.scalePercent}% size). That smallest version was saved — try a slightly bigger target if the portal allows it."
            }
            listOf(out) to msg
        }
    }, "Compress to target KB")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun BatchCompressBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberMultiFilePicker(arrayOf("image/*"))
    var quality by remember { mutableStateOf(70f) }
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select images (${picked.size} selected)") }
    picked.take(15).forEach { Text("• ${it.name}") }
    if (picked.size > 15) Text("…and ${picked.size - 15} more")
    Text("Quality for all: ${quality.toInt()}% (lower = smaller files)"); Slider(quality, { quality = it }, valueRange = 10f..100f)
    NoteCard("Every photo is compressed with the same setting and saved as its own JPEG copy — originals are never touched. The result lists each file's real before/after size, and any file that fails is named, not hidden.")
    ProcessButton(picked.isNotEmpty(), running, {
        scope.runJob({ state = it }) {
            val outputs = ArrayList<File>(); val lines = ArrayList<String>(); val failures = ArrayList<String>()
            for (p in picked) {
                try {
                    val input = FileUtils.copyUriToCache(context, p.uri)
                    val out = FileUtils.newOutput(context, p.name.substringBeforeLast('.', p.name) + "_compressed", "jpg")
                    val (b, a) = ImageTools.compress(input, out, quality.toInt(), Bitmap.CompressFormat.JPEG)
                    FileUtils.cleanupTemp(input); outputs.add(out)
                    lines.add("${p.name}: ${FileUtils.formatSize(b)} → ${FileUtils.formatSize(a)}")
                } catch (e: Exception) { failures.add("${p.name} (${e.message ?: "failed"})") }
            }
            if (outputs.isEmpty()) throw IllegalStateException("None of the images could be compressed. First failure: ${failures.firstOrNull() ?: "unknown"}")
            var msg = "Compressed ${outputs.size} of ${picked.size} image(s) at quality ${quality.toInt()}%:\n" + lines.joinToString("\n")
            if (failures.isNotEmpty()) msg += "\n\nFailed: " + failures.joinToString("; ")
            outputs to msg
        }
    }, "Compress all")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun CollageBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberMultiFilePicker(arrayOf("image/*"))
    var layout by remember { mutableStateOf(MoreImageTools.CollageLayout.GRID) }
    var png by remember { mutableStateOf(false) }
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select 2–9 images (${picked.size} selected)") }
    picked.forEachIndexed { i, f -> Text("${i + 1}. ${f.name}") }
    if (picked.size > 9) Text("Only the first 9 will be used.", color = MaterialTheme.colorScheme.error)
    Text("Layout:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MoreImageTools.CollageLayout.entries.forEach { l -> FilterChip(layout == l, { layout = l }, label = { Text(l.label) }) }
    }
    Row { RadioButton(!png, { png = false }); Text("JPEG (smaller)", Modifier.padding(top = 12.dp)); RadioButton(png, { png = true }); Text("PNG (lossless, bigger)", Modifier.padding(top = 12.dp)) }
    NoteCard("Photos are placed in the order listed and centre-cropped to fill each cell — edges may be trimmed, nothing is squeezed. Grid canvas is 2048×2048; strips are 2048×1024 (horizontal) or 1024×2048 (vertical). Unused grid cells stay dark.")
    ProcessButton(picked.size >= 2, running, {
        scope.runJob({ state = it }) {
            val use = picked.take(9)
            val inputs = use.map { FileUtils.copyUriToCache(context, it.uri) }
            val out = FileUtils.newOutput(context, "collage", if (png) "png" else "jpg")
            val (w, h) = MoreImageTools.makeCollage(inputs, out, layout, if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
            inputs.forEach { FileUtils.cleanupTemp(it) }
            listOf(out) to "Collage created from ${use.size} photos — canvas $w × $h px."
        }
    }, "Make collage")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun FiltersBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(arrayOf("image/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select image") }
    PickedCard(picked)
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(picked) {
        preview = if (picked == null) null else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val b = ImageTools.decode(f, 1024); FileUtils.cleanupTemp(f); b } } catch (e: Exception) { null }
    }
    var selected by remember { mutableStateOf(PhotoFilters.all[0]) }
    Text("Filter:")
    PhotoFilters.all.chunked(4).forEach { rowFilters ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowFilters.forEach { f -> FilterChip(selected === f, { selected = f }, label = { Text(f.name) }) }
        }
    }
    preview?.let { bmp ->
        Image(bmp.asImageBitmap(), contentDescription = "Preview with ${selected.name} filter",
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(selected.matrix)),
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
        Text("Live preview: ${selected.name} (on a downscaled copy — saving applies the same filter at full resolution).", style = MaterialTheme.typography.bodySmall)
    }
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val ext = picked.name.substringAfterLast('.', "jpg").lowercase()
            val out = FileUtils.newOutput(context, "filtered", if (ext == "png") "png" else "jpg")
            val src = ImageTools.decode(input)
            val filtered = PhotoFilters.apply(src, selected)
            ImageTools.save(filtered, out, ImageTools.formatForExt(out.extension), 95)
            filtered.recycle(); src.recycle(); FileUtils.cleanupTemp(input)
            listOf(out) to "${selected.name} filter applied and saved."
        }
    }, "Apply filter & save")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun ExifRemoverBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(arrayOf("image/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select image") }
    PickedCard(picked)
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var foundLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(picked) {
        foundLoaded = false
        found = if (picked == null) emptyList() else try {
            withContext(Dispatchers.IO) {
                val f = FileUtils.copyUriToCache(context, picked.uri)
                val m = ImageTools.exifInfo(f); FileUtils.cleanupTemp(f)
                m.entries.filter { it.key !in setOf("Dimensions", "File size", "Note") }.map { "${it.key}: ${it.value}" }
            }
        } catch (e: Exception) { emptyList() }
        foundLoaded = picked != null
    }
    if (picked != null && foundLoaded) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (found.isEmpty()) Text("No EXIF fields found in this photo — it is already metadata-light.")
        else { Text("Metadata found in the original:", style = MaterialTheme.typography.titleSmall); found.forEach { Text("• $it") } }
    } }
    NoteCard("The copy is re-encoded from raw pixels only, so it contains NO EXIF, camera or GPS data — guaranteed by how it is built, not by editing tags. The photo's orientation is baked into the pixels first, so it still displays the right way up. Re-encoding is at 95% quality (JPEG) or lossless (PNG).")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val ext = picked.name.substringAfterLast('.', "jpg").lowercase()
            val out = FileUtils.newOutput(context, "no_metadata", if (ext == "png") "png" else "jpg")
            val r = MoreImageTools.stripExif(input, out, ImageTools.formatForExt(out.extension))
            FileUtils.cleanupTemp(input)
            val base = if (r.fieldsFound.isEmpty()) "This photo had no EXIF metadata. A clean re-encoded copy was saved anyway."
                else "Removed metadata (${r.fieldsFound.joinToString(", ")}). The saved copy contains none of it."
            val orient = if (r.orientationDegrees != 0 || r.orientationFlipped) " Orientation was baked into the pixels so it displays correctly." else ""
            listOf(out) to base + orient
        }
    }, "Remove metadata & save copy")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun ImageWatermarkBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, _) = SingleImageHeader()
    var text by remember { mutableStateOf("") }
    var position by remember { mutableStateOf(MoreImageTools.StampPosition.BOTTOM_RIGHT) }
    var sizePct by remember { mutableStateOf(6f) }
    var opacity by remember { mutableStateOf(70f) }
    var dark by remember { mutableStateOf(false) }
    OutlinedTextField(text, { text = it }, label = { Text("Watermark text (your name, © you, brand…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Position:")
    MoreImageTools.StampPosition.entries.chunked(3).forEach { rowPos ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowPos.forEach { p -> FilterChip(position == p, { position = p }, label = { Text(p.label) }) }
        }
    }
    Text("Text size: ${sizePct.toInt()}% of image width"); Slider(sizePct, { sizePct = it }, valueRange = 3f..12f)
    Text("Opacity: ${opacity.toInt()}%"); Slider(opacity, { opacity = it }, valueRange = 10f..100f)
    Row { RadioButton(!dark, { dark = false }); Text("White text", Modifier.padding(top = 12.dp)); RadioButton(dark, { dark = true }); Text("Black text", Modifier.padding(top = 12.dp)) }
    NoteCard("The text is stamped permanently into the pixels (with a soft shadow so it stays readable). It cannot be removed later — keep your original if you need a clean copy. This is a visible watermark, not hidden/copyright metadata.")
    ProcessButton(picked != null && text.isNotBlank(), running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val ext = picked.name.substringAfterLast('.', "jpg").lowercase()
            val out = FileUtils.newOutput(context, "watermarked_photo", if (ext == "png") "png" else "jpg")
            MoreImageTools.stampText(input, out, text.trim(), position, sizePct, opacity.toInt(), dark, ImageTools.formatForExt(out.extension))
            FileUtils.cleanupTemp(input)
            listOf(out) to "Watermark \"${text.trim()}\" stamped at ${position.label.lowercase()}."
        }
    }, "Stamp watermark")
    ResultPanel(state) { state = RunState.Idle }
}

// ================= VIDEO =================

/** Picker + info card (same pattern as MediaToolScreen). Returns (picked, info). */
@Composable
private fun SingleVideoHeader(): Pair<PickedFile?, MediaTools.MediaInfo?> {
    val context = LocalContext.current
    val (picked, launch) = rememberFilePicker(arrayOf("video/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select video") }
    PickedCard(picked)
    var info by remember { mutableStateOf<MediaTools.MediaInfo?>(null) }
    LaunchedEffect(picked) {
        info = if (picked == null) null else try { withContext(Dispatchers.IO) { val f = FileUtils.copyUriToCache(context, picked.uri); val m = MediaTools.inspect(f); FileUtils.cleanupTemp(f); m } } catch (e: Exception) { null }
    }
    info?.let { m ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Duration: ${formatMs(m.durationMs)}")
            if (m.width > 0) Text("Resolution: ${m.width} × ${m.height}")
            if (m.bitrate > 0) Text("Bitrate: ${m.bitrate / 1000} kbps")
        } }
    }
    return picked to info
}

@Composable
private fun VideoSpeedBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, info) = SingleVideoHeader()
    val speeds = listOf(0.5f to "0.5×", 0.75f to "0.75×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×")
    var speed by remember { mutableStateOf(1.5f) }
    Text("Playback speed:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        speeds.forEach { (v, label) -> FilterChip(speed == v, { speed = v }, label = { Text(label) }) }
    }
    info?.let { if (it.durationMs > 0) Text("New duration will be about ${formatMs((it.durationMs / speed).toLong())} (from ${formatMs(it.durationMs)}).") }
    NoteCard("The video is genuinely re-encoded with Media3's speed-change effect: picture and sound are re-timed together at the same rate, so voices stay in sync (audio is time-stretched, not chopped). Keep the app open while it works.")
    ProcessButton(picked != null, running, {
        val beforeMs = info?.durationMs ?: 0L
        val label = speeds.firstOrNull { it.first == speed }?.second ?: "${speed}×"
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "speed_video", "mp4")
            MoreMediaTools.changeVideoSpeed(context, input, out, speed)
            val after = MediaTools.inspect(out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Speed set to $label. Duration: ${formatMs(beforeMs)} → ${formatMs(after.durationMs)}."
        }
    }, "Apply speed")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun VideoRotateFlipBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, _) = SingleVideoHeader()
    var rotateEnabled by remember { mutableStateOf(true) }
    var rotation by remember { mutableStateOf(90f) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    Row { Checkbox(rotateEnabled, { rotateEnabled = it }); Text("Rotate", Modifier.padding(top = 12.dp)) }
    if (rotateEnabled) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(90f, 180f, 270f).forEach { d -> FilterChip(rotation == d, { rotation = d }, label = { Text("${d.toInt()}°") }) }
    }
    Row { Checkbox(flipH, { flipH = it }); Text("Flip horizontal (mirror)", Modifier.padding(top = 12.dp)) }
    Row { Checkbox(flipV, { flipV = it }); Text("Flip vertical", Modifier.padding(top = 12.dp)) }
    NoteCard("Rotation and flips are applied by actually re-encoding the picture (a matrix effect) — the saved video really is turned/mirrored, not just flagged. A 90°/270° turn swaps width and height.")
    ProcessButton(picked != null && (rotateEnabled || flipH || flipV), running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "rotated_video", "mp4")
            MoreMediaTools.rotateFlipVideo(context, input, out, if (rotateEnabled) rotation else 0f, flipH, flipV)
            val after = MediaTools.inspect(out)
            FileUtils.cleanupTemp(input)
            val parts = ArrayList<String>()
            if (rotateEnabled) parts.add("rotated ${rotation.toInt()}°")
            if (flipH) parts.add("flipped horizontally")
            if (flipV) parts.add("flipped vertically")
            listOf(out) to "Done — ${parts.joinToString(", ")}. Output resolution: ${after.width} × ${after.height}."
        }
    }, "Apply")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun VideoCropBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, _) = SingleVideoHeader()
    data class Aspect(val label: String, val w: Float, val h: Float, val use: String)
    val aspects = listOf(
        Aspect("9:16", 9f, 16f, "Reels / Shorts / Status"),
        Aspect("1:1", 1f, 1f, "Square feed post"),
        Aspect("4:5", 4f, 5f, "Portrait feed post"),
        Aspect("16:9", 16f, 9f, "YouTube / landscape")
    )
    var selected by remember { mutableStateOf(aspects[0]) }
    Text("Crop to aspect ratio:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        aspects.forEach { a -> FilterChip(selected === a, { selected = a }, label = { Text(a.label) }) }
    }
    Text("Best for: ${selected.use}", style = MaterialTheme.typography.bodySmall)
    NoteCard("The frame is CENTRE-cropped to the chosen shape — edges are cut away, the picture is never squeezed or stretched. The video is re-encoded; the result shows the real output resolution.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "cropped_video", "mp4")
            MoreMediaTools.cropToAspect(context, input, out, selected.w, selected.h)
            val after = MediaTools.inspect(out)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Cropped to ${selected.label}. Output resolution: ${after.width} × ${after.height}."
        }
    }, "Crop video")
    ResultPanel(state) { state = RunState.Idle }
}

// ================= AUDIO =================

@Composable
private fun VoiceRecorderBody() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var startStamp by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    fun stopRecording(saveIt: Boolean) {
        val rec = recorder ?: return
        try { rec.stop() } catch (e: Exception) { if (saveIt) state = RunState.Failed("Recording stopped with an error: ${e.message}") }
        try { rec.release() } catch (_: Exception) {}
        recorder = null
        val wasRecording = recording
        recording = false
        val f = currentFile
        if (saveIt && wasRecording) {
            if (f != null && f.exists() && f.length() > 0) state = RunState.Done(listOf(f), "Recorded ${formatMs(elapsedMs)} — saved as M4A (AAC, 128 kbps).")
            else if (state !is RunState.Failed) state = RunState.Failed("The recording came out empty — the mic may have been in use by another app.")
        }
        if (!saveIt) f?.delete()
        currentFile = null
    }

    fun startRecording() {
        note = null; state = RunState.Idle
        val f = FileUtils.newOutput(context, "voice_recording", "m4a")
        val rec = MediaRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(f.absolutePath)
            rec.prepare(); rec.start()
            recorder = rec; currentFile = f
            startStamp = System.currentTimeMillis(); elapsedMs = 0L; recording = true
        } catch (e: Exception) {
            try { rec.release() } catch (_: Exception) {}
            f.delete()
            state = RunState.Failed("Could not start recording: ${e.message ?: "microphone unavailable"}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) startRecording() else note = "Mic permission is required to record. You can allow it from the button below or in system Settings → Apps."
    }

    // Keep the hardware released if the user leaves mid-recording; an unfinished take is discarded.
    val liveRecorder by rememberUpdatedState(recorder)
    val liveRecording by rememberUpdatedState(recording)
    val liveFile by rememberUpdatedState(currentFile)
    DisposableEffect(Unit) {
        onDispose {
            if (liveRecording) { runCatching { liveRecorder?.stop() }; liveFile?.delete() }
            runCatching { liveRecorder?.release() }
        }
    }
    LaunchedEffect(recording) {
        while (recording) { elapsedMs = System.currentTimeMillis() - startStamp; delay(200) }
    }

    NoteCard("Records with the phone's microphone and saves as M4A (AAC, 128 kbps). The recording stays on this device — nothing is uploaded. Use Save/Share on the result to keep it or send it.")
    if (recording) {
        Text("● Recording… ${formatMs(elapsedMs)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { stopRecording(saveIt = true) }, modifier = Modifier.fillMaxWidth()) { Text("⏹ Stop & save") }
        OutlinedButton(onClick = { stopRecording(saveIt = false) }, modifier = Modifier.fillMaxWidth()) { Text("Discard") }
    } else {
        Button(
            onClick = { if (hasMicPermission) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🎙️ Start recording") }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun AudioCompressBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(arrayOf("audio/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select audio") }
    PickedCard(picked)
    var bitrate by remember { mutableStateOf(64_000) }
    Text("Target bitrate:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(32_000, 48_000, 64_000, 96_000, 128_000).forEach { b -> FilterChip(bitrate == b, { bitrate = b }, label = { Text("${b / 1000}k") }) }
    }
    var mono by remember { mutableStateOf(false) }
    var halfRate by remember { mutableStateOf(false) }
    Row { Checkbox(mono, { mono = it }); Text("Convert to mono (great for voice — size drops further)", Modifier.padding(top = 12.dp)) }
    Row { Checkbox(halfRate, { halfRate = it }); Text("Halve sample rate (music will sound duller; voice stays clear)", Modifier.padding(top = 12.dp)) }
    NoteCard("The audio is decoded and genuinely re-encoded as AAC/M4A at your chosen bitrate. Voice notes shrink a lot at 32–48k. If the source is already at or below this bitrate, the file may not shrink — the result shows the real sizes either way.")
    ProcessButton(picked != null, running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "compressed_audio", "m4a")
            val (b, a) = MoreMediaTools.compressAudio(input, out, bitrate, mono, halfRate)
            FileUtils.cleanupTemp(input)
            listOf(out) to if (a < b) "Compressed: ${FileUtils.formatSize(b)} → ${FileUtils.formatSize(a)} at ${bitrate / 1000} kbps AAC."
                else "Original: ${FileUtils.formatSize(b)} → Output: ${FileUtils.formatSize(a)}. No reduction this time — the source was already at/below this quality level."
        }
    }, "Compress audio")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun AudioFadeBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    val (picked, launch) = rememberFilePicker(arrayOf("audio/*"))
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select audio") }
    PickedCard(picked)
    var fadeIn by remember { mutableStateOf(2f) }
    var fadeOut by remember { mutableStateOf(3f) }
    Text("Fade in: ${String.format("%.1f", fadeIn)} seconds"); Slider(fadeIn, { fadeIn = it }, valueRange = 0f..15f)
    Text("Fade out: ${String.format("%.1f", fadeOut)} seconds"); Slider(fadeOut, { fadeOut = it }, valueRange = 0f..15f)
    NoteCard("Smooth linear volume ramps are applied to the actual audio samples (no hard starts/stops). Output is WAV, the same as this app's volume and speed tools. On short clips the two fades can overlap — at each point the quieter ramp wins.")
    ProcessButton(picked != null && (fadeIn > 0f || fadeOut > 0f), running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "faded_audio", "wav")
            MoreMediaTools.fadeAudio(input, out, fadeIn.toDouble(), fadeOut.toDouble())
            FileUtils.cleanupTemp(input)
            listOf(out) to "Fades applied — in ${String.format("%.1f", fadeIn)}s, out ${String.format("%.1f", fadeOut)}s (WAV output)."
        }
    }, "Apply fades")
    ResultPanel(state) { state = RunState.Idle }
}

// ================= TEXT =================

@Composable
private fun PasswordGeneratorBody() {
    var length by remember { mutableStateOf(16f) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf(MoreTextTools.generatePassword(16, upper = true, lower = true, digits = true, symbols = true)) }
    var genError by remember { mutableStateOf<String?>(null) }
    fun regenerate() {
        genError = null
        try { result = MoreTextTools.generatePassword(length.toInt(), upper, lower, digits, symbols) }
        catch (e: Exception) { genError = e.message }
    }
    Text("Length: ${length.toInt()} characters"); Slider(length, { length = it }, valueRange = 8f..64f)
    Row { Checkbox(lower, { lower = it }); Text("lowercase (a–z)", Modifier.padding(top = 12.dp)); Checkbox(upper, { upper = it }); Text("UPPERCASE (A–Z)", Modifier.padding(top = 12.dp)) }
    Row { Checkbox(digits, { digits = it }); Text("Digits (0–9)", Modifier.padding(top = 12.dp)); Checkbox(symbols, { symbols = it }); Text("Symbols (!@#$…)", Modifier.padding(top = 12.dp)) }
    Button(onClick = { regenerate() }, modifier = Modifier.fillMaxWidth()) { Text("Generate new password") }
    genError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (genError == null) {
        Card(Modifier.fillMaxWidth()) { Text(result.password, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium) }
        Text("Strength: ${result.strengthLabel} — about ${result.entropyBits} bits of entropy. At least one character from every ticked type is guaranteed.", style = MaterialTheme.typography.bodySmall)
        MoreCopyButton(result.password, "Copy password")
    }
    NoteCard("Generated on this device with a cryptographically secure random source (SecureRandom). The password is never stored, logged or sent anywhere — copy it into your password manager and it's gone when you leave this screen.")
}

@Composable
private fun QrGeneratorBody() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }; val running = state is RunState.Running
    var text by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(BarcodeFormat.QR_CODE) }
    val formats = listOf("QR Code" to BarcodeFormat.QR_CODE, "Code 128" to BarcodeFormat.CODE_128, "EAN-13" to BarcodeFormat.EAN_13)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        formats.forEach { (label, f) -> FilterChip(format == f, { format = f }, label = { Text(label) }) }
    }
    OutlinedTextField(text, { text = it }, label = {
        Text(when (format) { BarcodeFormat.EAN_13 -> "12 digits (check digit added) or full 13-digit code"; BarcodeFormat.CODE_128 -> "Text / code for the barcode"; else -> "Text or link (https://…)" })
    }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    val (previewBmp, genError) = remember(text, format) {
        if (text.isBlank()) null to null
        else try {
            val w = if (format == BarcodeFormat.QR_CODE) 600 else 1000
            val h = if (format == BarcodeFormat.QR_CODE) 600 else 320
            MoreTextTools.barcodeBitmap(text, format, w, h) to null
        } catch (e: Exception) { null to (e.message ?: "Could not create this code") }
    }
    genError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    previewBmp?.let { bmp ->
        Image(bmp.asImageBitmap(), contentDescription = "Generated code preview", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    }
    NoteCard("Codes are created entirely on-device with the open-source ZXing library — your link/text is never sent anywhere. QR fits links and text; Code 128 suits labels and inventory; EAN-13 is the retail barcode and needs a genuinely valid number (a wrong check digit is reported, not silently \"fixed\").")
    ProcessButton(previewBmp != null, running, {
        scope.runJob({ state = it }) {
            val w = if (format == BarcodeFormat.QR_CODE) 1024 else 1600
            val h = if (format == BarcodeFormat.QR_CODE) 1024 else 512
            val bmp = MoreTextTools.barcodeBitmap(text, format, w, h)
            val out = FileUtils.newOutput(context, "code", "png")
            ImageTools.save(bmp, out, Bitmap.CompressFormat.PNG)
            bmp.recycle()
            listOf(out) to "${formats.first { it.second == format }.first} saved as a PNG. Share or print it — any camera or scanner app can read it."
        }
    }, "Save code as PNG")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun TextEncryptBody() {
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(input, { input = it }, label = { Text("Text — plain to encrypt, or encrypted text to decrypt") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), minLines = 4)
    OutlinedTextField(password, { password = it }, label = { Text("Password (min 4 characters)") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { error = null; output = ""; try { output = MoreTextTools.encryptText(input, password) } catch (e: Exception) { error = e.message } }, modifier = Modifier.weight(1f)) { Text("🔒 Encrypt") }
        OutlinedButton(onClick = { error = null; output = ""; try { output = MoreTextTools.decryptText(input, password) } catch (e: Exception) { error = e.message } }, modifier = Modifier.weight(1f)) { Text("🔓 Decrypt") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    MoreOutputBox(output)
    if (output.isNotEmpty()) MoreCopyButton(output)
    NoteCard("Real AES-256-GCM encryption. The key is derived from your password with PBKDF2 (120,000 rounds) and a fresh random salt every time — encrypting the same text twice gives different output. The encrypted result is plain Base64 text you can paste into any note or message. Your password is never stored; if you forget it, the text cannot be recovered — that is the point of real encryption.")
}

@Composable
private fun LoremBody() {
    var paragraphs by remember { mutableStateOf(2f) }
    var classic by remember { mutableStateOf(true) }
    var output by remember { mutableStateOf("") }
    Text("Paragraphs: ${paragraphs.toInt()}"); Slider(paragraphs, { paragraphs = it }, valueRange = 1f..10f)
    Row { Checkbox(classic, { classic = it }); Text("Start with the classic \"Lorem ipsum dolor sit amet…\"", Modifier.padding(top = 12.dp)) }
    Button(onClick = { output = MoreTextTools.loremIpsum(paragraphs.toInt(), classic) }, modifier = Modifier.fillMaxWidth()) { Text("Generate") }
    MoreOutputBox(output)
    if (output.isNotEmpty()) MoreCopyButton(output)
    Text("Placeholder text for designs, mockups and layouts — it has no meaning, which is exactly why it's useful.", style = MaterialTheme.typography.bodySmall)
}

// ================= CALCULATORS =================

@Composable
private fun BmiBody() {
    var ftMode by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(block: () -> String) { error = null; result = try { block() } catch (e: Exception) { error = e.message ?: "Invalid input"; "" } }
    Row { RadioButton(!ftMode, { ftMode = false }); Text("Height in cm", Modifier.padding(top = 12.dp)); RadioButton(ftMode, { ftMode = true }); Text("Height in ft + in", Modifier.padding(top = 12.dp)) }
    MoreNumField("Weight (kg)", weight) { weight = it }
    if (!ftMode) MoreNumField("Height (cm)", heightCm) { heightCm = it }
    else { MoreNumField("Feet", feet) { feet = it }; MoreNumField("Inches", inches) { inches = it } }
    Button(onClick = { run {
        val hCm = if (ftMode) MoreCalculators.heightCmFromFtIn(feet.filter { it.isDigit() }.toIntOrNull() ?: throw IllegalArgumentException("Enter feet"), inches.toDoubleOrNull() ?: 0.0)
            else heightCm.toDouble()
        val r = MoreCalculators.bmi(weight.toDouble(), hCm)
        "Your BMI: ${String.format("%.1f", r.bmi)} — ${r.category}\nFor your height (${fmtMore(hCm)} cm), the standard healthy weight range is ${fmtMore(r.healthyMinKg)}–${fmtMore(r.healthyMaxKg)} kg."
    } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate BMI") }
    NoteCard("BMI is a rough screening number for adults — it ignores muscle, age, pregnancy and body composition, so treat it as a starting point, not a diagnosis. Some Indian medical guidelines use lower cut-offs for South Asian adults (overweight from BMI 23). For personal advice, see a doctor.")
    ResultCard(result, error)
}

@Composable
private fun SipBody() {
    var monthly by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("12") }
    var years by remember { mutableStateOf("10") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(block: () -> String) { error = null; result = try { block() } catch (e: Exception) { error = e.message ?: "Invalid input"; "" } }
    MoreNumField("Monthly investment (₹)", monthly) { monthly = it }
    MoreNumField("Expected return (% per year)", rate) { rate = it }
    MoreNumField("Duration (years)", years) { years = it }
    Button(onClick = { run {
        val r = MoreCalculators.sip(monthly.toDouble(), rate.toDouble(), years.filter { it.isDigit() }.toIntOrNull() ?: throw IllegalArgumentException("Enter duration in years"))
        "You invest: ₹${fmtMore(r.invested)}\nEstimated gains: ₹${fmtMore(r.estimatedGain)}\nMaturity value: ₹${fmtMore(r.maturityValue)}\nAssumes the return stays constant and a payment is made every month for ${years} year(s)."
    } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate SIP") }
    NoteCard("This is a mathematical estimate, NOT financial advice and NOT a guarantee. Real market returns change every year, and mutual fund investments carry risk — the actual value can be higher or lower.")
    ResultCard(result, error)
}

@Composable
private fun TipBody() {
    var bill by remember { mutableStateOf("") }
    var tipText by remember { mutableStateOf("10") }
    var people by remember { mutableStateOf("1") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(block: () -> String) { error = null; result = try { block() } catch (e: Exception) { error = e.message ?: "Invalid input"; "" } }
    MoreNumField("Bill amount (₹)", bill) { bill = it }
    Text("Tip:")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 5, 10, 15, 20, 25).forEach { t -> FilterChip(tipText == t.toString(), { tipText = t.toString() }, label = { Text("$t%") }) }
    }
    MoreNumField("…or custom tip (%)", tipText) { tipText = it }
    MoreNumField("Split between (people)", people) { people = it }
    Button(onClick = { run {
        val ppl = people.filter { it.isDigit() }.toIntOrNull() ?: throw IllegalArgumentException("Enter how many people are splitting")
        val r = MoreCalculators.tip(bill.toDouble(), tipText.toDouble(), ppl)
        "Tip: ₹${fmtMore(r.tipAmount)}\nTotal to pay: ₹${fmtMore(r.total)}\nPer person: ₹${fmtMore(r.perPerson)} (including a tip share of ₹${fmtMore(r.tipPerPerson)})"
    } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
    ResultCard(result, error)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDiffBody() {
    val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf(LocalDate.now().format(fmt)) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(block: () -> String) { error = null; result = try { block() } catch (e: Exception) { error = "Enter both dates as dd-MM-yyyy (use the Pick buttons). ${e.message ?: ""}"; "" } }
    OutlinedTextField(fromText, { fromText = it }, label = { Text("From date (dd-MM-yyyy)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedButton(onClick = { showFrom = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick from date") }
    OutlinedTextField(toText, { toText = it }, label = { Text("To date (dd-MM-yyyy)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedButton(onClick = { showTo = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick to date") }
    if (showFrom) {
        val picker = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showFrom = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { ms -> fromText = LocalDate.ofEpochDay(ms / 86_400_000).format(fmt) }; showFrom = false }) { Text("OK") }
        }) { DatePicker(picker) }
    }
    if (showTo) {
        val picker = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showTo = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { ms -> toText = LocalDate.ofEpochDay(ms / 86_400_000).format(fmt) }; showTo = false }) { Text("OK") }
        }) { DatePicker(picker) }
    }
    Button(onClick = { run {
        val f = LocalDate.parse(fromText.trim(), fmt)
        val t = LocalDate.parse(toText.trim(), fmt)
        val r = MoreCalculators.dateDifference(f, t)
        val main = "Difference: ${r.years} years, ${r.months} months, ${r.days} days\nTotal days: ${r.totalDays} (${r.totalDays / 7} weeks + ${r.totalDays % 7} days)"
        if (r.orderSwapped) main + "\nNote: the To date is earlier than the From date, so the span between them is shown as a positive difference." else main
    } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate difference") }
    NoteCard("The years/months/days breakdown is calendar-accurate (leap years and month lengths handled by the date library) — the same engine as the Age Calculator.")
    ResultCard(result, error)
}
