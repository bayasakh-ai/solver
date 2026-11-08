package com.bay.sudokusolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InkClassifier must decide exactly like ink.py. Fixtures from export_ink_cases.py in
 * app/src/test/resources/ink/: piece stats measured by Python on puzzle1 (printed + red
 * pen, full size and at a 640 px phone frame), puzzle2 and synthetic pen_marks photos,
 * plus threshold / geometry edge cases. Kotlin recomputes each piece's chroma from the
 * raw colour medians, so neutralDistance is checked too, not only the thresholds.
 */
class InkClassifierTest {

    private fun lines(name: String): List<List<String>> =
        (javaClass.classLoader!!.getResourceAsStream("ink/$name")
            ?: error("missing test resource ink/$name — run export_ink_cases.py"))
            .bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) }

    private fun kindOf(s: String) = when (s) {
        "printed" -> InkKind.PRINTED
        "user" -> InkKind.USER
        "unsure" -> InkKind.UNSURE
        else -> error("unknown kind $s")
    }

    private class Exported(val key: String, val piece: InkPiece, val pyChroma: Double, val kind: InkKind)

    private fun pieces() = lines("pieces.txt").map { f ->
        val raw = f.subList(3, 8).map { it.toDouble() }
        val chroma = InkClassifier.neutralDistance(raw[0], raw[1], raw[2], raw[3], raw[4])
        Exported("${f[0]} ${f[1]} ${f[2]}", InkPiece(chroma, 0.0, f[9] == "1"), f[8].toDouble(), kindOf(f[10]))
    }

    @Test fun piecesMatchPython() {
        val all = pieces()
        assertTrue("pieces.txt is empty", all.isNotEmpty())
        val bad = all.mapNotNull { p ->
            val kind = InkClassifier.kind(p.piece)
            val sameChroma = kotlin.math.abs(p.piece.chroma - p.pyChroma) < 1e-9
            if (sameChroma && kind == p.kind) null
            else "${p.key}: Python chroma ${p.pyChroma} ${p.kind}, Kotlin ${p.piece.chroma} $kind"
        }
        assertTrue("Kotlin and Python disagree:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun cellsMatchPython() {
        val byCell = pieces().groupBy({ it.key }, { it.piece })
        val bad = lines("cells.txt").mapNotNull { f ->
            val key = "${f[0]} ${f[1]} ${f[2]}"
            val kotlin = InkClassifier.cellUnsure(byCell.getValue(key))
            if (kotlin == (f[3] == "1")) null else "$key: Python unsure=${f[3]}, Kotlin $kotlin"
        }
        assertTrue("Kotlin and Python disagree:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun safetyNetMatchesPython() {
        for (f in lines("grids.txt")) {
            assertEquals(f[0], f[2] == "1", InkClassifier.useRemoval(f[1].toInt()))
        }
    }

    /** The acceptance photo itself: every pen piece goes, every printed digit stays. */
    @Test fun puzzle1PenIsUserInkAndPrintIsKept() {
        val p1 = pieces().filter { it.key.startsWith("real_puzzle1 ") && it.piece.counts }
        assertTrue(p1.size > 80)
        assertTrue(p1.all { it.kind == InkKind.USER || it.kind == InkKind.PRINTED }) // nothing unsure
        assertEquals(56, p1.filter { it.kind == InkKind.USER }.map { it.key }.distinct().size)
        assertEquals(25, p1.filter { it.kind == InkKind.PRINTED }.map { it.key }.distinct().size)
    }

    @Test fun thresholdsAndSafetyNet() {
        assertEquals(InkKind.PRINTED, InkClassifier.kind(0.0))
        assertEquals(InkKind.UNSURE, InkClassifier.kind(InkClassifier.UNSURE_CHROMA))
        assertEquals(InkKind.USER, InkClassifier.kind(InkClassifier.USER_CHROMA))
        // an unsure piece only matters when it could be the cell's digit
        assertFalse(InkClassifier.cellUnsure(listOf(InkPiece(8.0, 0.5, counts = false))))
        assertTrue(InkClassifier.cellUnsure(listOf(InkPiece(8.0, 0.5, counts = true))))
        assertFalse(InkClassifier.useRemoval(16))
        assertTrue(InkClassifier.useRemoval(17))
    }
}
