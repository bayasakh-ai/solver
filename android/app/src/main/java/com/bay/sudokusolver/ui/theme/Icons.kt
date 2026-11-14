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
    val Info = icon("info", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "M12 16v-4", "M12 8h.01")
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

    // core flow (design/screens/flow)
    val ArrowRight = icon("arrow-right", "M5 12h14", "m12 5 7 7-7 7")
    val ChevronRight = icon("chevron-right", "m9 18 6-6-6-6")
    val Lightbulb = icon("lightbulb",
        "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5",
        "M9 18h6", "M10 22h4")
    val Eye = icon("eye",
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    val Undo = icon("undo-2", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
    val Eraser = icon("eraser",
        "m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21",
        "M22 21H7", "m5 11 9 9")
    val Clock = icon("clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 6v6l4 2")
    val Grid = icon("grid-3x3",
        "M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M3 9h18", "M3 15h18", "M9 3v18", "M15 3v18")
    val PenOff = icon("pen-off",
        "m10 10-6.157 6.162a2 2 0 0 0-.5.833l-1.322 4.36a.5.5 0 0 0 .622.624l4.358-1.323a2 2 0 0 0 .83-.5L14 13.982",
        "m12.829 7.172 4.359-4.346a1 1 0 1 1 3.986 3.986l-4.353 4.353", "m15 5 4 4", "m2 2 20 20")
}
