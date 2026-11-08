"""Photo -> detect -> read -> solve (with self-correction) -> overlay answers.
Usage: python run.py puzzle.jpg   (writes puzzle_solved.jpg)"""
import itertools
import sys

import cv2
import numpy as np

from grid import preprocess, find_grid, warp, extract_cells, chroma_planes, looks_dark, WARP_SIZE
from solver import solve, is_plausible


def read_digits(cells, model):
    """Returns the grid plus each read cell's 9 class probabilities."""
    grid = [[0] * 9 for _ in range(9)]
    probs = {}
    batch, pos = [], []
    for r in range(9):
        for c in range(9):
            if cells[r][c] is not None:
                batch.append(cells[r][c]); pos.append((r, c))
    if batch:
        out = model(np.array(batch, "float32")[..., None] / 255.0, training=False).numpy()
        for (r, c), p in zip(pos, out):
            grid[r][c] = int(np.argmax(p)) + 1
            probs[(r, c)] = p
    return grid, probs


def options_for(p, k=3):
    """Alternative readings for one cell, best first: (digit, probability).
    Digit 0 = 'not a digit after all' (a speck read as a digit)."""
    order = np.argsort(p)[::-1]
    alts = [(int(i) + 1, float(p[i])) for i in order[1:k + 1]]
    return alts + [(0, 0.01)]


def self_correct(grid, probs, max_changes=2, suspects=8):
    """If the grid has no unique solution, retry with the model's next-best
    readings for the least confident cells. Picks the most probable fix."""
    sols = solve(grid)
    if len(sols) == 1:
        return grid, sols[0], []
    weakest = sorted(probs, key=lambda rc: probs[rc].max())[:suspects]
    for n in range(1, max_changes + 1):
        best = None
        for cells in itertools.combinations(weakest, n):
            for choice in itertools.product(*(options_for(probs[rc]) for rc in cells)):
                g = [row[:] for row in grid]
                for (r, c), (digit, _) in zip(cells, choice):
                    g[r][c] = digit
                s = solve(g)
                if len(s) == 1:
                    score = np.prod([pr for _, pr in choice])
                    if best is None or score > best[0]:
                        best = (score, g, s[0], list(zip(cells, choice)))
        if best:
            _, g, sol, changes = best
            fixes = [(r, c, grid[r][c], probs[(r, c)].max(), d, pr)
                     for (r, c), (d, pr) in changes]
            return g, sol, fixes
    return grid, None, []


def draw_solution(img, M, puzzle, solution):
    step = WARP_SIZE // 9
    layer = np.zeros((WARP_SIZE, WARP_SIZE, 3), np.uint8)
    for r in range(9):
        for c in range(9):
            if puzzle[r][c] == 0:
                cv2.putText(layer, str(solution[r][c]), (c * step + 14, r * step + 38),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.1, (0, 160, 0), 2, cv2.LINE_AA)
    h, w = img.shape[:2]
    back = cv2.warpPerspective(layer, np.linalg.inv(M), (w, h))  # unwarp onto photo
    mask = back.any(axis=2)
    out = img.copy()
    out[mask] = back[mask]
    return out


def show(grid):
    return "\n".join(" ".join(str(v or ".") for v in row) for row in grid)


def process(img, model):
    """Full pipeline on one BGR image. Returns a dict; 'status' is one of:
    solved, no_grid, too_few_digits, unsolvable.
    Dark mode: the grid is searched in the image, then in its inversion if that
    fails to solve (dark-looking images try the inverted one first). Either way
    looks_dark() on the warped grid makes the cells dark-on-light before reading.
    The Android app does the same per frame, one grid search per frame
    (GridPolarity in Polarity.kt)."""
    inverted = 255 - img
    attempts = [img, inverted] if np.median(img) >= 110 else [inverted, img]
    first = None
    for i, attempt in enumerate(attempts):
        res = _process_once(attempt, model)
        res["inverted"] = attempt is inverted
        if res["status"] == "solved":
            return res
        if first is None:
            first = res
    return first  # neither worked: report the more likely polarity


def _process_once(img, model):
    gray, th = preprocess(img)
    corners = find_grid(th)
    if corners is None:
        return {"status": "no_grid"}
    warped, M = warp(gray, corners)
    if looks_dark(warped):  # the cells decide, not the whole photo (shadows fool that)
        warped = 255 - warped
    # user ink (coloured pen) is removed before reading: solve from printed digits only
    cells, ink = extract_cells(warped, chroma_planes(img, corners))
    read, probs = read_digits(cells, model)
    # corners/warped/probs/ink are only for inspect_photo.py: it shows what was really read
    result = {"read": read, "M": M, "corners": corners, "warped": warped, "probs": probs,
              "ink": ink}
    if not is_plausible(read):
        return {**result, "status": "too_few_digits"}
    puzzle, solution, fixes = self_correct(read, probs)
    result.update(puzzle=puzzle, solution=solution, fixes=fixes)
    result["status"] = "solved" if solution is not None else "unsolvable"
    return result


def main(path):
    import tensorflow as tf
    model = tf.keras.models.load_model("digits.keras")
    img = cv2.imread(path)
    if img is None:
        sys.exit(f"Can't open {path}")
    res = process(img, model)
    if res["status"] == "no_grid":
        sys.exit("No grid found")
    print(show(res["read"]))
    if res["status"] == "too_few_digits":
        sys.exit("Fewer than 17 digits read - check the photo")
    for r, c, old, old_p, new, new_p in res["fixes"]:
        new_txt = new if new else "empty"
        print(f"Fixed row {r + 1} col {c + 1}: read {old} ({old_p:.0%}) -> {new_txt} ({new_p:.0%})")
    if res["status"] == "unsolvable":
        sys.exit("No solution - couldn't fix the reading automatically")
    out_path = path.rsplit(".", 1)[0] + "_solved.jpg"
    cv2.imwrite(out_path, draw_solution(img, res["M"], res["puzzle"], res["solution"]))
    print("saved", out_path)


if __name__ == "__main__":
    main(sys.argv[1])
