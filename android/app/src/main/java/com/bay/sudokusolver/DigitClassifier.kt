package com.bay.sudokusolver

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs model.tflite (from app/src/main/assets) on 28x28 digit images.
 * Input cells: ByteArray(784), 0..255, white digit on black (same as clean_cell in Python).
 * Output per cell: FloatArray(9) of probabilities for digits 1..9.
 *
 * The model always runs on a fixed batch of 81 (a full grid). Resizing the batch
 * every frame made TFLite's output shape fall out of sync and crash; unused slots
 * are just zeros, and the extra work is negligible for this tiny model.
 */
class DigitClassifier(context: Context, modelAsset: String = "model.tflite") : Closeable {

    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(BATCH * CELL * CELL * 4).order(ByteOrder.nativeOrder())
    private val output = Array(BATCH) { FloatArray(9) }

    init {
        val bytes = context.assets.open(modelAsset).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
        interpreter.resizeInput(0, intArrayOf(BATCH, CELL, CELL, 1)) // once, never again
        interpreter.allocateTensors()
    }

    fun classify(cells: List<ByteArray>): Array<FloatArray> {
        val results = ArrayList<FloatArray>(cells.size)
        for (chunk in cells.chunked(BATCH)) {
            input.clear()
            for (cell in chunk) {
                require(cell.size == CELL * CELL) { "each cell must be ${CELL}x$CELL" }
                for (b in cell) input.putFloat((b.toInt() and 0xFF) / 255f) // same scaling as Python
            }
            while (input.hasRemaining()) input.putFloat(0f) // pad unused slots
            input.rewind()
            interpreter.run(input, output)
            for (k in chunk.indices) results.add(output[k].copyOf())
        }
        return results.toTypedArray()
    }

    /** Digit 1..9 with the highest probability. */
    fun predict(cells: List<ByteArray>): IntArray =
        classify(cells).map { p -> p.indices.maxBy { p[it] } + 1 }.toIntArray()

    override fun close() = interpreter.close()

    companion object {
        const val CELL = 28
        const val BATCH = 81
    }
}