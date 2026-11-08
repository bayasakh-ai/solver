package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour claims in the design, checked as numbers. A palette added to
 * AnswerPalette must pass the same bars, so "colourblind-safe" stays true.
 *
 * Thresholds (OKLab distance, worst case over normal/protan/deutan/tritan vision):
 *  - vs warning and amber signal colours >= 0.10: well past "barely visible" (~0.02);
 *    Ink blue's worst case is 0.169, the rejected Green/Magenta score 0.023/0.036.
 *  - vs printed digits: deep >= 0.15, bright >= 0.08. Answers also differ from print
 *    by weight and serif shape, so colour needs less margin here; the bright step on
 *    a dark screen is the tight case (Ink blue 0.088 under protanopia).
 */
class AnswerInkTest {

    private val minVsSignals = 0.10
    private val minDeepVsPrint = 0.15
    private val minBrightVsPrint = 0.08

    @Test
    fun designContrastNumbersHold() {
        // "Paper · 7.5:1 on white", "Dark screen · 10.2:1" in the v2 notes.
        assertEquals(7.5, ColorMath.contrast(0x1D4FB0, SignalColors.WHITE_PAPER), 0.05)
        assertEquals(10.2, ColorMath.contrast(0x8CC8FF, SignalColors.DARK_SCREEN), 0.05)
        // Accent on the ground is only 3:1 (chrome and strokes); accent-700 is text-safe.
        assertEquals(3.0, ColorMath.contrast(0xB68235, 0xF3F2F2), 0.05)
        assertTrue(ColorMath.contrast(0x7D5411, 0xF3F2F2) >= 4.5)
    }

    @Test
    fun everyPaletteHasEnoughContrast() {
        for (p in AnswerPalette.entries) {
            val deep = ColorMath.contrast(p.deep, SignalColors.WHITE_PAPER)
            val bright = ColorMath.contrast(p.bright, SignalColors.DARK_SCREEN)
            assertTrue("$p deep on white $deep", deep >= 4.5)
            assertTrue("$p bright on dark $bright", bright >= 4.5)
        }
        val inkDeep = ColorMath.contrast(AnswerPalette.INK_BLUE.deep, SignalColors.WHITE_PAPER)
        val hcDeep = ColorMath.contrast(AnswerPalette.HIGH_CONTRAST.deep, SignalColors.WHITE_PAPER)
        assertTrue("high contrast must beat the default on paper", hcDeep >= 12.0 && hcDeep > inkDeep)
        assertTrue(AnswerPalette.HIGH_CONTRAST.haloScale > AnswerPalette.INK_BLUE.haloScale)
    }

    @Test
    fun everyPaletteStaysDistinctUnderEveryColorVision() {
        for (p in AnswerPalette.entries) {
            for (s in SignalColors.onPaper) {
                val d = ColorMath.worstDistance(p.deep, s)
                assertTrue("$p deep vs %06X: %.3f".format(s, d), d >= minVsSignals)
            }
            for (s in SignalColors.onDark) {
                val d = ColorMath.worstDistance(p.bright, s)
                assertTrue("$p bright vs %06X: %.3f".format(s, d), d >= minVsSignals)
            }
            val dp = ColorMath.worstDistance(p.deep, SignalColors.PRINT_ON_PAPER)
            val bp = ColorMath.worstDistance(p.bright, SignalColors.PRINT_ON_DARK_SCREEN)
            assertTrue("$p deep vs print %.3f".format(dp), dp >= minDeepVsPrint)
            assertTrue("$p bright vs print %.3f".format(bp), bp >= minBrightVsPrint)
        }
    }

    /** Records why these were dropped from Settings; re-adding one would fail above. */
    @Test
    fun rejectedPalettesReallyFail() {
        fun worstVsSignals(deep: Int, bright: Int) = minOf(
            SignalColors.onPaper.minOf { ColorMath.worstDistance(deep, it) },
            SignalColors.onDark.minOf { ColorMath.worstDistance(bright, it) },
        )
        val green = worstVsSignals(0x17703A, 0x6FE39A)
        val magenta = worstVsSignals(0xA3195B, 0xFF9CCB)
        val gold = worstVsSignals(0x7D5411, 0xFACB8D)
        assertTrue("green $green", green < minVsSignals)
        assertTrue("magenta $magenta", magenta < minVsSignals)
        assertTrue("gold $gold", gold < minVsSignals)
        // Green specifically: protanopes see it as the amber hint stroke.
        assertTrue(ColorMath.distance(0x6FE39A, SignalColors.HINT_STROKE_DARK, ColorVision.PROTAN) < 0.03)
    }

    @Test
    fun stepSwitchesAtMidGrey() {
        val p = AnswerPalette.INK_BLUE
        assertEquals(AnswerStep.DEEP, p.stepFor(1.0))
        assertEquals(AnswerStep.DEEP, p.stepFor(ColorMath.luminance(0xE8E2D6)))   // newsprint
        assertEquals(AnswerStep.BRIGHT, p.stepFor(ColorMath.luminance(0x161616))) // dark-mode app
        assertEquals(AnswerStep.BRIGHT, p.stepFor(0.0))
        // Crossover: equal contrast when (L+0.05)^2 = (Ldeep+0.05)(Lbright+0.05) -> L ~ 0.2375.
        assertEquals(AnswerStep.DEEP, p.stepFor(0.25))
        assertEquals(AnswerStep.BRIGHT, p.stepFor(0.22))
    }

    @Test
    fun cameraLumaIsLinearisedBeforeChoosing() {
        val p = AnswerPalette.INK_BLUE
        // Switch sits near Y = 134 once linearised (not Y = 61 as Y/255 would suggest).
        assertEquals(AnswerStep.DEEP, p.stepFor(ColorMath.luminanceFromLuma(140)))
        assertEquals(AnswerStep.BRIGHT, p.stepFor(ColorMath.luminanceFromLuma(128)))
        assertEquals(AnswerStep.BRIGHT, p.stepFor(ColorMath.luminanceFromLuma(80)))
    }

    @Test
    fun haloContrastsWithItsStep() {
        for (p in AnswerPalette.entries) for (s in AnswerStep.entries) {
            assertTrue(ColorMath.contrast(p.color(s), p.halo(s)) >= 7.0)
        }
    }

    @Test
    fun normalVisionIsUnchangedAndSameColorHasZeroDistance() {
        val c = 0x1D4FB0
        val normal = ColorMath.simulate(c, ColorVision.NORMAL)
        assertEquals(ColorMath.srgbToLinear(0x1D), normal[0], 1e-12)
        assertEquals(0.0, ColorMath.distance(c, c, ColorVision.DEUTAN), 1e-12)
    }
}
