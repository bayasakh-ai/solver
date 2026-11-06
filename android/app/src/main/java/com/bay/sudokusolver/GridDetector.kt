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

    fun orderCorners(pts: Array<Point>): Array<Point> {
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.minBy { it.y - it.x }
        val bl = pts.maxBy { it.y - it.x }
        return arrayOf(tl, tr, br, bl)
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

    /** 81 cells, row by row: a 28x28 digit image (ByteArray(784)) or null if empty. */
    fun extractCells(warped: Mat): List<ByteArray?> {
        val th = binarize(warped)
        val step = WARP_SIZE / 9
        val cells = ArrayList<ByteArray?>(81)
        for (r in 0 until 9) for (c in 0 until 9) {
            val cell = th.submat(r * step, (r + 1) * step, c * step, (c + 1) * step)
            cells.add(cleanCell(cell))
        }
        th.release()
        return cells
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
