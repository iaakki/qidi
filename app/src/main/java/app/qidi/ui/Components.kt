package app.qidi.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QidiCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.radiusCard,
    padding: Dp = Dimens.gutter,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = QidiTheme.colors
    val base = modifier
        .clip(RoundedCornerShape(radius))
        .background(colors.surface)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(padding)
    Box(base) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = QidiTheme.type.microLabel,
        color = QidiTheme.colors.muted,
        modifier = modifier
    )
}

@Composable
fun StatusChip(text: String, hue: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.pill))
            .border(1.dp, hue.copy(alpha = 0.55f), RoundedCornerShape(Dimens.pill))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), style = QidiTheme.type.microLabel, color = hue)
    }
}

/** The hero's only decorative motion: a 2.6s breathe on the freshness dot. */
@Composable
fun BreathingDot(hue: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(Dimens.pill))
            .background(hue)
    )
}

@Composable
fun StatusDot(hue: Color, size: Dp = 9.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.pill))
            .background(hue)
    )
}

@Composable
fun PrimaryPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Dimens.actionButton,
    onClick: () -> Unit
) {
    val colors = QidiTheme.colors
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.pill))
            .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = QidiTheme.type.rowLabel, color = colors.background)
    }
}

@Composable
fun OutlinedPillButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.actionButton,
    onClick: () -> Unit
) {
    val colors = QidiTheme.colors
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.pill))
            .border(1.dp, colors.divider, RoundedCornerShape(Dimens.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = QidiTheme.type.rowLabel, color = colors.text)
    }
}

@Composable
fun LockPill(text: String, modifier: Modifier = Modifier) {
    val colors = QidiTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.pill))
            .background(colors.elevated)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(QidiIcons.Lock, null, Modifier.size(13.dp), tint = colors.muted)
        Text(text, style = QidiTheme.type.microLabel, color = colors.muted)
    }
}

@Composable
fun QidiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = QidiTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.background,
            checkedTrackColor = colors.ok,
            checkedBorderColor = colors.ok,
            uncheckedThumbColor = colors.muted,
            uncheckedTrackColor = colors.elevated,
            uncheckedBorderColor = colors.divider
        )
    )
}

@Composable
fun IconButtonBox(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Dimens.touchTarget)
            .clip(RoundedCornerShape(Dimens.pill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = QidiTheme.colors.text)
    }
}

/** Real launcher icon when available, otherwise the design's initial-letter square. */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    size: Dp,
    radius: Dp = Dimens.radiusIcon,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = QidiTheme.colors
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(colors.elevated),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = label.take(1).uppercase(),
                style = QidiTheme.type.rowLabel.copy(fontSize = (size.value * 0.4f).sp),
                color = colors.muted
            )
        }
    }
}

@Composable
fun RowScope.Spacer(width: Dp) = Box(Modifier.size(width))

@Composable
fun StatusGlyph(health: Health): ImageVector = when (health) {
    Health.OK -> QidiIcons.ShieldCheck
    Health.WAIT -> QidiIcons.ShieldClock
    Health.BAD -> QidiIcons.ShieldX
    Health.OFF -> QidiIcons.Power
}

@Composable
fun MinTouchRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
