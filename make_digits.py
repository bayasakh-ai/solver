"""Generate a synthetic printed-digit dataset (digits 1-9) using system fonts.
Every sample goes through grid.clean_cell, exactly like real camera cells.
Usage: python make_digits.py --per-digit 3000 --out digits.npz"""
import argparse
import glob
import random

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from grid import binarize, clean_cell

CELL = 50


def find_fonts():
    pats = ["/usr/share/fonts/**/*.ttf", "/usr/share/fonts/**/*.otf",
            "/Library/Fonts/*.ttf", "/System/Library/Fonts/**/*.ttf",
            "C:/Windows/Fonts/*.ttf", "./fonts/*.ttf"]
    fonts = [f for p in pats for f in glob.glob(p, recursive=True)]
    skip = ("symbol", "emoji", "dingbat", "wingding", "webding", "marlett", "braille",
            "ornament", "lastresort", "zapfino", "music", "math", "icon")
    return [f for f in fonts if not any(s in f.lower() for s in skip)]


def load_fonts():
    """Use good_fonts.txt (from font_check.py) if present, else scan the system."""
    try:
        with open("good_fonts.txt") as fh:
            fonts = [line.strip() for line in fh if line.strip()]
        print("using good_fonts.txt")
        return fonts
    except FileNotFoundError:
        return find_fonts()


def render(d, font_path):
    img = Image.new("L", (CELL, CELL), random.randint(170, 255))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(font_path, random.randint(int(CELL * 0.5), int(CELL * 0.8)))
    x0, y0, x1, y1 = draw.textbbox((0, 0), str(d), font=font)
    x = (CELL - (x1 - x0)) / 2 - x0 + random.uniform(-3, 3)
    y = (CELL - (y1 - y0)) / 2 - y0 + random.uniform(-3, 3)
    draw.text((x, y), str(d), fill=random.randint(0, 80), font=font)
    a = np.array(img)

    if random.random() < 0.6:  # partial grid lines at the border
        t = random.randint(1, 3)
        for side in random.sample(range(4), random.randint(1, 4)):
            if side == 0: a[:t, :] = 30
            if side == 1: a[-t:, :] = 30
            if side == 2: a[:, :t] = 30
            if side == 3: a[:, -t:] = 30
    M = cv2.getRotationMatrix2D((CELL / 2, CELL / 2), random.uniform(-8, 8), 1.0)
    a = cv2.warpAffine(a, M, (CELL, CELL), borderMode=cv2.BORDER_REPLICATE)
    if random.random() < 0.5:
        a = cv2.GaussianBlur(a, (3, 3), random.uniform(0.3, 1.2))
    a = np.clip(a + np.random.normal(0, random.uniform(0, 12), a.shape), 0, 255).astype(np.uint8)

    return clean_cell(binarize(a))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-digit", type=int, default=3000)
    ap.add_argument("--out", default="digits.npz")
    args = ap.parse_args()
    fonts = load_fonts()
    print(f"{len(fonts)} fonts")
    X, y, F = [], [], []
    for d in range(1, 10):
        made = 0
        while made < args.per_digit:
            fi = random.randrange(len(fonts))
            try:
                img = render(d, fonts[fi])
            except OSError:
                continue
            if img is not None:
                X.append(img); y.append(d - 1); F.append(fi); made += 1
        print(f"digit {d}: {made}")
    # fonts + f (font index per sample) let diagnose.py find bad fonts
    np.savez_compressed(args.out, X=np.array(X), y=np.array(y),
                        f=np.array(F), fonts=np.array(fonts))
    print("saved", args.out)


if __name__ == "__main__":
    main()
