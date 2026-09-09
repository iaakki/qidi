package app.qidi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Two channels by design: a silent heartbeat whose *presence* means Qidi is alive, and a
 * separate channel that only speaks for problems the user must act on.
 */
object QidiNotifications {
    // Channel importance is fixed once created, so the id carries a version suffix.
    const val STATUS_CHANNEL = "qidi_status_v2"
    const val PROBLEM_CHANNEL = "qidi_problems"
    const val STATUS_ID = 100
    private const val PROBLEM_ID = 200

    enum class Problem { NONE, SHIZUKU_PERMISSION, SHIZUKU_DEAD, SENTINEL_MISSING }

    private var lastStatusText: String? = null
    private var lastProblem: Problem = Problem.NONE

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, "Qidi status", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "Silent ongoing notification. Its presence means Qidi is alive."
                    setShowBadge(false)
                }
        )
        manager.createNotificationChannel(
            NotificationChannel(PROBLEM_CHANNEL, "Qidi problems", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Only for problems that need you. Never routine restarts." }
        )
    }

    fun statusNotification(context: Context, title: String, body: String): Notification {
        return NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_qidi)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Re-posts only when the visible text changes, so the shade stays still. */
    fun updateStatus(context: Context, title: String, body: String) {
        val text = "$title|$body"
        if (text == lastStatusText) return
        lastStatusText = text
        context.getSystemService(NotificationManager::class.java)
            .notify(STATUS_ID, statusNotification(context, title, body))
    }

    fun resetStatusCache() {
        lastStatusText = null
    }

    fun updateProblem(context: Context, problem: Problem) {
        if (problem == lastProblem) return
        lastProblem = problem

        val manager = context.getSystemService(NotificationManager::class.java)
        if (problem == Problem.NONE || !QidiSettings.areAlertsEnabled(context)) {
            manager.cancel(PROBLEM_ID)
            return
        }

        val (title, body) = when (problem) {
            Problem.SHIZUKU_PERMISSION ->
                "Shizuku permission was revoked" to
                    "Grant Qidi permission in Shizuku to resume protection."
            Problem.SHIZUKU_DEAD ->
                "Shizuku is not responding" to
                    "Open Shizuku and start the service. Protection resumes automatically."
            Problem.SENTINEL_MISSING ->
                "Backup sentinel is missing" to
                    "Nothing will restart Qidi if it is killed. Reinstall it from Qidi."
            Problem.NONE -> return
        }

        val notification = NotificationCompat.Builder(context, PROBLEM_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_qidi)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(PROBLEM_ID, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
