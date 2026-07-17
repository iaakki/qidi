package app.qidi

class QidiShellService : IQidiShellService.Stub() {
    override fun run(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            buildString {
                appendLine("exit=$exitCode")
                if (stdout.isNotBlank()) appendLine(stdout.trim())
                if (stderr.isNotBlank()) appendLine(stderr.trim())
            }
        } catch (error: Throwable) {
            buildString {
                appendLine("exit=-1")
                appendLine(error.stackTraceToString())
            }
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
