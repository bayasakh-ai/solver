package com.bay.sudokusolver

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/** What one analysed frame produced. Coordinates are in the upright frame's pixels. */
data class FrameResult(
    val corners: FloatArray?,      // x0,y0, x1,y1, x2,y2, x3,y3 = TL, TR, BR, BL (null = no grid)
    val frameWidth: Int,           // upright frame size, used to map onto the screen
    val frameHeight: Int,
    val warped: Bitmap?,           // flattened grid preview (debug thumbnail)
    val sharpness: Double,         // measured inside the flattened grid only
    val digits: Int,               // filled cells found
    val processMs: Long,
    val fps: Double,
    val message: String,           // what the user should know right now
    val puzzle: IntArray? = null,  // givens used for solving (after any fixes)
    val solution: IntArray? = null,
) {
    val isSharp get() = sharpness > FrameAnalyzer.SHARP_ENOUGH
}

/**
 * Live pipeline: camera frame -> upright grayscale -> find grid -> flatten ->
 * read digits (sharp frames only) -> vote across frames -> self-correct + solve once
 * -> report the solution so the overlay can draw it on every frame.
 */
class FrameAnalyzer(
    private val classifier: DigitClassifier,
    private val onResult: (FrameResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val tracker = PuzzleTracker()
    private val polarity = GridPolarity()
    private var solved: SolveResult? = null
    private var solvedFrom: IntArray? = null // the consensus reading that was solved

    private var frames = 0
    private var windowStart = System.nanoTime()
    private var fps = 0.0
    private var smoothed: FloatArray? = null
    private var lastFound: FrameResult? = null
    private var lastFoundAt = 0L

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val start = System.nanoTime()
            val raw = yPlaneToMat(frame)
            val gray = rotateUpright(raw, frame.imageInfo.rotationDegrees)
            if (gray !== raw) raw.release()

            // dark mode: search in the polarity that worked last (one search per frame)
            val searchedInverted = polarity.searchInverted
            val source = if (searchedInverted) Mat().also { Core.bitwise_not(gray, it) } else gray
            val th = GridDetector.preprocess(source)
            val corners = GridDetector.findGrid(th)
            th.release()
            if (corners == null) polarity.onMiss()

            frames++
            val now = System.nanoTime()
            if (now - windowStart >= 1_000_000_000L) {
                fps = frames * 1e9 / (now - windowStart)
                frames = 0
                windowStart = now
            }

            val result: FrameResult
            if (corners != null) {
                val (warped, m) = GridDetector.warp(source, corners)
                m.release()
                val pixels = ByteArray(warped.rows() * warped.cols())
                warped.get(0, 0, pixels)
                // the cells decide: digits must be dark-on-light for binarize + the model
                if (polarity.onFound(searchedInverted, Polarity.looksDark(pixels, warped.cols()))) {
                    Core.bitwise_not(warped, warped)
                }
                val sharp = sharpness(warped) // Laplacian variance: same either polarity
                val cells = GridDetector.extractCells(warped)
                val digits = cells.count { it != null }
                val thumb = toBitmap(warped, 225)
                warped.release()

                if (sharp > SHARP_ENOUGH) read(cells)
                val message = updateSolution(sharp > SHARP_ENOUGH)

                val flat = FloatArray(8) { i -> (if (i % 2 == 0) corners[i / 2].x else corners[i / 2].y).toFloat() }
                smoothed = smooth(flat, smoothed, gray.cols())
                val s = solved
                result = FrameResult(smoothed!!.copyOf(), gray.cols(), gray.rows(), thumb, sharp, digits,
                    (System.nanoTime() - start) / 1_000_000, fps, message,
                    s?.puzzle, s?.solution)
                lastFound = result
                lastFoundAt = now
            } else if (lastFound != null && now - lastFoundAt < HOLD_NANOS) {
                // grid missed for a moment (hand shake): keep showing it to avoid flicker
                result = lastFound!!.copy(processMs = (now - start) / 1_000_000, fps = fps)
            } else {
                // grid gone: forget this puzzle so the next one starts fresh
                smoothed = null
                lastFound = null
                tracker.reset()
                solved = null
                solvedFrom = null
                result = FrameResult(null, gray.cols(), gray.rows(), null, 0.0, 0,
                    (now - start) / 1_000_000, fps, "Point the camera at a sudoku")
            }
            if (source !== gray) source.release()
            gray.release()
            onResult(result)
        }
    }

    /** Classify the filled cells of one sharp frame and add the reading to the vote. */
    private fun read(cells: List<ByteArray?>) {
        val filled = cells.indices.filter { cells[it] != null }
        val grid = IntArray(81)
        val probs = arrayOfNulls<FloatArray>(81)
        if (filled.isNotEmpty()) {
            val out = classifier.classify(filled.map { cells[it]!! })
            filled.forEachIndexed { k, i ->
                probs[i] = out[k]
                grid[i] = out[k].indices.maxBy { out[k][it] } + 1
            }
        }
        tracker.add(grid, probs)
    }

    /** Solve when the vote produces a new stable reading; returns the status message. */
    private fun updateSolution(isSharp: Boolean): String {
        val consensus = tracker.consensus()
        if (consensus != null && !consensus.first.contentEquals(solvedFrom)) {
            solved = SelfCorrect.solve(consensus.first, consensus.second)
            solvedFrom = consensus.first
        }
        val s = solved
        return when {
            s?.solution != null && s.fixes.isNotEmpty() -> "Solved ✓ (fixed ${s.fixes.size} misread digit${if (s.fixes.size > 1) "s" else ""})"
            s?.solution != null -> "Solved ✓"
            s != null && !Solver.isPlausible(s.puzzle) -> "Too few digits found — move closer"
            s != null -> "Can't solve this reading — try better light or another angle"
            !isSharp -> "Hold steady…"
            else -> "Reading digits… ${tracker.frames}/${tracker.windowSize}"
        }
    }

    /** Average with the previous corners when they're close, so the outline doesn't jitter. */
    private fun smooth(new: FloatArray, prev: FloatArray?, width: Int): FloatArray {
        if (prev == null) return new
        val jump = new.indices.maxOf { abs(new[it] - prev[it]) }
        if (jump > 0.05f * width) return new // grid moved a lot: follow immediately
        return FloatArray(8) { 0.5f * new[it] + 0.5f * prev[it] }
    }

    companion object {
        /** Starting guess for sharpness inside the flattened grid; tune on your phone. */
        const val SHARP_ENOUGH = 100.0
        private const val HOLD_NANOS = 300_000_000L

        /** The Y (brightness) plane of a YUV frame is already a grayscale image. */
        fun yPlaneToMat(image: ImageProxy): Mat {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val w = image.width
            val h = image.height
            val stride = plane.rowStride
            val mat = Mat(h, w, CvType.CV_8UC1)
            val row = ByteArray(w)
            for (y in 0 until h) { // rows can be padded (stride > width), so copy row by row
                buffer.position(y * stride)
                buffer.get(row, 0, w)
                mat.put(y, 0, row)
            }
            return mat
        }

        /** Camera frames arrive sideways; rotate so they match what the user sees. */
        fun rotateUpright(m: Mat, degrees: Int): Mat {
            val code = when (degrees) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> return m
            }
            return Mat().also { Core.rotate(m, it, code) }
        }

        fun sharpness(gray: Mat): Double {
            val lap = Mat()
            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            val mean = MatOfDouble()
            val std = MatOfDouble()
            Core.meanStdDev(lap, mean, std)
            lap.release()
            val s = std.get(0, 0)[0]
            return s * s
        }

        private fun toBitmap(gray: Mat, size: Int): Bitmap {
            val small = Mat()
            Imgproc.resize(gray, small, Size(size.toDouble(), size.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(small, bmp)
            small.release()
            return bmp
        }
    }
}
