package app.qidi

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.qidi.ui.QidiApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        resumeWatchdogIfEnabled()
        setContent { QidiApp() }
    }

    private fun resumeWatchdogIfEnabled() {
        if (!QidiSettings.isWatchdogEnabled(this) || isWatchdogServiceRunning()) return
        QidiEventLog.append(this, "Watchdog was enabled but service was not running; restarting from UI open.")
        QidiSettings.markSelfRecovered(this)
        QidiWatchdogScheduler.scheduleRecoveryAlarm(this)
        val intent = Intent(this, QidiWatchdogService::class.java)
            .setAction(QidiWatchdogService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun isWatchdogServiceRunning(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return activityManager.getRunningServices(Int.MAX_VALUE).any { serviceInfo ->
            serviceInfo.service.className == QidiWatchdogService::class.java.name
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }
}
