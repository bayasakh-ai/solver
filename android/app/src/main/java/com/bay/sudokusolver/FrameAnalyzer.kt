package com.bay.sudokusolver

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

/**
 * Milestone 1 analyzer: proves the live loop works (camera -> OpenCV -> UI).
 * Reports frame rate and a sharpness score (variance of the Laplacian).
 * Low sharpness = blurry frame; later the solver will simply skip those frames.
 */
class FrameAnalyzer(private val onStatus: (String) -> Unit) : ImageAnalysis.Analyzer {

    private var frames = 0
    private var windowStart = System.nanoTime()
    private var fps = 0.0
    private var lastReport = 0L

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val gray = yPlaneToMat(frame)
            val sharpness = sharpness(gray)
            gray.release()

            frames++
            val now = System.nanoTime()
            if (now - windowStart >= 1_000_000_000L) {
                fps = frames * 1e9 / (now - windowStart)
                frames = 0
                windowStart = now
            }
            if (now - lastReport > 200_000_000L) { // update the text 5x per second
                lastReport = now
                val label = if (sharpness > SHARP_ENOUGH) "sharp" else "blurry"
                onStatus("%.0f fps · %dx%d · sharpness %.0f (%s)"
                    .format(fps, frame.width, frame.height, sharpness, label))
            }
        }
    }

    companion object {
        /** Rough starting point; tune it by watching the numbers on your phone. */
        const val SHARP_ENOUGH = 100.0

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
    }
}
