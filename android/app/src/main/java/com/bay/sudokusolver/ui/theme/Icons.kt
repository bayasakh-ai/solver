package com.bay.sudokusolver.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * Lucide icons (https://lucide.dev, ISC licence in assets/licenses/LICENSE-Lucide.txt),
 * as used by the design files: 24x24 viewbox, 1.75 stroke, round caps and joins,
 * no fill. Path data copied from design/screens/Sudoku Lens v2.dc.html.
 * Tint them with Icon(tint = ...); the stroke colour here is only a placeholder.
 */
object LucideIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private val scanCorners = arrayOf(
        "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2",
        "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2",
    )

    val Scan = icon("scan", *scanCorners)
    val ScanLine = icon("scan-line", *scanCorners, "M7 12h10")
    val Move = icon("move", "M12 2v20", "m15 19-3 3-3-3", "m19 9 3 3-3 3", "M2 12h20", "m5 9-3 3 3 3", "m9 5 3-3 3 3")
    val Check = icon("check", "M20 6 9 17l-5-5")
    val AlertCircle = icon("alert-circle", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "M12 8v4", "M12 16h.01")
    val Pause = icon("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z")
    val Play = icon("play", "M6 3l14 9-14 9z")
    val Reset = icon("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    val Camera = icon("camera",
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M9 13a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    val Zap = icon("zap", "M13 2 3 14h9l-1 8 10-12h-9l1-8z")
    val ZapOff = icon("zap-off", "M13 2 3 14h9l-1 8 10-12h-9l1-8z", "m2 2 20 20")
    val Sliders = icon("sliders-horizontal",
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3",
        "M14 2v4", "M8 10v4", "M16 18v4")
    val Pencil = icon("pencil",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4")
    val ArrowLeft = icon("arrow-left", "m12 19-7-7 7-7", "M19 12H5")
    val ChevronLeft = icon("chevron-left", "m15 18-6-6 6-6")
    val X = icon("x", "M18 6 6 18", "M6 6l12 12")
}
