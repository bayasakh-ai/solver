package com.bay.sudokusolver.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.AnswerPalette
import com.bay.sudokusolver.AnswerStep

/*
 * Visual check of the theme against design/screens (no app screens yet).
 * Open in Android Studio's preview pane; compare with design/screenshots.
 */

@Composable
private fun Swatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(40.dp).background(color, MaterialTheme.shapes.small)
                .border(1.dp, Classical.Divider, MaterialTheme.shapes.small)
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnswerTile(step: AnswerStep, background: Color, print: Color) {
    Row(
        Modifier.width(140.dp).height(72.dp).background(background, MaterialTheme.shapes.small),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("4", style = SudokuType.PrintedDigit.copy(fontSize = SudokuType.AnswerDigit.fontSize, color = print))
        AnswerDigit(7, step)
    }
}

@Composable
private fun OutlineSample(state: GridOutline, label: String) {
    val style = SudokuTheme.colors.outline(state)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.width(48.dp).height(12.dp)) {
            val y = size.height / 2
            val w = style.width.toPx()
            if (style.pattern == LinePattern.DOUBLE) {
                val line = w / 3
                for (dy in listOf(-line, line)) drawLine(style.color, Offset(0f, y + dy), Offset(size.width, y + dy), line)
            } else {
                drawLine(style.color, Offset(0f, y), Offset(size.width, y), w,
                    cap = style.cap, pathEffect = style.pathEffect(w))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Chrome.Text)
    }
}

@Composable
fun ThemeSheet(palette: AnswerPalette) {
    SudokuLensTheme(palette) {
        val c = SudokuTheme.colors
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text("ANSWER COLOR", style = SudokuType.Kicker, color = MaterialTheme.colorScheme.primary)
            Text(palette.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnswerTile(AnswerStep.DEEP, Color(0xFFE8E2D6), Color(0xFF222222))
                AnswerTile(AnswerStep.BRIGHT, Color(0xFF161616), Color(0xFFD7D8DC))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Answer color", style = MaterialTheme.typography.titleLarge)
            Text("One hue in two steps: deep on paper, bright on dark screens. The step is chosen for each cell.",
                style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
            Text("Shows the flattened grid and frame stats over the camera.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Swatch(Classical.Ground, "bg"); Swatch(Classical.Surface, "surf")
                Swatch(Classical.Ink, "ink"); Swatch(Classical.AccentStroke, "acc")
                Swatch(Classical.AccentText, "a700"); Swatch(c.warningPaper, "warn")
                Swatch(c.unsureFill, "unsure")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Check-grid marks: unsure ring, corrected underline.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(c.unsureFill).border(2.dp, c.unsureRing, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("8", style = SudokuType.PrintedDigit, color = c.printedDigit)
                }
                Box(Modifier.size(36.dp).background(c.gridCell), contentAlignment = Alignment.Center) {
                    Text("5", style = SudokuType.CorrectedDigit.copy(textDecoration = TextDecoration.Underline),
                        color = c.answer(AnswerStep.DEEP))
                }
                Text("Unsure · ring    Corrected · underline", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ChromeSheet() {
    SudokuLensTheme {
        CameraChrome {
            Column(
                Modifier.fillMaxWidth().background(Chrome.Bar).padding(Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text("HINT 1 OF 4", style = SudokuType.Kicker, color = Chrome.Accent)
                Text("Solved", style = MaterialTheme.typography.headlineSmall, color = Chrome.Text)
                Text("51 answers drawn on the puzzle", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlineSample(GridOutline.READING, "Reading · dashed")
                OutlineSample(GridOutline.HOLD_STEADY, "Hold steady · dotted")
                OutlineSample(GridOutline.SOLVED, "Solved · solid")
                OutlineSample(GridOutline.NEEDS_REVIEW, "Needs review · double")
                Text("26 digits · sharpness 142 · 30 fps · 5 ms", style = SudokuType.Stats, color = Chrome.Text)
            }
        }
    }
}

@Preview(name = "Theme · Ink blue", widthDp = 360)
@Composable
private fun PreviewInkBlue() = ThemeSheet(AnswerPalette.INK_BLUE)

@Preview(name = "Theme · High contrast", widthDp = 360)
@Composable
private fun PreviewHighContrast() = ThemeSheet(AnswerPalette.HIGH_CONTRAST)

@Preview(name = "Camera chrome", widthDp = 360)
@Composable
private fun PreviewChrome() = ChromeSheet()
