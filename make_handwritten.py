"""Generate synthetic HANDWRITTEN digit cells (digits 1-9) from MNIST, for the
printed-vs-handwritten classifier (train_hand.py). Every sample goes through
grid.binarize + grid.clean_cell, exactly like real camera cells and make_digits.py.

A sudoku entry is not an MNIST digit: it is written small, in pencil or pen, inside a
printed cell. So each MNIST digit is redrawn into a 50 px cell with a random size,
stroke width (thinned: MNIST strokes are marker-thick), slant, position, ink darkness
(pencil is grey), the cell's grid lines, blur and noise.

Usage: python make_handwritten.py --per-digit 3000 --out hand.npz
MNIST is downloaded once by Keras (~11 MB, ~/.keras/datasets)."""
import argparse
import random

import cv2
import numpy as np

from grid import binarize, clean_cell

CELL = 50


def stroke_width(mask):
    """Typical stroke width of a binary shape, px (twice the 90th-percentile depth)."""
    padded = cv2.copyMakeBorder(mask, 1, 1, 1, 1, cv2.BORDER_CONSTANT, value=0)  # a filled bbox (a "1") has no edge inside
    dist = cv2.distanceTransform(padded, cv2.DIST_L2, 3)
    return 2 * float(np.percentile(dist[padded > 0], 90))


def render(digit28):
    """One MNIST digit (28x28, white on black) -> a 28x28 cleaned cell or None."""
    ys, xs = np.nonzero(digit28 > 40)
    glyph = digit28[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    # big first (smooth strokes), then thin to a pen / pencil width, then down to size
    big = cv2.resize(glyph, None, fx=8, fy=8, interpolation=cv2.INTER_CUBIC)
    mask = (big > 110).astype(np.uint8)
    height = random.randint(int(CELL * 0.36), int(CELL * 0.78))   # written size varies a lot
    scale = height / mask.shape[0]
    want = random.uniform(1.6, 4.2) / scale                        # final stroke 1.6-4.2 px
    have = stroke_width(mask)
    if have > want + 2:
        k = int((have - want) / 2)
        thin = cv2.erode(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * k + 1, 2 * k + 1)))
        if thin.sum() > 0.25 * mask.sum() * want / have:           # keep it if the digit survived
            mask = thin
    stretch = random.uniform(0.8, 1.25)
    w = max(3, int(mask.shape[1] * scale * stretch))
    small = cv2.resize(mask * 255, (min(w, CELL - 6), height), interpolation=cv2.INTER_AREA)
    h, w = small.shape

    paper = random.randint(170, 255)
    ink = random.randint(0, 150)                                   # pen dark, pencil grey
    a = np.full((CELL, CELL), paper, np.float32)
    x = int((CELL - w) / 2 + random.uniform(-7, 7))
    y = int((CELL - h) / 2 + random.uniform(-7, 7))
    x, y = min(max(x, 1), CELL - w - 1), min(max(y, 1), CELL - h - 1)
    alpha = small.astype(np.float32) / 255
    a[y:y + h, x:x + w] = a[y:y + h, x:x + w] * (1 - alpha) + ink * alpha
    a = a.astype(np.uint8)

    if random.random() < 0.6:  # partial grid lines at the border (same as make_digits)
        t = random.randint(1, 3)
        for side in random.sample(range(4), random.randint(1, 4)):
            if side == 0: a[:t, :] = 30
            if side == 1: a[-t:, :] = 30
            if side == 2: a[:, :t] = 30
            if side == 3: a[:, -t:] = 30
    M = cv2.getRotationMatrix2D((CELL / 2, CELL / 2), random.uniform(-15, 15), 1.0)
    M[0, 1] += random.uniform(-0.25, 0.25)                          # slant
    a = cv2.warpAffine(a, M, (CELL, CELL), borderMode=cv2.BORDER_REPLICATE)
    if random.random() < 0.5:
        a = cv2.GaussianBlur(a, (3, 3), random.uniform(0.3, 1.2))
    a = np.clip(a + np.random.normal(0, random.uniform(0, 12), a.shape), 0, 255).astype(np.uint8)
    return clean_cell(binarize(a))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-digit", type=int, default=3000)
    ap.add_argument("--out", default="hand.npz")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    random.seed(args.seed)
    np.random.seed(args.seed)
    import tensorflow as tf
    (x_train, y_train), (x_test, y_test) = tf.keras.datasets.mnist.load_data()
    x, labels = np.concatenate([x_train, x_test]), np.concatenate([y_train, y_test])
    X, y = [], []
    for d in range(1, 10):
        pool = x[labels == d]
        made = 0
        while made < args.per_digit:
            img = render(pool[random.randrange(len(pool))])
            if img is not None:
                X.append(img); y.append(d - 1); made += 1
        print(f"digit {d}: {made}")
    np.savez_compressed(args.out, X=np.array(X), y=np.array(y))
    print("saved", args.out)


if __name__ == "__main__":
    main()
