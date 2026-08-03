package app.qidi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class QidiWatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QidiWatchdogScheduler.ACTION_RECOVER_WATCHDOG) return
        if (!QidiSettings.isWatchdogEnabled(context)) return

        QidiEventLog.append(context, "Watchdog recovery alarm fired.")
        QidiWatchdogScheduler.scheduleRecoveryAlarm(context)

        val serviceIntent = Intent(context, QidiWatchdogService::class.java)
            .setAction(QidiWatchdogService.ACTION_START)
            .putExtra(QidiWatchdogService.EXTRA_RECOVER_NOW, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}