package app.qidi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide glyphs (lucide.dev), stroke 2.75 as specified by the design handoff. */
object QidiIcons {
    private const val STROKE = 2.75f

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private const val SHIELD =
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 " +
            "4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"

    val Shield = icon("shield", SHIELD)
    val ShieldCheck = icon("shield-check", SHIELD, "m9 12 2 2 4-4")
    val ShieldClock = icon("shield-clock", SHIELD, "M12 8.6v3.6l2.2 1.3")
    val ShieldX = icon("shield-x", SHIELD, "m14.5 9.5-5 5", "m9.5 9.5 5 5")
    val Power = icon("power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.77.04")
    val Refresh = icon(
        "refresh-cw",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5"
    )
    val Settings = icon(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 " +
            "0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15" +
            ".09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 " +
            "1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2" +
            " 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5" +
            "a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 " +
            "0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    )
    val ArrowLeft = icon("arrow-left", "m12 19-7-7 7-7", "M19 12H5")
    val ChevronRight = icon("chevron-right", "m9 18 6-6-6-6")
    val Search = icon("search", "M17 11a6 6 0 1 1-12 0 6 6 0 0 1 12 0z", "m21 21-4.35-4.35")
    val Lock = icon(
        "lock",
        "M5 11h14v10H5z",
        "M8 11V7a4 4 0 0 1 8 0v4"
    )
    val Plus = icon("plus", "M5 12h14", "M12 5v14")
}
