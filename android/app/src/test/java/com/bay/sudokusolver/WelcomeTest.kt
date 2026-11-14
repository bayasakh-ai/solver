package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeTest {

    @Test fun aNewUserGetsThreePagesEndingOnTheCamera() {
        var w = Welcome(cameraAllowed = false)
        assertEquals(listOf(WelcomePage.HELLO, WelcomePage.HOW, WelcomePage.CAMERA), w.pages)
        assertTrue(w.isFirst)
        assertNull(w.end)
        w = w.next()
        assertEquals(WelcomePage.HOW, w.page)
        assertEquals(1, w.index)
        assertNull(w.end)
        w = w.next()
        assertEquals(WelcomePage.CAMERA, w.page)
        assertTrue(w.isLast)
        assertEquals(WelcomeEnd.ALLOW_CAMERA, w.end)
        assertEquals(w, w.next()) // nothing after the last page
    }

    @Test fun backWalksToTheFirstPageAndStopsThere() {
        val last = Welcome(cameraAllowed = false).next().next()
        assertEquals(WelcomePage.HOW, last.back()!!.page)
        assertEquals(WelcomePage.HELLO, last.back()!!.back()!!.page)
        assertNull(Welcome(cameraAllowed = false).back())
    }

    @Test fun skipStopsOnTheCameraPageNotPastIt() {
        val w = Welcome(cameraAllowed = false)
        assertTrue(w.canSkip)
        assertEquals(WelcomePage.CAMERA, w.skip().page)
        assertEquals(WelcomeEnd.ALLOW_CAMERA, w.skip().end)
        // from the second page Skip would be the same as Next
        assertFalse(w.next().canSkip)
        assertFalse(w.skip().canSkip)
    }

    @Test fun cameraAlreadyAllowedLeavesTheCameraPageOut() {
        val w = Welcome(cameraAllowed = true)
        assertEquals(listOf(WelcomePage.HELLO, WelcomePage.HOW), w.pages)
        assertFalse(w.canSkip) // two pages: nothing to pass over
        assertEquals(WelcomeEnd.START, w.next().end)
        assertTrue(w.next().isLast)
    }

    @Test fun aReplayFromSettingsEndsOnDone() {
        assertEquals(WelcomeEnd.DONE, Welcome(cameraAllowed = true, replay = true).next().end)
        // still not allowed: the replay can ask too
        assertEquals(WelcomeEnd.ALLOW_CAMERA, Welcome(cameraAllowed = false, replay = true).skip().end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun theCameraPageDoesNotExistOnceTheCameraIsAllowed() {
        Welcome(cameraAllowed = true, page = WelcomePage.CAMERA)
    }

    @Test fun aRefusalTheUserSawIsNotMistakenForASilentOne() {
        // first refusal: rationale turns on
        assertTrue(CameraAsk.dialogWasShown(rationaleBefore = false, rationaleAfter = true, elapsedMs = 2000))
        // second refusal: rationale turns off again, but it was on before
        assertTrue(CameraAsk.dialogWasShown(rationaleBefore = true, rationaleAfter = false, elapsedMs = 1500))
        // quick fingers on a dialog that was shown: the rationale flags still say so
        assertTrue(CameraAsk.dialogWasShown(rationaleBefore = true, rationaleAfter = false, elapsedMs = 200))
        // dismissed without choosing (Android 11+): flags stay off, the time says a dialog was up
        assertTrue(CameraAsk.dialogWasShown(rationaleBefore = false, rationaleAfter = false, elapsedMs = 3000))
    }

    @Test fun refusedTwiceBeforeMeansNoDialog() {
        assertFalse(CameraAsk.dialogWasShown(rationaleBefore = false, rationaleAfter = false, elapsedMs = 60))
        assertFalse(CameraAsk.dialogWasShown(false, false, CameraAsk.NO_DIALOG_MS - 1))
        assertTrue(CameraAsk.dialogWasShown(false, false, CameraAsk.NO_DIALOG_MS))
    }
}
