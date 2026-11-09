package com.bay.sudokusolver

/**
 * Which screen is showing. The camera is always the root; everything else opens over
 * it, so a solved puzzle is still there on return. The core flow is
 * Check -> Choose -> Play (design/screens/flow). Pure Kotlin (no Android imports) so it moves to the shared
 * iOS module; MainActivity only renders [NavStack.current].
 */
sealed interface Route {
    /** Live AR scanner (answers only). */
    data object Camera : Route

    /** Answer palette and debug thumbnail. */
    data object Settings : Route

    /** Check the reading after a capture: from Play on Solved, or Check the reading on Needs review. */
    data class Check(val capture: Capture) : Route

    /**
     * Puzzle ready: difficulty and the three ways to play. [start]: the digits the user
     * wrote on paper (Mark as mine), 0 elsewhere; the board starts with them.
     */
    class Choose(val puzzle: Puzzle, val start: IntArray = IntArray(81)) : Route {
        override fun equals(other: Any?) = other is Choose && puzzle == other.puzzle && start.contentEquals(other.start)
        override fun hashCode() = puzzle.hashCode()
    }

    /** Play a puzzle: pick a difficulty, play a generated one. */
    data object NewPuzzle : Route

    /** The digital board. [mode] SOLUTION shows every answer, read-only. */
    data class Play(val puzzle: Puzzle, val mode: PlayMode) : Route
}

enum class PlayMode { HINTS, MYSELF, SOLUTION }

/** Back stack with the camera at the bottom. Immutable: each change returns a new stack. */
data class NavStack(val stack: List<Route> = listOf(Route.Camera)) {
    init {
        require(stack.firstOrNull() == Route.Camera) { "the camera is always the root" }
    }

    val current: Route get() = stack.last()
    val onCamera: Boolean get() = stack.size == 1

    /** Open [route] on top. Opening the screen already on top does nothing (double taps). */
    fun open(route: Route): NavStack =
        if (route == Route.Camera) NavStack() else if (current == route) this else NavStack(stack + route)

    /** Back: drop the top screen. On the camera there is nothing to go back to (null). */
    fun back(): NavStack? = if (onCamera) null else NavStack(stack.dropLast(1))
}

/**
 * The game in progress, kept in storage after every move so it survives process death,
 * the app being swiped away and a phone restart (GameStore writes it; the camera bar
 * offers Continue). One slot: a new game replaces it, a finished one is dropped. Check
 * isn't kept: its photo is large and a rescan is quick. Pure Kotlin, plain text.
 */
object SavedSession {
    /** A kept game and how it was being played (Hints or Myself; never Show solution). */
    class Saved(val game: GameState, val mode: PlayMode) {
        val board: Route.Play get() = Route.Play(game.puzzle, mode)
    }

    fun encode(game: GameState, mode: PlayMode): String = mode.name + "\n" + game.encode()

    /** Null for anything unreadable or finished: nothing to continue. */
    fun decode(text: String): Saved? {
        val mode = PlayMode.entries.firstOrNull { it.name == text.substringBefore('\n').trim() && it != PlayMode.SOLUTION }
            ?: PlayMode.HINTS
        val game = GameState.decode(text.substringAfter('\n', "")) ?: return null
        return Saved(game, mode).takeIf { keep(game) }
    }

    /** Worth keeping: a finished game has nothing to continue. */
    fun keep(game: GameState) = !game.finished

    /**
     * What to write when the game changed from [before] to [after]: the whole game after
     * a move, only the time after a clock tick (the game text is up to ~33 KB; the clock
     * ticks every second), nothing to keep once finished.
     */
    fun change(before: GameState?, after: GameState?): Change = when {
        after == null || !keep(after) -> Change.Clear
        before != null && after.sameMovesAs(before) -> if (after.elapsedMs == before.elapsedMs) Change.None else Change.Time
        else -> Change.Game
    }

    enum class Change { None, Time, Game, Clear }
}

