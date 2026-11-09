package com.bay.sudokusolver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand-off from the live scanner to the Check screen (design/screens/flow). */
class CaptureTest {

    // puzzle3, as in the design files
    private val givens = Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900")

    /** Confident one-hot-ish probabilities for the read digits, with [second] as runner-up. */
    private fun probs(read: IntArray, second: Map<Int, Pair<Int, Float>> = emptyMap()) =
        Array(81) { i ->
            if (read[i] == 0) null else FloatArray(9) { 0.01f }.also { p ->
                val (alt, altP) = second[i] ?: ((read[i] % 9) + 1 to 0.02f)
                p[alt - 1] = altP
                p[read[i] - 1] = 0.9f
            }
        }

    @Test fun selfCorrectedReadingKeepsBothReadAndFixedDigits() {
        val read = givens.copyOf().also { it[4] = 8 } // r1c5: the camera read 8, the paper says 3
        val p = probs(read, mapOf(4 to (3 to 0.08f)))
        p[4]!![7] = 0.55f // the misread is the least confident cell
        val result = SelfCorrect.solve(read, p)
        val c = Capture.from(read, p, result, inkUnsure = null, inkIgnored = 0)

        assertTrue(c.solved)
        assertEquals(8, c.read[4])
        assertEquals(3, c.digits[4])
        assertEquals(setOf(4), c.corrected)
        assertEquals(3, c.secondGuess[4])   // what Check shows dashed
        assertEquals(0.55f, c.confidence[4], 1e-6f)
        assertArrayEquals(Solver.solve(givens)[0], c.solution)
    }

    @Test fun emptyCellsHaveNoConfidenceOrSecondGuess() {
        val c = Capture.from(givens, probs(givens), SolveResult(givens, null, emptyList()), null, 0)
        for (i in 0 until 81) if (givens[i] == 0) {
            assertEquals(0f, c.confidence[i])
            assertEquals(0, c.secondGuess[i])
        }
        assertEquals(26, c.filled)
        assertFalse(c.solved)
    }

    @Test fun inkFlagsAndCountPassThroughAsCopies() {
        val unsure = BooleanArray(81).also { it[36] = true }
        val c = Capture.from(givens, probs(givens), SolveResult(givens, null, emptyList()), unsure, inkIgnored = 56)
        unsure[36] = false // the analyzer reuses its arrays: the capture must not change with them
        assertTrue(c.inkUnsure[36])
        assertEquals(56, c.inkIgnored)
        assertEquals(1, c.inkUnsure.count { it })
    }

    @Test fun noInkVerdictMeansNothingUnsure() {
        val c = Capture.from(givens, probs(givens), SolveResult(givens, null, emptyList()), null, 0)
        assertEquals(0, c.inkUnsure.count { it })
    }
}
