package com.bay.sudokusolver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Voting + self-correction, using the newspaper puzzle whose 2 was misread as 9. */
class SelfCorrectTest {

    private val truth = Solver.parse(
        "....6...2 4...156.. ...7...9. ...6..1.7 .7.....8. 3.6..9... .5...8... ..149...3 8...5....")
    private val solution = Solver.parse(
        "785964312439215678162783594598642137274531986316879245957328461621497853843156729")

    private fun confident(d: Int) = FloatArray(9) { 0.001f }.also { it[d - 1] = 0.99f }

    private fun reading(): Pair<IntArray, Array<FloatArray?>> {
        val g = truth.copyOf()
        val p = arrayOfNulls<FloatArray>(81)
        for (i in 0 until 81) if (g[i] != 0) p[i] = confident(g[i])
        return g to p
    }

    @Test fun votingKeepsMajorityAndSelfCorrectFixesIt() {
        val tracker = PuzzleTracker()
        repeat(5) { f ->
            val (g, p) = reading()
            if (f < 4) { // top-right 2 read as 9 in 4 of 5 frames
                g[8] = 9
                p[8] = FloatArray(9) { 0.01f }.also { it[8] = 0.6f; it[1] = 0.33f }
            }
            tracker.add(g, p)
        }
        val (grid, probs) = tracker.consensus()!!
        assertEquals(9, grid[8])
        val r = SelfCorrect.solve(grid, probs)
        assertEquals(1, r.fixes.size)
        assertEquals(2, r.fixes[0].to)
        assertArrayEquals(solution, r.solution)
    }

    @Test fun flickeringCellIsNotStable() {
        val tracker = PuzzleTracker()
        repeat(5) { f ->
            val (g, p) = reading()
            g[0] = f % 3 + 1
            tracker.add(g, p)
        }
        assertNull(tracker.consensus())
    }

    @Test fun fixesTwoMisreads() {
        val (g, p) = reading()
        g[8] = 9; p[8] = FloatArray(9) { 0.01f }.also { it[8] = 0.55f; it[1] = 0.35f }
        g[72] = 3; p[72] = FloatArray(9) { 0.01f }.also { it[2] = 0.5f; it[7] = 0.4f }
        val r = SelfCorrect.solve(g, p)
        assertEquals(2, r.fixes.size)
        assertArrayEquals(solution, r.solution)
    }

    @Test fun correctReadingNeedsNoFixes() {
        val (g, p) = reading()
        val r = SelfCorrect.solve(g, p)
        assertEquals(0, r.fixes.size)
        assertArrayEquals(solution, r.solution)
    }
}
