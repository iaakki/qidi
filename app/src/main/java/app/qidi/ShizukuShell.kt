package app.qidi

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Runs shell commands as uid `shell` through one long-lived [ShellSession] started via Shizuku.
 *
 * Why one session instead of a process per command: the Shizuku client caches every
 * `newProcess` result in a static set that is only cleared when the server dies, so each call
 * pins an object inside `shizuku_server`. At Qidi's polling rate that killed the server with
 * OutOfMemoryError about every 8 hours. A session costs one such object per Qidi/Shizuku
 * lifetime. (A Shizuku UserService would be the textbook fix, but its starter crashes on this
 * Android 16 device before reaching app code.)
 */
object ShizukuShell {
    private const val COMMAND_TIMEOUT_SECONDS = 60L
    private const val STDERR_FILE = "/data/local/tmp/qidi-shell.err"

    private val lock = Any()
    private var session: ShellSession? = null
    private var newProcessMethod: Method? = null
    private val timeouts = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "qidi-shell-timeout").apply { isDaemon = true }
    }

    /**
     * Blocks until the command finishes; call it off the main thread. Calls are serialised.
     * The session is reused until a command fails on it; then it is replaced and the command
     * retried once (e.g. after shizuku_server restarted).
     */
    fun run(command: String): ShellResult = synchronized(lock) {
        session?.let { existing ->
            existing.exec(command)?.let { return it }
            existing.close()
            session = null
        }
        val fresh = openSession() ?: return ShellResult(-1, "", "qidi: Shizuku shell is not available")
        session = fresh
        fresh.exec(command) ?: run {
            fresh.close()
            session = null
            ShellResult(-1, "", "qidi: shell session failed or timed out")
        }
    }

    fun quote(value: String): String = shellQuote(value)

    private fun openSession(): ShellSession? {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            ShellSession(createProcess(arrayOf("sh")), STDERR_FILE, COMMAND_TIMEOUT_SECONDS, timeouts)
        } catch (error: Throwable) {
            null
        }
    }

    private fun createProcess(command: Array<String>): Process {
        val method = newProcessMethod ?: Shizuku::class.java
            .getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            .also {
                it.isAccessible = true
                newProcessMethod = it
            }

        return method.invoke(null, command, null, null) as Process
    }
}
