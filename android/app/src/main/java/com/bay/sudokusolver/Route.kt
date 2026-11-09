package com.bay.sudokusolver

/**
 * Which screen is showing. The camera is always the root; everything else opens over
 * it, so a solved puzzle is still there on return. Stage 3 adds Choose and Play here
 * (design/screens/flow). Pure Kotlin (no Android imports) so it moves to the shared
 * iOS module; MainActivity only renders [NavStack.current].
 */
sealed interface Route {
    /** Live AR scanner (answers only). */
    data object Camera : Route

    /** Answer palette and debug thumbnail. */
    data object Settings : Route

    /** Check the reading after a capture: from Play on Solved, or Check the reading on Needs review. */
    data class Check(val capture: Capture) : Route
}

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
