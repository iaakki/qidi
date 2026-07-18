package app.qidi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class QidiBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!QidiSettings.isWatchdogEnabled(context)) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        QidiEventLog.append(context, "Watchdog resume requested after $action.")
        val serviceIntent = Intent(context, QidiWatchdogService::class.java).setAction(QidiWatchdogService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
    }
}