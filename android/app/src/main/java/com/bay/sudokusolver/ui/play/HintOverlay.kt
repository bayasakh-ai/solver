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
import com.bay.sudokusolver.SudokuUnit
import com.bay.sudokusolver.Technique
import com.bay.sudokusolver.UnitType
import com.bay.sudokusolver.ui.theme.SudokuColors

/*
 * Hint marks over the board (design/screens/flow/Play.dc.html): the unit frame, rings
 * on the digits that rule cells out, lines sweeping along their rows and columns,
 * crosses on ruled-out cells, a glow on the target and the digit writing in. Level 3
 * (Show me) plays them on a 5 s loop with the design's keyframes; [t] is 0..1 in that
 * loop. Everything is drawn from the Hint data; nothing here knows the technique rules.
 *
 * Each technique has its own story (CLAUDE.md "Hints"; only the hidden single is in the
 * design files, the other two reuse its marks and colours):
 *   hidden single    the digits ring, lines sweep, cells are crossed out, one is left
 *   naked single     row, column and box light up, the eight digits the cell sees are
 *                    ringed one by one in digit order, the ninth writes in
 *   last empty cell  the unit lights up, its eight digits are ticked off one by one,
 *                    the missing one writes in (sooner: there is less to show)
 *   pointing, claiming, naked pair, hidden pair (Technique.isPattern)
 *                    the pattern's cells are boxed with their candidate digits, the
 *                    cells that lose a digit show it struck through, the unit with one
 *                    place left is framed, then the digit writes in
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

    /** When the target starts to glow: after the technique's own marks are done. */
    fun glowAt(tech: Technique) = when (tech) {
        Technique.LAST_EMPTY_CELL -> 0.30f
        Technique.NAKED_SINGLE -> 0.46f
        Technique.HIDDEN_SINGLE, Technique.ADVANCED -> 0.40f
        else -> 0.46f // pattern techniques: pattern, elimination, then the digit
    }
    fun glow(t: Float, tech: Technique = Technique.HIDDEN_SINGLE) =
        ramp(t, glowAt(tech), glowAt(tech) + 0.10f) * fadeOut(t)
    fun writeAlpha(t: Float, tech: Technique = Technique.HIDDEN_SINGLE) =
        ramp(t, glowAt(tech) + 0.10f, glowAt(tech) + 0.22f) * fadeOut(t)
    fun writeScale(t: Float, tech: Technique = Technique.HIDDEN_SINGLE) =
        1.5f - 0.5f * ramp(t, glowAt(tech) + 0.10f, glowAt(tech) + 0.22f)

    /** Which of the 3 step lines is lit (0-based). */
    fun step(t: Float, tech: Technique = Technique.HIDDEN_SINGLE) = when {
        tech == Technique.HIDDEN_SINGLE || tech == Technique.ADVANCED -> when { t <= 0.30f -> 0; t <= 0.50f -> 1; else -> 2 }
        tech.isPattern -> when { t <= PATTERN_DONE -> 0; t <= glowAt(tech) -> 1; else -> 2 }
        t <= ONE_BY_ONE_FROM -> 0
        t <= glowAt(tech) -> 1
        else -> 2
    }

    /** Naked single and last empty cell: the marks come one by one from here ... */
    const val ONE_BY_ONE_FROM = 0.08f
    /** ... and the last one is done this long before the target glows. */
    private const val ONE_BY_ONE_REST = 0.02f

    /**
     * Progress 0..1 of mark [k] of [n] appearing in turn (naked single: the rings; last
     * empty cell: the ticks). Each takes one slot between [ONE_BY_ONE_FROM] and the glow.
     */
    fun oneByOne(t: Float, k: Int, n: Int, tech: Technique): Float {
        val slot = (glowAt(tech) - ONE_BY_ONE_REST - ONE_BY_ONE_FROM) / n
        return ramp(t, ONE_BY_ONE_FROM + k * slot, ONE_BY_ONE_FROM + (k + 1) * slot)
    }

    /** The same pop as the hidden single's rings, over a mark's own progress [p]. */
    fun pop(p: Float) = if (p < 0.5f) 0.6f + 0.58f * (p / 0.5f) else 1.18f - 0.18f * ((p - 0.5f) / 0.5f)

    /** Row, column and box of a naked single fade in before the counting starts. */
    fun lightUp(t: Float) = ramp(t, 0f, ONE_BY_ONE_FROM)

    // pattern techniques: the pattern's cells, then the cells that lose a digit, then the finish
    const val PATTERN_FROM = 0.05f
    const val PATTERN_DONE = 0.21f
    const val REMOVE_FROM = 0.24f
    const val REMOVE_DONE = 0.38f

    private fun slot(t: Float, k: Int, n: Int, from: Float, to: Float): Float {
        val w = (to - from) / n
        return ramp(t, from + k * w, from + (k + 1) * w)
    }
    /** Progress 0..1 of pattern cell [k] of [n] being boxed. */
    fun patternIn(t: Float, k: Int, n: Int) = slot(t, k, n, PATTERN_FROM, PATTERN_DONE)
    /** Progress 0..1 of removed cell [k] of [n] getting its struck digit. */
    fun removeIn(t: Float, k: Int, n: Int) = slot(t, k, n, REMOVE_FROM, REMOVE_DONE)
    /** The line the pattern rules digits out of lights up as the removals start. */
    fun lineIn(t: Float) = ramp(t, PATTERN_DONE, REMOVE_FROM)
    /** The unit with one place left is framed (and its other cells crossed) just before the glow. */
    fun finishIn(t: Float) = ramp(t, REMOVE_DONE, 0.46f)
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
        val style = PlayerDigit.copy(fontSize = 24.sp, color = answer.copy(alpha = alpha))
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
    fun rectOf(u: SudokuUnit) = when (u.type) {
        UnitType.BOX -> Rect(Offset(u.index % 3 * 3 * cell, u.index / 3 * 3 * cell), Size(3 * cell, 3 * cell))
        UnitType.ROW -> Rect(Offset(0f, u.index * cell), Size(9 * cell, cell))
        UnitType.COLUMN -> Rect(Offset(u.index * cell, 0f), Size(cell, 9 * cell))
    }
    val tech = hint.technique
    val frame = hint.unit?.let { rectOf(it) } ?: cellRect(hint.target)
    // naked single, from level 2: the cell's row, column and box light up behind it
    if (tech == Technique.NAKED_SINGLE && level >= 2) {
        val a = HintKeys.lightUp(t) * fadeOut(t)
        for (u in listOf(Cells.rowOf(hint.target), Cells.colOf(hint.target), Cells.boxOf(hint.target))) {
            val r = rectOf(u)
            drawRect(colors.hintTintPaper.copy(alpha = colors.hintTintPaper.alpha * a), r.topLeft, r.size)
        }
    }
    drawRect(colors.hintTintPaper, frame.topLeft, frame.size)
    drawRect(halo, frame.topLeft, frame.size, style = Stroke(haloW))
    drawRect(stroke, frame.topLeft, frame.size, style = Stroke(w))
    if (level < 2) return

    if (tech == Technique.NAKED_SINGLE) {
        // the eight digits the cell sees, ringed one by one in digit order
        hint.eliminators.forEachIndexed { k, e ->
            val p = HintKeys.oneByOne(t, k, hint.eliminators.size, tech)
            val a = p * fadeOut(t)
            if (a <= 0f) return@forEachIndexed
            val r = cell * 0.42f * HintKeys.pop(p)
            drawCircle(halo.copy(alpha = halo.alpha * a), r, center(e), style = Stroke(haloW))
            drawCircle(stroke.copy(alpha = a), r, center(e), style = Stroke(w))
        }
        glow(hint.target, HintKeys.glow(t, tech))
        if (level >= 3) write(hint.target, HintKeys.writeAlpha(t, tech), HintKeys.writeScale(t, tech))
        return
    }
    if (tech.isPattern) {
        val small = PlayerDigit.copy(fontSize = 10.sp, color = colors.printedDigit)
        fun digits(ds: List<Int>, at: Offset, alpha: Float, struck: Boolean) {
            val text = ds.joinToString(" ")
            val layout = measurer.measure(text, small.copy(color = colors.printedDigit.copy(alpha = alpha)))
            val tl = at - Offset(layout.size.width / 2f, layout.size.height / 2f)
            drawText(layout, topLeft = tl)
            if (struck) {
                val pad = 2.dp.toPx()
                val a = Offset(tl.x - pad, at.y + layout.size.height * 0.28f)
                val b = Offset(tl.x + layout.size.width + pad, at.y - layout.size.height * 0.28f)
                drawLine(halo.copy(alpha = halo.alpha * alpha), a, b, haloW, StrokeCap.Round)
                drawLine(stroke.copy(alpha = alpha), a, b, w, StrokeCap.Round)
            }
        }
        val fade = fadeOut(t)
        // where the pattern rules digits out (pointing: the line; claiming: the box)
        hint.line?.takeIf { it != hint.unit }?.let { u ->
            val a = HintKeys.lineIn(t) * fade
            val r = rectOf(u)
            drawRect(colors.hintTintPaper.copy(alpha = colors.hintTintPaper.alpha * a), r.topLeft, r.size)
            drawRect(stroke.copy(alpha = a * 0.6f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))
        }
        // the pattern: its cells boxed one by one, with the digits they share
        val up = Offset(0f, cell * 0.2f)
        hint.pattern.forEachIndexed { k, i ->
            val p = HintKeys.patternIn(t, k, hint.pattern.size)
            val a = p * fade
            if (a <= 0f) return@forEachIndexed
            val inset = cell * (0.5f - 0.42f * HintKeys.pop(p))
            val r = cellRect(i).deflate(inset)
            drawRect(halo.copy(alpha = halo.alpha * a), r.topLeft, r.size, style = Stroke(haloW))
            drawRect(stroke.copy(alpha = a), r.topLeft, r.size, style = Stroke(w))
            digits(hint.patternDigits, if (i in hint.removed) center(i) - up else center(i), a, struck = false)
        }
        // the cells that lose a digit: shown struck through (fading as the answer writes over one)
        val writing = if (level >= 3) HintKeys.writeAlpha(t, tech) else 0f
        hint.removed.forEachIndexed { k, i ->
            var a = HintKeys.removeIn(t, k, hint.removed.size) * fade
            if (i == hint.target) a *= 1f - writing
            if (a > 0f) digits(hint.removedDigits, if (i in hint.pattern) center(i) + up else center(i), a, struck = true)
        }
        // the finish: a hidden single's unit is framed and its other empty cells crossed
        val fin = HintKeys.finishIn(t) * fade
        if (fin > 0f) {
            hint.finishUnit?.takeIf { it != hint.unit }?.let { u ->
                val r = rectOf(u)
                drawRect(halo.copy(alpha = halo.alpha * fin), r.topLeft, r.size, style = Stroke(haloW))
                drawRect(stroke.copy(alpha = fin), r.topLeft, r.size, style = Stroke(w))
            }
            for (x in hint.crossedOut) {
                val c = center(x); val h = 5.dp.toPx()
                for ((p, q) in listOf(Offset(-h, -h) to Offset(h, h), Offset(h, -h) to Offset(-h, h))) {
                    drawLine(stroke.copy(alpha = fin), c + p, c + q, 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        glow(hint.target, HintKeys.glow(t, tech))
        if (level >= 3) write(hint.target, writing, HintKeys.writeScale(t, tech))
        return
    }
    if (tech == Technique.LAST_EMPTY_CELL) {
        // the unit's eight digits, ticked off one by one: a short stroke under each
        val filled = hint.unit?.cells.orEmpty().filter { it != hint.target }
        filled.forEachIndexed { k, i ->
            val p = HintKeys.oneByOne(t, k, filled.size, tech)
            val a = p * fadeOut(t)
            if (a <= 0f) return@forEachIndexed
            val c = center(i) + Offset(0f, cell * 0.34f)
            val half = 7.dp.toPx() * p
            drawLine(halo.copy(alpha = halo.alpha * a), c - Offset(half, 0f), c + Offset(half, 0f), haloW, StrokeCap.Round)
            drawLine(stroke.copy(alpha = a), c - Offset(half, 0f), c + Offset(half, 0f), w, StrokeCap.Round)
        }
        glow(hint.target, HintKeys.glow(t, tech))
        if (level >= 3) write(hint.target, HintKeys.writeAlpha(t, tech), HintKeys.writeScale(t, tech))
        return
    }

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
