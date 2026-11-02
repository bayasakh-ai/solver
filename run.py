"""Photo -> detect -> read -> solve -> overlay answers.
Usage: python run.py puzzle.jpg   (writes puzzle_solved.jpg)"""
import sys

import cv2
import numpy as np

from grid import preprocess, find_grid, warp, extract_cells, WARP_SIZE
from solver import solve, is_plausible


def read_digits(cells, model):
    grid = [[0] * 9 for _ in range(9)]
    batch, pos = [], []
    for r in range(9):
        for c in range(9):
            if cells[r][c] is not None:
                batch.append(cells[r][c]); pos.append((r, c))
    if batch:
        probs = model.predict(np.array(batch, "float32")[..., None] / 255.0, verbose=0)
        for (r, c), p in zip(pos, probs):
            grid[r][c] = int(np.argmax(p)) + 1
    return grid


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


def main(path):
    import tensorflow as tf
    model = tf.keras.models.load_model("digits.keras")
    img = cv2.imread(path)
    gray, th = preprocess(img)
    corners = find_grid(th)
    if corners is None:
        sys.exit("No grid found")
    warped, M = warp(gray, corners)
    puzzle = read_digits(extract_cells(warped), model)
    print("\n".join(" ".join(str(v or ".") for v in row) for row in puzzle))
    if not is_plausible(puzzle):
        sys.exit("Fewer than 17 digits read - check the photo")
    sols = solve(puzzle)
    if not sols:
        sys.exit("No solution - a digit was probably misread")
    if len(sols) > 1:
        print("Warning: multiple solutions - a digit may be missing")
    out_path = path.rsplit(".", 1)[0] + "_solved.jpg"
    cv2.imwrite(out_path, draw_solution(img, M, puzzle, sols[0]))
    print("saved", out_path)


if __name__ == "__main__":
    main(sys.argv[1])
