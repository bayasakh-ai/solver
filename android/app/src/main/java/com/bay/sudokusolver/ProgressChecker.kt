package com.bay.sudokusolver

/*
 * Check my progress (CLAUDE.md "Handwriting: check progress on paper"): the digits the
 * user wrote on the page against the solution of the printed puzzle. Pure Kotlin (iOS).
 *
 * The rules it keeps:
 *  - the count comes first; where the wrong digits are is told only when asked;
 *  - a user's digit is never "fixed": it is right or wrong as written;
 *  - confidence before accusing: a digit the handwriting reader was not sure of is not
 *    judged at all. It is listed apart, to be looked at, never called a mistake.
 *    And a digit is only called wrong when the writing could not ALSO be the right
 *    digit: the reader must give the solution's digit under 0.1% ([PLAUSIBLE]).
 *
 * Why the second half. Measured 2026-11-15 on the owner's three book pages that have one
 * solution (157 handwritten digits, none of them wrong; single frames at 640 px): with
 * the confidence rule alone every page was told "1 digit doesn't fit" (3 confident
 * misreads, e.g. a flagged 1 read as 7). On 260 real handwritten digits the reader is
 * confident and wrong 8 times, and gives what was really written 0.0004 .. 0.084 there.
 * Asking for under 0.001 leaves 2 of those 8 (0 of the 3 on the book pages) and, by
 * simulation (a right, confident reading against each other digit as "the solution"),
 * still reports about 90% of real mistakes; 0.01 leaves 5 of 8 for 95%. The price: one
 * more "couldn't read for sure" digit per page. No real page with mistakes yet.
 */

/**
 * [written]: digits the user wrote (in cells the puzzle leaves open); [toFill]: open
 * cells in the puzzle; [wrong]: written cells that don't match the solution;
 * [doubtful]: written cells whose reading was not confident, left out of the judging.
 */
class Progress(val written: Int, val toFill: Int, val wrong: List<Int>, val doubtful: List<Int>) {
    /** Digits that were read confidently and compared. */
    val judged: Int get() = written - doubtful.size
    val right: Int get() = judged - wrong.size
    /** Cells still empty on the paper. */
    val left: Int get() = toFill - written
    val allFit: Boolean get() = wrong.isEmpty()
    /** Everything written, read for sure, and right: the paper is solved. */
    val finished: Boolean get() = left == 0 && wrong.isEmpty() && doubtful.isEmpty()
}

object ProgressChecker {

    /**
     * [entries]: the user's digits, 0 = none (digits on printed cells are ignored: those
     * are the puzzle). [doubtful]: cells whose reading Check asked about and the user did
     * not settle. [couldBe]: per cell, what else the handwriting could be (bit d = digit
     * d, see [couldBe]); 0 = nothing else. A digit that differs from the solution but
     * could be the solution's digit is not judged either.
     */
    fun check(puzzle: Puzzle, entries: IntArray, doubtful: Set<Int> = emptySet(), couldBe: IntArray = IntArray(81)): Progress {
        require(entries.size == 81 && couldBe.size == 81) { "need 81 cells" }
        val written = (0 until 81).filter { puzzle.givens[it] == 0 && entries[it] != 0 }
        fun unread(i: Int) = i in doubtful ||
            (entries[i] != puzzle.solution[i] && couldBe[i] and (1 shl puzzle.solution[i]) != 0)
        val unsure = written.filter { unread(it) }
        val wrong = written.filter { !unread(it) && entries[it] != puzzle.solution[it] }
        return Progress(written.size, puzzle.toFill, wrong, unsure)
    }

    /** A digit the reader gives at least this much could be what the user wrote. */
    const val PLAUSIBLE = 0.001f

    /** Bit mask (bit d = digit d) of the digits a reading could be, from the reader's nine probabilities. */
    fun couldBe(probs: FloatArray?): Int {
        var mask = 0
        if (probs != null) for (d in 1..9) if (probs[d - 1] >= PLAUSIBLE) mask = mask or (1 shl d)
        return mask
    }

    /** The count, first: says how many don't fit, never which. */
    fun headline(p: Progress): String = when {
        p.written == 0 -> "Nothing written yet"
        p.finished -> "Solved on paper"
        p.judged == 0 -> "I couldn’t read your digits for sure"
        p.allFit -> if (p.judged == 1) "Your digit fits" else "All ${p.judged} fit so far"
        p.wrong.size == 1 -> "1 digit doesn’t fit"
        else -> "${p.wrong.size} digits don’t fit"
    }

    /** One or two sentences under the headline. [shown]: the wrong cells are marked on the grid. */
    fun detail(p: Progress, shown: Boolean = false): String {
        val parts = ArrayList<String>()
        when {
            p.written == 0 -> parts += "The page has no handwriting the scan could find."
            p.finished -> parts += "Every cell is filled and every digit is right."
            p.judged == 0 -> {}
            p.allFit -> parts += "Every digit you wrote matches the solution. ${cells(p.left)} to go."
            shown -> parts += "Marked with a coral frame. The other ${count(p.right, "digit")} you wrote ${if (p.right == 1) "is" else "are"} right."
            else -> parts += "Of the ${count(p.judged, "digit")} you wrote, ${p.right} ${if (p.right == 1) "is" else "are"} right. " +
                "Look for ${if (p.wrong.size == 1) "it" else "them"} yourself, or ask where."
        }
        if (p.doubtful.isNotEmpty()) {
            parts += "${count(p.doubtful.size, "digit")} (ringed) I couldn’t read for sure, so I haven’t judged " +
                "${if (p.doubtful.size == 1) "it" else "them"}."
        }
        return parts.joinToString(" ")
    }

    private fun count(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"
    private fun cells(n: Int) = if (n == 0) "No cells" else "${count(n, "cell")}"
}
