package com.bay.sudokusolver

/*
 * The welcome pages a new user sees before the camera, and the rules for asking for the
 * camera. Pure Kotlin (no Android imports) so it moves to the shared iOS module; the
 * pages are drawn by ui/welcome/WelcomeScreen.kt and wired up in MainActivity.
 */

enum class WelcomePage {
    /** What the app is. */
    HELLO,
    /** Scan, check, play. */
    HOW,
    /** Why the camera, before Android's own dialog. Left out when it is already allowed. */
    CAMERA,
}

/** What the main button on the last page does. */
enum class WelcomeEnd {
    /** Ask Android for the camera (the page also offers Not now). */
    ALLOW_CAMERA,
    /** First run, camera already allowed: on to the scanner. */
    START,
    /** Opened again from Settings: just close. */
    DONE,
}

/**
 * Where the user is in the welcome. Immutable: each step returns a new value.
 *
 * [cameraAllowed] is the permission when the welcome opened. It is true on a first run
 * only after an update from a version without a welcome; then there is nothing to ask and
 * the camera page is left out. [replay]: opened from Settings, not a first run.
 */
data class Welcome(
    val cameraAllowed: Boolean,
    val replay: Boolean = false,
    val page: WelcomePage = WelcomePage.HELLO,
) {
    init {
        require(page in pages) { "$page is not shown when the camera is already allowed" }
    }

    val pages: List<WelcomePage>
        get() = if (cameraAllowed) listOf(WelcomePage.HELLO, WelcomePage.HOW) else WelcomePage.entries

    /** 0-based position of [page], for "2 of 3" and the page marks. */
    val index: Int get() = pages.indexOf(page)
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == pages.lastIndex

    /** The last page's main action; null on the pages before it (their button is Next). */
    val end: WelcomeEnd?
        get() = when {
            !isLast -> null
            page == WelcomePage.CAMERA -> WelcomeEnd.ALLOW_CAMERA
            replay -> WelcomeEnd.DONE
            else -> WelcomeEnd.START
        }

    /**
     * Skip is offered only while it would pass over a page. It jumps to the last page and
     * never past it: the camera page is what makes Android's dialog make sense, so a user
     * in a hurry still reads one sentence about why before being asked.
     */
    val canSkip: Boolean get() = index + 1 < pages.lastIndex

    fun next(): Welcome = if (isLast) this else copy(page = pages[index + 1])

    /** The page before; null on the first page (the caller decides: leave the app, or close a replay). */
    fun back(): Welcome? = if (isFirst) null else copy(page = pages[index - 1])

    fun skip(): Welcome = copy(page = pages.last())
}

/**
 * Asking for the camera. Android shows its dialog at most twice; after two refusals a
 * request returns "denied" at once without showing anything, and only the app's page in
 * the system settings can change it. Android doesn't say which case happened, so it is
 * worked out from what can be observed around the request.
 */
object CameraAsk {
    /**
     * A person can't read the dialog and answer it this fast; a request that comes back
     * sooner was answered by Android itself. A guess, not a measurement: check it on the
     * phone (see CLAUDE.md, "Welcome").
     */
    const val NO_DIALOG_MS = 450L

    /**
     * The request came back denied: was the dialog shown at all? False means the tap on
     * "Allow camera" did nothing the user could see, so the caller opens the app's
     * settings page instead.
     *
     * [rationaleBefore] / [rationaleAfter]: Android's shouldShowRequestPermissionRationale
     * before the request and after the answer. It is true only between the first refusal
     * and the second, so:
     *   never asked, refused now      false -> true    dialog shown
     *   refused once, refused again   true  -> false   dialog shown (and it was the last one)
     *   refused twice already         false -> false   no dialog, answer comes back at once
     *   never asked, dialog dismissed false -> false   dialog shown (Android 11+ doesn't count
     *                                                  a dismissal), but it took the user time
     * The last two differ only by [elapsedMs], hence the time limit.
     */
    fun dialogWasShown(rationaleBefore: Boolean, rationaleAfter: Boolean, elapsedMs: Long): Boolean =
        rationaleBefore || rationaleAfter || elapsedMs >= NO_DIALOG_MS
}
