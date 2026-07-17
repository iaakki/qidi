package app.qidi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var appListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        requestNotificationPermissionIfNeeded()
        renderAppPicker()
        renderStatus("Qidi ready. Choose apps, apply protections, then start the watchdog.")
    }

    private fun buildLayout(): ScrollView {
        output = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
        }

        val checkShizukuButton = Button(this).apply {
            text = "Check Shizuku"
            setOnClickListener { checkShizuku() }
        }

        val protectButton = Button(this).apply {
            text = "Apply Protections"
            setOnClickListener { applyProtections() }
        }

        val checkStatusButton = Button(this).apply {
            text = "Check Selected Status"
            setOnClickListener { traceSelectedPackages() }
        }

        val startWatchdogButton = Button(this).apply {
            text = "Start Watchdog"
            setOnClickListener { startWatchdog() }
        }

        val stopWatchdogButton = Button(this).apply {
            text = "Stop Watchdog"
            setOnClickListener { stopWatchdog() }
        }

        val watchdogStatusButton = Button(this).apply {
            text = "Show Watchdog Status"
            setOnClickListener { renderStatus(QidiSettings.watchdogStatus(this@MainActivity)) }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh App List"
            setOnClickListener {
                renderAppPicker()
                renderStatus("App list refreshed.")
            }
        }

        appListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(context).apply {
                text = "Qidi"
                textSize = 28f
            })
            addView(checkShizukuButton)
            addView(protectButton)
            addView(checkStatusButton)
            addView(startWatchdogButton)
            addView(stopWatchdogButton)
            addView(watchdogStatusButton)
            addView(refreshButton)
            addView(output)
            addView(TextView(context).apply {
                text = "Protected apps"
                textSize = 20f
                setPadding(0, 24, 0, 8)
            })
            addView(appListContainer)
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

    private fun startWatchdog() {
        if (!ensureShizukuPermission()) return
        QidiSettings.setWatchdogEnabled(this, true)
        val intent = Intent(this, QidiWatchdogService::class.java).setAction(QidiWatchdogService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        renderStatus("Watchdog started. Qidi will restart selected apps up to 3 times per minute per app.")
    }

    private fun stopWatchdog() {
        QidiSettings.setWatchdogEnabled(this, false)
        startService(Intent(this, QidiWatchdogService::class.java).setAction(QidiWatchdogService.ACTION_STOP))
        renderStatus("Watchdog stopped.")
    }

    private fun applyProtections() {
        if (!ensureShizukuPermission()) return

        val packages = selectedProtectedPackages()
        if (packages.isEmpty()) {
            renderStatus("No protected apps selected.")
            return
        }

        val log = StringBuilder()
        packages.forEach { packageName ->
            log.appendLine("== $packageName ==")
            QidiCommands.protectionCommands(packageName).forEach { command ->
                log.appendLine("$ $command")
                log.appendLine(runShell(command).trim())
            }
            log.appendLine()
        }
        renderStatus(log.toString())
    }

    private fun traceSelectedPackages() {
        if (!ensureShizukuPermission()) return

        val packages = selectedProtectedPackages()
        if (packages.isEmpty()) {
            renderStatus("No protected apps selected.")
            return
        }

        val log = StringBuilder()
        packages.forEach { packageName ->
            log.appendLine("== $packageName ==")
            log.appendLine(tracePackageStatus(packageName).trim())
            log.appendLine()
        }
        renderStatus(log.toString())
    }

    private fun tracePackageStatus(packageName: String): String {
        val commands = listOf(
            "pidof ${ShizukuShell.quote(packageName)} || true",
            "cmd deviceidle whitelist | grep -F ${ShizukuShell.quote(packageName)} || true",
            "am get-standby-bucket ${ShizukuShell.quote(packageName)}",
            "cmd appops get ${ShizukuShell.quote(packageName)} | grep -E 'RUN|SCHEDULE|FOREGROUND|POST_NOTIFICATION' || true",
            "dumpsys package ${ShizukuShell.quote(packageName)} | grep -E 'stopped=|enabled=|suspended='",
            "dumpsys activity exit-info ${ShizukuShell.quote(packageName)} | sed -n '1,90p'",
            "logcat -b events -d -v threadtime | grep -E 'am_kill|am_proc_died|am_uid_stopped|am_force_stop' | tail -n 80"
        )

        val log = StringBuilder()
        commands.forEach { command ->
            log.appendLine("$ $command")
            log.appendLine(runShell(command).trim())
            log.appendLine()
        }
        return log.toString()
    }

    private fun renderAppPicker() {
        appListContainer.removeAllViews()

        val selectedPackages = selectedProtectedPackages().toMutableSet()
        val apps = installedPickerApps(selectedPackages)

        apps.forEach { app ->
            appListContainer.addView(CheckBox(this).apply {
                text = "${app.label}\n${app.packageName}"
                textSize = 14f
                isChecked = app.packageName in selectedPackages
                isEnabled = app.packageName != packageName
                setPadding(0, 4, 0, 4)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
                    saveProtectedPackages(selectedPackages)
                }
            })
        }
    }

    private fun installedPickerApps(selectedPackages: Set<String>): List<PickerApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        val installedApps = packageManager.getInstalledApplications(0)
            .filter { applicationInfo ->
                applicationInfo.packageName in selectedPackages ||
                    applicationInfo.packageName in launchablePackages ||
                    applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            .map { applicationInfo ->
                PickerApp(
                    packageManager.getApplicationLabel(applicationInfo).toString(),
                    applicationInfo.packageName
                )
            }

        val fallbackApps = selectedPackages.map { packageName ->
            PickerApp(labelForPackage(packageName), packageName)
        }

        return (installedApps + fallbackApps)
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<PickerApp> { it.packageName in selectedPackages }.thenBy { it.label.lowercase() })
    }

    private fun labelForPackage(packageName: String): String {
        return try {
            val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun selectedProtectedPackages(): Set<String> {
        return QidiSettings.selectedProtectedPackages(this)
    }

    private fun saveProtectedPackages(packages: Set<String>) {
        QidiSettings.saveProtectedPackages(this, packages)
    }

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
        return ShizukuShell.run(command).format()
    }

    private fun renderStatus(message: String) {
        output.text = message
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }

    private data class PickerApp(
        val label: String,
        val packageName: String
    )
}