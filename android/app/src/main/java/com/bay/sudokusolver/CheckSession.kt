package com.bay.sudokusolver

/*
 * Check the reading (design/screens/flow/Main.dc.html, CheckFix.dc.html): the scan's
 * reading plus the player's fixes. Says which cells are worth a look and why, what
 * the picker shows dashed, and whether the fixed grid is a puzzle. Pure Kotlin.
 */

enum class CellMark { NONE, UNSURE, CORRECTED, CHANGED }

/** Why a cell is unsure: the model wasn't confident, or the ink may be the user's. */
enum class UnsureReason { LOW_CONFIDENCE, INK }

sealed interface CheckResult {
    data class Ready(val puzzle: Puzzle) : CheckResult
    data object TooFew : CheckResult
    /** A digit repeats in a row, column or box, or no solution exists. */
    data object NoSolution : CheckResult
    data object Ambiguous : CheckResult
}

class CheckSession(val capture: Capture, val edits: Map<Int, Int> = emptyMap()) {

    fun value(i: Int): Int = edits[i] ?: capture.digits[i]

    fun lowConfidence(i: Int) = capture.read[i] != 0 && capture.confidence[i] < LOW_CONFIDENCE

    fun unsureReason(i: Int): UnsureReason? = when {
        i in capture.corrected -> null // underlined instead: it already says what changed
        capture.inkUnsure[i] -> UnsureReason.INK
        lowConfidence(i) -> UnsureReason.LOW_CONFIDENCE
        else -> null
    }

    fun mark(i: Int): CellMark = when {
        i in edits -> CellMark.CHANGED
        i in capture.corrected -> CellMark.CORRECTED
        unsureReason(i) != null -> CellMark.UNSURE
        else -> CellMark.NONE
    }

    fun fix(i: Int): Fix? = capture.fixes.firstOrNull { it.cell == i }

    /**
     * The picker's dashed key (0 = Empty): the model's second guess when it wasn't
     * confident, Empty when the ink may be the user's, the original reading when
     * self-correction changed it. Null: nothing to suggest.
     */
    fun suggestion(i: Int): Int? = when {
        fix(i) != null -> fix(i)!!.from
        unsureReason(i) == UnsureReason.INK -> 0
        unsureReason(i) == UnsureReason.LOW_CONFIDENCE -> capture.secondGuess[i]
        else -> null
    }?.takeIf { it != value(i) }

    /** Set [i] to [d] (0 = empty). Setting it back to what the scan gave drops the edit. */
    fun edit(i: Int, d: Int): CheckSession =
        CheckSession(capture, if (d == capture.digits[i]) edits - i else edits + (i to d))

    val unsureCount: Int get() = (0 until 81).count { mark(it) == CellMark.UNSURE }
    val correctedCount: Int get() = (0 until 81).count { mark(it) == CellMark.CORRECTED }
    val changedCount: Int get() = edits.size
    val filled: Int get() = (0 until 81).count { value(it) != 0 }

    /**
     * Looks right. With no edits a solved scan is used as is; after edits the grid is
     * solved exactly as the player left it: no more self-correction once a person has
     * looked at it.
     */
    fun confirm(): CheckResult {
        val grid = IntArray(81) { value(it) }
        if (edits.isEmpty() && capture.solution != null) return CheckResult.Ready(Puzzle(grid, capture.solution))
        if (!Solver.isPlausible(grid)) return CheckResult.TooFew
        val sols = Solver.solve(grid)
        return when (sols.size) {
            0 -> CheckResult.NoSolution
            1 -> CheckResult.Ready(Puzzle(grid, sols[0]))
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
