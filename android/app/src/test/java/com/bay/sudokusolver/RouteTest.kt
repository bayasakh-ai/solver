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

    @Test(expected = IllegalArgumentException::class)
    fun theCameraIsAlwaysTheRoot() {
        NavStack(listOf(Route.Settings))
    }
}
