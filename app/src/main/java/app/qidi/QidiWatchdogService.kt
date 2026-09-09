package app.qidi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class QidiWatchdogService : Service() {
    private lateinit var executor: ScheduledExecutorService
    private var watchdogTask: ScheduledFuture<*>? = null
    private var lastImmediateCheckAt = 0L
    private var lastHeartbeatAt = 0L
    private var lastSentinelEnsureAt = 0L
    private var lastLoggedStatus: String? = null
    private var publishedPackages: List<String>? = null
    private val restartHistory = mutableMapOf<String, ArrayDeque<Long>>()
    private val lastKnownRunning = mutableMapOf<String, Boolean>()
    private val lastLoggedState = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadScheduledExecutor()
        createNotificationChannel()
        QidiEventLog.append(this, "Watchdog service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWatchdog()
            return START_NOT_STICKY
        }

        val recoverNow = intent?.getBooleanExtra(EXTRA_RECOVER_NOW, false) == true
        QidiSettings.setWatchdogEnabled(this, true)
        startInForeground("Watching selected apps")
        QidiWatchdogScheduler.scheduleRecoveryAlarm(this)
        QidiEventLog.append(this, "Watchdog service started.")
        QidiFieldLog.append(this, "watchdog-start selected=${QidiSettings.selectedProtectedPackages(this).joinToString()}")
        if (executor.isShutdown) executor = Executors.newSingleThreadScheduledExecutor()
        if (watchdogTask?.isDone != false) {
            watchdogTask = executor.scheduleWithFixedDelay(
                { checkProtectedApps(false) },
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            )
        }
        if (recoverNow || System.currentTimeMillis() - lastImmediateCheckAt > MIN_IMMEDIATE_CHECK_INTERVAL_MS) {
            lastImmediateCheckAt = System.currentTimeMillis()
            executor.execute { checkProtectedApps(recoverNow) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watchdogTask?.cancel(true)
        executor.shutdownNow()
        if (QidiSettings.isWatchdogEnabled(this)) QidiWatchdogScheduler.scheduleRecoveryAlarm(this)
        QidiEventLog.append(this, "Watchdog service destroyed.")
        super.onDestroy()
    }

    private fun stopWatchdog() {
        QidiSettings.setWatchdogEnabled(this, false)
        ShizukuShell.run(QidiCommands.stopSentinelCommand())
        QidiWatchdogScheduler.cancelRecoveryAlarm(this)
        QidiSettings.setWatchdogStatus(this, "Watchdog stopped at ${timestamp()}.")
        QidiEventLog.append(this, "Watchdog stopped by user.")
        watchdogTask?.cancel(true)
        watchdogTask = null
        executor.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun checkProtectedApps(recoverNow: Boolean) {
        if (!QidiSettings.isWatchdogEnabled(this)) return

        if (!Shizuku.pingBinder()) {
            updateStatus("Watchdog waiting: Shizuku binder is not available.", recordEvent = true)
            publishUnavailable("Waiting for Shizuku")
            QidiNotifications.updateProblem(this, QidiNotifications.Problem.SHIZUKU_DEAD)
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            updateStatus("Watchdog waiting: Shizuku permission is not granted.", recordEvent = true)
            publishUnavailable("Shizuku permission needed")
            QidiNotifications.updateProblem(this, QidiNotifications.Problem.SHIZUKU_PERMISSION)
            return
        }

        QidiCommands.protectionCommands(packageName).forEach { command -> ShizukuShell.run(command) }
        ensureShellSentinel()

        val screenProbe = ShizukuShell.run(QidiCommands.isScreenAwakeCommand()).stdout.trim()
        if (screenProbe.isBlank()) {
            updateStatus("Watchdog waiting: Shizuku shell is not responding.", recordEvent = true)
            publishUnavailable("Waiting for Shizuku")
            QidiNotifications.updateProblem(this, QidiNotifications.Problem.SHIZUKU_DEAD)
            return
        }
        val screenAwake = screenProbe == "true"

        val sentinelMissing =
            ShizukuShell.run(QidiCommands.sentinelAliveCommand()).stdout.trim() != "true"
        QidiSettings.setLastCheckAt(this, System.currentTimeMillis())
        QidiNotifications.updateProblem(
            this,
            if (sentinelMissing) {
                QidiNotifications.Problem.SENTINEL_MISSING
            } else {
                QidiNotifications.Problem.NONE
            }
        )

        val packages = QidiSettings.selectedProtectedPackages(this)
            .filter { packageName -> packageName != this.packageName && packageName.isValidPackageName() }

        publishProtectedPackages(packages)

        if (packages.isEmpty()) {
            updateStatus("Watchdog running: no restartable apps selected.", recordEvent = true)
            publishHeartbeat(0, 0, sentinelMissing)
            return
        }

        recordHeartbeat(packages)

        val restarted = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val idle = mutableListOf<String>()
        val deferred = mutableListOf<String>()
        val unknown = mutableListOf<String>()

        packages.forEach { packageName ->
            val state = ShizukuShell.run(QidiCommands.processStateCommand(packageName)).stdout.trim()
            if (state.isBlank()) {
                unknown.add(packageName)
                return@forEach
            }
            if (state == "running") {
                lastKnownRunning[packageName] = true
                recordStateChange(packageName, state)
                return@forEach
            }

            recordStateChange(packageName, state)

            val shouldRestart = recoverNow || state == "stopped" || lastKnownRunning[packageName] == true
            if (!shouldRestart) {
                idle.add(packageName)
                return@forEach
            }

            if (!recordRestartAttempt(packageName)) {
                limited.add(packageName)
                QidiEventLog.append(this, "Rate limited restart for $packageName.")
                recordIncident(packageName, state, "rate-limited")
                return@forEach
            }

            QidiEventLog.append(this, "Restarting $packageName after state=$state.")
            recordIncident(packageName, state, "restart-attempt")
            QidiCommands.protectionCommands(packageName).forEach { command -> ShizukuShell.run(command) }
            ShizukuShell.run(QidiCommands.quietRestartCommand(packageName))
            val updatedState = ShizukuShell.run(QidiCommands.processStateCommand(packageName)).stdout.trim()
            when {
                updatedState == "running" -> {
                    restarted.add(packageName)
                    lastKnownRunning[packageName] = true
                    QidiSettings.recordRecovery(this)
                    QidiEventLog.append(this, "Restarted $packageName in the background.")
                    recordIncident(packageName, updatedState, "restart-success")
                }
                screenAwake -> {
                    deferred.add(packageName)
                    QidiEventLog.append(this, "Deferred $packageName until the screen turns off.")
                    recordIncident(packageName, updatedState, "restart-deferred")
                }
                else -> {
                    failed.add(packageName)
                    lastKnownRunning[packageName] = false
                    QidiEventLog.append(this, "Failed to restart $packageName; state=$updatedState.")
                    recordIncident(packageName, updatedState, "restart-failed")
                }
            }
        }

        val status = buildString {
            append("Watchdog checked ${packages.size} apps at ${timestamp()}.")
            if (recoverNow) append(" Manual recovery.")
            if (restarted.isNotEmpty()) append(" Restarted: ${restarted.joinToString()}.")
            if (deferred.isNotEmpty()) append(" Waiting for screen off: ${deferred.joinToString()}.")
            if (idle.isNotEmpty()) append(" Idle: ${idle.size}.")
            if (limited.isNotEmpty()) append(" Rate limited: ${limited.joinToString()}.")
            if (failed.isNotEmpty()) append(" Failed: ${failed.joinToString()}.")
            if (unknown.isNotEmpty()) append(" Unreadable: ${unknown.size}.")
        }
        updateStatus(
            status,
            recordEvent = recoverNow || restarted.isNotEmpty() || limited.isNotEmpty() ||
                failed.isNotEmpty() || deferred.isNotEmpty()
        )
        publishHeartbeat(packages.size, deferred.size, sentinelMissing)
    }

    private fun publishProtectedPackages(packages: List<String>) {
        val payload = packages.sorted()
        if (payload == publishedPackages) return
        if (ShizukuShell.run(QidiCommands.writeProtectedPackagesCommand(payload)).exitCode != 0) return
        publishedPackages = payload
        QidiFieldLog.append(this, "sentinel-packages ${payload.joinToString()}")
    }

    private fun recordHeartbeat(packages: List<String>) {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatAt = now
        QidiFieldLog.append(this, "heartbeat watchdog-running selected=${packages.joinToString()}")
    }

    private fun ensureShellSentinel() {
        val now = System.currentTimeMillis()
        if (now - lastSentinelEnsureAt < SENTINEL_ENSURE_INTERVAL_MS) return
        lastSentinelEnsureAt = now

        val result = ShizukuShell.run(QidiCommands.installSentinelCommand())
        val detail = result.format().compactForLog()
        QidiFieldLog.append(this, "shell-sentinel-ensure $detail")
    }

    private fun recordStateChange(packageName: String, state: String) {
        val previousState = lastLoggedState.put(packageName, state)
        if (previousState != null && previousState != state) {
            QidiFieldLog.append(this, "state-change package=$packageName previous=$previousState current=$state")
        }
    }

    private fun recordIncident(packageName: String, state: String, action: String) {
        val packageState = ShizukuShell.run(QidiCommands.packageStateCommand(packageName)).stdout.compactForLog()
        val standbyBucket = ShizukuShell.run("am get-standby-bucket ${ShizukuShell.quote(packageName)} 2>/dev/null").stdout.compactForLog()
        val exitInfo = ShizukuShell.run(QidiCommands.recentExitInfoCommand(packageName)).stdout.compactForLog()
        QidiFieldLog.append(
            this,
            "incident action=$action package=$packageName state=$state bucket=$standbyBucket packageState=$packageState exitInfo=$exitInfo"
        )
    }

    private fun recordRestartAttempt(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val attempts = restartHistory.getOrPut(packageName) { ArrayDeque() }
        while (attempts.isNotEmpty() && now - attempts.first() > RATE_LIMIT_WINDOW_MS) {
            attempts.removeFirst()
        }
        if (attempts.size >= MAX_RESTARTS_PER_WINDOW) return false
        attempts.addLast(now)
        return true
    }

    private fun updateStatus(status: String, recordEvent: Boolean = false) {
        QidiSettings.setWatchdogStatus(this, status)
        if (recordEvent || lastLoggedStatus != status && !status.startsWith("Watchdog checked")) {
            QidiEventLog.append(this, status)
            lastLoggedStatus = status
        }
    }

    /** Heartbeat text stays still unless something real changed; see [QidiNotifications]. */
    private fun publishHeartbeat(protectedCount: Int, deferred: Int, sentinelMissing: Boolean) {
        val title = "Qidi · protecting $protectedCount apps"
        val body = when {
            sentinelMissing -> "Backup sentinel missing"
            deferred > 0 -> "$deferred waiting for screen off"
            else -> "Ongoing · silent"
        }
        QidiNotifications.updateStatus(this, title, body)
    }

    private fun publishUnavailable(body: String) {
        QidiNotifications.updateStatus(this, "Qidi · not protecting", body)
    }

    private fun startInForeground(status: String) {
        val notification = QidiNotifications.statusNotification(this, "Qidi · starting", status)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        QidiNotifications.resetStatusCache()
    }

    private fun createNotificationChannel() {
        QidiNotifications.ensureChannels(this)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    private fun String.isValidPackageName(): Boolean {
        return PACKAGE_NAME_PATTERN.matches(this)
    }

    private fun String.compactForLog(): String {
        return lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .joinToString(" | ")
            .ifBlank { "none" }
            .take(FIELD_LOG_VALUE_LIMIT)
    }

    companion object {
        const val ACTION_START = "app.qidi.action.START_WATCHDOG"
        const val ACTION_STOP = "app.qidi.action.STOP_WATCHDOG"
        const val EXTRA_RECOVER_NOW = "app.qidi.extra.RECOVER_NOW"

        private const val NOTIFICATION_ID = 100
        private const val CHECK_INTERVAL_SECONDS = 15L
        private const val MAX_RESTARTS_PER_WINDOW = 3
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
        private const val MIN_IMMEDIATE_CHECK_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val SENTINEL_ENSURE_INTERVAL_MS = 5 * 60 * 1000L
        private const val FIELD_LOG_VALUE_LIMIT = 700
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}