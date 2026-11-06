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
 */
class DigitClassifier(context: Context, modelAsset: String = "model.tflite") : Closeable {

    private val interpreter: Interpreter

    init {
        val bytes = context.assets.open(modelAsset).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    }

    fun classify(cells: List<ByteArray>): Array<FloatArray> {
        if (cells.isEmpty()) return emptyArray()
        val n = cells.size
        interpreter.resizeInput(0, intArrayOf(n, CELL, CELL, 1))
        interpreter.allocateTensors()

        val input = ByteBuffer.allocateDirect(n * CELL * CELL * 4).order(ByteOrder.nativeOrder())
        for (cell in cells) {
            require(cell.size == CELL * CELL) { "each cell must be ${CELL}x$CELL" }
            for (b in cell) input.putFloat((b.toInt() and 0xFF) / 255f) // same scaling as Python
        }
        input.rewind()

        val output = Array(n) { FloatArray(9) }
        interpreter.run(input, output)
        return output
    }

    /** Digit 1..9 with the highest probability. */
    fun predict(cells: List<ByteArray>): IntArray =
        classify(cells).map { p -> p.indices.maxBy { p[it] } + 1 }.toIntArray()

    override fun close() = interpreter.close()

    companion object {
        const val CELL = 28
    }
}
