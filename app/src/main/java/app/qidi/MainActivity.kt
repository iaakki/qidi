package app.qidi

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var output: TextView
    private var shellService: IQidiShellService? = null

    private val protectedPackages = listOf(
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

    override fun onDestroy() {
        super.onDestroy()
        shellService = null
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
            text = "Trace Delta"
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
                bindShellService()
                "Shizuku permission granted. Binding shell service."
            }
            else -> {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                "Requested Shizuku permission."
            }
        }
        renderStatus(message)
    }

    private fun applyProtections() {
        if (!ensureShellService()) return

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
        if (!ensureShellService()) return

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

    private fun ensureShellService(): Boolean {
        if (!ensureShizukuPermission()) return false
        if (shellService != null) return true

        bindShellService()
        renderStatus("Binding Shizuku shell service. Try again in a moment.")
        return false
    }

    private fun bindShellService() {
        val args = Shizuku.UserServiceArgs(ComponentName(this, QidiShellService::class.java))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

        Shizuku.bindUserService(args, shellConnection)
    }

    private val shellConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            shellService = IQidiShellService.Stub.asInterface(service)
            renderStatus("Shizuku shell service connected.")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shellService = null
            renderStatus("Shizuku shell service disconnected.")
        }
    }

    private fun runShell(command: String): String = shellService?.run(command) ?: "exit=-1\nShell service is not connected."

    private fun renderStatus(message: String) {
        output.text = message
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 1001
    }
}