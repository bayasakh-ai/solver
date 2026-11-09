package com.bay.sudokusolver

import kotlin.random.Random

/*
 * Puzzles for "Play a puzzle". Port of synth.random_puzzle (fill the three diagonal
 * boxes at random, solve, dig holes while the solution stays unique), plus a target
 * difficulty: a hole that would make HintEngine rate the puzzle harder than asked is
 * filled back in. So the rating on Choose and Play is the one the player picked, by
 * construction, and HintEngine stays the only judge of difficulty. Pure Kotlin.
 */
object Generator {

    /**
     * Digging stops at this many givens: an easy puzzle with 22 givens is legal but feels
     * bare. Hard has no floor of its own; it needs few givens to need an advanced step.
     */
    fun minGivens(d: Difficulty) = when (d) {
        Difficulty.EASY -> 36
        Difficulty.MEDIUM -> 30
        Difficulty.TRICKY -> 26
        Difficulty.HARD -> 17
    }

    /** A full grid, as in synth.random_puzzle: random diagonal boxes, then the solver fills the rest. */
    fun solvedGrid(random: Random): IntArray {
        val g = IntArray(81)
        for (b in 0 until 3) {
            val digits = (1..9).shuffled(random)
            for (k in 0 until 9) g[(b * 3 + k / 3) * 9 + b * 3 + k % 3] = digits[k]
        }
        // the solver tries digits in order, so relabel to spread the rest of the grid too
        val relabel = (1..9).shuffled(random)
        return Solver.solve(g, limit = 1)[0].map { relabel[it - 1] }.toIntArray()
    }

    /**
     * One attempt: dig [solution] in random order, keeping each hole only if the puzzle
     * stays unique and rates no harder than [target]. Rated attempts can still end
     * EASIER than [target] (every harder hole was refused); [generate] retries those.
     */
    fun dig(solution: IntArray, target: Difficulty, random: Random): IntArray {
        val puzzle = solution.copyOf()
        var givens = 81
        val floor = minGivens(target)
        for (i in (0 until 81).shuffled(random)) {
            if (givens <= floor) break
            val keep = puzzle[i]
            puzzle[i] = 0
            val ok = Solver.solve(puzzle, limit = 2).size == 1 &&
                // Hard: any rating is fine while digging; the finished puzzle is checked
                (target == Difficulty.HARD || HintEngine.rate(puzzle) <= target)
            if (ok) givens-- else puzzle[i] = keep
        }
        return puzzle
    }

    /**
     * A puzzle HintEngine rates exactly [target]. [maxTries] bounds the work; measured
     * in GeneratorTest (JVM): every difficulty succeeded within a few tries over 40
     * seeds. Null only if all tries miss.
     */
    fun generate(target: Difficulty, random: Random = Random.Default, maxTries: Int = 50): Puzzle? {
        repeat(maxTries) {
            val solution = solvedGrid(random)
            val givens = dig(solution, target, random)
            if (HintEngine.rate(givens) == target) return Puzzle(givens, solution, PuzzleSource.GENERATED)
        }
        return null
    }
}
