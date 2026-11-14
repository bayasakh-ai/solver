"""Printed or handwritten? Decision rules for one grid (pure logic, no OpenCV).

Pencil and black pen can't be told from print by colour (ink.py) or by absolute
darkness, so each filled cell is compared with THE SAME PUZZLE'S printed digits:

  stroke    how thick the lines of the digit are        (grid.cell_shape)
  darkness  how far the ink is below the paper behind it
  height    how tall the digit is

The reference is the median over the cells the shape model (hand.tflite, P(handwritten)
from the 28x28 cleaned cell) is sure are printed. Fewer than 5 such cells: no reference,
nothing is split (the two 247 photos with a bad warp: 1 sure cell of 32 / 50; with
every cell as the reference 10 printed digits were called handwritten).

  thin   stroke   < 0.92 x reference    handwriting is thinner than print
  pale   darkness < 0.88 x reference    pencil and ballpoint are lighter than print
  odd    height more than 12% off       printed digits of one puzzle share a height

HAND = thin AND pale. Real handwriting shows both in 203 of 205 cells; printed digits
that show only one of them with an odd height are damaged or merged with a grid line
(synthetic tilt / low_res / moire; screen_glare_sudokucom r1c5, paler and shorter,
made that photo unsolvable when it was moved out of the puzzle). Two exceptions -> UNSURE:
  - the shape model is sure it is print and the height fits the print;
  - the stroke is only just thinner (>= 0.85 x reference) and the shape model is not
    sure it is handwriting (P < 0.9). Glare thins and pales printed digits to stroke
    0.86-0.90 (4 synthetic glare cells, 3 puzzles lost); handwriting is rarely that
    thick (4 of 205 cells).
Any other two signs -> UNSURE. One or none -> PRINTED, unless the shape model insists
on handwriting (P >= 0.9) -> UNSURE: a thin printed font filled in with a thick pen
gives no signs, and there the shape model is the only signal.
UNSURE = kept in the puzzle as read, the Check screen asks; never silently moved.

Measured 2026-11-14 (640 px frames; real = 37 photos with a found grid: newspaper,
the owner's book in ballpoint and pencil, screens; synthetic = evaluate.py --synth 20):
                              printed -> HAND   printed UNSURE   handwritten -> HAND
  real paper (356 / 205)           0                  5          201 (4 unsure, 0 printed)
  real screens (507 printed)       0                  6          typed entries look printed
  synthetic, 13 conditions (7159)  0                 89          -
  synthetic pencil (553)           0                 29          -
  the first rule (two of the three signs): 7 synthetic printed digits -> HAND, glare
  100 -> 85%, tilt 95 -> 90%, moire 90 -> 85%.
  the shape model alone (P >= 0.5):  8 printed flagged, 38 handwritten missed
  height alone: 8 printed flagged, 152 handwritten missed (handwriting is often the
  same height as the print).
The thresholds were chosen on those same cells (two writers): not a held-out result.

The background colour behind a digit (books that shade their givens) is NOT used: in
gray it overlaps (book_pen_01: printed 0.76-1.11, handwritten 0.65-1.15 of the printed
median) and other books shade whole boxes instead (book_01).

Mirrored by Hand.kt (HandParityTest, fixtures from export_hand_cases.py)."""
from collections import namedtuple

Shape = namedtuple("Shape", "stroke dark height")

PRINTED, HAND, UNSURE = "printed", "hand", "unsure"

STROKE = 0.92          # thin: stroke below this share of the printed reference
NEAR_STROKE = 0.85     # thin, but from this share on the shape model must agree
DARK = 0.88            # pale: darkness below this share of the printed reference
HEIGHT = 0.12          # odd: height more than this share away from the reference
SURE_PRINTED = 0.1     # shape model P(handwritten) below this: a reference cell
SURE_HAND = 0.9        # shape model P(handwritten) from this: it insists on handwriting
MIN_REFERENCE = 5      # fewer sure-printed cells than this: no reference, no split
MIN_PRINTED_CELLS = 17  # fewer printed cells left: don't trust the split (like ink.py)


def _median(values):
    v = sorted(values)
    n = len(v)
    return v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2


def reference(shapes, probs):
    """The puzzle's printed digits as a Shape of medians, or None when the shape model
    is sure of too few cells to say what this puzzle's print looks like (the two 247
    photos with a bad warp: 1 sure cell of 32 / 50, and with every cell as the reference
    10 printed digits were called handwritten). shapes / probs: same-length lists over
    the filled cells (Shape, P(handwritten))."""
    sure = [s for s, p in zip(shapes, probs) if p < SURE_PRINTED]
    if len(sure) < MIN_REFERENCE:
        return None
    return Shape(_median([s.stroke for s in sure]), _median([s.dark for s in sure]),
                 _median([s.height for s in sure]))


def kind(shape, ref, prob):
    thin = shape.stroke < STROKE * ref.stroke
    pale = shape.dark < DARK * ref.dark
    odd = abs(shape.height - ref.height) > HEIGHT * ref.height
    if thin and pale:
        if prob < SURE_PRINTED and not odd:
            return UNSURE
        if shape.stroke >= NEAR_STROKE * ref.stroke and prob < SURE_HAND:
            return UNSURE
        return HAND
    if thin + pale + odd >= 2:
        return UNSURE
    return UNSURE if prob >= SURE_HAND else PRINTED


def classify(shapes, probs):
    """Kinds for the filled cells of one grid, or all PRINTED when the split leaves too
    few printed digits for any sudoku, or there is no reference (the measurements don't
    fit this puzzle)."""
    if not shapes:
        return []
    ref = reference(shapes, probs)
    if ref is None:
        return [PRINTED] * len(shapes)
    kinds = [kind(s, ref, p) for s, p in zip(shapes, probs)]
    if sum(k != HAND for k in kinds) < MIN_PRINTED_CELLS:
        return [PRINTED] * len(shapes)
    return kinds
