package com.andene.fern

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
            } catch (_: Throwable) {
                // Never let reporting itself mask the original crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The report from the last crash, or null if the app didn't crash since it was last read. */
    fun pendingReport(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun clearPendingReport(context: Context) {
        crashFile(context).delete()
    }

    private fun crashFile(context: Context) = File(context.filesDir, CRASH_FILE_NAME)
}
