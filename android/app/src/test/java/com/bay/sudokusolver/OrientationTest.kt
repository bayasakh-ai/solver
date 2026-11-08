package com.bay.sudokusolver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationTest {

    private val puzzle = Solver.parse(
        "....6...2 4...156.. ...7...9. ...6..1.7 .7.....8. 3.6..9... .5...8... ..149...3 8...5....")

    /** np.rot90(grid, k): k quarter turns counterclockwise. */
    private fun rot90(g: IntArray, k: Int): IntArray {
        var cur = g
        repeat(k.mod(4)) { val src = cur; cur = IntArray(81) { i -> src[(i % 9) * 9 + (8 - i / 9)] } }
        return cur
    }

    @Test fun consistencyCatchesDuplicatesInRowColumnAndBox() {
        assertTrue(Orientation.isConsistent(puzzle))
        assertTrue(Orientation.isConsistent(IntArray(81)))
        val row = puzzle.copyOf().also { it[1] = 6 }        // row 0 already has a 6
        val col = puzzle.copyOf().also { it[9 * 8 + 4] = 6 } // column 4 already has a 6 (r0)
        val box = puzzle.copyOf().also { it[10] = 4 }        // box 0 already has a 4 (r1c0)
        assertFalse(Orientation.isConsistent(row))
        assertFalse(Orientation.isConsistent(col))
        assertFalse(Orientation.isConsistent(box))
    }

    @Test fun aTurnedGridStaysConsistentSoOnlyTheReadingCanTell() {
        // turning a valid grid keeps it valid: consistency checks the READING of a turn,
        // where sideways digits come out as wrong digits and collide
        for (k in 0 until 4) assertTrue(Orientation.isConsistent(rot90(puzzle, k)))
    }

    @Test fun chooseTurnPrefersConsistentThenScoreThenLowerK() {
        // python orientation.choose_turn: same cases
        assertEquals(2, Orientation.chooseTurn(doubleArrayOf(0.95, 0.99, 0.97, 0.90),
            booleanArrayOf(true, false, true, false)))   // 0.99 is a sideways misread
        assertEquals(1, Orientation.chooseTurn(doubleArrayOf(0.95, 0.99, 0.97, 0.90),
            booleanArrayOf(false, false, false, false))) // nothing consistent: best score
        assertEquals(0, Orientation.chooseTurn(doubleArrayOf(0.98, 0.90, 0.98, 0.90),
            booleanArrayOf(true, true, true, true)))     // tie: keep the grid as found
    }

    @Test fun rollRelabelsCornersLikeNumpy() {
        val flat = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f) // TL TR BR BL
        assertArrayEquals(flat, Orientation.roll(flat, 0), 0f)
        assertArrayEquals(floatArrayOf(10f, 0f, 10f, 10f, 0f, 10f, 0f, 0f), Orientation.roll(flat, 1), 0f)
        assertArrayEquals(Orientation.roll(flat, 3), Orientation.roll(flat, -1), 0f)
        assertEquals(listOf("TR", "BR", "BL", "TL"), Orientation.roll(listOf("TL", "TR", "BR", "BL"), 1))
    }

    /** Corners of a square turned by [deg] around (240, 320), labelled like GridDetector.orderCorners. */
    private fun screenLabelled(deg: Double): FloatArray {
        val a = Math.toRadians(deg)
        val pts = listOf(-150.0 to -150.0, 150.0 to -150.0, 150.0 to 150.0, -150.0 to 150.0).map { (x, y) ->
            (240 + x * cos(a) - y * sin(a)).toFloat() to (320 + x * sin(a) + y * cos(a)).toFloat()
        }
        val tl = pts.minBy { it.first + it.second }; val br = pts.maxBy { it.first + it.second }
        val tr = pts.minBy { it.second - it.first }; val bl = pts.maxBy { it.second - it.first }
        return floatArrayOf(tl.first, tl.second, tr.first, tr.second, br.first, br.second, bl.first, bl.second)
    }

    @Test fun trackingFollowsThePaperWhileThePhoneTurnsPast45Degrees() {
        // turn 2 degrees per frame from 0 to 120: screen labels flip at 45 and 90+45,
        // the tracked labels must keep the same physical corner as top-left throughout
        var tracked = screenLabelled(0.0)
        val physicalTl = tracked.copyOfRange(0, 2)
        var deg = 0.0
        while (deg < 120.0) {
            deg += 2.0
            val raw = screenLabelled(deg)
            tracked = Orientation.roll(raw, Orientation.bestRoll(raw, tracked))
            // where the original top-left corner is now
            val a = Math.toRadians(deg)
            val ex = (240 + (physicalTl[0] - 240) * cos(a) - (physicalTl[1] - 320) * sin(a)).toFloat()
            val ey = (320 + (physicalTl[0] - 240) * sin(a) + (physicalTl[1] - 320) * cos(a)).toFloat()
            assertEquals("x at $deg", ex, tracked[0], 0.5f)
            assertEquals("y at $deg", ey, tracked[1], 0.5f)
        }
    }

    @Test fun cornerOrderUsesEachCornerOnceEvenAt45Degrees() {
        // the rotate_steep failure: a diamond where the old min/max tests picked the
        // bottom corner as both BR and BL (corners from the failing synthetic photo)
        val xs = doubleArrayOf(455.0, 68.0, 825.0, 435.0)  // bottom, left, right, top
        val ys = doubleArrayOf(815.0, 410.0, 400.0, 37.0)
        val idx = Orientation.clockwiseFromTopLeft(xs, ys)
        assertEquals(setOf(0, 1, 2, 3), idx.toSet())
        assertArrayEquals(intArrayOf(3, 2, 0, 1), idx) // top, right, bottom, left
        // an upright grid keeps the usual TL, TR, BR, BL
        assertArrayEquals(intArrayOf(2, 3, 0, 1), Orientation.clockwiseFromTopLeft(
            doubleArrayOf(10.0, 0.0, 0.0, 10.0), doubleArrayOf(10.0, 10.0, 0.0, 0.0)))
    }

    @Test fun lockStopsAtAConsistentTurnOrAfterMaxTries() {
        val lock = OrientationLock(maxTries = 3)
        lock.record(consistent = false); assertFalse(lock.locked)
        lock.record(consistent = false); assertFalse(lock.locked)
        lock.record(consistent = false); assertTrue(lock.locked)
        lock.reset(); assertFalse(lock.locked)
        lock.record(consistent = true); assertTrue(lock.locked)
    }
}
