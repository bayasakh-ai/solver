package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Hints beyond singles (HintAdvanced.kt): one elimination (pointing, claiming, naked
 * pair, hidden pair) plus the single it leaves. Checked on generated Hard puzzles
 * played hint by hint (every hint is true and its story holds together) and on the
 * real puzzles from photos/ (resources/hints/real_puzzles.txt: `name givens`).
 *
 * Measured 2026-11-15. Real puzzles: 13 distinct ones with one solution, 5 rated Hard;
 * singles + reveal needed 1, 1, 3, 4, 4 reveals, with pattern hints 0, 0, 2, 0, 0
 * (puzzle2, puzzle3, the "expert" site, NY Times, sudoku.com). Generated Hard as it was
 * then (anything singles can't solve, 60 seeds): 46 pattern hints, 94 reveals left, 45
 * puzzles still needing one; chaining eliminations or adding triples moved that only
 * to 82 / 43. So one-step patterns cover published puzzles, not arbitrary ones, and
 * the generator now only makes Hard puzzles these hints can carry (Generator.kt).
 */
class HintAdvancedTest {

    private class Played(val techniques: List<Technique>, val hints: List<Hint>)

    /** Play [p] with hints only, writing every hinted digit (a reveal for the fallback). */
    private fun play(p: Puzzle): Played {
        val entries = IntArray(81)
        val techniques = ArrayList<Technique>()
        val hints = ArrayList<Hint>()
        while (true) {
            val h = (HintEngine.next(p.givens, entries, p.solution) as? HintResult.Step)?.hint ?: break
            assertEquals("${h.technique} at ${h.target}", p.solution[h.target], h.digit)
            assertEquals(0, p.givens[h.target] + entries[h.target])
            techniques.add(h.technique)
            hints.add(h)
            entries[h.target] = h.digit
        }
        assertTrue((0 until 81).all { p.givens[it] + entries[it] == p.solution[it] })
        return Played(techniques, hints)
    }

    private val hard: List<Puzzle> by lazy { (0 until 60).mapNotNull { Generator.generate(Difficulty.HARD, Random(it)) } }

    @Test fun everyHintIsTrueAndTheStoryHoldsTogether() {
        var patterns = 0
        for (p in hard) {
            val entries = IntArray(81)
            for (h in play(p).hints) {
                val board = IntArray(81) { p.givens[it] + entries[it] }
                if (h.technique.isPattern) {
                    patterns++
                    val cand = AdvancedHints.candidateMasks(board)
                    fun can(i: Int, d: Int) = cand[i] and (1 shl d) != 0
                    // no single existed: the pattern was needed
                    assertNull(HintEngine.find(board, (0 until 81).filterTo(HashSet()) { board[it] == 0 }))
                    assertTrue(h.pattern.size >= 2 && h.pattern.all { it in h.unit!!.cells && board[it] == 0 })
                    assertTrue(h.patternDigits.all { d -> h.pattern.any { can(it, d) } })
                    assertTrue(h.removed.isNotEmpty() && h.removed.all { it in h.line!!.cells })
                    // the removed cells really could take those digits before, and the answer is not one of them
                    assertTrue(h.removed.all { c -> h.removedDigits.any { can(c, it) } })
                    when (h.technique) {
                        Technique.POINTING -> {
                            val d = h.patternDigits.single()
                            assertEquals(UnitType.BOX, h.unit!!.type)
                            assertEquals(h.pattern, h.unit!!.cells.filter { can(it, d) })       // all the box's places
                            assertTrue(h.pattern.all { it in h.line!!.cells })                   // on one line
                            assertTrue(h.removed.none { it in h.unit!!.cells })
                        }
                        Technique.CLAIMING -> {
                            val d = h.patternDigits.single()
                            assertEquals(UnitType.BOX, h.line!!.type)
                            assertEquals(h.pattern, h.unit!!.cells.filter { can(it, d) })       // all the line's places
                            assertTrue(h.pattern.all { it in h.line!!.cells })                   // inside one box
                        }
                        Technique.NAKED_PAIR -> {
                            assertEquals(2, h.pattern.size)
                            val mask = h.patternDigits.fold(0) { m, d -> m or (1 shl d) }
                            assertTrue(h.pattern.all { cand[it] == mask })
                            assertTrue(h.removed.none { it in h.pattern })
                        }
                        else -> {
                            assertEquals(2, h.patternDigits.size)
                            for (d in h.patternDigits) assertEquals(h.pattern, h.unit!!.cells.filter { can(it, d) })
                            assertTrue(h.removed.all { it in h.pattern })
                        }
                    }
                    if (h.finish == Technique.HIDDEN_SINGLE) {
                        // without the removed cells, the digit has one place left in the finish unit
                        val left = h.finishUnit!!.cells.filter { can(it, h.digit) && it !in h.removed }
                        assertEquals(listOf(h.target), left)
                        assertEquals(listOf(h.digit), h.removedDigits)
                        assertTrue(h.crossedOut.none { it == h.target || it in h.removed })
                    } else {
                        assertEquals(Technique.NAKED_SINGLE, h.finish)
                        assertEquals(listOf(h.target), h.removed)
                        val left = (1..9).filter { can(h.target, it) && it !in h.removedDigits }
                        assertEquals(listOf(h.digit), left)
                    }
                    assertTrue(h.focus.containsAll(h.pattern + h.removed + h.target))
                    assertEquals(3, h.steps.size)
                    assertTrue(h.text.isNotBlank() && h.reason.contains("${h.digit}") && h.nudgeTitle.startsWith("Look at"))
                    assertTrue("the nudge must not give the digit away: ${h.nudgeText}", h.nudgeText.none { it.isDigit() })
                }
                entries[h.target] = h.digit
            }
        }
        assertTrue("pattern hints seen: $patterns", patterns >= hard.size)
    }

    private val real: Map<String, IntArray> by lazy {
        javaClass.classLoader!!.getResourceAsStream("hints/real_puzzles.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.associate { l -> l.split(" ").let { it[0] to Solver.parse(it[1]) } }
    }

    private fun playReal(name: String): Played {
        val givens = real.getValue(name)
        return play(Puzzle(givens, Solver.solve(givens)[0]))
    }

    @Test fun scannedHardPuzzlesAreExplainedWithoutAReveal() {
        for (name in listOf("puzzle2", "puzzle3", "screen_angle_ny_01", "screen_angle_sudokucom_01")) {
            val t = playReal(name).techniques
            assertEquals(name, Difficulty.HARD, HintEngine.rate(real.getValue(name)))
            assertTrue("$name: no pattern hint", t.any { it.isPattern })
            assertTrue("$name: still needs a reveal", t.none { it == Technique.ADVANCED })
            assertTrue(name, HintEngine.explains(real.getValue(name)))
        }
        // puzzle3 (the design's puzzle): one pointing step is all it takes
        assertEquals(listOf(Technique.POINTING), playReal("puzzle3").techniques.filter { it.isPattern })
        // every one of the four techniques turns up on real puzzles
        val seen = real.keys.flatMap { playReal(it).techniques }.filter { it.isPattern }.toSet()
        assertEquals(setOf(Technique.POINTING, Technique.CLAIMING, Technique.NAKED_PAIR, Technique.HIDDEN_PAIR), seen)
    }

    @Test fun theRevealFallbackIsStillThereWhenPatternsRunOut() {
        // the "expert" site's puzzle needs longer chains: 3 reveals before, 2 now
        val t = playReal("screen_angle_expert_01").techniques
        assertEquals(2, t.count { it == Technique.ADVANCED })
        assertEquals(1, t.count { it.isPattern })
        assertTrue(!HintEngine.explains(real.getValue("screen_angle_expert_01")))
        // puzzles singles solve never see a pattern
        for (name in listOf("book_01", "book_pen_01", "puzzle1", "screen_247_01")) {
            assertTrue(name, playReal(name).techniques.none { it.isPattern || it == Technique.ADVANCED })
        }
    }

    /** Generated Hard puzzles: singles run out, and a pattern hint always carries on. */
    @Test fun generatedHardPuzzlesNeverNeedAReveal() {
        val byTechnique = HashMap<Technique, Int>()
        var givens = 0
        for (p in hard) {
            val t = play(p).techniques
            assertEquals(Difficulty.HARD, HintEngine.rate(p.givens))
            assertTrue(t.none { it == Technique.ADVANCED })
            assertTrue(t.any { it.isPattern })
            assertTrue(HintEngine.explains(p.givens))
            for (x in t) if (x.isPattern) byTechnique.merge(x, 1, Int::plus)
            givens += p.givens.count { it != 0 }
        }
        println("ADVANCED_STATS puzzles=${hard.size} meanGivens=${givens / hard.size.toDouble()} $byTechnique")
        assertEquals(60, hard.size)
    }

    @Test fun ratingAndSinglesAreUntouched() {
        // the rating still stops at singles: pattern hints don't make a Hard puzzle easier on paper
        for (p in hard.take(10)) assertEquals(Difficulty.HARD, HintEngine.rate(p.givens))
        val tricky = Generator.generate(Difficulty.TRICKY, Random(1))!!
        assertTrue(play(tricky).techniques.none { it.isPattern || it == Technique.ADVANCED })
    }

    @Test fun aWrongEntryStillBlocksWithACountNotAPattern() {
        val p = hard.first()
        val entries = IntArray(81)
        // play up to the first stuck point, then put a wrong digit somewhere else
        while (true) {
            val h = (HintEngine.next(p.givens, entries, p.solution) as HintResult.Step).hint
            if (h.technique.isPattern || h.technique == Technique.ADVANCED) break
            entries[h.target] = h.digit
        }
        val open = (0 until 81).first { p.givens[it] == 0 && entries[it] == 0 }
        entries[open] = (1..9).first { it != p.solution[open] }
        assertEquals(HintResult.Mistakes(1), HintEngine.next(p.givens, entries, p.solution))
    }
}
