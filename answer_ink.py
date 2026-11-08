"""Answer-digit step choice, mirrored from AnswerInk.kt (Android).

Answers use one hue in two fixed steps: DEEP on paper, BRIGHT on dark screens. Each
empty cell gets the step with the higher contrast against the background behind it,
measured as the median camera luma of the cell (grid.cell_backgrounds).

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


def step_for(background_luma, palette="INK_BLUE"):
    deep, bright = PALETTES[palette]
    bg = luminance_from_luma(background_luma)
    return DEEP if contrast(luminance(deep), bg) >= contrast(luminance(bright), bg) else BRIGHT
