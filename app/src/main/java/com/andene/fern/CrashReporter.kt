package com.andene.fern

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * On-device crash reporting with no third-party service: an uncaught exception is written to
 * a local file before the app dies, and on the next launch the user is offered to share it
 * (see MainActivity). Nothing leaves the device unless the user chooses to share it.
 */
object CrashReporter {
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val PREFS = "crash_reporter"
    private const val KEY_LAST_EXIT_CHECK = "last_exit_check"

    /** Installs the handler; the previous handler still runs afterwards, so the system's own crash handling is unchanged. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
                val report = buildString {
                    appendLine("Fern ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(trace)
                }
                crashFile(appContext).writeText(report)
            } catch (ignored: Throwable) {
                // Never let reporting itself mask the original crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Turns deaths the in-process handler can't see - freezes the system killed as "not
     * responding" (ANR) and native crashes - into a pending report, using the exit reasons
     * Android records (API 30+). Only exits since the previous check count, so each is offered
     * once; an existing Java crash report takes precedence.
     */
    fun recordUnseenExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_EXIT_CHECK, 0L)
        prefs.edit().putLong(KEY_LAST_EXIT_CHECK, System.currentTimeMillis()).apply()
        if (lastCheck == 0L || crashFile(context).exists()) return // first launch: older exits predate reporting
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exit = manager.getHistoricalProcessExitReasons(null, 0, 5).firstOrNull {
            it.timestamp > lastCheck && (it.reason == ApplicationExitInfo.REASON_ANR || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
        } ?: return
        val report = buildString {
            appendLine("Fern ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(if (exit.reason == ApplicationExitInfo.REASON_ANR) "App not responding (ANR)" else "Native crash")
            appendLine(exit.description.orEmpty())
            appendLine()
            // The ANR trace holds every thread's stack; capped so a share stays a reasonable size.
            exit.traceInputStream?.use { append(it.bufferedReader().readText().take(MAX_TRACE_CHARS)) }
        }
        crashFile(context).writeText(report)
    }

    private const val MAX_TRACE_CHARS = 64_000

    /** The report from the last crash, or null if the app didn't crash since it was last read. */
    fun pendingReport(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun clearPendingReport(context: Context) {
        crashFile(context).delete()
    }

    private fun crashFile(context: Context) = File(context.filesDir, CRASH_FILE_NAME)
}
