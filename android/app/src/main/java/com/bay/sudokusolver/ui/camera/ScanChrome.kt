package com.bay.sudokusolver.ui.camera

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.FrameResult
import com.bay.sudokusolver.ScanState
import com.bay.sudokusolver.ui.theme.Chrome
import com.bay.sudokusolver.ui.theme.LinePattern
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Spacing
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType
import com.bay.sudokusolver.ui.theme.outline

/*
 * Camera chrome, docked-bar direction (design v2, option "a"): a status chip with
 * the outline's line pattern at the top left, debug stats + thumbnail when enabled,
 * and a docked bottom bar with the state, its progress and its actions.
 * Live AR is answers-only: no Hint action (hints live on the digital board).
 */

/** One action tile in the bar. [active] = toggled on (Freeze while frozen). */
data class BarAction(
    val label: String,
    val icon: ImageVector,
    val primary: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/** Reading progress: [filled] of [total] segments; [paused] draws them hatched. */
data class Progress(val filled: Int, val total: Int, val paused: Boolean)

data class BarContent(
    val chip: String,
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val progress: Progress? = null,
    val actions: List<BarAction> = emptyList(),
)

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/**
 * What the bar says for each scan state. Wording follows the design. Live AR stays the
 * quick "just show me the answer" mode; [onCheck] hands the capture to the core flow
 * (Check -> Choose -> Play): "Play" on Solved (v2's Hint slot), "Check the reading" on
 * Needs review (v2's in-camera review card, replaced by the Check screen).
 */
fun barContent(
    result: FrameResult?,
    frozen: Boolean,
    error: String?,
    onFreeze: () -> Unit,
    onReset: () -> Unit,
    onCheck: () -> Unit,
    /** Play a puzzle (a generated one): offered while no grid is in view. */
    onPlayPuzzle: (() -> Unit)? = null,
    /** Continue the kept game: offered first while no grid is in view. */
    onContinue: (() -> Unit)? = null,
): BarContent {
    if (error != null) return BarContent("Error", LucideIcons.AlertCircle, "Something went wrong", error)
    val state = result?.state ?: ScanState.Searching
    return when (state) {
        ScanState.Searching -> BarContent("Searching", LucideIcons.Scan,
            "Point at a sudoku", "Fit the whole grid inside the frame",
            actions = listOfNotNull(
                onContinue?.let { BarAction("Continue", LucideIcons.Play, primary = true, onClick = it) },
                onPlayPuzzle?.let { BarAction("Play a puzzle", LucideIcons.Grid, onClick = it) },
            ))
        is ScanState.Reading -> BarContent("Puzzle found", LucideIcons.ScanLine,
            "Reading digits… ${state.frames}/${state.window}", "Keep the whole grid in view",
            Progress(state.frames, state.window, paused = false))
        is ScanState.HoldSteady -> BarContent("Hold steady", LucideIcons.Move,
            "Hold steady", "The image is blurred. Paused at ${state.frames}/${state.window}.",
            Progress(state.frames, state.window, paused = true))
        is ScanState.Solved -> {
            val answers = result?.puzzle?.count { it == 0 } ?: 0
            val fixed = if (state.fixes > 0) " · ${plural(state.fixes, "misread digit")} fixed" else ""
            BarContent(
                chip = if (frozen) "Solved · frozen" else "Solved",
                icon = LucideIcons.Check,
                title = "Solved",
                sub = if (frozen) "Image frozen. You can lower the phone."
                      else "${plural(answers, "answer")} drawn on the puzzle$fixed",
                actions = listOfNotNull(
                    if (result?.capture != null) BarAction("Play", LucideIcons.Pencil, onClick = onCheck) else null,
                    BarAction(if (frozen) "Live" else "Freeze", if (frozen) LucideIcons.Play else LucideIcons.Pause,
                        primary = true, active = frozen, onClick = onFreeze),
                    BarAction("Reset", LucideIcons.Reset, onClick = onReset),
                ),
            )
        }
        is ScanState.NeedsReview -> BarContent(
            chip = "Needs review",
            icon = LucideIcons.AlertCircle,
            title = "Couldn’t solve",
            sub = when (state.reason) {
                ScanState.Reason.TOO_FEW_DIGITS -> "Too few digits found. Move closer, or add the missing ones."
                ScanState.Reason.NO_UNIQUE_SOLUTION -> "Some digits may be misread. Check them, or rescan in better light."
            },
            actions = listOfNotNull(
                BarAction("Rescan", LucideIcons.Camera, primary = result?.capture == null, onClick = onReset),
                if (result?.capture != null) BarAction("Check the reading", LucideIcons.Check, primary = true, onClick = onCheck) else null,
            ),
        )
    }
}

/** Debug line: frame stats, as in the design's stats chip. */
fun statsText(r: FrameResult?): String = when {
    r == null -> "Starting camera…"
    r.corners == null -> "No grid · %.0f fps · %d ms".format(r.fps, r.processMs)
    else -> "%d digits · sharpness %.0f · %.0f fps · %d ms · steps %d µs · orient %d ms%s".format(
        r.digits, r.sharpness, r.fps, r.processMs, r.stepsMicros, r.orientMs,
        if (r.inkCells > 0) " · ink ignored in ${r.inkCells}" else "")
}

/** Top row: status chip (+ stats) on the left; debug thumbnail and settings on the right. */
@Composable
fun TopChrome(
    content: BarContent,
    result: FrameResult?,
    showDebug: Boolean,
    thumbnail: Bitmap?,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(content.chip, result?.state)
            if (showDebug) {
                Text(statsText(result), style = SudokuType.Stats, color = Chrome.Text,
                    modifier = Modifier.background(Chrome.Chip, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        if (showDebug && thumbnail != null) {
            Image(thumbnail.asImageBitmap(), contentDescription = "Flattened grid",
                modifier = Modifier.size(64.dp).border(2.dp, Chrome.Text))
        }
        if (onSettings != null) {
            RoundButton(LucideIcons.Sliders, "Settings", size = Spacing.touch, onClick = onSettings,
                background = Chrome.Chip, border = Chrome.ChipBorder, tint = Chrome.Text)
        }
    }
}

@Composable
private fun StatusChip(label: String, state: ScanState?) {
    Row(
        Modifier.height(40.dp)
            .background(Chrome.Chip, MaterialTheme.shapes.medium)
            .border(1.dp, Chrome.ChipBorder, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state?.gridOutline()?.let { LineSample(it) }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Chrome.Text)
    }
}

/** The outline's pattern in miniature, so the chip never says the state by colour alone. */
@Composable
private fun LineSample(outline: com.bay.sudokusolver.ui.theme.GridOutline) {
    val style = SudokuTheme.colors.outline(outline)
    Canvas(Modifier.width(22.dp).height(8.dp)) {
        val y = size.height / 2
        if (style.pattern == LinePattern.DOUBLE) {
            val line = 1.5.dp.toPx()
            for (dy in listOf(-2.dp.toPx(), 2.dp.toPx())) {
                drawLine(style.color, Offset(0f, y + dy), Offset(size.width, y + dy), line)
            }
        } else {
            val w = 3.dp.toPx()
            drawLine(style.color, Offset(w / 2, y), Offset(size.width - w / 2, y), w,
                cap = style.cap, pathEffect = style.pathEffect(w))
        }
    }
}

/** Docked bottom bar (M3 bottom app bar shape, restyled to Classical). */
@Composable
fun DockedBar(
    content: BarContent,
    flashOn: Boolean?, // null = no flash unit: no button
    onFlash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .background(Chrome.Bar)
            .drawBehind { drawLine(Chrome.BarRule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = 18.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).border(1.dp, Chrome.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(content.icon, contentDescription = null, tint = Chrome.Accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(content.title, style = MaterialTheme.typography.headlineSmall, color = Chrome.Text)
                Text(content.sub, style = MaterialTheme.typography.bodyMedium, color = Chrome.TextMuted)
            }
            if (flashOn != null) {
                RoundButton(
                    if (flashOn) LucideIcons.Zap else LucideIcons.ZapOff,
                    if (flashOn) "Flash on" else "Flash off",
                    size = 52.dp, onClick = onFlash,
                    background = if (flashOn) Chrome.Pressed else Color.Transparent,
                    border = if (flashOn) Chrome.Accent else Chrome.ButtonBorder,
                    tint = if (flashOn) Chrome.Accent else Chrome.Text,
                )
            }
        }
        content.progress?.let { Segments(it) }
        if (content.actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in content.actions) ActionTile(a, Modifier.weight(1f))
            }
        }
    }
}

/** Five reading segments: filled = frames voted; hatched while paused (hold steady). */
@Composable
private fun Segments(p: Progress) {
    val amber = Chrome.Accent
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in 1..p.total) {
            val on = n <= p.filled
            Canvas(
                Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(2.dp)).clipToBounds()
                    .border(1.dp, amber.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            ) {
                if (!on) return@Canvas
                if (!p.paused) {
                    drawRect(amber)
                } else {
                    // 135° hatching, 3 px stripes on 6 px (design)
                    val step = 6.dp.toPx() / 2
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(amber, Offset(x, size.height), Offset(x + size.height, 0f), step / 1.4f)
                        x += step * 2
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(a: BarAction, modifier: Modifier) {
    val accent = a.primary || a.active
    val fg = if (accent) Chrome.Accent else Chrome.Text
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier.height(68.dp)
            .clip(shape)
            .background(if (a.active) Chrome.Pressed else Color.Transparent)
            .border(if (a.primary) 1.5.dp else 1.dp, if (accent) Chrome.Accent else Chrome.ButtonBorder, shape)
            .clickable(role = Role.Button, onClick = a.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(a.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    background: Color,
    border: Color,
    tint: Color,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}
