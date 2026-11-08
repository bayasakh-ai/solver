package com.bay.sudokusolver.ui.settings

import android.content.Context
import com.bay.sudokusolver.AnswerPalette

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

    private companion object {
        const val KEY_PALETTE = "answer_palette"
        const val KEY_DEBUG = "debug_thumbnail"
    }
}
