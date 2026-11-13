package app.qidi

import android.content.Context
import java.io.File

/**
 * Where Qidi keeps its diagnostic logs: app-specific external storage
 * (`/sdcard/Android/data/app.qidi/files/`). `adb pull` can read it on release builds, where
 * `run-as` is not available. Other apps cannot read it, and Android deletes it on uninstall.
 */
object QidiLogFiles {
    const val EVENT_LOG = "qidi-events.log"
    const val FIELD_LOG = "qidi-field-events.log"

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Moves logs written by v0.3.2 and earlier out of internal storage, once. */
    fun migrate(context: Context) {
        val target = dir(context)
        if (target == context.filesDir) return
        listOf(EVENT_LOG, FIELD_LOG).forEach { name ->
            val old = File(context.filesDir, name)
            if (!old.exists()) return@forEach
            val new = File(target, name)
            runCatching {
                if (!new.exists()) old.copyTo(new)
                old.delete()
            }
        }
    }

    private fun dir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir
}
