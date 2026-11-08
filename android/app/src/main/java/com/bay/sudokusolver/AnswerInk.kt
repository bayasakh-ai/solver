package com.bay.sudokusolver

import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Answer-digit colours and the rules that pick them. Pure Kotlin (no Android or
 * Compose imports) so it moves to the shared module for iOS unchanged.
 *
 * Colours are 0xRRGGBB ints. The Compose theme (ui/theme) wraps these same values,
 * so the numbers AnswerInkTest checks are the numbers the app draws.
 *
 * Design rule (design/screens/Sudoku Lens v2.dc.html, "Answer color"): one hue in
 * two fixed steps. DEEP on paper (with a light halo), BRIGHT on dark screens (with a
 * dark halo). The step is picked per cell from the background behind it.
 */

/** The two lightness steps of an answer palette. */
enum class AnswerStep {
    DEEP, BRIGHT;

    fun other() = if (this == DEEP) BRIGHT else DEEP
}

/**
 * Answer palettes offered in Settings. Each entry must pass AnswerInkTest: enough
 * contrast on its background, and distinct from print and from every warning colour
 * under normal, protan, deutan and tritan vision.
 *
 * Measured 2026-11-08 (OKLab distance after Machado 2009 simulation, severity 1):
 *  - INK_BLUE: worst case vs warnings 0.169, vs print 0.088 (bright step, protan).
 *  - Green (#17703A / #6FE39A) was rejected: 0.023 from the amber hint stroke and
 *    0.037 from coral under protanopia, i.e. the same colour.
 *  - Magenta (#A3195B / #FF9CCB) was rejected: grey to deuteranopes (chroma 0.007
 *    vs black print) and 0.036 from coral under tritanopia.
 *  - Gold was dropped: it IS the amber accent (distance 0).
 *  - HIGH_CONTRAST: the bright step can't go lighter than #8CC8FF without drifting
 *    into light-grey screen print (#C4E4FF scored 0.05), so only the deep step and
 *    the halo get stronger.
 */
enum class AnswerPalette(val deep: Int, val bright: Int, val haloScale: Float) {
    INK_BLUE(deep = 0x1D4FB0, bright = 0x8CC8FF, haloScale = 1.0f),        // 7.5:1 on white, 10.2:1 on #161616
    HIGH_CONTRAST(deep = 0x0517A2, bright = 0x8CC8FF, haloScale = 1.5f);   // 12.7:1 on white

    fun color(step: AnswerStep): Int = if (step == AnswerStep.DEEP) deep else bright

    /** Halo behind the digit: light around the deep step, dark around the bright one. */
    fun halo(step: AnswerStep): Int = if (step == AnswerStep.DEEP) 0xFFFFFF else 0x000000

    /**
     * Contrast of the non-default step / contrast of [default], against a background
     * of relative luminance [backgroundLuminance] (0 = black, 1 = white).
     */
    fun advantage(backgroundLuminance: Double, default: AnswerStep): Double {
        val cDeep = ColorMath.contrast(ColorMath.luminance(deep), backgroundLuminance)
        val cBright = ColorMath.contrast(ColorMath.luminance(bright), backgroundLuminance)
        return if (default == AnswerStep.DEEP) cBright / cDeep else cDeep / cBright
    }

    /**
     * Step for one cell. The grid decides the default ([screen] = the dark-mode vote
     * in camera polarity: dark screen -> BRIGHT, paper -> DEEP); the cell switches
     * only when the other step is at least [SWITCH] times better, i.e. a real shadow
     * (Ink blue on paper: Y <= 109). Grey newsprint keeps one shade: measured on
     * photos/puzzle4 (paper Y 113-147), a plain "higher contrast wins" rule split it
     * 33 pale / 22 deep while both steps were only 1.5-2.8:1 there.
     */
    fun stepFor(backgroundLuminance: Double, screen: Boolean = false): AnswerStep {
        val default = defaultStep(screen)
        return if (advantage(backgroundLuminance, default) >= SWITCH) default.other() else default
    }

    companion object {
        val DEFAULT = INK_BLUE
        const val SWITCH = 2.0

        fun defaultStep(screen: Boolean) = if (screen) AnswerStep.BRIGHT else AnswerStep.DEEP
    }
}

/**
 * Signal colours from the design that answers must never be confused with.
 * PAPER_* sit on light backgrounds, CAMERA_* on the dark chrome or dark screens.
 */
object SignalColors {
    // Typical printed digits the answers sit next to.
    const val PRINT_ON_PAPER = 0x201F1D
    const val PRINT_ON_DARK_SCREEN = 0xD7D8DC

    // Warnings: coral, paired with the double outline (never colour alone).
    const val WARNING_PAPER = 0xC2412D
    const val WARNING_CAMERA = 0xFF9B85

    // Amber accent: reading/hold-steady outlines, buttons, hint reasoning strokes.
    const val AMBER_CAMERA = 0xE1AD66
    const val HINT_STROKE_PAPER = 0x7D5411
    const val HINT_STROKE_DARK = 0xFACB8D

    // Unsure cell: amber ring (+ light fill in the Check grid).
    const val UNSURE_RING = 0xA06F24
    const val UNSURE_FILL = 0xFFE3BF

    // Reference backgrounds for the contrast claims in the design notes.
    const val WHITE_PAPER = 0xFFFFFF
    const val DARK_SCREEN = 0x161616

    val onPaper = listOf(WARNING_PAPER, HINT_STROKE_PAPER, UNSURE_RING)
    val onDark = listOf(WARNING_CAMERA, AMBER_CAMERA, HINT_STROKE_DARK)
}

/**
 * Background behind each cell: the median luma of the cell's interior on the
 * flattened 450x450 grid, in CAMERA polarity (what the user sees, not the
 * dark-on-light version the reader works on). Mirrors grid.cell_backgrounds;
 * export_background_cases.py + BackgroundParityTest check they agree exactly.
 *
 * Median, not mean: printed digits and grid-line bleed are a minority of an
 * empty cell's pixels, so the median is the paper (or screen) itself.
 */
object CellBackground {
    const val MARGIN = 5 // same cell interior as Polarity.cellVotes / grid.cell_votes

    /**
     * [pixels] = flattened grid row-major, [size] x [size]. [inverted] = these pixels
     * are 255 - camera (the grid was found in the inverted image, or flipped for
     * dark mode); the result is mapped back to camera values.
     */
    fun medians(pixels: ByteArray, size: Int, inverted: Boolean): IntArray {
        val step = size / 9
        val hist = IntArray(256)
        return IntArray(81) { i ->
            val r = i / 9
            val c = i % 9
            hist.fill(0)
            var n = 0
            for (y in r * step + MARGIN until (r + 1) * step - MARGIN) {
                val row = y * size
                for (x in c * step + MARGIN until (c + 1) * step - MARGIN) {
                    hist[pixels[row + x].toInt() and 0xFF]++
                    n++
                }
            }
            // numpy percentile 50, method="lower": sorted[(n - 1) / 2]
            val target = (n - 1) / 2
            var seen = 0
            var v = 0
            while (true) {
                seen += hist[v]
                if (seen > target) break
                v++
            }
            if (inverted) 255 - v else v
        }
    }
}

/**
 * Per-cell answer step for the live overlay, stable from frame to frame:
 *  - each cell's background is smoothed (exponential average, [SMOOTHING] of the
 *    new frame), so sensor noise and auto-exposure steps don't jump it;
 *  - the grid's default step comes from the dark-mode vote ([AnswerPalette.stepFor]);
 *    a cell leaves it when the other step is [AnswerPalette.SWITCH] (2x) better and
 *    only returns once that advantage drops below 2x / [HYSTERESIS] (1.15x), so a
 *    cell at a shadow's edge doesn't flicker. Ink blue on paper: to bright at
 *    Y <= 109, back to deep at Y >= 114; on a screen: to deep at Y >= 162, back
 *    at Y <= 155.
 * A palette change or a paper/screen flip re-chooses every cell from scratch.
 * Analysis thread only. [reset] when the grid is lost.
 */
class AnswerSteps {
    private var smoothed: FloatArray? = null
    private var steps: Array<AnswerStep>? = null
    private var lastPalette: AnswerPalette? = null
    private var lastScreen: Boolean? = null

    fun reset() {
        smoothed = null
        steps = null
        lastPalette = null
        lastScreen = null
    }

    fun update(backgrounds: IntArray, palette: AnswerPalette, screen: Boolean = false): Array<AnswerStep> {
        require(backgrounds.size == 81)
        val s = smoothed?.also { prev ->
            for (i in 0 until 81) prev[i] += SMOOTHING * (backgrounds[i] - prev[i])
        } ?: FloatArray(81) { backgrounds[it].toFloat() }
        smoothed = s

        val prev = steps.takeIf { palette == lastPalette && screen == lastScreen }
        lastPalette = palette
        lastScreen = screen
        val default = AnswerPalette.defaultStep(screen)
        val next = Array(81) { i ->
            val bg = ColorMath.luminanceFromLuma(s[i].roundToInt())
            val current = prev?.get(i) ?: return@Array palette.stepFor(bg, screen)
            val adv = palette.advantage(bg, default)
            when {
                current == default && adv >= AnswerPalette.SWITCH -> default.other()
                current != default && adv < AnswerPalette.SWITCH / HYSTERESIS -> default
                else -> current
            }
        }
        steps = next
        return next
    }

    companion object {
        const val SMOOTHING = 0.3f
        const val HYSTERESIS = 1.15
    }
}

enum class ColorVision { NORMAL, PROTAN, DEUTAN, TRITAN }

/** WCAG contrast, OKLab distance and colour-vision simulation. */
object ColorMath {

    fun srgbToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun linear(rgb: Int) = doubleArrayOf(
        srgbToLinear((rgb shr 16) and 0xFF),
        srgbToLinear((rgb shr 8) and 0xFF),
        srgbToLinear(rgb and 0xFF),
    )

    /** WCAG relative luminance of an 0xRRGGBB colour. */
    fun luminance(rgb: Int): Double {
        val (r, g, b) = linear(rgb)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Approximate relative luminance of a grey camera pixel (Y plane, 0-255). Y is
     * gamma-encoded like sRGB, so it must be linearised before comparing with
     * luminances; using Y/255 directly would put the deep/bright switch near Y=61
     * instead of the correct ~134.
     */
    fun luminanceFromLuma(y: Int): Double = srgbToLinear(y.coerceIn(0, 255))

    fun contrast(l1: Double, l2: Double): Double {
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun contrast(rgb1: Int, rgb2: Int): Double = contrast(luminance(rgb1), luminance(rgb2))

    // Machado, Oliveira & Fernandes (2009), severity 1.0, on linear RGB.
    private val machado = mapOf(
        ColorVision.PROTAN to arrayOf(
            doubleArrayOf(0.152286, 1.052583, -0.204868),
            doubleArrayOf(0.114503, 0.786281, 0.099216),
            doubleArrayOf(-0.003882, -0.048116, 1.051998)),
        ColorVision.DEUTAN to arrayOf(
            doubleArrayOf(0.367322, 0.860646, -0.227968),
            doubleArrayOf(0.280085, 0.672501, 0.047413),
            doubleArrayOf(-0.011820, 0.042940, 0.968881)),
        ColorVision.TRITAN to arrayOf(
            doubleArrayOf(1.255528, -0.076749, -0.178779),
            doubleArrayOf(-0.078411, 0.930809, 0.147602),
            doubleArrayOf(0.004733, 0.691367, 0.303900)),
    )

    /** Linear RGB as seen with the given colour vision. */
    fun simulate(rgb: Int, vision: ColorVision): DoubleArray {
        val l = linear(rgb)
        val m = machado[vision] ?: return l
        return DoubleArray(3) { i ->
            (m[i][0] * l[0] + m[i][1] * l[1] + m[i][2] * l[2]).coerceIn(0.0, 1.0)
        }
    }

    /** OKLab (L, a, b) of a linear-RGB triple. */
    fun oklab(lin: DoubleArray): DoubleArray {
        val (r, g, b) = lin
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** OKLab distance between two colours as seen with [vision]. ~0.02 is barely visible. */
    fun distance(rgb1: Int, rgb2: Int, vision: ColorVision): Double {
        val a = oklab(simulate(rgb1, vision))
        val b = oklab(simulate(rgb2, vision))
        val dl = a[0] - b[0]
        return sqrt(dl * dl + hypot(a[1] - b[1], a[2] - b[2]).pow(2))
    }

    /** Smallest [distance] over all colour-vision types. */
    fun worstDistance(rgb1: Int, rgb2: Int): Double =
        ColorVision.entries.minOf { distance(rgb1, rgb2, it) }
}
