package com.bay.sudokusolver.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grid outline states. Never colour alone: each state has its own line pattern,
 * so the state still reads in greyscale or with any colour vision.
 *
 *   Reading      dashed  amber
 *   Hold steady  dotted  amber
 *   Solved       solid   answer palette, bright step
 *   Needs review double  coral
 */
enum class GridOutline { READING, HOLD_STEADY, SOLVED, NEEDS_REVIEW }

enum class LinePattern { SOLID, DASHED, DOTTED, DOUBLE }

@Immutable
data class OutlineStyle(
    val color: Color,
    val pattern: LinePattern,
    /** Total width; for DOUBLE this spans both lines and the gap between (thirds). */
    val width: Dp,
    /** Dark halo on both sides so the outline holds on bright paper and glare. */
    val halo: Color = Color(0x66000000),
    val haloWidth: Dp = 3.dp,
) {
    /** Path effect for one stroke of [strokePx] width (the full width, or a third for DOUBLE). */
    fun pathEffect(strokePx: Float): PathEffect? = when (pattern) {
        LinePattern.SOLID, LinePattern.DOUBLE -> null
        LinePattern.DASHED -> PathEffect.dashPathEffect(floatArrayOf(3f * strokePx, 2f * strokePx))
        // Zero-length dashes with round caps draw dots one stroke apart.
        LinePattern.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.01f, 2f * strokePx))
    }

    val cap: StrokeCap get() = if (pattern == LinePattern.DOTTED) StrokeCap.Round else StrokeCap.Butt
}

/** Outline styles on the camera view, from the v2 design (5 px dashed, 9 px double on a 450 px grid). */
fun SudokuColors.outline(state: GridOutline): OutlineStyle = when (state) {
    GridOutline.READING -> OutlineStyle(readingCamera, LinePattern.DASHED, 4.dp)
    GridOutline.HOLD_STEADY -> OutlineStyle(readingCamera, LinePattern.DOTTED, 4.dp)
    GridOutline.SOLVED -> OutlineStyle(solvedCamera, LinePattern.SOLID, 4.dp)
    GridOutline.NEEDS_REVIEW -> OutlineStyle(warningCamera, LinePattern.DOUBLE, 7.dp)
}
