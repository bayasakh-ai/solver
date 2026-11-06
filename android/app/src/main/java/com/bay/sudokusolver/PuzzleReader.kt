package com.bay.sudokusolver

/**
 * Turns many noisy per-frame readings into one trustworthy puzzle, then solves it.
 * Port of the voting idea plus run.py's self_correct().
 */

/** Keeps the last [window] sharp readings; a cell is trusted when [needed] of them agree. */
class PuzzleTracker(private val window: Int = 5, private val needed: Int = 3) {

    private class Frame(val grid: IntArray, val probs: Array<FloatArray?>)

    private val history = ArrayDeque<Frame>()

    val frames: Int get() = history.size
    val windowSize: Int get() = window

    fun reset() = history.clear()

    /** grid: 81 digits (0 = empty); probs: the model's 9 probabilities for each read cell. */
    fun add(grid: IntArray, probs: Array<FloatArray?>) {
        history.addLast(Frame(grid, probs))
        if (history.size > window) history.removeFirst()
    }

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
