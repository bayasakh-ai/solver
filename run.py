"""Photo -> detect -> read -> solve (with self-correction) -> overlay answers.
Usage: python run.py puzzle.jpg   (writes puzzle_solved.jpg)"""
import itertools
import sys

import cv2
import numpy as np

import hand
from grid import cell_shapes, descreen, preprocess, find_grid, grid_accept, warp, extract_cells, chroma_planes, looks_dark, cell_backgrounds, WARP_SIZE
from solver import solve, is_plausible
from orientation import is_consistent, turn_score, choose_turn, roll_corners


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


HAND = True        # tell handwritten digits from printed ones (hand.py); False: before/after
_hand_model = None


def hand_model():
    """The printed-vs-handwritten shape model (train_hand.py), loaded once; None if absent."""
    global _hand_model
    if _hand_model is None:
        import os
        import tensorflow as tf
        _hand_model = tf.keras.models.load_model("hand.keras") if os.path.exists("hand.keras") else False
    return _hand_model or None


_hand_reader = None


def hand_reader():
    """The reader for handwritten digits (train_hand_digits.py), loaded once; None if
    absent: handwritten digits are then read by the print model."""
    global _hand_reader
    if _hand_reader is None:
        import os
        import tensorflow as tf
        _hand_reader = (tf.keras.models.load_model("hand_digits.keras")
                        if os.path.exists("hand_digits.keras") else False)
    return _hand_reader or None


def read_hand(cells, kinds):
    """{(r, c): digit} for the cells called handwritten, read by the handwriting reader.
    Empty without the reader."""
    pos = [p for p, k in kinds.items() if k == hand.HAND]
    reader = hand_reader()
    if not pos or reader is None:
        return {}
    out = reader(np.array([cells[r][c] for r, c in pos], "float32")[..., None] / 255.0,
                 training=False).numpy()
    return {p: int(np.argmax(o)) + 1 for p, o in zip(pos, out)}


def split_hand(cells, warped):
    """({(r, c): hand.PRINTED / HAND / UNSURE}, {(r, c): P(handwritten)}) for the filled
    cells, or ({}, {}) when switched off or the shape model is missing. Decision rules
    in hand.py."""
    model = hand_model() if HAND else None
    shapes = cell_shapes(warped) if model is not None else None
    pos = [(r, c) for r in range(9) for c in range(9)
           if cells[r][c] is not None and shapes is not None and shapes[r][c] is not None]
    if not pos:
        return {}, {}
    probs = model(np.array([cells[r][c] for r, c in pos], "float32")[..., None] / 255.0,
                  training=False).numpy()[:, 0]
    kinds = hand.classify([hand.Shape(*shapes[r][c]) for r, c in pos], [float(p) for p in probs])
    return dict(zip(pos, kinds)), dict(zip(pos, (float(p) for p in probs)))


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


def process(img, model, find_scale=1.0):
    """Full pipeline on one BGR image. Returns a dict; 'status' is one of:
    solved, no_grid, too_few_digits, unsolvable.
    Dark mode: the grid is searched in the image, then in its inversion if that
    fails to solve (dark-looking images try the inverted one first). Either way
    looks_dark() on the warped grid makes the cells dark-on-light before reading.
    The Android app does the same per frame, one grid search per frame
    (GridPolarity in Polarity.kt).
    find_scale < 1: find the grid on a smaller copy (the cheap part the app does every
    frame), then warp and read digits from the full-size image (measurement option)."""
    inverted = 255 - img
    attempts = [img, inverted] if np.median(img) >= 110 else [inverted, img]
    first = None
    for i, attempt in enumerate(attempts):
        res = _process_once(attempt, model, searched_inverted=attempt is inverted, find_scale=find_scale)
        res["inverted"] = attempt is inverted
        if res["status"] == "solved":
            return res
        if first is None:
            first = res
    return first  # neither worked: report the more likely polarity


def orient(warped, model):
    """Quarter turns (np.rot90) that make the dark-on-light warp upright: read each
    turn without the chroma step, pick by confidence checked by sudoku logic."""
    scores, consistent = [], []
    for k in range(4):
        cells, _ = extract_cells(np.ascontiguousarray(np.rot90(warped, k)), None)
        read, probs = read_digits(cells, model)
        scores.append(turn_score(probs))
        consistent.append(is_consistent(read))
    return choose_turn(scores, consistent)


def _process_once(img, model, searched_inverted=False, find_scale=1.0):
    gray, th = preprocess(img)
    if find_scale < 1:
        small = cv2.resize(img, None, fx=find_scale, fy=find_scale, interpolation=cv2.INTER_AREA)
        corners = find_grid(preprocess(small)[1],
                            lambda c: grid_accept(gray)((c / find_scale).astype(np.float32)))
        if corners is not None:
            corners = (corners / find_scale).astype(np.float32)
    else:
        corners = find_grid(th, grid_accept(gray))
    if corners is None:
        return {"status": "no_grid"}
    warped, M = warp(gray, corners)
    warped = descreen(warped)
    dark = looks_dark(warped)  # a quarter turn doesn't change the per-cell vote
    if dark:  # the cells decide, not the whole photo (shadows fool that)
        warped = 255 - warped
    screen = dark != searched_inverted  # dark page in the CAMERA's view: answers default bright
    # which way is up: the reading decides (orientation.py); relabel the corners to match
    k = orient(warped, model)
    if k:
        corners = roll_corners(corners, k)
        warped = np.ascontiguousarray(np.rot90(warped, k))
        M = cv2.getPerspectiveTransform(corners, np.float32(
            [[0, 0], [WARP_SIZE - 1, 0], [WARP_SIZE - 1, WARP_SIZE - 1], [0, WARP_SIZE - 1]]))
    # background behind each cell in CAMERA polarity, for the answer colour step.
    # warped is dark-on-light now: camera = warped XOR screen. Median first, then map,
    # exactly like CellBackground.medians(pixels, inverted = screen). Not used for reading.
    backgrounds = cell_backgrounds(warped)
    if screen:
        backgrounds = [255 - v for v in backgrounds]
    # user ink (coloured pen) is removed before reading: solve from printed digits only
    cells, ink = extract_cells(warped, chroma_planes(img, corners))
    read, probs = read_digits(cells, model)
    # handwritten digits (pencil, black pen) leave the puzzle: it is solved from printed
    # digits only. They are kept as the player's entries ("user"), read by the
    # handwriting reader as they are and never corrected. Unsure cells stay in the
    # puzzle; Check asks about them.
    kinds, hand_probs = split_hand(cells, warped)
    written = read_hand(cells, kinds)
    user = [[0] * 9 for _ in range(9)]
    for (r, c), k in kinds.items():
        if k == hand.HAND:
            user[r][c], read[r][c] = written.get((r, c), read[r][c]), 0
            del probs[(r, c)]
    # corners/warped/probs/ink are only for inspect_photo.py: it shows what was really read
    result = {"read": read, "M": M, "corners": corners, "warped": warped, "probs": probs,
              "ink": ink, "backgrounds": backgrounds, "screen": screen, "user": user, "kinds": kinds,
              "hand_probs": hand_probs}
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
