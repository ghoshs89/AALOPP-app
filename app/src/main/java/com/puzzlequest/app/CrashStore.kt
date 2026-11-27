package com.puzzlequest.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Private, on-device crash recorder. Saves the most recent uncaught exception stack trace to
 * app-internal storage so parents can view or copy it from Grown-ups without any network telemetry.
 */
object CrashStore {
    private const val FILENAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val report = buildString {
                    appendLine("Time: ${Instant.now()}")
                    appendLine("App: Puzzle Quest ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString())
                }
                File(appContext.filesDir, FILENAME).writeText(report.take(16_384))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.applicationContext.filesDir, FILENAME)
        if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            File(context.applicationContext.filesDir, FILENAME).delete()
        }
    }
}
