package app.qidi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object QidiWatchdogScheduler {
    private const val REQUEST_CODE = 200
    private const val RECOVERY_INTERVAL_MS = 30 * 60 * 1000L

    fun scheduleRecoveryAlarm(context: Context) {
        if (!QidiSettings.isWatchdogEnabled(context)) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + RECOVERY_INTERVAL_MS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            recoveryIntent(context)
        )
        QidiEventLog.append(context, "Next watchdog recovery alarm scheduled in 30 minutes.")
    }

    fun cancelRecoveryAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(recoveryIntent(context))
        QidiEventLog.append(context, "Watchdog recovery alarm canceled.")
    }

    private fun recoveryIntent(context: Context): PendingIntent {
        val intent = Intent(context, QidiWatchdogAlarmReceiver::class.java)
            .setAction(ACTION_RECOVER_WATCHDOG)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    const val ACTION_RECOVER_WATCHDOG = "app.qidi.action.RECOVER_WATCHDOG"
}