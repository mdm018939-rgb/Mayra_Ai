package com.example.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object MairaLogger {
    enum class Level { INFO, WARN, ERROR }
    enum class Category {
        API_KEY, CONNECTION, AUDIO, TOOL, PERMISSION, ACTION, TRIGGER, CHAT, BUILDER, UI
    }

    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val category: Category,
        val message: String
    )

    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_FILE_BYTES = 500 * 1024L
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logFile: File? = null
    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pendingBuffer = mutableListOf<LogEntry>()

    fun init(context: Context) {
        logFile = File(context.filesDir, "maira_logs.txt")
        loadLogsFromFile()
        startPeriodicFlush()
    }

    fun redact(text: String): String {
        return text
            .replace(Regex("AIza[A-Za-z0-9_\\-]{10,}"), "[KEY]")
            .replace(Regex("gsk_[A-Za-z0-9_\\-]{10,}"), "[KEY]")
    }

    fun log(category: Category, level: Level, message: String) {
        val sanitizedMsg = redact(message)
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, category, sanitizedMsg)

        synchronized(this) {
            val updated = _logsFlow.value.toMutableList()
            updated.add(0, entry) // Newest first
            if (updated.size > MAX_MEMORY_LOGS) {
                updated.removeAt(updated.size - 1)
            }
            _logsFlow.value = updated
            pendingBuffer.add(entry)
        }
    }

    private fun startPeriodicFlush() {
        loggerScope.launch {
            while (isActive) {
                delay(2000)
                flushLogsToFile()
            }
        }
    }

    private fun flushLogsToFile() {
        val file = logFile ?: return
        val entriesToWrite: List<LogEntry>
        synchronized(this) {
            if (pendingBuffer.isEmpty()) return
            entriesToWrite = pendingBuffer.toList()
            pendingBuffer.clear()
        }

        try {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                file.writeText("") // Clear file if exceeds 500KB
            }
            file.appendText(buildString {
                entriesToWrite.reversed().forEach { entry ->
                    append("${entry.timestamp} [${entry.level}] [${entry.category}]: ${entry.message}\n")
                }
            })
        } catch (e: Exception) {
            // Avoid infinite log loop on file write error
        }
    }

    private fun loadLogsFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val lines = file.readLines().takeLast(MAX_MEMORY_LOGS)
            val parsed = lines.mapNotNull { line ->
                try {
                    val parts = line.split(" ", limit = 4)
                    if (parts.size >= 4) {
                        val time = "${parts[0]} ${parts[1]}"
                        val levelStr = parts[2].removeSurrounding("[", "]")
                        val catStr = parts[3].substringBefore("]:").removeSurrounding("[", "]")
                        val msg = parts[3].substringAfter("]: ")
                        LogEntry(
                            timestamp = time,
                            level = Level.valueOf(levelStr),
                            category = Category.valueOf(catStr),
                            message = msg
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }.reversed()
            _logsFlow.value = parsed
        } catch (e: Exception) {
            // Ignore corrupted logs
        }
    }

    fun clearLogs() {
        synchronized(this) {
            _logsFlow.value = emptyList()
            pendingBuffer.clear()
        }
        logFile?.let {
            try {
                if (it.exists()) it.writeText("")
            } catch (e: Exception) {}
        }
        log(Category.UI, Level.INFO, "সকল লগ মুছে ফেলা হয়েছে")
    }
}
