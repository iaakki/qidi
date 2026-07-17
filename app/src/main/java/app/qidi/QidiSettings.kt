package app.qidi

import android.content.Context

object QidiSettings {
    private const val PREFERENCES_NAME = "qidi"
    private const val PROTECTED_PACKAGES_KEY = "protected_packages"
    private const val WATCHDOG_ENABLED_KEY = "watchdog_enabled"
    private const val WATCHDOG_STATUS_KEY = "watchdog_status"

    private val defaultProtectedPackages = setOf(
        "app.qidi",
        "dev.shadoe.delta",
        "io.homeassistant.companion.android",
        "com.assaabloy.yale",
        "com.life360.android.safetymapd"
    )

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