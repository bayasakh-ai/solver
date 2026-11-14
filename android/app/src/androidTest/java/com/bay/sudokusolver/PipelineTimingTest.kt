package com.bay.sudokusolver

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * Per-frame cost of the scan pipeline on the phone at three analysis resolutions:
 *   640  = find + read on a 640x480 frame (the app today)
 *   1280 = find + read on a 1280x960 frame
 *   hybrid = shrink 1280x960 to 640x480, find there, warp + read from 1280x960
 * and, on its own, the printed / handwritten step that runs on every frame that is read
 * (cell shapes + hand.tflite + the rule, then hand_digits.tflite on the handwritten cells).
 * Measurement only (no assertions). Photos are pushed, not committed:
 *   adb push photos/screen_*.jpg /sdcard/Android/data/com.bay.sudokusolver/files/timing/
 *   adb push photos/book_*.jpg   /sdcard/Android/data/com.bay.sudokusolver/files/timing/
 * Results go to logcat, tag PipelineTiming. Skipped when no photos are there.
 */
@RunWith(AndroidJUnit4::class)
class PipelineTimingTest {

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1e6

    @Test fun timePerResolution() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "timing")
        val photos = dir.listFiles { f -> f.name.endsWith(".jpg") }?.sorted().orEmpty()
        assumeTrue("no photos in $dir", photos.isNotEmpty())
        assumeTrue(OpenCVLoader.initLocal())
        val runs = 15
        val ctx = inst.targetContext
        val handClf = DigitClassifier(ctx, "hand.tflite", outputs = 1)
        val handReader = DigitClassifier(ctx, "hand_digits.tflite")
        DigitClassifier(ctx).use { clf ->
            for (mode in listOf("640", "1280", "hybrid")) {
                val find = ArrayList<Double>(); val read = ArrayList<Double>(); var found = 0
                val shapesMs = ArrayList<Double>(); val handMs = ArrayList<Double>(); var written = 0
                for (photo in photos) {
                    val bmp = BitmapFactory.decodeFile(photo.path)
                    val rgba = Mat().also { Utils.bitmapToMat(bmp, it) }
                    val gray = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2GRAY) }
                    val long = if (mode == "640") 640.0 else 1280.0
                    val f = long / maxOf(gray.cols(), gray.rows())
                    val frame = Mat().also { Imgproc.resize(gray, it, Size(), f, f, Imgproc.INTER_AREA) }
                    repeat(runs + 2) { k -> // 2 warm-up runs
                        var t0 = System.nanoTime()
                        val corners: Array<Point>? = if (mode == "hybrid") {
                            val small = Mat().also { Imgproc.resize(frame, it, Size(), 0.5, 0.5, Imgproc.INTER_AREA) }
                            val th = GridDetector.preprocess(small)
                            GridDetector.findGrid(th) { c -> GridDetector.looksLikeGrid(frame, c.map { Point(it.x * 2, it.y * 2) }.toTypedArray()) }
                                ?.map { Point(it.x * 2, it.y * 2) }?.toTypedArray()
                                .also { th.release(); small.release() }
                        } else {
                            val th = GridDetector.preprocess(frame)
                            GridDetector.findGrid(th) { GridDetector.looksLikeGrid(frame, it) }.also { th.release() }
                        }
                        val tFind = ms(t0)
                        if (corners == null) { if (k >= 2) find.add(tFind); return@repeat }
                        t0 = System.nanoTime()
                        val (warped, m) = GridDetector.warp(frame, corners)
                        val cells = GridDetector.extractCells(warped).cells
                        val filled = cells.filterNotNull()
                        if (filled.isNotEmpty()) clf.classify(filled)
                        val tRead = ms(t0)
                        // printed or handwritten, as FrameAnalyzer.read does it
                        t0 = System.nanoTime()
                        val shapes = GridDetector.cellShapes(warped)
                        val tShapes = ms(t0)
                        val shaped = (0 until 81).filter { cells[it] != null && shapes[it] != null }
                        var hand = 0
                        if (shaped.isNotEmpty()) {
                            val p = handClf.classify(shaped.map { cells[it]!! })
                            val kinds = Hand.classify(shaped.map { shapes[it]!! }, p.map { it[0].toDouble() })
                            val mine = shaped.filterIndexed { j, _ -> kinds[j] == HandKind.HAND }
                            if (mine.isNotEmpty()) handReader.classify(mine.map { cells[it]!! })
                            hand = mine.size
                        }
                        val tHand = ms(t0)
                        warped.release(); m.release()
                        if (k >= 2) {
                            find.add(tFind); read.add(tRead); shapesMs.add(tShapes); handMs.add(tHand)
                            if (k == 2) { found++; written += hand }
                        }
                    }
                    frame.release(); gray.release(); rgba.release(); bmp.recycle()
                }
                fun stats(v: List<Double>) = if (v.isEmpty()) "-" else
                    v.sorted().let { "median %.1f ms, p90 %.1f ms".format(it[it.size / 2], it[it.size * 9 / 10]) }
                Log.i("PipelineTiming", "mode $mode: ${photos.size} photos, grid found in $found | " +
                    "find ${stats(find)} | warp+cells+model ${stats(read)} | " +
                    "printed/handwritten ${stats(handMs)} (cell shapes alone ${stats(shapesMs)}), " +
                    "$written cells called handwritten")
            }
        }
        handClf.close(); handReader.close()
    }
}
