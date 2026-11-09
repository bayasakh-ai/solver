package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeneratorTest {

    @Test fun solvedGridIsAValidSudoku() {
        val g = Generator.solvedGrid(Random(1))
        assertTrue(g.all { it in 1..9 })
        for (u in Cells.units) assertEquals((1..9).toSet(), u.cells.map { g[it] }.toSet())
    }

    @Test fun sameSeedSamePuzzle() {
        val a = Generator.generate(Difficulty.MEDIUM, Random(7))!!
        val b = Generator.generate(Difficulty.MEDIUM, Random(7))!!
        assertEquals(a, b)
    }

    /**
     * The acceptance test: for each difficulty and 10 seeds, the puzzle is unique, its
     * solution is the one stored, and HintEngine rates it what was asked. Also prints
     * tries/givens/time so the phone cost can be judged (JVM, desktop).
     */
    @Test fun everyDifficultyRatesAsChosen() {
        for (d in Difficulty.entries) {
            val start = System.nanoTime()
            val givens = ArrayList<Int>()
            for (seed in 0 until 10) {
                val p = Generator.generate(d, Random(seed * 31 + d.ordinal))
                assertNotNull("$d seed $seed", p)
                p!!
                assertEquals(PuzzleSource.GENERATED, p.source)
                val sols = Solver.solve(p.givens)
                assertEquals("$d seed $seed unique", 1, sols.size)
                assertTrue(sols[0].contentEquals(p.solution))
                assertTrue((0 until 81).all { p.givens[it] == 0 || p.givens[it] == p.solution[it] })
                assertEquals("$d seed $seed", d, p.difficulty)
                assertTrue(p.givens.count { it != 0 } >= Generator.minGivens(d))
                givens += p.givens.count { it != 0 }
            }
            val ms = (System.nanoTime() - start) / 1_000_000 / 10
            println("$d: givens ${givens.min()}-${givens.max()}, ~$ms ms per puzzle")
        }
    }
}
