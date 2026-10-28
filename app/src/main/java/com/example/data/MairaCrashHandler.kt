package com.example.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object MairaCrashHandler {
    private const val CRASH_FILE_NAME = "maira_crash.txt"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(context, throwable)
            } catch (e: Exception) {
                // Ignore errors during crash logging
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawTrace = sw.toString()
        val sanitizedTrace = MairaLogger.redact(rawTrace)

        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(sanitizedTrace)

        MairaLogger.log(
            MairaLogger.Category.UI,
            MairaLogger.Level.ERROR,
            "[CRASH] অ্যাপ অস্বাভাবিকভাবে বন্ধ হয়েছিল: ${throwable.localizedMessage ?: "Unknown"}"
        )
    }

    fun hasPendingCrashLog(context: Context): Boolean {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun getAndClearCrashLog(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            file.delete()
            content
        } catch (e: Exception) {
            null
        }
    }
}
