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

    private fun y(v: Int) = ColorMath.luminanceFromLuma(v)

    @Test
    fun gridDefaultHoldsOnGreyPaperAndSwitchesOnlyInRealShadow() {
        val p = AnswerPalette.INK_BLUE
        // paper: deep everywhere, including grey newsprint (puzzle4: Y 113-147)
        for (v in listOf(255, 220, 147, 134, 120, 113, 110)) assertEquals("paper Y $v", AnswerStep.DEEP, p.stepFor(y(v)))
        // a real shadow: bright is >= 2x better from Y 109 down (Y 74: 5.0:1 vs 1.2:1)
        for (v in listOf(109, 100, 74, 0)) assertEquals("shadow Y $v", AnswerStep.BRIGHT, p.stepFor(y(v)))
        // dark screen: bright, unless a cell is light enough (Y >= 162) for deep to win 2x
        for (v in listOf(0, 22, 100, 155, 161)) assertEquals("screen Y $v", AnswerStep.BRIGHT, p.stepFor(y(v), screen = true))
        for (v in listOf(162, 220, 255)) assertEquals("screen Y $v", AnswerStep.DEEP, p.stepFor(y(v), screen = true))
    }

    @Test
    fun cameraLumaIsLinearisedBeforeChoosing() {
        // Treating Y/255 as linear would call Y 120 luminance 0.47 (deep far ahead);
        // linearised it is 0.19, where deep is only 1.7:1 and bright 2.5:1.
        assertEquals(0.188, y(120), 0.001)
        assertEquals(1.46, AnswerPalette.INK_BLUE.advantage(y(120), AnswerStep.DEEP), 0.01)
    }

    private fun flat(y: Int) = IntArray(81) { y }

    @Test
    fun hysteresisHoldsAtTheShadowEdge() {
        // Ink blue on paper: to bright at Y <= 109, back to deep only at Y >= 114
        // (2x, then 2x / 1.15; computed in answer_ink.py with the same formulas).
        val steps = AnswerSteps()
        val p = AnswerPalette.INK_BLUE
        assertEquals(AnswerStep.DEEP, steps.update(flat(200), p)[0])
        repeat(30) { steps.update(flat(110), p) }
        assertEquals(AnswerStep.DEEP, steps.update(flat(110), p)[0])
        repeat(30) { steps.update(flat(109), p) }
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(109), p)[0])
        repeat(30) { steps.update(flat(113), p) }
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(113), p)[0])
        repeat(30) { steps.update(flat(114), p) }
        assertEquals(AnswerStep.DEEP, steps.update(flat(114), p)[0])
    }

    @Test
    fun hysteresisOnAScreen() {
        // dark screen: to deep at Y >= 162, back to bright at Y <= 155
        val steps = AnswerSteps()
        val p = AnswerPalette.INK_BLUE
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(20), p, screen = true)[0])
        repeat(30) { steps.update(flat(162), p, screen = true) }
        assertEquals(AnswerStep.DEEP, steps.update(flat(162), p, screen = true)[0])
        repeat(30) { steps.update(flat(156), p, screen = true) }
        assertEquals(AnswerStep.DEEP, steps.update(flat(156), p, screen = true)[0])
        repeat(30) { steps.update(flat(155), p, screen = true) }
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(155), p, screen = true)[0])
    }

    @Test
    fun smoothingIgnoresAOneFrameSpike() {
        val steps = AnswerSteps()
        val p = AnswerPalette.INK_BLUE
        steps.update(flat(200), p)
        // one frame of a dark flash (exposure step, hand shadow): 0.3 * (40 - 200) -> Y 152
        assertEquals(AnswerStep.DEEP, steps.update(flat(40), p)[0])
    }

    @Test
    fun cellsChooseIndependentlyAndResetStartsFresh() {
        val steps = AnswerSteps()
        val p = AnswerPalette.INK_BLUE
        val half = IntArray(81) { if (it % 9 < 4) 220 else 70 } // hard shadow over the right side
        val s = steps.update(half, p)
        assertEquals(AnswerStep.DEEP, s[0])
        assertEquals(AnswerStep.BRIGHT, s[8])
        steps.reset()
        // without the reset, cell 0 would be smoothed to 220 + 0.3 * (30 - 220) = 163 -> deep
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(30), p)[0])
    }

    @Test
    fun paletteOrScreenChangeRechoosesWithoutHysteresis() {
        val steps = AnswerSteps()
        repeat(30) { steps.update(flat(100), AnswerPalette.INK_BLUE) }
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(100), AnswerPalette.INK_BLUE)[0])
        // High contrast's deep is darker, so its paper switch is lower (Y <= 91): Y 100 is deep
        assertEquals(AnswerStep.DEEP, steps.update(flat(100), AnswerPalette.HIGH_CONTRAST)[0])
        // the same cells judged as a dark screen default to bright at once
        assertEquals(AnswerStep.BRIGHT, steps.update(flat(100), AnswerPalette.HIGH_CONTRAST, screen = true)[0])
    }

    @Test
    fun cellMedianUsesInteriorAndCameraPolarity() {
        val size = 450
        val px = ByteArray(size * size) { 200.toByte() }
        // cell 0: grid line on its border (outside the 5 px margin) + a digit covering 30%
        for (y in 0 until 50) for (x in 0 until 3) px[y * size + x] = 0
        for (y in 10 until 30) for (x in 10 until 40) px[y * size + x] = 20
        val m = CellBackground.medians(px, size, inverted = false)
        assertEquals(200, m[0])
        assertEquals(200, m[80])
        assertEquals(55, CellBackground.medians(px, size, inverted = true)[0])
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
