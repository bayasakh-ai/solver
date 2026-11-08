package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/**
 * Checks CellBackground.medians and AnswerPalette.stepFor give the SAME numbers as
 * grid.cell_backgrounds + answer_ink.step_for for the same flattened grids.
 * Fixtures come from export_background_cases.py, in app/src/test/resources/background/:
 * one PNG per grid (polarity it was found in) + expected.txt lines
 * `name searched_inverted m0..m80 steps_ink_blue steps_high_contrast`.
 * All 81 medians are compared, not just the steps, so an off-by-one in the
 * median index or the margin shows up even when every step agrees.
 */
class BackgroundParityTest {

    private class Case(val name: String, val inverted: Boolean, val medians: IntArray,
                       val inkBlue: String, val highContrast: String)

    private fun resource(name: String) =
        javaClass.classLoader!!.getResourceAsStream("background/$name")
            ?: error("missing test resource background/$name — run export_background_cases.py")

    private fun loadGray(name: String): Pair<ByteArray, Int> {
        val img = resource("$name.png").use { ImageIO.read(it) }
        assertEquals("$name must be square", img.width, img.height)
        val samples = img.raster.getSamples(0, 0, img.width, img.height, 0, null as IntArray?)
        return ByteArray(samples.size) { samples[it].toByte() } to img.width
    }

    private fun cases(): List<Case> =
        resource("expected.txt").bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
            val f = line.trim().split(Regex("\\s+"))
            require(f.size == 2 + 81 + 2) { "bad line: ${f[0]}" }
            Case(f[0], f[1] == "1", IntArray(81) { f[2 + it].toInt() }, f[83], f[84])
        }

    private fun steps(medians: IntArray, palette: AnswerPalette) = medians.joinToString("") {
        if (palette.stepFor(ColorMath.luminanceFromLuma(it)) == AnswerStep.DEEP) "D" else "B"
    }

    @Test fun matchesPythonBackgroundsAndSteps() {
        val cases = cases()
        assertTrue("expected.txt is empty", cases.size >= 4)
        val mismatches = cases.mapNotNull { c ->
            val (pixels, size) = loadGray(c.name)
            val m = CellBackground.medians(pixels, size, c.inverted)
            val ink = steps(m, AnswerPalette.INK_BLUE)
            val hc = steps(m, AnswerPalette.HIGH_CONTRAST)
            val bad = (0 until 81).filter { m[it] != c.medians[it] }
            when {
                bad.isNotEmpty() -> "${c.name}: medians differ in cells $bad " +
                    "(Python ${bad.map { c.medians[it] }}, Kotlin ${bad.map { m[it] }})"
                ink != c.inkBlue -> "${c.name}: Ink blue steps\n  Python ${c.inkBlue}\n  Kotlin $ink"
                hc != c.highContrast -> "${c.name}: High contrast steps\n  Python ${c.highContrast}\n  Kotlin $hc"
                else -> null
            }
        }
        assertTrue("Kotlin and Python disagree:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }
}
