package com.pushpendratech.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.Calculators
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.core.GifMaker
import com.pushpendratech.toolbox.core.MediaTools
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val VIDEO_MIME = arrayOf("video/*")
private val AUDIO_MIME = arrayOf("audio/*")

@Composable
fun MediaToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        var state by remember { mutableStateOf<RunState>(RunState.Idle) }
        val running = state is RunState.Running

        if (tool.id == "video_estimator") {
            EstimatorBody(); return@ToolScaffold
        }
        if (tool.id == "audio_merge") {
            val (picked, launch) = rememberMultiFilePicker(AUDIO_MIME)
            Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select audio files (${picked.size} selected)") }
            picked.forEachIndexed { i, f -> Text("${i + 1}. ${f.name}") }
            NoteCard("Files are joined in the order listed. All files should use the same codec (e.g. all M4A/AAC). If merging fails, convert each file to M4A with Audio Converter first, then merge.")
            ProcessButton(picked.size >= 2, running, {
                scope.runJob({ state = it }) {
                    val inputs = picked.map { FileUtils.copyUriToCache(context, it.uri) }
                    val out = FileUtils.newOutput(context, "merged_audio", "m4a"); MediaTools.mergeAudio(inputs, out)
                    inputs.forEach { FileUtils.cleanupTemp(it) }; listOf(out) to "Merged ${picked.size} audio files into one M4A."
                }
            }, "Merge audio")
            ResultPanel(state) { state = RunState.Idle }
            return@ToolScaffold
        }

        val isVideo = tool.category.name == "VIDEO"
        val (picked, launch) = rememberFilePicker(if (isVideo) VIDEO_MIME else AUDIO_MIME)
        Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text(if (isVideo) "Select video" else "Select audio") }
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
                if (m.sampleRate > 0) Text("Sample rate: ${m.sampleRate} Hz"); if (m.channels > 0) Text("Channels: ${m.channels}")
                if (m.mime.isNotEmpty()) Text("Format: ${m.mime}")
            } }
        }
        fun inputFile(): File = FileUtils.copyUriToCache(context, picked!!.uri)
        val durationSec = (info?.durationMs ?: 0) / 1000.0

        when (tool.id) {
            "video_compress" -> {
                var preset by remember { mutableStateOf(1) }
                Text("Compress to (target video bitrate):")
                listOf("Light — 4 Mbps (near-original for 720p)", "Recommended — 2 Mbps", "Strong — 1 Mbps", "Maximum — 500 kbps (smallest, softer picture)").forEachIndexed { i, l ->
                    Row { RadioButton(preset == i, { preset = i }); Text(l, Modifier.padding(top = 12.dp)) } }
                NoteCard("The video is genuinely re-encoded at the lower bitrate (H.264 + AAC). If the source bitrate is already lower, the file may not shrink — that's normal and will be visible in the result sizes. Keep the app open while it works; large videos take time.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "compressed_video", "mp4")
                        MediaTools.transcode(context, input, out, videoBitrateBps = listOf(4_000_000, 2_000_000, 1_000_000, 500_000)[preset])
                        val msg = "Original: ${FileUtils.formatSize(input.length())} → Compressed: ${FileUtils.formatSize(out.length())}." +
                            if (out.length() >= input.length()) " No reduction this time — the source was already at/below this bitrate." else ""
                        FileUtils.cleanupTemp(input); listOf(out) to msg
                    }
                })
            }
            "video_resolution" -> {
                var height by remember { mutableStateOf(720) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(720, 480, 360, 240).forEach { h -> FilterChip(height == h, { height = h }, label = { Text("${h}p") }) } }
                NoteCard("Re-encodes to the chosen height, keeping the aspect ratio. The result card shows the real output size; the reported resolution is the target height (width scales proportionally, even numbers rounded by the encoder).")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "resized_video", "mp4")
                        MediaTools.transcode(context, input, out, targetHeight = height)
                        val actual = MediaTools.inspect(out); FileUtils.cleanupTemp(input)
                        listOf(out) to "Done. Actual output resolution: ${actual.width} × ${actual.height}, size ${FileUtils.formatSize(out.length())}."
                    }
                })
            }
            "video_trim", "audio_trim" -> {
                var start by remember { mutableStateOf("0") }; var end by remember { mutableStateOf("") }
                if (durationSec > 0 && end.isEmpty()) end = String.format("%.1f", durationSec)
                OutlinedTextField(start, { start = it }, label = { Text("Start (seconds or mm:ss)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(end, { end = it }, label = { Text("End (seconds or mm:ss)") }, modifier = Modifier.fillMaxWidth())
                NoteCard("Trimming is lossless stream-copy (no re-encode), so it's fast — but cuts land on the nearest keyframe, which can differ from your exact second by a little on some videos.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val s = parseTimeToMs(start) ?: throw IllegalArgumentException("Enter a valid start time")
                        val e = parseTimeToMs(end) ?: throw IllegalArgumentException("Enter a valid end time")
                        if (e <= s) throw IllegalArgumentException("End must be after start")
                        val input = inputFile()
                        if (isVideo) { val out = FileUtils.newOutput(context, "trimmed", "mp4"); MediaTools.trimVideo(input, out, s, e); FileUtils.cleanupTemp(input); listOf(out) to "Trimmed ${formatMs(s)} → ${formatMs(e)}." }
                        else { val out = FileUtils.newOutput(context, "trimmed_audio", "m4a"); MediaTools.trimAudio(input, out, s, e); FileUtils.cleanupTemp(input); listOf(out) to "Trimmed ${formatMs(s)} → ${formatMs(e)} (saved as M4A)." }
                    }
                })
            }
            "video_to_mp3" -> {
                NoteCard("Honest limitation: Android has no built-in MP3 encoder, and the MP3 encoder library (LAME) carries patent-era/GPL licensing baggage we avoid. This tool extracts the audio track WITHOUT re-encoding into an .m4a file that plays everywhere. It never renames a file to .mp3 falsely — if your source audio is AAC (usual for MP4), the copy is bit-for-bit identical, just in an M4A container.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "audio_track", "m4a"); MediaTools.extractAudio(input, out)
                        FileUtils.cleanupTemp(input); listOf(out) to "Audio track extracted to M4A (${FileUtils.formatSize(out.length())})."
                    }
                }, "Extract audio")
            }
            "video_mute" -> {
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "muted", "mp4"); MediaTools.muteVideo(input, out)
                        FileUtils.cleanupTemp(input); listOf(out) to "Audio track removed. The video stream itself was copied unchanged (no quality loss)."
                    }
                }, "Remove audio")
            }
            "video_to_gif" -> {
                var start by remember { mutableStateOf("0") }; var length by remember { mutableStateOf("3") }; var width by remember { mutableStateOf(320f) }
                OutlinedTextField(start, { start = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Start (seconds)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(length, { length = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Length (seconds, max 10)") }, modifier = Modifier.fillMaxWidth())
                Text("GIF width: ${width.toInt()}px"); Slider(width, { width = it }, valueRange = 160f..480f)
                NoteCard("GIFs grow fast: even 3 seconds can be several MB. The tool samples 8 frames/second and uses a 256-colour palette, so colours are approximate — fine for memes/sharing, not for archiving.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val startMs = (start.toDoubleOrNull() ?: 0.0) * 1000
                        val lenMs = ((length.toDoubleOrNull() ?: 3.0).coerceIn(0.5, 10.0)) * 1000
                        val frames = ArrayList<android.graphics.Bitmap>(); val frameCount = (lenMs / 125).toInt().coerceIn(2, 80)
                        val srcInfo = MediaTools.inspect(input); val srcW = srcInfo.width.coerceAtLeast(1); val srcH = srcInfo.height.coerceAtLeast(1)
                        val targetH = (width * srcH / srcW).toInt().coerceAtLeast(1)
                        for (i in 0 until frameCount) {
                            val bmp = MediaTools.frameAt(input, (startMs + i * 125).toLong(), 1600)
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, width.toInt(), (width * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
                            frames.add(scaled); if (scaled !== bmp) bmp.recycle()
                        }
                        val out = FileUtils.newOutput(context, "clip", "gif"); val size = GifMaker.makeGif(frames, out, 125)
                        frames.forEach { it.recycle() }; FileUtils.cleanupTemp(input)
                        listOf(out) to "GIF created (${frames.size} frames, ${targetH}px tall): ${FileUtils.formatSize(size)}." + if (size > 8_000_000) " That's large — try a shorter clip or smaller width." else ""
                    }
                }, "Create GIF")
            }
            "video_frames" -> {
                var at by remember { mutableStateOf("1") }
                OutlinedTextField(at, { at = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Timestamp (seconds)") }, modifier = Modifier.fillMaxWidth())
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val bmp = MediaTools.frameAt(input, ((at.toDoubleOrNull() ?: 1.0) * 1000).toLong(), 1920)
                        val out = FileUtils.newOutput(context, "frame", "jpg"); FileUtils.outputDir(context)
                        out.outputStream().use { o -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o) }; bmp.recycle(); FileUtils.cleanupTemp(input)
                        listOf(out) to "Frame saved as JPG."
                    }
                }, "Extract frame")
            }
            "audio_convert" -> {
                var target by remember { mutableStateOf("wav") }
                Row { RadioButton(target == "wav", { target = "wav" }); Text("WAV (lossless PCM, larger)", Modifier.padding(top = 12.dp)); RadioButton(target == "m4a", { target = "m4a" }); Text("M4A (AAC 128 kbps)", Modifier.padding(top = 12.dp)) }
                NoteCard("Only formats this app can genuinely encode on-device are offered: WAV and M4A (AAC). There is no MP3 encoder in Android, so MP3 output is not faked.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "converted_audio", target); MediaTools.convertAudio(context, input, out, target)
                        FileUtils.cleanupTemp(input); listOf(out) to "Converted to ${target.uppercase()}."
                    }
                })
            }
            "audio_volume" -> {
                var gain by remember { mutableStateOf(1.5f) }
                Text("Volume: ${String.format("%.1f", gain)}×"); Slider(gain, { gain = it }, valueRange = 0.2f..3f)
                NoteCard("Works by scaling the actual audio samples (decoded to PCM, saved as WAV). Samples that would exceed full scale are clamped, which can sound harsh at very high gain — 1.5–2× is a sensible range.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "volume", "wav"); MediaTools.changeVolume(input, out, gain)
                        FileUtils.cleanupTemp(input); listOf(out) to "Volume adjusted to ${String.format("%.1f", gain)}× (WAV output)."
                    }
                })
            }
            "audio_speed" -> {
                var speed by remember { mutableStateOf(1.25f) }
                Text("Speed: ${String.format("%.2f", speed)}×"); Slider(speed, { speed = it }, valueRange = 0.5f..2f)
                NoteCard("This changes speed by resampling, so pitch shifts too (faster = higher voice). True pitch-preserving time-stretch needs a dedicated DSP library and is NOT claimed here.")
                ProcessButton(picked != null, running, {
                    scope.runJob({ state = it }) {
                        val input = inputFile(); val out = FileUtils.newOutput(context, "speed", "wav"); MediaTools.changeSpeed(input, out, speed)
                        FileUtils.cleanupTemp(input); listOf(out) to "Speed changed to ${String.format("%.2f", speed)}× by resampling (WAV output)."
                    }
                })
            }
            "audio_inspector" -> {
                if (info == null && picked != null) Text("Reading metadata…")
                NoteCard("Everything above was read from the file on this device. Missing values mean the file itself doesn't store them — nothing is guessed.")
            }
            else -> Text("Unknown media tool: ${tool.id}")
        }
        ResultPanel(state) { state = RunState.Idle }
    }
}

@Composable
private fun EstimatorBody() {
    var duration by remember { mutableStateOf("60") }; var vBitrate by remember { mutableStateOf("2000") }; var aBitrate by remember { mutableStateOf("128") }
    OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Duration (seconds)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(vBitrate, { vBitrate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Video bitrate (kbps) — e.g. 8000 for 1080p, 2500 for 720p, 800 for 480p") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(aBitrate, { aBitrate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Audio bitrate (kbps) — 128 is typical") }, modifier = Modifier.fillMaxWidth())
    val d = duration.toDoubleOrNull(); val v = vBitrate.toDoubleOrNull(); val a = aBitrate.toDoubleOrNull()
    if (d != null && v != null && a != null) {
        val bytes = try { Calculators.estimateVideoBytes(d, v, a) } catch (e: Exception) { 0 }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("Estimated size: ${FileUtils.formatSize(bytes)}")
            Text("This is an ESTIMATE: (video + audio bitrate) × duration ÷ 8. Real files vary ~1–3% with container overhead and variable-bitrate encoding.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}
