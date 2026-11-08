"""Answer-digit step choice, mirrored from AnswerInk.kt (Android).

Answers use one hue in two fixed steps: DEEP on paper, BRIGHT on dark screens.
The grid decides the default: the dark-mode vote (looks_dark, in camera polarity)
says paper -> DEEP or screen -> BRIGHT. A single cell switches to the other step
only when that step's contrast against the cell's background (median camera luma,
grid.cell_backgrounds) is at least SWITCH (2x) better, i.e. a real shadow on paper
(Ink blue: Y <= 109). Grey newsprint keeps one shade instead of splitting in two:
in Y 113-147 both steps are only 1.5-2.8:1 and the halo does the work anyway
(measured on photos/puzzle4, 2026-11-08).

Only the single-frame rule lives here (for evaluate.py's step report and the
parity fixtures). The app adds frame-to-frame smoothing and hysteresis on top
(AnswerSteps in AnswerInk.kt) so a cell near the switch point doesn't flicker.
"""

DEEP, BRIGHT = "D", "B"

# (deep, bright) as 0xRRGGBB, same values as AnswerPalette in AnswerInk.kt
PALETTES = {
    "INK_BLUE": (0x1D4FB0, 0x8CC8FF),
    "HIGH_CONTRAST": (0x0517A2, 0x8CC8FF),
}


def srgb_to_linear(channel):
    c = channel / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = (srgb_to_linear((rgb >> s) & 0xFF) for s in (16, 8, 0))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def luminance_from_luma(y):
    """Camera luma is gamma-encoded like sRGB: linearise before comparing."""
    return srgb_to_linear(min(255, max(0, int(y))))


def contrast(l1, l2):
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


SWITCH = 2.0  # the other step must beat the grid's default by this contrast factor


def advantage(background_luma, default, palette="INK_BLUE"):
    """Contrast of the non-default step / contrast of the default step."""
    deep, bright = PALETTES[palette]
    bg = luminance_from_luma(background_luma)
    c_deep, c_bright = contrast(luminance(deep), bg), contrast(luminance(bright), bg)
    return c_bright / c_deep if default == DEEP else c_deep / c_bright


def step_for(background_luma, screen=False, palette="INK_BLUE"):
    """screen = the grid is a dark screen (dark-mode vote, camera polarity)."""
    default, other = (BRIGHT, DEEP) if screen else (DEEP, BRIGHT)
    return other if advantage(background_luma, default, palette) >= SWITCH else default
