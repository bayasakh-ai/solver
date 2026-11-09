package com.bay.sudokusolver.ui.newpuzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.Difficulty
import com.bay.sudokusolver.GameState
import com.bay.sudokusolver.ui.board.FlowHeader
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudokuLensTheme

/*
 * Play a puzzle: pick a difficulty, get a generated puzzle (Generator.kt) that HintEngine
 * rates exactly that. Not in the design files yet: built from Choose's option rows.
 */

private fun blurb(d: Difficulty) = when (d) {
    Difficulty.EASY -> "Lots of givens. Each step: one place left in a box."
    Difficulty.MEDIUM -> "Look along rows and columns too."
    Difficulty.TRICKY -> "Some cells only show their digit once you count what they see."
    Difficulty.HARD -> "Needs a step beyond singles. Hints can reveal a cell when you’re stuck."
}

/**
 * [making]: the difficulty being generated (its row says so; taps are ignored meanwhile).
 * [inProgress]: a generated game not finished yet, offered first as Carry on.
 */
@Composable
fun NewPuzzleScreen(
    making: Difficulty?,
    inProgress: GameState?,
    onPick: (Difficulty) -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            FlowHeader("Play a puzzle", "Choose a difficulty", "A new puzzle, rated by the same steps hints use.",
                "Back to camera", onBack)
            Column(Modifier.padding(horizontal = 22.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (inProgress != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .border(1.dp, Classical.AccentStroke, MaterialTheme.shapes.small)
                            .clickable(enabled = making == null, role = Role.Button, onClick = onResume)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LucideIcons.Play, null, tint = Classical.AccentText, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Carry on", style = MaterialTheme.typography.titleLarge)
                            Text("${inProgress.puzzle.difficulty.label} · ${inProgress.toFill} to fill",
                                style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
                        }
                        Icon(LucideIcons.ChevronRight, null, tint = Classical.AccentText, modifier = Modifier.size(20.dp))
                    }
                }
                for (d in Difficulty.entries) Level(d, making, onPick)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Level(d: Difficulty, making: Difficulty?, onPick: (Difficulty) -> Unit) {
    val shape = MaterialTheme.shapes.small
    val busy = making == d
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .border(if (busy) 1.5.dp else 1.dp, if (busy) Classical.AccentStroke else Classical.Ink.copy(alpha = 0.22f), shape)
            .background(if (busy) Ramp.Accent100 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = making == null, role = Role.Button, onClick = { onPick(d) })
            .semantics(mergeDescendants = true) {
                contentDescription = "${d.label}, ${d.dots} of 4"
                if (busy) stateDescription = "Making a puzzle"
            }
            .alpha(if (making != null && !busy) 0.45f else 1f)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.label, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { k ->
                        Box(Modifier.size(8.dp).then(
                            if (k < d.dots) Modifier.background(Classical.Ink) else Modifier.border(1.dp, Ramp.Neutral500)))
                    }
                }
            }
            Text(if (busy) "Making a puzzle…" else blurb(d), style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
        }
        Icon(LucideIcons.ChevronRight, null, tint = Classical.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Preview(name = "Play a puzzle", widthDp = 390, heightDp = 844)
@Composable
private fun PNewPuzzle() = SudokuLensTheme {
    NewPuzzleScreen(making = null, inProgress = null, onPick = {}, onResume = {}, onBack = {})
}

@Preview(name = "Play a puzzle · making", widthDp = 390, heightDp = 844)
@Composable
private fun PNewPuzzleMaking() = SudokuLensTheme {
    NewPuzzleScreen(making = Difficulty.TRICKY, inProgress = null, onPick = {}, onResume = {}, onBack = {})
}
