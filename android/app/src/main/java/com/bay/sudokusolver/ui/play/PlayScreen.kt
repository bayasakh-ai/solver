package com.bay.sudokusolver.ui.play

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.bay.sudokusolver.FinishSummary
import com.bay.sudokusolver.PuzzleSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.BoardSettings
import com.bay.sudokusolver.Cells
import com.bay.sudokusolver.GameState
import com.bay.sudokusolver.Hint
import com.bay.sudokusolver.HintResult
import com.bay.sudokusolver.PlayMode
import com.bay.sudokusolver.Puzzle
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.Technique
import com.bay.sudokusolver.formatTime
import com.bay.sudokusolver.ui.board.BackButton
import com.bay.sudokusolver.ui.board.FlowButton
import com.bay.sudokusolver.ui.board.IconButton
import com.bay.sudokusolver.ui.board.SudokuGrid
import com.bay.sudokusolver.ui.board.cellLabel
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType
import kotlinx.coroutines.delay

/*
 * The digital board (design/screens/flow/Play.dc.html + PlayHint*.dc.html). Game rules
 * live in GameState and hints in HintEngine (pure Kotlin); this file draws them.
 * Printed digits: regular sans in ink. The player's digits and revealed answers:
 * semibold serif in the answer colour, so the two differ by colour AND shape.
 */

/** An open hint: what the engine said, the level shown (1-3), and the advanced reveal. */
private class OpenHint(val result: HintResult, val level: Int = 1, val revealed: Boolean = false, val replay: Int = 0) {
    val hint: Hint? get() = (result as? HintResult.Step)?.hint
    val advanced: Boolean get() = hint?.technique == Technique.ADVANCED
    fun copy(level: Int = this.level, revealed: Boolean = this.revealed, replay: Int = this.replay) =
        OpenHint(result, level, revealed, replay)
}

@Composable
fun PlayScreen(
    game: GameState,
    mode: PlayMode,
    settings: BoardSettings,
    running: Boolean,
    /**
     * Apply a move to the LATEST game. A function, not a new state: the clock ticks and
     * taps can land before the next redraw, and a state computed from the drawn game
     * would undo whatever happened in between.
     */
    update: ((GameState) -> GameState) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onPlayInstead: () -> Unit,
    onScanAnother: () -> Unit = {},
    onPlayAnother: () -> Unit = {},
    initialHintLevel: Int = 0,
) {
    val solution = mode == PlayMode.SOLUTION
    var sel by remember { mutableIntStateOf(-1) }
    var notesMode by remember { mutableStateOf(false) }
    var open by remember {
        mutableStateOf(if (initialHintLevel > 0) OpenHint(game.nextHint(), initialHintLevel.coerceAtMost(3)) else null)
    }
    // Solved pop-up: once per finished game; closing it leaves the finished board in view
    var finishSeen by rememberSaveable(game.puzzle.hashCode()) { mutableStateOf(false) }
    val summary = if (solution) null else game.summary()
    if (summary != null && !finishSeen) {
        FinishDialog(summary, onClose = { finishSeen = true },
            onScanAnother = { finishSeen = true; onScanAnother() }, onPlayAnother = { finishSeen = true; onPlayAnother() })
    }
    // timer: one second at a time while the screen is showing and the app is in front
    LaunchedEffect(running, solution, game.finished) {
        if (!running || solution || game.finished) return@LaunchedEffect
        while (true) {
            delay(1000)
            update { it.tick(1000) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(game, mode, settings, onBack, onSettings)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Board(
                    game, solution, settings, sel = if (open != null) -1 else sel, open,
                    onPick = { if (open == null && !solution) sel = it },
                    modifier = Modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp),
                )
            }
            StatusLine(game, solution, settings, Modifier.padding(horizontal = 22.dp).padding(top = 10.dp))
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                val o = open
                when {
                    solution -> SolutionFooter(onPlayInstead)
                    o != null -> HintSheet(
                        o, game.hintsUsed,
                        onLevel = { open = o.copy(level = it, replay = o.replay + 1) },
                        onReplay = { open = o.copy(replay = o.replay + 1) },
                        onReveal = { open = o.copy(revealed = true) },
                        onClose = {
                            // a revealed advanced cell is written in even when closed with X (as in the design)
                            if (o.advanced && o.revealed) update { it.reveal(o.hint!!.target) }
                            open = null
                        },
                        onKeepPlaying = {
                            o.hint?.let { h -> update { it.reveal(h.target) } }
                            open = null
                        },
                    )
                    else -> Keypad(
                        game, mode, notesMode, settings.showRemaining,
                        onUndo = { update { it.undo() } },
                        onErase = { if (sel >= 0) update { it.erase(sel) } },
                        onNotes = { notesMode = !notesMode },
                        onHint = {
                            val r = game.nextHint()
                            if (r != HintResult.Solved) {
                                update { it.hintOpened() }
                                open = OpenHint(r)
                                sel = -1
                            }
                        },
                        onDigit = { d ->
                            val i = sel
                            if (i >= 0) update { if (notesMode) it.toggleNote(i, d) else it.enter(i, d) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(game: GameState, mode: PlayMode, settings: BoardSettings, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackButton("Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(if (game.puzzle.source == PuzzleSource.GENERATED) "NEW PUZZLE" else "SCANNED PUZZLE", style = SudokuType.Kicker.copy(fontSize = 11.sp), color = Classical.AccentText)
            val title = when {
                mode == PlayMode.SOLUTION -> "Solution"
                game.finished -> "Solved"
                else -> game.puzzle.difficulty.label
            }
            Text(title, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp))
        }
        if (settings.showTimer && mode != PlayMode.SOLUTION) {
            val t = formatTime(game.elapsedMs)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Time $t" },
            ) {
                Icon(LucideIcons.Clock, null, tint = Classical.TextBody2, modifier = Modifier.size(16.dp))
                Text(t, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Classical.TextBody2)
            }
        }
        IconButton(LucideIcons.Sliders, "Board settings", onSettings)
    }
}

@Composable
private fun Board(
    game: GameState,
    solution: Boolean,
    settings: BoardSettings,
    sel: Int,
    open: OpenHint?,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = SudokuTheme.colors
    val answer = colors.answer(AnswerStep.DEEP)
    val hint = open?.hint
    val focus = remember(hint) { hint?.let { if (it.technique == Technique.ADVANCED) setOf(it.target) else it.focus } }
    val conflicts = remember(game) { if (settings.highlightConflicts && !solution) game.conflicts() else emptySet() }
    val selVal = if (sel >= 0) game.value(sel) else 0
    val measurer = rememberTextMeasurer()
    val reduceMotion = rememberReduceMotion()

    fun value(i: Int) = if (solution) game.puzzle.solution[i] else game.value(i)
    fun peer(i: Int) = sel >= 0 && i != sel && (
        (settings.highlightLines && (Cells.row(i) == Cells.row(sel) || Cells.col(i) == Cells.col(sel))) ||
            (settings.highlightBox && Cells.box(i) == Cells.box(sel)))
    fun same(i: Int) = settings.highlightSame && selVal != 0 && i != sel && value(i) == selVal

    // level 3 plays on a loop; levels 1-2 and reduced motion show the still frame
    val o = open
    val animating = o != null && hint != null && o.level == 3 && !reduceMotion && !o.advanced
    val t = if (animating) key(o!!.replay) { loopProgress() } else STILL
    val drawLevel = when { o == null || hint == null -> 0; o.advanced -> 3; else -> o.level }
    val revealed = o?.revealed == true

    SudokuGrid(
        modifier,
        background = { i ->
            when {
                focus != null -> if (i in focus) Color.Transparent else colors.dimCell
                i == sel || same(i) -> colors.selectedCell
                peer(i) -> colors.peerCell
                else -> Color.Transparent
            }
        },
        overlay = if (hint != null) { cell -> drawHint(hint, drawLevel, t, cell, colors, measurer, answer, revealed) } else null,
    ) { i ->
        val v = value(i)
        val given = game.isGiven(i)
        val dim = focus != null && i !in focus
        val clash = i in conflicts
        val what = when {
            v == 0 -> "empty" + game.notesOf(i).takeIf { it.isNotEmpty() }?.let { ", notes ${it.joinToString(" ")}" }.orEmpty()
            given -> "$v, printed"
            solution -> "$v, solution"
            else -> "$v, yours"
        } + (if (clash) ", clashes" else "")
        Box(
            Modifier.fillMaxSize()
                .then(if (i == sel) Modifier.border(2.dp, Classical.Ink) else Modifier)
                .clickable(enabled = !solution, role = Role.Button, onClickLabel = "Select") { onPick(i) }
                .semantics { contentDescription = cellLabel(i, what); selected = i == sel },
            contentAlignment = Alignment.Center,
        ) {
            if (clash) Box(Modifier.fillMaxSize().padding(4.dp).border(1.5.dp, colors.conflict, MaterialTheme.shapes.extraSmall))
            val notes = if (o?.advanced == true && hint!!.target == i && !revealed) hint.candidates else game.notesOf(i)
            when {
                v != 0 -> Text(
                    "$v",
                    style = (if (given) SudokuType.PrintedDigit.copy(color = colors.printedDigit)
                    else PlayerDigit.copy(color = if (clash) colors.conflict else answer)).copy(
                        textDecoration = if (same(i)) TextDecoration.Underline else null),
                    modifier = Modifier.alpha(if (dim) 0.32f else 1f),
                )
                notes.isNotEmpty() && !solution -> NotesGrid(notes, Modifier.alpha(if (dim) 0.32f else 1f))
            }
        }
    }
}

/** The player's digits: Lora semibold (SudokuType.PlayerDigit, changed from Cormorant after the phone test). */
internal val PlayerDigit = SudokuType.PlayerDigit

@Composable
private fun NotesGrid(notes: List<Int>, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(2.dp)) {
        for (r in 0 until 3) Row(Modifier.weight(1f)) {
            for (c in 0 until 3) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val d = r * 3 + c + 1
                if (d in notes) Text("$d", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp,
                    letterSpacing = 0.sp), color = Classical.TextSecondary)
            }
        }
    }
}

@Composable
private fun loopProgress(): Float {
    val transition = rememberInfiniteTransition(label = "hint")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    return t
}

/** Animations off in the system settings: show still frames (prefers-reduced-motion in the design). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
private fun StatusLine(game: GameState, solution: Boolean, settings: BoardSettings, modifier: Modifier) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (solution) {
            SolutionLegend(style)
            return@Row
        }
        Text(if (game.finished) "Solved${if (settings.showTimer) " in ${formatTime(game.elapsedMs)}" else ""}" else "${game.toFill} to fill",
            style = style, color = Classical.TextSecondary)
        if (settings.showMistakes) {
            Text(plural(game.mistakes, "mistake"), style = style,
                color = if (game.mistakes > 0) SudokuTheme.colors.warningPaper else Classical.TextSecondary)
        }
        Text(plural(game.hintsUsed, "hint") + " used", style = style, color = Classical.TextSecondary)
    }
}

/** Show solution: printed digits in ink sans, the solution in the answer colour, serif. */
@Composable
private fun SolutionLegend(style: TextStyle) {
    val colors = SudokuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("5", style = SudokuType.PrintedDigit.copy(fontSize = 16.sp, color = colors.printedDigit))
            Text("Printed", style = style, color = Classical.TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("3", style = PlayerDigit.copy(fontSize = 18.sp, lineHeight = 18.sp, color = colors.answer(AnswerStep.DEEP)))
            Text("Solution", style = style, color = Classical.TextSecondary)
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

@Composable
private fun SolutionFooter(onPlayInstead: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Every answer is filled in. Want to solve it yourself instead?", style = MaterialTheme.typography.bodySmall,
            color = Classical.TextSecondary)
        FlowButton("Play it instead", onPlayInstead, Modifier.fillMaxWidth(), icon = LucideIcons.Pencil, primary = true)
    }
}

@Composable
private fun Keypad(
    game: GameState,
    mode: PlayMode,
    notesMode: Boolean,
    showRemaining: Boolean,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onNotes: () -> Unit,
    onHint: () -> Unit,
    onDigit: (Int) -> Unit,
) {
    val done = game.finished
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(58f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tool = Modifier.weight(1f).fillMaxHeight()
            Tool(LucideIcons.Undo, "Undo", game.canUndo, onUndo, tool)
            Tool(LucideIcons.Eraser, "Erase", !done, onErase, tool)
            Tool(LucideIcons.Pencil, if (notesMode) "Notes on" else "Notes", !done, onNotes, tool, active = notesMode)
            Tool(LucideIcons.Lightbulb, "Hint", !done, onHint, tool, outlined = mode == PlayMode.HINTS)
        }
        Column(Modifier.weight(3 * 60f + 16f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 3) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 1..3) {
                    val d = row * 3 + c
                    val left = game.remaining(d)
                    DigitKey(d, left, showRemaining, enabled = left > 0 && !done, onClick = { onDigit(d) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier,
                 active: Boolean = false, outlined: Boolean = false) {
    val shape = MaterialTheme.shapes.small
    val accent = Classical.AccentStroke
    Column(
        modifier.clip(shape)
            .then(if (outlined) Modifier.border(1.dp, accent, shape) else Modifier)
            .background(if (active) Ramp.Accent100 else Color.Transparent)
            .drawBehind { if (active) drawRect(accent, Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (active) stateDescription = "On" }
            .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = if (outlined) Classical.AccentText else Classical.Ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal))
    }
}

@Composable
private fun DigitKey(d: Int, left: Int, showLeft: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier.clip(shape).border(1.dp, Classical.Ink.copy(alpha = 0.22f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (showLeft) "$d, $left left" else "$d" }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Text("$d", style = PlayerDigit.copy(fontSize = 26.sp, lineHeight = 26.sp))
        if (showLeft && left > 0) Text("$left", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
            color = Classical.TextSecondary, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp))
    }
}

// --- solved pop-up -----------------------------------------------------------------

/** "Solved!" with time, hints used and mistakes; Scan another / Play another. Not yet in the design files. */
@Composable
private fun FinishDialog(summary: FinishSummary, onClose: () -> Unit, onScanAnother: () -> Unit, onPlayAnother: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.background)
                .border(1.dp, Classical.AccentStroke, MaterialTheme.shapes.small)
                .padding(start = 22.dp, end = 10.dp, top = 6.dp, bottom = 22.dp)
                .semantics { contentDescription = "Puzzle solved" },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PUZZLE COMPLETE", style = SudokuType.Kicker, color = Classical.AccentText, modifier = Modifier.weight(1f))
                IconButton(LucideIcons.X, "Close", onClose)
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Solved!", style = MaterialTheme.typography.displaySmall)
                Text(summary.line, style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
                Row(Modifier.fillMaxWidth().border(1.dp, Classical.Divider, MaterialTheme.shapes.small).padding(vertical = 12.dp)) {
                    FinishFact(LucideIcons.Clock, "Time", summary.time, Modifier.weight(1f))
                    FinishFact(LucideIcons.Lightbulb, "Hints", summary.hints, Modifier.weight(1f))
                    FinishFact(LucideIcons.AlertCircle, "Mistakes", summary.mistakesText, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowButton("Scan another", onScanAnother, Modifier.weight(1f), icon = LucideIcons.Camera)
                    FlowButton("Play another", onPlayAnother, Modifier.weight(1f), icon = LucideIcons.Grid, primary = true)
                }
            }
        }
    }
}

@Composable
private fun FinishFact(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Classical.TextBody2, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = Classical.Ink)
    }
}

// --- hint sheet ---------------------------------------------------------------------

private fun techniqueName(t: Technique) = when (t) {
    Technique.LAST_EMPTY_CELL -> "Last empty cell"
    Technique.HIDDEN_SINGLE -> "Hidden single"
    Technique.NAKED_SINGLE -> "Naked single"
    Technique.ADVANCED -> "Advanced"
}

@Composable
private fun HintSheet(
    open: OpenHint,
    hintsUsed: Int,
    onLevel: (Int) -> Unit,
    onReplay: () -> Unit,
    onReveal: () -> Unit,
    onClose: () -> Unit,
    onKeepPlaying: () -> Unit,
) {
    val hint = open.hint
    val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
    val (kicker, title, body) = when {
        hint == null -> Triple("Hint $hintsUsed", "A digit you entered doesn’t fit",
            (open.result as HintResult.Mistakes).count.let { n ->
                "${if (n == 1) "One of your digits doesn’t" else "$n of your digits don’t"} match the solution, and the next simple " +
                    "step depends on it. Check your entries, or undo."
            })
        open.advanced && open.revealed -> Triple("Hint $hintsUsed · Revealed", "${Cells.name(hint.target).replaceFirstChar { it.uppercase() }} is a ${hint.digit}",
            "That opens up simple steps again. Ask for another hint if you get stuck.")
        open.advanced -> Triple("Hint $hintsUsed · Advanced", hint.text, hint.reason)
        open.level == 1 -> Triple("Hint $hintsUsed", hint.nudgeTitle, hint.nudgeText)
        open.level == 2 -> Triple("Hint $hintsUsed · ${techniqueName(hint.technique)}", hint.text, hint.reason)
        else -> Triple("Hint $hintsUsed · ${techniqueName(hint.technique)}", "So the ${hint.digit} goes here",
            "${Cells.name(hint.target).replaceFirstChar { it.uppercase() }}.")
    }
    Column(
        Modifier.fillMaxSize().shadow(14.dp, shape).clip(shape).background(Ramp.Neutral100)
            .border(1.dp, Classical.Divider, shape)
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 18.dp)
            .semantics { contentDescription = "Hint" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    hint != null && !open.advanced -> LevelTabs(open.level, onLevel)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(LucideIcons.Lightbulb, null, tint = Classical.AccentText, modifier = Modifier.size(18.dp))
                        Text(if (hint == null) "Check your digits" else "No simple step left",
                            style = MaterialTheme.typography.bodySmall, color = Classical.TextBody2)
                    }
                }
            }
            IconButton(LucideIcons.X, "Close hint", onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kicker.uppercase(), style = SudokuType.Kicker.copy(fontSize = 11.sp), color = Classical.AccentText)
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2, modifier = Modifier.padding(top = 2.dp))
            if (hint != null && !open.advanced && open.level == 3) Steps(hint, open.replay)
            if (open.advanced && !open.revealed) Candidates(hint!!)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                hint == null -> FlowButton("Keep trying", onClose, Modifier.weight(1f), primary = true)
                open.advanced && !open.revealed -> {
                    FlowButton("Keep trying", onClose)
                    FlowButton("Reveal this cell", onReveal, Modifier.weight(1f), icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
                }
                open.advanced -> FlowButton("Keep playing", onKeepPlaying, Modifier.weight(1f), icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
                else -> {
                    FlowButton("Replay", onReplay, icon = LucideIcons.Reset, enabled = open.level == 3)
                    if (open.level < 3) FlowButton("Next level", { onLevel(open.level + 1) }, Modifier.weight(1f),
                        icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
                    else FlowButton("Keep playing", onKeepPlaying, Modifier.weight(1f), icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
                }
            }
        }
    }
}

@Composable
private fun LevelTabs(level: Int, onLevel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Nudge", "Technique", "Show me").forEachIndexed { k, name ->
            val n = k + 1
            val cur = n == level
            val done = n < level
            val line = when { cur -> Classical.Ink; done -> Ramp.Accent600; else -> Ramp.Neutral500 }
            val w = when { cur -> 4.dp; done -> 3.dp; else -> 2.dp }
            Box(
                Modifier.weight(1f).height(48.dp)
                    .drawBehind {
                        val y = size.height - w.toPx() / 2
                        val effect = if (!cur && !done) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
                        drawLine(line, Offset(0f, y), Offset(size.width, y), w.toPx(), pathEffect = effect)
                    }
                    .clickable(role = Role.Tab, onClick = { onLevel(n) })
                    .semantics { selected = cur },
                contentAlignment = Alignment.Center,
            ) {
                Text("$n $name", style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (cur || done) Classical.Ink else Classical.TextSecondary)
            }
        }
    }
}

/** Show me's three steps, each lit in turn with the animation. */
@Composable
private fun Steps(hint: Hint, replay: Int) {
    val reduce = rememberReduceMotion()
    val lit = if (reduce) -1 else key(replay) { HintKeys.step(loopProgress()) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        hint.steps.forEachIndexed { k, s ->
            Text("${k + 1}. $s", style = MaterialTheme.typography.bodySmall, color = Classical.Ink,
                modifier = Modifier.alpha(if (lit < 0 || lit == k) 1f else 0.45f))
        }
    }
}

/** Advanced fallback: what the cell still allows, the rest struck through. */
@Composable
private fun Candidates(hint: Hint) {
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().border(1.dp, Classical.Divider, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${Cells.name(hint.target)}: only ${hint.candidates.joinToString(" or ")}"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Row ${Cells.row(hint.target) + 1} · Col ${Cells.col(hint.target) + 1}".uppercase(),
            style = SudokuType.Kicker.copy(fontSize = 11.sp), color = Classical.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..9) {
                val ok = d in hint.candidates
                Text("$d", style = SudokuType.PrintedDigit.copy(fontSize = 15.sp, lineHeight = 15.sp,
                    textDecoration = if (ok) null else TextDecoration.LineThrough),
                    color = if (ok) Classical.Ink else Ramp.Neutral500,
                    modifier = if (ok) Modifier.border(1.dp, Ramp.Accent700, MaterialTheme.shapes.extraSmall).padding(horizontal = 3.dp)
                    else Modifier.padding(horizontal = 3.dp))
            }
        }
    }
}

// --- previews (puzzle3, the design's states) -----------------------------------------

private val previewPuzzle = Puzzle(
    Solver.parse("005031040001000008794600000002000003900000005300000800000003517500000600070820900"),
    Solver.parse("685731249231549768794682351462958173918376425357214896829463517543197682176825934"),
)

private fun designGame(stuck: Boolean = false): GameState {
    var g = GameState(previewPuzzle)
    if (stuck) {
        val board = Solver.parse("005031040031000008794600301402000103908300405307000800009463517543000682176825934")
        for (i in 0 until 81) if (!g.isGiven(i) && board[i] != 0) g = g.enter(i, board[i])
    } else {
        for ((i, d) in listOf(22 to 8, 23 to 2, 24 to 3, 54 to 8, 55 to 2)) g = g.enter(i, d)
    }
    return g.tick(252_000)
}

@Preview(name = "Play", widthDp = 390, heightDp = 844)
@Composable
private fun PPlay() = SudokuLensTheme {
    PlayScreen(designGame().toggleNote(0, 6).toggleNote(0, 8), PlayMode.HINTS, BoardSettings(showMistakes = true), false, {}, {}, {}, {})
}

@Preview(name = "Play · hint level 2", widthDp = 390, heightDp = 844)
@Composable
private fun PHint2() = SudokuLensTheme {
    PlayScreen(designGame().hintOpened(), PlayMode.HINTS, BoardSettings(), false, {}, {}, {}, {}, initialHintLevel = 2)
}

@Preview(name = "Play · advanced fallback", widthDp = 390, heightDp = 844)
@Composable
private fun PHintAdvanced() = SudokuLensTheme {
    PlayScreen(designGame(stuck = true).hintOpened(), PlayMode.HINTS, BoardSettings(), false, {}, {}, {}, {}, initialHintLevel = 1)
}

@Preview(name = "Show solution", widthDp = 390, heightDp = 844)
@Composable
private fun PSolution() = SudokuLensTheme {
    PlayScreen(GameState(previewPuzzle), PlayMode.SOLUTION, BoardSettings(), false, {}, {}, {}, {})
}

@Preview(name = "Play · solved", widthDp = 390, heightDp = 844)
@Composable
private fun PSolved() = SudokuLensTheme {
    var g = designGame().hintOpened()
    for (i in 0 until 81) if (!g.isGiven(i)) g = g.reveal(i)
    PlayScreen(g, PlayMode.HINTS, BoardSettings(), false, {}, {}, {}, {})
}

/** Interactive in Android Studio's preview / PreviewActivity: moves are kept. */
@Preview(name = "Play · interactive", widthDp = 390, heightDp = 844)
@Composable
private fun PPlayInteractive() = SudokuLensTheme {
    var g by remember { mutableStateOf(designGame()) }
    PlayScreen(g, PlayMode.HINTS, BoardSettings(showMistakes = true), true, { f -> g = f(g) }, {}, {}, {})
}
