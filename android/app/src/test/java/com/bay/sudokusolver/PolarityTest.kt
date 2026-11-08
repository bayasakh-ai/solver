package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dark-mode detection on hand-drawn 450x450 "warped grids" (no OpenCV needed). */
class PolarityTest {

    private val size = 450
    private val step = size / 9

    /** Light paper, dark grid lines, a blocky dark "digit" in about a third of the cells. */
    private fun paperGrid(paper: Int = 230, ink: Int = 30): IntArray {
        val img = IntArray(size * size) { paper }
        for (y in 0 until size) for (x in 0 until size) {
            val onLine = (0..9).any { k -> val p = minOf(k * step, size - 1); kotlin.math.abs(x - p) <= 1 || kotlin.math.abs(y - p) <= 1 }
            if (onLine) img[y * size + x] = ink
        }
        for (cell in 0 until 81 step 3) { // 27 filled cells
            val r = cell / 9; val c = cell % 9
            for (y in r * step + 15 until r * step + 35) for (x in c * step + 20 until c * step + 30) {
                img[y * size + x] = ink
            }
        }
        return img
    }

    private fun IntArray.toBytes() = ByteArray(size) { this[it].coerceIn(0, 255).toByte() }
    private fun IntArray.inverted() = IntArray(size) { 255 - this[it] }
    private fun median(img: IntArray) = img.sorted()[img.size / 2]

    @Test fun paperIsNotDark() {
        assertFalse(Polarity.looksDark(paperGrid().toBytes(), size))
    }

    @Test fun darkModeScreenIsDark() {
        assertTrue(Polarity.looksDark(paperGrid().inverted().toBytes(), size))
    }

    /** The case a single brightness threshold gets wrong: a hard shadow over most of the page. */
    @Test fun shadowedPaperIsNotDark() {
        val img = paperGrid()
        for (y in 0 until size) for (x in 0 until size) {
            if (x > 100) img[y * size + x] = img[y * size + x] * 30 / 100 // paper ~69, ink ~9
        }
        assertTrue("whole-grid median should look dark", median(img) < 110)
        assertFalse(Polarity.looksDark(img.toBytes(), size))
    }

    @Test fun emptyGridDefaultsToPaper() {
        assertFalse(Polarity.looksDark(ByteArray(size * size) { 200.toByte() }, size))
    }

    @Test fun missTriesTheOtherPolarityNextFrame() {
        val p = GridPolarity()
        assertFalse(p.searchInverted)
        p.onMiss()
        assertTrue(p.searchInverted)
        p.onMiss()
        assertFalse(p.searchInverted)
    }

    @Test fun darkPageFoundInNormalFrameIsInvertedAndSearchedInvertedNext() {
        val p = GridPolarity()
        assertTrue(p.onFound(searchedInverted = false, warpedLooksDark = true))
        assertTrue(p.searchInverted)
    }

    @Test fun darkPageFoundInInvertedFrameIsReadAsIs() {
        val p = GridPolarity()
        p.onMiss()
        assertFalse(p.onFound(searchedInverted = true, warpedLooksDark = false))
        assertTrue(p.searchInverted)
    }

    @Test fun paperFoundInInvertedFrameIsInvertedBackAndSearchedNormalNext() {
        val p = GridPolarity()
        p.onMiss()
        assertTrue(p.onFound(searchedInverted = true, warpedLooksDark = true))
        assertFalse(p.searchInverted)
    }

    @Test fun wrongSizeIsRejected() {
        val e = runCatching { Polarity.looksDark(ByteArray(10), size) }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, e?.javaClass)
    }
}
