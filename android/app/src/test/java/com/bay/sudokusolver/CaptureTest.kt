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

    @Test fun handwrittenDigitsAreCarriedApartFromThePuzzle() {
        // the frames called r1c1 and r1c2 handwritten (7 and 2); r1c3 is a printed 5 some
        // frames also called handwritten: the settled printed reading wins there
        val user = IntArray(81).also { it[0] = 7; it[1] = 2; it[2] = 9 }
        val maybe = BooleanArray(81).also { it[5] = true; it[0] = true } // r1c6 is a printed 1; r1c1 isn't read
        val solution = Solver.solve(givens)[0]
        val c = Capture.from(givens, probs(givens), SolveResult(givens, solution, emptyList()), null, 0, user, maybe)
        user[0] = 0 // the analyzer reuses its arrays
        assertEquals(7, c.userEntries[0])
        assertEquals(2, c.userEntries[1])
        assertEquals(0, c.userEntries[2])
        assertEquals(0, c.read[0])            // not part of the puzzle
        assertTrue(c.handUnsure[5])
        assertEquals(1, c.handUnsure.count { it }) // only cells that hold a printed reading
        // Check starts with them marked as the user's, and the board gets them as entries
        val s = CheckSession(c)
        assertEquals(setOf(0, 1), s.mine)
        assertEquals(UnsureReason.HAND, s.unsureReason(5))
        assertEquals(null, s.suggestion(5))
        val ready = s.confirm() as CheckResult.Ready
        assertEquals(7, ready.start[0])
        assertEquals(2, ready.start[1])
        assertArrayEquals(givens, ready.puzzle.givens)
    }

    @Test fun handwrittenDigitsNeedThreeFramesAndTakeTheCommonestReading() {
        fun frame(vararg cells: Pair<Int, Int>) = IntArray(81).also { f -> cells.forEach { (i, d) -> f[i] = d } }
        val frames = listOf(frame(0 to 4, 1 to 6), frame(0 to 9, 1 to 6), frame(0 to 4), null, frame(0 to 4, 2 to 3))
        val user = userEntries(frames, needed = 3)
        assertEquals(4, user[0])   // handwritten in 4 frames, read 4 in three of them
        assertEquals(0, user[1])   // only 2 frames: not settled, stays out
        assertEquals(0, user[2])
        // and through the tracker, next to the printed vote
        val tracker = PuzzleTracker()
        repeat(5) { tracker.add(givens, probs(givens), frame(0 to 4)) }
        assertEquals(4, tracker.userEntries()[0])
        assertArrayEquals(givens, tracker.consensus()!!.first)
    }

    @Test fun doubtfulHandwritingIsAskedAboutNotGuessed() {
        fun frame(vararg cells: Pair<Int, Int>) = IntArray(81).also { f -> cells.forEach { (i, d) -> f[i] = d } }
        fun probs(i: Int, vararg p: Pair<Int, Float>) = arrayOfNulls<FloatArray>(81).also { a ->
            a[i] = FloatArray(9).also { q -> p.forEach { (d, v) -> q[d - 1] = v } }
        }
        // r1c1: two frames read 7 weakly, one reads 1 strongly: the average says 1, not the count
        val frames = listOf(frame(0 to 7), frame(0 to 7), frame(0 to 1))
        val reader = listOf(probs(0, 7 to 0.5f, 1 to 0.45f), probs(0, 7 to 0.5f, 1 to 0.45f), probs(0, 1 to 0.95f, 7 to 0.03f))
        val vote = userVote(frames, reader, needed = 3)
        assertEquals(1, vote.entries[0])
        assertEquals(7, vote.second[0])
        assertEquals((0.45f + 0.45f + 0.95f) / 3, vote.confidence[0], 1e-6f)
        // no reader: the commonest reading, and nothing to doubt it with
        val plain = userVote(frames, listOf(null, null, null), needed = 3)
        assertEquals(7, plain.entries[0])
        assertEquals(1f, plain.confidence[0])
        assertEquals(0, plain.second[0])

        val solution = Solver.solve(givens)[0]
        val user = IntArray(81).also { it[0] = 1; it[1] = 2 }
        val conf = FloatArray(81) { 1f }.also { it[0] = 0.62f; it[1] = 0.97f }
        val second = IntArray(81).also { it[0] = 7; it[1] = 3 }
        val c = Capture.from(givens, probs(givens), SolveResult(givens, solution, emptyList()), null, 0, user, null, conf, second)
        var s = CheckSession(c)
        assertTrue(s.mineUnsure(0))
        assertFalse(s.mineUnsure(1))            // confident: carried over without a question
        assertEquals(CellMark.MINE, s.mark(0))  // still the user's, drawn in their hand
        assertEquals(7, s.suggestion(0))        // the dashed key
        assertEquals(null, s.suggestion(1))
        val asked = s.unsureCount              // the helper's printed digits are below 0.95 too
        // picking a digit settles it, and that digit goes to the board
        s = s.edit(0, 7)
        assertFalse(s.mineUnsure(0))
        assertEquals(asked - 1, s.unsureCount)
        assertEquals(7, (s.confirm() as CheckResult.Ready).start[0])
        // a digit the user marks by hand is never doubted
        assertFalse(CheckSession(c).setMine(2, true).mineUnsure(2))
    }

    @Test fun noInkVerdictMeansNothingUnsure() {
        val c = Capture.from(givens, probs(givens), SolveResult(givens, null, emptyList()), null, 0)
        assertEquals(0, c.inkUnsure.count { it })
    }
}
