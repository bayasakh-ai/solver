package com.bay.sudokusolver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Facts checked against design/screens/flow (README + Play.dc.html), puzzle3. */
class HintEngineTest {

    private val givens = Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900")
    private val solution = Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934")
    /** Where singles stop (computed in Python for the design). */
    private val stuckBoard = Solver.parse("005031040031000008794600301402000103908300405307000800009463517543000682176825934")

    private fun entriesOf(board: IntArray) = IntArray(81) { if (givens[it] == 0) board[it] else 0 }

    @Test fun singlesPlace21DigitsThenStickWith34Empty() {
        val run = HintEngine.singles(givens)
        assertEquals(21, run.placed)
        assertEquals(34, run.board.count { it == 0 })
        assertArrayEquals(stuckBoard, run.board)
        assertEquals(Difficulty.HARD, run.difficulty)
        assertEquals(Difficulty.HARD, HintEngine.rate(givens))
    }

    @Test fun stuckBoardOffersTheAdvancedFallbackAtR1C7() {
        val r = HintEngine.next(givens, entriesOf(stuckBoard), solution) as HintResult.Step
        assertEquals(Technique.ADVANCED, r.hint.technique)
        assertEquals(6, r.hint.target)          // r1c7
        assertEquals(2, r.hint.digit)
        assertEquals(listOf(2, 7), r.hint.candidates)
    }

    @Test fun afterRevealingR1C7SinglesFinishThePuzzle() {
        val board = stuckBoard.copyOf().also { it[6] = 2 }
        val run = HintEngine.singles(board)
        assertTrue(!run.stuck)
        assertArrayEquals(solution, run.board)
    }

    @Test fun designsHiddenSingle3InTheMiddleBox() {
        // the player's entries in the design: r3c5 8, r3c6 2, r3c7 3, r7c1 8, r7c2 2
        val board = givens.copyOf().also { it[22] = 8; it[23] = 2; it[24] = 3; it[54] = 8; it[55] = 2 }
        val h = HintEngine.hiddenSingle(board, SudokuUnit(UnitType.BOX, 4), 3)!!
        assertEquals(39, h.target)                                  // r5c4
        assertEquals(listOf(4, 35, 45, 59), h.eliminators)          // r1c5, r4c9, r6c1, r7c6
        assertEquals(listOf(30, 31, 32, 40, 41, 48, 49, 50), h.crossedOut)
        assertEquals("3 can only go in one place in this box.", h.text)
        assertEquals("The 3s in rows 4 and 6 and columns 5 and 6 rule out the other eight cells.", h.reason)
        assertEquals("Look at the middle box", h.nudgeTitle)
        // every crossed-out cell lies on a sweep line
        val swept = h.sweeps.flatMap { cellsBetween(it.from, it.to) }.toSet()
        assertTrue(h.crossedOut.all { it in swept })
        assertEquals(4, h.sweeps.size)
    }

    @Test fun lastEmptyCellComesFirst() {
        val board = solution.copyOf().also { it[40] = 0 }
        val h = HintEngine.find(board, setOf(40))!!
        assertEquals(Technique.LAST_EMPTY_CELL, h.technique)
        assertEquals(solution[40], h.digit)
        assertEquals(UnitType.BOX, h.unit!!.type) // boxes are scanned first
    }

    @Test fun nakedSingleNamesTheEightBlockingDigits() {
        val board = solution.copyOf().also { it[40] = 0 } // r5c5 is a 7
        val h = HintEngine.nakedSingle(board, 40)!!
        assertEquals(7, h.digit)
        assertEquals(8, h.eliminators.size)
        assertEquals((1..9).filter { it != 7 }.toSet(), h.eliminators.map { board[it] }.toSet())
        assertTrue(h.eliminators.all { Cells.sees(it, 40) })
        // in digit order, so Show me rings them one by one like counting: 1, 2, 3 ...
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 8, 9), h.eliminators.map { board[it] })
        assertEquals("It sees 1, 2, 3, 4, 5, 6, 8 and 9 in its row, column and box.", h.reason)
    }

    @Test fun wrongEntriesAreIgnoredAndNeverTargeted() {
        // fill every singles cell correctly except one, which is wrong
        val entries = entriesOf(stuckBoard)
        val wrongAt = (0 until 81).first { entries[it] != 0 }
        entries[wrongAt] = if (solution[wrongAt] == 1) 2 else 1
        val r = HintEngine.next(givens, entries, solution)
        // only that wrong cell had a single; no hint may point at it, so the count comes back
        if (r is HintResult.Step) assertTrue(r.hint.target != wrongAt && entries[r.hint.target] == 0)
        else assertEquals(HintResult.Mistakes(1), r)
    }

    @Test fun fullBoardIsSolvedOrHasMistakes() {
        val all = entriesOf(solution)
        assertEquals(HintResult.Solved, HintEngine.next(givens, all, solution))
        val wrong = all.copyOf().also { it[0] = 1 } // r1c1 is a 6
        assertEquals(HintResult.Mistakes(1), HintEngine.next(givens, wrong, solution))
    }

    @Test fun hintsFromTheStartAreAlwaysRight() {
        var entries = IntArray(81)
        repeat(21) {
            val h = (HintEngine.next(givens, entries, solution) as HintResult.Step).hint
            assertTrue(h.technique != Technique.ADVANCED)
            assertEquals(solution[h.target], h.digit)
            entries = entries.copyOf().also { it[h.target] = h.digit }
        }
        val h = (HintEngine.next(givens, entries, solution) as HintResult.Step).hint
        assertEquals(Technique.ADVANCED, h.technique)
    }
}
