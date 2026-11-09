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
 * What survives Android killing the process (onSaveInstanceState): the game in progress
 * and, if the board was open, how it was being played. Restoring opens that board
 * straight over the camera. Check isn't saved: its photo is too big for the Bundle and
 * a rescan is quick. Pure Kotlin, plain text.
 */
object SavedSession {
    fun encode(game: GameState, playing: PlayMode?): String = (playing?.name ?: "-") + "\n" + game.encode()

    /** The game and the screen to show, or null for anything unreadable (start at the camera). */
    fun decode(text: String): Pair<GameState, NavStack>? {
        val mode = text.substringBefore('\n')
        val game = GameState.decode(text.substringAfter('\n', "")) ?: return null
        val playing = PlayMode.entries.firstOrNull { it.name == mode && it != PlayMode.SOLUTION }
        return game to (playing?.let { NavStack().open(Route.Play(game.puzzle, it)) } ?: NavStack())
    }
}
