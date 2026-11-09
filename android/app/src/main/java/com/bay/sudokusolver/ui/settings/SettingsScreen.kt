package com.bay.sudokusolver.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.AnswerPalette
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.BoardSettings
import com.bay.sudokusolver.ui.theme.AnswerDigit
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/* Settings (design v2, "Settings"): answer palette, game board helpers and the debug thumbnail. */

private data class PaletteInfo(val name: String, val note: String?, val recommended: Boolean)

private fun info(p: AnswerPalette) = when (p) {
    AnswerPalette.INK_BLUE -> PaletteInfo("Ink blue", null, recommended = true)
    AnswerPalette.HIGH_CONTRAST -> PaletteInfo("High contrast", "Darker blue on paper, thicker halo", recommended = false)
}

@Composable
fun SettingsScreen(
    palette: AnswerPalette,
    debug: Boolean,
    board: BoardSettings,
    onPalette: (AnswerPalette) -> Unit,
    onDebug: (Boolean) -> Unit,
    onBoard: (BoardSettings) -> Unit,
    onBack: () -> Unit,
) {
    // Surface blocks touches, so nothing reaches the camera screen underneath
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LucideIcons.ChevronLeft, contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Answer color", style = MaterialTheme.typography.titleLarge)
                Text(
                    "One hue in two steps: deep on paper, bright on dark screens. " +
                        "Cells in a deep shadow switch on their own.",
                    style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2,
                )
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).selectableGroup()) {
                for (p in AnswerPalette.entries) {
                    PaletteRow(p, selected = p == palette, onClick = { onPalette(p) })
                    HorizontalDivider(color = Classical.Ink.copy(alpha = 0.14f))
                }
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Game board", style = MaterialTheme.typography.titleLarge)
                Text("Help while you play on the phone.", style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                val rows = listOf(
                    Triple("Error counter", "Counts digits that don’t match the solution. Off by default: it tells you a digit is wrong.",
                        board.showMistakes) to { v: Boolean -> board.copy(showMistakes = v) },
                    Triple("Timer", "Time spent on the puzzle, paused while the app is in the background.",
                        board.showTimer) to { v: Boolean -> board.copy(showTimer = v) },
                    Triple("Highlight conflicts", "Frames a digit that repeats in its row, column or box.",
                        board.highlightConflicts) to { v: Boolean -> board.copy(highlightConflicts = v) },
                    Triple("Highlight row and column", "Tints the selected cell’s row and column.",
                        board.highlightLines) to { v: Boolean -> board.copy(highlightLines = v) },
                    Triple("Highlight box", "Tints the selected cell’s 3×3 box.",
                        board.highlightBox) to { v: Boolean -> board.copy(highlightBox = v) },
                    Triple("Highlight identical numbers", "Marks every cell with the selected digit.",
                        board.highlightSame) to { v: Boolean -> board.copy(highlightSame = v) },
                    Triple("Remaining counts", "Shows on each keypad digit how many more the board needs.",
                        board.showRemaining) to { v: Boolean -> board.copy(showRemaining = v) },
                )
                for ((info, change) in rows) {
                    ToggleRow(info.first, info.second, info.third, { onBoard(change(it)) }, Modifier)
                    HorizontalDivider(color = Classical.Ink.copy(alpha = 0.14f))
                }
            }

            Text("Camera", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp))
            ToggleRow("Debug thumbnail", "Shows the flattened grid and frame stats over the camera.", debug, onDebug,
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun PaletteRow(palette: AnswerPalette, selected: Boolean, onClick: () -> Unit) {
    val i = info(palette)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Swatch(palette, AnswerStep.DEEP, Color(0xFFE8E2D6))
            Swatch(palette, AnswerStep.BRIGHT, Color(0xFF161616))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(i.name, style = MaterialTheme.typography.titleMedium)
            i.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary) }
            if (i.recommended) {
                Text("Recommended", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = Ramp.Accent800,
                    modifier = Modifier.background(Ramp.Accent100, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        // radio: accent ring + dot when selected (Classical: colour as stroke)
        Box(
            Modifier.size(22.dp).border(1.5.dp, if (selected) Classical.AccentStroke else Ramp.Neutral500, CircleShape)
                .padding(5.dp),
        ) {
            if (selected) Box(Modifier.fillMaxSize().background(Classical.AccentStroke, CircleShape))
        }
    }
}

/** A 7 in the palette's step on paper or on a dark screen, as in the design. */
@Composable
private fun Swatch(palette: AnswerPalette, step: AnswerStep, background: Color) {
    Box(Modifier.size(44.dp).background(background, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        AnswerDigit(7, step, palette = palette,
            style = SudokuType.AnswerDigit.copy(fontSize = 26.sp, lineHeight = 26.sp))
    }
}

@Composable
private fun ToggleRow(title: String, text: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 10.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
        }
        // the word says the state too, not only the knob position and colour
        Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelMedium,
            color = Classical.TextSecondary, modifier = Modifier.width(24.dp))
        Box(
            Modifier.size(width = 52.dp, height = 32.dp)
                .background(if (on) Ramp.Accent100 else Color.Transparent, RoundedCornerShape(16.dp))
                .border(1.5.dp, if (on) Classical.AccentStroke else Ramp.Neutral500, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = if (on) 26.dp else 5.dp).size(19.dp)
                    .background(if (on) Classical.AccentStroke else Ramp.Neutral600, CircleShape)
            )
        }
    }
}

@Preview(name = "Settings", widthDp = 360, heightDp = 1500)
@Composable
private fun SettingsPreview() {
    SudokuLensTheme {
        SettingsScreen(AnswerPalette.INK_BLUE, debug = false, board = BoardSettings(), onPalette = {}, onDebug = {}, onBoard = {}, onBack = {})
    }
}

@Preview(name = "Settings · high contrast, debug on", widthDp = 360, heightDp = 780)
@Composable
private fun SettingsPreviewHc() {
    SudokuLensTheme(AnswerPalette.HIGH_CONTRAST) {
        SettingsScreen(AnswerPalette.HIGH_CONTRAST, debug = true, board = BoardSettings(showMistakes = true), onPalette = {}, onDebug = {}, onBoard = {}, onBack = {})
    }
}
