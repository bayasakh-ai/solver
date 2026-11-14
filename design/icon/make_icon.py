"""Launcher icon for Sudofolio: every direction as SVG, the chosen one as Android resources.

    python design/icon/make_icon.py             # SVGs + contact sheet for all directions
    python design/icon/make_icon.py --apply scan  # also write the Android resources

Why a script: the digit is Lora semibold (the answer face) and Android vector drawables
can't hold text, so the glyph outline is taken from the bundled font. Changing the
direction, the digit or a colour is then one command, and the adaptive icon, the themed
(monochrome) layer, the legacy bitmaps and the Play Store PNG can't drift apart.

Needs: pip install fonttools cairosvg pillow   (not in the app's venv requirements)

Geometry is the adaptive-icon canvas: 108 x 108, centre (54, 54). Launchers show a
72 x 72 window with their own mask; only a circle of diameter 66 is always visible, so
every mark stays inside radius 33 of the centre (a square's corners: half-side <= 23).
"""
import argparse
import io
import os
import sys

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(REPO, "android", "app", "src", "main", "res")

# colours from ui/theme/Color.kt and AnswerInk.kt
PAPER, INK = "#F3F2F2", "#201F1D"
AMBER, AMBER_DARK = "#B68235", "#E1AD66"   # accent stroke on paper / on dark chrome
BLUE, BLUE_BRIGHT = "#1D4FB0", "#8CC8FF"   # Ink blue: deep step / bright step
DARK = "#171513"                           # camera chrome


def glyph(font_file, weight, char, cx, cy, height):
    """Outline of [char] as path data, [height] tall, centred on (cx, cy)."""
    font = instantiateVariableFont(TTFont(os.path.join(RES, "font", font_file)), {"wght": weight})
    glyphs = font.getGlyphSet()
    name = font.getBestCmap()[ord(char)]
    bounds = BoundsPen(glyphs)
    glyphs[name].draw(bounds)
    x0, y0, x1, y1 = bounds.bounds
    s = height / (y1 - y0)
    pen = SVGPathPen(glyphs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    # font units are y-up; flip, scale, and move the glyph's box centre to (cx, cy)
    glyphs[name].draw(TransformPen(pen, (s, 0, 0, -s, cx - s * (x0 + x1) / 2, cy + s * (y0 + y1) / 2)))
    return pen.getCommands()


def path(d, stroke=None, width=0.0, fill=None, cap="round", join="round"):
    return {"d": d, "stroke": stroke, "width": width, "fill": fill, "cap": cap, "join": join}


def brackets(h, arm, r):
    """Viewfinder corners of a square of half-side [h]: four L shapes with a rounded elbow."""
    out = []
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        x, y = 54 + sx * h, 54 + sy * h
        out.append("M%g %gL%g %gQ%g %g %g %gL%g %g" % (
            x, y - sy * arm, x, y - sy * r, x, y, x - sx * r, y, x - sx * arm, y))
    return " ".join(out)


def hash_lines(h, gap=0.0, hole=None):
    """
    The four inner lines of a 3 x 3 box of half-side [h], stopping [gap] short of its edge.
    [hole] = (half-width, half-height) of a centred box the lines stay out of (the digit),
    so the themed one-colour icon doesn't run lines through it.
    """
    c = 2 * h / 3
    a, b = 54 - h + gap, 54 + h - gap
    out = []
    for x in (54 - c / 2, 54 + c / 2):
        if hole is None:
            out.append("M%g %gL%g %g M%g %gL%g %g" % (x, a, x, b, a, x, b, x))
        else:
            hw, hh = hole
            out.append("M%g %gL%g %g M%g %gL%g %g" % (x, a, x, 54 - hh, x, 54 + hh, x, b))  # vertical
            out.append("M%g %gL%g %g M%g %gL%g %g" % (a, x, 54 - hw, x, 54 + hw, x, b, x))  # horizontal
    return " ".join(out).replace("%g" % (54 - c / 2), "%.2f" % (54 - c / 2)).replace("%g" % (54 + c / 2), "%.2f" % (54 + c / 2))


def scan(dark=False):
    """A: viewfinder corners around a 3 x 3 box, the answer written in over the lines."""
    ground = DARK if dark else PAPER
    nine = glyph("lora.ttf", 600, "9", 54, 54, 24)
    return ground, [
        path(hash_lines(22, 2.5, hole=(11.5, 15.5)), stroke=PAPER if dark else INK, width=1.7),
        path(nine, fill=BLUE_BRIGHT if dark else BLUE),
        path(brackets(22, 11, 3.5), stroke=AMBER_DARK if dark else AMBER, width=3.2),
    ]


def folio():
    """B: a leaf with a turned corner, a 3 x 3 box on it and one answer."""
    l, t, r, b, f = 35, 29, 73, 79, 11   # page box and fold size
    page = "M%g %gL%g %gL%g %gL%g %gL%g %gZ" % (l, t, r - f, t, r, t + f, r, b, l, b)
    fold = "M%g %gL%g %gL%g %g" % (r - f, t, r - f, t + f, r, t + f)
    g0, g1, gy0 = 41, 67, 46              # 26 wide grid on the page
    c = (g1 - g0) / 3
    grid = "M%g %gh%gv%gh%gZ " % (g0, gy0, g1 - g0, g1 - g0, g0 - g1) + " ".join(
        "M%g %gv%g M%g %gh%g" % (g0 + k * c, gy0, g1 - g0, g0, gy0 + k * c, g1 - g0) for k in (1, 2))
    seven = glyph("lora.ttf", 600, "7", g0 + 1.5 * c, gy0 + 1.5 * c, 6.4)
    return PAPER, [
        path(page, stroke=INK, width=2.4, fill="#FBFAF9"),
        path(fold, stroke=AMBER, width=2.4),
        path(grid, stroke=INK, width=1.2),
        path(seven, fill=BLUE),
    ]


def monogram():
    """C: a Cormorant S in an amber frame, hairline box lines behind it."""
    s = glyph("cormorant_garamond.ttf", 600, "S", 54, 54, 30)
    return PAPER, [
        path(hash_lines(22, 0), stroke="#BAB6B6", width=1.1),
        path(s, stroke=PAPER, width=5),
        path(s, fill=INK),
        path("M32 32h44v44h-44Z", stroke=AMBER, width=2.6),
    ]


DIRECTIONS = {"scan": scan, "scan_dark": lambda: scan(dark=True), "folio": folio, "monogram": monogram}


def svg(ground, paths, window=None, background=True):
    """[window] = (x, y, size) crops the canvas (72-wide launcher window: (18, 18, 72))."""
    x, y, s = window or (0, 0, 108)
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="%g %g %g %g">' % (x, y, s, s)]
    if background:
        out.append('<rect width="108" height="108" fill="%s"/>' % ground)
    for p in paths:
        out.append('<path d="%s" fill="%s" stroke="%s" stroke-width="%g" stroke-linecap="%s" stroke-linejoin="%s"/>' % (
            p["d"], p["fill"] or "none", p["stroke"] or "none", p["width"], p["cap"], p["join"]))
    return "\n".join(out + ["</svg>"]) + "\n"


def vector(paths, tint=None):
    """Android vector drawable, 108 dp canvas. [tint]: every mark in one colour (themed icon)."""
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- Generated by design/icon/make_icon.py: edit the script, not this file. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp"', '    android:height="108dp"',
           '    android:viewportWidth="108"', '    android:viewportHeight="108">']
    for p in paths:
        out.append("    <path")
        if p["fill"]:
            out.append('        android:fillColor="%s"' % (tint or p["fill"]))
        if p["stroke"]:
            out += ['        android:strokeColor="%s"' % (tint or p["stroke"]),
                    '        android:strokeWidth="%g"' % p["width"],
                    '        android:strokeLineCap="%s"' % p["cap"],
                    '        android:strokeLineJoin="%s"' % p["join"]]
        out.append('        android:pathData="%s" />' % p["d"])
    return "\n".join(out + ["</vector>"]) + "\n"


def png(svg_text, size):
    import cairosvg
    from PIL import Image
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg_text.encode(), output_width=size, output_height=size))).convert("RGBA")


def masked(img, shape):
    from PIL import Image, ImageDraw
    n = img.size[0] * 4
    mask = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(mask)
    if shape == "circle":
        d.ellipse((0, 0, n - 1, n - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, n - 1, n - 1), radius=int(n * 0.24), fill=255)
    out = img.copy()
    out.putalpha(mask.resize(img.size, Image.LANCZOS))
    return out


def contact_sheet():
    """Every direction at 192 px and at launcher size (48 dp = 96 px here), on a light and a dark wallpaper."""
    from PIL import Image, ImageDraw
    names = list(DIRECTIONS)
    sheet = Image.new("RGB", (40 + 250 * len(names), 520), "#FFFFFF")
    draw = ImageDraw.Draw(sheet)
    draw.rectangle((0, 260, sheet.size[0], 390), fill="#DDE3EA")
    draw.rectangle((0, 390, sheet.size[0], 520), fill="#22262B")
    for i, name in enumerate(names):
        ground, paths = DIRECTIONS[name]()
        window = svg(ground, paths, window=(18, 18, 72))
        x = 40 + 250 * i
        sheet.paste(masked(png(window, 192), "squircle"), (x, 30), masked(png(window, 192), "squircle"))
        draw.text((x, 232), name, fill="#201F1D")
        for row, y in enumerate((277, 407)):
            for k, (shape, size) in enumerate((("circle", 96), ("squircle", 96))):
                m = masked(png(window, size), shape)
                sheet.paste(m, (x + k * 106, y), m)
    sheet.save(os.path.join(HERE, "directions.png"))


def apply(name):
    """Write the Android resources for [name]."""
    from PIL import Image
    ground, paths = DIRECTIONS[name]()
    # themed icon: one tint for every mark. A ground-coloured halo would become a blob, so drop it.
    halo = [p for p in paths if not (p["stroke"] == ground and p["fill"] is None)]
    files = {
        "drawable/ic_launcher_foreground.xml": vector(paths),
        "drawable/ic_launcher_monochrome.xml": vector(halo, tint="#FF000000"),
        "values/ic_launcher_background.xml": '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <!-- Generated by design/icon/make_icon.py -->\n'
            '    <color name="ic_launcher_background">%s</color>\n</resources>\n' % ground,
        "mipmap-anydpi-v26/ic_launcher.xml": ADAPTIVE,
        "mipmap-anydpi-v26/ic_launcher_round.xml": ADAPTIVE,
    }
    for rel, text in files.items():
        with open(os.path.join(RES, rel), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    old = os.path.join(RES, "drawable", "ic_launcher_background.xml")  # the template's green grid
    if os.path.exists(old):
        os.remove(old)
    # API 24-25 have no adaptive icons: plain bitmaps of the 72-wide window, 48 dp
    window = svg(ground, paths, window=(18, 18, 72))
    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        img = png(window, px)
        masked(img, "squircle").save(os.path.join(RES, "mipmap-" + density, "ic_launcher.webp"), lossless=True)
        masked(img, "circle").save(os.path.join(RES, "mipmap-" + density, "ic_launcher_round.webp"), lossless=True)
    # Play Store listing: 512 x 512, full square (Play applies its own mask), no transparency
    png(window, 512).convert("RGB").save(os.path.join(HERE, "play_store_512.png"))
    print("applied", name)


ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", choices=sorted(DIRECTIONS), help="write the Android resources for this direction")
    args = ap.parse_args()
    for name, make in DIRECTIONS.items():
        ground, paths = make()
        with open(os.path.join(HERE, name + ".svg"), "w", encoding="utf-8", newline="\n") as f:
            f.write(svg(ground, paths))
    contact_sheet()
    if args.apply:
        apply(args.apply)
    sys.exit(0)
