package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/**
 * Checks that Polarity.kt gives the SAME vote as grid.py for the same flattened grids,
 * the way DigitClassifierTest does for the model. Plain JVM: Polarity has no Android
 * or OpenCV imports, so no phone is needed.
 * Fixtures come from export_polarity_cases.py, in app/src/test/resources/polarity/:
 * one PNG per grid + expected.txt lines `name inverted light dark looks_dark`.
 * Exact vote counts are compared, not just the verdict, so an off-by-one in the
 * percentile rule or the MIN_SPREAD cut-off shows up even when the verdict agrees.
 */
class PolarityParityTest {

    private class Case(val name: String, val inverted: Boolean, val light: Int, val dark: Int, val looksDark: Boolean)

    private fun resource(name: String) =
        javaClass.classLoader!!.getResourceAsStream("polarity/$name")
            ?: error("missing test resource polarity/$name — run export_polarity_cases.py")

    private fun loadGray(name: String): Pair<ByteArray, Int> {
        val img = resource("$name.png").use { ImageIO.read(it) }
        assertEquals("$name must be square", img.width, img.height)
        val samples = img.raster.getSamples(0, 0, img.width, img.height, 0, null as IntArray?)
        return ByteArray(samples.size) { samples[it].toByte() } to img.width
    }

    private fun cases(): List<Case> =
        resource("expected.txt").bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
            val f = line.trim().split(Regex("\\s+"))
            Case(f[0], f[1] == "1", f[2].toInt(), f[3].toInt(), f[4] == "1")
        }

    @Test fun matchesPythonVotes() {
        val cases = cases()
        assertTrue("expected.txt is empty", cases.isNotEmpty())
        val grids = cases.map { it.name }.distinct().associateWith { loadGray(it) }
        val mismatches = cases.mapNotNull { c ->
            val (stored, size) = grids.getValue(c.name)
            val pixels = if (c.inverted) ByteArray(stored.size) { (255 - (stored[it].toInt() and 0xFF)).toByte() } else stored
            val (light, dark) = Polarity.cellVotes(pixels, size)
            val verdict = Polarity.looksDark(pixels, size)
            if (light == c.light && dark == c.dark && verdict == c.looksDark) null
            else "${c.name}${if (c.inverted) " (inverted)" else ""}: Python ${c.light}/${c.dark} dark=${c.looksDark}, Kotlin $light/$dark dark=$verdict"
        }
        assertTrue("Kotlin and Python disagree:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }
}
