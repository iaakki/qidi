package app.qidi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    state: QidiUiState,
    checking: Boolean,
    onOpenSettings: () -> Unit,
    onOpenApps: () -> Unit,
    onToggleProtection: () -> Unit,
    onRecoverNow: () -> Unit,
    onDismissBanner: () -> Unit,
    onShizukuAction: () -> Unit,
    onReinstallSentinel: () -> Unit
) {
    val colors = QidiTheme.colors
    val health = state.health
    val hue = colors.hue(health)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.gutter)
            .padding(bottom = Dimens.space6)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Qidi", style = QidiTheme.type.display, color = colors.text)
            Spacer(Modifier.weight(1f))
            IconButtonBox(QidiIcons.Settings, "Settings", onOpenSettings)
        }

        state.selfRecoveredAt?.let { at ->
            RecoveryBanner(at, onDismissBanner)
            Spacer(Modifier.height(Dimens.space3))
        }

        HealthHero(state, health, hue, checking)

        Spacer(Modifier.height(Dimens.space3))

        HealthChecklist(state, onShizukuAction, onReinstallSentinel)

        if (!state.firstRun) {
            Spacer(Modifier.height(Dimens.space3))
            ProtectedSummary(state, onOpenApps)
        }

        if (!state.firstRun && state.protectionEnabled) {
            Spacer(Modifier.height(Dimens.space3))
            Text(
                text = activitySentence(state),
                style = QidiTheme.type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = Dimens.space1)
            )
        }

        Spacer(Modifier.height(Dimens.space4))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            PrimaryPillButton(
                text = when {
                    state.firstRun -> "Choose apps to protect"
                    state.protectionEnabled -> "Stop protection"
                    else -> "Start protection"
                },
                modifier = Modifier.weight(1f),
                onClick = if (state.firstRun) onOpenApps else onToggleProtection
            )
            if (!state.firstRun && state.protectionEnabled) {
                OutlinedPillButton(
                    text = if (checking) "Checking…" else "Recover now",
                    onClick = onRecoverNow
                )
            }
        }
    }
}

@Composable
private fun RecoveryBanner(at: Long, onDismiss: () -> Unit) {
    val colors = QidiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.tint(Health.OK))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(QidiIcons.Refresh, null, Modifier.size(18.dp), tint = colors.ok)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Qidi was force-stopped at ${clockTime(at)} and brought itself back. " +
                "Nothing was missed.",
            style = QidiTheme.type.secondary,
            color = colors.text,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Got it",
            style = QidiTheme.type.rowLabel.copy(fontSize = 13.sp),
            color = colors.ok,
            modifier = Modifier.clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun HealthHero(state: QidiUiState, health: Health, hue: Color, checking: Boolean) {
    val colors = QidiTheme.colors
    val copy = state.heroCopy()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(colors.tint(health))
            .border(1.dp, hue.copy(alpha = 0.32f), RoundedCornerShape(Dimens.radiusHero))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(Dimens.pill))
                    .border(2.5.dp, hue, RoundedCornerShape(Dimens.pill)),
                contentAlignment = Alignment.Center
            ) {
                Icon(StatusGlyph(health), null, Modifier.size(27.dp), tint = hue)
            }
            Spacer(Modifier.width(Dimens.space3))
            StatusChip(copy.chip, hue)
        }

        Spacer(Modifier.height(Dimens.space3))
        Text(copy.title, style = QidiTheme.type.heroTitle, color = colors.text)
        Spacer(Modifier.height(Dimens.space2))
        Text(copy.sentence, style = QidiTheme.type.body.copy(fontSize = 14.5.sp), color = colors.muted)

        if (state.protectionEnabled && !state.firstRun) {
            Spacer(Modifier.height(Dimens.space3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BreathingDot(hue)
                Spacer(Modifier.width(Dimens.space2))
                Text(
                    text = if (checking) "Checking now…" else freshness(state.lastCheckAt),
                    style = QidiTheme.type.secondary,
                    color = colors.muted
                )
            }
        }
    }
}

@Composable
private fun HealthChecklist(
    state: QidiUiState,
    onShizukuAction: () -> Unit,
    onReinstallSentinel: () -> Unit
) {
    val colors = QidiTheme.colors

    QidiCard(Modifier.fillMaxWidth(), padding = 6.dp) {
        Column {
            val shizukuHealthy = state.shizuku == ShizukuState.READY
            ChecklistRow(
                label = "Shizuku",
                value = when (state.shizuku) {
                    ShizukuState.READY -> "Ready"
                    ShizukuState.PERMISSION_NEEDED -> "Permission needed"
                    ShizukuState.NOT_RESPONDING -> "Not responding"
                },
                health = if (shizukuHealthy) Health.OK else Health.BAD,
                hint = when (state.shizuku) {
                    ShizukuState.PERMISSION_NEEDED ->
                        "Qidi needs permission in Shizuku before it can run privileged commands."
                    ShizukuState.NOT_RESPONDING ->
                        "Open Shizuku and start the service. Protection resumes on its own."
                    else -> null
                },
                actionLabel = when (state.shizuku) {
                    ShizukuState.PERMISSION_NEEDED -> "Grant"
                    ShizukuState.NOT_RESPONDING -> "Open Shizuku"
                    else -> null
                },
                onAction = onShizukuAction
            )

            val watchdogHealthy = state.protectionEnabled
            ChecklistRow(
                label = "Watchdog",
                value = if (watchdogHealthy) "Running" else "Stopped",
                health = if (watchdogHealthy) Health.OK else Health.OFF,
                hint = null,
                actionLabel = null,
                onAction = {}
            )

            if (state.sentinel == SentinelState.MISSING && state.protectionEnabled) {
                ChecklistRow(
                    label = "Backup sentinel",
                    value = "Missing",
                    health = Health.WAIT,
                    hint = "Nothing will restart Qidi if it is killed.",
                    actionLabel = "Reinstall",
                    onAction = onReinstallSentinel
                )
            }
        }
    }

    if (state.sentinel == SentinelState.ACTIVE) {
        Spacer(Modifier.height(Dimens.space2))
        Row(
            Modifier.padding(horizontal = Dimens.space1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(QidiIcons.Shield, null, Modifier.size(13.dp), tint = colors.muted)
            Spacer(Modifier.width(6.dp))
            Text(
                "Backup sentinel active",
                style = QidiTheme.type.secondary.copy(fontSize = 12.sp),
                color = colors.muted
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    value: String,
    health: Health,
    hint: String?,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val colors = QidiTheme.colors
    val hue = colors.hue(health)
    val unhealthy = hint != null || actionLabel != null

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(Dimens.radiusRow))
            .background(if (unhealthy) colors.tint(health) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = if (unhealthy) 13.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(hue)
            Spacer(Modifier.width(Dimens.space2))
            Text(label, style = QidiTheme.type.rowLabel, color = colors.text)
            Spacer(Modifier.weight(1f))
            Text(value, style = QidiTheme.type.statusValue, color = hue)
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = QidiTheme.type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(start = 21.dp)
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            PrimaryPillButton(
                text = actionLabel,
                height = 40.dp,
                modifier = Modifier.padding(start = 21.dp),
                onClick = onAction
            )
        }
    }
}

@Composable
private fun ProtectedSummary(state: QidiUiState, onOpenApps: () -> Unit) {
    val colors = QidiTheme.colors
    val watched = state.watchedApps

    QidiCard(Modifier.fillMaxWidth(), onClick = onOpenApps) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(if (watched.isEmpty()) 0.dp else (34 + (watched.size.coerceAtMost(5) - 1) * 26).dp)) {
                watched.take(5).forEachIndexed { index, app ->
                    AppIcon(
                        packageName = app.packageName,
                        label = app.label,
                        size = 34.dp,
                        radius = Dimens.radiusIconSmall,
                        modifier = Modifier.offset(x = (-8 * index).dp)
                    )
                }
            }
            if (watched.isNotEmpty()) Spacer(Modifier.width(Dimens.space3))
            Column(Modifier.weight(1f)) {
                Text(
                    "${watched.size} apps protected",
                    style = QidiTheme.type.rowLabel,
                    color = colors.text
                )
                Text(
                    text = when {
                        state.shizuku != ShizukuState.READY ->
                            "None protected while Shizuku is unavailable"
                        state.deferredCount > 0 ->
                            "${state.runningCount} running · ${state.deferredCount} waiting for screen off"
                        else -> "${state.runningCount} running now"
                    },
                    style = QidiTheme.type.secondary,
                    color = colors.muted
                )
            }
            Icon(QidiIcons.ChevronRight, null, Modifier.size(20.dp), tint = colors.muted)
        }
    }
}

private fun activitySentence(state: QidiUiState): String {
    if (state.recoveries24h == 0) return "No recoveries in the last 24 hours."
    val recent = state.lastRecoveryAt?.let { clockTime(it) }
    return if (state.shizuku != ShizukuState.READY) {
        "${state.recoveries24h} recoveries in the last 24 hours · none since Shizuku stopped responding"
    } else {
        "${state.recoveries24h} recoveries in the last 24 hours · most recent $recent"
    }
}

private fun freshness(lastCheckAt: Long): String {
    if (lastCheckAt <= 0L) return "Not checked yet"
    val seconds = (System.currentTimeMillis() - lastCheckAt) / 1000
    return when {
        seconds < 5 -> "Checked just now"
        seconds < 60 -> "Checked $seconds seconds ago"
        seconds < 3600 -> "Checked ${seconds / 60} minutes ago"
        else -> "Checked ${seconds / 3600} hours ago"
    }
}

private fun clockTime(at: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
