package com.bay.sudokusolver

import android.content.Context

/**
 * Keeps the game in progress on disk (SharedPreferences "game", app-private storage),
 * written after every change, so it survives process death, swiping the app away and a
 * phone restart. The rules (what to write when, the text format) are in SavedSession and
 * GameState, pure Kotlin; this class only stores strings.
 *
 * apply() writes in the background but Android flushes pending writes before the
 * activity stops, so a swipe from Recents (which comes after onStop) loses nothing; a
 * hard kill mid-write can lose at most the last move.
 */
class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)

    /** The kept game, its clock brought up to the last tick saved. Null: nothing to continue. */
    fun load(): SavedSession.Saved? {
        val text = prefs.getString(KEY_GAME, null) ?: return null
        // unreadable: offer nothing, but don't delete it (a format bug must not wipe a
        // game; a fixed version can still read it). The next move overwrites it.
        val saved = SavedSession.decode(text) ?: return null
        val ticks = prefs.getLong(KEY_ELAPSED, 0L) - saved.game.elapsedMs
        return if (ticks > 0) SavedSession.Saved(saved.game.tick(ticks), saved.mode) else saved
    }

    /** Write what changed from [before] to [after] (see SavedSession.change). */
    fun update(before: GameState?, after: GameState?, mode: PlayMode) {
        when (SavedSession.change(before, after)) {
            SavedSession.Change.None -> Unit
            SavedSession.Change.Time -> prefs.edit().putLong(KEY_ELAPSED, after!!.elapsedMs).apply()
            SavedSession.Change.Game -> prefs.edit()
                .putString(KEY_GAME, SavedSession.encode(after!!, mode))
                .putLong(KEY_ELAPSED, after.elapsedMs)
                .apply()
            SavedSession.Change.Clear -> clear()
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_ELAPSED = "elapsed_ms"
    }
}
