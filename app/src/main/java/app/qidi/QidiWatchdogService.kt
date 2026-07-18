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
    private val restartHistory = mutableMapOf<String, ArrayDeque<Long>>()
    private val lastKnownRunning = mutableMapOf<String, Boolean>()

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
        QidiEventLog.append(this, "Watchdog service started.")
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
        QidiEventLog.append(this, "Watchdog service destroyed.")
        super.onDestroy()
    }

    private fun stopWatchdog() {
        QidiSettings.setWatchdogEnabled(this, false)
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
            updateStatus("Watchdog waiting: Shizuku binder is not available.")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            updateStatus("Watchdog waiting: Shizuku permission is not granted.")
            return
        }

        val packages = QidiSettings.selectedProtectedPackages(this)
            .filter { packageName -> packageName != this.packageName && packageName.isValidPackageName() }

        if (packages.isEmpty()) {
            updateStatus("Watchdog running: no restartable apps selected.")
            return
        }

        val restarted = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val idle = mutableListOf<String>()
        val qidiWasForeground = ShizukuShell.run(QidiCommands.isPackageFocusedCommand(packageName)).stdout.trim() == "true"

        packages.forEach { packageName ->
            val state = ShizukuShell.run(QidiCommands.processStateCommand(packageName)).stdout.trim()
            if (state == "running") {
                lastKnownRunning[packageName] = true
                return@forEach
            }

            val shouldRestart = recoverNow || state == "stopped" || lastKnownRunning[packageName] == true
            if (!shouldRestart) {
                idle.add(packageName)
                return@forEach
            }

            if (!recordRestartAttempt(packageName)) {
                limited.add(packageName)
                QidiEventLog.append(this, "Rate limited restart for $packageName.")
                return@forEach
            }

            QidiEventLog.append(this, "Restarting $packageName after state=$state.")
            QidiCommands.protectionCommands(packageName).forEach { command -> ShizukuShell.run(command) }
            ShizukuShell.run(QidiCommands.restartCommand(packageName))
            val updatedState = ShizukuShell.run(QidiCommands.processStateCommand(packageName)).stdout.trim()
            if (updatedState == "running") {
                restarted.add(packageName)
                lastKnownRunning[packageName] = true
                QidiEventLog.append(this, "Restarted $packageName.")
                if (qidiWasForeground) ShizukuShell.run(QidiCommands.startQidiCommand(this.packageName))
            } else {
                failed.add(packageName)
                lastKnownRunning[packageName] = false
                QidiEventLog.append(this, "Failed to restart $packageName; state=$updatedState.")
            }
        }

        val status = buildString {
            append("Watchdog checked ${packages.size} apps at ${timestamp()}.")
            if (recoverNow) append(" Manual recovery.")
            if (restarted.isNotEmpty()) append(" Restarted: ${restarted.joinToString()}.")
            if (idle.isNotEmpty()) append(" Idle: ${idle.size}.")
            if (limited.isNotEmpty()) append(" Rate limited: ${limited.joinToString()}.")
            if (failed.isNotEmpty()) append(" Failed: ${failed.joinToString()}.")
        }
        updateStatus(status)
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

    private fun updateStatus(status: String) {
        QidiSettings.setWatchdogStatus(this, status)
        QidiEventLog.append(this, status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification(status))
    }

    private fun startInForeground(status: String) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(status), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification(status))
        }
    }

    private fun notification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Qidi watchdog")
            .setContentText(status.take(NOTIFICATION_TEXT_LIMIT))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Qidi watchdog",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    private fun String.isValidPackageName(): Boolean {
        return PACKAGE_NAME_PATTERN.matches(this)
    }

    companion object {
        const val ACTION_START = "app.qidi.action.START_WATCHDOG"
        const val ACTION_STOP = "app.qidi.action.STOP_WATCHDOG"
        const val EXTRA_RECOVER_NOW = "app.qidi.extra.RECOVER_NOW"

        private const val NOTIFICATION_CHANNEL_ID = "qidi_watchdog"
        private const val NOTIFICATION_ID = 100
        private const val CHECK_INTERVAL_SECONDS = 15L
        private const val MAX_RESTARTS_PER_WINDOW = 3
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
        private const val MIN_IMMEDIATE_CHECK_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_TEXT_LIMIT = 180
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}