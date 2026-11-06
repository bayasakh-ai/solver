package com.bay.sudokusolver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Checks that the phone gives the SAME predictions as Python for the same cells.
 * test_cells.bin and test_expected.txt come from export_test_cells.py and live in
 * app/src/androidTest/assets/.
 */
@RunWith(AndroidJUnit4::class)
class DigitClassifierTest {

    @Test fun matchesPythonPredictions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val raw = testAssets.open("test_cells.bin").use { it.readBytes() }
        val expected = testAssets.open("test_expected.txt").bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line -> line.trim().split(Regex("\\s+")).map { it.toFloat() }.toFloatArray() }

        val size = DigitClassifier.CELL * DigitClassifier.CELL
        val cells = (raw.indices step size).map { raw.copyOfRange(it, it + size) }
        assertTrue(cells.size == expected.size)

        DigitClassifier(instrumentation.targetContext).use { clf ->
            val probs = clf.classify(cells)
            val argmax = { p: FloatArray -> p.indices.maxBy { p[it] } }
            assertArrayEquals(
                expected.map(argmax).toIntArray(),
                probs.map(argmax).toIntArray()
            )
            val maxDiff = probs.indices.maxOf { i ->
                probs[i].indices.maxOf { j -> abs(probs[i][j] - expected[i][j]) }
            }
            assertTrue("max probability difference $maxDiff", maxDiff < 1e-3f)
        }
    }
}
