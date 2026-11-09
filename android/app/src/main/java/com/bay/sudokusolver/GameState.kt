package com.bay.sudokusolver

/*
 * The digital board (design/screens/flow/Play.dc.html): printed digits, the player's
 * entries and notes, undo, mistakes, hints used and time. Immutable: every move returns
 * a new state, so Compose redraws from one value and undo is a list of snapshots.
 * Pure Kotlin (iOS port; the puzzle library reuses it in v1.1).
 */

/** A puzzle ready to play: printed digits and their unique solution. */
class Puzzle(val givens: IntArray, val solution: IntArray) {
    init {
        require(givens.size == 81 && solution.size == 81) { "need 81 cells" }
    }
    val difficulty: Difficulty by lazy { HintEngine.rate(givens) }
    val toFill: Int get() = givens.count { it == 0 }

    override fun equals(other: Any?) =
        other is Puzzle && givens.contentEquals(other.givens) && solution.contentEquals(other.solution)
    override fun hashCode() = givens.contentHashCode()
}

/** How the board helps, from Settings ("Game board"). Defaults are what a new player gets. */
data class BoardSettings(
    /** Count digits entered that don't match the solution. Off: it tells you a digit is wrong. */
    val showMistakes: Boolean = false,
    val showTimer: Boolean = true,
    /** Mark digits that clash with the same digit in their row, column or box (rules, not the answer). */
    val highlightConflicts: Boolean = true,
    /** Tint the selected cell's row and column. */
    val highlightLines: Boolean = true,
    /** Tint the selected cell's box. */
    val highlightBox: Boolean = true,
    /** Mark every cell holding the selected digit. */
    val highlightSame: Boolean = true,
)

class GameState private constructor(
    val puzzle: Puzzle,
    /** The player's digits, 0 = none. Never set on printed cells. */
    val entries: IntArray,
    /** Pencil notes per cell, bit d = note d. */
    val notes: IntArray,
    private val history: List<Pair<IntArray, IntArray>>,
    /** Digits entered that weren't the solution (counted when entered; erasing doesn't undo it). */
    val mistakes: Int,
    val hintsUsed: Int,
    val elapsedMs: Long,
) {
    constructor(puzzle: Puzzle) : this(puzzle, IntArray(81), IntArray(81), emptyList(), 0, 0, 0)

    val givens: IntArray get() = puzzle.givens

    fun value(i: Int) = if (puzzle.givens[i] != 0) puzzle.givens[i] else entries[i]
    fun isGiven(i: Int) = puzzle.givens[i] != 0
    fun notesOf(i: Int): List<Int> = (1..9).filter { notes[i] and (1 shl it) != 0 }

    val canUndo: Boolean get() = history.isNotEmpty()
    val toFill: Int get() = (0 until 81).count { value(it) == 0 }
    val finished: Boolean get() = (0 until 81).all { value(it) == puzzle.solution[it] }

    /** How many more of [d] the board needs (wrong entries count as placed, like on paper). */
    fun remaining(d: Int) = 9 - (0 until 81).count { value(it) == d }

    /** Cells whose digit repeats in their row, column or box. */
    fun conflicts(): Set<Int> = buildSet {
        for (i in 0 until 81) {
            val v = value(i)
            if (v != 0 && Cells.peers[i].any { value(it) == v }) add(i)
        }
    }

    /** Place [d] (1..9) in [i]. Also clears that digit from the notes the cell sees. */
    fun enter(i: Int, d: Int): GameState {
        if (isGiven(i) || finished || entries[i] == d) return this
        val e = entries.copyOf().also { it[i] = d }
        val n = notes.copyOf().also { n ->
            n[i] = 0
            for (p in Cells.peers[i]) n[p] = n[p] and (1 shl d).inv()
        }
        return copy(e, n, history + (entries to notes), mistakes = mistakes + if (d != puzzle.solution[i]) 1 else 0)
    }

    /** Toggle note [d] on an empty cell. */
    fun toggleNote(i: Int, d: Int): GameState {
        if (isGiven(i) || entries[i] != 0 || finished) return this
        val n = notes.copyOf().also { it[i] = it[i] xor (1 shl d) }
        return copy(entries, n, history + (entries to notes))
    }

    /** Clear the player's digit and notes in [i]. */
    fun erase(i: Int): GameState {
        if (isGiven(i) || finished || (entries[i] == 0 && notes[i] == 0)) return this
        val e = entries.copyOf().also { it[i] = 0 }
        val n = notes.copyOf().also { it[i] = 0 }
        return copy(e, n, history + (entries to notes))
    }

    fun undo(): GameState {
        val (e, n) = history.lastOrNull() ?: return this
        return copy(e, n, history.dropLast(1))
    }

    /** A hint's digit written in (Show me -> Keep playing, or Reveal this cell). Undoable. */
    fun reveal(i: Int): GameState {
        if (isGiven(i)) return this
        val d = puzzle.solution[i]
        val e = entries.copyOf().also { it[i] = d }
        val n = notes.copyOf().also { n -> n[i] = 0; for (p in Cells.peers[i]) n[p] = n[p] and (1 shl d).inv() }
        return copy(e, n, history + (entries to notes))
    }

    fun hintOpened(): GameState = copy(hintsUsed = hintsUsed + 1)
    fun tick(ms: Long): GameState = if (finished) this else copy(elapsedMs = elapsedMs + ms)

    fun nextHint(): HintResult = HintEngine.next(puzzle.givens, entries, puzzle.solution)

    private fun copy(
        entries: IntArray = this.entries,
        notes: IntArray = this.notes,
        history: List<Pair<IntArray, IntArray>> = this.history,
        mistakes: Int = this.mistakes,
        hintsUsed: Int = this.hintsUsed,
        elapsedMs: Long = this.elapsedMs,
    ) = GameState(puzzle, entries, notes, history.takeLast(MAX_UNDO), mistakes, hintsUsed, elapsedMs)

    companion object {
        const val MAX_UNDO = 500
    }
}

/** "04:12", or "1:04:12" past an hour. */
fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = s / 60 % 60; val sec = s % 60
    fun two(n: Long) = n.toString().padStart(2, '0') // not String.format: JVM-only, and this goes to iOS
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}
