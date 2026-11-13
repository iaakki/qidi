package app.qidi

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs shell commands as uid `shell` via the [QidiShellService] Shizuku UserService.
 *
 * Do not go back to `Shizuku.newProcess` as the main path. Every call leaks an object inside
 * `shizuku_server` for as long as Qidi lives, and at Qidi's polling rate that killed the server
 * with OutOfMemoryError about every 8 hours. It remains only as a fallback for when the user
 * service cannot be bound.
 */
object ShizukuShell {
    private const val BIND_TIMEOUT_MS = 5_000L

    private val bindLock = Any()
    @Volatile private var service: IQidiShell? = null
    @Volatile private var bindLatch: CountDownLatch? = null
    private var newProcessMethod: Method? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, QidiShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                runCatching { binder.linkToDeath({ service = null }, 0) }
                service = IQidiShell.Stub.asInterface(binder)
            }
            bindLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** Blocks while the user service binds, so call it off the main thread. */
    fun run(command: String): ShellResult {
        val shell = service ?: connect()
        if (shell != null) {
            try {
                val result = shell.exec(command)
                return ShellResult(result[0].toIntOrNull() ?: -1, result[1], result[2])
            } catch (error: Throwable) {
                service = null
            }
        }
        return runViaNewProcess(command)
    }

    fun quote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }

    private fun connect(): IQidiShell? {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        // Connection callbacks arrive on the main thread, so the main thread cannot wait for them.
        val onMainThread = Looper.myLooper() == Looper.getMainLooper()
        synchronized(bindLock) {
            service?.let { return it }
            val latch = CountDownLatch(1)
            bindLatch = latch
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (error: Throwable) {
                return null
            }
            if (!onMainThread) latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return service
        }
    }

    private fun runViaNewProcess(command: String): ShellResult {
        val process = try {
            createProcess(arrayOf("sh", "-c", command))
        } catch (error: Throwable) {
            return ShellResult(-1, "", error.stackTraceToString())
        }
        return try {
            process.outputStream.close()
            var stderr = ""
            val errReader = thread(name = "qidi-shizuku-err") {
                stderr = runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            errReader.join()
            ShellResult(exitCode, stdout, stderr)
        } catch (error: Throwable) {
            ShellResult(-1, "", error.stackTraceToString())
        } finally {
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
