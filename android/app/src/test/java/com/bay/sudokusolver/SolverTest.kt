package com.bay.sudokusolver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same puzzles the Python pipeline solved, so both versions must agree. */
class SolverTest {

    private val puzzle2 = Solver.parse("""
        . . . . 6 . . . 2
        4 . . . 1 5 6 . .
        . . . 7 . . . 9 .
        . . . 6 . . 1 . 7
        . 7 . . . . . 8 .
        3 . 6 . . 9 . . .
        . 5 . . . 8 . . .
        . . 1 4 9 . . . 3
        8 . . . 5 . . . .
    """)
    private val solution2 = Solver.parse(
        "785964312439215678162783594598642137274531986316879245957328461621497853843156729")

    private val puzzle4 = Solver.parse("""
        . . 5 . 3 1 . 4 .
        . . 1 . . . . . 8
        7 9 4 6 . . . . .
        . . 2 . . . . . 3
        9 . . . . . . . 5
        3 . . . . . 8 . .
        . . . . . 3 5 1 7
        5 . . . . . 6 . .
        . 7 . 8 2 . 9 . .
    """)
    private val solution4 = Solver.parse(
        "685731249231549768794682351462958173918376425357214896829463517543197682176825934")

    @Test fun solvesNewspaperPuzzle() {
        val sols = Solver.solve(puzzle2)
        assertEquals(1, sols.size)
        assertArrayEquals(solution2, sols[0])
    }

    @Test fun solvesFiveStarPuzzle() {
        val sols = Solver.solve(puzzle4)
        assertEquals(1, sols.size)
        assertArrayEquals(solution4, sols[0])
    }

    @Test fun solvesVeryHardPuzzleQuickly() {
        val hard = Solver.parse(
            "8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..")
        val start = System.nanoTime()
        val sols = Solver.solve(hard)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1, sols.size)
        assertTrue("took $ms ms", ms < 1000)
    }

    @Test fun conflictingGivensHaveNoSolution() {
        val bad = puzzle2.copyOf().also { it[0] = 2 } // second 2 in row 1
        assertEquals(0, Solver.solve(bad).size)
    }

    @Test fun misreadThatRemovesADigitIsAmbiguous() {
        val sparse = IntArray(81).also { it[0] = 1 }
        assertEquals(2, Solver.solve(sparse).size)
        assertTrue(!Solver.isPlausible(sparse))
    }

    @Test fun doesNotModifyInput() {
        val copy = puzzle2.copyOf()
        Solver.solve(puzzle2)
        assertArrayEquals(copy, puzzle2)
    }
}
