package com.bay.sudokusolver

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Printed or handwritten? Port of hand.py plus grid.cell_shape (pure Kotlin, no Android
 * or OpenCV, so it moves to the shared module for iOS). HandParityTest checks both
 * against Python on real photos (export_hand_cases.py).
 *
 * Pencil and black pen can't be told from print by colour (InkClassifier) or by absolute
 * darkness, so each filled cell is compared with THE SAME PUZZLE'S printed digits: the
 * reference is the median shape of the cells the shape model (hand.tflite,
 * P(handwritten) from the 28x28 cleaned cell) is sure are printed.
 *
 *   thin   stroke   < 0.92 x reference    handwriting is thinner than print
 *   pale   darkness < 0.88 x reference    pencil and ballpoint are lighter than print
 *   odd    height more than 12% off       printed digits of one puzzle share a height
 *
 * HAND = thin AND pale, unless the shape model is sure it is print and the height fits,
 * or the stroke is only just thinner (>= 0.85) and the model is not sure it is
 * handwriting (glare thins and pales print): those are UNSURE. Other mixed signs ->
 * UNSURE; no signs but a model that insists on handwriting -> UNSURE. UNSURE cells stay
 * in the puzzle as read and the Check screen asks. The measurements behind every number
 * are in the hand.py docstring.
 */
enum class HandKind { PRINTED, HAND, UNSURE }

/**
 * stroke: twice the mean depth of the deepest fifth of the ink (px). dark: how far the
 * ink (its 25th percentile) is below the paper around it, as a share of the paper.
 * height: the digit's bounding box (px).
 */
data class CellShape(val stroke: Double, val dark: Double, val height: Double)

object Hand {
    const val STROKE = 0.92        // thin: stroke below this share of the printed reference
    const val NEAR_STROKE = 0.85   // thin, but from this share on the shape model must agree
    const val DARK = 0.88          // pale: darkness below this share of the printed reference
    const val HEIGHT = 0.12        // odd: height more than this share away from the reference
    const val SURE_PRINTED = 0.1   // shape model P(handwritten) below this: a reference cell
    const val SURE_HAND = 0.9      // shape model P(handwritten) from this: it insists on handwriting
    const val MIN_REFERENCE = 5    // fewer sure-printed cells than this: no reference, no split
    const val MIN_PRINTED_CELLS = 17 // fewer printed cells left: don't trust the split

    private fun median(values: List<Double>): Double {
        val v = values.sorted()
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
    }

    /**
     * The puzzle's printed digits as a shape of medians, or null when the shape model is
     * sure of too few cells to say what this puzzle's print looks like.
     * [shapes] / [probs]: the filled cells, same order (probs = P(handwritten)).
     */
    fun reference(shapes: List<CellShape>, probs: List<Double>): CellShape? {
        val sure = shapes.filterIndexed { i, _ -> probs[i] < SURE_PRINTED }
        if (sure.size < MIN_REFERENCE) return null
        return CellShape(median(sure.map { it.stroke }), median(sure.map { it.dark }), median(sure.map { it.height }))
    }

    fun kind(shape: CellShape, ref: CellShape, prob: Double): HandKind {
        val thin = shape.stroke < STROKE * ref.stroke
        val pale = shape.dark < DARK * ref.dark
        val odd = abs(shape.height - ref.height) > HEIGHT * ref.height
        if (thin && pale) {
            if (prob < SURE_PRINTED && !odd) return HandKind.UNSURE
            if (shape.stroke >= NEAR_STROKE * ref.stroke && prob < SURE_HAND) return HandKind.UNSURE
            return HandKind.HAND
        }
        if ((if (thin) 1 else 0) + (if (pale) 1 else 0) + (if (odd) 1 else 0) >= 2) return HandKind.UNSURE
        return if (prob >= SURE_HAND) HandKind.UNSURE else HandKind.PRINTED
    }

    /**
     * Kinds for the filled cells of one grid, or all PRINTED when there is no reference or
     * the split leaves too few printed digits for any sudoku (the measurements don't fit
     * this puzzle).
     */
    fun classify(shapes: List<CellShape>, probs: List<Double>): List<HandKind> {
        if (shapes.isEmpty()) return emptyList()
        val ref = reference(shapes, probs) ?: return List(shapes.size) { HandKind.PRINTED }
        val kinds = shapes.indices.map { kind(shapes[it], ref, probs[it]) }
        if (kinds.count { it != HandKind.HAND } < MIN_PRINTED_CELLS) return List(shapes.size) { HandKind.PRINTED }
        return kinds
    }

    /**
     * Shapes of the 81 cells of a flattened, dark-on-light grid (null = empty cell).
     * [bin]: the binarized grid (GridDetector.binarize: ink != 0), [gray]: the grid itself,
     * both [width] x [width], row by row. Same as grid.cell_shapes.
     */
    fun cellShapes(bin: ByteArray, gray: ByteArray, width: Int): Array<CellShape?> {
        val step = width / 9
        val m = (0.12 * step).toInt()
        val side = step - 2 * m
        val b = BooleanArray(side * side)
        val g = IntArray(side * side)
        return Array(81) { i ->
            val top = i / 9 * step + m
            val left = i % 9 * step + m
            for (y in 0 until side) for (x in 0 until side) {
                val at = (top + y) * width + left + x
                b[y * side + x] = bin[at].toInt() != 0
                g[y * side + x] = gray[at].toInt() and 0xFF
            }
            cellShape(b, g, side)
        }
    }

    /**
     * One cell without its 12% margin ([side] x [side]): [ink] from binarize, [gray]
     * 0..255. Same pieces as cleanCell keeps: not a speck (< 1% of the cell), centre
     * within 35% of the middle; empty unless the largest is >= 3% of the cell.
     */
    fun cellShape(ink: BooleanArray, gray: IntArray, side: Int): CellShape? {
        val total = side * side
        val label = IntArray(total)          // 0 = paper / not visited, else piece number
        val mask = BooleanArray(total)
        val stack = IntArray(total)
        val kept = BooleanArray(total / 2 + 2) // pieces are numbered from 1
        var pieces = 0
        var largest = 0
        var any = false
        for (start in 0 until total) {
            if (!ink[start] || label[start] != 0) continue
            pieces++
            var top = 0
            stack[top++] = start
            label[start] = pieces
            var area = 0
            var sx = 0L
            var sy = 0L
            while (top > 0) {
                val p = stack[--top]
                val px = p % side
                val py = p / side
                area++; sx += px; sy += py
                for (dy in -1..1) for (dx in -1..1) {
                    val x = px + dx
                    val y = py + dy
                    if (x < 0 || y < 0 || x >= side || y >= side) continue
                    val q = y * side + x
                    if (ink[q] && label[q] == 0) { label[q] = pieces; stack[top++] = q }
                }
            }
            if (area < 0.01 * total) continue
            val cx = sx.toDouble() / area
            val cy = sy.toDouble() / area
            if (abs(cx - side / 2.0) > side * 0.35 || abs(cy - side / 2.0) > side * 0.35) continue
            kept[pieces] = true
            largest = max(largest, area)
            any = true
        }
        if (!any || largest < 0.03 * total) return null
        for (p in 0 until total) mask[p] = kept[label[p]]

        // histograms instead of sorting: this runs for 81 cells on every frame that is read
        var y0 = side
        var y1 = -1
        val inkHist = IntArray(256)
        val paperHist = IntArray(256)
        var inkCount = 0
        for (p in 0 until total) {
            if (mask[p]) {
                val y = p / side
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                inkHist[gray[p]]++
                inkCount++
            } else paperHist[gray[p]]++
        }
        val depth = chamfer(mask, side)
        val depths = FloatArray(inkCount)
        var n = 0
        for (p in 0 until total) if (mask[p]) depths[n++] = depth[p]
        depths.sort()
        val take = max(1, inkCount / 5)
        var sum = 0.0
        for (k in inkCount - take until inkCount) sum += depths[k]
        val stroke = 2 * sum / take

        val paperCount = total - inkCount
        val paper = when {
            paperCount == 0 -> 255.0
            paperCount % 2 == 1 -> nth(paperHist, paperCount / 2).toDouble()
            else -> (nth(paperHist, paperCount / 2 - 1) + nth(paperHist, paperCount / 2)) / 2.0
        }
        val pos = 0.25 * (inkCount - 1)
        val lo = floor(pos).toInt()
        val low = nth(inkHist, lo)
        val high = nth(inkHist, min(lo + 1, inkCount - 1))
        val inkY = low + (high - low) * (pos - lo)
        return CellShape(stroke, (paper - inkY) / max(paper, 1.0), (y1 - y0 + 1).toDouble())
    }

    /** The value at index [k] of the sorted pixels a histogram stands for. */
    private fun nth(hist: IntArray, k: Int): Int {
        var seen = 0
        for (v in hist.indices) {
            seen += hist[v]
            if (seen > k) return v
        }
        return 255
    }

    /**
     * Distance of every ink pixel to the nearest paper pixel; outside the cell counts as
     * paper. Two-pass 5x5 chamfer with OpenCV's weights and fixed point, so it matches
     * cv2.distanceTransform(mask padded by 1, DIST_L2, 5).
     */
    private fun chamfer(mask: BooleanArray, side: Int): FloatArray {
        val border = 3                     // 1 px of paper + 2 px the 5x5 mask can reach
        val w = side + 2 * border
        val far = Int.MAX_VALUE shr 2
        val t = IntArray(w * w) { far }
        // the ring of paper around the cell
        for (y in border - 1..side + border) for (x in border - 1..side + border) {
            val inside = y >= border && y < side + border && x >= border && x < side + border
            if (!inside) t[y * w + x] = 0
        }
        for (y in 0 until side) for (x in 0 until side) {
            val j = (y + border) * w + x + border
            if (!mask[y * side + x]) { t[j] = 0; continue }
            var d = t[j - 2 * w - 1] + LONG
            d = min(d, t[j - 2 * w + 1] + LONG)
            d = min(d, t[j - w - 2] + LONG)
            d = min(d, t[j - w - 1] + DIAG)
            d = min(d, t[j - w] + HV)
            d = min(d, t[j - w + 1] + DIAG)
            d = min(d, t[j - w + 2] + LONG)
            d = min(d, t[j - 1] + HV)
            t[j] = d
        }
        val out = FloatArray(side * side)
        for (y in side - 1 downTo 0) for (x in side - 1 downTo 0) {
            val j = (y + border) * w + x + border
            var d = t[j]
            if (d > HV) {
                d = min(d, t[j + 2 * w + 1] + LONG)
                d = min(d, t[j + 2 * w - 1] + LONG)
                d = min(d, t[j + w + 2] + LONG)
                d = min(d, t[j + w + 1] + DIAG)
                d = min(d, t[j + w] + HV)
                d = min(d, t[j + w - 1] + DIAG)
                d = min(d, t[j + w - 2] + LONG)
                d = min(d, t[j + 1] + HV)
                t[j] = d
            }
            out[y * side + x] = d * (1f / 65536)
        }
        return out
    }

    // OpenCV's 5x5 weights for DIST_L2 (1, 1.4, 2.1969) in 16.16 fixed point
    private const val HV = 65536
    private const val DIAG = 91750
    private const val LONG = 143976
}
