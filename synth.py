"""Synthetic sudoku photos with known answers, under controlled conditions.
Used by evaluate.py --synth. Each condition stresses one weakness."""
import random

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from solver import solve

CONDITIONS = ["clean", "tilt", "rotate", "shadow", "glare", "blur",
              "low_res", "jpeg", "dark_mode", "touching_lines"]


# ---------- puzzle generation ----------

def random_puzzle(target_givens=28):
    """Random puzzle with a unique solution. Returns (puzzle, solution)."""
    g = [[0] * 9 for _ in range(9)]
    for b in range(3):  # diagonal boxes are independent: fill them randomly
        digits = random.sample(range(1, 10), 9)
        for k in range(9):
            g[b * 3 + k // 3][b * 3 + k % 3] = digits[k]
    solution = solve(g, limit=1)[0]
    puzzle = [row[:] for row in solution]
    cells = [(r, c) for r in range(9) for c in range(9)]
    random.shuffle(cells)
    givens = 81
    for r, c in cells:
        if givens <= target_givens:
            break
        keep = puzzle[r][c]
        puzzle[r][c] = 0
        if len(solve(puzzle, limit=2)) != 1:
            puzzle[r][c] = keep  # removing it breaks uniqueness
        else:
            givens -= 1
    return puzzle, solution


# ---------- rendering ----------

def render_board(puzzle, font_path, size=540):
    cell = size // 9
    img = Image.new("L", (size + 1, size + 1), 255)
    d = ImageDraw.Draw(img)
    for i in range(10):
        w = 4 if i % 3 == 0 else 1
        d.line([(i * cell, 0), (i * cell, size)], fill=0, width=w)
        d.line([(0, i * cell), (size, i * cell)], fill=0, width=w)
    font = ImageFont.truetype(font_path, int(cell * random.uniform(0.6, 0.75)))
    for r in range(9):
        for c in range(9):
            if puzzle[r][c]:
                t = str(puzzle[r][c])
                x0, y0, x1, y1 = d.textbbox((0, 0), t, font=font)
                d.text((c * cell + (cell - (x1 - x0)) / 2 - x0,
                        r * cell + (cell - (y1 - y0)) / 2 - y0), t, fill=0, font=font)
    return np.array(img)


def place_on_page(board, canvas=900, tilt=0.06, angle=0.0, touching=False):
    """Put the board on a page with some text-like clutter, then apply a
    random perspective (tilt = max corner jitter as a fraction of canvas)."""
    page = np.full((canvas, canvas), random.randint(225, 250), np.uint8)
    b = board.shape[0]
    off = (canvas - b) // 2
    for _ in range(25):  # fake text lines around (never over) the grid
        y, x = random.randint(0, canvas - 10), random.randint(0, canvas - 200)
        x2 = x + random.randint(60, 200)
        if off - 15 < y < off + b + 15 and x2 > off - 15 and x < off + b + 15:
            continue
        cv2.line(page, (x, y), (x2, y), 90, 3)
    region = page[off:off + b, off:off + b]
    page[off:off + b, off:off + b] = np.minimum(region, board)
    if touching:  # a page rule running along the left border, plus one under it
        cv2.line(page, (off, off - 80), (off, off + b + 120), 0, 2)
        cv2.line(page, (off - 60, off + b), (off + b + 60, off + b), 0, 2)
        page[off + b + 10:off + b + 80, off:off + 300] = 60  # text block touching
    corners = np.float32([[off, off], [off + b, off], [off + b, off + b], [off, off + b]])
    c = canvas / 2
    rot = cv2.getRotationMatrix2D((c, c), angle, 1.0)
    corners_rot = cv2.transform(corners[None], rot)[0]
    jitter = np.random.uniform(-tilt, tilt, (4, 2)).astype(np.float32) * canvas
    src = np.float32([[0, 0], [canvas, 0], [canvas, canvas], [0, canvas]])
    full = cv2.getPerspectiveTransform(src, src + jitter) @ np.vstack([rot, [0, 0, 1]])
    out = cv2.warpPerspective(page, full, (canvas, canvas), borderValue=200)
    return out


def apply_condition(img, cond):
    h, w = img.shape
    f = img.astype(np.float32)
    if cond == "shadow":
        x0 = random.uniform(0.2, 0.8) * w
        xs = np.arange(w)[None, :]
        f *= np.clip(0.35 + 0.65 / (1 + np.exp(-(xs - x0) / 25)), 0, 1)  # hard shadow edge
        f *= np.linspace(random.uniform(0.5, 0.8), 1, h)[:, None]           # dim gradient
    elif cond == "glare":
        cx, cy = random.uniform(0.3, 0.7) * w, random.uniform(0.3, 0.7) * h
        yy, xx = np.mgrid[0:h, 0:w]
        blob = np.exp(-((xx - cx) ** 2 + (yy - cy) ** 2) / (2 * (0.12 * w) ** 2))
        f = f + blob * 200 * (1 - f / 255)
    elif cond == "blur":
        k = random.choice([5, 7, 9])
        kernel = np.zeros((k, k), np.float32)
        kernel[k // 2, :] = 1 / k  # horizontal motion blur
        f = cv2.filter2D(f, -1, kernel)
    elif cond == "low_res":
        s = random.uniform(0.25, 0.35)
        f = cv2.resize(cv2.resize(f, None, fx=s, fy=s, interpolation=cv2.INTER_AREA),
                       (w, h), interpolation=cv2.INTER_LINEAR)
    elif cond == "dark_mode":
        f = 255 - f
    f = np.clip(f + np.random.normal(0, 4, f.shape), 0, 255).astype(np.uint8)
    if cond == "jpeg":
        q = random.randint(8, 20)
        f = cv2.imdecode(cv2.imencode(".jpg", f, [cv2.IMWRITE_JPEG_QUALITY, q])[1], 0)
    else:
        f = cv2.imdecode(cv2.imencode(".jpg", f, [cv2.IMWRITE_JPEG_QUALITY, 85])[1], 0)
    return cv2.cvtColor(f, cv2.COLOR_GRAY2BGR)


def make_sample(cond, fonts):
    puzzle, solution = random_puzzle(random.randint(24, 32))
    board = render_board(puzzle, random.choice(fonts))
    tilt = 0.12 if cond == "tilt" else 0.04
    angle = random.uniform(-25, 25) if cond == "rotate" else random.uniform(-3, 3)
    page = place_on_page(board, tilt=tilt, angle=angle, touching=(cond == "touching_lines"))
    return apply_condition(page, cond), puzzle, solution
