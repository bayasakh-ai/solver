"""Export flattened grids + Python's cell shapes and printed / handwritten verdicts, so
HandParityTest (plain JVM, no phone) can check Hand.kt measures and decides like
grid.cell_shape and hand.py.
Writes android/app/src/test/resources/hand/:
  <case>.bin.gz  the binarized grid then the gray grid, 450x450 bytes each (gzip)
  shapes.txt     `case r c stroke dark height` per cell that has a shape
  kinds.txt      `case r c P(handwritten) kind` per cell that was classified
  grids.txt      `case reference(0/1) hand unsure` per case

Cases (640 px frames like the phone): the owner's book in ballpoint and pencil, a blank
newspaper puzzle, a glare-damaged screen (its r1c5 must stay UNSURE), a bad warp with no
reference, a synthetic glare photo, and edge cases around every threshold.
Re-run after changing hand.py or grid.cell_shape, then run the JVM tests."""
import gzip
import os
import random

import cv2
import numpy as np

import hand
import run
from evaluate import fit_frame
from grid import binarize, cell_shapes

OUT = os.path.join("android", "app", "src", "test", "resources", "hand")
PHOTOS = ["book_pen_01", "book_pencil_01", "book_pencil_03", "puzzle2",
          "screen_glare_sudokucom_01", "screen_filled_247_01"]


def main():
    import tensorflow as tf
    os.makedirs(OUT, exist_ok=True)
    random.seed(4)
    np.random.seed(4)
    model = tf.keras.models.load_model("digits.keras")
    images = [(n, fit_frame(cv2.imread(os.path.join("photos", n + ".jpg")), 640)) for n in PHOTOS]
    from make_digits import load_fonts
    from synth import make_sample
    images.append(("synth_glare", make_sample("glare", load_fonts())[0]))

    shapes_out, kinds_out, grids_out = [], [], []
    fmt = lambda x: repr(float(x))
    for name, img in images:
        res = run.process(img, model)
        warped = res["warped"]
        with gzip.open(os.path.join(OUT, name + ".bin.gz"), "wb") as f:
            f.write(binarize(warped).tobytes() + warped.tobytes())
        shapes = cell_shapes(warped)
        for r in range(9):
            for c in range(9):
                if shapes[r][c] is not None:
                    shapes_out.append(f"{name} {r} {c} {' '.join(fmt(v) for v in shapes[r][c])}")
        probs = res["hand_probs"]
        for (r, c), k in sorted(res["kinds"].items()):
            kinds_out.append(f"{name} {r} {c} {fmt(probs[(r, c)])} {k}")
        pos = sorted(res["kinds"])
        ref = hand.reference([hand.Shape(*shapes[r][c]) for r, c in pos], [probs[p] for p in pos])
        ks = list(res["kinds"].values())
        grids_out.append(f"{name} {int(ref is not None)} {ks.count(hand.HAND)} {ks.count(hand.UNSURE)}")

    # edge cases: one cell against the reference (4.0, 0.6, 25.0): `stroke dark height P kind`
    ref = hand.Shape(4.0, 0.6, 25.0)
    edges = []
    for stroke in (0.84, 0.85, 0.9199, 0.92, 1.0):
        for dark in (0.5, 0.8799, 0.88, 1.0):
            for height in (0.87, 0.88, 1.0, 1.12, 1.13):
                for p in (0.0, 0.0999, 0.1, 0.5, 0.8999, 0.9, 1.0):
                    s = hand.Shape(stroke * ref.stroke, dark * ref.dark, height * ref.height)
                    edges.append(f"{fmt(s.stroke)} {fmt(s.dark)} {fmt(s.height)} {fmt(p)} {hand.kind(s, ref, p)}")

    for fname, lines in (("shapes.txt", shapes_out), ("kinds.txt", kinds_out), ("grids.txt", grids_out),
                         ("edges.txt", edges)):
        with open(os.path.join(OUT, fname), "w") as f:
            f.write("\n".join(lines) + "\n")
    print(f"{len(shapes_out)} shapes, {len(kinds_out)} classified cells, {len(edges)} edge cases -> {OUT}")
    for line in grids_out:
        print("  ", line)


if __name__ == "__main__":
    main()
