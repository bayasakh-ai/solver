package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private val capture = Capture(IntArray(81), IntArray(81), FloatArray(81), IntArray(81),
        emptyList(), BooleanArray(81), 0, null)

    @Test fun startsOnTheCameraWithNothingToGoBackTo() {
        val nav = NavStack()
        assertEquals(Route.Camera, nav.current)
        assertTrue(nav.onCamera)
        assertNull(nav.back())
    }

    @Test fun screensOpenOverTheCameraAndBackReturnsToIt() {
        val check = NavStack().open(Route.Check(capture))
        assertEquals(Route.Check(capture), check.current)
        assertEquals(NavStack(), check.back())

        val settings = check.open(Route.Settings)
        assertEquals(listOf(Route.Camera, Route.Check(capture), Route.Settings), settings.stack)
        assertEquals(Route.Check(capture), settings.back()!!.current)
    }

    @Test fun doubleTapDoesNotStackTheSameScreenTwice() {
        val once = NavStack().open(Route.Settings)
        assertSame(once, once.open(Route.Settings))
    }

    @Test fun openingTheCameraClearsTheStack() {
        assertEquals(NavStack(), NavStack().open(Route.Settings).open(Route.Check(capture)).open(Route.Camera))
    }

    @Test fun theCoreFlowStacksCheckChoosePlay() {
        val puzzle = Puzzle(Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
            Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"))
        val play = NavStack().open(Route.Check(capture)).open(Route.Choose(puzzle)).open(Route.Play(puzzle, PlayMode.HINTS))
        assertEquals(4, play.stack.size)
        assertEquals(Route.Choose(puzzle), play.back()!!.current)
        // a puzzle with the same digits is the same route (Puzzle compares by content)
        assertSame(play, play.open(Route.Play(Puzzle(puzzle.givens.copyOf(), puzzle.solution.copyOf()), PlayMode.HINTS)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun theCameraIsAlwaysTheRoot() {
        NavStack(listOf(Route.Settings))
    }

    private val p3 = Puzzle(Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
        Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"))

    @Test fun chooseComparesTheStartToo() {
        val a = Route.Choose(p3, IntArray(81).also { it[0] = 6 })
        assertEquals(a, Route.Choose(p3, IntArray(81).also { it[0] = 6 }))
        assertTrue(a != Route.Choose(p3))
    }

    @Test fun savedSessionKeepsTheGameAndHowItWasPlayed() {
        val g = GameState(p3).enter(0, 6).toggleNote(1, 8).hintOpened().tick(5000)
        val saved = SavedSession.decode(SavedSession.encode(g, PlayMode.MYSELF))!!
        assertEquals(6, saved.game.value(0))
        assertEquals(listOf(8), saved.game.notesOf(1))
        assertEquals(1, saved.game.hintsUsed)
        assertEquals(5000, saved.game.elapsedMs)
        assertEquals(Route.Play(p3, PlayMode.MYSELF), saved.board)
        // Show solution is never a way to continue: back to hints
        assertEquals(PlayMode.HINTS, SavedSession.decode(SavedSession.encode(g, PlayMode.SOLUTION))!!.mode)
        assertNull(SavedSession.decode("HINTS\ngarbage"))
        assertNull(SavedSession.decode(""))
    }

    @Test fun aFinishedGameIsNotKept() {
        var g = GameState(p3)
        for (i in 0 until 81) if (!g.isGiven(i)) g = g.enter(i, p3.solution[i])
        assertNull(SavedSession.decode(SavedSession.encode(g, PlayMode.HINTS)))
        assertEquals(SavedSession.Change.Clear, SavedSession.change(null, g))
    }

    @Test fun movesRewriteTheGameTicksOnlyTheTime() {
        val g = GameState(p3)
        assertEquals(SavedSession.Change.Game, SavedSession.change(null, g))          // a new game
        assertEquals(SavedSession.Change.Time, SavedSession.change(g, g.tick(1000)))  // the clock
        assertEquals(SavedSession.Change.None, SavedSession.change(g, g))
        val moved = g.enter(0, 6)
        assertEquals(SavedSession.Change.Game, SavedSession.change(g, moved))
        assertEquals(SavedSession.Change.Game, SavedSession.change(moved, moved.undo()))
        assertEquals(SavedSession.Change.Game, SavedSession.change(g, g.toggleNote(0, 1)))
        assertEquals(SavedSession.Change.Game, SavedSession.change(g, g.hintOpened()))
        assertEquals(SavedSession.Change.Game, SavedSession.change(g, g.enter(0, 1)))  // a mistake
        assertEquals(SavedSession.Change.Game, SavedSession.change(g, GameState(Generator.generate(Difficulty.EASY)!!)))
        assertEquals(SavedSession.Change.Clear, SavedSession.change(g, null))
    }

    @Test fun timeSavedAloneAddsUpOnLoad() {
        // what GameStore.load does: the game text is from the last move, the time from the last tick
        val atMove = GameState(p3).enter(0, 6).tick(3000)
        val restored = SavedSession.decode(SavedSession.encode(atMove, PlayMode.HINTS))!!.game
        val lastTick = 65_000L
        assertEquals(65_000, restored.tick(lastTick - restored.elapsedMs).elapsedMs)
    }
}
