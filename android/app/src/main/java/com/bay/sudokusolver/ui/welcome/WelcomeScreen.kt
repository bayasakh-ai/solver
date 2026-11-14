package com.bay.sudokusolver.ui.welcome

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.Welcome
import com.bay.sudokusolver.WelcomeEnd
import com.bay.sudokusolver.WelcomePage
import com.bay.sudokusolver.ui.board.BackButton
import com.bay.sudokusolver.ui.board.FlowButton
import com.bay.sudokusolver.ui.board.IconButton
import com.bay.sudokusolver.ui.theme.AnswerDigit
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.LucideIcons
import com.bay.sudokusolver.ui.theme.Ramp
import com.bay.sudokusolver.ui.theme.SudofolioTheme
import com.bay.sudokusolver.ui.theme.SudokuTheme
import com.bay.sudokusolver.ui.theme.SudokuType

/*
 * Welcome: what the app does, how a scan goes, and why it wants the camera, shown once
 * before the scanner (and again from Settings). Which pages exist and what the last
 * button does is decided by Welcome.kt; this file only draws [state].
 *
 * Not in the design files: built from the core flow's pieces (kicker, outlined buttons,
 * option rows) in the Classical style. The main button stays at the same height on every
 * page, low enough for a thumb.
 */

/**
 * [onBack]: the page before, or closing a replay from its first page.
 * [onEnd]: the last page's main button; what it does is [Welcome.end].
 */
@Composable
fun WelcomeScreen(
    state: Welcome,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onNotNow: () -> Unit,
) {
    // Surface blocks touches, so nothing reaches the screen underneath
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !state.isFirst -> BackButton("Previous page", onBack)
                    state.replay -> IconButton(LucideIcons.X, "Close", onBack)
                    else -> Spacer(Modifier.size(48.dp)) // first run, first page: Back leaves the app
                }
                Box(Modifier.weight(1f))
                // the position in words and figures, so it doesn't rest on a row of dots
                Text("${state.index + 1} of ${state.pages.size}".uppercase(), style = SudokuType.Kicker, color = Classical.AccentText)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 16.dp),
            ) {
                when (state.page) {
                    WelcomePage.HELLO -> Hello()
                    WelcomePage.HOW -> How()
                    WelcomePage.CAMERA -> CameraAccess()
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 10.dp)) {
                when (state.end) {
                    null -> FlowButton("Next", onNext, Modifier.fillMaxWidth(), icon = LucideIcons.ArrowRight, iconAfter = true, primary = true)
                    WelcomeEnd.ALLOW_CAMERA -> FlowButton("Allow camera", onEnd, Modifier.fillMaxWidth(), icon = LucideIcons.Camera, primary = true)
                    WelcomeEnd.START -> FlowButton("Start scanning", onEnd, Modifier.fillMaxWidth(), icon = LucideIcons.ScanLine, primary = true)
                    WelcomeEnd.DONE -> FlowButton("Done", onEnd, Modifier.fillMaxWidth(), icon = LucideIcons.Check, primary = true)
                }
                // the second action's row is always there, so the main button doesn't jump
                when {
                    state.end == WelcomeEnd.ALLOW_CAMERA -> QuietButton("Not now", onNotNow)
                    state.canSkip -> QuietButton("Skip", onSkip)
                    else -> Spacer(Modifier.height(QuietHeight))
                }
            }
        }
    }
}

private val QuietHeight = 48.dp

/** Text-only button for the lesser action (Classical's ghost button). */
@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(QuietHeight).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Classical.TextBody2)
    }
}

@Composable
private fun Hello() {
    Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 36.dp), contentAlignment = Alignment.Center) {
        ScanVignette()
    }
    Text("Sudofolio", style = MaterialTheme.typography.displayLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        "Point the camera at a sudoku in a newspaper, a book or on a screen. The answers appear on the page.",
        style = MaterialTheme.typography.bodyLarge, color = Classical.TextBody2,
    )
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Classical.Ink.copy(alpha = 0.14f))
    Text(
        "Would you rather solve it yourself? Move the puzzle onto a board and ask for a hint only when you’re stuck.",
        style = MaterialTheme.typography.bodyMedium, color = Classical.TextSecondary,
    )
}

@Composable
private fun How() {
    Text("How it works", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Step(LucideIcons.ScanLine, "Scan",
        "Hold the phone over the puzzle so the grid fills most of the screen. There’s no button: " +
            "it reads as soon as the picture is sharp, and the answers appear on the page.")
    HorizontalDivider(color = Classical.Ink.copy(alpha = 0.14f))
    Step(LucideIcons.Check, "Check",
        "Tap Play to see what was read, next to your photo. If a digit is wrong, tap it and pick the right one.")
    HorizontalDivider(color = Classical.Ink.copy(alpha = 0.14f))
    Step(LucideIcons.Lightbulb, "Play",
        "See the solution, solve it yourself, or play with hints. A hint points first, then explains, then shows.")
    HorizontalDivider(color = Classical.Ink.copy(alpha = 0.14f))
    Spacer(Modifier.height(14.dp))
    Text(
        "Nothing to scan? Play a puzzle makes a new one at the difficulty you choose.",
        style = MaterialTheme.typography.bodySmall, color = Classical.TextSecondary,
    )
}

@Composable
private fun Step(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(icon, 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
        }
    }
}

/** An icon in a hairline frame: amber as a stroke, never a fill. Decorative: the text beside it says it. */
@Composable
private fun IconTile(icon: ImageVector, size: Int) {
    Box(
        Modifier.size(size.dp).border(1.dp, Classical.Ink.copy(alpha = 0.22f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ramp.Accent600, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun CameraAccess() {
    Spacer(Modifier.height(20.dp))
    IconTile(LucideIcons.Camera, 56)
    Spacer(Modifier.height(24.dp))
    Text("Allow the camera", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        "Sudofolio uses the camera for one thing: reading the puzzle in front of it.",
        style = MaterialTheme.typography.bodyLarge, color = Classical.TextBody2,
    )
    Spacer(Modifier.height(12.dp))
    // True while the manifest asks for the camera only. Rewrite this before the app gets
    // internet access (ads, opt-in upload of corrections): see CLAUDE.md, "Welcome".
    Text(
        "The reading happens on your phone. No picture is saved, and the app has no internet access, " +
            "so nothing can leave it.",
        style = MaterialTheme.typography.bodyLarge, color = Classical.TextBody2,
    )
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Classical.Ink.copy(alpha = 0.14f))
    Text(
        "Without the camera you can still play puzzles made on the phone, and allow it later.",
        style = MaterialTheme.typography.bodyMedium, color = Classical.TextSecondary,
    )
}

/**
 * One 3x3 box inside the viewfinder corners: printed digits in ink sans, the answers in
 * the answer colour and face, as they look on the camera. A picture of the promise above
 * it, so it is hidden from TalkBack (the sentence says the same).
 */
@Composable
private fun ScanVignette() {
    val colors = SudokuTheme.colors
    val printed = intArrayOf(5, 0, 3, 0, 7, 0, 6, 0, 1)
    val answers = intArrayOf(0, 2, 0, 8, 0, 4, 0, 9, 0)
    Box(Modifier.size(196.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            val arm = 30.dp.toPx()
            for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                val x = if (right) size.width - w / 2 else w / 2
                val y = if (bottom) size.height - w / 2 else w / 2
                val inX = if (right) x - arm else x + arm
                val inY = if (bottom) y - arm else y + arm
                drawLine(Classical.AccentStroke, Offset(x, y), Offset(inX, y), w, cap = StrokeCap.Round)
                drawLine(Classical.AccentStroke, Offset(x, y), Offset(x, inY), w, cap = StrokeCap.Round)
            }
        }
        Box(Modifier.size(156.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(colors.gridCell)
                val step = size.width / 3
                for (k in 1..2) {
                    drawLine(colors.gridThinLine, Offset(k * step, 0f), Offset(k * step, size.height), 1.dp.toPx())
                    drawLine(colors.gridThinLine, Offset(0f, k * step), Offset(size.width, k * step), 1.dp.toPx())
                }
                val b = 2.dp.toPx()
                drawRect(colors.gridBoxLine, Offset(b / 2, b / 2), Size(size.width - b, size.height - b), style = Stroke(b))
            }
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until 3) Row(Modifier.weight(1f)) {
                    for (c in 0 until 3) Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        val i = r * 3 + c
                        if (printed[i] != 0) {
                            Text(printed[i].toString(), color = colors.printedDigit,
                                style = SudokuType.PrintedDigit.copy(fontSize = 28.sp, lineHeight = 28.sp))
                        } else {
                            AnswerDigit(answers[i], AnswerStep.DEEP,
                                style = SudokuType.AnswerDigit.copy(fontSize = 30.sp, lineHeight = 30.sp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The scanner's place when the camera isn't allowed: say so, offer to allow it, and keep
 * what works without it (a kept game, generated puzzles). [onContinue] null: no kept game.
 */
@Composable
fun NoCameraScreen(onAllow: () -> Unit, onContinue: (() -> Unit)?, onPlayPuzzle: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            IconTile(LucideIcons.Camera, 56)
            Spacer(Modifier.height(24.dp))
            Text("The camera is off", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Sudofolio needs the camera to scan a puzzle. Until then you can play one made on the phone.",
                style = MaterialTheme.typography.bodyLarge, color = Classical.TextBody2,
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // one main action: the game in progress if there is one, otherwise the camera
                if (onContinue != null) FlowButton("Continue", onContinue, Modifier.fillMaxWidth(), icon = LucideIcons.Play, primary = true)
                FlowButton("Allow camera", onAllow, Modifier.fillMaxWidth(), icon = LucideIcons.Camera, primary = onContinue == null)
                FlowButton("Play a puzzle", onPlayPuzzle, Modifier.fillMaxWidth(), icon = LucideIcons.Grid)
            }
        }
    }
}

@Preview(name = "Welcome · 1 hello", widthDp = 390, heightDp = 844)
@Composable
private fun PWelcomeHello() = SudofolioTheme {
    WelcomeScreen(Welcome(cameraAllowed = false), {}, {}, {}, {}, {})
}

@Preview(name = "Welcome · 2 how it works", widthDp = 390, heightDp = 844)
@Composable
private fun PWelcomeHow() = SudofolioTheme {
    WelcomeScreen(Welcome(cameraAllowed = false).next(), {}, {}, {}, {}, {})
}

@Preview(name = "Welcome · 3 camera", widthDp = 390, heightDp = 844)
@Composable
private fun PWelcomeCamera() = SudofolioTheme {
    WelcomeScreen(Welcome(cameraAllowed = false).skip(), {}, {}, {}, {}, {})
}

@Preview(name = "Welcome · replay, last page (small phone)", widthDp = 320, heightDp = 568)
@Composable
private fun PWelcomeReplay() = SudofolioTheme {
    WelcomeScreen(Welcome(cameraAllowed = true, replay = true).next(), {}, {}, {}, {}, {})
}

@Preview(name = "No camera", widthDp = 390, heightDp = 844)
@Composable
private fun PNoCamera() = SudofolioTheme {
    NoCameraScreen(onAllow = {}, onContinue = null, onPlayPuzzle = {})
}

@Preview(name = "No camera · game in progress", widthDp = 390, heightDp = 844)
@Composable
private fun PNoCameraContinue() = SudofolioTheme {
    NoCameraScreen(onAllow = {}, onContinue = {}, onPlayPuzzle = {})
}
