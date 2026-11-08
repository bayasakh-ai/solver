package com.bay.sudokusolver

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Port of grid.py. Finds the sudoku's 4 corners in a grayscale image,
 * flattens it, and cuts it into 81 cells ready for the digit model.
 * Corners are always ordered top-left, top-right, bottom-right, bottom-left.
 */
object GridDetector {

    const val WARP_SIZE = 450 // 9 * 50 px cells
    const val DIGIT_SIZE = 28 // model input size

    // ---------- preprocessing ----------

    fun preprocess(gray: Mat): Mat {
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(7.0, 7.0), 0.0)
        val th = Mat()
        Imgproc.adaptiveThreshold(blur, th, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 11, 2.0)
        blur.release()
        return th
    }

    /** Ink -> white, paper -> black. Must match grid.binarize() used in training. */
    fun binarize(gray: Mat): Mat {
        val th = Mat()
        Imgproc.adaptiveThreshold(gray, th, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 21, 10.0)
        return th
    }

    /** Clockwise from the corner nearest the top-left; see Orientation.clockwiseFromTopLeft. */
    fun orderCorners(pts: Array<Point>): Array<Point> {
        val idx = Orientation.clockwiseFromTopLeft(DoubleArray(4) { pts[it].x }, DoubleArray(4) { pts[it].y })
        return Array(4) { pts[idx[it]] }
    }

    // ---------- finding the grid ----------

    /** Fast border method first, then the cell-lattice fallback with edge snapping. */
    fun findGrid(th: Mat, minAreaRatio: Double = 0.1): Array<Point>? {
        findByBorder(th, minAreaRatio)?.let { return it }
        val rough = findByCells(th) ?: return null
        return refineEdges(th, orderCorners(rough))
    }

    private fun findByBorder(th: Mat, minAreaRatio: Double): Array<Point>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(th, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        val imageArea = th.rows().toDouble() * th.cols()
        try {
            for (c in contours.sortedByDescending { Imgproc.contourArea(it) }.take(5)) {
                if (Imgproc.contourArea(c) < minAreaRatio * imageArea) break
                val c2f = MatOfPoint2f(*c.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
                val pts = approx.toArray()
                c2f.release(); approx.release()
                if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                    return orderCorners(pts)
                }
            }
        } finally {
            contours.forEach { it.release() }
        }
        return null
    }

    /** Biggest group of same-sized square holes = the puzzle's cells. */
    private fun findByCells(th: Mat, minCells: Int = 40): Array<Point>? {
        val closed = Mat()
        Imgproc.dilate(th, closed, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        closed.release(); hierarchy.release()

        val imageArea = th.rows().toDouble() * th.cols()
        val areas = ArrayList<Double>()
        val centers = ArrayList<Point>()
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < 0.0004 * imageArea || area > 0.05 * imageArea) continue
            val c2f = MatOfPoint2f(*c.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.1 * Imgproc.arcLength(c2f, true), true)
            val corners = approx.total()
            c2f.release(); approx.release()
            val r: Rect = Imgproc.boundingRect(c)
            val aspect = r.width.toDouble() / r.height
            if (corners == 4L && aspect > 0.6 && aspect < 1.6 && area > 0.6 * r.width * r.height) {
                val pts = c.toArray()
                areas.add(area)
                centers.add(Point(pts.sumOf { it.x } / pts.size, pts.sumOf { it.y } / pts.size))
            }
        }
        contours.forEach { it.release() }
        if (areas.size < minCells) return null

        // area with the most similar-sized squares around it = the cell size
        val counts = areas.map { a -> areas.count { it > 0.6 * a && it < 1.6 * a } }
        val ref = areas[counts.indices.maxBy { counts[it] }]
        var cellCenters = centers.filterIndexed { i, _ -> areas[i] > 0.6 * ref && areas[i] < 1.6 * ref }
        if (cellCenters.size < minCells) return null

        // drop stray squares far from the main cluster
        val medX = cellCenters.map { it.x }.sorted()[cellCenters.size / 2]
        val medY = cellCenters.map { it.y }.sorted()[cellCenters.size / 2]
        val cellSize = sqrt(ref)
        cellCenters = cellCenters.filter { hypot(it.x - medX, it.y - medY) < 7 * cellSize }
        if (cellCenters.size < minCells) return null
        return fitLattice(cellCenters, cellSize)
    }

    /** Walk neighbour to neighbour to give each cell its (column,row), then fit a homography. */
    private fun fitLattice(pts: List<Point>, s: Double): Array<Point>? {
        val medX = pts.map { it.x }.sorted()[pts.size / 2]
        val medY = pts.map { it.y }.sorted()[pts.size / 2]
        val start = pts.indices.minBy { hypot(pts[it].x - medX, pts[it].y - medY) }
        val index = HashMap<Int, Pair<Int, Int>>()
        index[start] = 0 to 0
        val queue = ArrayDeque<Int>().apply { add(start) }
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            val (i, j) = index.getValue(k)
            for (m in pts.indices) {
                if (m in index) continue
                val dx = pts[m].x - pts[k].x
                val dy = pts[m].y - pts[k].y
                val d = hypot(dx, dy)
                if (d <= 0.6 * s || d >= 1.3 * s) continue
                index[m] = if (abs(dx) > abs(dy)) (i + sign(dx).toInt()) to j else i to (j + sign(dy).toInt())
                queue.add(m)
            }
        }
        val keys = index.keys.toList()
        val minI = keys.minOf { index.getValue(it).first }
        val minJ = keys.minOf { index.getValue(it).second }
        val maxI = keys.maxOf { index.getValue(it).first } - minI
        val maxJ = keys.maxOf { index.getValue(it).second } - minJ
        if (maxI != 8 || maxJ != 8) return null // couldn't see a full 9x9 span

        val lattice = MatOfPoint2f(*keys.map { k ->
            val (i, j) = index.getValue(k)
            Point(i - minI + 0.5, j - minJ + 0.5)
        }.toTypedArray())
        val image = MatOfPoint2f(*keys.map { pts[it] }.toTypedArray())
        val h = Calib3d.findHomography(lattice, image, Calib3d.RANSAC, 0.3 * s)
        lattice.release(); image.release()
        if (h.empty()) return null
        val corners = MatOfPoint2f(Point(0.0, 0.0), Point(9.0, 0.0), Point(9.0, 9.0), Point(0.0, 9.0))
        val out = MatOfPoint2f()
        Core.perspectiveTransform(corners, out, h)
        val result = out.toArray()
        corners.release(); out.release(); h.release()
        return result
    }

    /** Snap a rough quad onto the real border lines (strongest line near each edge). */
    private fun refineEdges(th: Mat, rough: Array<Point>, pad: Int = 40, band: Int = 30): Array<Point> {
        val s = WARP_SIZE
        val full = s + 2 * pad
        val p = pad.toDouble()
        val dst = MatOfPoint2f(Point(p, p), Point(p + s, p), Point(p + s, p + s), Point(p, p + s))
        val src = MatOfPoint2f(*rough)
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val w = Mat()
        Imgproc.warpPerspective(th, w, m, Size(full.toDouble(), full.toDouble()))
        src.release(); dst.release()

        val halves = listOf(pad + s / 8 to pad + s / 2, pad + s / 2 to pad + 7 * s / 8)

        fun strongest(profile: IntArray, nominal: Int): Int {
            var best = nominal - band
            for (x in nominal - band until nominal + band) if (profile[x] > profile[best]) best = x
            return best
        }

        fun profile(region: Mat, dim: Int): IntArray {
            val sum = Mat()
            Core.reduce(region, sum, dim, Core.REDUCE_SUM, CvType.CV_32S)
            val out = IntArray(sum.total().toInt())
            sum.get(0, 0, out)
            sum.release()
            return out
        }

        val lines = HashMap<String, List<Point>>()
        for ((name, nominal) in listOf("left" to pad, "right" to pad + s, "top" to pad, "bottom" to pad + s)) {
            lines[name] = halves.map { (a, b) ->
                val mid = (a + b) / 2.0
                if (name == "left" || name == "right") {
                    val region = w.submat(a, b, 0, full)          // rows a..b, sum per column
                    Point(strongest(profile(region, 0), nominal).toDouble(), mid)
                } else {
                    val region = w.submat(0, full, a, b)          // cols a..b, sum per row
                    Point(mid, strongest(profile(region, 1), nominal).toDouble())
                }
            }
        }
        w.release()

        fun intersect(l1: List<Point>, l2: List<Point>): Point {
            val (x1, y1) = l1[0].x to l1[0].y
            val (x2, y2) = l1[1].x to l1[1].y
            val (x3, y3) = l2[0].x to l2[0].y
            val (x4, y4) = l2[1].x to l2[1].y
            val d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            val a = x1 * y2 - y1 * x2
            val b = x3 * y4 - y3 * x4
            return Point((a * (x3 - x4) - (x1 - x2) * b) / d, (a * (y3 - y4) - (y1 - y2) * b) / d)
        }

        val quad = MatOfPoint2f(
            intersect(lines.getValue("top"), lines.getValue("left")),
            intersect(lines.getValue("top"), lines.getValue("right")),
            intersect(lines.getValue("bottom"), lines.getValue("right")),
            intersect(lines.getValue("bottom"), lines.getValue("left")),
        )
        val inv = m.inv()
        val out = MatOfPoint2f()
        Core.perspectiveTransform(quad, out, inv)
        val result = out.toArray()
        quad.release(); out.release(); inv.release(); m.release()
        return result
    }

    // ---------- flattening and cells ----------

    /** Returns the flattened 450x450 grid and the transform (image -> flat). */
    fun warp(gray: Mat, corners: Array<Point>): Pair<Mat, Mat> {
        val s = (WARP_SIZE - 1).toDouble()
        val src = MatOfPoint2f(*corners)
        val dst = MatOfPoint2f(Point(0.0, 0.0), Point(s, 0.0), Point(s, s), Point(0.0, s))
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(gray, warped, m, Size(WARP_SIZE.toDouble(), WARP_SIZE.toDouble()))
        src.release(); dst.release()
        return warped to m
    }

    /**
     * What ink removal did on one grid. applied = false: the safety net kept everything.
     * removed / unsure: per cell, row by row (unsure = the Check screen should ask).
     */
    class InkResult(val applied: Boolean, val removed: BooleanArray, val unsure: BooleanArray)

    class Cells(val cells: List<ByteArray?>, val ink: InkResult?)

    /**
     * Warp a half-resolution chroma plane (U or V of YUV_420_888, already upright) with
     * the grid's full-resolution corners, giving 450x450 like the gray warp.
     * Same as grid.chroma_planes.
     */
    fun warpHalf(plane: Mat, corners: Array<Point>): Mat =
        warp(plane, Array(4) { Point(corners[it].x / 2, corners[it].y / 2) }).let { (w, m) -> m.release(); w }

    /**
     * 81 cells, row by row: a 28x28 digit image (ByteArray(784)) or null if empty.
     * With warped chroma planes (u, v from warpHalf), user ink (coloured pen) is removed
     * from the binarized cells BEFORE cleanCell, so binarize/cleanCell stay exactly as in
     * training. Same as grid.extract_cells; decisions in InkClassifier.
     */
    fun extractCells(warped: Mat, chroma: Pair<Mat, Mat>? = null): Cells {
        val th = binarize(warped)
        val step = WARP_SIZE / 9
        fun cell(m: Mat, i: Int) = m.submat(i / 9 * step, (i / 9 + 1) * step, i % 9 * step, (i % 9 + 1) * step)
        fun clean(m: Mat) = (0 until 81).map { cleanCell(cell(m, it)) }

        if (chroma == null) return Cells(clean(th), null).also { th.release() }
        val original = th.clone() // the safety net may need the cells without removal
        val removed = BooleanArray(81)
        val unsure = BooleanArray(81)
        for (i in 0 until 81) {
            val pieces = removeUserInk(cell(th, i), cell(warped, i), cell(chroma.first, i), cell(chroma.second, i))
            removed[i] = pieces.any { InkClassifier.kind(it) == InkKind.USER }
            unsure[i] = InkClassifier.cellUnsure(pieces)
        }
        var cells = clean(th)
        val applied = InkClassifier.useRemoval(cells.count { it != null })
        if (!applied) {
            cells = clean(original)
            removed.fill(false)
            unsure.fill(false)
        }
        th.release(); original.release()
        return Cells(cells, InkResult(applied, removed, unsure))
    }

    /**
     * Measures every ink piece of one cell (same crop and components as cleanCell) and
     * erases the USER pieces from [bin] in place. Same as grid.cell_pieces.
     * Chroma = how far the piece's colour is from any neutral ink (InkClassifier.neutralDistance).
     */
    private fun removeUserInk(bin: Mat, gray: Mat, u: Mat, v: Mat): List<InkPiece> {
        val h = bin.rows()
        val m = (0.12 * h).toInt()
        fun crop(x: Mat) = x.submat(m, h - m, m, x.cols() - m)
        val inner = crop(bin)
        val ih = inner.rows()
        val iw = inner.cols()
        val labels = Mat()
        val stats = Mat()
        val cents = Mat()
        val n = Imgproc.connectedComponentsWithStats(inner, labels, stats, cents, 8, CvType.CV_32S)
        val lab = IntArray(ih * iw).also { labels.get(0, 0, it) }
        labels.release()
        fun bytes(x: Mat) = ByteArray(ih * iw).also { crop(x).get(0, 0, it) }
        val g = bytes(gray)
        val cu = bytes(u)
        val cv = bytes(v)

        // per label: histograms of Y, U, V -> lower medians without sorting (label 0 = paper)
        val hist = Array(n) { Array(3) { IntArray(256) } }
        for (k in lab.indices) {
            val hk = hist[lab[k]]
            hk[0][g[k].toInt() and 0xFF]++
            hk[1][cu[k].toInt() and 0xFF]++
            hk[2][cv[k].toInt() and 0xFF]++
        }
        val paperCount = hist[0][0].sum()
        val paperY = if (paperCount > 0) lowerMedian(hist[0][0]) else 255.0
        val paperU = if (paperCount > 0) lowerMedian(hist[0][1]) else 128.0
        val paperV = if (paperCount > 0) lowerMedian(hist[0][2]) else 128.0

        val pieces = ArrayList<InkPiece>()
        val user = BooleanArray(n)
        for (i in 1 until n) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (area < 0.01 * ih * iw) continue // noise speck: cleanCell ignores it anyway
            val darkness = (paperY - lowerMedian(hist[i][0])) / max(paperY, 1.0)
            val chroma = InkClassifier.neutralDistance(lowerMedian(hist[i][2]) - 128, lowerMedian(hist[i][1]) - 128,
                paperV - 128, paperU - 128, 1.0 - darkness)
            val cx = cents.get(i, 0)[0]
            val cy = cents.get(i, 1)[0]
            val counts = abs(cx - iw / 2.0) <= iw * 0.35 && abs(cy - ih / 2.0) <= ih * 0.35
            val piece = InkPiece(chroma, darkness, counts)
            pieces.add(piece)
            user[i] = InkClassifier.kind(piece) == InkKind.USER
        }
        stats.release(); cents.release()
        if (user.any { it }) {
            val px = ByteArray(ih * iw).also { inner.get(0, 0, it) }
            for (k in lab.indices) if (user[lab[k]]) px[k] = 0
            inner.put(0, 0, px) // writes through to the binarized grid
        }
        return pieces
    }

    /** Element at index (count-1)/2 of the sorted values = numpy's lower median. */
    private fun lowerMedian(hist: IntArray): Double {
        val target = (hist.sum() - 1) / 2
        var seen = 0
        for (value in hist.indices) {
            seen += hist[value]
            if (seen > target) return value.toDouble()
        }
        return 0.0
    }

    /** Same logic as grid.clean_cell: keep every piece of the digit, centre it in 28x28. */
    fun cleanCell(cell: Mat): ByteArray? {
        val h = cell.rows()
        val m = (0.12 * h).toInt()
        val inner = cell.submat(m, h - m, m, cell.cols() - m)
        val ih = inner.rows()
        val iw = inner.cols()
        val labels = Mat()
        val stats = Mat()
        val cents = Mat()
        val n = Imgproc.connectedComponentsWithStats(inner, labels, stats, cents, 8, CvType.CV_32S)

        val keep = HashSet<Int>()
        var largest = 0
        for (i in 1 until n) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            val cx = cents.get(i, 0)[0]
            val cy = cents.get(i, 1)[0]
            if (area < 0.01 * ih * iw) continue                                   // noise speck
            if (abs(cx - iw / 2.0) > iw * 0.35 || abs(cy - ih / 2.0) > ih * 0.35) continue // grid-line bit
            keep.add(i)
            largest = max(largest, area)
        }
        stats.release(); cents.release()
        if (largest < 0.03 * ih * iw) { labels.release(); return null } // empty cell

        val lab = IntArray(ih * iw)
        labels.get(0, 0, lab)
        labels.release()
        var x0 = iw; var y0 = ih; var x1 = -1; var y1 = -1
        val mask = ByteArray(ih * iw)
        for (y in 0 until ih) for (x in 0 until iw) {
            if (lab[y * iw + x] in keep) {
                mask[y * iw + x] = 255.toByte()
                if (x < x0) x0 = x; if (x > x1) x1 = x
                if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        val maskMat = Mat(ih, iw, CvType.CV_8UC1)
        maskMat.put(0, 0, mask)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        val digit = maskMat.submat(y0, y1 + 1, x0, x1 + 1)

        val target = DIGIT_SIZE - 8 // 4 px padding like MNIST
        val scale = target.toDouble() / max(bw, bh)
        val nw = max(1, (bw * scale).toInt())
        val nh = max(1, (bh * scale).toInt())
        val resized = Mat()
        Imgproc.resize(digit, resized, Size(nw.toDouble(), nh.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        val canvas = Mat.zeros(DIGIT_SIZE, DIGIT_SIZE, CvType.CV_8UC1)
        val ox = (DIGIT_SIZE - nw) / 2
        val oy = (DIGIT_SIZE - nh) / 2
        resized.copyTo(canvas.submat(oy, oy + nh, ox, ox + nw))

        val out = ByteArray(DIGIT_SIZE * DIGIT_SIZE)
        canvas.get(0, 0, out)
        maskMat.release(); resized.release(); canvas.release()
        return out
    }

    private fun hypot(x: Double, y: Double) = sqrt(x * x + y * y)
}
