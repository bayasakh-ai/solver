package com.bay.sudokusolver

/**
 * Dark mode: is this flattened grid light digits on a dark background?
 * Port of grid.py's looks_dark(). Pure Kotlin (no OpenCV) so it is JVM-testable
 * and shared with a future iOS port.
 *
 * Why a per-cell vote instead of one brightness number for the whole image:
 * a hard shadow puts half a paper puzzle at brightness ~70, so "median < 110 means
 * dark" got 12 of 20 synthetic shadow photos wrong. Inside one 50x50 cell the light
 * is nearly even, so each cell can tell on its own whether its ink is the dark tail
 * of its pixels (normal paper) or the bright tail (dark mode). Measured on synthetic
 * photos: 0 wrong out of 164, all 10 conditions, including every shadow photo.
 */
object Polarity {
    private const val CELLS = 9
    private const val MARGIN = 5    // skip the grid lines at the cell's edge
    private const val MIN_SPREAD = 20 // flatter than this = empty cell, no evidence

    /**
     * [pixels]: the warped grid, row-major grayscale, [size] x [size] (WARP_SIZE = 450).
     * Returns true when more cells show bright ink on dark than dark ink on light.
     */
    fun looksDark(pixels: ByteArray, size: Int): Boolean {
        val (light, dark) = cellVotes(pixels, size)
        return dark > light
    }

    /**
     * (cells with dark ink on light, cells with light ink on dark); flat cells don't vote.
     * Exposed so PolarityParityTest can compare exact counts with grid.py's cell_votes.
     */
    fun cellVotes(pixels: ByteArray, size: Int): Pair<Int, Int> {
        require(pixels.size == size * size) { "expected ${size * size} pixels, got ${pixels.size}" }
        val step = size / CELLS
        val hist = IntArray(256)
        var light = 0
        var dark = 0
        for (r in 0 until CELLS) for (c in 0 until CELLS) {
            hist.fill(0)
            var n = 0
            for (y in r * step + MARGIN until (r + 1) * step - MARGIN) {
                val row = y * size
                for (x in c * step + MARGIN until (c + 1) * step - MARGIN) {
                    hist[pixels[row + x].toInt() and 0xFF]++
                    n++
                }
            }
            val p2 = percentile(hist, n, 0.02)
            val med = percentile(hist, n, 0.50)
            val p98 = percentile(hist, n, 0.98)
            val below = med - p2  // how far the dark tail reaches
            val above = p98 - med // how far the bright tail reaches
            if (maxOf(below, above) < MIN_SPREAD) continue
            if (below > above) light++ else dark++
        }
        return light to dark
    }

    /** Same rule as numpy's percentile(method="lower"): the sorted value at floor(q*(n-1)). */
    private fun percentile(hist: IntArray, n: Int, q: Double): Int {
        val target = q * (n - 1)
        var seen = 0
        for (v in 0 until 256) {
            seen += hist[v]
            if (seen > target) return v
        }
        return 255
    }
}

/**
 * Which polarity the live analyzer looks for the grid in. Python's process() simply
 * runs the whole pipeline twice when needed; live we can't afford two grid searches
 * per frame (~5 ms each), so we search once per frame:
 * - use the polarity that matched the last grid found;
 * - after a miss, try the other one on the next frame (dark-mode grids are found
 *   one frame later at worst, and a frame with no grid still costs one search);
 * - once a grid is found, the per-cell vote says what the page really is, and the
 *   next frames search in that polarity (the right one gives the cleanest corners).
 * Cells are always read light-background-up, whatever polarity found the grid, so
 * readings stay comparable and the vote across frames is never mixed up.
 */
class GridPolarity {
    /** true = look for the grid in the inverted frame. */
    var searchInverted = false
        private set

    fun onMiss() {
        searchInverted = !searchInverted
    }

    /**
     * [searchedInverted]: the polarity the grid was found in this frame.
     * [warpedLooksDark]: Polarity.looksDark on the grid warped from that same image.
     * Returns true when the warped cells must be inverted before reading.
     */
    fun onFound(searchedInverted: Boolean, warpedLooksDark: Boolean): Boolean {
        searchInverted = searchedInverted != warpedLooksDark // page is dark-mode
        return warpedLooksDark
    }
}
