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
    private lateinit var content: LinearLayout
    private var currentView = ViewMode.DASHBOARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        requestNotificationPermissionIfNeeded()
        renderCurrentView()
    }

    private fun buildLayout(): ScrollView {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
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

    private fun renderCurrentView(message: String? = null) {
        content.removeAllViews()
        addHeader()
        when (currentView) {
            ViewMode.DASHBOARD -> renderDashboard(message)
            ViewMode.APPS -> renderApps(message)
            ViewMode.TERMINATIONS -> renderTerminations(message)
        }
    }

    private fun addHeader() {
        content.addView(TextView(this).apply {
            text = "Qidi"
            textSize = 28f
        })
        content.addView(TextView(this).apply {
            text = "Keep selected apps alive with Shizuku."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        })

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(navButton("Watchdog", ViewMode.DASHBOARD), equalWeightParams())
            addView(navButton("Apps", ViewMode.APPS), equalWeightParams())
            addView(navButton("Terminations", ViewMode.TERMINATIONS), equalWeightParams())
        })
    }

    private fun renderDashboard(message: String?) {
        addSectionTitle("Watchdog")
        addStatusLine("Shizuku", shizukuStatus())
        addStatusLine("Watchdog", if (QidiSettings.isWatchdogEnabled(this)) "Running" else "Stopped")
        addStatusLine("Selected apps", selectedProtectedPackages().size.toString())

        content.addView(actionButton("Start Watchdog") { startWatchdog() })
        content.addView(actionButton("Stop Watchdog") { stopWatchdog() })
        content.addView(actionButton("Apply Protections Now") { applyProtections() })
        content.addView(actionButton("Check Shizuku") { checkShizuku() })

        addSectionTitle("Flow")
        addBody("Apps: choose what Qidi protects. Watchdog: keep it running. Terminations: review recent stops.")

        addSectionTitle("Latest")
        addBody(message ?: QidiSettings.watchdogStatus(this))
    }

    private fun renderApps(message: String?) {
        addSectionTitle("Protected Apps")
        addBody(message ?: "Checked apps are watched in the background. Qidi stays selected so it can protect itself.")
        content.addView(actionButton("Refresh App List") { renderCurrentView("App list refreshed.") })

        val selectedPackages = selectedProtectedPackages().toMutableSet()
        val apps = installedPickerApps(selectedPackages)

        apps.forEach { app ->
            content.addView(CheckBox(this).apply {
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

    private fun renderTerminations(message: String?) {
        addSectionTitle("Recent Terminations")
        if (message != null) addBody(message)
        content.addView(actionButton("Refresh Terminations") { refreshTerminations() })
        addBody(recentTerminationsSummary())
    }

    private fun checkShizuku() {
        val message = when {
            !Shizuku.pingBinder() -> "Shizuku binder is not available."
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Shizuku is ready."
            else -> {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                "Requested Shizuku permission."
            }
        }
        renderCurrentView(message)
    }

    private fun startWatchdog() {
        if (!ensureShizukuPermission()) return
        QidiSettings.setWatchdogEnabled(this, true)
        val intent = Intent(this, QidiWatchdogService::class.java).setAction(QidiWatchdogService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        renderCurrentView("Watchdog started. Selected stopped apps will be restarted, up to 3 times per minute per app.")
    }

    private fun stopWatchdog() {
        QidiSettings.setWatchdogEnabled(this, false)
        startService(Intent(this, QidiWatchdogService::class.java).setAction(QidiWatchdogService.ACTION_STOP))
        renderCurrentView("Watchdog stopped.")
    }

    private fun applyProtections() {
        if (!ensureShizukuPermission()) return

        val packages = selectedProtectedPackages()
        if (packages.isEmpty()) {
            renderCurrentView("No protected apps selected.")
            return
        }

        val failed = mutableListOf<String>()
        packages.forEach { packageName ->
            QidiCommands.protectionCommands(packageName).forEach { command ->
                if (ShizukuShell.run(command).exitCode != 0) failed.add(packageName)
            }
        }

        val message = if (failed.isEmpty()) {
            "Protections applied to ${packages.size} apps."
        } else {
            "Protections applied with failures: ${failed.distinct().joinToString()}."
        }
        renderCurrentView(message)
    }

    private fun refreshTerminations() {
        if (!ensureShizukuPermission()) return
        currentView = ViewMode.TERMINATIONS
        renderCurrentView("Termination list refreshed.")
    }

    private fun recentTerminationsSummary(): String {
        if (!Shizuku.pingBinder()) return "Shizuku binder is not available."
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "Shizuku permission is not granted."

        val entries = selectedProtectedPackages()
            .filter { it != packageName }
            .flatMap { packageName -> recentTerminations(packageName) }
            .sortedByDescending { it.timestamp }
            .take(MAX_TERMINATION_ENTRIES)

        if (entries.isEmpty()) return "No recent terminations found for selected apps."
        return entries.joinToString("\n\n") { entry ->
            buildString {
                appendLine(entry.packageName)
                appendLine(entry.timestamp)
                appendLine("${entry.reason} / ${entry.subreason}")
                if (entry.description.isNotBlank()) append(entry.description)
            }
        }
    }

    private fun recentTerminations(packageName: String): List<TerminationEntry> {
        val output = ShizukuShell.run("dumpsys activity exit-info ${ShizukuShell.quote(packageName)} | sed -n '1,120p'").stdout
        val entries = mutableListOf<TerminationEntry>()
        var timestamp = ""
        var reason = ""
        var subreason = ""
        var description = ""

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("timestamp=") -> timestamp = trimmed.substringAfter("timestamp=").substringBefore(" pid=")
                trimmed.startsWith("process=") -> {
                    reason = trimmed.substringAfter("reason=").substringBefore(" subreason=")
                    subreason = trimmed.substringAfter("subreason=").substringBefore(" status=")
                }
                trimmed.startsWith("importance=") -> {
                    description = trimmed.substringAfter("description=", "").substringBefore(" state=")
                    if (timestamp.isNotBlank() && reason.isNotBlank()) {
                        entries.add(TerminationEntry(packageName, timestamp, reason, subreason, description))
                    }
                    timestamp = ""
                    reason = ""
                    subreason = ""
                    description = ""
                }
            }
        }
        return entries.take(3)
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

        val fallbackApps = selectedPackages.map { packageName -> PickerApp(labelForPackage(packageName), packageName) }

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

    private fun selectedProtectedPackages(): Set<String> = QidiSettings.selectedProtectedPackages(this)

    private fun saveProtectedPackages(packages: Set<String>) {
        QidiSettings.saveProtectedPackages(this, packages)
    }

    private fun ensureShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            renderCurrentView("Shizuku binder is not available.")
            return false
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
        renderCurrentView("Requested Shizuku permission. Try again after approving it.")
        return false
    }

    private fun shizukuStatus(): String {
        return when {
            !Shizuku.pingBinder() -> "Unavailable"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Ready"
            else -> "Permission needed"
        }
    }

    private fun addSectionTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 20f
            setPadding(0, 24, 0, 8)
        })
    }

    private fun addStatusLine(label: String, value: String) {
        content.addView(TextView(this).apply {
            text = "$label: $value"
            textSize = 16f
            setPadding(0, 4, 0, 4)
        })
    }

    private fun addBody(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
    }

    private fun navButton(label: String, viewMode: ViewMode): Button {
        return actionButton(if (currentView == viewMode) "[$label]" else label) {
            currentView = viewMode
            renderCurrentView()
        }
    }

    private fun equalWeightParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
        private const val MAX_TERMINATION_ENTRIES = 12
    }

    private enum class ViewMode {
        DASHBOARD,
        APPS,
        TERMINATIONS
    }

    private data class PickerApp(
        val label: String,
        val packageName: String
    )

    private data class TerminationEntry(
        val packageName: String,
        val timestamp: String,
        val reason: String,
        val subreason: String,
        val description: String
    )
}