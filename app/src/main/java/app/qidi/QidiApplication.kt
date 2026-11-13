package app.qidi

import android.app.Application

class QidiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        QidiLogFiles.migrate(this)
        recordCrashes()
    }

    /**
     * Qidi crashed on 2026-09-29 and 2026-10-13, and the traces had already expired from
     * dropbox when we looked. Write them to the field log first (same logging toggle), then let
     * the platform handler crash the process as usual.
     */
    private fun recordCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTraceToString().lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(CRASH_TRACE_LINES)
                    .joinToString(" | ")
                QidiFieldLog.append(this, "crash thread=${thread.name} $trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        const val CRASH_TRACE_LINES = 40
    }
}
