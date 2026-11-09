package com.bay.sudokusolver.ui.settings

import android.content.Context
import com.bay.sudokusolver.AnswerPalette
import com.bay.sudokusolver.BoardSettings

/**
 * User settings, saved in SharedPreferences (small, synchronous, no extra
 * dependency). Read once at start; written on every change.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var palette: AnswerPalette
        get() = prefs.getString(KEY_PALETTE, null)
            ?.let { name -> AnswerPalette.entries.firstOrNull { it.name == name } }
            ?: AnswerPalette.DEFAULT // unknown name (e.g. a removed palette): back to the default
        set(value) = prefs.edit().putString(KEY_PALETTE, value.name).apply()

    /** Debug thumbnail of the flattened grid + frame stats. Off by default. */
    var debug: Boolean
        get() = prefs.getBoolean(KEY_DEBUG, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG, value).apply()

    /** Play board helpers ("Game board"). Missing keys fall back to BoardSettings' defaults. */
    var board: BoardSettings
        get() {
            val d = BoardSettings()
            return BoardSettings(
                showMistakes = prefs.getBoolean(KEY_MISTAKES, d.showMistakes),
                showTimer = prefs.getBoolean(KEY_TIMER, d.showTimer),
                highlightConflicts = prefs.getBoolean(KEY_CONFLICTS, d.highlightConflicts),
                highlightLines = prefs.getBoolean(KEY_LINES, d.highlightLines),
                highlightBox = prefs.getBoolean(KEY_BOX, d.highlightBox),
                highlightSame = prefs.getBoolean(KEY_SAME, d.highlightSame),
            )
        }
        set(v) = prefs.edit()
            .putBoolean(KEY_MISTAKES, v.showMistakes)
            .putBoolean(KEY_TIMER, v.showTimer)
            .putBoolean(KEY_CONFLICTS, v.highlightConflicts)
            .putBoolean(KEY_LINES, v.highlightLines)
            .putBoolean(KEY_BOX, v.highlightBox)
            .putBoolean(KEY_SAME, v.highlightSame)
            .apply()

    private companion object {
        const val KEY_PALETTE = "answer_palette"
        const val KEY_DEBUG = "debug_thumbnail"
        const val KEY_MISTAKES = "board_show_mistakes"
        const val KEY_TIMER = "board_show_timer"
        const val KEY_CONFLICTS = "board_highlight_conflicts"
        const val KEY_LINES = "board_highlight_lines"
        const val KEY_BOX = "board_highlight_box"
        const val KEY_SAME = "board_highlight_same"
    }
}
