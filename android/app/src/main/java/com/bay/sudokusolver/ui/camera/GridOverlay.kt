package com.bay.sudokusolver.ui.camera

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.FrameResult
import com.bay.sudokusolver.R
import com.bay.sudokusolver.ScanState
import com.bay.sudokusolver.ui.theme.Chrome
import com.bay.sudokusolver.ui.theme.GridOutline
import com.bay.sudokusolver.ui.theme.LinePattern
import com.bay.sudokusolver.ui.theme.OutlineStyle
import com.bay.sudokusolver.ui.theme.SudokuColors
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.answerHaloWidth
import com.bay.sudokusolver.ui.theme.outline
import kotlin.math.hypot
import kotlin.math.max

/** Outline pattern for each scan state; null = no grid, so the viewfinder shows instead. */
fun ScanState.gridOutline(): GridOutline? = when (this) {
    ScanState.Searching -> null
    is ScanState.Reading -> GridOutline.READING
    is ScanState.HoldSteady -> GridOutline.HOLD_STEADY
    is ScanState.Solved -> GridOutline.SOLVED
    is ScanState.NeedsReview -> GridOutline.NEEDS_REVIEW
}

/** The outline sits just outside the grid (design: 10 px around a 450 px grid). */
private const val OUTSET = 10f / 450f

/** Answer size relative to the cell (design: 34 px digits in 50 px cells). */
private const val ANSWER_SIZE = 0.68f

/** Halo per side relative to the digit size: the theme's 2.5 dp at 34 sp. */
private const val HALO_PER_EM = 2.5f / 34f

/** Native paints for the answer digits: Lora semibold over a stroked halo. */
private class AnswerPaints(context: Context) {
    private val lora: Typeface = ResourcesCompat.getFont(context, R.font.lora) ?: Typeface.SERIF

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = lora
        textAlign = Paint.Align.CENTER
        // variable font: weight from the wght axis (API 26+); 24-25 keep the regular cut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) fontVariationSettings = "'wght' 600"
    }
    val halo = Paint(fill).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    /** Baseline offset that centres a digit vertically, as a fraction of the text size. */
    val centreOffset: Float = run {
        val probe = Paint(fill).apply { textSize = 100f }
        val bounds = Rect()
        probe.getTextBounds("0123456789", 0, 10, bounds)
        -bounds.exactCenterY() / 100f
    }
}

/**
 * Draws the detected grid over the camera preview. The preview fills the screen
 * (PreviewView's default FILL_CENTER), so frame pixels are scaled by the larger
 * ratio and centred — the same maths here keeps the outline on top of the puzzle.
 */
@Composable
fun GridOverlay(result: FrameResult?, modifier: Modifier = Modifier) {
    val colors = SudokuTheme.colors
    val context = LocalContext.current
    val paints = remember(context) { AnswerPaints(context) }
    Canvas(modifier) {
        val c = result?.corners
        if (result == null || c == null) {
            drawViewfinder()
            return@Canvas
        }
        val r = result
        val scale = max(size.width / r.frameWidth, size.height / r.frameHeight)
        val ox = (size.width - r.frameWidth * scale) / 2f
        val oy = (size.height - r.frameHeight * scale) / 2f
        fun corner(i: Int) = Offset(ox + c[2 * i] * scale, oy + c[2 * i + 1] * scale)
        val tl = corner(0); val tr = corner(1); val br = corner(2); val bl = corner(3)

        // true perspective: maps (u, v) in the unit square onto the grid's 4 corners
        val dx1 = tr.x - br.x; val dx2 = bl.x - br.x; val dx3 = tl.x - tr.x + br.x - bl.x
        val dy1 = tr.y - br.y; val dy2 = bl.y - br.y; val dy3 = tl.y - tr.y + br.y - bl.y
        val det = dx1 * dy2 - dx2 * dy1
        val g = (dx3 * dy2 - dx2 * dy3) / det
        val h = (dx1 * dy3 - dx3 * dy1) / det
        val a = tr.x - tl.x + g * tr.x; val b = bl.x - tl.x + h * bl.x
        val d = tr.y - tl.y + g * tr.y; val e = bl.y - tl.y + h * bl.y
        fun at(u: Float, v: Float): Offset {
            val w = g * u + h * v + 1f
            return Offset((a * u + b * v + tl.x) / w, (d * u + e * v + tl.y) / w)
        }
        val side = (hypot(tr.x - tl.x, tr.y - tl.y) + hypot(br.x - tr.x, br.y - tr.y) +
            hypot(bl.x - br.x, bl.y - br.y) + hypot(tl.x - bl.x, tl.y - bl.y)) / 4f

        r.state.gridOutline()?.let {
            val style = colors.outline(it)
            drawBoxLines(style, ::at)
            drawGridOutline(style, side, ::at)
        }

        val steps = r.steps
        fun stepOf(i: Int) = steps?.get(i) ?: AnswerStep.DEEP
        fun cellHeight(row: Int, u: Float) = (at(u, (row + 1f) / 9f) - at(u, row / 9f)).getDistance()

        // cells whose ink might be the user's: a RING (a shape, not only a colour),
        // until the Check screen lets the user confirm them
        r.unsure?.forEachIndexed { i, unsure ->
            if (!unsure) return@forEachIndexed
            val row = i / 9
            val u = (i % 9 + 0.5f) / 9f
            val cell = cellHeight(row, u)
            drawUnsureRing(colors, stepOf(i), at(u, (row + 0.5f) / 9f), cell)
        }

        // the answers, drawn into each empty cell with the cell's own size and perspective
        val puzzle = r.puzzle ?: return@Canvas
        val solution = r.solution ?: return@Canvas
        val canvas = drawContext.canvas.nativeCanvas
        for (i in 0 until 81) {
            if (puzzle[i] != 0) continue
            val row = i / 9
            val u = (i % 9 + 0.5f) / 9f
            val center = at(u, (row + 0.5f) / 9f)
            val textSize = cellHeight(row, u) * ANSWER_SIZE
            val step = stepOf(i)
            val text = solution[i].toString()
            val y = center.y + textSize * paints.centreOffset
            paints.halo.textSize = textSize
            paints.halo.strokeWidth = 2f * textSize * HALO_PER_EM * colors.palette.haloScale
            paints.halo.color = colors.answerHalo(step).toArgb()
            canvas.drawText(text, center.x, y, paints.halo)
            paints.fill.textSize = textSize
            paints.fill.color = colors.answer(step).toArgb()
            canvas.drawText(text, center.x, y, paints.fill)
        }
    }
}

/** Quad around the grid, [outset] (unit-square fraction) outside it. */
private fun quad(outset: Float, at: (Float, Float) -> Offset) = Path().apply {
    val lo = -outset
    val hi = 1f + outset
    at(lo, lo).let { moveTo(it.x, it.y) }
    at(hi, lo).let { lineTo(it.x, it.y) }
    at(hi, hi).let { lineTo(it.x, it.y) }
    at(lo, hi).let { lineTo(it.x, it.y) }
    close()
}

/**
 * Grid outline in its state's pattern over a continuous dark halo, so dashes and
 * dots hold on bright paper and glare. DOUBLE = two lines a third of the width each
 * with a gap between, drawn as two quads at slightly different outsets.
 */
private fun DrawScope.drawGridOutline(style: OutlineStyle, side: Float, at: (Float, Float) -> Offset) {
    val w = style.width.toPx()
    val halo = style.haloWidth.toPx()
    drawPath(quad(OUTSET, at), style.halo, style = Stroke(width = w + 2 * halo, join = StrokeJoin.Round))
    if (style.pattern == LinePattern.DOUBLE) {
        val line = w / 3f
        for (sign in listOf(-1f, 1f)) {
            drawPath(quad(OUTSET + sign * line / side, at), style.color,
                style = Stroke(width = line, join = StrokeJoin.Round))
        }
    } else {
        drawPath(quad(OUTSET, at), style.color, style = Stroke(
            width = w, cap = style.cap, join = StrokeJoin.Round, pathEffect = style.pathEffect(w)))
    }
}

/**
 * The two inner 3x3 box lines each way, thin and solid in the outline's colour over
 * a thin dark halo. Kept on purpose (the owner's choice; the design drops them):
 * they show the boxes line up with the paper. The state's pattern stays on the outer
 * outline only, so dashed/dotted/double is read in one place.
 */
private fun DrawScope.drawBoxLines(style: OutlineStyle, at: (Float, Float) -> Offset) {
    val w = 1.5.dp.toPx()
    val halo = w + 2 * 1.dp.toPx()
    for (k in 1..2) {
        val f = k / 3f
        for ((from, to) in listOf(at(f, 0f) to at(f, 1f), at(0f, f) to at(1f, f))) {
            drawLine(style.halo, from, to, halo)
            drawLine(style.color, from, to, w)
        }
    }
}

/** Unsure ring: amber, with a halo that contrasts with the cell (white on paper, black on screens). */
private fun DrawScope.drawUnsureRing(colors: SudokuColors, step: AnswerStep, center: Offset, cell: Float) {
    val onPaper = step == AnswerStep.DEEP
    val ring = if (onPaper) colors.unsureRing else colors.unsureRingOnDark
    val halo = if (onPaper) Color(0xEBFFFFFF) else Color(0xCC000000) // 92% white / 80% black
    val radius = cell * 0.44f
    val width = max(cell * 0.07f, 2.dp.toPx())
    drawCircle(halo, radius, center, style = Stroke(width = width + 2 * 1.5.dp.toPx()))
    drawCircle(ring, radius, center, style = Stroke(width = width))
}

/**
 * Corner brackets while no grid is found: where to put the puzzle. Screen-anchored,
 * same box as the design (24 dp from the sides, top ~17% / height ~46% of the screen).
 */
private fun DrawScope.drawViewfinder() {
    val inset = 24.dp.toPx()
    val left = inset
    val right = size.width - inset
    val top = size.height * (136f / 780f)
    val bottom = top + size.height * (360f / 780f)
    val arm = 44.dp.toPx()
    val corners = listOf(
        listOf(Offset(left, top + arm), Offset(left, top), Offset(left + arm, top)),
        listOf(Offset(right - arm, top), Offset(right, top), Offset(right, top + arm)),
        listOf(Offset(right, bottom - arm), Offset(right, bottom), Offset(right - arm, bottom)),
        listOf(Offset(left + arm, bottom), Offset(left, bottom), Offset(left, bottom - arm)),
    )
    val rounded = PathEffect.cornerPathEffect(7.dp.toPx())
    for (pts in corners) {
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y)
        }
        drawPath(path, Color(0x73000000), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round,
            join = StrokeJoin.Round, pathEffect = rounded))
        drawPath(path, Chrome.Text, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round,
            join = StrokeJoin.Round, pathEffect = rounded))
    }
}
