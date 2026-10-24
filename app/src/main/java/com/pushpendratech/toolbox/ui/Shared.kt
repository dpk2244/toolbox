package com.pushpendratech.toolbox.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.FileUtils
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PickedFile(val uri: Uri, val name: String, val size: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = onToggleFav) { Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Favorite") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Single-file picker button + details card. Returns picked file state. */
@Composable
fun rememberFilePicker(mimeTypes: Array<String>): Pair<PickedFile?, () -> Unit> {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = PickedFile(uri, FileUtils.displayName(context, uri) ?: "file", FileUtils.sizeOf(context, uri))
    }
    return picked to { launcher.launch(mimeTypes) }
}

@Composable
fun rememberMultiFilePicker(mimeTypes: Array<String>): Pair<List<PickedFile>, () -> Unit> {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris.map { PickedFile(it, FileUtils.displayName(context, it) ?: "file", FileUtils.sizeOf(context, it)) }
    }
    return picked to { launcher.launch(mimeTypes) }
}

@Composable
fun PickedCard(picked: PickedFile?) {
    if (picked == null) Text("No file selected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(picked.name, fontWeight = FontWeight.Medium)
            Text("Size: ${FileUtils.formatSize(picked.size)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

sealed interface RunState {
    data object Idle : RunState
    data object Running : RunState
    data class Done(val outputs: List<File>, val message: String) : RunState
    data class Failed(val message: String) : RunState
}

/** Result panel: honest success/failure, output size, Save (user picks destination), Share, Open, Process-again reset. */
@Composable
fun ResultPanel(state: RunState, onReset: () -> Unit) {
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { dest ->
        val src = pendingSave
        if (dest != null && src != null) {
            try { FileUtils.copyToUri(context, src, dest) } catch (e: Exception) { /* surfaced by file remaining in cache; share still available */ }
        }
        pendingSave = null
    }
    when (state) {
        RunState.Idle -> {}
        RunState.Running -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(20.dp)); Text("Processing… please keep this screen open.") }
        is RunState.Failed -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("It didn't work", fontWeight = FontWeight.Bold)
                Text(state.message)
                OutlinedButton(onClick = onReset) { Text("Try again") }
            }
        }
        is RunState.Done -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✅ Done", fontWeight = FontWeight.Bold)
                Text(state.message)
                state.outputs.forEach { f ->
                    Text("${f.name} — ${FileUtils.formatSize(f.length())}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingSave = f; saveLauncher.launch(f.name) }) { Text("Save") }
                        OutlinedButton(onClick = { FileUtils.shareFile(context, f) }) { Text("Share") }
                        OutlinedButton(onClick = { FileUtils.openFile(context, f) }) { Text("Open") }
                    }
                }
                Button(onClick = onReset) { Text("Process another file") }
            }
        }
    }
}

/** Runs [block] on IO, converting exceptions into honest failure messages (never a fake success). */
fun CoroutineScope.runJob(setState: (RunState) -> Unit, block: suspend () -> Pair<List<File>, String>) {
    setState(RunState.Running)
    launch {
        try {
            val (files, msg) = withContext(Dispatchers.IO) { block() }
            val existing = files.filter { it.exists() && it.length() > 0 }
            if (existing.size != files.size) setState(RunState.Failed("Processing finished but an output file was empty or missing. Nothing was saved."))
            else setState(RunState.Done(existing, msg))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            setState(RunState.Failed("Out of memory — this file is too large for this device. Try a smaller file or lower quality."))
        } catch (e: Throwable) {
            // Catch Throwable (not just Exception): an Error such as
            // NoClassDefFoundError inside a tool must surface as a readable
            // on-screen failure with the error name, not kill the app.
            setState(RunState.Failed("${e.javaClass.simpleName}: ${e.message ?: "Something went wrong while processing this file."}"))
        }
    }
}

@Composable
fun ProcessButton(enabled: Boolean, running: Boolean, onClick: () -> Unit, label: String = "Process") {
    Button(onClick = onClick, enabled = enabled && !running, modifier = Modifier.fillMaxWidth()) {
        if (running) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text(label)
    }
}

@Composable
fun NoteCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

fun formatMs(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}
fun parseTimeToMs(text: String): Long? {
    val t = text.trim(); if (t.isEmpty()) return null
    return try {
        if (t.contains(":")) { val parts = t.split(":").map { it.toDouble() }; if (parts.size == 2) ((parts[0] * 60 + parts[1]) * 1000).toLong() else null }
        else (t.toDouble() * 1000).toLong()
    } catch (e: Exception) { null }
}
