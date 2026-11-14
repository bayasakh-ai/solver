"""Export flattened quads (grid.line_ink) + grid.box_line_score for GridCheckParityTest.

Writes one PNG per case (450x450, ink = 255) and expected.txt lines `name score accepted`
into android/app/src/test/resources/gridcheck/. Real grids (synth conditions, photos) and
the false quads the finder proposes on photos/nogrid_*.jpg (book cover, title, keyboard).
Run after changing box_line_score / looks_like_grid / BOX_LINES:
    python export_grid_check_cases.py
"""
import glob
import os
import random

import cv2
import numpy as np

import grid
from grid import BOX_LINES, box_line_score, find_grid, line_ink, looks_dark, preprocess, warp
from evaluate import fit_frame
from make_digits import load_fonts
from synth import make_sample

OUT = os.path.join("android", "app", "src", "test", "resources", "gridcheck")
CONDITIONS = ["clean", "shadow", "blur", "dark_mode", "moire"]
PHOTOS = ["puzzle2", "screen_small_ny_01", "screen_glare_web_01", "book_01"]


def first_quad(img):
    """line_ink of the first quad the finder proposes, with NO check
    (normal polarity first, like the app), or None."""
    for attempt in (img, 255 - img):
        gray, th = preprocess(attempt)
        corners = find_grid(th)  # no accept: every candidate, grid or not
        if corners is not None:
            w = warp(gray, corners)[0]
            return line_ink(255 - w if looks_dark(w) else w)
    return None


def blurred(name, scale, sigma):
    """A real photo as a far, blurred frame: shrunk into a 640 px frame, Gaussian blur."""
    img = fit_frame(cv2.imread(os.path.join("photos", f"{name}.jpg")), int(640 * scale))
    h, w = img.shape[:2]
    H, W = max(640 if h > w else 480, h), max(480 if h > w else 640, w)
    img = cv2.copyMakeBorder(img, (H - h) // 2, H - h - (H - h) // 2, (W - w) // 2, W - w - (W - w) // 2,
                             cv2.BORDER_REFLECT)
    return cv2.GaussianBlur(img, (0, 0), sigma)


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
            b = first_quad(make_sample(cond, fonts)[0])
            if b is not None:
                cases.append((f"synth_{cond}", b))
                break
    names = PHOTOS + sorted(os.path.splitext(os.path.basename(p))[0]
                            for p in glob.glob(os.path.join("photos", "nogrid_*.jpg")))
    for name in names:
        img = cv2.imread(os.path.join("photos", f"{name}.jpg"))
        b = first_quad(fit_frame(img, 640)) if img is not None else None
        if b is not None:
            cases.append((name, b))
    for name, scale, sigma in (("puzzle2", 0.7, 1.6), ("book_01", 1.0, 1.0), ("screen_ny_01", 0.5, 1.0),
                               ("nogrid_5", 0.5, 0.7), ("nogrid_2", 0.7, 1.3)):
        b = first_quad(blurred(name, scale, sigma))
        if b is not None:
            cases.append((f"{name}_far_{scale}_blur_{sigma}", b))
    lines = []
    for name, b in cases:
        assert b.shape == (grid.WARP_SIZE, grid.WARP_SIZE) and b.dtype == np.uint8
        cv2.imwrite(os.path.join(OUT, f"{name}.png"), b)
        score = box_line_score(b)
        lines.append(f"{name} {score!r} {int(score >= BOX_LINES)}")
    with open(os.path.join(OUT, "expected.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
