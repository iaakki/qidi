package app.qidi

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QidiFieldLog {
    private const val FILE_NAME = "qidi-field-events.log"
    private const val MAX_LINES = 2_000
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun append(context: Context, event: String) {
        if (!QidiSettings.isLoggingEnabled(context)) return
        val file = logFile(context)
        val line = "${timestampFormat.format(Date())} | $event"
        val lines = if (file.exists()) file.readLines() else emptyList()
        file.writeText((lines + line).takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    fun sizeBytes(context: Context): Long = logFile(context).let { if (it.exists()) it.length() else 0L }

    fun recent(context: Context, count: Int = 40): List<String> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(count).asReversed()
    }

    fun clear(context: Context) {
        logFile(context).delete()
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)
}