package app.qidi

import java.io.BufferedReader
import java.io.Writer
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * One long-lived `sh` that runs commands sent over its stdin. Pure JVM (no Android or Shizuku
 * types), so it is unit-tested against a local shell in `ShellSessionTest`.
 *
 * Each command runs in its own `sh -c` with stdin from /dev/null, so `exit` and background
 * jobs behave as they would in a process of their own. The reply is framed as
 * `<stdout>\n<marker> <exit>\n<stderr>\n<marker>\n` with a per-call random marker. Joining
 * the lines before each marker with "\n" restores the exact text, because of the "\n" printed
 * before each marker.
 *
 * Never probe liveness with `process.exitValue()`: across Shizuku's binder the "still running"
 * exception changes type. A broken session shows up as [exec] returning null.
 */
internal class ShellSession(
    private val process: Process,
    private val stderrFile: String,
    private val timeoutSeconds: Long,
    private val timeouts: ScheduledExecutorService
) {
    private val writer: Writer = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    init {
        // Errors of the session shell itself are never read, so they must not fill a pipe.
        send("exec 2>/dev/null\n")
    }

    /** Returns null if the session broke or the command timed out; the caller must [close] it. */
    fun exec(command: String): ShellResult? {
        val marker = "__QIDI_${UUID.randomUUID().toString().replace("-", "")}__"
        val timeout = timeouts.schedule({ close() }, timeoutSeconds, TimeUnit.SECONDS)
        return try {
            send(
                "sh -c ${shellQuote(command)} </dev/null 2>$stderrFile\n" +
                    "printf '\\n%s %s\\n' $marker \$?\n" +
                    "cat $stderrFile 2>/dev/null\n" +
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

    fun close() {
        runCatching { writer.close() }
        runCatching { process.destroy() }
    }

    private fun send(text: String) {
        writer.write(text)
        writer.flush()
    }
}

internal fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

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
