package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Hand.kt must measure and decide exactly like grid.cell_shape + hand.py. Fixtures from
 * export_hand_cases.py in app/src/test/resources/hand/: flattened grids (binarized +
 * gray) of the owner's book in ballpoint and pencil, a blank newspaper puzzle, a
 * glare-damaged screen, a bad warp without a reference and a synthetic glare photo,
 * with Python's shapes, P(handwritten) and verdicts; plus threshold edge cases.
 */
class HandParityTest {

    private fun stream(name: String) = javaClass.classLoader!!.getResourceAsStream("hand/$name")
        ?: error("missing test resource hand/$name: run export_hand_cases.py")

    private fun lines(name: String): List<List<String>> =
        stream(name).bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) }

    private fun kindOf(s: String) = when (s) {
        "printed" -> HandKind.PRINTED
        "hand" -> HandKind.HAND
        "unsure" -> HandKind.UNSURE
        "note" -> HandKind.NOTE
        else -> error("unknown kind $s")
    }

    private val cases = lines("grids.txt").map { it[0] }

    private val shapes: Map<String, Array<CellShape?>> by lazy {
        cases.associateWith { name ->
            val bytes = GZIPInputStream(stream("$name.bin.gz")).readBytes()
            val n = SIDE * SIDE
            assertEquals("$name: two ${SIDE}x$SIDE images", 2 * n, bytes.size)
            Hand.cellShapes(bytes.copyOfRange(0, n), bytes.copyOfRange(n, 2 * n), SIDE)
        }
    }

    @Test fun shapesMatchPython() {
        val expected = lines("shapes.txt").groupBy { it[0] }
        val bad = ArrayList<String>()
        for (name in cases) {
            val want = expected[name].orEmpty().associate { (it[1].toInt() * 9 + it[2].toInt()) to it.drop(3).map(String::toDouble) }
            val got = shapes.getValue(name)
            for (i in 0 until 81) {
                val w = want[i]
                val g = got[i]
                if (w == null && g == null) continue
                if (w == null || g == null) { bad.add("$name r${i / 9}c${i % 9}: Python $w, Kotlin $g"); continue }
                if (abs(g.stroke - w[0]) > 1e-4 || abs(g.dark - w[1]) > 1e-9 || g.height != w[2] || g.tallest != w[3]) {
                    bad.add("$name r${i / 9}c${i % 9}: Python $w, Kotlin $g")
                }
            }
        }
        assertTrue("Kotlin and Python disagree:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** Kotlin's own shapes + Python's model output -> the same verdict for every cell. */
    @Test fun kindsMatchPython() {
        val byCase = lines("kinds.txt").groupBy { it[0] }
        val bad = ArrayList<String>()
        for (f in lines("grids.txt")) {
            val name = f[0]
            val rows = byCase.getValue(name)
            val cells = rows.map { it[1].toInt() * 9 + it[2].toInt() }
            val probs = rows.map { it[3].toDouble() }
            val s = cells.map { shapes.getValue(name)[it] ?: error("$name cell $it has no shape") }
            val kinds = Hand.classify(s, probs)
            rows.forEachIndexed { k, row ->
                if (kinds[k] != kindOf(row[4])) bad.add("$name r${row[1]}c${row[2]}: Python ${row[4]}, Kotlin ${kinds[k]}")
            }
            assertEquals("$name reference", f[1] == "1", Hand.reference(s, probs) != null)
            assertEquals("$name handwritten", f[2].toInt(), kinds.count { it == HandKind.HAND })
            assertEquals("$name unsure", f[3].toInt(), kinds.count { it == HandKind.UNSURE })
            assertEquals("$name notes", f[4].toInt(), kinds.count { it == HandKind.NOTE })
        }
        assertTrue("Kotlin and Python disagree:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun edgesMatchPython() {
        val ref = CellShape(4.0, 0.6, 25.0)
        val all = lines("edges.txt")
        assertTrue(all.size >= 2100)
        assertTrue(all.any { it[5] == "note" })
        val bad = all.mapNotNull { f ->
            val kind = Hand.kind(CellShape(f[0].toDouble(), f[1].toDouble(), f[2].toDouble(), f[3].toDouble()), ref, f[4].toDouble())
            if (kind == kindOf(f[5])) null else "${f.joinToString(" ")}: Kotlin $kind"
        }
        assertTrue("Kotlin and Python disagree:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** The photos themselves: the book's handwriting leaves, print stays, glare asks. */
    @Test fun bookHandwritingIsFoundAndPrintIsKept() {
        val grids = lines("grids.txt").associate { it[0] to it }
        assertEquals("54", grids.getValue("book_pen_01")[2])
        assertEquals("51", grids.getValue("book_pencil_01")[2])  // of 53: two short ones are taken for notes
        assertEquals("0", grids.getValue("puzzle2")[2])               // blank newspaper: nothing moved
        assertEquals("0", grids.getValue("screen_glare_sudokucom_01")[2])
        assertEquals("0", grids.getValue("screen_filled_247_01")[1])  // bad warp: no reference
    }

    /** Pencilled candidates are not answers; a scribbled-out digit is asked about, not accepted. */
    @Test fun notesAreSkippedAndTheCrossedOutDigitIsAsked() {
        val grids = lines("grids.txt").associate { it[0] to it }
        val notes = grids.getValue("synth_notes")
        assertTrue("notes found: ${notes[4]}", notes[4].toInt() >= 5)
        assertTrue("notes carried as answers: ${notes[2]}", notes[2].toInt() < notes[4].toInt())
        // no note on a blank puzzle
        assertEquals("0", grids.getValue("puzzle2")[4])
        // book_pen_02 r4c1: a digit scribbled out with a 2 written beside it
        val crossed = lines("kinds.txt").single { it[0] == "book_pen_02" && it[1] == "3" && it[2] == "0" }
        assertEquals("unsure", crossed[4])
    }

    @Test fun rules() {
        val print = CellShape(4.0, 0.6, 25.0)
        val pencil = CellShape(2.8, 0.3, 27.0)
        val shapes = List(20) { print } + List(10) { pencil }
        val probs = List(20) { 0.01 } + List(10) { 0.97 }
        val kinds = Hand.classify(shapes, probs)
        assertTrue(kinds.take(20).all { it == HandKind.PRINTED })
        assertTrue(kinds.drop(20).all { it == HandKind.HAND })
        // fewer than 17 printed digits left: the measurements don't fit, nothing is split
        val few = Hand.classify(List(16) { print } + List(10) { pencil }, List(16) { 0.01 } + List(10) { 0.97 })
        assertTrue(few.all { it == HandKind.PRINTED })
        // the shape model is sure of too few printed cells: no reference
        assertNull(Hand.reference(shapes, List(4) { 0.01 } + List(26) { 0.5 }))
        assertNotNull(Hand.reference(shapes, List(5) { 0.01 } + List(25) { 0.5 }))
        assertTrue(Hand.classify(emptyList(), emptyList()).isEmpty())
        // an empty cell has no shape
        assertNull(Hand.cellShape(BooleanArray(38 * 38), IntArray(38 * 38) { 200 }, 38))
        // a short piece among the handwriting is a pencilled note: neither puzzle nor entry
        val note = CellShape(2.4, 0.3, 26.0, tallest = 12.0)
        val mixed = Hand.classify(shapes + note, probs + 0.97)
        assertEquals(HandKind.NOTE, mixed.last())
        assertTrue(mixed.dropLast(1).none { it == HandKind.NOTE })
        // print is never a note, whatever its height
        assertEquals(HandKind.PRINTED, Hand.kind(CellShape(4.0, 0.6, 25.0, 12.0), print, 0.01))
    }

    private companion object {
        const val SIDE = 450
    }
}
