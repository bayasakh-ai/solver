"""Find fonts that draw real digits. Writes good_fonts.txt and preview pages
(fonts_preview_N.png) so you can eyeball every font that passed.
Usage: python font_check.py"""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from make_digits import find_fonts

ROW_H, PER_PAGE = 44, 50


def digits_ok(path):
    """Reject fonts that fail to load, draw nothing, or draw the same
    glyph (usually an empty box) for different digits."""
    try:
        font = ImageFont.truetype(path, 36)
    except OSError:
        return False
    seen = set()
    for d in "123456789":
        im = Image.new("L", (50, 50), 0)
        ImageDraw.Draw(im).text((8, 2), d, fill=255, font=font)
        a = np.array(im)
        if a.sum() == 0:
            return False
        seen.add(a.tobytes())
    return len(seen) == 9


def main():
    fonts = find_fonts()
    good = [f for f in fonts if digits_ok(f)]
    print(f"{len(good)} of {len(fonts)} fonts passed the automatic check")
    with open("good_fonts.txt", "w") as fh:
        fh.write("\n".join(good) + "\n")

    label_font = ImageFont.load_default()
    for p in range(0, len(good), PER_PAGE):
        page = good[p:p + PER_PAGE]
        sheet = Image.new("L", (900, ROW_H * len(page)), 255)
        draw = ImageDraw.Draw(sheet)
        for i, path in enumerate(page):
            y = i * ROW_H
            draw.text((5, y + 15), f"{p + i + 1}: {path.split('/')[-1][:40]}", fill=0, font=label_font)
            font = ImageFont.truetype(path, 32)
            draw.text((380, y + 4), "1 2 3 4 5 6 7 8 9", fill=0, font=font)
            draw.line([(0, y + ROW_H - 1), (900, y + ROW_H - 1)], fill=200)
        name = f"fonts_preview_{p // PER_PAGE + 1}.png"
        sheet.save(name)
        print("saved", name)
    print("Open the previews. Delete lines from good_fonts.txt for any font whose "
          "digits look wrong (dots, symbols, scribbly script). Then rerun make_digits.py.")


if __name__ == "__main__":
    main()
