"""Export flattened grids + Python's per-cell backgrounds and answer steps, so the
Android app can check CellBackground.medians and AnswerPalette.stepFor give the
SAME numbers (BackgroundParityTest, plain JVM, no phone).

Writes one PNG per grid (lossless grayscale, 450x450, stored in the polarity the
grid was FOUND in, like the app's warp) and expected.txt lines
`name searched_inverted m0 .. m80 steps_ink_blue steps_high_contrast`
into android/app/src/test/resources/background/. m = camera-polarity medians;
steps = 81 chars of D/B (single frame, no hysteresis).

Cases: synthetic paper/shadow/glare/dark_mode/low_res, the real photos, and edge
grids for the median rule (even split -> lower value) and for luma values either
side of each palette's switch point (Ink blue Y 133/134, High contrast 114/115).
Re-run after changing cell_backgrounds / answer_ink.py, then run the JVM tests."""
import os
import random

import cv2
import numpy as np

from grid import preprocess, find_grid, warp, cell_backgrounds, WARP_SIZE
from answer_ink import step_for
from synth import make_sample
from make_digits import load_fonts

OUT = os.path.join("android", "app", "src", "test", "resources", "background")
CONDITIONS = ["clean", "shadow", "glare", "dark_mode", "low_res"]


def warped_from(gray):
    """Warp from the polarity that finds the grid (normal first), like the app does."""
    for inv, attempt in ((False, gray), (True, 255 - gray)):
        g, th = preprocess(attempt)
        corners = find_grid(th)
        if corners is not None:
            return warp(g, corners)[0], inv
    return None, None


def edge_grid():
    """Flat cells at chosen lumas, plus cells split exactly in half (even count)."""
    values = [0, 30, 60, 90, 110, 114, 115, 116, 120, 128, 129, 133, 134, 135, 139, 140,
              160, 200, 255]
    step = WARP_SIZE // 9
    g = np.empty((WARP_SIZE, WARP_SIZE), np.uint8)
    for k in range(81):
        r, c = divmod(k, 9)
        cell = g[r * step:(r + 1) * step, c * step:(c + 1) * step]
        if k < len(values):
            cell[:] = values[k]
        else:
            # interior is 40x40 = 1600 px: top half one value, bottom half another
            lo, hi = 100 + (k % 7) * 5, 180 - (k % 5) * 7
            cell[:] = lo
            cell[25:, :] = hi  # rows 5..24 (20 rows) lo, rows 25..44 (20 rows) hi
    return g


def main():
    random.seed(11)
    np.random.seed(11)
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    cases = []

    fonts = load_fonts()
    for cond in CONDITIONS:
        for _ in range(5):
            img, _, _ = make_sample(cond, fonts)
            w, inv = warped_from(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))
            if w is not None:
                cases.append((f"synth_{cond}", w, inv))
                break

    for name in ("puzzle1", "puzzle2", "puzzle3"):
        img = cv2.imread(os.path.join("photos", f"{name}.jpg"), cv2.IMREAD_GRAYSCALE)
        w, inv = warped_from(img) if img is not None else (None, None)
        if w is not None:
            cases.append((f"real_{name}", w, inv))

    edge = edge_grid()
    cases.append(("edge_values", edge, False))
    cases.append(("edge_values_inv", 255 - edge, True))  # same cells, found inverted

    lines = []
    for name, w, inv in cases:
        assert w.shape == (WARP_SIZE, WARP_SIZE) and w.dtype == np.uint8
        cv2.imwrite(os.path.join(OUT, f"{name}.png"), w)
        bg = cell_backgrounds(w)
        if inv:
            bg = [255 - v for v in bg]
        ink = "".join(step_for(v, "INK_BLUE") for v in bg)
        hc = "".join(step_for(v, "HIGH_CONTRAST") for v in bg)
        lines.append(f"{name} {int(inv)} {' '.join(map(str, bg))} {ink} {hc}")
        print(f"{name:<18} inv={int(inv)} median range {min(bg)}-{max(bg)}  "
              f"ink blue {ink.count('D')}D/{ink.count('B')}B")
    with open(os.path.join(OUT, "expected.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"exported {len(cases)} grids to {OUT} ({size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
