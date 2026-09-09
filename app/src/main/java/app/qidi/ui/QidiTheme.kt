package app.qidi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.qidi.R

/** Status semantics from the design system; colour is never the only carrier. */
enum class Health { OK, WAIT, BAD, OFF }

@Immutable
data class QidiColors(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val accent: Color,
    val ok: Color,
    val wait: Color,
    val bad: Color,
    val off: Color,
    val isDark: Boolean
) {
    fun hue(health: Health): Color = when (health) {
        Health.OK -> ok
        Health.WAIT -> wait
        Health.BAD -> bad
        Health.OFF -> off
    }

    fun tint(health: Health): Color = hue(health).copy(
        alpha = when (health) {
            Health.OK, Health.WAIT -> 0.12f
            Health.BAD -> 0.13f
            Health.OFF -> 0.10f
        }
    )
}

private val DarkColors = QidiColors(
    background = Color(0xFF15120F),
    surface = Color(0xFF221E19),
    elevated = Color(0xFF2C2721),
    text = Color(0xFFF7F1E6),
    muted = Color(0xFFB3A897),
    divider = Color(0xFFF7F1E6).copy(alpha = 0.13f),
    accent = Color(0xFFC67139),
    ok = Color(0xFFB9CB9B),
    wait = Color(0xFFF0A16F),
    bad = Color(0xFFE5806C),
    off = Color(0xFF9C9384),
    isDark = true
)

private val LightColors = QidiColors(
    background = Color(0xFFF5EAD8),
    surface = Color(0xFFECE0CA),
    elevated = Color(0xFFFFFAF0),
    text = Color(0xFF201E1D),
    muted = Color(0xFF6B6355),
    divider = Color(0xFF201E1D).copy(alpha = 0.15f),
    accent = Color(0xFFC67139),
    ok = Color(0xFF56633F),
    wait = Color(0xFFA2551F),
    bad = Color(0xFF9D3B28),
    off = Color(0xFF82796A),
    isDark = false
)

/** Spacing, radii and sizes transcribed from the handoff. */
object Dimens {
    val space1 = 4.4.dp
    val space2 = 8.8.dp
    val space3 = 13.2.dp
    val space4 = 17.6.dp
    val space6 = 26.4.dp

    val gutter = 16.dp
    val radiusHero = 30.dp
    val radiusCard = 26.dp
    val radiusRow = 21.dp
    val radiusInset = 18.dp
    val radiusIcon = 13.dp
    val radiusIconSmall = 11.dp
    val pill = 999.dp

    val touchTarget = 48.dp
    val appRow = 64.dp
    val actionButton = 50.dp
}

private val Caprasimo = FontFamily(Font(R.font.caprasimo, FontWeight.Normal))

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    resId = R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private val Figtree = FontFamily(figtree(400), figtree(600), figtree(700))

@Immutable
data class QidiTypography(
    val display: TextStyle,
    val screenTitle: TextStyle,
    val heroTitle: TextStyle,
    val body: TextStyle,
    val rowLabel: TextStyle,
    val secondary: TextStyle,
    val packageName: TextStyle,
    val microLabel: TextStyle,
    val statusValue: TextStyle
)

private val Typography = QidiTypography(
    display = TextStyle(fontFamily = Caprasimo, fontSize = 25.sp, lineHeight = 28.sp),
    screenTitle = TextStyle(fontFamily = Caprasimo, fontSize = 22.sp, lineHeight = 25.sp),
    heroTitle = TextStyle(fontFamily = Caprasimo, fontSize = 27.sp, lineHeight = 30.2.sp),
    body = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 22.5.sp),
    rowLabel = TextStyle(fontFamily = Figtree, fontSize = 15.sp, fontWeight = FontWeight(600)),
    secondary = TextStyle(fontFamily = Figtree, fontSize = 12.5.sp, lineHeight = 18.1.sp),
    packageName = TextStyle(fontFamily = Figtree, fontSize = 11.5.sp, lineHeight = 15.sp),
    microLabel = TextStyle(
        fontFamily = Figtree,
        fontSize = 10.5.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 1.37.sp
    ),
    statusValue = TextStyle(fontFamily = Figtree, fontSize = 13.sp, fontWeight = FontWeight(700))
)

private val LocalQidiColors = staticCompositionLocalOf { DarkColors }
private val LocalQidiTypography = staticCompositionLocalOf { Typography }

object QidiTheme {
    val colors: QidiColors
        @Composable @ReadOnlyComposable get() = LocalQidiColors.current
    val type: QidiTypography
        @Composable @ReadOnlyComposable get() = LocalQidiTypography.current
}

@Composable
fun QidiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            surface = colors.surface,
            background = colors.background,
            onSurface = colors.text
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            surface = colors.surface,
            background = colors.background,
            onSurface = colors.text
        )
    }
    CompositionLocalProvider(
        LocalQidiColors provides colors,
        LocalQidiTypography provides Typography
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
