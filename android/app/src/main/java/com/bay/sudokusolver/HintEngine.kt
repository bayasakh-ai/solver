package com.bay.sudokusolver

/*
 * Hints that follow real solving techniques (CLAUDE.md "Hints"). A hint is data, not
 * UI: the engine says which technique, digit, unit, target cell, which digits rule
 * the other cells out, and the words; the Play screen only plays it. The same engine
 * rates difficulty by the hardest technique a puzzle needs. Pure Kotlin (iOS port).
 *
 * Indexes are 0-based: cells 0..80 row by row, units 0..8 (see Units.kt), the same
 * shape as the hint data in design/screens/flow/Play.dc.html.
 */

enum class Technique { LAST_EMPTY_CELL, HIDDEN_SINGLE, NAKED_SINGLE, ADVANCED }

/** Show me draws a line from an eliminator along its row or column, ending at [to]. */
data class Sweep(val from: Int, val along: SudokuUnit, val to: Int)

data class Hint(
    val technique: Technique,
    val digit: Int,
    val target: Int,
    /** The unit the hint is about (null for naked singles and the advanced fallback). */
    val unit: SudokuUnit?,
    /**
     * Placed digits that rule the other cells out (ringed in Show me). Hidden single:
     * in cell order. Naked single: the eight digits the cell sees, in digit order
     * (1, 2, 3 ...), so Show me can light them up one by one like counting.
     */
    val eliminators: List<Int> = emptyList(),
    /** Empty cells of [unit] that can't take [digit] (crossed out in Show me). */
    val crossedOut: List<Int> = emptyList(),
    val sweeps: List<Sweep> = emptyList(),
    /** Advanced fallback: what the target cell still allows. */
    val candidates: List<Int> = emptyList(),
    /** Level 1 (Nudge): title and line. Region only, no digit. */
    val nudgeTitle: String = "",
    val nudgeText: String = "",
    /** Level 2 (Technique): the rule, then why. */
    val text: String,
    val reason: String,
    /** Level 3 (Show me): the animation's steps, in order. */
    val steps: List<String> = emptyList(),
) {
    /** Cells the hint is about: everything outside is dimmed. */
    val focus: Set<Int>
        get() = buildSet {
            unit?.let { addAll(it.cells) }
            if (technique == Technique.NAKED_SINGLE) {
                addAll(Cells.rowOf(target).cells); addAll(Cells.colOf(target).cells); addAll(Cells.boxOf(target).cells)
            }
            add(target)
            addAll(eliminators)
            for (s in sweeps) addAll(cellsBetween(s.from, s.to))
        }
}

sealed interface HintResult {
    data class Step(val hint: Hint) : HintResult
    /** No hint can be given without touching a wrong entry: say how many, not where. */
    data class Mistakes(val count: Int) : HintResult
    data object Solved : HintResult
}

/** Rated by the hardest technique needed, easiest first. [dots] of 4 on Choose. */
enum class Difficulty(val label: String, val dots: Int, val hardestStep: String) {
    EASY("Easy", 1, "Hidden singles in boxes"),
    MEDIUM("Medium", 2, "Hidden singles in rows and columns"),
    TRICKY("Tricky", 3, "Naked singles"),
    HARD("Hard", 4, "Needs an advanced step"),
}

/** Result of solving with singles only, from the givens. */
class SinglesRun(val board: IntArray, val placed: Int, val difficulty: Difficulty) {
    val stuck: Boolean get() = board.any { it == 0 }
}

object HintEngine {

    /**
     * The next hint for a game. Uses printed digits plus the player's CORRECT entries
     * (CLAUDE.md), so a wrong entry never leads a hint astray, and never targets a cell
     * the player has filled.
     */
    fun next(givens: IntArray, entries: IntArray, solution: IntArray): HintResult {
        val board = IntArray(81) { i ->
            when {
                givens[i] != 0 -> givens[i]
                entries[i] != 0 && entries[i] == solution[i] -> entries[i]
                else -> 0
            }
        }
        val open = (0 until 81).filterTo(HashSet()) { givens[it] == 0 && entries[it] == 0 }
        val wrong = (0 until 81).count { givens[it] == 0 && entries[it] != 0 && entries[it] != solution[it] }
        if (open.isEmpty()) return if (wrong > 0) HintResult.Mistakes(wrong) else HintResult.Solved
        find(board, open)?.let { return HintResult.Step(it) }
        if (wrong > 0) return HintResult.Mistakes(wrong)
        return HintResult.Step(advanced(board, open, solution))
    }

    /** The easiest single whose target is in [allowed], or null when only advanced steps are left. */
    fun find(board: IntArray, allowed: Set<Int>): Hint? =
        lastEmptyCell(board, allowed) ?: hiddenSingle(board, allowed) ?: nakedSingle(board, allowed)

    /** Solve with singles only, always taking the easiest step; the hardest one rates the puzzle. */
    fun singles(givens: IntArray): SinglesRun {
        val board = givens.copyOf()
        var hardest = Difficulty.EASY
        var placed = 0
        while (true) {
            val empties = (0 until 81).filterTo(HashSet()) { board[it] == 0 }
            if (empties.isEmpty()) break
            val h = find(board, empties) ?: return SinglesRun(board, placed, Difficulty.HARD)
            if (level(h) > hardest) hardest = level(h)
            board[h.target] = h.digit
            placed++
        }
        return SinglesRun(board, placed, hardest)
    }

    fun rate(givens: IntArray): Difficulty = singles(givens).difficulty

    private fun level(h: Hint) = when (h.technique) {
        Technique.LAST_EMPTY_CELL -> Difficulty.EASY
        Technique.HIDDEN_SINGLE -> if (h.unit?.type == UnitType.BOX) Difficulty.EASY else Difficulty.MEDIUM
        Technique.NAKED_SINGLE -> Difficulty.TRICKY
        Technique.ADVANCED -> Difficulty.HARD
    }

    // --- techniques ---------------------------------------------------------------

    private fun lastEmptyCell(board: IntArray, allowed: Set<Int>): Hint? {
        for (u in Cells.units) {
            val empty = u.cells.filter { board[it] == 0 }
            if (empty.size != 1 || empty[0] !in allowed) continue
            val target = empty[0]
            val digit = (1..9).first { d -> u.cells.none { board[it] == d } }
            val where = unitName(u)
            return Hint(
                Technique.LAST_EMPTY_CELL, digit, target, u,
                nudgeTitle = "Look at ${where}", nudgeText = "One cell here is easy to fill.",
                text = "Only $digit is missing in this ${u.type.word}.",
                reason = "${where.cap()} has eight digits already. The empty cell takes the ninth.",
                steps = listOf("The ${u.type.word} lights up.", "Eight digits are there. Only $digit is missing.", "The $digit goes there."),
            )
        }
        return null
    }

    private fun hiddenSingle(board: IntArray, allowed: Set<Int>): Hint? {
        for (u in Cells.units) {
            val cells = u.cells
            for (d in 1..9) {
                if (cells.any { board[it] == d }) continue
                val spots = cells.filter { board[it] == 0 && d in Cells.candidates(board, it) }
                if (spots.size != 1 || spots[0] !in allowed) continue
                return hiddenSingleHint(board, u, d, spots[0])
            }
        }
        return null
    }

    /** The hidden single for [d] in [u], if [d] has exactly one place there. */
    fun hiddenSingle(board: IntArray, u: SudokuUnit, d: Int): Hint? {
        if (u.cells.any { board[it] == d }) return null
        val spots = u.cells.filter { board[it] == 0 && d in Cells.candidates(board, it) }
        return if (spots.size == 1) hiddenSingleHint(board, u, d, spots[0]) else null
    }

    private fun hiddenSingleHint(board: IntArray, u: SudokuUnit, d: Int, target: Int): Hint {
        val crossed = u.cells.filter { board[it] == 0 && it != target }
        // fewest placed digits that rule out every other empty cell (greedy cover)
        val blockers = crossed.associateWith { c -> Cells.peers[c].filter { board[it] == d } }
        val uncovered = crossed.toMutableSet()
        val eliminators = ArrayList<Int>()
        while (uncovered.isNotEmpty()) {
            val best = blockers.filterKeys { it in uncovered }.values.flatten().distinct()
                .maxWithOrNull(compareBy<Int> { e -> uncovered.count { Cells.sees(e, it) } }.thenByDescending { it })
                ?: break
            eliminators += best
            uncovered.removeAll { Cells.sees(best, it) }
        }
        eliminators.sort()
        val sweeps = eliminators.mapNotNull { e ->
            val covered = crossed.filter { Cells.sees(e, it) }
            when {
                covered.any { Cells.row(it) == Cells.row(e) } && u.type != UnitType.ROW ->
                    Sweep(e, Cells.rowOf(e), covered.filter { Cells.row(it) == Cells.row(e) }.maxBy { kotlin.math.abs(Cells.col(it) - Cells.col(e)) })
                covered.any { Cells.col(it) == Cells.col(e) } && u.type != UnitType.COLUMN ->
                    Sweep(e, Cells.colOf(e), covered.filter { Cells.col(it) == Cells.col(e) }.maxBy { kotlin.math.abs(Cells.row(it) - Cells.row(e)) })
                else -> null // rules out through a shared box: ringed, no line
            }
        }
        val rows = sweeps.filter { it.along.type == UnitType.ROW }.map { Cells.row(it.from) + 1 }.sorted()
        val cols = sweeps.filter { it.along.type == UnitType.COLUMN }.map { Cells.col(it.from) + 1 }.sorted()
        val boxes = eliminators.filter { e -> sweeps.none { it.from == e } }.map { unitName(Cells.boxOf(it)) }
        val places = listOfNotNull(
            rows.takeIf { it.isNotEmpty() }?.let { "${if (it.size == 1) "row" else "rows"} ${and(it.map(Int::toString))}" },
            cols.takeIf { it.isNotEmpty() }?.let { "${if (it.size == 1) "column" else "columns"} ${and(it.map(Int::toString))}" },
            boxes.takeIf { it.isNotEmpty() }?.let { and(it) },
        )
        val digits = if (eliminators.size == 1) "The $d" else "The ${d}s"
        val where = unitName(u)
        val others = if (crossed.size == 1) "the other cell" else "the other ${count(crossed.size)} cells"
        return Hint(
            Technique.HIDDEN_SINGLE, d, target, u, eliminators, crossed, sweeps,
            nudgeTitle = "Look at ${where}",
            nudgeText = "One digit has only one place it can go here.",
            text = "$d can only go in one place in this ${u.type.word}.",
            reason = "$digits in ${and(places)} rule${if (eliminators.size == 1) "s" else ""} out $others.",
            steps = listOf(
                if (eliminators.size == 1) "The $d crosses out its ${if (sweeps.isEmpty()) "box" else "line"}."
                else "The ${count(eliminators.size)} ${d}s cross out their rows and columns.",
                "One cell in the ${u.type.word} is left.",
                "The $d goes there.",
            ),
        )
    }

    private fun nakedSingle(board: IntArray, allowed: Set<Int>): Hint? =
        allowed.sorted().firstNotNullOfOrNull { nakedSingle(board, it) }

    /** The naked single in empty cell [target], if only one digit fits there. */
    fun nakedSingle(board: IntArray, target: Int): Hint? {
        val cands = Cells.candidates(board, target)
        if (cands.size != 1) return null
        val d = cands[0]
        // one placed digit for each of the other eight: row first, then column, then box
        val order = Cells.rowOf(target).cells + Cells.colOf(target).cells + Cells.boxOf(target).cells
        val eliminators = (1..9).filter { it != d }.map { other -> order.first { it != target && board[it] == other } }
        return Hint(
            Technique.NAKED_SINGLE, d, target, unit = null, eliminators = eliminators,
            nudgeTitle = "Look at ${Cells.name(target)}",
            nudgeText = "Only one digit fits in this cell.",
            text = "Only $d fits in this cell.",
            reason = "It sees ${and((1..9).filter { it != d }.map(Int::toString))} in its row, column and box.",
            steps = listOf("Its row, column and box light up.", "Eight digits are already taken, one by one.", "Only $d is left."),
        )
    }

    /** No single anywhere: offer to reveal the open cell with the fewest candidates. */
    private fun advanced(board: IntArray, open: Set<Int>, solution: IntArray): Hint {
        val target = open.minWith(compareBy<Int> { Cells.candidates(board, it).size }.thenBy { it })
        return Hint(
            Technique.ADVANCED, solution[target], target, unit = null,
            candidates = Cells.candidates(board, target),
            text = "This step needs an advanced technique.",
            reason = "No single works anywhere right now. This app doesn’t explain pairs or pointing yet, " +
                "but it can reveal a cell to get you moving.",
        )
    }

    // --- words --------------------------------------------------------------------

    private val boxNames = listOf("top-left", "top", "top-right", "left", "middle", "right", "bottom-left", "bottom", "bottom-right")

    fun unitName(u: SudokuUnit) = when (u.type) {
        UnitType.BOX -> "the ${boxNames[u.index]} box"
        UnitType.ROW -> "row ${u.index + 1}"
        UnitType.COLUMN -> "column ${u.index + 1}"
    }

    private val UnitType.word get() = when (this) { UnitType.ROW -> "row"; UnitType.COLUMN -> "column"; UnitType.BOX -> "box" }

    private fun count(n: Int) = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight").getOrElse(n) { "$n" }

    private fun and(items: List<String>) = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun String.cap() = replaceFirstChar { it.uppercase() }
}

/** Cells on the straight line from [a] to [b] (same row or column), both ends included. */
fun cellsBetween(a: Int, b: Int): List<Int> = when {
    Cells.row(a) == Cells.row(b) -> (minOf(Cells.col(a), Cells.col(b))..maxOf(Cells.col(a), Cells.col(b))).map { Cells.row(a) * 9 + it }
    Cells.col(a) == Cells.col(b) -> (minOf(Cells.row(a), Cells.row(b))..maxOf(Cells.row(a), Cells.row(b))).map { it * 9 + Cells.col(a) }
    else -> listOf(a, b)
}
