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

    @Test fun smallGridAsksToMoveCloserButKeepsReading() {
        // 640x480 frame: a 150x150 grid is 7.3% (fine), a 120x120 grid 4.7% (too small)
        fun square(side: Float) = floatArrayOf(100f, 100f, 100f + side, 100f, 100f + side, 100f + side, 100f, 100f + side)
        val big = ScanState.gridShare(square(150f), 640, 480)
        val small = ScanState.gridShare(square(120f), 640, 480)
        assertEquals(150.0 * 150 / (640 * 480), big, 1e-9)
        assertEquals(ScanState.Reading(2, 5), ScanState.decide(null, true, 2, 5, big))
        assertEquals(ScanState.Reading(2, 5, tooSmall = true), ScanState.decide(null, true, 2, 5, small))
        assertEquals(ScanState.HoldSteady(2, 5, tooSmall = true), ScanState.decide(null, false, 2, 5, small))
        // a settled result is shown as it is, however small
        assertEquals(ScanState.Solved(0),
            ScanState.decide(SolveResult(puzzle, solution, emptyList()), true, 5, 5, small))
    }

    @Test fun gridShareIgnoresCornerOrderAndPerspective() {
        val quad = floatArrayOf(0f, 0f, 200f, 20f, 180f, 220f, 10f, 200f)
        val reversed = floatArrayOf(10f, 200f, 180f, 220f, 200f, 20f, 0f, 0f)
        assertEquals(ScanState.gridShare(quad, 640, 480), ScanState.gridShare(reversed, 640, 480), 1e-12)
    }
}
