package app.qidi.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.qidi.QidiCommands
import app.qidi.QidiEventLog
import app.qidi.QidiFieldLog
import app.qidi.QidiSettings
import app.qidi.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuState { READY, PERMISSION_NEEDED, NOT_RESPONDING }

enum class SentinelState { ACTIVE, MISSING }

/** `DEFERRED` means Qidi is deliberately waiting for the screen to turn off. */
enum class AppStatus { RUNNING, DEFERRED, STOPPED, UNWATCHED }

data class AppEntry(
    val packageName: String,
    val label: String,
    val isProtected: Boolean,
    val status: AppStatus
)

data class HeroCopy(val chip: String, val title: String, val sentence: String)

data class QidiUiState(
    val ownPackage: String = "app.qidi",
    val shizuku: ShizukuState = ShizukuState.NOT_RESPONDING,
    val sentinel: SentinelState = SentinelState.MISSING,
    val protectionEnabled: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val lastCheckAt: Long = 0L,
    val recoveries24h: Int = 0,
    val lastRecoveryAt: Long? = null,
    val selfRecoveredAt: Long? = null,
    val logBytes: Long = 0L,
    val loggingEnabled: Boolean = true,
    val alertsEnabled: Boolean = true,
    val screenOffOnly: Boolean = true,
    val loading: Boolean = true
) {
    val protectedApps: List<AppEntry> get() = apps.filter { it.isProtected }

    /** Qidi always protects itself, so "nothing chosen" means only Qidi is on the list. */
    val firstRun: Boolean get() = protectedApps.none { it.packageName != ownPackage }

    val watchedApps: List<AppEntry> get() = protectedApps.filter { it.packageName != ownPackage }

    val runningCount: Int get() = watchedApps.count { it.status == AppStatus.RUNNING }

    val deferredCount: Int get() = watchedApps.count { it.status == AppStatus.DEFERRED }

    val health: Health
        get() = when {
            !protectionEnabled || firstRun -> Health.OFF
            shizuku != ShizukuState.READY -> Health.BAD
            sentinel == SentinelState.MISSING -> Health.WAIT
            else -> Health.OK
        }

    fun heroCopy(): HeroCopy {
        val total = watchedApps.size
        return when {
            firstRun -> HeroCopy(
                "Not set up",
                "Nothing to protect yet",
                "Choose the apps that must stay alive. Qidi always protects itself."
            )
            !protectionEnabled -> HeroCopy(
                "Off",
                "Protection is off",
                "Nothing is being watched."
            )
            shizuku == ShizukuState.PERMISSION_NEEDED -> HeroCopy(
                "Broken",
                "Shizuku permission was revoked",
                "Qidi cannot run privileged commands, so nothing is being protected right now."
            )
            shizuku == ShizukuState.NOT_RESPONDING -> HeroCopy(
                "Broken",
                "Shizuku is not responding",
                "Qidi has no privileged access and cannot restart anything. Start the Shizuku " +
                    "service and protection resumes on its own."
            )
            sentinel == SentinelState.MISSING -> HeroCopy(
                "Attention",
                "Protection is thinner than usual",
                "Your apps are running, but nothing is watching Qidi itself. If Qidi is killed " +
                    "now, it stays dead until you open it."
            )
            deferredCount > 0 -> {
                val waiting = watchedApps.first { it.status == AppStatus.DEFERRED }.label
                HeroCopy(
                    "Protected",
                    "$runningCount running, $deferredCount queued",
                    "$waiting has no invisible way to start, so Qidi is holding it until your " +
                        "screen turns off rather than interrupting you."
                )
            }
            else -> HeroCopy(
                "Protected",
                "All $total apps running",
                "Everything Qidi watches is alive. Nothing has needed you."
            )
        }
    }
}

object QidiStateLoader {

    suspend fun load(context: Context): QidiUiState = withContext(Dispatchers.IO) {
        val own = context.packageName
        val shizuku = shizukuState()
        val shellUsable = shizuku == ShizukuState.READY

        val screenAwake = if (shellUsable) {
            ShizukuShell.run(QidiCommands.isScreenAwakeCommand()).stdout.trim() == "true"
        } else {
            true
        }

        val sentinel = if (shellUsable &&
            ShizukuShell.run(QidiCommands.sentinelAliveCommand()).stdout.trim() == "true"
        ) {
            SentinelState.ACTIVE
        } else {
            SentinelState.MISSING
        }

        val selected = QidiSettings.selectedProtectedPackages(context)
        val apps = installedApps(context, selected).map { entry ->
            if (!entry.isProtected || entry.packageName == own) {
                entry.copy(status = if (entry.isProtected) AppStatus.RUNNING else AppStatus.UNWATCHED)
            } else if (!shellUsable) {
                entry.copy(status = AppStatus.UNWATCHED)
            } else {
                val state = ShizukuShell.run(QidiCommands.processStateCommand(entry.packageName))
                    .stdout.trim()
                entry.copy(
                    status = when {
                        state == "running" -> AppStatus.RUNNING
                        screenAwake -> AppStatus.DEFERRED
                        else -> AppStatus.STOPPED
                    }
                )
            }
        }

        val recoveries = QidiSettings.recoveryTimestamps(context)
        QidiUiState(
            ownPackage = own,
            shizuku = shizuku,
            sentinel = sentinel,
            protectionEnabled = QidiSettings.isWatchdogEnabled(context),
            apps = apps,
            lastCheckAt = QidiSettings.lastCheckAt(context),
            recoveries24h = recoveries.size,
            lastRecoveryAt = recoveries.maxOrNull(),
            selfRecoveredAt = QidiSettings.selfRecoveredAt(context),
            logBytes = QidiEventLog.sizeBytes(context) + QidiFieldLog.sizeBytes(context),
            loggingEnabled = QidiSettings.isLoggingEnabled(context),
            alertsEnabled = QidiSettings.areAlertsEnabled(context),
            screenOffOnly = QidiSettings.isScreenOffOnly(context),
            loading = false
        )
    }

    private fun shizukuState(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RESPONDING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuState.PERMISSION_NEEDED
            else -> ShizukuState.READY
        }
    } catch (_: Throwable) {
        ShizukuState.NOT_RESPONDING
    }

    private fun installedApps(context: Context, selected: Set<String>): List<AppEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        val installed = pm.getInstalledApplications(0)
            .filter { info ->
                info.packageName in selected ||
                    info.packageName in launchable ||
                    info.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    isProtected = info.packageName in selected,
                    status = AppStatus.UNWATCHED
                )
            }

        val missing = selected.filter { pkg -> installed.none { it.packageName == pkg } }
            .map { AppEntry(it, it, true, AppStatus.UNWATCHED) }

        return (installed + missing)
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<AppEntry> { it.isProtected }.thenBy { it.label.lowercase() })
    }
}
