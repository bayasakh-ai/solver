package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Check my progress: the count first, never the place unless asked; a digit the scan
 * couldn't read for sure is not judged; the user's digits are compared as written.
 */
class ProgressCheckerTest {

    // puzzle3, as in the design files
    private val puzzle = Puzzle(
        Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
        Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"),
    )
    private val open = (0 until 81).filter { puzzle.givens[it] == 0 }

    /** The first [n] open cells filled from the solution, the cells in [wrong] changed. */
    private fun paper(n: Int, vararg wrong: Int) = IntArray(81).also { e ->
        open.take(n).forEach { e[it] = puzzle.solution[it] }
        for (i in wrong) e[i] = e[i] % 9 + 1
    }

    @Test fun countsWhatFitsAndWhatDoesNot() {
        val p = ProgressChecker.check(puzzle, paper(30, open[0], open[7]))
        assertEquals(30, p.written)
        assertEquals(55, p.toFill)
        assertEquals(25, p.left)
        assertEquals(listOf(open[0], open[7]), p.wrong)
        assertEquals(28, p.right)
        assertFalse(p.allFit)
        assertEquals("2 digits don’t fit", ProgressChecker.headline(p))
    }

    @Test fun theCountNeverSaysWhere() {
        val p = ProgressChecker.check(puzzle, paper(30, open[0], open[7]))
        val told = ProgressChecker.headline(p) + " " + ProgressChecker.detail(p)
        assertFalse("no row or column before asking: $told", told.contains("row", ignoreCase = true) || told.contains("column", ignoreCase = true))
        // nor the digits themselves: only counts appear (2 wrong, 30 written, 28 right)
        assertEquals(setOf("2", "30", "28"), Regex("\\d+").findAll(told).map { it.value }.toSet())
        assertTrue(ProgressChecker.detail(p).contains("or ask where"))
        // once asked, the words point at the marks on the grid
        assertTrue(ProgressChecker.detail(p, shown = true).contains("coral frame"))
    }

    @Test fun aDoubtfulReadingIsNotJudged() {
        // open[0] is wrong on the page, but the scan wasn't sure what it says: no accusation
        val entries = paper(30, open[0], open[7])
        val p = ProgressChecker.check(puzzle, entries, doubtful = setOf(open[0], open[3]))
        assertEquals(listOf(open[7]), p.wrong)
        assertEquals(listOf(open[0], open[3]), p.doubtful)
        assertEquals(28, p.judged)
        assertEquals(27, p.right)
        assertEquals("1 digit doesn’t fit", ProgressChecker.headline(p))
        assertTrue(ProgressChecker.detail(p).contains("2 digits (ringed) I couldn’t read for sure"))
        // a doubtful cell with nothing written, or on a printed digit, doesn't count
        val printed = (0 until 81).first { puzzle.givens[it] != 0 }
        val q = ProgressChecker.check(puzzle, entries, doubtful = setOf(open[40], printed))
        assertTrue(q.doubtful.isEmpty())
    }

    @Test fun allFitAndTheFinishedPage() {
        val p = ProgressChecker.check(puzzle, paper(22))
        assertTrue(p.allFit)
        assertFalse(p.finished)
        assertEquals("All 22 fit so far", ProgressChecker.headline(p))
        assertTrue(ProgressChecker.detail(p).contains("33 cells to go"))
        assertEquals("Your digit fits", ProgressChecker.headline(ProgressChecker.check(puzzle, paper(1))))

        val done = ProgressChecker.check(puzzle, paper(55))
        assertTrue(done.finished)
        assertEquals("Solved on paper", ProgressChecker.headline(done))
        // full but with a wrong digit, or with one unread: not "solved"
        assertFalse(ProgressChecker.check(puzzle, paper(55, open[5])).finished)
        assertFalse(ProgressChecker.check(puzzle, paper(55), setOf(open[5])).finished)
    }

    @Test fun edgesOfThePage() {
        val none = ProgressChecker.check(puzzle, IntArray(81))
        assertEquals("Nothing written yet", ProgressChecker.headline(none))
        assertEquals(55, none.left)
        // every digit doubtful: nothing to say about right or wrong
        val entries = paper(3, open[0])
        val blind = ProgressChecker.check(puzzle, entries, open.take(3).toSet())
        assertEquals("I couldn’t read your digits for sure", ProgressChecker.headline(blind))
        assertTrue(blind.wrong.isEmpty())
        // a digit written over a printed one is not the user's entry
        val printed = (0 until 81).first { puzzle.givens[it] != 0 }
        val over = IntArray(81).also { it[printed] = puzzle.givens[printed] % 9 + 1 }
        assertEquals(0, ProgressChecker.check(puzzle, over).written)
    }

    /** A confident misread must not become "1 digit doesn't fit" on a page with no mistakes. */
    @Test fun aDigitThatCouldBeTheRightOneIsNotCalledWrong() {
        val i = open[0]
        val right = puzzle.solution[i]
        val read = right % 9 + 1                       // the reader says this, confidently
        val entries = paper(30).also { it[i] = read }
        fun probs(pRight: Float) = FloatArray(9).also { it[read - 1] = 0.98f; it[right - 1] = pRight }
        // the right digit is still possible (0.4%): not judged, ringed
        val soft = IntArray(81).also { it[i] = ProgressChecker.couldBe(probs(0.004f)) }
        val p = ProgressChecker.check(puzzle, entries, couldBe = soft)
        assertTrue(p.wrong.isEmpty())
        assertEquals(listOf(i), p.doubtful)
        assertEquals("All 29 fit so far", ProgressChecker.headline(p))
        // the reader rules the right digit out (0.02%): a mistake
        val hard = IntArray(81).also { it[i] = ProgressChecker.couldBe(probs(0.0002f)) }
        assertEquals(listOf(i), ProgressChecker.check(puzzle, entries, couldBe = hard).wrong)
        // a digit that is right is right, whatever else it could be
        val fine = IntArray(81) { 0x3FE }
        assertTrue(ProgressChecker.check(puzzle, paper(30), couldBe = fine).let { it.wrong.isEmpty() && it.doubtful.isEmpty() })
        // the mask itself
        assertEquals((1 shl read) or (1 shl right), ProgressChecker.couldBe(probs(ProgressChecker.PLAUSIBLE)))
        assertEquals(1 shl read, ProgressChecker.couldBe(probs(0.00099f)))
        assertEquals(0, ProgressChecker.couldBe(null))
    }

    /** From the scan to the count: Check's unsettled doubts travel with the puzzle. */
    @Test fun checkHandsItsDoubtsOn() {
        val givens = puzzle.givens
        val probs = Array(81) { i -> if (givens[i] == 0) null else FloatArray(9) { 0.001f }.also { it[givens[i] - 1] = 0.99f } }
        val user = IntArray(81).also { it[open[0]] = puzzle.solution[open[0]] % 9 + 1; it[open[1]] = puzzle.solution[open[1]] }
        val conf = FloatArray(81) { 1f }.also { it[open[0]] = 0.6f }
        val second = IntArray(81).also { it[open[0]] = puzzle.solution[open[0]] }
        // open[2]: read confidently as a wrong digit, but the right one is still possible
        val soft = open[2]
        user[soft] = puzzle.solution[soft] % 9 + 1
        val reader = arrayOfNulls<FloatArray>(81)
        reader[soft] = FloatArray(9).also { it[user[soft] - 1] = 0.97f; it[puzzle.solution[soft] - 1] = 0.02f }
        val capture = Capture.from(givens, probs, SolveResult(givens, puzzle.solution, emptyList()), null, 0, user, null, conf, second, reader)
        val ready = CheckSession(capture).confirm() as CheckResult.Ready
        assertEquals(setOf(open[0]), ready.doubtful)
        val p = ProgressChecker.check(ready.puzzle, ready.start, ready.doubtful, ready.couldBe)
        assertTrue("a doubtful wrong reading is not a mistake", p.wrong.isEmpty())
        assertEquals(listOf(open[0], soft), p.doubtful)
        assertEquals(1, p.right)
        // once the user sets that cell on Check, it is their word: judged, and wrong
        val insisted = CheckSession(capture).edit(soft, puzzle.solution[soft] % 9 + 2).confirm() as CheckResult.Ready
        assertEquals(0, insisted.couldBe[soft])
        assertEquals(listOf(soft), ProgressChecker.check(insisted.puzzle, insisted.start, insisted.doubtful, insisted.couldBe).wrong)
        // the user settles it on Check (picks the dashed second choice): now it is judged, and right
        val settled = CheckSession(capture).edit(open[0], second[open[0]]).confirm() as CheckResult.Ready
        assertTrue(settled.doubtful.isEmpty())
        assertEquals(2, ProgressChecker.check(settled.puzzle, settled.start, settled.doubtful, settled.couldBe).right)
    }
}
