package com.bay.sudokusolver.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.bay.sudokusolver.AnswerStep
import com.bay.sudokusolver.Capture
import com.bay.sudokusolver.SolveResult
import com.bay.sudokusolver.FrameResult
import com.bay.sudokusolver.ScanState
import com.bay.sudokusolver.Solver
import com.bay.sudokusolver.ui.theme.SudofolioTheme

/*
 * Android Studio previews of the overlay on a fake frame (no camera): one per
 * outline state, plus a half-shadowed grid where cells switch step.
 * Same puzzle as the design files (puzzle3).
 */

private val PUZZLE = Solver.parse(
    "005031040001000008794600000002000003900000005300000800000003517500000600070820900")
private val SOLUTION = Solver.parse(
    "685731249231549768794682351462958173918376425357214896829463517543197682176825934")

/** Slightly tilted grid in a 480x640 portrait frame. */
private val CORNERS = floatArrayOf(52f, 150f, 432f, 138f, 440f, 520f, 44f, 530f)

/** The same grid with the phone turned 30°, ordered the way GridDetector.orderCorners does. */
private val CORNERS_TURNED = floatArrayOf(178f, 98f, 472f, 268f, 302f, 562f, 8f, 392f)

/** A capture of PUZZLE as the vote would hand it over (confident reads, nothing fixed). */
private fun capture(solved: Boolean) = Capture.from(PUZZLE,
    Array(81) { i -> if (PUZZLE[i] == 0) null else FloatArray(9) { k -> if (k == PUZZLE[i] - 1) 0.96f else 0.005f } },
    SolveResult(PUZZLE, if (solved) SOLUTION else null, emptyList()), inkUnsure = null, inkIgnored = 0)

private fun fake(state: ScanState, solved: Boolean, steps: Array<AnswerStep>? = null,
                 unsure: BooleanArray? = null, corners: FloatArray = CORNERS,
                 captured: Boolean = false) = FrameResult(
    corners = corners, frameWidth = 480, frameHeight = 640, warped = null, sharpness = 140.0,
    digits = 26, processMs = 5, fps = 30.0, state = state,
    puzzle = if (solved) PUZZLE else null, solution = if (solved) SOLUTION else null,
    unsure = unsure, steps = steps,
    capture = if (captured) capture(solved) else null,
)

@Composable
private fun Stage(background: Brush, result: FrameResult?) {
    SudofolioTheme {
        Box(Modifier.fillMaxSize().background(background)) {
            GridOverlay(result, Modifier.fillMaxSize())
        }
    }
}

private val paper = Brush.linearGradient(listOf(Color(0xFFE8E2D6), Color(0xFFCFC8BB)))
private val screen = Brush.linearGradient(listOf(Color(0xFF1C1D21), Color(0xFF0F1012)))
private val shadow = Brush.horizontalGradient(
    0f to Color(0xFFE8E2D6), 0.5f to Color(0xFFE0DACE), 0.56f to Color(0xFF4A4740), 1f to Color(0xFF3A3833))

@Preview(name = "Searching", widthDp = 360, heightDp = 780)
@Composable private fun PSearching() = Stage(paper, null)

@Preview(name = "Reading · dashed", widthDp = 360, heightDp = 780)
@Composable private fun PReading() = Stage(paper, fake(ScanState.Reading(3, 5), solved = false,
    unsure = BooleanArray(81) { it == 4 || it == 40 }))

@Preview(name = "Hold steady · dotted", widthDp = 360, heightDp = 780)
@Composable private fun PHold() = Stage(paper, fake(ScanState.HoldSteady(3, 5), solved = false))

@Preview(name = "Solved on paper", widthDp = 360, heightDp = 780)
@Composable private fun PSolvedPaper() = Stage(paper, fake(ScanState.Solved(0), solved = true,
    steps = Array(81) { AnswerStep.DEEP }))

@Preview(name = "Solved on a dark screen", widthDp = 360, heightDp = 780)
@Composable private fun PSolvedScreen() = Stage(screen, fake(ScanState.Solved(0), solved = true,
    steps = Array(81) { AnswerStep.BRIGHT }))

@Preview(name = "Solved, hard shadow on the right", widthDp = 360, heightDp = 780)
@Composable private fun PSolvedShadow() = Stage(shadow, fake(ScanState.Solved(0), solved = true,
    steps = Array(81) { if (it % 9 >= 5) AnswerStep.BRIGHT else AnswerStep.DEEP }))

@Preview(name = "Needs review · double", widthDp = 360, heightDp = 780)
@Composable private fun PReview() = Stage(paper, fake(
    ScanState.NeedsReview(ScanState.Reason.NO_UNIQUE_SOLUTION), solved = false))

/* Full screens: overlay + top chrome + docked bar, like the design's phone frames. */

@Composable
private fun FullScreen(background: Brush, result: FrameResult?, frozen: Boolean = false, flash: Boolean? = false) {
    SudofolioTheme {
        Box(Modifier.fillMaxSize().background(background)) {
            GridOverlay(result, Modifier.fillMaxSize())
            val content = barContent(result, frozen, error = null, onFreeze = {}, onReset = {}, onCheck = {})
            com.bay.sudokusolver.ui.theme.CameraChrome {
                TopChrome(content, result, showDebug = false, thumbnail = null, onSettings = {},
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                DockedBar(content, flashOn = flash, onFlash = {},
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
    }
}

@Preview(name = "Screen · Searching", widthDp = 360, heightDp = 780)
@Composable private fun SSearching() = FullScreen(paper, null)

@Preview(name = "Screen · Reading 3/5", widthDp = 360, heightDp = 780)
@Composable private fun SReading() = FullScreen(paper, fake(ScanState.Reading(3, 5), solved = false))

@Preview(name = "Screen · Hold steady", widthDp = 360, heightDp = 780)
@Composable private fun SHold() = FullScreen(paper, fake(ScanState.HoldSteady(3, 5), solved = false))

@Preview(name = "Screen · Solved", widthDp = 360, heightDp = 780)
@Composable private fun SSolved() = FullScreen(paper, fake(ScanState.Solved(1), solved = true,
    steps = Array(81) { AnswerStep.DEEP }, captured = true))

@Preview(name = "Screen · Solved, frozen, flash on, dark screen", widthDp = 360, heightDp = 780)
@Composable private fun SFrozen() = FullScreen(screen, fake(ScanState.Solved(0), solved = true,
    steps = Array(81) { AnswerStep.BRIGHT }), frozen = true, flash = true)

@Preview(name = "Screen · Needs review", widthDp = 360, heightDp = 780)
@Composable private fun SReview() = FullScreen(paper, fake(
    ScanState.NeedsReview(ScanState.Reason.NO_UNIQUE_SOLUTION), solved = false, captured = true))

@Preview(name = "Screen · Needs review, too few digits", widthDp = 360, heightDp = 780)
@Composable private fun SReviewFew() = FullScreen(paper, fake(
    ScanState.NeedsReview(ScanState.Reason.TOO_FEW_DIGITS), solved = false, captured = true))

@Preview(name = "Solved, phone turned 30° (answers follow the cells)", widthDp = 360, heightDp = 780)
@Composable private fun PTurned() = Stage(paper, fake(ScanState.Solved(0), solved = true,
    steps = Array(81) { AnswerStep.DEEP }, unsure = BooleanArray(81) { it == 4 }, corners = CORNERS_TURNED))
