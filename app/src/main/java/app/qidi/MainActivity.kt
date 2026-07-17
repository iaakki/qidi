package app.qidi

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

class MainActivity : Activity() {
    private lateinit var output: TextView
    private var newProcessMethod: Method? = null

    private val protectedPackages = listOf(
        "app.qidi",
        "dev.shadoe.delta",
        "io.homeassistant.companion.android",
        "com.assaabloy.yale",
        "com.life360.android.safetymapd"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        renderStatus("Qidi ready. Connect Shizuku, then check or apply protections.")
    }

    private fun buildLayout(): ScrollView {
        output = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
        }

        val statusButton = Button(this).apply {
            text = "Check Shizuku"
            setOnClickListener { checkShizuku() }
        }

        val protectButton = Button(this).apply {
            text = "Apply Protections"
            setOnClickListener { applyProtections() }
        }

        val traceButton = Button(this).apply {
            text = "Check Delta Status"
            setOnClickListener { tracePackage("dev.shadoe.delta") }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(context).apply {
                text = "Qidi"
                textSize = 28f
            })
            addView(statusButton)
            addView(protectButton)
            addView(traceButton)
            addView(output)
        }

        return ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun checkShizuku() {
        val message = when {
            !Shizuku.pingBinder() -> "Shizuku binder is not available."
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                "Shizuku permission granted. Shell runner is ready."
            }
            else -> {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                "Requested Shizuku permission."
            }
        }
        renderStatus(message)
    }

    private fun applyProtections() {
        if (!ensureShizukuPermission()) return

        val log = StringBuilder()
        protectedPackages.forEach { packageName ->
            log.appendLine("== $packageName ==")
            protectionCommands(packageName).forEach { command ->
                log.appendLine("$ $command")
                log.appendLine(runShell(command).trim())
            }
            log.appendLine()
        }
        renderStatus(log.toString())
    }

    private fun tracePackage(packageName: String) {
        if (!ensureShizukuPermission()) return

        val commands = listOf(
            "pidof $packageName || true",
            "dumpsys package $packageName | grep -E 'stopped=|enabled=|suspended='",
            "dumpsys activity exit-info $packageName | sed -n '1,90p'",
            "logcat -b events -d -v threadtime | grep -E 'am_kill|am_proc_died|am_uid_stopped|am_force_stop' | tail -n 80"
        )

        val log = StringBuilder()
        commands.forEach { command ->
            log.appendLine("$ $command")
            log.appendLine(runShell(command).trim())
            log.appendLine()
        }
        renderStatus(log.toString())
    }

    private fun protectionCommands(packageName: String): List<String> = listOf(
        "cmd deviceidle whitelist +$packageName",
        "am set-standby-bucket $packageName active",
        "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
        "cmd appops set $packageName RUN_IN_BACKGROUND allow",
        "cmd appops set $packageName START_FOREGROUND allow",
        "cmd appops set $packageName SCHEDULE_EXACT_ALARM allow"
    )

    private fun ensureShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            renderStatus("Shizuku binder is not available.")
            return false
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
        renderStatus("Requested Shizuku permission. Try again after approving it.")
        return false
    }

    private fun runShell(command: String): String {
        return try {
            val process = createShizukuProcess(arrayOf("sh", "-c", command))
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

    private fun createShizukuProcess(command: Array<String>): Process {
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

    private fun renderStatus(message: String) {
        output.text = message
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 1001
    }
}