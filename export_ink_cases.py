"""Export ink-piece stats + Python's verdicts, so InkClassifierTest (plain JVM, no phone)
can check InkClassifier.kt decides EXACTLY like ink.py.
Writes android/app/src/test/resources/ink/:
  pieces.txt  `case r c icr icb pcr pcb k chroma counts kind` per ink piece:
              raw colour inputs -> Kotlin recomputes chroma (neutralDistance), then kind
  cells.txt   `case r c unsure` per cell that has pieces (cellUnsure)
  grids.txt   `case printed_cells_left applied` (useRemoval, the 17-cell safety net)

Cases:
- photos/puzzle1.jpg (25 printed + 56 red pen) at full size and scaled to a 640 px
  frame like the phone; photos/puzzle2.jpg (blank newspaper, no user ink);
- synthetic pen_marks photos (colour cast, blue/red/navy pens: has unsure pieces);
- edge cases: chroma exactly at / just below both thresholds, the segment geometry of
  neutral_distance (cast vs thin-stroke ends, k outside 0..1, no paper tint), and
  printed_cells_left 16 / 17 for the safety net.
Re-run after changing ink.py or the piece stats in grid.py, then run the JVM tests."""
import os
import random

import cv2
import numpy as np

import ink
from grid import chroma_planes, extract_cells, find_grid, looks_dark, preprocess, warp

OUT = os.path.join("android", "app", "src", "test", "resources", "ink")


def grid_ink(img):
    gray, th = preprocess(img)
    corners = find_grid(th)
    assert corners is not None, "no grid"
    w = warp(gray, corners)[0]
    if looks_dark(w):
        w = 255 - w
    return extract_cells(w, chroma_planes(img, corners))[1]


def scaled(img, side=640):
    f = side / max(img.shape[:2])
    return cv2.resize(img, None, fx=f, fy=f, interpolation=cv2.INTER_AREA)


def main():
    os.makedirs(OUT, exist_ok=True)
    random.seed(11)
    np.random.seed(11)
    cases = []
    for name in ("puzzle1", "puzzle2"):
        img = cv2.imread(os.path.join("photos", f"{name}.jpg"))
        if img is None:
            continue
        cases.append((f"real_{name}", grid_ink(img)))
        if name == "puzzle1":
            cases.append((f"real_{name}_640", grid_ink(scaled(img))))
    from make_digits import load_fonts
    from synth import make_sample
    fonts = load_fonts()
    for k in range(3):
        cases.append((f"synth_pen_marks_{k}", grid_ink(make_sample("pen_marks", fonts)[0])))

    pieces, cells, grids = [], [], []
    fmt = lambda x: repr(float(x))
    for name, res in cases:
        for r in range(9):
            for c in range(9):
                ps = res["pieces"][r][c]
                for p, raw in ps:
                    pieces.append(f"{name} {r} {c} {' '.join(fmt(v) for v in raw)} "
                                  f"{fmt(p.chroma)} {int(p.counts)} {ink.kind(p.chroma)}")
                if ps:
                    cells.append(f"{name} {r} {c} {int(ink.cell_unsure([p for p, _ in ps]))}")
        grids.append(f"{name} {res['printed_cells_left']} {int(res['applied'])}")

    # edge cases: (icr, icb, pcr, pcb, k) chosen to land on exact distances
    edges = [(ink.USER_CHROMA, 0, 0, 0, 1), (ink.USER_CHROMA - 1e-9, 0, 0, 0, 1),
             (0, ink.UNSURE_CHROMA, 0, 0, 0.5), (0, ink.UNSURE_CHROMA - 1e-9, 0, 0, 0.5),
             (7, 3, 10, 0, 0.5),     # beside the segment (5..10, 0): distance 3
             (2, 4, 10, 0, 0.5),     # before its start (5, 0): distance 5
             (14, -3, 10, 0, 0.5),   # past the paper end (10, 0): distance 5
             (5, 0, 10, 0, 1.7),     # k > 1 clamps to 1: segment is the paper point
             (5, 0, 10, 0, -0.4),    # k < 0 clamps to 0: segment 0..paper, on it
             (-20, 13, 0, 0, 0.3)]   # untinted paper: plain distance from neutral
    for i, raw in enumerate(edges):
        ch = ink.neutral_distance(*raw)
        counts = i % 2 == 0
        pieces.append(f"edge 0 {i} {' '.join(fmt(v) for v in raw)} {fmt(ch)} {int(counts)} {ink.kind(ch)}")
        cells.append(f"edge 0 {i} {int(ink.cell_unsure([ink.Piece(ch, 0.5, counts)]))}")
    for left in (0, 16, 17, 81):
        grids.append(f"edge_left_{left} {left} {int(ink.use_removal(left))}")

    for fname, lines in (("pieces.txt", pieces), ("cells.txt", cells), ("grids.txt", grids)):
        with open(os.path.join(OUT, fname), "w") as f:
            f.write("\n".join(lines) + "\n")
    kinds = {k: sum(l.endswith(" " + k) for l in pieces) for k in (ink.PRINTED, ink.UNSURE, ink.USER)}
    print(f"{len(pieces)} pieces {kinds}, {len(cells)} cells "
          f"({sum(l.endswith(' 1') for l in cells)} unsure), {len(grids)} grids -> {OUT}")
    for line in grids:
        print("  ", line)


if __name__ == "__main__":
    main()
