package com.bay.sudokusolver

import kotlin.math.sqrt

/**
 * Printed vs user ink: the decision rules. Port of ink.py (pure Kotlin, no Android or
 * OpenCV, so it moves to the shared module for iOS). The piece statistics are measured
 * in GridDetector.extractCells; InkClassifierTest checks the verdicts match Python's on
 * pieces exported from real photos (export_ink_cases.py).
 *
 * A piece = one connected blob of ink inside a cell, the same pieces cleanCell sees, so
 * a pen stroke spilling into a printed cell or a note in a corner is removed on its own.
 *
 * Only colour decides (v1): distance from neutral ink (neutralDistance) is
 * at most 2.0 for printed pieces (real photos and colour-cast synthetic ones), at least
 * 42 for red pen at 640 px, and 6+ for synthetic dark-blue pen. Numbers in ink.py.
 * Darkness is carried for reports
 * but does NOT decide: print under glare/blur/low resolution is as light as pen or
 * pencil (see ink.py), so pencil waits for the v1.1 shape model, like black pen.
 */
enum class InkKind { PRINTED, USER, UNSURE }

/**
 * chroma: distance of the piece's median colour from neutral ink in its cell.
 * darkness: (paperY - inkY) / paperY, reported only.
 * counts: cleanCell would keep the piece (not a speck or a grid-line fragment).
 */
data class InkPiece(val chroma: Double, val darkness: Double, val counts: Boolean)

object InkClassifier {
    const val USER_CHROMA = 12.0      // >= this far from neutral ink: coloured pen, removed
    const val UNSURE_CHROMA = 6.0     // in between: kept, cell flagged for the user to confirm
    const val MIN_PRINTED_CELLS = 17  // fewer left after removal: the digits are printed in colour

    /**
     * Colour distance of an ink piece from neutral ink, in (Cr, Cb) = (V, U) offsets from
     * 128. Neutral ink shows anything between paper offset x k (k = inkY/paperY: a colour
     * cast multiplies channels, so solid black is tinted less than the paper) and the
     * paper offset itself (thin strokes at half-resolution chroma show the paper's
     * colour). Distance from that segment. Same as ink.neutral_distance.
     */
    fun neutralDistance(icr: Double, icb: Double, pcr: Double, pcb: Double, kIn: Double): Double {
        val k = kIn.coerceIn(0.0, 1.0)
        val ax = pcr * k; val ay = pcb * k
        val sx = pcr - ax; val sy = pcb - ay
        val dx = icr - ax; val dy = icb - ay
        val ss = sx * sx + sy * sy
        val t = if (ss < 1e-9) 0.0 else ((dx * sx + dy * sy) / ss).coerceIn(0.0, 1.0)
        val ex = dx - t * sx; val ey = dy - t * sy
        return sqrt(ex * ex + ey * ey)
    }

    fun kind(chroma: Double): InkKind = when {
        chroma >= USER_CHROMA -> InkKind.USER
        chroma >= UNSURE_CHROMA -> InkKind.UNSURE
        else -> InkKind.PRINTED
    }

    fun kind(piece: InkPiece): InkKind = kind(piece.chroma)

    /** The cell needs the user's check when a piece that could be its digit is unsure. */
    fun cellUnsure(pieces: List<InkPiece>): Boolean =
        pieces.any { it.counts && kind(it) == InkKind.UNSURE }

    /**
     * Safety net: a valid puzzle has at least 17 givens. If removing user ink leaves
     * fewer, the "user ink" was the puzzle itself (e.g. a magazine printing digits in
     * blue): keep everything.
     */
    fun useRemoval(printedCellsLeft: Int): Boolean = printedCellsLeft >= MIN_PRINTED_CELLS
}
