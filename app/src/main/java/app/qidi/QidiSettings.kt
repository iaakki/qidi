package app.qidi

import android.content.Context

object QidiSettings {
    private const val PREFERENCES_NAME = "qidi"
    private const val PROTECTED_PACKAGES_KEY = "protected_packages"
    private const val WATCHDOG_ENABLED_KEY = "watchdog_enabled"
    private const val WATCHDOG_STATUS_KEY = "watchdog_status"
    private const val LOGGING_ENABLED_KEY = "logging_enabled"
    private const val ALERTS_ENABLED_KEY = "alerts_enabled"
    private const val SCREEN_OFF_ONLY_KEY = "screen_off_only"
    private const val RECOVERIES_KEY = "recovery_timestamps"
    private const val LAST_CHECK_KEY = "last_check_at"
    private const val RECOVERED_AT_KEY = "self_recovered_at"
    private const val RECOVERED_SEEN_KEY = "self_recovered_seen"
    private const val INSTALLED_AT_KEY = "installed_at"

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private val defaultProtectedPackages = emptySet<String>()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isLoggingEnabled(context: Context): Boolean = prefs(context).getBoolean(LOGGING_ENABLED_KEY, true)

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(LOGGING_ENABLED_KEY, enabled).apply()
    }

    fun areAlertsEnabled(context: Context): Boolean = prefs(context).getBoolean(ALERTS_ENABLED_KEY, true)

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ALERTS_ENABLED_KEY, enabled).apply()
    }

    fun isScreenOffOnly(context: Context): Boolean = prefs(context).getBoolean(SCREEN_OFF_ONLY_KEY, true)

    fun setScreenOffOnly(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SCREEN_OFF_ONLY_KEY, enabled).apply()
    }

    fun recordRecovery(context: Context, at: Long = System.currentTimeMillis()) {
        val kept = (recoveryTimestamps(context) + at).filter { at - it < DAY_MS }
        prefs(context).edit().putString(RECOVERIES_KEY, kept.joinToString(",")).apply()
    }

    fun recoveryTimestamps(context: Context): List<Long> {
        val raw = prefs(context).getString(RECOVERIES_KEY, "").orEmpty()
        val cutoff = System.currentTimeMillis() - DAY_MS
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it >= cutoff }
    }

    fun setLastCheckAt(context: Context, at: Long) {
        prefs(context).edit().putLong(LAST_CHECK_KEY, at).apply()
    }

    fun lastCheckAt(context: Context): Long = prefs(context).getLong(LAST_CHECK_KEY, 0L)

    fun markSelfRecovered(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(RECOVERED_AT_KEY, at).putBoolean(RECOVERED_SEEN_KEY, false).apply()
    }

    fun selfRecoveredAt(context: Context): Long? {
        if (prefs(context).getBoolean(RECOVERED_SEEN_KEY, true)) return null
        val at = prefs(context).getLong(RECOVERED_AT_KEY, 0L)
        return if (at > 0 && System.currentTimeMillis() - at < DAY_MS) at else null
    }

    fun dismissSelfRecovered(context: Context) {
        prefs(context).edit().putBoolean(RECOVERED_SEEN_KEY, true).apply()
    }

    fun installedAt(context: Context): Long {
        val existing = prefs(context).getLong(INSTALLED_AT_KEY, 0L)
        if (existing > 0L) return existing
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(INSTALLED_AT_KEY, now).apply()
        return now
    }

    fun selectedProtectedPackages(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val savedPackages = preferences.getStringSet(PROTECTED_PACKAGES_KEY, null)
        return ((savedPackages ?: defaultProtectedPackages) + context.packageName).toSortedSet()
    }

    fun saveProtectedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PROTECTED_PACKAGES_KEY, packages + context.packageName)
            .apply()
    }

    fun isWatchdogEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(WATCHDOG_ENABLED_KEY, false)
    }

    fun setWatchdogEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WATCHDOG_ENABLED_KEY, enabled)
            .apply()
    }

    fun watchdogStatus(context: Context): String {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(WATCHDOG_STATUS_KEY, "Watchdog has not run yet.") ?: "Watchdog has not run yet."
    }

    fun setWatchdogStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WATCHDOG_STATUS_KEY, status)
            .apply()
    }
}