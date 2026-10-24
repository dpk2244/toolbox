package com.pushpendratech.toolbox.core

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    fun outputDir(context: Context): File = File(context.cacheDir, "outputs").apply { mkdirs() }

    /** Unique output file — never overwrites an input or an earlier output. */
    fun newOutput(context: Context, baseName: String, ext: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "output" }
        var f = File(outputDir(context), "${safe}_$stamp.$ext"); var i = 1
        while (f.exists()) { f = File(outputDir(context), "${safe}_${stamp}_$i.$ext"); i++ }
        return f
    }

    /** Copies a content:// stream into app cache so Android media/PDF APIs (which need real files or FDs repeatedly) can work. Caller owns deletion via cleanupTemp. */
    fun copyUriToCache(context: Context, uri: Uri, prefix: String = "in"): File {
        val name = displayName(context, uri) ?: "input"
        val ext = name.substringAfterLast('.', "").take(10).ifBlank { "bin" }
        val out = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            ?: throw IllegalStateException("Could not open the selected file. It may have been moved or permission was revoked.")
        if (out.length() == 0L) { out.delete(); throw IllegalStateException("The selected file is empty or unreadable.") }
        return out
    }

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment
        } catch (e: Exception) { uri.lastPathSegment } finally { cursor?.close() }
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } catch (e: Exception) { -1L } finally { cursor?.close() }
    }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"
        "gif" -> "image/gif"; "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "wav" -> "audio/x-wav"
        "aac" -> "audio/aac"; "txt" -> "text/plain"; else -> "application/octet-stream"
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        val units = arrayOf("B", "KB", "MB", "GB"); var v = bytes.toDouble(); var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, if (i == 0) "%.0f %s" else "%.2f %s", v, units[i])
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(file.extension); putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    fun openFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(file.extension)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
    }

    /** Writes [file] to a user-picked SAF destination (from ActivityResultContracts.CreateDocument). */
    fun copyToUri(context: Context, file: File, dest: Uri) {
        context.contentResolver.openOutputStream(dest)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IllegalStateException("Could not open the destination you picked.")
    }

    fun cleanupTemp(vararg files: File?) { files.forEach { try { if (it != null && it.exists() && it.path.contains("cache")) it.delete() } catch (_: Exception) {} } }
}
