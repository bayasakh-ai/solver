"""Find what the digit model gets wrong.
Usage:  python diagnose.py              -> report + mistakes.png
        python diagnose.py --prune 0.2  -> also removes fonts with >20% errors
                                           from good_fonts.txt (backup kept)"""
import argparse
import shutil

import cv2
import numpy as np
import tensorflow as tf

ap = argparse.ArgumentParser()
ap.add_argument("--prune", type=float, default=None)
args = ap.parse_args()

d = np.load("digits.npz")
X, y, F, fonts = d["X"], d["y"], d["f"], d["fonts"]
model = tf.keras.models.load_model("digits.keras")
pred = model.predict(X[..., None].astype("float32") / 255.0, verbose=0).argmax(1)
wrong = pred != y
print(f"overall accuracy: {1 - wrong.mean():.4f}")

cm = np.zeros((9, 9), int)
np.add.at(cm, (y, pred), 1)
print("\nconfusion (row = true digit, col = predicted):")
print("     " + " ".join(f"{i:>5}" for i in range(1, 10)))
for i in range(9):
    print(f"{i + 1:>3}: " + " ".join(f"{v:>5}" for v in cm[i]))

rates = []
for fi, name in enumerate(fonts):
    m = F == fi
    if m.sum():
        rates.append((wrong[m].mean(), int(m.sum()), str(name)))
rates.sort(reverse=True)
print("\nworst fonts (error rate, samples, font):")
for r, n, name in rates[:15]:
    print(f"  {r:6.1%}  {n:5d}  {name.split('/')[-1]}")

# mistakes.png: each tile shows the image with "true>predicted" under it
idx = np.where(wrong)[0][:200]
tiles = []
for i in idx:
    t = cv2.resize(X[i], (56, 56), interpolation=cv2.INTER_NEAREST)
    t = cv2.copyMakeBorder(t, 0, 16, 2, 2, cv2.BORDER_CONSTANT, value=60)
    cv2.putText(t, f"{y[i] + 1}>{pred[i] + 1}", (8, 69), cv2.FONT_HERSHEY_SIMPLEX, 0.4, 255, 1)
    tiles.append(t)
if tiles:
    cols = 20
    while len(tiles) % cols:
        tiles.append(np.zeros_like(tiles[0]))
    rows = [np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)]
    cv2.imwrite("mistakes.png", np.vstack(rows))
    print("\nsaved mistakes.png")

if args.prune is not None:
    bad = {name for r, n, name in rates if r > args.prune}
    shutil.copy("good_fonts.txt", "good_fonts_backup.txt")
    keep = [l.strip() for l in open("good_fonts.txt") if l.strip() and l.strip() not in bad]
    open("good_fonts.txt", "w").write("\n".join(keep) + "\n")
    print(f"\nremoved {len(bad)} fonts, {len(keep)} left in good_fonts.txt "
          "(backup: good_fonts_backup.txt). Now rerun make_digits.py and train_digits.py.")
