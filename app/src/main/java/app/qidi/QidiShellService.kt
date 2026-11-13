package app.qidi

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku UserService: Shizuku starts this class in its own process running as uid `shell`, and
 * Qidi sends it commands over a single binder.
 *
 * It replaces `Shizuku.newProcess`. The client library caches every remote process forever,
 * so `shizuku_server` leaked one holder per command and died of OutOfMemoryError about every
 * 8 hours. Here each command is a plain local child process that is fully cleaned up.
 */
class QidiShellService : IQidiShell.Stub() {

    override fun exec(command: String): Array<String> {
        val process = try {
            ProcessBuilder("sh", "-c", command).start()
        } catch (error: Throwable) {
            return arrayOf("-1", "", error.stackTraceToString())
        }
        try {
            process.outputStream.close()
            var stdout = ""
            var stderr = ""
            val outReader = thread(name = "qidi-shell-out") { stdout = process.inputStream.readAll() }
            val errReader = thread(name = "qidi-shell-err") { stderr = process.errorStream.readAll() }
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            outReader.join(READER_JOIN_MS)
            errReader.join(READER_JOIN_MS)
            val exitCode = if (finished) process.exitValue() else -1
            if (!finished) stderr += "\nqidi: command timed out after ${COMMAND_TIMEOUT_SECONDS}s"
            return arrayOf(exitCode.toString(), stdout, stderr)
        } finally {
            process.destroy()
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun InputStream.readAll(): String =
        runCatching { bufferedReader().use { it.readText() } }.getOrDefault("")

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 60L
        const val READER_JOIN_MS = 2_000L
    }
}
