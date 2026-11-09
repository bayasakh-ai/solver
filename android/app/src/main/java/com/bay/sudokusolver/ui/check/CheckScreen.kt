package com.bay.sudokusolver.ui.check

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.Capture
import com.bay.sudokusolver.Fix
import com.bay.sudokusolver.SolveResult
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/*
 * Check the reading (design/screens/flow/Main.dc.html) — Stage 2 stub: shows what the
 * scan captured, read-only. Stage 3 adds the Reading/Photo switch, the tap-to-fix picker
 * with the model's second guess, and Looks right -> Choose.
 */

@Composable
fun CheckScreen(capture: Capture, onBack: () -> Unit) {
    val colors = SudokuTheme.colors
    val unsure = capture.inkUnsure.count { it }
    val corrected = capture.corrected
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 22.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Back to camera", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LucideIcons.ArrowLeft, contentDescription = "Back to camera",
                        tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.weight(1f))
                Text("STEP 2 OF 3", style = SudokuType.Kicker, color = Classical.AccentText)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Check the reading", style = MaterialTheme.typography.headlineMedium)
                Text("Compare it with your puzzle. Fixing cells comes next.",
                    style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
            }

            ReadingGrid(capture, Modifier.padding(top = 18.dp).padding(horizontal = 22.dp).widthIn(max = 420.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).border(1.5.dp, colors.unsureRing, CircleShape))
                        Text("Unsure · $unsure", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("3", style = SudokuType.PrintedDigit.copy(textDecoration = TextDecoration.Underline))
                        Text("Corrected · ${corrected.size}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (capture.inkIgnored > 0) {
                    Text("${capture.inkIgnored} pen entries ignored", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${capture.filled} digits read" +
                        if (capture.solved) ". It solves." else ". No unique solution yet: a digit is probably misread.",
                    style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2,
                )
            }
        }
    }
}

/** Read-only 9x9 grid: printed digits in sans, coral ring = unsure ink, underline = corrected. */
@Composable
private fun ReadingGrid(capture: Capture, modifier: Modifier = Modifier) {
    val colors = SudokuTheme.colors
    val corrected = capture.corrected
    Box(modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.gridCell)
            val step = size.width / 9
            for (k in 1 until 9) {
                val box = k % 3 == 0
                val color = if (box) colors.gridBoxLine else colors.gridCellLine.copy(alpha = 0.5f)
                val w = (if (box) 2 else 1).dp.toPx()
                drawLine(color, Offset(k * step, 0f), Offset(k * step, size.height), w)
                drawLine(color, Offset(0f, k * step), Offset(size.width, k * step), w)
            }
            val outer = 2.dp.toPx()
            drawRect(colors.gridBoxLine, topLeft = Offset(outer / 2, outer / 2),
                size = androidx.compose.ui.geometry.Size(size.width - outer, size.height - outer),
                style = androidx.compose.ui.graphics.drawscope.Stroke(outer))
        }
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) Row(Modifier.weight(1f)) {
                for (c in 0 until 9) {
                    val i = r * 9 + c
                    val d = capture.digits[i]
                    val ring = capture.inkUnsure[i]
                    val fixed = i in corrected
                    val what = (if (d == 0) "empty" else "$d") + (if (ring) ", unsure" else "") + (if (fixed) ", corrected" else "")
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .semantics { contentDescription = "Row ${r + 1}, column ${c + 1}, $what" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ring) Box(Modifier.fillMaxSize(0.8f).border(1.5.dp, colors.unsureRing, CircleShape))
                        if (d != 0) Text("$d", style = SudokuType.PrintedDigit.copy(
                            textDecoration = if (fixed) TextDecoration.Underline else null))
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PCheck() = SudokuLensTheme {
    val read = Solver.parse("005081040001000008794600000002000003900000005300000800000003517500000600070820900")
    val fixed = read.copyOf().also { it[4] = 3 }
    val solution = Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934")
    val probs = Array(81) { i -> if (read[i] == 0) null else FloatArray(9) { k -> if (k == read[i] - 1) 0.9f else 0.0125f } }
    val unsure = BooleanArray(81).also { it[36] = true; it[63] = true }
    val capture = Capture.from(read, probs, SolveResult(fixed, solution, listOf(Fix(4, 8, 0.6f, 3, 0.3f))), unsure, 56)
    CheckScreen(capture, onBack = {})
}
