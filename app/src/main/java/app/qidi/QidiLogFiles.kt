package app.qidi

import android.content.Context
import java.io.File

/**
 * Where Qidi keeps its diagnostic logs and the sentinel's marker files: app-specific external
 * storage (`/sdcard/Android/data/app.qidi/files/`). `adb pull` can read it on release builds, where
 * `run-as` is not available. Other apps cannot read it, and Android deletes it on uninstall.
 */
object QidiLogFiles {
    const val EVENT_LOG = "qidi-events.log"
    const val FIELD_LOG = "qidi-field-events.log"

    /** Present while the watchdog runs without Shizuku's binder; read by the shell sentinel. */
    const val BINDER_MISSING_MARKER = "shizuku-binder-missing"

    /**
     * Present when "Only launch apps while the screen is off" is turned off; the sentinel then
     * may launch an app visibly while the screen is on instead of deferring it.
     */
    const val VISIBLE_LAUNCH_MARKER = "allow-visible-launch"

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

    fun setBinderMissing(context: Context, missing: Boolean) {
        val marker = file(context, BINDER_MISSING_MARKER)
        runCatching { if (missing) marker.writeText("") else marker.delete() }
    }

    /** Makes the sentinel's flag file match the setting; cheap when nothing changed. */
    fun syncVisibleLaunch(context: Context) {
        val allowed = !QidiSettings.isScreenOffOnly(context)
        val marker = file(context, VISIBLE_LAUNCH_MARKER)
        if (marker.exists() == allowed) return
        runCatching { if (allowed) marker.writeText("") else marker.delete() }
    }

    private fun dir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir
}
