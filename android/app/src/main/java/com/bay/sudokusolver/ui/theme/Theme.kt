package com.bay.sudokusolver.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.AnswerPalette
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.SignalColors

/*
 * Material 3 with its roles mapped to Classical. Components are restyled per
 * screen (outlined primary buttons, 4 dp corners) rather than used stock.
 *
 * There is no app-wide dark theme: the design defines the light ground for
 * Settings and sheets, and the camera chrome is always dark ([CameraChrome]).
 * Dynamic colour is off; it would replace the brand and the tested palettes.
 */

private val LightColors = lightColorScheme(
    primary = Classical.AccentText,          // text-safe accent (6.0:1)
    onPrimary = Classical.Ground,
    primaryContainer = Ramp.Accent100,
    onPrimaryContainer = Ramp.Accent800,
    secondary = Classical.AccentStroke,      // strokes, rings, radio dots (3.0:1, not for text)
    onSecondary = Classical.Ground,
    secondaryContainer = Ramp.Accent200,
    onSecondaryContainer = Ramp.Accent900,
    tertiary = Ramp.Accent600,               // pressed state, one step past the base
    onTertiary = Classical.Ground,
    background = Classical.Ground,
    onBackground = Classical.Ink,
    surface = Classical.Ground,
    onSurface = Classical.Ink,
    surfaceVariant = Classical.Surface,
    onSurfaceVariant = Classical.TextSecondary,
    surfaceContainerLowest = Ramp.Neutral100,
    surfaceContainerLow = Classical.Ground,
    surfaceContainer = Classical.Ground,
    surfaceContainerHigh = Classical.Surface,
    surfaceContainerHighest = Ramp.Neutral200,
    outline = Classical.Divider,
    outlineVariant = Classical.Ink.copy(alpha = 0.14f),
    error = rgb(SignalColors.WARNING_PAPER),
    onError = Classical.Ground,
    scrim = Color(0xFF0E0D0C),
    inverseSurface = Chrome.Bar,
    inverseOnSurface = Chrome.Text,
    inversePrimary = Chrome.Accent,
)

private val ChromeColors = darkColorScheme(
    primary = Chrome.Accent,
    onPrimary = Color(0xFF171513),
    primaryContainer = Chrome.Pressed,
    onPrimaryContainer = Chrome.Accent,
    secondary = Chrome.Accent,
    onSecondary = Color(0xFF171513),
    background = Chrome.Bar,
    onBackground = Chrome.Text,
    surface = Chrome.Bar,
    onSurface = Chrome.Text,
    surfaceVariant = Chrome.Chip,
    onSurfaceVariant = Chrome.TextMuted,
    outline = Chrome.ButtonBorder,
    outlineVariant = Chrome.ChipBorder,
    error = rgb(SignalColors.WARNING_CAMERA),
    onError = Color(0xFF171513),
    scrim = Color(0xFF0E0D0C),
)

@Composable
fun SudokuLensTheme(
    palette: AnswerPalette = AnswerPalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colors = remember(palette) { SudokuColors(palette) }
    CompositionLocalProvider(LocalSudokuColors provides colors) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** Dark chrome for anything drawn over the camera image (chip, docked bottom bar). */
@Composable
fun CameraChrome(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChromeColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** Access to the colours outside Material roles: `SudokuTheme.colors.answer(step)`. */
object SudokuTheme {
    val colors: SudokuColors
        @Composable @ReadOnlyComposable get() = LocalSudokuColors.current
}

/** Halo width for answer digits: thin, scaled by the palette (1.5x in High contrast). */
fun SudokuColors.answerHaloWidth(base: Dp = 2.5.dp): Dp = base * palette.haloScale

/**
 * One answer digit: the step's colour over a contrasting halo. The halo is the
 * same glyph stroked underneath, which stays crisp where a blur shadow would smear.
 */
@Composable
fun AnswerDigit(
    digit: Int,
    step: AnswerStep,
    modifier: Modifier = Modifier,
    style: TextStyle = SudokuType.AnswerDigit,
) {
    val colors = SudokuTheme.colors
    val haloPx = with(LocalDensity.current) { colors.answerHaloWidth().toPx() }
    val text = digit.toString()
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = style.copy(
                color = colors.answerHalo(step),
                drawStyle = Stroke(width = haloPx * 2, join = StrokeJoin.Round),
            ),
        )
        Text(text, style = style.copy(color = colors.answer(step)))
    }
}
