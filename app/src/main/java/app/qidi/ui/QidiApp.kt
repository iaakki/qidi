package app.qidi.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.qidi.QidiCommands
import app.qidi.QidiEventLog
import app.qidi.QidiFieldLog
import app.qidi.QidiSettings
import app.qidi.QidiWatchdogService
import app.qidi.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private enum class Screen { STATUS, APPS, SETTINGS }

@Composable
fun QidiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.STATUS) }
    var state by remember { mutableStateOf(QidiUiState(ownPackage = context.packageName)) }
    var checking by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        while (true) {
            state = QidiStateLoader.load(context)
            delay(5_000)
        }
    }

    fun refresh() {
        refreshKey++
    }

    QidiTheme {
        if (screen != Screen.STATUS) {
            BackHandler { screen = Screen.STATUS }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(QidiTheme.colors.background)
                .systemBarsPadding()
        ) {
            when (screen) {
                Screen.STATUS -> StatusScreen(
                    state = state,
                    checking = checking,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenApps = { screen = Screen.APPS },
                    onToggleProtection = {
                        scope.launch {
                            toggleProtection(context, state.protectionEnabled)
                            refresh()
                        }
                    },
                    onRecoverNow = {
                        scope.launch {
                            checking = true
                            startWatchdog(context, recoverNow = true)
                            delay(1_400)
                            checking = false
                            refresh()
                        }
                    },
                    onDismissBanner = {
                        QidiSettings.dismissSelfRecovered(context)
                        refresh()
                    },
                    onShizukuAction = {
                        scope.launch {
                            handleShizukuAction(context, state.shizuku)
                            refresh()
                        }
                    },
                    onReinstallSentinel = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ShizukuShell.run(QidiCommands.installSentinelCommand())
                            }
                            refresh()
                        }
                    }
                )

                Screen.APPS -> AppsScreen(
                    state = state,
                    onBack = { screen = Screen.STATUS },
                    onToggleApp = { packageName, checked ->
                        val selected = QidiSettings.selectedProtectedPackages(context).toMutableSet()
                        if (checked) selected.add(packageName) else selected.remove(packageName)
                        QidiSettings.saveProtectedPackages(context, selected)
                        refresh()
                    }
                )

                Screen.SETTINGS -> SettingsScreen(
                    state = state,
                    installedAt = QidiSettings.installedAt(context),
                    onBack = { screen = Screen.STATUS },
                    onLoggingChange = {
                        QidiSettings.setLoggingEnabled(context, it)
                        refresh()
                    },
                    onAlertsChange = {
                        QidiSettings.setAlertsEnabled(context, it)
                        refresh()
                    },
                    onScreenOffOnlyChange = {
                        QidiSettings.setScreenOffOnly(context, it)
                        refresh()
                    },
                    onDeleteLog = {
                        QidiEventLog.clear(context)
                        QidiFieldLog.clear(context)
                        refresh()
                    }
                )
            }
        }
    }
}

private fun toggleProtection(context: Context, currentlyEnabled: Boolean) {
    if (currentlyEnabled) {
        QidiSettings.setWatchdogEnabled(context, false)
        context.startService(
            Intent(context, QidiWatchdogService::class.java)
                .setAction(QidiWatchdogService.ACTION_STOP)
        )
    } else {
        startWatchdog(context, recoverNow = true)
    }
}

private fun startWatchdog(context: Context, recoverNow: Boolean) {
    QidiSettings.setWatchdogEnabled(context, true)
    val intent = Intent(context, QidiWatchdogService::class.java)
        .setAction(QidiWatchdogService.ACTION_START)
        .putExtra(QidiWatchdogService.EXTRA_RECOVER_NOW, recoverNow)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun handleShizukuAction(context: Context, shizuku: ShizukuState) {
    when (shizuku) {
        ShizukuState.PERMISSION_NEEDED -> runCatching { Shizuku.requestPermission(1001) }
        ShizukuState.NOT_RESPONDING -> openShizuku(context)
        ShizukuState.READY -> Unit
    }
}

private fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (intent != null) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
