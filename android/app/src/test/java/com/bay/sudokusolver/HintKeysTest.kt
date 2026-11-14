package com.bay.sudokusolver

import com.bay.sudokusolver.ui.play.HintKeys
import com.bay.sudokusolver.ui.play.STILL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Show me's timelines (ui/play/HintOverlay.kt): each technique tells its story in order
 * (its own marks, then the glow, then the digit) and the still frame used for level 2
 * and with animations off shows everything.
 */
class HintKeysTest {

    private val singles = listOf(Technique.LAST_EMPTY_CELL, Technique.HIDDEN_SINGLE, Technique.NAKED_SINGLE)

    @Test fun marksComeOneByOneAndFinishBeforeTheGlow() {
        for (tech in listOf(Technique.NAKED_SINGLE, Technique.LAST_EMPTY_CELL)) {
            val n = 8
            var t = 0f
            while (t <= 1f) {
                val p = (0 until n).map { HintKeys.oneByOne(t, it, n, tech) }
                // in turn: a mark never runs ahead of the one before it, and only one moves at a time
                assertTrue("$tech t=$t $p", p.zipWithNext().all { (a, b) -> a >= b })
                assertTrue("$tech t=$t $p", p.count { it > 0f && it < 1f } <= 1)
                if (HintKeys.glow(t, tech) > 0f) assertTrue("$tech t=$t: glow before the marks are done", p.all { it == 1f })
                t += 0.005f
            }
            assertTrue((0 until n).all { HintKeys.oneByOne(HintKeys.ONE_BY_ONE_FROM, it, n, tech) == 0f })
        }
    }

    @Test fun theDigitWritesAfterTheGlowStarts() {
        for (tech in singles) {
            var t = 0f
            while (t <= 0.9f) {
                if (HintKeys.writeAlpha(t, tech) > 0f) assertEquals("$tech t=$t", 1f, HintKeys.glow(t, tech), 1e-4f)
                t += 0.005f
            }
            assertEquals(1.5f, HintKeys.writeScale(0f, tech), 1e-6f)
            assertEquals(1f, HintKeys.writeScale(0.9f, tech), 1e-6f)
        }
        // less to show, so the last empty cell is filled sooner; the naked single counts eight digits first
        assertTrue(HintKeys.glowAt(Technique.LAST_EMPTY_CELL) < HintKeys.glowAt(Technique.HIDDEN_SINGLE))
        assertTrue(HintKeys.glowAt(Technique.HIDDEN_SINGLE) < HintKeys.glowAt(Technique.NAKED_SINGLE))
    }

    @Test fun theStillFrameShowsEverything() {
        for (tech in singles) {
            assertEquals(1f, HintKeys.glow(STILL, tech), 1e-6f)
            assertEquals(1f, HintKeys.writeAlpha(STILL, tech), 1e-6f)
            assertEquals(1f, HintKeys.oneByOne(STILL, 7, 8, tech), 1e-6f)
            assertEquals(2, HintKeys.step(STILL, tech))
        }
        assertEquals(1f, HintKeys.lightUp(STILL), 1e-6f)
        assertEquals(1f, HintKeys.sweep(STILL), 1e-6f)
        assertEquals(1f, HintKeys.cross(STILL), 1e-6f)
    }

    @Test fun stepLinesFollowTheStory() {
        for (tech in singles) {
            assertEquals(0, HintKeys.step(0.01f, tech))
            assertEquals(1, HintKeys.step(HintKeys.glowAt(tech) - 0.05f, tech))
            assertEquals(2, HintKeys.step(HintKeys.glowAt(tech) + 0.11f, tech))
        }
        // the hidden single keeps the design's keyframes
        assertEquals(0, HintKeys.step(0.30f))
        assertEquals(1, HintKeys.step(0.50f))
        assertEquals(0.40f, HintKeys.glowAt(Technique.HIDDEN_SINGLE), 1e-6f)
    }

    @Test fun patternHintsShowThePatternThenTheEliminationThenTheDigit() {
        val patterns = Technique.values().filter { it.isPattern }
        assertEquals(4, patterns.size)
        for (tech in patterns) {
            var t = 0f
            while (t <= 0.9f) {
                val pattern = (0 until 3).map { HintKeys.patternIn(t, it, 3) }
                val removed = (0 until 2).map { HintKeys.removeIn(t, it, 2) }
                assertTrue("$tech t=$t", pattern.zipWithNext().all { (a, b) -> a >= b })
                // nothing is struck out before the whole pattern is on the board
                if (removed.any { it > 0f }) assertTrue("$tech t=$t", pattern.all { it == 1f })
                if (HintKeys.lineIn(t) > 0f) assertTrue("$tech t=$t", pattern.all { it == 1f })
                // the finish frame waits for the eliminations, the glow for the frame
                if (HintKeys.finishIn(t) > 0f) assertTrue("$tech t=$t", removed.all { it == 1f })
                if (HintKeys.glow(t, tech) > 0f) assertEquals("$tech t=$t", 1f, HintKeys.finishIn(t), 1e-4f)
                t += 0.005f
            }
            assertEquals(0, HintKeys.step(0.10f, tech))
            assertEquals(1, HintKeys.step(0.30f, tech))
            assertEquals(2, HintKeys.step(0.60f, tech))
            // level 2 and animations off: everything at once
            assertEquals(1f, HintKeys.patternIn(STILL, 2, 3), 1e-6f)
            assertEquals(1f, HintKeys.removeIn(STILL, 1, 2), 1e-6f)
            assertEquals(1f, HintKeys.finishIn(STILL), 1e-6f)
            assertEquals(1f, HintKeys.writeAlpha(STILL, tech), 1e-6f)
        }
    }

    @Test fun popOvershootsThenSettles() {
        assertEquals(0.6f, HintKeys.pop(0f), 1e-6f)
        assertEquals(1.18f, HintKeys.pop(0.5f), 1e-6f)
        assertEquals(1f, HintKeys.pop(1f), 1e-6f)
    }
}
