package com.pushpendratech.toolbox.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Captures the most recent uncaught crash (full stack trace) to a private file
 * so the app can show it on the next launch. This exists so a device-only crash
 * can be diagnosed from the exact error instead of guesswork: the user can copy
 * or screenshot the report and share it. The previous default handler is always
 * chained to, so system crash behaviour is unchanged.
 *
 * Two safeguards keep reports truthful:
 *  - Every report is stamped with the app version that produced it.
 *  - On the first launch of a NEW build, any report left over from an older
 *    build is deleted — a report can never be mistaken for a fresh crash.
 */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"
    private const val PREFS = "crashlog"
    private const val KEY_SEEN_VERSION = "seen_version_code"

    private fun versionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (_: Throwable) { -1L }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) { "?" }

    fun install(context: Context) {
        val appCtx = context.applicationContext
        // Drop stale reports from older builds before anything else can crash.
        try {
            val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = versionCode(appCtx)
            if (prefs.getLong(KEY_SEEN_VERSION, -1L) != current) {
                File(appCtx.filesDir, FILE_NAME).delete()
                prefs.edit().putLong(KEY_SEEN_VERSION, current).apply()
            }
        } catch (_: Throwable) { }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val header = "App version: ${versionName(appCtx)} (${versionCode(appCtx)})\nThread: ${thread.name}\n"
                File(appCtx.filesDir, FILE_NAME).writeText(header + sw.toString())
            } catch (_: Throwable) { /* never let the reporter itself crash the handler */ }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = try {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists()) f.readText().take(6000) else null
    } catch (_: Throwable) { null }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Throwable) { }
    }
}
