package com.bay.sudokusolver

/**
 * What one scan hands to the Check screen (design/screens/flow/Main.dc.html): the
 * reading as the vote settled, what self-correction changed, and what to ask the user
 * about. Built once per new consensus, so it is cheap. Cells are 0..80, row by row.
 * Pure Kotlin (no Android imports) so it moves to the shared iOS module; the flattened
 * colour photo for the Photo view stays on the platform side, next to it.
 */
class Capture(
    /** Consensus reading before self-correction (0 = empty). */
    val read: IntArray,
    /** Givens that were solved: [read] with [fixes] applied. */
    val digits: IntArray,
    /** The model's top probability per read cell, averaged over the vote; 0 for empty cells. */
    val confidence: FloatArray,
    /** The model's second choice per read cell (1-9); 0 for empty cells. Check shows it dashed. */
    val secondGuess: IntArray,
    /** Digits self-correction changed (Check underlines them and says read -> now). */
    val fixes: List<Fix>,
    /** Cells whose ink might be the user's (InkClassifier UNSURE): ask, never guess. */
    val inkUnsure: BooleanArray,
    /** Cells where coloured user ink was ignored ("N pen entries ignored"). */
    val inkIgnored: Int,
    /** Unique solution of [digits], or null when the scan needs review. */
    val solution: IntArray?,
    /**
     * Digits the user wrote (0 elsewhere), kept apart from the printed [digits]: Check
     * starts "Mark as mine" from these and hands them to the board as the player's
     * entries. The scanner fills it with the digits it called handwritten (Hand.kt:
     * pencil, ballpoint), read as they are and never corrected. Coloured pen is removed
     * before reading (InkClassifier), so it is not here.
     */
    val userEntries: IntArray = IntArray(81),
    /**
     * Printed-looking digits that might be handwriting (Hand.kt UNSURE): they stay in
     * the puzzle as read, Check rings them and offers Mark as mine.
     */
    val handUnsure: BooleanArray = BooleanArray(81),
) {
    val solved: Boolean get() = solution != null
    val corrected: Set<Int> get() = fixes.mapTo(HashSet()) { it.cell }
    val filled: Int get() = digits.count { it != 0 }

    companion object {
        /**
         * [read] / [probs]: the consensus PuzzleTracker settled on; [result]: SelfCorrect's
         * answer for it; [inkUnsure] / [inkIgnored]: from the last frame that was read.
         * [userEntries]: the handwritten digits the frames agree on (not in [read]).
         * A cell can't be both: where the vote settled on a printed digit, that wins.
         */
        fun from(
            read: IntArray,
            probs: Array<FloatArray?>,
            result: SolveResult,
            inkUnsure: BooleanArray?,
            inkIgnored: Int,
            userEntries: IntArray? = null,
            handUnsure: BooleanArray? = null,
        ): Capture {
            require(read.size == 81 && probs.size == 81) { "need 81 cells" }
            val confidence = FloatArray(81)
            val second = IntArray(81)
            for (i in 0 until 81) {
                val p = probs[i] ?: continue
                if (read[i] == 0) continue
                val order = p.indices.sortedByDescending { p[it] }
                confidence[i] = p[order[0]]
                second[i] = order[1] + 1
            }
            return Capture(
                read = read.copyOf(),
                digits = result.puzzle.copyOf(),
                confidence = confidence,
                secondGuess = second,
                fixes = result.fixes,
                inkUnsure = inkUnsure?.copyOf() ?: BooleanArray(81),
                inkIgnored = inkIgnored,
                solution = result.solution?.copyOf(),
                userEntries = IntArray(81) { if (read[it] == 0) userEntries?.get(it) ?: 0 else 0 },
                handUnsure = BooleanArray(81) { read[it] != 0 && handUnsure?.get(it) == true },
            )
        }
    }
}
