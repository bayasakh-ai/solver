package com.bay.sudokusolver

/*
 * Check the reading (design/screens/flow/Main.dc.html, CheckFix.dc.html): the scan's
 * reading plus the player's fixes. Says which cells are worth a look and why, what
 * the picker shows dashed, and whether the fixed grid is a puzzle. Pure Kotlin.
 */

enum class CellMark { NONE, UNSURE, CORRECTED, CHANGED, MINE }

/** Why a cell is unsure: the model wasn't confident, or the ink may be the user's. */
enum class UnsureReason { LOW_CONFIDENCE, INK }

sealed interface CheckResult {
    /** [start]: the digits marked as the user's (0 elsewhere); the board starts with them. */
    class Ready(val puzzle: Puzzle, val start: IntArray = IntArray(81)) : CheckResult
    data object TooFew : CheckResult
    /** A digit repeats in a row, column or box, or no solution exists. */
    data object NoSolution : CheckResult
    data object Ambiguous : CheckResult
}

/**
 * [mine]: cells the user marked as their own writing (pencil or black pen, which colour
 * can't separate from print). They stay on the grid but are not part of the puzzle:
 * the puzzle is solved from the other (printed) digits only, and the marked digits go
 * to the board as the player's entries, right or wrong.
 */
class CheckSession(
    val capture: Capture,
    val edits: Map<Int, Int> = emptyMap(),
    val mine: Set<Int> = (0 until 81).filterTo(HashSet()) { capture.userEntries[it] != 0 },
) {

    /**
     * What the cell holds now. A cell marked as the user's shows what the camera READ
     * there: self-correction only applies to printed digits, so it must not have
     * "fixed" the user's writing.
     */
    fun value(i: Int): Int = edits[i] ?: capture.userEntries[i].takeIf { it != 0 } ?: scanned(i, i in mine)

    private fun scanned(i: Int, mine: Boolean) = if (mine) capture.read[i] else capture.digits[i]

    fun isMine(i: Int) = i in mine

    /** The printed digits as they stand now: what the puzzle is solved from. */
    val printed: IntArray by lazy { IntArray(81) { if (it in mine) 0 else value(it) } }

    fun lowConfidence(i: Int) = capture.read[i] != 0 && capture.confidence[i] < LOW_CONFIDENCE

    fun unsureReason(i: Int): UnsureReason? = when {
        i in capture.corrected -> null // underlined instead: it already says what changed
        capture.inkUnsure[i] -> UnsureReason.INK
        lowConfidence(i) -> UnsureReason.LOW_CONFIDENCE
        else -> null
    }

    fun mark(i: Int): CellMark = when {
        i in mine -> CellMark.MINE
        i in edits -> CellMark.CHANGED
        i in capture.corrected -> CellMark.CORRECTED
        unsureReason(i) != null -> CellMark.UNSURE
        else -> CellMark.NONE
    }

    fun fix(i: Int): Fix? = capture.fixes.firstOrNull { it.cell == i }

    /**
     * Printed cells whose digit repeats in their row, column or box: a misread for sure,
     * shown at once (coral frame). The user's own digits don't count: a wrong entry on
     * paper is real, and it isn't part of the puzzle.
     */
    val duplicates: Set<Int> by lazy {
        (0 until 81).filterTo(HashSet()) { i -> printed[i] != 0 && Cells.peers[i].any { printed[it] == printed[i] } }
    }

    /** Picker: would [d] in [i] repeat a printed digit [i] sees? Marked, never blocked. */
    fun wouldDuplicate(i: Int, d: Int): Boolean = d != 0 && Cells.peers[i].any { printed[it] == d }

    /** The printed digits have exactly one solution now. */
    val uniquelySolvable: Boolean by lazy { Solver.isPlausible(printed) && Solver.solve(printed, limit = 2).size == 1 }

    /**
     * While the printed digits are not a puzzle: the choices for [i] (0 = Empty) that
     * would make them one, i.e. exactly one solution. Empty when the grid is already
     * fine, or [i] is the user's. Ten small solves, run when the picker opens.
     */
    fun solvingDigits(i: Int): List<Int> {
        if (uniquelySolvable || i in mine) return emptyList()
        return (0..9).filter { d ->
            if (d == printed[i]) return@filter false
            val g = printed.copyOf().also { it[i] = d }
            Solver.isPlausible(g) && Solver.solve(g, limit = 2).size == 1
        }
    }

    /**
     * The picker's dashed key (0 = Empty). First, a choice that makes the grid a puzzle.
     * If several do: what the scan gave (the user's edit broke it), else the model's
     * second guess, else the only digit among them (a printed cell is likelier misread
     * than imagined), else nothing: no guessing.
     * Otherwise: the model's second guess when it wasn't confident, Empty when the ink
     * may be the user's, the original reading when self-correction changed it.
     * Null: nothing to suggest.
     */
    fun suggestion(i: Int): Int? {
        val solving = solvingDigits(i)
        if (solving.isNotEmpty()) {
            val scanned = capture.digits[i]
            return listOf(scanned, capture.secondGuess[i].takeIf { it != 0 }).firstOrNull { it != null && it in solving }
                ?: solving.filter { it != 0 }.singleOrNull()
                ?: solving.singleOrNull()
        }
        return when {
            i in mine -> null
            fix(i) != null -> fix(i)!!.from
            unsureReason(i) == UnsureReason.INK -> 0
            unsureReason(i) == UnsureReason.LOW_CONFIDENCE -> capture.secondGuess[i]
            else -> null
        }?.takeIf { it != value(i) }
    }

    /** Why [suggestion] is dashed: true when it is the digit that makes the grid solvable. */
    fun suggestionSolves(i: Int): Boolean = suggestion(i)?.let { it in solvingDigits(i) } == true

    /** Set [i] to [d] (0 = empty). Setting it back to what the scan gave drops the edit; Empty isn't anyone's. */
    fun edit(i: Int, d: Int): CheckSession {
        val scanned = capture.userEntries[i].takeIf { it != 0 } ?: scanned(i, i in mine)
        return CheckSession(capture, if (d == scanned) edits - i else edits + (i to d), if (d == 0) mine - i else mine)
    }

    /** Mark as mine / Printed: the digit in [i] is the user's writing, or print. Empty cells can't be either. */
    fun setMine(i: Int, on: Boolean): CheckSession = when {
        on && (edits[i] ?: capture.userEntries[i].takeIf { it != 0 } ?: scanned(i, true)) == 0 -> this
        on -> CheckSession(capture, edits, mine + i)
        else -> CheckSession(capture, edits, mine - i)
    }

    val unsureCount: Int get() = (0 until 81).count { mark(it) == CellMark.UNSURE }
    val correctedCount: Int get() = (0 until 81).count { mark(it) == CellMark.CORRECTED }
    val changedCount: Int get() = edits.size
    val mineCount: Int get() = mine.size
    /** Printed digits (the user's own are counted in [mineCount]). */
    val filled: Int get() = printed.count { it != 0 }

    /**
     * Looks right. With no edits and nothing marked, a solved scan is used as is; after
     * that the printed digits are solved exactly as the player left them: no more
     * self-correction once a person has looked at it.
     */
    fun confirm(): CheckResult {
        val grid = printed.copyOf()
        val start = IntArray(81) { if (it in mine) value(it) else 0 }
        val scanAsIs = edits.isEmpty() && mine == CheckSession(capture).mine
        if (scanAsIs && capture.solution != null) return CheckResult.Ready(Puzzle(grid, capture.solution), start)
        if (!Solver.isPlausible(grid)) return CheckResult.TooFew
        val sols = Solver.solve(grid)
        return when (sols.size) {
            0 -> CheckResult.NoSolution
            1 -> CheckResult.Ready(Puzzle(grid, sols[0]), start)
            else -> CheckResult.Ambiguous
        }
    }

    companion object {
        /**
         * Below this top probability a read cell gets a coral ring. Measured 2026-11-09
         * (photos/ at full size and 640 px, plus evaluate.py's synthetic set, seed 0,
         * 20 per condition, pencil excluded): right readings on real photos were all
         * >= 0.976; the two wrong ones (puzzle2's 2 read as 9) were 0.944 and 0.721.
         * Synthetic: 0.95 rings 7/7 wrong readings and 14/6610 right ones (0.21%);
         * 0.90 misses one wrong, 0.99 rings 45 right ones. Only 9 wrong readings in all:
         * re-check after the real-world session. Live readings average 3-5 frames, which
         * raises confidence, so the phone should ring fewer cells than this test.
         */
        const val LOW_CONFIDENCE = 0.95f
    }
}
