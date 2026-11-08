package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same branches the old message string in FrameAnalyzer.updateSolution had, in order. */
class ScanStateTest {

    private val puzzle = Solver.parse(
        "....6...2 4...156.. ...7...9. ...6..1.7 .7.....8. 3.6..9... .5...8... ..149...3 8...5....")
    private val solution = Solver.parse(
        "785964312439215678162783594598642137274531986316879245957328461621497853843156729")

    private fun fix() = Fix(cell = 8, from = 9, fromProb = 0.6f, to = 2, toProb = 0.33f)

    @Test fun noConsensusYetIsReadingOrHoldSteady() {
        assertEquals(ScanState.Reading(2, 5), ScanState.decide(null, isSharp = true, frames = 2, window = 5))
        assertEquals(ScanState.HoldSteady(2, 5), ScanState.decide(null, isSharp = false, frames = 2, window = 5))
    }

    @Test fun solvedCountsFixesAndIgnoresBlur() {
        val clean = SolveResult(puzzle, solution, emptyList())
        val fixed = SolveResult(puzzle, solution, listOf(fix()))
        assertEquals(ScanState.Solved(0), ScanState.decide(clean, isSharp = true, frames = 5, window = 5))
        assertEquals(ScanState.Solved(1), ScanState.decide(fixed, isSharp = false, frames = 5, window = 5))
    }

    @Test fun unsolvableSaysWhy() {
        val sparse = IntArray(81).also { for (i in 0 until 16) it[i * 5] = 1 + i % 9 }
        assertEquals(ScanState.NeedsReview(ScanState.Reason.TOO_FEW_DIGITS),
            ScanState.decide(SolveResult(sparse, null, emptyList()), isSharp = true, frames = 5, window = 5))
        assertEquals(ScanState.NeedsReview(ScanState.Reason.NO_UNIQUE_SOLUTION),
            ScanState.decide(SolveResult(puzzle, null, emptyList()), isSharp = false, frames = 5, window = 5))
    }
}
