package app.qidi

import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuShell {
    private var newProcessMethod: Method? = null

    fun run(command: String): ShellResult {
        return try {
            val process = createProcess(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(process.waitFor(), stdout, stderr)
        } catch (error: Throwable) {
            ShellResult(-1, "", error.stackTraceToString())
        }
    }

    fun quote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
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