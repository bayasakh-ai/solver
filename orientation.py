"""Which way is up? Mirrored by Orientation.kt (Android).

find_grid labels the corners by SCREEN position (order_corners: top-left = smallest
x + y). Past 45 degrees of rotation that is a different physical corner, so the
warp comes out a quarter turn off and every digit reaches the model sideways
(rotate_steep: 0%, digits 2-5% right as warped, 99-100% once turned upright).

So the reading decides: read the warp at each quarter turn k (np.rot90(warp, k)),
score = mean top probability over the read cells, and take the best-scoring turn
whose reading is a CONSISTENT sudoku (no digit twice in a row, column or box).
Confidence alone picked the right turn in 149/150 samples but with margins down
to 0.010 (the model is confidently wrong on sideways digits too), so sudoku logic
checks it. If no turn is consistent (a misread in the right one), the best score wins.

Turn k == relabel the corners with roll_corners(corners, k) (new i = old (i + k) % 4),
so everything downstream (chroma, reading, drawing) is upright with no other change.
"""
import numpy as np


def is_consistent(grid):
    """No digit twice in any row, column or box (empty cells ignored)."""
    units = [[(r, c) for c in range(9)] for r in range(9)]
    units += [[(r, c) for r in range(9)] for c in range(9)]
    units += [[(br + r, bc + c) for r in range(3) for c in range(3)]
              for br in (0, 3, 6) for bc in (0, 3, 6)]
    for unit in units:
        digits = [grid[r][c] for r, c in unit if grid[r][c]]
        if len(digits) != len(set(digits)):
            return False
    return True


def turn_score(probs):
    """Mean top probability over read cells (probs: {(r, c): 9 probabilities})."""
    return float(np.mean([p.max() for p in probs.values()])) if probs else 0.0


def choose_turn(scores, consistent):
    """Best-scoring consistent turn; if none is consistent, the best score.
    Ties go to the lower k (0 = as found), so an upright grid stays as it is."""
    order = sorted(range(len(scores)), key=lambda k: (-scores[k], k))
    for k in order:
        if consistent[k]:
            return k
    return order[0]


def roll_corners(corners, k):
    """Relabel TL, TR, BR, BL so the warp turns by k quarter turns (== np.rot90(warp, k))."""
    return np.roll(np.asarray(corners), -k, axis=0)
