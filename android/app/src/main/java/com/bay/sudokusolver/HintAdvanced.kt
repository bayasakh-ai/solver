package com.bay.sudokusolver

/*
 * Hints beyond singles (CLAUDE.md "Hints", technique 4): pointing, claiming, naked pair,
 * hidden pair. Pure Kotlin (iOS port).
 *
 * A hint must end with a digit, and the board keeps no candidates between hints (the
 * notes are the player's own). So each of these hints is ONE elimination plus the
 * single it leaves behind:
 *
 *   pattern  ->  some cells lose a candidate  ->  a naked or hidden single appears
 *
 * Candidates are worked out fresh from the digits on the board. When no single
 * elimination leaves a single (two or more are needed in a row), there is still no
 * hint: HintEngine falls back to offering to reveal a cell. Measured in
 * HintAdvancedTest on generated Hard puzzles.
 *
 * Singles are tried first by HintEngine, so when this runs no single exists: any single
 * found after an elimination is caused by it.
 */
internal object AdvancedHints {

    /** One elimination: [pattern] cells holding [digits] in [unit] clear [removals] (cell -> digit mask) in [line]. */
    private class Elimination(
        val technique: Technique,
        val pattern: List<Int>,
        val digits: List<Int>,
        val unit: SudokuUnit,
        val line: SudokuUnit,
        val removals: Map<Int, Int>,
    )

    private fun bit(d: Int) = 1 shl d
    private fun digitsOf(mask: Int) = (1..9).filter { mask and bit(it) != 0 }

    fun candidateMasks(board: IntArray) = IntArray(81) { i -> Cells.candidates(board, i).fold(0) { m, d -> m or bit(d) } }

    /** The easiest one-step advanced hint whose target is in [allowed], or null. */
    fun find(board: IntArray, allowed: Set<Int>): Hint? {
        val cand = candidateMasks(board)
        for (e in eliminations(board, cand)) finish(board, cand, e, allowed)?.let { return it }
        return null
    }

    private fun eliminations(board: IntArray, cand: IntArray): Sequence<Elimination> = sequence {
        val boxes = Cells.units.filter { it.type == UnitType.BOX }
        val lines = Cells.units.filter { it.type != UnitType.BOX }

        // pointing: in a box, a digit fits only in one row or column -> not elsewhere in that line
        for (box in boxes) for (d in 1..9) {
            val spots = box.cells.filter { cand[it] and bit(d) != 0 }
            if (spots.size < 2) continue
            val line = when {
                spots.all { Cells.row(it) == Cells.row(spots[0]) } -> Cells.rowOf(spots[0])
                spots.all { Cells.col(it) == Cells.col(spots[0]) } -> Cells.colOf(spots[0])
                else -> continue
            }
            val out = line.cells.filter { Cells.box(it) != box.index && cand[it] and bit(d) != 0 }
            if (out.isNotEmpty()) yield(Elimination(Technique.POINTING, spots, listOf(d), box, line, out.associateWith { bit(d) }))
        }
        // claiming: in a row or column, a digit fits only inside one box -> not elsewhere in that box
        for (line in lines) for (d in 1..9) {
            val spots = line.cells.filter { cand[it] and bit(d) != 0 }
            if (spots.size < 2 || spots.any { Cells.box(it) != Cells.box(spots[0]) }) continue
            val box = Cells.boxOf(spots[0])
            val out = box.cells.filter { it !in spots && cand[it] and bit(d) != 0 }
            if (out.isNotEmpty()) yield(Elimination(Technique.CLAIMING, spots, listOf(d), line, box, out.associateWith { bit(d) }))
        }
        // naked pair: two cells of a unit hold the same two candidates -> nobody else in the unit gets them
        for (u in Cells.units) {
            val twos = u.cells.filter { board[it] == 0 && Integer.bitCount(cand[it]) == 2 }
            for (a in twos.indices) for (b in a + 1 until twos.size) {
                val mask = cand[twos[a]]
                if (cand[twos[b]] != mask) continue
                val out = u.cells.filter { it != twos[a] && it != twos[b] && cand[it] and mask != 0 }
                if (out.isNotEmpty()) yield(Elimination(Technique.NAKED_PAIR, listOf(twos[a], twos[b]), digitsOf(mask), u, u,
                    out.associateWith { cand[it] and mask }))
            }
        }
        // hidden pair: two digits of a unit fit only in the same two cells -> nothing else fits in those
        for (u in Cells.units) {
            val places = (0..9).map { d -> if (d == 0) emptyList() else u.cells.filter { cand[it] and bit(d) != 0 } }
            for (a in 1..9) for (b in a + 1..9) {
                if (places[a].size != 2 || places[a] != places[b]) continue
                val pair = bit(a) or bit(b)
                val out = places[a].filter { cand[it] and pair.inv() != 0 }
                if (out.isNotEmpty()) yield(Elimination(Technique.HIDDEN_PAIR, places[a], listOf(a, b), u, u,
                    out.associateWith { cand[it] and pair.inv() }))
            }
        }
    }

    /** The single that [e] leaves behind, as a hint, or null when it leaves none (in [allowed]). */
    private fun finish(board: IntArray, cand: IntArray, e: Elimination, allowed: Set<Int>): Hint? {
        val after = cand.copyOf()
        for ((cell, mask) in e.removals) after[cell] = after[cell] and mask.inv()

        // a digit that lost a place now has only one left in a box, row or column
        for ((cell, mask) in e.removals) for (d in digitsOf(mask)) {
            for (u in listOf(Cells.boxOf(cell), Cells.rowOf(cell), Cells.colOf(cell))) {
                if (u.cells.any { board[it] == d }) continue
                val spots = u.cells.filter { after[it] and bit(d) != 0 }
                if (spots.size != 1 || spots[0] !in allowed) continue
                val removed = u.cells.filter { e.removals[it]?.and(bit(d)) ?: 0 != 0 }
                val crossed = u.cells.filter { board[it] == 0 && it != spots[0] && it !in removed }
                return hint(e, d, spots[0], removed, listOf(d), Technique.HIDDEN_SINGLE, u, crossed)
            }
        }
        // a cell that lost candidates has only one left
        for ((cell, mask) in e.removals) {
            if (Integer.bitCount(after[cell]) != 1 || cell !in allowed) continue
            return hint(e, digitsOf(after[cell])[0], cell, listOf(cell), digitsOf(mask), Technique.NAKED_SINGLE, null, emptyList())
        }
        return null
    }

    private fun hint(
        e: Elimination, digit: Int, target: Int, removed: List<Int>, removedDigits: List<Int>,
        finish: Technique, finishUnit: SudokuUnit?, crossed: List<Int>,
    ): Hint {
        val unit = HintEngine.unitName(e.unit)
        val line = HintEngine.unitName(e.line)
        val d = e.digits[0]
        val both = e.digits.joinToString(" and ")
        val either = e.digits.joinToString(" or ")
        val last = if (finish == Technique.HIDDEN_SINGLE) "That leaves one place for $digit in ${HintEngine.unitName(finishUnit!!)}."
        else "That leaves only $digit for the cell in ${Cells.name(target)}."
        val end = "One place is left: the $digit goes there."
        val w = e.line.type.word
        val words = when (e.technique) {
            Technique.POINTING -> Words(
                "One digit in this box is stuck on a single $w.",
                "In $unit, $d can only be in $line.",
                "Wherever it lands, $line gets its $d from this box, so the rest of $line can’t be $d. $last",
                listOf("The ${d}s of the box sit in one $w.", "No other $d in that $w.", end))
            Technique.CLAIMING -> Words(
                "One digit in this ${e.unit.type.word} is stuck inside a single box.",
                "In $unit, $d can only be inside $line.",
                "Wherever it lands, $line gets its $d from $unit, so the rest of the box can’t be $d. $last",
                listOf("The ${d}s of the ${e.unit.type.word} sit in one box.", "No other $d in that box.", end))
            Technique.NAKED_PAIR -> Words(
                "Two cells here share the same two candidates.",
                "Two cells in $unit can only be $either.",
                "Between them they use up $both, so no other cell in $unit can be $either. $last",
                listOf("Two cells hold only $both.", "Nobody else in the ${e.unit.type.word} gets $either.", end))
            else -> Words(
                "Two digits here share the same two cells.",
                "In $unit, $both can only go in the same two cells.",
                "Those two cells are taken by $both, so nothing else fits in them. $last",
                listOf("$both fit in just two cells.", "Nothing else fits in those cells.", end))
        }
        return Hint(
            e.technique, digit, target, e.unit,
            crossedOut = crossed,
            nudgeTitle = "Look at $unit", nudgeText = words.nudge,
            text = words.text, reason = words.reason, steps = words.steps,
            pattern = e.pattern, patternDigits = e.digits, line = e.line,
            removed = removed, removedDigits = removedDigits, finish = finish, finishUnit = finishUnit,
        )
    }

    private class Words(val nudge: String, val text: String, val reason: String, val steps: List<String>)

    private val UnitType.word get() = when (this) { UnitType.ROW -> "row"; UnitType.COLUMN -> "column"; UnitType.BOX -> "box" }
}
