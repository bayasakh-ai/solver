package com.bay.sudokusolver

/**
 * Turns many noisy per-frame readings into one trustworthy puzzle, then solves it.
 * Port of the voting idea plus run.py's self_correct().
 */

/** Keeps the last [window] sharp readings; a cell is trusted when [needed] of them agree. */
class PuzzleTracker(private val window: Int = 5, private val needed: Int = 3) {

    private class Frame(val grid: IntArray, val probs: Array<FloatArray?>, val user: IntArray?,
                        val userProbs: Array<FloatArray?>?)

    private val history = ArrayDeque<Frame>()

    val frames: Int get() = history.size
    val windowSize: Int get() = window

    fun reset() = history.clear()

    /**
     * grid: 81 printed digits (0 = empty); probs: the model's 9 probabilities for each
     * read cell. user: the digits this frame called handwritten (Hand.kt), 0 elsewhere:
     * they are NOT in grid, the puzzle is solved from printed digits only. userProbs:
     * the handwriting reader's 9 probabilities for those cells, when there is a reader.
     */
    fun add(grid: IntArray, probs: Array<FloatArray?>, user: IntArray? = null, userProbs: Array<FloatArray?>? = null) {
        history.addLast(Frame(grid, probs, user, userProbs))
        if (history.size > window) history.removeFirst()
    }

    /** The user's handwritten digits over the same frames (see [userEntries] below). */
    fun userEntries(): IntArray = userEntries(history.map { it.user }, needed)

    /** The same, with how sure the reader is and its second choice (see [userVote] below). */
    fun userVote(): UserVote = userVote(history.map { it.user }, history.map { it.userProbs }, needed)

    /** Majority reading per cell with averaged probabilities, or null if not yet stable. */
    fun consensus(): Pair<IntArray, Array<FloatArray?>>? {
        if (history.size < window) return null
        val grid = IntArray(81)
        val probs = arrayOfNulls<FloatArray>(81)
        for (i in 0 until 81) {
            val counts = IntArray(10)
            for (f in history) counts[f.grid[i]]++
            val best = counts.indices.maxBy { counts[it] }
            if (counts[best] < needed) return null // this cell keeps changing: not stable yet
            grid[i] = best
            if (best != 0) {
                val avg = FloatArray(9)
                var n = 0
                for (f in history) {
                    val p = f.probs[i] ?: continue
                    for (k in 0 until 9) avg[k] += p[k]
                    n++
                }
                for (k in 0 until 9) avg[k] /= n
                probs[i] = avg
            }
        }
        return grid to probs
    }
}

/**
 * The user's handwritten digits as the frames agree on them: a cell counts when [needed]
 * frames called it handwritten, and takes the digit most of those frames read (the
 * lowest on a tie). Looser than [PuzzleTracker.consensus] on purpose: a handwritten
 * digit the model reads two ways must not hold up the puzzle, and it is never corrected,
 * so the user can fix it on Check or on the board.
 */
fun userEntries(frames: List<IntArray?>, needed: Int): IntArray {
    val out = IntArray(81)
    for (i in 0 until 81) {
        val counts = IntArray(10)
        var seen = 0
        for (f in frames) {
            val d = f?.get(i) ?: 0
            if (d != 0) { counts[d]++; seen++ }
        }
        if (seen >= needed) out[i] = (1..9).maxBy { counts[it] }
    }
    return out
}

/**
 * [entries]: the user's digits (0 elsewhere). [confidence]: the handwriting reader's
 * probability for that digit, averaged over the frames; 1 where no reader ran (nothing
 * to doubt with). [second]: its second choice, 0 without a reader. [probs]: the reader's
 * nine averaged probabilities per cell (null without a reader): what else the writing
 * could be, for Check my progress.
 */
class UserVote(val entries: IntArray, val confidence: FloatArray, val second: IntArray,
               val probs: Array<FloatArray?> = arrayOfNulls(81))

/**
 * [userEntries] with the reader's probabilities: a cell counts when [needed] frames
 * called it handwritten; with probabilities for it, the digit is the one with the
 * highest AVERAGE probability over those frames (a frame that is sure outweighs two
 * that are not), else the commonest reading.
 */
fun userVote(frames: List<IntArray?>, probs: List<Array<FloatArray?>?>, needed: Int): UserVote {
    val entries = userEntries(frames, needed)
    val confidence = FloatArray(81) { 1f }
    val second = IntArray(81)
    val averaged = arrayOfNulls<FloatArray>(81)
    for (i in 0 until 81) {
        if (entries[i] == 0) continue
        val avg = FloatArray(9)
        var n = 0
        for (k in frames.indices) {
            if ((frames[k]?.get(i) ?: 0) == 0) continue
            val p = probs.getOrNull(k)?.get(i) ?: continue
            for (d in 0 until 9) avg[d] += p[d]
            n++
        }
        if (n == 0) continue
        val order = (0 until 9).sortedByDescending { avg[it] }
        entries[i] = order[0] + 1
        confidence[i] = avg[order[0]] / n
        second[i] = order[1] + 1
        averaged[i] = FloatArray(9) { avg[it] / n }
    }
    return UserVote(entries, confidence, second, averaged)
}

data class Fix(val cell: Int, val from: Int, val fromProb: Float, val to: Int, val toProb: Float)

/** puzzle = the givens actually used (after fixes); solution = null if unsolvable. */
class SolveResult(val puzzle: IntArray, val solution: IntArray?, val fixes: List<Fix>)

object SelfCorrect {

    /**
     * If the reading has no unique solution, retry with the model's next-best
     * readings for the least confident cells (1 change, then 2), and keep the
     * most probable fix that gives exactly one solution.
     */
    fun solve(grid: IntArray, probs: Array<FloatArray?>, maxChanges: Int = 2, suspects: Int = 8): SolveResult {
        if (!Solver.isPlausible(grid)) return SolveResult(grid, null, emptyList())
        val direct = Solver.solve(grid)
        if (direct.size == 1) return SolveResult(grid, direct[0], emptyList())

        val weakest = (0 until 81).filter { probs[it] != null }
            .sortedBy { i -> probs[i]!!.maxOf { it } }
            .take(suspects)

        for (n in 1..maxChanges) {
            var bestScore = -1.0
            var best: SolveResult? = null
            for (cells in combinations(weakest, n)) {
                for (choice in product(cells.map { options(probs[it]!!) })) {
                    val g = grid.copyOf()
                    cells.forEachIndexed { k, cell -> g[cell] = choice[k].first }
                    val sols = Solver.solve(g)
                    if (sols.size != 1) continue
                    val score = choice.fold(1.0) { acc, c -> acc * c.second }
                    if (score > bestScore) {
                        bestScore = score
                        val fixes = cells.mapIndexed { k, cell ->
                            Fix(cell, grid[cell], probs[cell]!!.maxOf { it }, choice[k].first, choice[k].second)
                        }
                        best = SolveResult(g, sols[0], fixes)
                    }
                }
            }
            if (best != null) return best
        }
        return SolveResult(grid, null, emptyList())
    }

    /** The model's 2nd-4th choices, plus "empty" (a speck that was read as a digit). */
    private fun options(p: FloatArray): List<Pair<Int, Float>> {
        val order = p.indices.sortedByDescending { p[it] }
        return order.subList(1, 4).map { (it + 1) to p[it] } + (0 to 0.01f)
    }

    private fun combinations(items: List<Int>, n: Int): List<List<Int>> =
        if (n == 0) listOf(emptyList())
        else items.indices.flatMap { i ->
            combinations(items.drop(i + 1), n - 1).map { listOf(items[i]) + it }
        }

    private fun <T> product(lists: List<List<T>>): List<List<T>> =
        lists.fold(listOf(emptyList())) { acc, list -> acc.flatMap { prefix -> list.map { prefix + it } } }
}
