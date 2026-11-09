package com.bay.sudokusolver.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.bay.sudokusolver.AnswerPalette
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.SignalColors

/*
 * Colours from the "Classical" design system (design/system/styles.css) and the
 * Sudoku Lens screens. Classical is a mono scheme: one amber accent, used as
 * strokes, rules and outlines rather than fills.
 */

/** 0xRRGGBB (as used by the pure-Kotlin colour files) -> opaque Compose Color. */
fun rgb(value: Int): Color = Color(0xFF000000.toInt() or value)

/** Tonal ramps from styles.css (OKLCH, shared lightness scale). */
object Ramp {
    val Neutral100 = Color(0xFFF8F4F4)
    val Neutral200 = Color(0xFFEAE7E7)
    val Neutral300 = Color(0xFFD7D3D3)
    val Neutral400 = Color(0xFFBAB6B6)
    val Neutral500 = Color(0xFF9B9797)
    val Neutral600 = Color(0xFF7D7979)
    val Neutral700 = Color(0xFF605D5D)
    val Neutral800 = Color(0xFF444141)
    val Neutral900 = Color(0xFF2D2B2B)

    val Accent100 = Color(0xFFFFF3E4)
    val Accent200 = Color(0xFFFFE3BF)
    val Accent300 = Color(0xFFFACB8D)
    val Accent400 = Color(0xFFE1AD66)
    val Accent500 = Color(0xFFC28D41)
    val Accent600 = Color(0xFFA06F24)
    val Accent700 = Color(0xFF7D5411)
    val Accent800 = Color(0xFF5A3B0A)
    val Accent900 = Color(0xFF3A270D)
}

/** Base roles on the light ground (Settings, sheets, Check grid). */
object Classical {
    val Ground = Color(0xFFF3F2F2)        // --color-bg
    val Surface = Color(0xFFEAE9E9)       // --color-surface
    val Ink = Color(0xFF201F1D)           // --color-text
    /** --color-accent. Only 3.0:1 on the ground: strokes, rings, icons, large text. */
    val AccentStroke = Color(0xFFB68235)
    /** Accent for text at paragraph size (6.0:1 on the ground). */
    val AccentText = Ramp.Accent700
    val Divider = Ink.copy(alpha = 0.16f) // --color-divider
    val TextSecondary = Ramp.Neutral700   // captions, sheet subtitles
    val TextBody2 = Ramp.Neutral800       // longer secondary copy
}

/** Dark chrome over the camera image: status chip, docked bottom bar. */
object Chrome {
    val Bar = Color(0xED171513)           // rgba(23,21,19,.93)
    val Chip = Color(0xE0171513)          // rgba(23,21,19,.88)
    val Text = Color(0xFFF3F2F2)
    val TextMuted = Color(0xCCF3F2F2)     // 80%
    val Accent = Ramp.Accent400           // #e1ad66: primary button, kicker, icons
    val ButtonBorder = Color(0x6BF3F2F2)  // 42%
    val ChipBorder = Color(0x29F3F2F2)    // 16%
    val BarRule = Color(0x80E1AD66)       // top rule of the docked bar, 50%
    val Pressed = Color(0x42E1AD66)       // rgba(225,173,102,.26)
}

/**
 * Colours outside the Material roles: answers, warnings, hint marks, overlays.
 * Values that tests check come from [SignalColors] / [AnswerPalette] so they can't drift.
 */
@Immutable
data class SudokuColors(
    val palette: AnswerPalette,
    // Warnings: coral, always paired with a shape (double outline).
    val warningPaper: Color = rgb(SignalColors.WARNING_PAPER),
    val warningCamera: Color = rgb(SignalColors.WARNING_CAMERA),
    // Reading / hold-steady outline and other amber marks on the camera.
    val readingCamera: Color = rgb(SignalColors.AMBER_CAMERA),
    // Unsure cell: coral ring (a shape, so never colour alone).
    val unsureRing: Color = rgb(SignalColors.UNSURE_RING),
    val unsureFill: Color = rgb(SignalColors.UNSURE_FILL),
    val unsureRingOnDark: Color = rgb(SignalColors.UNSURE_RING_CAMERA),
    // Hint reasoning strokes (rings, sweeps, crosses, lanes, box frames).
    val hintStrokePaper: Color = rgb(SignalColors.HINT_STROKE_PAPER),
    val hintStrokeDark: Color = rgb(SignalColors.HINT_STROKE_DARK),
    val hintTintPaper: Color = Color(0x47FACB8D),   // 28%
    val hintTintDark: Color = Color(0x24FACB8D),    // 14%
    val hintGlowPaper: Color = Color(0x99E1AD66),   // 60%
    val hintGlowDark: Color = Color(0x59FACB8D),    // 35%
    val strokeHaloPaper: Color = Color(0xD9FFFFFF), // 85% white behind hint strokes
    val strokeHaloDark: Color = Color(0xB3000000),  // 70% black
    // Dimming outside the relevant area.
    val dimHint: Color = Color(0xA80E0D0C),         // 66%
    val dimReview: Color = Color(0xB80E0D0C),       // 72%
    // Digital grid (Check screen, play board).
    val gridCell: Color = Color(0xFFF8F6F4),
    val gridBoxLine: Color = Classical.Ink,
    val gridCellLine: Color = Ramp.Neutral500,
    val printedDigit: Color = Classical.Ink,
    val gridThinLine: Color = Color(0x33201F1D),    // 20% ink
    // Play board (design/screens/flow/Play.dc.html)
    val selectedCell: Color = Ramp.Accent100,       // selected cell + same digit
    val peerCell: Color = Color(0xFFEEECE9),        // selected cell's row / column / box
    val dimCell: Color = Color(0x17201F1D),         // outside a hint's focus (9%)
    /** Clashing digits: coral, with an inset frame so it isn't colour alone. */
    val conflict: Color = rgb(SignalColors.WARNING_PAPER),
) {
    fun answer(step: AnswerStep): Color = rgb(palette.color(step))
    fun answerHalo(step: AnswerStep): Color = rgb(palette.halo(step))
    /** Outline colour when solved: the bright step, since it sits on the camera's dark edge. */
    val solvedCamera: Color get() = rgb(palette.bright)
}

val LocalSudokuColors = staticCompositionLocalOf { SudokuColors(AnswerPalette.DEFAULT) }
