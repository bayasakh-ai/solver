package com.bay.sudokusolver.ui.check

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.Capture
import com.bay.sudokusolver.CellMark
import com.bay.sudokusolver.CheckResult
import com.bay.sudokusolver.CheckSession
import com.bay.sudokusolver.Fix
import com.bay.sudokusolver.SolveResult
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.UnsureReason
import com.bay.sudokusolver.ui.board.FlowButton
import com.bay.sudokusolver.ui.board.FlowHeader
import com.bay.sudokusolver.ui.board.IconButton
import com.bay.sudokusolver.ui.board.SudokuGrid
import com.bay.sudokusolver.ui.board.cellLabel
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/*
 * Check the reading (design/screens/flow/Main.dc.html + CheckFix.dc.html): the reading
 * next to the flattened photo; tap a cell to fix it with 1-9 / Empty, the model's
 * second guess dashed. Looks right -> Choose. Logic in CheckSession (pure).
 */

@Composable
fun CheckScreen(
    session: CheckSession,
    photo: Bitmap?,
    onEdit: (cell: Int, digit: Int) -> Unit,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onConfirm: () -> CheckResult,
    initialSel: Int = -1,
) {
    var photoOn by remember { mutableStateOf(false) }
    var sel by remember { mutableIntStateOf(initialSel) }
    var problem by remember { mutableStateOf<CheckResult?>(null) }
    val image = remember(photo) { photo?.asImageBitmap() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                FlowHeader("Step 2 of 3", "Check the reading", "Compare it with your puzzle. Tap any cell to change it.",
                    "Back to camera", onBack)
                ViewSwitch(photoOn, hasPhoto = image != null, onChange = { photoOn = it; if (it) sel = -1 },
                    Modifier.padding(horizontal = 22.dp).padding(top = 14.dp))
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    val boardMod = Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp)
                    if (photoOn && image != null) {
                        Image(image, "The captured puzzle, flattened", boardMod.fillMaxWidth().aspectRatio(1f))
                    } else {
                        ReadingGrid(session, sel, onPick = { sel = it; problem = null }, boardMod)
                    }
                }
                Legend(session, Modifier.padding(horizontal = 22.dp).padding(top = 12.dp))
                Spacer(Modifier.height(if (sel >= 0) 300.dp else 170.dp)) // room to scroll above the bottom panel
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                if (sel >= 0) {
                    FixSheet(session, sel, image, onPick = { d -> onEdit(sel, d); sel = -1 }, onClose = { sel = -1 })
                } else {
                    BottomActions(session, problem, onRescan, onConfirm = {
                        val r = onConfirm()
                        problem = r.takeIf { it !is CheckResult.Ready }
                    })
                }
            }
        }
    }
}

@Composable
private fun ViewSwitch(photoOn: Boolean, hasPhoto: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    Row(modifier.fillMaxWidth().height(40.dp).clip(shape).border(1.dp, Classical.Ink.copy(alpha = 0.22f), shape)) {
        Segment("Reading", LucideIcons.Grid, on = !photoOn, enabled = true, onClick = { onChange(false) }, Modifier.weight(1f))
        Segment("Photo", LucideIcons.Camera, on = photoOn, enabled = hasPhoto, onClick = { onChange(true) }, Modifier.weight(1f))
    }
}

@Composable
private fun Segment(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, enabled: Boolean,
                    onClick: () -> Unit, modifier: Modifier) {
    val accent = Classical.AccentStroke
    Row(
        modifier.fillMaxSize()
            .background(if (on) Ramp.Accent100 else Color.Transparent)
            .drawBehind { if (on) drawRect(accent, Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { selected = on },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else 0.4f
        Icon(icon, null, tint = Classical.Ink.copy(alpha = alpha), modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = Classical.Ink.copy(alpha = alpha))
    }
}

/** The reading: printed digits in sans, coral ring = unsure, underline = corrected. */
@Composable
private fun ReadingGrid(session: CheckSession, sel: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val colors = SudokuTheme.colors
    SudokuGrid(modifier, background = { if (it == sel) colors.selectedCell else Color.Transparent }) { i ->
        val v = session.value(i)
        val mark = session.mark(i)
        val what = (if (v == 0) "empty" else "$v") + when (mark) {
            CellMark.UNSURE -> ", unsure"; CellMark.CORRECTED -> ", corrected"; CellMark.CHANGED -> ", changed by you"; CellMark.NONE -> ""
        }
        Box(
            Modifier.fillMaxSize()
                .then(if (i == sel) Modifier.border(2.dp, Classical.Ink) else Modifier)
                .clickable(role = Role.Button, onClickLabel = "Change") { onPick(i) }
                .semantics { contentDescription = cellLabel(i, what) },
            contentAlignment = Alignment.Center,
        ) {
            if (mark == CellMark.UNSURE) Box(Modifier.fillMaxSize(0.8f).border(1.5.dp, colors.unsureRing, CircleShape))
            if (v != 0) Digit(v, underline = mark == CellMark.CORRECTED)
        }
    }
}

@Composable
private fun Digit(d: Int, underline: Boolean, sizeSp: Int = 20) {
    val line = Ramp.Accent700
    Text(
        "$d", style = SudokuType.PrintedDigit.copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp),
        modifier = if (!underline) Modifier else Modifier.drawBehind {
            val y = size.height + 3.dp.toPx()
            drawLine(line, Offset(-2.dp.toPx(), y), Offset(size.width + 2.dp.toPx(), y), 2.dp.toPx())
        },
    )
}

@Composable
private fun Legend(session: CheckSession, modifier: Modifier) {
    val colors = SudokuTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).border(1.5.dp, colors.unsureRing, CircleShape), contentAlignment = Alignment.Center) {
                    Text("9", style = SudokuType.PrintedDigit.copy(fontSize = 12.sp, lineHeight = 12.sp))
                }
                Text("Unsure · ${session.unsureCount}", style = MaterialTheme.typography.bodySmall, color = Classical.TextBody2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(horizontal = 4.dp)) { Digit(3, underline = true, sizeSp = 14) }
                Text("Corrected · ${session.correctedCount}", style = MaterialTheme.typography.bodySmall, color = Classical.TextBody2)
            }
        }
        if (session.capture.inkIgnored > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LucideIcons.PenOff, null, tint = Classical.TextBody2, modifier = Modifier.padding(horizontal = 2.dp).size(18.dp))
                val n = session.capture.inkIgnored
                Text("$n pen ${if (n == 1) "entry" else "entries"} ignored", style = MaterialTheme.typography.bodySmall,
                    color = Classical.TextBody2)
            }
        }
    }
}

@Composable
private fun BottomActions(session: CheckSession, problem: CheckResult?, onRescan: () -> Unit, onConfirm: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .drawBehind { drawLine(Classical.Divider, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (problem != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(LucideIcons.AlertCircle, null, tint = SudokuTheme.colors.warningPaper, modifier = Modifier.size(18.dp))
                Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = Classical.Ink)
            }
        } else {
            Text(summary(session), style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowButton("Rescan", onRescan, icon = LucideIcons.Reset)
            FlowButton("Looks right", onConfirm, Modifier.weight(1f), icon = LucideIcons.Check, primary = true)
        }
    }
}

private fun summary(s: CheckSession): String {
    val look = s.unsureCount + s.correctedCount
    return "${s.filled} digits read" +
        (if (look > 0) ", $look worth a look" else "") +
        (if (s.changedCount > 0) ", ${s.changedCount} changed by you" else "") +
        ". If the grid matches the paper, carry on."
}

private fun problemText(r: CheckResult) = when (r) {
    CheckResult.NoSolution -> "These digits have no solution, so one is still misread. Compare them with the Photo view."
    CheckResult.Ambiguous -> "These digits fit more than one solution. A digit is probably missing."
    CheckResult.TooFew -> "Too few digits for a sudoku (it needs at least 17)."
    is CheckResult.Ready -> ""
}

/** Fixing a cell: the camera's crop, why it's marked, 1-9 + Empty, the suggestion dashed. */
@Composable
private fun FixSheet(session: CheckSession, i: Int, photo: androidx.compose.ui.graphics.ImageBitmap?, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val v = session.value(i)
    val alt = session.suggestion(i)
    val (title, note) = sheetText(session, i)
    val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
    Column(
        Modifier.fillMaxWidth().shadow(16.dp, shape).clip(shape).background(Ramp.Neutral100)
            .border(1.dp, Classical.Divider, shape)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 22.dp)
            .semantics { contentDescription = "Change row ${i / 9 + 1}, column ${i % 9 + 1}" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            if (photo != null) CellCrop(photo, i)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Row ${i / 9 + 1} · Column ${i % 9 + 1}".uppercase(), style = SudokuType.Kicker, color = Classical.AccentText)
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary)
            }
            IconButton(LucideIcons.X, "Close", onClose, Modifier.padding(top = 0.dp))
        }
        val keys = (1..9).toList() + 0
        for (row in keys.chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in row) PickerKey(d, current = d == v, dashed = d == alt, onClick = { onPick(d) }, Modifier.weight(1f))
            }
        }
    }
}

private fun sheetText(s: CheckSession, i: Int): Pair<String, String> {
    val v = s.value(i)
    val read = if (v == 0) "empty" else "$v"
    return when (s.mark(i)) {
        CellMark.CORRECTED -> {
            val f = s.fix(i)!!
            val to = if (f.to == 0) "empty" else "${f.to}"
            "Corrected: ${f.from} → $to" to
                "The camera read ${f.from}, which left no solution. Changed to $to, its next most likely reading."
        }
        CellMark.UNSURE -> when (s.unsureReason(i)) {
            UnsureReason.INK -> "Is this printed?" to "The ink is close to a pen colour. If you wrote it, make it Empty (dashed)."
            else -> "Is this a $v?" to "Read as $v, but not confidently. It might be a ${s.capture.secondGuess[i]} (dashed)."
        }
        CellMark.CHANGED -> (if (v == 0) "You cleared it" else "You set $v") to "Pick again if it still doesn’t match the paper."
        CellMark.NONE -> "Read as $read" to "Pick what the paper shows."
    }
}

@Composable
private fun CellCrop(photo: androidx.compose.ui.graphics.ImageBitmap, i: Int) {
    Canvas(
        Modifier.size(80.dp).background(Classical.Surface).border(1.dp, Classical.Divider).padding(4.dp)
            .semantics { contentDescription = "What the camera saw in this cell" }
    ) {
        val cell = photo.width / 9
        drawImage(photo, IntOffset(i % 9 * cell, i / 9 * cell), IntSize(cell, cell),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}

@Composable
private fun PickerKey(d: Int, current: Boolean, dashed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    val coral = SudokuTheme.colors.unsureRing
    var m = modifier.height(48.dp).clip(shape)
    m = when {
        current -> m.background(Ramp.Accent100).border(2.dp, Classical.Ink, shape)
        dashed -> m.drawBehind {
            val w = 1.5.dp.toPx()
            drawRoundRect(coral, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                CornerRadius(4.dp.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        else -> m.border(1.dp, Classical.Ink.copy(alpha = 0.22f), shape)
    }
    Box(
        m.clickable(role = Role.Button, onClickLabel = if (d == 0) "Make the cell empty" else "Set $d", onClick = onClick)
            .semantics { selected = current; if (dashed) contentDescription = "${if (d == 0) "Empty" else "$d"}, suggested" },
        contentAlignment = Alignment.Center,
    ) {
        if (d == 0) Text("Empty", style = MaterialTheme.typography.bodySmall)
        else Text("$d", style = SudokuType.PrintedDigit.copy(fontSize = 21.sp, lineHeight = 21.sp))
    }
}

// --- previews (puzzle3, as in the design files) -------------------------------------

private fun previewSession(): CheckSession {
    val read = Solver.parse("005081040001000008794600000002000003900000005300000800000003517500000600070820900")
    val fixed = read.copyOf().also { it[4] = 3 }
    val solution = Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934")
    val probs = Array(81) { i ->
        if (read[i] == 0) null else FloatArray(9) { 0.001f }.also { p ->
            val top = when (i) { 36 -> 0.81f; 63 -> 0.77f; else -> 0.99f }
            p[read[i] - 1] = top
            p[(when (i) { 36 -> 4; 63 -> 6; else -> read[i] % 9 + 1 }) - 1] = 1f - top - 0.007f
        }
    }
    val capture = Capture.from(read, probs, SolveResult(fixed, solution, listOf(Fix(4, 8, 0.6f, 3, 0.3f))), null, 56)
    return CheckSession(capture)
}

@Preview(name = "Check", widthDp = 390, heightDp = 844)
@Composable
private fun PCheck() = SudokuLensTheme {
    CheckScreen(previewSession(), null, { _, _ -> }, {}, {}, { CheckResult.TooFew })
}

@Preview(name = "Check · fixing a cell", widthDp = 390, heightDp = 844)
@Composable
private fun PCheckFix() = SudokuLensTheme {
    CheckScreen(previewSession(), null, { _, _ -> }, {}, {}, { CheckResult.TooFew }, initialSel = 36)
}
