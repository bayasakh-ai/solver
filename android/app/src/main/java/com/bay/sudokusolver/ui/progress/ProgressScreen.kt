package com.bay.sudokusolver.ui.progress

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.Progress
import com.bay.sudokusolver.ProgressChecker
import com.bay.sudokusolver.Puzzle
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.ui.board.FlowButton
import com.bay.sudokusolver.ui.board.FlowHeader
import com.bay.sudokusolver.ui.board.SudokuGrid
import com.bay.sudokusolver.ui.board.cellLabel
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.SudofolioTheme
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/*
 * Check my progress (not in the design files; built from the Check and Play screens'
 * parts). The digits the user wrote on paper against the solution: the count first,
 * where they are only when asked (ProgressChecker has the rules). Wrong digits get the
 * coral inset frame Play uses for conflicts, digits the scan couldn't read for sure the
 * coral ring Check uses: shape and colour, never colour alone.
 */
@Composable
fun ProgressScreen(
    puzzle: Puzzle,
    /** The digits the user wrote on the page, 0 elsewhere. */
    entries: IntArray,
    /** Cells whose reading was not confident: ringed, not judged. */
    doubtful: Set<Int>,
    /** Per cell, what else the handwriting could be (bit d = digit d): see ProgressChecker. */
    couldBe: IntArray,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    startShown: Boolean = false,
) {
    val progress = remember(puzzle, entries, doubtful, couldBe) { ProgressChecker.check(puzzle, entries, doubtful, couldBe) }
    var shown by rememberSaveable { mutableStateOf(startShown) }
    val colors = SudokuTheme.colors
    val answer = colors.answer(AnswerStep.DEEP)
    val wrong = remember(progress) { progress.wrong.toSet() }
    val unread = remember(progress) { progress.doubtful.toSet() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            FlowHeader("On paper so far", ProgressChecker.headline(progress), ProgressChecker.detail(progress, shown),
                "Back", onBack)

            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                SudokuGrid(Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp)) { i ->
                    val given = puzzle.givens[i]
                    val mine = if (given == 0) entries[i] else 0
                    val marked = shown && i in wrong
                    val ringed = i in unread
                    val what = when {
                        given != 0 -> "$given, printed"
                        mine == 0 -> "empty"
                        ringed -> "$mine, yours, not read for sure"
                        marked -> "$mine, yours, doesn’t fit"
                        else -> "$mine, yours"
                    }
                    Box(Modifier.fillMaxSize().semantics { contentDescription = cellLabel(i, what) }, contentAlignment = Alignment.Center) {
                        if (marked) Box(Modifier.fillMaxSize().padding(4.dp).border(1.5.dp, colors.conflict, MaterialTheme.shapes.extraSmall))
                        if (ringed) Box(Modifier.fillMaxSize(0.8f).border(1.5.dp, colors.unsureRing, CircleShape))
                        when {
                            given != 0 -> Text("$given", style = SudokuType.PrintedDigit.copy(color = colors.printedDigit))
                            mine != 0 -> Text("$mine", style = SudokuType.PlayerDigit.copy(color = if (marked) colors.conflict else answer))
                        }
                    }
                }
            }

            Facts(progress, Modifier.padding(horizontal = 22.dp).padding(top = 14.dp))

            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = 22.dp).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progress.wrong.isNotEmpty() && !shown) FlowButton("Show me where", { shown = true }, icon = LucideIcons.Eye)
                FlowButton(if (progress.finished) "Open the board" else "Carry on on the phone", onPlay, Modifier.weight(1f),
                    icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
            }
        }
    }
}

@Composable
private fun Facts(p: Progress, modifier: Modifier) {
    Row(modifier.fillMaxWidth().border(1.dp, Classical.Divider, MaterialTheme.shapes.small).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Fact("You wrote", "${p.written} of ${p.toFill}")
        Fact("Still empty", "${p.left}")
        if (p.doubtful.isNotEmpty()) Fact("Not judged", "${p.doubtful.size}")
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = SudokuType.Kicker.copy(fontSize = 11.sp), color = Classical.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

// --- previews (puzzle3 with a page half filled in) ------------------------------------

private val previewPuzzle = Puzzle(
    Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
    Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"),
)

/** The first [n] open cells filled in from the solution, with [wrong] of them changed. */
private fun paper(n: Int, vararg wrong: Int): IntArray {
    val e = IntArray(81)
    (0 until 81).filter { previewPuzzle.givens[it] == 0 }.take(n).forEach { e[it] = previewPuzzle.solution[it] }
    for (i in wrong) e[i] = e[i] % 9 + 1
    return e
}

@Preview(name = "Progress · the count first", widthDp = 390, heightDp = 844)
@Composable
private fun PProgress() = SudofolioTheme {
    ProgressScreen(previewPuzzle, paper(30, 0, 13), setOf(9), IntArray(81), {}, {})
}

@Preview(name = "Progress · shown where", widthDp = 390, heightDp = 844)
@Composable
private fun PProgressShown() = SudofolioTheme {
    ProgressScreen(previewPuzzle, paper(30, 0, 13), setOf(9), IntArray(81), {}, {}, startShown = true)
}

@Preview(name = "Progress · all fit", widthDp = 390, heightDp = 844)
@Composable
private fun PProgressFine() = SudofolioTheme {
    ProgressScreen(previewPuzzle, paper(22), emptySet(), IntArray(81), {}, {})
}
