package app.qidi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppsScreen(
    state: QidiUiState,
    onBack: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit
) {
    val colors = QidiTheme.colors
    var query by remember { mutableStateOf("") }

    val matches = { app: AppEntry ->
        query.isBlank() ||
            app.label.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true)
    }
    val protected = state.apps.filter { it.isProtected && matches(it) }
    val others = state.apps.filter { !it.isProtected && matches(it) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButtonBox(QidiIcons.ArrowLeft, "Back", onBack)
            Spacer(Modifier.width(4.dp))
            Text("Protected apps", style = QidiTheme.type.screenTitle, color = colors.text)
        }

        Box(Modifier.padding(horizontal = Dimens.gutter)) {
            SearchField(query) { query = it }
        }

        Spacer(Modifier.height(Dimens.space3))

        if (state.protectedApps.none { it.packageName != state.ownPackage } && query.isBlank()) {
            Box(Modifier.padding(horizontal = Dimens.gutter)) {
                EmptyProtectedState()
            }
            Spacer(Modifier.height(Dimens.space3))
        }

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter)) {
            if (protected.isNotEmpty()) {
                item {
                    SectionLabel("Protected · ${protected.size}", Modifier.padding(vertical = Dimens.space2))
                }
                items(protected, key = { it.packageName }) { app ->
                    AppRow(app, state.ownPackage, onToggleApp)
                }
            }
            if (others.isNotEmpty()) {
                item {
                    SectionLabel("All apps", Modifier.padding(top = Dimens.space4, bottom = Dimens.space2))
                }
                items(others, key = { it.packageName }) { app ->
                    AppRow(app, state.ownPackage, onToggleApp)
                }
            }
            item { Spacer(Modifier.height(Dimens.space6)) }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = QidiTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(Dimens.touchTarget)
            .clip(RoundedCornerShape(Dimens.pill))
            .background(colors.surface),
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            QidiIcons.Search,
            null,
            Modifier.padding(start = 14.dp).size(17.dp),
            tint = colors.muted
        )
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = QidiTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 14.dp)
        ) { inner ->
            if (query.isEmpty()) {
                Text("Search apps", style = QidiTheme.type.body, color = colors.muted)
            }
            inner()
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, ownPackage: String, onToggle: (String, Boolean) -> Unit) {
    val colors = QidiTheme.colors
    val isSelf = app.packageName == ownPackage

    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.appRow)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = app.packageName,
            label = app.label,
            size = 42.dp,
            modifier = if (app.isProtected) Modifier else Modifier.alpha(0.75f)
        )
        Spacer(Modifier.width(Dimens.space3))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = QidiTheme.type.rowLabel,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                app.packageName,
                style = QidiTheme.type.packageName,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.isProtected) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val health = when (app.status) {
                        AppStatus.RUNNING -> Health.OK
                        AppStatus.DEFERRED -> Health.WAIT
                        AppStatus.STOPPED -> Health.BAD
                        AppStatus.UNWATCHED -> Health.OFF
                    }
                    StatusDot(colors.hue(health), size = 7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (app.status) {
                            AppStatus.RUNNING -> "Running"
                            AppStatus.DEFERRED -> "Queued for screen off"
                            AppStatus.STOPPED -> "Stopped"
                            AppStatus.UNWATCHED -> "Not watched"
                        },
                        style = QidiTheme.type.packageName.copy(fontSize = 11.5.sp),
                        color = colors.hue(health)
                    )
                }
            }
        }
        Spacer(Modifier.width(Dimens.space2))

        if (isSelf) {
            Column(
                modifier = Modifier.width(124.dp),
                horizontalAlignment = Alignment.End
            ) {
                LockPill("Always on")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Qidi is killed too — it must guard itself",
                    style = QidiTheme.type.packageName.copy(fontSize = 10.5.sp),
                    color = QidiTheme.colors.muted,
                    textAlign = TextAlign.End
                )
            }
        } else {
            QidiSwitch(
                checked = app.isProtected,
                onCheckedChange = { checked -> onToggle(app.packageName, checked) }
            )
        }
    }
}

@Composable
private fun EmptyProtectedState() {
    val colors = QidiTheme.colors
    QidiCard(Modifier.fillMaxWidth(), padding = 22.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(Dimens.pill))
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(QidiIcons.Plus, null, Modifier.size(22.dp), tint = colors.accent)
            }
            Spacer(Modifier.height(Dimens.space3))
            Text("Nothing protected yet", style = QidiTheme.type.rowLabel, color = colors.text)
            Spacer(Modifier.height(Dimens.space2))
            Text(
                "Turn on the few apps that must never die — a messenger, a sync client, your " +
                    "automation. Qidi keeps itself alive automatically, so it is already on the list.",
                style = QidiTheme.type.secondary,
                color = colors.muted
            )
        }
    }
}
