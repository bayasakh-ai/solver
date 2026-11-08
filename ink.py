"""Printed vs user ink: the decision rules. Mirrored 1:1 by InkClassifier.kt (pure
Kotlin); the piece statistics themselves are measured in grid.py / GridDetector.kt.
export_ink_cases.py + InkClassifierTest check Python and Kotlin agree.

A piece = one connected blob of ink inside a cell (the same pieces clean_cell sees).
Each piece is judged on its own, so a pen stroke spilling into a printed cell, or a
note in a cell corner, is removed without touching the printed digit.

Only colour decides (v1). chroma = distance from neutral ink (neutral_distance).
Measured (2026-11-08), chroma at half resolution like the phone's YUV_420_888:
- printed pieces: max 1.4 on photos/ (also scaled to a 640 px frame), max 2.0 on
  synthetic pen_marks/pencil photos with warm/cool colour casts;
- red pen on puzzle1: min 42 at 640 px (69 at full size);
- synthetic pen digit pieces (red, blue, dark navy): 0/1032 below 6, 14 below 12,
  72 below 20 (thin dark-blue strokes lose colour at half resolution).
So 12 / 6 rather than the plan's first guess of 20 / 10: printed stays 3x below
"unsure", and no pen piece is silently taken as print. Starting points: check them
with real blue pens.

Darkness (pencil) is measured and reported but does NOT decide. Darkness vs local
paper overlaps between print and user ink: newspaper print in puzzle2/puzzle3 is
0.42-0.60, red pen 0.33-0.43, and synthetic glare/blur/low_res print drops to
0.12-0.20 (0.21-0.50 even relative to the grid's darkest digits). Any pencil cut-off
would drop printed digits in those conditions, so pencil waits for the v1.1 shape
model, like black pen."""

import math
from collections import namedtuple

PRINTED, USER, UNSURE = "printed", "user", "unsure"

# One ink piece; same fields as Kotlin's InkPiece. counts = clean_cell would keep it.
Piece = namedtuple("Piece", "chroma darkness counts")

USER_CHROMA = 12.0   # >= this far from neutral ink: coloured pen, removed
UNSURE_CHROMA = 6.0  # between the two: kept, cell flagged for the user to confirm
MIN_PRINTED_CELLS = 17  # fewer left after removal: digits are printed in colour, skip


def neutral_distance(icr, icb, pcr, pcb, k):
    """Colour distance of an ink piece from neutral ink, in (Cr, Cb) offsets from 128.
    Neutral ink can show anything between two colours:
    - paper offset * k (k = inkY/paperY): a colour cast MULTIPLIES the channels, so a
      solid black stroke is tinted much less than the paper (warm light);
    - the paper offset itself: a thin stroke at half-resolution chroma (and JPEG
      chroma subsampling) mostly shows the paper's colour (tinted newsprint).
    So: distance from the segment between those two points. Coloured pen is far from
    both. Mirrored by InkClassifier.neutralDistance."""
    k = min(max(k, 0.0), 1.0)
    ax, ay = pcr * k, pcb * k                 # segment start
    sx, sy = pcr - ax, pcb - ay               # segment direction (to the paper point)
    dx, dy = icr - ax, icb - ay
    ss = sx * sx + sy * sy
    t = 0.0 if ss < 1e-9 else min(max((dx * sx + dy * sy) / ss, 0.0), 1.0)
    return math.hypot(dx - t * sx, dy - t * sy)


def kind(chroma):
    """chroma = distance of the piece's colour from neutral ink (neutral_distance)."""
    if chroma >= USER_CHROMA:
        return USER
    if chroma >= UNSURE_CHROMA:
        return UNSURE
    return PRINTED


def cell_unsure(pieces):
    """A cell needs the user's check when a piece that could be its digit is unsure."""
    return any(p.counts and kind(p.chroma) == UNSURE for p in pieces)


def use_removal(printed_cells_left):
    """Safety net: a puzzle has >= 17 givens. If removing user ink leaves fewer, the
    'user ink' was the puzzle itself (e.g. a magazine printing digits in blue)."""
    return printed_cells_left >= MIN_PRINTED_CELLS
