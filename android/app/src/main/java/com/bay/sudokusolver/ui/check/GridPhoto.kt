package com.bay.sudokusolver.ui.check

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * The Photo view on Check: the frozen camera picture, flattened with the same corners
 * the overlay uses (already turned upright by the orientation step), in colour and
 * with no filter, so the user compares against what the camera really saw.
 */
object GridPhoto {
    const val SIZE = 576 // 64 px per cell, as in the design's photo

    /**
     * [view]: PreviewView's bitmap (the view's size, FILL_CENTER crop); [corners]: TL, TR,
     * BR, BL in the upright analysis frame of [frameWidth] x [frameHeight]. Same frame ->
     * view maths as GridOverlay.
     */
    fun flatten(view: Bitmap, corners: FloatArray, frameWidth: Int, frameHeight: Int): Bitmap? {
        if (corners.size != 8 || frameWidth <= 0 || frameHeight <= 0) return null
        val scale = max(view.width / frameWidth.toFloat(), view.height / frameHeight.toFloat())
        val ox = (view.width - frameWidth * scale) / 2f
        val oy = (view.height - frameHeight * scale) / 2f
        val src = MatOfPoint2f(*Array(4) { Point((ox + corners[2 * it] * scale).toDouble(), (oy + corners[2 * it + 1] * scale).toDouble()) })
        val last = (SIZE - 1).toDouble()
        val dst = MatOfPoint2f(Point(0.0, 0.0), Point(last, 0.0), Point(last, last), Point(0.0, last))
        val input = Mat()
        val output = Mat()
        val m = Imgproc.getPerspectiveTransform(src, dst)
        return try {
            val argb = if (view.config == Bitmap.Config.ARGB_8888) view else view.copy(Bitmap.Config.ARGB_8888, false)
            Utils.bitmapToMat(argb, input)
            Imgproc.warpPerspective(input, output, m, Size(SIZE.toDouble(), SIZE.toDouble()), Imgproc.INTER_LINEAR)
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(output, it) }
        } catch (e: Exception) {
            null // no photo: Check still works on the reading alone
        } finally {
            input.release(); output.release(); m.release(); src.release(); dst.release()
        }
    }
}
