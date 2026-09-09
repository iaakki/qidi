package app.qidi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    state: QidiUiState,
    installedAt: Long,
    onBack: () -> Unit,
    onLoggingChange: (Boolean) -> Unit,
    onAlertsChange: (Boolean) -> Unit,
    onScreenOffOnlyChange: (Boolean) -> Unit,
    onDeleteLog: () -> Unit
) {
    val colors = QidiTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButtonBox(QidiIcons.ArrowLeft, "Back", onBack)
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = QidiTheme.type.screenTitle, color = colors.text)
        }

        Column(Modifier.padding(horizontal = Dimens.gutter)) {
            QidiCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                Column {
                    SettingRow(
                        label = "Record diagnostic log",
                        subtitle = "Only needed when something goes wrong. It writes " +
                            "continuously and uses storage.",
                        checked = state.loggingEnabled,
                        onCheckedChange = onLoggingChange
                    )

                    if (state.loggingEnabled) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(Dimens.radiusInset))
                                .background(colors.elevated)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Diagnostic log · ${formatBytes(state.logBytes)}",
                                style = QidiTheme.type.secondary,
                                color = colors.text,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedPillButton("Delete now", height = 38.dp, onClick = onDeleteLog)
                        }
                    }

                    SettingRow(
                        label = "Alert me to problems",
                        subtitle = "Sound for things you must act on. Never for routine restarts.",
                        checked = state.alertsEnabled,
                        onCheckedChange = onAlertsChange
                    )

                    SettingRow(
                        label = "Status notification",
                        subtitle = "Android requires it while Qidi runs — and its disappearance " +
                            "is how you know Qidi was killed. That is why it cannot be turned off.",
                        checked = true,
                        onCheckedChange = null
                    )

                    SettingRow(
                        label = "Only launch apps while the screen is off",
                        subtitle = "Qidi waits rather than throwing an app in front of you. " +
                            "Turning this off lets it launch visibly.",
                        checked = state.screenOffOnly,
                        onCheckedChange = onScreenOffOnlyChange
                    )
                }
            }

            Spacer(Modifier.height(Dimens.space4))

            Column(Modifier.padding(horizontal = Dimens.space2)) {
                Text(
                    "Qidi · watching since ${monthDay(installedAt)}",
                    style = QidiTheme.type.secondary,
                    color = colors.muted
                )
                Text(
                    "${state.recoveries24h} recoveries in the last 24 hours",
                    style = QidiTheme.type.secondary,
                    color = colors.muted
                )
            }

            Spacer(Modifier.height(Dimens.space6))
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    val colors = QidiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = QidiTheme.type.rowLabel, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = QidiTheme.type.secondary, color = colors.muted)
        }
        Spacer(Modifier.width(Dimens.space3))
        if (onCheckedChange == null) {
            LockPill("Always on")
        } else {
            QidiSwitch(checked, onCheckedChange)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun monthDay(at: Long): String =
    SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(at))
