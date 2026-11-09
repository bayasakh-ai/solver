package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckSessionTest {

    private val givens = Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900")
    private val solution = Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934")

    /** Every read cell confident (0.99, runner-up = next digit) unless [weak] says otherwise. */
    private fun probs(read: IntArray, weak: Map<Int, Pair<Float, Int>> = emptyMap()) = Array(81) { i ->
        if (read[i] == 0) null else FloatArray(9) { 0.001f }.also { p ->
            val (top, alt) = weak[i] ?: (0.99f to (read[i] % 9) + 1)
            p[alt - 1] = 1f - top - 0.007f
            p[read[i] - 1] = top
        }
    }

    private fun capture(read: IntArray, weak: Map<Int, Pair<Float, Int>> = emptyMap(), ink: Set<Int> = emptySet()): Capture {
        val p = probs(read, weak)
        return Capture.from(read, p, SelfCorrect.solve(read, p), BooleanArray(81) { it in ink }, 0)
    }

    @Test fun lowConfidenceCellsAreUnsureWithTheSecondGuessDashed() {
        // r5c1 (36) is a 9, read at 0.90 with 4 as runner-up; r6c1 (45) read at 0.951
        val c = CheckSession(capture(givens, mapOf(36 to (0.90f to 4), 45 to (0.951f to 6))))
        assertEquals(CellMark.UNSURE, c.mark(36))
        assertEquals(UnsureReason.LOW_CONFIDENCE, c.unsureReason(36))
        assertEquals(4, c.suggestion(36))
        assertEquals(CellMark.NONE, c.mark(45))       // just above the 0.95 threshold
        assertNull(c.suggestion(45))
        assertEquals(1, c.unsureCount)
    }

    @Test fun inkUnsureSuggestsEmpty() {
        val c = CheckSession(capture(givens, ink = setOf(2)))
        assertEquals(UnsureReason.INK, c.unsureReason(2))
        assertEquals(0, c.suggestion(2))
    }

    @Test fun selfCorrectedCellIsUnderlinedNotRingedAndSuggestsTheOriginal() {
        val read = givens.copyOf().also { it[4] = 8 }  // r1c5: the camera read 8, the paper says 3
        val c = CheckSession(capture(read, mapOf(4 to (0.55f to 3))))
        assertEquals(CellMark.CORRECTED, c.mark(4))
        assertNull(c.unsureReason(4))
        assertEquals(3, c.value(4))
        assertEquals(8, c.suggestion(4))
        assertEquals(1, c.correctedCount)
    }

    @Test fun editsAreMarkedAndSettingBackDropsThem() {
        val c = CheckSession(capture(givens))
        val e = c.edit(0, 6)
        assertEquals(CellMark.CHANGED, e.mark(0))
        assertEquals(6, e.value(0))
        assertEquals(1, e.changedCount)
        assertTrue(e.edit(0, 0).edits.isEmpty())
    }

    @Test fun confirmUsesTheScanWhenUntouched() {
        val c = CheckSession(capture(givens))
        val r = c.confirm() as CheckResult.Ready
        assertEquals(Puzzle(givens, solution), r.puzzle)
    }

    @Test fun confirmSolvesTheFixedGridExactly() {
        // the camera read r1c5 as 8 and nothing else could fix it: needs review
        val read = givens.copyOf().also { it[4] = 8 }
        val cap = Capture(read, read, FloatArray(81) { if (read[it] != 0) 0.99f else 0f }, IntArray(81),
            emptyList(), BooleanArray(81), 0, null)
        val c = CheckSession(cap)
        assertTrue(c.confirm() !is CheckResult.Ready)
        val fixed = c.edit(4, 3).confirm() as CheckResult.Ready
        assertEquals(solution.toList(), fixed.puzzle.solution.toList())
    }

    @Test fun confirmReportsWhatIsWrong() {
        val c = CheckSession(capture(givens))
        assertEquals(CheckResult.NoSolution, c.edit(0, 5).confirm())  // a second 5 in row 1
        assertEquals(CheckResult.Ambiguous, c.edit(2, 0).edit(3, 0).edit(4, 0).edit(5, 0).confirm())
        var few = c
        for (i in 0 until 81) if (givens[i] != 0 && few.filled > 16) few = few.edit(i, 0)
        assertEquals(CheckResult.TooFew, few.confirm())
    }
}
