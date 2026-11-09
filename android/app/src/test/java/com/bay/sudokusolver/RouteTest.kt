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
}
