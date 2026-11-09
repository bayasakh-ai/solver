package com.bay.sudokusolver

/*
 * The digital board (design/screens/flow/Play.dc.html): printed digits, the player's
 * entries and notes, undo, mistakes, hints used and time. Immutable: every move returns
 * a new state, so Compose redraws from one value and undo is a list of snapshots.
 * Pure Kotlin (iOS port; the puzzle library reuses it in v1.1).
 */

/** Where a puzzle came from: the header says "Scanned puzzle" or "New puzzle". */
enum class PuzzleSource { SCANNED, GENERATED }

/** A puzzle ready to play: printed digits and their unique solution. */
class Puzzle(val givens: IntArray, val solution: IntArray, val source: PuzzleSource = PuzzleSource.SCANNED) {
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
    /** Small count on each keypad digit: how many more of it the board needs. */
    val showRemaining: Boolean = true,
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

    /**
     * Carry on from paper: [start] holds the digits the player wrote (Check -> Mark as
     * mine), 0 elsewhere. They are the player's entries as written, right or wrong
     * (never "fixed", CLAUDE.md), not mistakes made on this board, and not undoable.
     */
    constructor(puzzle: Puzzle, start: IntArray) : this(
        puzzle,
        IntArray(81) { i -> if (puzzle.givens[i] == 0) start[i] else 0 },
        IntArray(81), emptyList(), 0, 0, 0,
    )

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

    /** What the Solved pop-up says. Null until the board is finished. */
    fun summary(): FinishSummary? = if (finished) FinishSummary(elapsedMs, hintsUsed, mistakes) else null

    /**
     * The game as text, to survive Android killing the process (onSaveInstanceState).
     * Plain text, not Java serialization: it moves to iOS and stays readable. Keeps the
     * last [SAVED_UNDO] undo steps so the Bundle stays small (~33 KB at most).
     */
    fun encode(): String = buildString {
        appendLine(FORMAT)
        appendLine(puzzle.source.name)
        appendLine(digits(puzzle.givens))
        appendLine(digits(puzzle.solution))
        appendLine("$mistakes $hintsUsed $elapsedMs")
        appendLine(snapshot(entries, notes))
        for ((e, n) in history.takeLast(SAVED_UNDO)) appendLine(snapshot(e, n))
    }

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
        const val SAVED_UNDO = 100
        private const val FORMAT = "game v1"

        /** Back from [encode]. Null for anything that isn't a whole, consistent game (start fresh). */
        fun decode(text: String): GameState? = runCatching {
            val lines = text.lines().filter { it.isNotEmpty() }
            require(lines[0] == FORMAT)
            val puzzle = Puzzle(parseDigits(lines[2]), parseDigits(lines[3]), PuzzleSource.valueOf(lines[1]))
            require(Solver.solve(puzzle.givens, limit = 2).let { it.size == 1 && it[0].contentEquals(puzzle.solution) })
            val (mistakes, hints, elapsed) = lines[4].split(' ')
            val snaps = lines.drop(5).map(::parseSnapshot)
            for ((e, _) in snaps) require((0 until 81).all { e[it] == 0 || puzzle.givens[it] == 0 })
            val (entries, notes) = snaps[0]
            GameState(puzzle, entries, notes, snaps.drop(1), mistakes.toInt(), hints.toInt(), elapsed.toLong())
        }.getOrNull()

        private fun digits(a: IntArray) = a.joinToString("")
        private fun parseDigits(s: String): IntArray {
            require(s.length == 81 && s.all { it in '0'..'9' })
            return IntArray(81) { s[it] - '0' }
        }
        /** 81 entry digits, a space, then each cell's notes as three hex digits (bits 1-9). */
        private fun snapshot(e: IntArray, n: IntArray) =
            digits(e) + " " + n.joinToString("") { it.toString(16).padStart(3, '0') }
        private fun parseSnapshot(s: String): Pair<IntArray, IntArray> {
            val (e, n) = s.split(' ')
            require(n.length == 243)
            return parseDigits(e) to IntArray(81) { n.substring(it * 3, it * 3 + 3).toInt(16) and 0x3FE }
        }
    }
}

/** The Solved pop-up: time, hints used, mistakes made on the board. */
data class FinishSummary(val elapsedMs: Long, val hintsUsed: Int, val mistakes: Int) {
    val time: String get() = formatTime(elapsedMs)
    val hints: String get() = if (hintsUsed == 0) "No hints" else if (hintsUsed == 1) "1 hint" else "$hintsUsed hints"
    val mistakesText: String get() = if (mistakes == 0) "No mistakes" else if (mistakes == 1) "1 mistake" else "$mistakes mistakes"
    /** A line under "Solved!": praise only what was earned. */
    val line: String get() = when {
        hintsUsed == 0 && mistakes == 0 -> "Clean solve: no hints, no mistakes."
        hintsUsed == 0 -> "Solved without hints."
        else -> "Solved."
    }
}

/** "04:12", or "1:04:12" past an hour. */
fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = s / 60 % 60; val sec = s % 60
    fun two(n: Long) = n.toString().padStart(2, '0') // not String.format: JVM-only, and this goes to iOS
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}
