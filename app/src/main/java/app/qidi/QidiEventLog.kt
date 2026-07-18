package app.qidi

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QidiEventLog {
    private const val FILE_NAME = "qidi-events.log"
    private const val MAX_LINES = 250
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun append(context: Context, event: String) {
        val file = logFile(context)
        val line = "${timestampFormat.format(Date())} | $event"
        val lines = if (file.exists()) file.readLines() else emptyList()
        file.writeText((lines + line).takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    fun recent(context: Context, count: Int = 20): List<String> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(count).asReversed()
    }

    fun clear(context: Context) {
        logFile(context).delete()
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)
}