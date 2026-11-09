package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    private val puzzle = Puzzle(
        Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
        Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"),
    )
    private val fresh = GameState(puzzle)

    @Test fun printedDigitsCannotBeChanged() {
        assertSame(fresh, fresh.enter(2, 9))  // r1c3 is a printed 5
        assertSame(fresh, fresh.erase(2))
        assertSame(fresh, fresh.toggleNote(2, 1))
    }

    @Test fun enterAndUndo() {
        val g = fresh.enter(0, 6)
        assertEquals(6, g.value(0))
        assertTrue(g.canUndo)
        val back = g.undo()
        assertEquals(0, back.value(0))
        assertFalse(back.canUndo)
    }

    @Test fun mistakesCountWrongDigitsWhenEnteredAndStay() {
        val g = fresh.enter(0, 1).erase(0).enter(0, 6) // r1c1 is a 6
        assertEquals(1, g.mistakes)
        assertEquals(1, g.undo().undo().mistakes)     // undo doesn't hide a mistake
    }

    @Test fun placingADigitClearsItsNotesInPeers() {
        val g = fresh.toggleNote(1, 6).toggleNote(1, 8).toggleNote(80, 6).enter(0, 6)
        assertEquals(listOf(8), g.notesOf(1))         // same row: 6 removed
        assertEquals(listOf(6), g.notesOf(80))        // not a peer: kept
        assertEquals(listOf(6, 8), g.undo().notesOf(1))
    }

    @Test fun notesOnlyOnEmptyCells() {
        val g = fresh.enter(0, 6)
        assertSame(g, g.toggleNote(0, 1))
    }

    @Test fun conflictsAreRulesNotTheAnswer() {
        val g = fresh.enter(0, 5)   // printed 5s at r1c3 (same row) and r8c1 (same column)
        assertEquals(setOf(0, 2, 63), g.conflicts())
        val wrongButLegal = fresh.enter(0, 2)         // wrong (it's 6) but clashes with nothing
        assertTrue(wrongButLegal.conflicts().isEmpty())
    }

    @Test fun remainingCounts() {
        val sixes = puzzle.givens.count { it == 6 }
        assertEquals(9 - sixes, fresh.remaining(6))
        assertEquals(9 - sixes - 1, fresh.enter(0, 6).remaining(6))
        assertEquals(55, fresh.toFill)
    }

    @Test fun finishingStopsTheClockAndTheBoard() {
        var g = fresh
        for (i in 0 until 81) if (!g.isGiven(i)) g = g.enter(i, puzzle.solution[i])
        assertTrue(g.finished)
        assertSame(g, g.tick(1000))
        assertSame(g, g.erase(0))
        assertEquals(0, g.mistakes)
    }

    @Test fun revealWritesTheAnswer() {
        val g = fresh.reveal(6)
        assertEquals(2, g.value(6))
        assertEquals(0, g.mistakes)
        assertEquals(0, g.undo().value(6))
    }

    @Test fun timeFormat() {
        assertEquals("04:12", formatTime(252_000))
        assertEquals("1:00:05", formatTime(3_605_000))
        assertEquals(1500, fresh.tick(1000).tick(500).elapsedMs)
    }

    @Test fun puzzlesCompareByContent() {
        assertEquals(puzzle, Puzzle(puzzle.givens.copyOf(), puzzle.solution.copyOf()))
        assertEquals(Difficulty.HARD, puzzle.difficulty)
    }
}
