package com.pushpendratech.toolbox.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.AiTools
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val IMAGE_MIME = arrayOf("image/*")

/**
 * AI Tools screens (ML Kit + Android speech, fully on-device). Photos and text
 * never leave the phone; the only network use in this whole category is the
 * translator's one-time language-pack download, which the UI announces.
 */
@Composable
fun AiToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        when (tool.id) {
            "ai_bg_remover" -> BgRemoverBody()
            "ai_face_blur" -> FaceBlurBody()
            "ai_ocr" -> OcrBody()
            "ai_labeler" -> LabelerBody()
            "ai_barcode_scan" -> BarcodeBody()
            "ai_translator" -> TranslatorBody()
            "ai_voice_text" -> VoiceBody()
            else -> NoteCard("This AI tool is not part of this build.")
        }
    }
}

@Composable
private fun BgRemoverBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val (picked, launch) = rememberFilePicker(IMAGE_MIME)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select photo") }
    PickedCard(picked)
    var mode by remember { mutableStateOf(AiTools.BgMode.TRANSPARENT) }
    Text("Background becomes:")
    Row {
        RadioButton(mode == AiTools.BgMode.TRANSPARENT, { mode = AiTools.BgMode.TRANSPARENT }); Text("Transparent", Modifier.padding(top = 12.dp))
        RadioButton(mode == AiTools.BgMode.WHITE, { mode = AiTools.BgMode.WHITE }); Text("White", Modifier.padding(top = 12.dp))
        RadioButton(mode == AiTools.BgMode.BLURRED, { mode = AiTools.BgMode.BLURRED }); Text("Blurred", Modifier.padding(top = 12.dp))
    }
    NoteCard("AI portrait segmentation — works best on photos of people (selfies, portraits). It is not a general object remover: photos without a person may give a poor result, and the app will still show you exactly what it produced. Fully offline.")
    ProcessButton(picked != null, state is RunState.Running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "bg_removed", "png")
            AiTools.removeBackground(context, input, out, mode)
            FileUtils.cleanupTemp(input)
            listOf(out) to "Background removed (${mode.name.lowercase()}). Saved as PNG."
        }
    }, "Remove background")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun FaceBlurBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val (picked, launch) = rememberFilePicker(IMAGE_MIME)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select photo") }
    PickedCard(picked)
    NoteCard("Detects faces in the photo and blurs them — handy before sharing photos with children or bystanders. Detection runs on your phone; if no face is found, you'll be told honestly instead of getting an unchanged photo.")
    ProcessButton(picked != null, state is RunState.Running, {
        scope.runJob({ state = it }) {
            val input = FileUtils.copyUriToCache(context, picked!!.uri)
            val out = FileUtils.newOutput(context, "faces_blurred", "jpg")
            val n = AiTools.blurFaces(input, out)
            FileUtils.cleanupTemp(input)
            if (n == 0) { out.delete(); throw IllegalStateException("No faces were detected in this photo, so nothing was saved.") }
            listOf(out) to "Blurred $n ${if (n == 1) "face" else "faces"}."
        }
    }, "Detect & blur faces")
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun OcrBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    val (picked, launch) = rememberFilePicker(IMAGE_MIME)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select photo") }
    PickedCard(picked)
    var script by remember { mutableStateOf(AiTools.OcrScript.LATIN) }
    Text("Photo text is mostly:")
    Row {
        RadioButton(script == AiTools.OcrScript.LATIN, { script = AiTools.OcrScript.LATIN }); Text("English / Latin", Modifier.padding(top = 12.dp))
        RadioButton(script == AiTools.OcrScript.DEVANAGARI, { script = AiTools.OcrScript.DEVANAGARI }); Text("Hindi (देवनागरी)", Modifier.padding(top = 12.dp))
    }
    NoteCard("Reads text from the photo on-device (Google ML Kit). Clear, straight photos work best. The text is also saved as a .txt file below so you can share it.")
    var ocrText by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    ProcessButton(picked != null, state is RunState.Running, {
        state = RunState.Running
        scope.launch {
            try {
                val input = FileUtils.copyUriToCache(context, picked!!.uri)
                val text = withContext(Dispatchers.IO) { AiTools.recognizeText(input, script) }
                FileUtils.cleanupTemp(input)
                if (text.isBlank()) {
                    ocrText = null
                    state = RunState.Failed("No readable text was found in this photo. Try a clearer, closer photo.")
                } else {
                    ocrText = text
                    val out = FileUtils.newOutput(context, "recognized_text", "txt")
                    out.writeText(text)
                    state = RunState.Done(listOf(out), "Recognized ${text.length} characters — full text below.")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) {
                state = RunState.Failed("${e.javaClass.simpleName}: ${e.message ?: "OCR failed on this photo."}")
            }
        }
    }, "Extract text")
    ocrText?.let { text ->
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text("Recognized text") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp))
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy text") }
    }
    ResultPanel(state) { state = RunState.Idle }
}

@Composable
private fun LabelerBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (picked, launch) = rememberFilePicker(IMAGE_MIME)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select photo") }
    PickedCard(picked)
    NoteCard("Google ML Kit's on-device image labeler names what's in the photo, with a confidence score for each label. Labels are in English.")
    var running by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf<List<Pair<String, Float>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    ProcessButton(picked != null, running, {
        running = true; error = null
        scope.launch {
            try {
                val input = FileUtils.copyUriToCache(context, picked!!.uri)
                val found = withContext(Dispatchers.IO) { AiTools.labelImage(input) }
                FileUtils.cleanupTemp(input)
                if (found.isEmpty()) error = "No labels could be identified in this photo."
                labels = found
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { error = "${e.javaClass.simpleName}: ${e.message ?: "Labeling failed."}" }
            running = false
        }
    }, "Identify photo")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    labels?.takeIf { it.isNotEmpty() }?.let { list ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.take(12).forEach { (label, conf) -> Text("$label — ${(conf * 100).toInt()}%") }
            }
        }
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(list.joinToString("\n") { (l, c) -> "$l — ${(c * 100).toInt()}%" })) }, modifier = Modifier.fillMaxWidth()) { Text("Copy labels") }
    }
}

@Composable
private fun BarcodeBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (picked, launch) = rememberFilePicker(IMAGE_MIME)
    Button(onClick = launch, modifier = Modifier.fillMaxWidth()) { Text("Select photo") }
    PickedCard(picked)
    NoteCard("Scans QR codes and barcodes (EAN, UPC, Code 128 and more) from a photo or screenshot — on-device, nothing is uploaded.")
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    ProcessButton(picked != null, running, {
        running = true; error = null
        scope.launch {
            try {
                val input = FileUtils.copyUriToCache(context, picked!!.uri)
                val found = withContext(Dispatchers.IO) { AiTools.scanBarcodes(input) }
                FileUtils.cleanupTemp(input)
                if (found.isEmpty()) error = "No QR code or barcode was found in this photo. Try a sharper, closer image."
                results = found
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { error = "${e.javaClass.simpleName}: ${e.message ?: "Scanning failed."}" }
            running = false
        }
    }, "Scan photo")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    results?.forEach { (format, value) ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(format, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("Copy") }
            }
        }
    }
}

private val TRANSLATE_LANGS = listOf(
    "en" to "English", "hi" to "Hindi (हिंदी)", "bn" to "Bengali (বাংলা)", "ta" to "Tamil (தமிழ்)",
    "te" to "Telugu (తెలుగు)", "mr" to "Marathi (मराठी)", "gu" to "Gujarati (ગુજરાતી)", "pa" to "Punjabi (ਪੰਜਾਬੀ)",
    "kn" to "Kannada (ಕನ್ನಡ)", "ml" to "Malayalam (മലയാളം)", "ur" to "Urdu (اردو)", "es" to "Spanish",
    "fr" to "French", "de" to "German", "ar" to "Arabic", "zh" to "Chinese", "ja" to "Japanese",
    "ko" to "Korean", "ru" to "Russian", "pt" to "Portuguese", "it" to "Italian", "id" to "Indonesian",
    "tr" to "Turkish", "vi" to "Vietnamese", "th" to "Thai", "nl" to "Dutch"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageMenu(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(tag); expanded = false })
            }
        }
    }
}

@Composable
private fun TranslatorBody() {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("auto") }
    var target by remember { mutableStateOf("hi") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    NoteCard("Translation runs on your phone (Google ML Kit). The FIRST time you use a language pair, its pack (~30 MB per language) downloads once — after that it works fully offline, even in flight mode. Your text is never uploaded; only the language pack is downloaded.")
    LanguageMenu("From", listOf("auto" to "Auto-detect") + TRANSLATE_LANGS, source) { source = it }
    LanguageMenu("To", TRANSLATE_LANGS, target) { target = it }
    OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Text to translate") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp))
    ProcessButton(input.isNotBlank(), busy, {
        busy = true; status = "Working… (first use of a pair downloads its language pack)"; output = null
        scope.launch {
            var client: com.google.mlkit.nl.translate.Translator? = null
            try {
                val srcTag = if (source == "auto") {
                    val detected = withContext(Dispatchers.IO) { AiTools.identifyLanguage(input) }
                    if (detected == "und") throw IllegalStateException("Could not detect the language of this text. Please choose the 'From' language manually.")
                    detected
                } else source
                if (srcTag == target) throw IllegalStateException("Source and target languages are the same — pick a different target language.")
                client = AiTools.translator(srcTag, target)
                val result = withContext(Dispatchers.IO) { AiTools.translate(client, input) }
                output = result
                status = if (source == "auto") "Detected language: ${TRANSLATE_LANGS.firstOrNull { it.first == srcTag }?.second ?: srcTag}" else null
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) {
                output = null
                status = "Failed: ${e.javaClass.simpleName}: ${e.message ?: "Translation failed. If this is the first use, check your internet connection for the one-time pack download."}"
            } finally {
                client?.close()
                busy = false
            }
        }
    }, "Translate")
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    output?.let { text ->
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text("Translation") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 260.dp))
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy translation") }
    }
}

private val VOICE_LANGS = listOf(
    "hi-IN" to "Hindi (हिंदी)", "en-IN" to "English (India)", "en-US" to "English (US)",
    "bn-IN" to "Bengali", "ta-IN" to "Tamil", "te-IN" to "Telugu", "mr-IN" to "Marathi",
    "gu-IN" to "Gujarati", "pa-IN" to "Punjabi", "kn-IN" to "Kannada", "ml-IN" to "Malayalam", "ur-IN" to "Urdu"
)

@Composable
private fun VoiceBody() {
    val context = LocalContext.current
    var lang by remember { mutableStateOf("hi-IN") }
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun startListening() {
        if (recognizer == null) { note = "Speech recognition is not available on this device."; return }
        note = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye… / Speak now…")
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                note = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Samajh nahi aaya — paas se, saaf dobara bolkar dekho."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch suna nahi — mic ke paas bolkar dobara try karo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye — neeche button se allow karo."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, 13 -> "Ye language is phone par offline support nahi karti. Settings me us language ka offline pack download karna pad sakta hai."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy hai — ek second ruk kar dobara try karo."
                    else -> "Speech error (code $error). Agar baar-baar aaye to phone ki default voice-input language settings check karo."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!said.isNullOrBlank()) text = if (text.isBlank()) said else "$text $said"
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val said = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!said.isNullOrBlank()) note = "… $said"
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    var hasMicPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) startListening() else note = "Mic permission ke bina voice typing nahi ho sakti."
    }

    NoteCard("Bolkar text likho — Android ki on-device speech recognition. Offline tabhi kaam karega jab phone me us language ka offline voice pack hoga (Samsung/Google voice settings se download hota hai); warna ye phone ke default voice service par nirbhar hai. Audio app ke bahar nahi bheja jata hamari taraf se.")
    LanguageMenu("Language", VOICE_LANGS, lang) { lang = it }
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Your text") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 260.dp))
    Button(
        onClick = { if (hasMicPermission) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        enabled = !listening, modifier = Modifier.fillMaxWidth()
    ) { Text(if (listening) "🎙️ Sun raha hoon… bolo" else "🎙️ Bolkar likho (Start)") }
    if (listening) OutlinedButton(onClick = { recognizer?.stopListening() }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, enabled = text.isNotBlank()) { Text("Copy text") }
        OutlinedButton(onClick = { text = "" }, enabled = text.isNotBlank()) { Text("Clear") }
    }
}
