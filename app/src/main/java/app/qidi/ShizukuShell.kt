package app.qidi

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.Writer
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs shell commands as uid `shell` through one long-lived `sh` started via Shizuku.
 *
 * Why one session instead of a process per command: the Shizuku client caches every
 * `newProcess` result in a static set that is only cleared when the server dies, so each call
 * pins an object inside `shizuku_server`. At Qidi's polling rate that killed the server with
 * OutOfMemoryError about every 8 hours. A session costs one such object per Qidi/Shizuku
 * lifetime. (A Shizuku UserService would be the textbook fix, but its starter crashes on this
 * Android 16 device before reaching app code.)
 *
 * Each command still runs in its own `sh -c` with stdin from /dev/null, so `exit` and
 * background jobs behave as they did with a process per command. Output is framed by a
 * per-call random marker; see [Session.exec].
 */
object ShizukuShell {
    private const val COMMAND_TIMEOUT_SECONDS = 60L
    private const val STDERR_FILE = "/data/local/tmp/qidi-shell.err"

    private val lock = Any()
    private var session: Session? = null
    private var newProcessMethod: Method? = null
    private val timeouts = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "qidi-shell-timeout").apply { isDaemon = true }
    }

    /** Blocks until the command finishes; call it off the main thread. Calls are serialised. */
    fun run(command: String): ShellResult = synchronized(lock) {
        val current = session?.takeIf { it.isAlive() } ?: openSession()
            ?: return ShellResult(-1, "", "qidi: Shizuku shell is not available")
        session = current
        val result = current.exec(command)
        if (result == null) {
            current.close()
            session = null
            return ShellResult(-1, "", "qidi: shell session failed or timed out")
        }
        result
    }

    fun quote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }

    private fun openSession(): Session? {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val process = createProcess(arrayOf("sh"))
            Session(process).also {
                // Errors of the session shell itself are never read, so they must not fill a pipe.
                it.send("exec 2>/dev/null\n")
            }
        } catch (error: Throwable) {
            null
        }
    }

    private class Session(private val process: Process) {
        private val writer: Writer = process.outputStream.bufferedWriter()
        private val reader: BufferedReader = process.inputStream.bufferedReader()

        fun send(text: String) {
            writer.write(text)
            writer.flush()
        }

        /**
         * Writes `<stdout>\n<marker> <exit>\n<stderr>\n<marker>\n`. Joining the lines before
         * each marker with "\n" gives back the exact output, because of the "\n" printed
         * before the marker. Returns null if the session broke or the command timed out.
         */
        fun exec(command: String): ShellResult? {
            val marker = "__QIDI_${UUID.randomUUID().toString().replace("-", "")}__"
            val timeout = timeouts.schedule({ close() }, COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return try {
                send(
                    "sh -c ${quote(command)} </dev/null 2>$STDERR_FILE\n" +
                        "printf '\\n%s %s\\n' $marker \$?\n" +
                        "cat $STDERR_FILE 2>/dev/null\n" +
                        "printf '\\n%s\\n' $marker\n"
                )
                val stdout = mutableListOf<String>()
                var exitCode: Int? = null
                while (exitCode == null) {
                    val line = reader.readLine() ?: return null
                    if (line.startsWith("$marker ")) {
                        exitCode = line.substringAfter(' ').trim().toIntOrNull() ?: -1
                    } else {
                        stdout.add(line)
                    }
                }
                val stderr = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: return null
                    if (line == marker) break
                    stderr.add(line)
                }
                ShellResult(exitCode, stdout.joinToString("\n"), stderr.joinToString("\n"))
            } catch (error: Throwable) {
                null
            } finally {
                timeout.cancel(false)
            }
        }

        fun isAlive(): Boolean = try {
            process.exitValue()
            false
        } catch (stillRunning: IllegalThreadStateException) {
            true
        } catch (error: Throwable) {
            false
        }

        fun close() {
            runCatching { writer.close() }
            runCatching { process.destroy() }
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

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    fun format(): String = buildString {
        appendLine("exit=$exitCode")
        if (stdout.isNotBlank()) appendLine(stdout.trim())
        if (stderr.isNotBlank()) appendLine(stderr.trim())
    }
}
