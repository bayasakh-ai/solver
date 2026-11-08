package com.bay.sudokusolver

import kotlin.math.atan2

/**
 * Which way is up? Mirrors orientation.py. Pure Kotlin (no Android or OpenCV).
 *
 * GridDetector labels corners by SCREEN position, so past 45 degrees the warp is a
 * quarter turn off and digits reach the model sideways (rotate_steep: 0%; digits
 * 99-100% right once turned upright). The reading decides instead:
 *  - new grid: read the warp at each quarter turn, take the best-scoring turn whose
 *    reading is a consistent sudoku ([chooseTurn]);
 *  - following frames: keep each corner on the nearest corner of the last frame
 *    ([bestRoll]), because findGrid relabels the corners again on every frame and
 *    turning the phone past 45 degrees would otherwise flip them mid-scan.
 * Turn k == relabel with [roll] (new i = old (i + k) % 4) == rotate the warp by k
 * quarter turns counterclockwise (np.rot90(warp, k)).
 */
object Orientation {

    /**
     * Indices of 4 corners clockwise around their centre (TL, TR, BR, BL on screen),
     * starting at the one with the smallest x + y. Mirrors grid.order_corners. By angle,
     * so each corner is used once: picking each corner by its own min/max test chose
     * the same corner twice for a grid at ~45 degrees and the warp collapsed.
     */
    fun clockwiseFromTopLeft(xs: DoubleArray, ys: DoubleArray): IntArray {
        val cx = xs.average()
        val cy = ys.average()
        val order = (0 until 4).sortedBy { atan2(ys[it] - cy, xs[it] - cx) }
        val start = (0 until 4).minBy { xs[order[it]] + ys[order[it]] }
        return IntArray(4) { order[(start + it) % 4] }
    }

    /** No digit twice in any row, column or box (0 = empty). */
    fun isConsistent(grid: IntArray): Boolean {
        require(grid.size == 81)
        fun ok(cells: (Int) -> Int): Boolean {
            var seen = 0
            for (k in 0 until 9) {
                val d = grid[cells(k)]
                if (d == 0) continue
                val bit = 1 shl d
                if (seen and bit != 0) return false
                seen = seen or bit
            }
            return true
        }
        for (u in 0 until 9) {
            if (!ok { u * 9 + it }) return false                               // row
            if (!ok { it * 9 + u }) return false                               // column
            val r0 = u / 3 * 3
            val c0 = u % 3 * 3
            if (!ok { (r0 + it / 3) * 9 + c0 + it % 3 }) return false          // box
        }
        return true
    }

    /** Mean top probability over the read cells. */
    fun turnScore(probs: List<FloatArray>): Double =
        if (probs.isEmpty()) 0.0 else probs.sumOf { p -> p.max().toDouble() } / probs.size

    /** Best-scoring consistent turn, else the best score; ties go to the lower k. */
    fun chooseTurn(scores: DoubleArray, consistent: BooleanArray): Int {
        val order = scores.indices.sortedWith(compareBy({ -scores[it] }, { it }))
        return order.firstOrNull { consistent[it] } ?: order.first()
    }

    /** Corners TL, TR, BR, BL relabelled by k quarter turns: new i = old (i + k) % 4. */
    fun <T> roll(corners: List<T>, k: Int): List<T> {
        val n = corners.size
        return List(n) { corners[(it + k).mod(n)] }
    }

    /** Same for flat x0,y0..x3,y3 corners. */
    fun roll(flat: FloatArray, k: Int): FloatArray = FloatArray(8) { i ->
        val corner = (i / 2 + k).mod(4)
        flat[corner * 2 + i % 2]
    }

    /**
     * The k for [roll] that puts each new corner closest to the same-labelled
     * corner of the previous frame (smallest summed squared distance).
     */
    fun bestRoll(new: FloatArray, prev: FloatArray): Int = (0 until 4).minBy { k ->
        val r = roll(new, k)
        (0 until 8).sumOf { i -> ((r[i] - prev[i]) * (r[i] - prev[i])).toDouble() }
    }
}

/**
 * When to stop re-checking a grid's orientation. A turn chosen with a consistent
 * reading locks at once; otherwise (a misread in every turn) it is re-checked on
 * the next sharp frames and locks after [maxTries] anyway, so a hard-to-read grid
 * doesn't pay four classifications on every frame.
 */
class OrientationLock(private val maxTries: Int = 5) {
    var locked = false
        private set
    private var tries = 0

    fun record(consistent: Boolean) {
        tries++
        if (consistent || tries >= maxTries) locked = true
    }

    fun reset() {
        locked = false
        tries = 0
    }
}
