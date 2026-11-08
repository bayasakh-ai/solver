package com.bay.sudokusolver.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Classical radii: 2 / 4 / 7, plus 12 for the top of bottom sheets. */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),  // --radius-sm
    small = RoundedCornerShape(4.dp),       // --radius-md: buttons, keys, cells
    medium = RoundedCornerShape(7.dp),      // --radius-lg: chips, action tiles, cards
    large = RoundedCornerShape(12.dp),      // bottom sheets (top corners)
    extraLarge = RoundedCornerShape(12.dp),
)

/** Spacing scale from styles.css (density 1.15x is baked in). */
object Spacing {
    val s1 = 4.6.dp
    val s2 = 9.2.dp
    val s3 = 13.8.dp
    val s4 = 18.4.dp
    val s6 = 27.6.dp
    val s8 = 36.8.dp

    /** Screen-edge gutter used by the camera chrome and sheets. */
    val gutter = 16.dp
    /** Minimum touch target. */
    val touch = 48.dp
}
