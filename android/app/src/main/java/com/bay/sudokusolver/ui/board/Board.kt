package com.bay.sudokusolver.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/*
 * Pieces shared by the core-flow screens (design/screens/flow): the 9x9 grid, the
 * header with back + step kicker, and the outlined buttons (Classical: never filled).
 */

/**
 * A square 9x9 grid: paper ground, [background] per cell, thin cell lines, 2 dp box
 * lines and border. [cell] draws each cell's content; [overlay] draws on top
 * (hint marks), with the cell size in px.
 */
@Composable
fun SudokuGrid(
    modifier: Modifier = Modifier,
    background: (Int) -> Color = { Color.Transparent },
    boxLine: Dp = 2.dp,
    overlay: (DrawScope.(cell: Float) -> Unit)? = null,
    cell: @Composable BoxScope.(Int) -> Unit,
) {
    val colors = SudokuTheme.colors
    Box(modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.gridCell)
            val step = size.width / 9
            for (i in 0 until 81) {
                val bg = background(i)
                if (bg != Color.Transparent) drawRect(bg, Offset(i % 9 * step, i / 9 * step), Size(step, step))
            }
            for (k in 1 until 9) {
                if (k % 3 == 0) continue
                drawLine(colors.gridThinLine, Offset(k * step, 0f), Offset(k * step, size.height), 1.dp.toPx())
                drawLine(colors.gridThinLine, Offset(0f, k * step), Offset(size.width, k * step), 1.dp.toPx())
            }
            val w = boxLine.toPx()
            for (k in 1..2) {
                drawLine(colors.gridBoxLine, Offset(k * 3 * step, 0f), Offset(k * 3 * step, size.height), w)
                drawLine(colors.gridBoxLine, Offset(0f, k * 3 * step), Offset(size.width, k * 3 * step), w)
            }
            drawRect(colors.gridBoxLine, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
        }
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) Row(Modifier.weight(1f)) {
                for (c in 0 until 9) Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    cell(r * 9 + c)
                }
            }
        }
        if (overlay != null) Canvas(Modifier.fillMaxSize()) { overlay(size.width / 9) }
    }
}

/** Back button, step kicker on the right, title and line (Check, Choose). */
@Composable
fun FlowHeader(kicker: String, title: String, subtitle: String, backLabel: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(backLabel, onBack, Modifier.offset(x = (-12).dp))
            Box(Modifier.weight(1f))
            Text(kicker.uppercase(), style = SudokuType.Kicker, color = Classical.AccentText)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Classical.TextSecondary)
    }
}

@Composable
fun BackButton(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(LucideIcons.ArrowLeft, label, onBack, modifier)

@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Classical.Ink) {
    Box(
        modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Outlined button. [primary]: amber border, semibold label, amber icon. */
@Composable
fun FlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconAfter: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.small
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier.height(50.dp).clip(shape)
            .border(1.dp, (if (primary) Classical.AccentStroke else Classical.Ink.copy(alpha = 0.28f)).copy(alpha = alpha), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val tint = (if (primary) Ramp.Accent600 else Classical.Ink).copy(alpha = alpha)
        if (icon != null && !iconAfter) Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal),
            color = Classical.Ink.copy(alpha = alpha))
        if (icon != null && iconAfter) Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** "Row 5, column 4, 3, printed" style labels for TalkBack. */
fun cellLabel(i: Int, what: String) = "Row ${i / 9 + 1}, column ${i % 9 + 1}, $what"
