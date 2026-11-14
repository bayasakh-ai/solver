package com.bay.sudokusolver

/**
 * Is a found quad really a sudoku? The grid finder returns any four-cornered shape
 * (a book cover, a title block, a keyboard); this asks the flattened image for the
 * grid's inner box lines. Pure Kotlin (no Android or OpenCV imports).
 * Mirrors grid.box_line_score; GridCheckParityTest compares the two on exported cases.
 */
object GridCheck {

    /** 3 of the 4 inner box lines must cover at least this share of their length. */
    const val BOX_LINES = 0.93

    /** [sameQuad]: every corner within this share of the grid's side of an old corner. */
    const val SAME_QUAD = 0.15

    /**
     * The second weakest of the 4 inner box lines (1/3 and 2/3 across, both directions)
     * of a [size] x [size] flattened quad, so 3 of the 4 must show (glare or a finger may
     * hide one). [ink]: GridDetector.lineInk, != 0 is ink, row by row. A line's strength
     * is the best share of its length that is ink, among the columns (rows) within [tol]
     * px of where it should be; ink within [widen] px sideways counts, because lines are
     * not perfectly straight after the warp.
     * Only the thick box lines: thin cell lines of some screen sudokus are too faint, and
     * small grids lose them to blur.
     * Measured (2026-11-14, 640 px frames): 270 solved grids (photos + synth) >= 0.976;
     * 446 more real grids at 30-100% size with blur sigma 0-2: 23 below 0.93; 376 false
     * quads from no-grid photos (book cover, title, keyboard; jittered, shrunk, blurred)
     * <= 0.77 but one at 0.907. The first version (binarize C=10, weakest line >= 0.6)
     * rejected 147 of those 716 real grids: on the phone the grid was found late and
     * only up close.
     */
    fun boxLineScore(ink: ByteArray, size: Int, tol: Int = 9, widen: Int = 5): Double {
        val half = widen / 2
        val lines = ArrayList<Double>(4)
        for (vertical in booleanArrayOf(true, false)) {
            for (i in intArrayOf(3, 6)) {
                val centre = i * size / 9
                var best = 0
                for (line in centre - tol..centre + tol) {
                    var count = 0
                    for (along in 0 until size) {
                        for (d in -half..half) {
                            val across = line + d
                            val px = if (vertical) ink[along * size + across] else ink[across * size + along]
                            if (px != 0.toByte()) { count++; break }
                        }
                    }
                    if (count > best) best = count
                }
                lines.add(best.toDouble() / size)
            }
        }
        return lines.sorted()[1]
    }

    fun looksLikeGrid(ink: ByteArray, size: Int): Boolean = boxLineScore(ink, size) >= BOX_LINES

    /**
     * Is [found] the grid already on screen ([last])? Corners x0,y0..x3,y3 in any order.
     * The live scanner judges a quad once, when it appears; after that the same quad is
     * kept without asking again, so a blurred frame in between (a moving hand) doesn't
     * drop the outline and restart the vote. A different quad is judged afresh.
     */
    fun sameQuad(found: FloatArray, last: FloatArray): Boolean {
        val limit = SAME_QUAD * kotlin.math.sqrt(ScanState.gridShare(last, 1, 1))
        for (i in 0 until 4) {
            var nearest = Double.MAX_VALUE
            for (j in 0 until 4) {
                val dx = (found[2 * i] - last[2 * j]).toDouble()
                val dy = (found[2 * i + 1] - last[2 * j + 1]).toDouble()
                nearest = minOf(nearest, kotlin.math.sqrt(dx * dx + dy * dy))
            }
            if (nearest > limit) return false
        }
        return true
    }
}
