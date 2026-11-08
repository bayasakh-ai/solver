package com.bay.sudokusolver.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bay.sudokusolver.R

/*
 * Classical type: Cormorant Garamond headings over Lora body text. No bold:
 * interface headings stop at semibold (600) and display sizes use the regular cut.
 *
 * The fonts are the unmodified variable TTFs from github.com/google/fonts (OFL; the
 * licences ship in assets/licenses/). Lora's licence reserves its name, so we don't
 * cut static instances. Weights come from the wght axis, which Android applies on
 * API 26+; on API 24-25 the 600 weight renders as 400 (answers there still differ
 * from print by colour, halo and serif shape).
 */

@OptIn(ExperimentalTextApi::class) // FontVariation.Settings is still marked experimental
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Cormorant = FontFamily(
    variable(R.font.cormorant_garamond, 400),
    variable(R.font.cormorant_garamond, 600),
)

val Lora = FontFamily(
    variable(R.font.lora, 400),
    variable(R.font.lora, 600),
)

private val Regular = FontWeight.Normal
private val Semibold = FontWeight.SemiBold

private fun heading(size: Int, lineHeight: Double, weight: FontWeight = Semibold) = TextStyle(
    fontFamily = Cormorant, fontWeight = weight,
    fontSize = size.sp, lineHeight = (size * lineHeight).sp, letterSpacing = (-0.015).em,
)

private fun body(size: Int, lineHeight: Double, weight: FontWeight = Regular) = TextStyle(
    fontFamily = Lora, fontWeight = weight,
    fontSize = size.sp, lineHeight = (size * lineHeight).sp,
)

/** Sizes taken from the v2 and Hints screens (360 dp wide phone frames). */
val Typography = Typography(
    displayLarge = heading(42, 1.12, Regular),
    displayMedium = heading(34, 1.05, Regular),   // notes headline
    displaySmall = heading(32, 1.05, Regular),
    headlineLarge = heading(32, 1.1),
    headlineMedium = heading(30, 1.0),             // screen title ("Settings")
    headlineSmall = heading(25, 1.1),              // bottom-bar and hint-sheet title
    titleLarge = heading(22, 1.2),                 // section heading ("Answer color")
    titleMedium = body(16, 1.4),                   // list item title
    titleSmall = body(14, 1.4, Semibold),
    bodyLarge = body(15, 1.4),                     // hint body
    bodyMedium = body(14, 1.4),                    // bar subtitle, descriptions
    bodySmall = body(13, 1.45),                    // captions, helper text
    labelLarge = body(15, 1.3),                    // button labels
    labelMedium = body(13, 1.3),                   // small button labels, legend
    labelSmall = body(11, 1.3).copy(letterSpacing = 0.1.em), // kicker; caller uppercases
)

/** Styles outside the Material roles. Numbers are tabular wherever they are figures. */
object SudokuType {
    /** Answer digits: semibold serif, so they differ from print by shape and weight too. */
    val AnswerDigit = TextStyle(
        fontFamily = Lora, fontWeight = Semibold, fontSize = 34.sp, lineHeight = 34.sp,
        fontFeatureSettings = "tnum",
    )
    /** Printed digits in the digital grid: regular sans, like most puzzle print. */
    val PrintedDigit = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = Regular, fontSize = 20.sp, lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )
    /** Digits the user corrected in the Check grid: answer face, underlined by the screen. */
    val CorrectedDigit = AnswerDigit.copy(fontSize = 20.sp, lineHeight = 20.sp)
    /** Digit picker keys. */
    val PickerDigit = TextStyle(
        fontFamily = Lora, fontWeight = Regular, fontSize = 24.sp, lineHeight = 24.sp,
        fontFeatureSettings = "tnum",
    )
    /** Uppercase label over a title ("HINT 1 OF 4"). Uppercase the text when using it. */
    val Kicker = body(12, 1.3).copy(letterSpacing = 0.1.em, fontFeatureSettings = "tnum")
    /** Debug stats chip. */
    val Stats = body(12, 1.3).copy(fontFeatureSettings = "tnum")
}
