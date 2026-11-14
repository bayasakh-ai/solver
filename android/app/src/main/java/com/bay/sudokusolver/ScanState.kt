package com.bay.sudokusolver

/**
 * What the live scanner is doing right now, as data. The UI maps each state to its
 * outline pattern, status chip and bottom-bar text; FrameAnalyzer only decides the
 * state. Pure Kotlin (no Android imports) so it moves to the shared iOS module.
 */
sealed interface ScanState {
    /** No grid in view. */
    data object Searching : ScanState

    /** Grid found, collecting sharp readings for the vote. [tooSmall]: ask to move closer. */
    data class Reading(val frames: Int, val window: Int, val tooSmall: Boolean = false) : ScanState

    /** Grid found but the frame is blurred: progress pauses. */
    data class HoldSteady(val frames: Int, val window: Int, val tooSmall: Boolean = false) : ScanState

    /** Unique solution. [fixes] = misread digits self-correction changed. */
    data class Solved(val fixes: Int) : ScanState

    /** The vote settled but the reading can't be solved. */
    data class NeedsReview(val reason: Reason) : ScanState

    enum class Reason {
        /** Fewer than 17 digits: no sudoku with a unique solution has that few. */
        TOO_FEW_DIGITS,
        /** Enough digits, but no unique solution even after self-correction. */
        NO_UNIQUE_SOLUTION,
    }

    companion object {
        /**
         * Below this share of the frame the grid is too small to read well, so the
         * scanner asks the user to move closer (reading carries on). Measured
         * (size sweep, 640x480 frames, 10 synthetic puzzles per size): 10% 10/10 solved,
         * 6% 9/10, 4% 2/10, 3% 0/10.
         */
        const val MOVE_CLOSER_SHARE = 0.06

        /** Grid area / frame area for corners x0,y0..x3,y3 (any order around the quad). */
        fun gridShare(corners: FloatArray, frameWidth: Int, frameHeight: Int): Double {
            var twice = 0.0
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                twice += corners[2 * i].toDouble() * corners[2 * j + 1] - corners[2 * j].toDouble() * corners[2 * i + 1]
            }
            return kotlin.math.abs(twice) / 2 / (frameWidth.toDouble() * frameHeight)
        }

        /**
         * State for a frame where the grid was found. [solved] is the last
         * self-correction result for the current consensus (null = no consensus yet).
         * Once a reading is solved or rejected it stays that way while the grid is in
         * view, even on blurred frames; only a new consensus changes it.
         * [gridShare] (see [gridShare]) below [MOVE_CLOSER_SHARE] marks Reading /
         * HoldSteady as too small; a settled result is shown as it is.
         */
        fun decide(
            solved: SolveResult?, isSharp: Boolean, frames: Int, window: Int, gridShare: Double = 1.0,
        ): ScanState {
            val tooSmall = gridShare < MOVE_CLOSER_SHARE
            return when {
                solved?.solution != null -> Solved(solved.fixes.size)
                solved != null && !Solver.isPlausible(solved.puzzle) -> NeedsReview(Reason.TOO_FEW_DIGITS)
                solved != null -> NeedsReview(Reason.NO_UNIQUE_SOLUTION)
                !isSharp -> HoldSteady(frames, window, tooSmall)
                else -> Reading(frames, window, tooSmall)
            }
        }
    }
}
