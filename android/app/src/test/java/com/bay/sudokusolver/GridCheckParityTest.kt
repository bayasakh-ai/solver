package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/**
 * GridCheck.kt must score flattened quads exactly like grid.box_line_score. Plain JVM.
 * Fixtures from export_grid_check_cases.py, in app/src/test/resources/gridcheck/: one
 * line-ink PNG per quad + expected.txt lines `name score accepted`. Real grids (synth,
 * photos) and the false quads found on the owner's no-grid photos (book cover, title,
 * keyboard), so the verdict is tested on both sides of the threshold.
 */
class GridCheckParityTest {

    private fun resource(name: String) =
        javaClass.classLoader!!.getResourceAsStream("gridcheck/$name")
            ?: error("missing test resource gridcheck/$name — run export_grid_check_cases.py")

    private fun load(name: String): Pair<ByteArray, Int> {
        val img = resource("$name.png").use { ImageIO.read(it) }
        assertEquals("$name must be square", img.width, img.height)
        val samples = img.raster.getSamples(0, 0, img.width, img.height, 0, null as IntArray?)
        return ByteArray(samples.size) { samples[it].toByte() } to img.width
    }

    @Test fun matchesPythonScores() {
        val lines = resource("expected.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        assertTrue("expected.txt is empty", lines.isNotEmpty())
        var grids = 0
        var others = 0
        for (line in lines) {
            val (name, score, accepted) = line.trim().split(" ")
            val (bin, size) = load(name)
            assertEquals(name, score.toDouble(), GridCheck.boxLineScore(bin, size), 1e-12)
            assertEquals(name, accepted == "1", GridCheck.looksLikeGrid(bin, size))
            if (accepted == "1") grids++ else others++
        }
        assertTrue("need cases on both sides of the threshold", grids > 0 && others > 0)
    }

    @Test fun drawnGridNeedsThreeOfItsFourBoxLines() {
        val size = 450
        // lines at i/9; skipRow / skipCol leave one horizontal / vertical line out
        fun grid(skipRow: Int? = null, skipCol: Int? = null) = ByteArray(size * size).also { b ->
            for (i in 0..9) {
                val at = minOf(i * size / 9, size - 1)
                for (k in 0 until size) {
                    if (i != skipRow) b[at * size + k] = 255.toByte()
                    if (i != skipCol) b[k * size + at] = 255.toByte()
                }
            }
        }
        assertEquals(1.0, GridCheck.boxLineScore(grid(), size), 0.0)
        assertTrue(GridCheck.looksLikeGrid(grid(), size))
        // thin cell lines missing change nothing; one box line may be hidden (glare, a
        // finger), two may not
        assertTrue(GridCheck.looksLikeGrid(grid(skipRow = 4, skipCol = 5), size))
        assertTrue(GridCheck.looksLikeGrid(grid(skipRow = 3), size))
        assertFalse(GridCheck.looksLikeGrid(grid(skipRow = 3, skipCol = 6), size))
        assertFalse(GridCheck.looksLikeGrid(ByteArray(size * size), size))
    }

    @Test fun sameQuadFollowsAGridButNotADifferentOne() {
        val grid = floatArrayOf(100f, 100f, 300f, 100f, 300f, 300f, 100f, 300f) // side 200
        val nudged = FloatArray(8) { grid[it] + if (it % 2 == 0) 12f else -9f }
        assertTrue(GridCheck.sameQuad(nudged, grid))
        // corner order doesn't matter (findGrid relabels corners every frame)
        assertTrue(GridCheck.sameQuad(floatArrayOf(300f, 300f, 100f, 300f, 100f, 100f, 300f, 100f), grid))
        // a bigger quad around it (the page, a book cover) is another quad
        assertFalse(GridCheck.sameQuad(floatArrayOf(40f, 40f, 360f, 40f, 360f, 360f, 40f, 360f), grid))
        // moved by a third of its side
        assertFalse(GridCheck.sameQuad(FloatArray(8) { grid[it] + 70f }, grid))
    }
}
