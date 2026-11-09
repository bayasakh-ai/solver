package com.bay.sudokusolver.ui.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.Cells
import com.bay.sudokusolver.Hint
import com.bay.sudokusolver.Technique
import com.bay.sudokusolver.UnitType
import com.bay.sudokusolver.ui.theme.SudokuColors

/*
 * Hint marks over the board (design/screens/flow/Play.dc.html): the unit frame, rings
 * on the digits that rule cells out, lines sweeping along their rows and columns,
 * crosses on ruled-out cells, a glow on the target and the digit writing in. Level 3
 * (Show me) plays them on a 5 s loop with the design's keyframes; [t] is 0..1 in that
 * loop. Everything is drawn from the Hint data; nothing here knows the technique rules.
 */

/** Progress of [t] between [a] and [b], clamped to 0..1. */
private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Fade out over the last 8% of the loop, as in the design (opacity 1 until 92%). */
private fun fadeOut(t: Float) = 1f - ramp(t, 0.92f, 1f)

/** Static frame used by level 2 and when animations are off: everything shown. */
const val STILL = 0.7f

object HintKeys {
    fun ringScale(t: Float) = when {
        t < 0.06f -> 0.6f + 0.58f * ramp(t, 0f, 0.06f)
        t < 0.12f -> 1.18f - 0.18f * ramp(t, 0.06f, 0.12f)
        else -> 1f
    }
    fun ringAlpha(t: Float) = ramp(t, 0f, 0.06f) * fadeOut(t)
    fun sweep(t: Float) = ramp(t, 0.12f, 0.30f)
    fun sweepAlpha(t: Float) = fadeOut(t)
    fun cross(t: Float) = ramp(t, 0.30f, 0.38f) * fadeOut(t)
    fun glow(t: Float) = ramp(t, 0.40f, 0.50f) * fadeOut(t)
    fun writeAlpha(t: Float) = ramp(t, 0.50f, 0.62f) * fadeOut(t)
    fun writeScale(t: Float) = 1.5f - 0.5f * ramp(t, 0.50f, 0.62f)
    /** Which of the 3 step lines is lit (0-based). */
    fun step(t: Float) = when { t <= 0.30f -> 0; t <= 0.50f -> 1; else -> 2 }
}

fun DrawScope.drawHint(
    hint: Hint,
    level: Int,
    t: Float,
    cell: Float,
    colors: SudokuColors,
    measurer: TextMeasurer,
    answer: Color,
    revealed: Boolean = false,
) {
    val stroke = colors.hintStrokePaper
    val halo = colors.strokeHaloPaper
    val w = 2.dp.toPx()
    val haloW = w + 3.dp.toPx()
    fun center(i: Int) = Offset((Cells.col(i) + 0.5f) * cell, (Cells.row(i) + 0.5f) * cell)
    fun cellRect(i: Int) = Rect(Offset(Cells.col(i) * cell, Cells.row(i) * cell), Size(cell, cell))

    fun glow(i: Int, alpha: Float) {
        if (alpha <= 0f) return
        val r = cellRect(i).deflate(1.dp.toPx())
        for (k in 3 downTo 1) drawRect(colors.hintGlowPaper.copy(alpha = colors.hintGlowPaper.alpha * alpha / k),
            r.topLeft - Offset(k * 2.dp.toPx(), k * 2.dp.toPx()), Size(r.width + k * 4.dp.toPx(), r.height + k * 4.dp.toPx()),
            style = Stroke(2.dp.toPx()))
        drawRect(stroke.copy(alpha = alpha), r.topLeft, r.size, style = Stroke(w))
    }

    fun write(i: Int, alpha: Float, scale: Float) {
        if (alpha <= 0f) return
        val style = PlayerDigit.copy(fontSize = 28.sp, color = answer.copy(alpha = alpha))
        val layout = measurer.measure("${hint.digit}", style)
        val c = center(i)
        scale(scale, c) {
            val tl = c - Offset(layout.size.width / 2f, layout.size.height / 2f)
            drawText(measurer, "${hint.digit}", tl, style.copy(color = halo.copy(alpha = alpha),
                drawStyle = Stroke(3.dp.toPx())))
            drawText(layout, topLeft = tl)
        }
    }

    if (hint.technique == Technique.ADVANCED) {
        glow(hint.target, 1f)
        if (revealed) write(hint.target, 1f, 1f)
        return
    }

    // unit frame with a light tint (levels 1-3)
    val frame = hint.unit?.let { u ->
        when (u.type) {
            UnitType.BOX -> Rect(Offset(u.index % 3 * 3 * cell, u.index / 3 * 3 * cell), Size(3 * cell, 3 * cell))
            UnitType.ROW -> Rect(Offset(0f, u.index * cell), Size(9 * cell, cell))
            UnitType.COLUMN -> Rect(Offset(u.index * cell, 0f), Size(cell, 9 * cell))
        }
    } ?: cellRect(hint.target)
    drawRect(colors.hintTintPaper, frame.topLeft, frame.size)
    drawRect(halo, frame.topLeft, frame.size, style = Stroke(haloW))
    drawRect(stroke, frame.topLeft, frame.size, style = Stroke(w))
    if (level < 2) return

    // sweeps: from each eliminator along its row or column, past the last crossed cell
    val sweepP = HintKeys.sweep(t); val sweepA = HintKeys.sweepAlpha(t)
    if (sweepP > 0f) for (s in hint.sweeps) {
        val a = center(s.from); val b = center(s.to)
        val dir = (b - a).let { it / it.getDistance() }
        val end = b + dir * (cell / 2)
        val tip = a + (end - a) * sweepP
        drawLine(halo.copy(alpha = halo.alpha * sweepA), a, tip, haloW, StrokeCap.Butt)
        drawLine(stroke.copy(alpha = sweepA), a, tip, w, StrokeCap.Butt)
    }
    // rings on the eliminators
    val ringA = HintKeys.ringAlpha(t); val ringS = HintKeys.ringScale(t)
    if (ringA > 0f) for (e in hint.eliminators) {
        val r = cell * 0.42f * ringS
        drawCircle(halo.copy(alpha = halo.alpha * ringA), r, center(e), style = Stroke(haloW))
        drawCircle(stroke.copy(alpha = ringA), r, center(e), style = Stroke(w))
    }
    // crosses on cells that can't take the digit
    val crossA = HintKeys.cross(t)
    if (crossA > 0f) for (x in hint.crossedOut) {
        val c = center(x); val h = 6.dp.toPx()
        for ((p, q) in listOf(Offset(-h, -h) to Offset(h, h), Offset(h, -h) to Offset(-h, h))) {
            drawLine(stroke.copy(alpha = crossA), c + p, c + q, 1.8.dp.toPx(), StrokeCap.Round)
        }
    }
    glow(hint.target, HintKeys.glow(t))
    if (level >= 3) write(hint.target, HintKeys.writeAlpha(t), HintKeys.writeScale(t))
}
