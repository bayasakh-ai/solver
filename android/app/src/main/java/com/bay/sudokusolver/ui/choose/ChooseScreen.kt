package com.bay.sudokusolver.ui.choose

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.PlayMode
import com.bay.sudokusolver.Puzzle
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.ui.board.FlowHeader
import com.bay.sudokusolver.ui.board.SudokuGrid
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudofolioTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/* Puzzle ready (design/screens/flow/Choose.dc.html): difficulty and three ways to play. */

@Composable
fun ChooseScreen(
    puzzle: Puzzle,
    resumable: Boolean,
    onPlay: (PlayMode) -> Unit,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    /** Digits the user wrote on paper (Mark as mine): the board starts with them. */
    yours: Int = 0,
) {
    val difficulty = puzzle.difficulty
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            FlowHeader("Step 3 of 3", "Puzzle ready", "How do you want to play it?", "Back to check", onBack)

            Row(
                Modifier.padding(horizontal = 22.dp).padding(top = 18.dp).fillMaxWidth()
                    .border(1.dp, Classical.Divider, MaterialTheme.shapes.small).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SudokuGrid(Modifier.width(139.dp).clearAndSetSemantics { }, boxLine = 1.5.dp) { i ->
                    val v = puzzle.givens[i]
                    if (v != 0) Text("$v", style = SudokuType.PrintedDigit.copy(fontSize = 10.sp, lineHeight = 10.sp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fact("Difficulty") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "${difficulty.label}, ${difficulty.dots} of 4"
                            }) {
                            Text(difficulty.label, style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(4) { k ->
                                    Box(Modifier.size(8.dp).then(
                                        if (k < difficulty.dots) Modifier.background(Classical.Ink)
                                        else Modifier.border(1.dp, Ramp.Neutral500)))
                                }
                            }
                        }
                    }
                    Fact("Hardest step") { Text(difficulty.hardestStep, style = MaterialTheme.typography.bodySmall) }
                    Fact("To fill") {
                        Text("${puzzle.toFill - yours} of 81 cells" + if (yours > 0) " ($yours yours already)" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 22.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Option(LucideIcons.Lightbulb, "Play with hints",
                    if (resumable || yours > 0) "Carry on where you left off. When you’re stuck, a hint shows you where to look, then why."
                    else "Solve it on the phone. When you’re stuck, a hint shows you where to look, then why.",
                    primary = true) { onPlay(PlayMode.HINTS) }
                Option(LucideIcons.Pencil, "Solve it myself",
                    if (resumable || yours > 0) "Carry on, without hints. You can still ask for one later."
                    else "The same board without hints. You can still ask for one later.") { onPlay(PlayMode.MYSELF) }
                Option(LucideIcons.Eye, "Show solution", "Fill in all ${puzzle.toFill} answers now.") { onPlay(PlayMode.SOLUTION) }
            }

            Spacer(Modifier.height(24.dp)) // scrolls on short screens, so no weight here
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 22.dp).height(48.dp)
                    .clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onCamera).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LucideIcons.Camera, null, tint = Classical.TextBody2, modifier = Modifier.size(18.dp))
                Text("Back to the camera for live answers", style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = SudokuType.Kicker.copy(fontSize = 11.sp), color = Classical.TextSecondary)
        value()
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, text: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val border = if (primary) Classical.AccentStroke else Classical.Ink.copy(alpha = 0.22f)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).border(1.dp, if (primary) Classical.AccentStroke else Classical.Ink.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Classical.AccentText else Classical.Ink, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
        }
        Icon(LucideIcons.ChevronRight, null, tint = if (primary) Classical.AccentText else Classical.TextSecondary,
            modifier = Modifier.size(20.dp))
    }
}

@Preview(name = "Choose", widthDp = 390, heightDp = 844)
@Composable
private fun PChoose() = SudofolioTheme {
    ChooseScreen(
        Puzzle(Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
            Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934")),
        resumable = false, onPlay = {}, onBack = {}, onCamera = {},
    )
}
