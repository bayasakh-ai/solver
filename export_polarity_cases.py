"""Export flattened grids + Python's dark-mode vote, so the Android app can check
Polarity.kt gives the SAME counts (PolarityParityTest, plain JVM, no phone).
Writes one PNG per grid (lossless grayscale, 450x450) and expected.txt lines
`name inverted light dark looks_dark` into android/app/src/test/resources/polarity/.
Each grid is checked as stored (inverted=0) AND as 255 - grid (inverted=1): the test
inverts it itself, which halves the fixture size (~120 KB per noisy photo grid).

Cases:
- synthetic photos for the conditions with distinct pixel statistics, warped the
  way the app sees them; `_searched_inv` = grid only found in the inverted image;
- real photos (puzzle2/3.jpg if present);
- edge cases for the percentile/threshold rules: cells whose spread is exactly
  19/20/21 (the MIN_SPREAD cut-off), exact ties (below == above, which count as
  dark on both sides), and coarse noise (9 grey levels, so it compresses but
  every cell sits near a threshold).
Re-run after changing cell_votes / Polarity.cellVotes, then run the JVM tests."""
import os
import random

import cv2
import numpy as np

from grid import preprocess, find_grid, warp, cell_votes, WARP_SIZE
from synth import make_sample
from make_digits import load_fonts

OUT = os.path.join("android", "app", "src", "test", "resources", "polarity")
CONDITIONS = ["clean", "shadow", "blur", "jpeg", "dark_mode"]


def warped_from(img):
    """Warp from the polarity that finds the grid (normal first), like the app does.
    Returns (warped, searched_inverted) or (None, None)."""
    for inv, attempt in ((False, img), (True, 255 - img)):
        gray, th = preprocess(attempt)
        corners = find_grid(th)
        if corners is not None:
            return warp(gray, corners)[0], inv
    return None, None


def edge_grid():
    """Each cell is flat 128 with a 10x10 patch; the patch value picks the case."""
    g = np.full((WARP_SIZE, WARP_SIZE), 128, np.uint8)
    # (dark patch offset, bright patch offset) per cell; 0 = no patch
    cases = [(-19, 0), (-20, 0), (-21, 0), (0, 19), (0, 20), (0, 21),
             (-30, 30), (-25, 25), (-40, 39), (-39, 40), (0, 0)]
    step = WARP_SIZE // 9
    for k in range(81):
        lo, hi = cases[k % len(cases)]
        r, c = divmod(k, 9)
        y, x = r * step + 10, c * step + 10
        if lo:
            g[y:y + 10, x:x + 10] = 128 + lo   # 100 px of 1600 = 6%: inside the 2% tail
        if hi:
            g[y + 20:y + 30, x + 20:x + 30] = 128 + hi
    return g


def noise_grid():
    """9 grey levels 10 apart; each cell draws them with its own random weights, so
    cells skew dark or bright by varying amounts and the votes come out mixed
    (uniform weights would make every cell an exact tie)."""
    levels = np.arange(88, 169, 10, dtype=np.uint8)
    step = WARP_SIZE // 9
    g = np.empty((WARP_SIZE, WARP_SIZE), np.uint8)
    for r in range(9):
        for c in range(9):
            p = np.random.dirichlet(np.full(9, 0.7))
            g[r * step:(r + 1) * step, c * step:(c + 1) * step] = \
                levels[np.random.choice(9, (step, step), p=p)]
    return g


def main():
    random.seed(7)
    np.random.seed(7)
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    cases = []

    fonts = load_fonts()
    for cond in CONDITIONS:
        for _ in range(5):  # blur often has no grid: retry a few times
            img, _, _ = make_sample(cond, fonts)
            w, inv = warped_from(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))
            if w is not None:
                cases.append((f"synth_{cond}" + ("_searched_inv" if inv else ""), w))
                break

    for name in ("puzzle2", "puzzle3"):
        img = cv2.imread(f"{name}.jpg", cv2.IMREAD_GRAYSCALE)
        w, inv = warped_from(img) if img is not None else (None, None)
        if w is not None:
            cases.append((f"real_{name}" + ("_searched_inv" if inv else ""), w))

    cases.append(("edge_spread_ties", edge_grid()))
    cases.append(("edge_noise", noise_grid()))

    lines = []
    for name, w in cases:
        assert w.shape == (WARP_SIZE, WARP_SIZE) and w.dtype == np.uint8
        cv2.imwrite(os.path.join(OUT, f"{name}.png"), w)
        for inv, img in ((0, w), (1, 255 - w)):
            light, dark = cell_votes(img)
            lines.append(f"{name} {inv} {light} {dark} {int(dark > light)}")
    with open(os.path.join(OUT, "expected.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"exported {len(cases)} grids to {OUT} ({size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
