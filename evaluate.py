"""Measure how often the pipeline solves puzzles correctly.

  python evaluate.py --synth 20        # 20 synthetic photos per condition
  python evaluate.py photos/           # your real photos (each with a .txt truth)

Real photos: put e.g. news_shadow_01.jpg next to news_shadow_01.txt, where the
.txt holds 9 lines like '. . 5 . 3 1 . 4 .' (the format run.py prints).
Results are grouped by name without the trailing number (news_shadow).
Failures are saved to eval_failures/ so you can look at them."""
import argparse
import glob
import os
import random
import re
import shutil
import time
from collections import defaultdict

import cv2
import numpy as np

from run import process, show

OUTCOMES = ["solved", "wrong_answer", "unsolvable", "too_few_digits", "no_grid"]


def load_truth(path):
    rows = [l.split() for l in open(path) if l.strip()]
    grid = [[0 if v == "." else int(v) for v in row] for row in rows]
    assert len(grid) == 9 and all(len(r) == 9 for r in grid), f"bad truth file {path}"
    return grid


def judge(res, truth_puzzle, truth_solution=None):
    """Classify one result and count cells the model misread (before fixes)."""
    status = res["status"]
    misreads = None
    if "read" in res:
        misreads = sum(res["read"][r][c] != truth_puzzle[r][c] for r in range(9) for c in range(9))
    if status == "solved":
        if truth_solution is None:  # real photo: givens must match, solution then follows
            ok = res["puzzle"] == truth_puzzle
        else:
            ok = res["solution"] == truth_solution
        status = "solved" if ok else "wrong_answer"
    return status, misreads


def report(results):
    width = max(len(k) for k in results) + 2
    print(f"\n{'condition':<{width}} {'solved':>7} {'n':>4}   failures")
    total_ok = total = 0
    for cond, rows in results.items():
        counts = defaultdict(int)
        for status, _, _ in rows:
            counts[status] += 1
        n = len(rows)
        ok = counts["solved"]
        total_ok += ok; total += n
        fails = ", ".join(f"{counts[o]} {o}" for o in OUTCOMES[1:] if counts[o])
        print(f"{cond:<{width}} {ok / n:>7.0%} {n:>4}   {fails}")
    print(f"{'TOTAL':<{width}} {total_ok / total:>7.0%} {total:>4}")
    fixed = [r for rows in results.values() for r in rows if r[0] == "solved" and r[1]]
    if fixed:
        print(f"\n{len(fixed)} solved puzzles had misreads that self-correction fixed")


def save_failure(img, name, status, res, truth):
    os.makedirs("eval_failures", exist_ok=True)
    cv2.imwrite(f"eval_failures/{name}_{status}.jpg", img)
    with open(f"eval_failures/{name}_{status}.txt", "w") as fh:
        fh.write("truth:\n" + show(truth) + "\n")
        if "read" in res:
            fh.write("\nread:\n" + show(res["read"]) + "\n")


def run_synth(model, per_condition, seed):
    from synth import CONDITIONS, make_sample
    from make_digits import load_fonts
    random.seed(seed); np.random.seed(seed)
    fonts = load_fonts()
    results = defaultdict(list)
    for cond in CONDITIONS:
        for i in range(per_condition):
            img, puzzle, solution = make_sample(cond, fonts)
            t = time.time()
            res = process(img, model)
            status, misreads = judge(res, puzzle, solution)
            results[cond].append((status, misreads, time.time() - t))
            if status != "solved":
                save_failure(img, f"{cond}_{i:02d}", status, res, puzzle)
        print(f"  {cond} done")
    return results


def run_real(model, folder):
    results = defaultdict(list)
    images = sorted(f for ext in ("jpg", "jpeg", "png")
                    for f in glob.glob(os.path.join(folder, f"*.{ext}"))
                    if not f.endswith("_solved.jpg"))
    for path in images:
        truth_path = os.path.splitext(path)[0] + ".txt"
        if not os.path.exists(truth_path):
            print(f"  skipping {os.path.basename(path)} (no .txt truth)")
            continue
        truth = load_truth(truth_path)
        img = cv2.imread(path)
        res = process(img, model)
        status, misreads = judge(res, truth)
        name = os.path.splitext(os.path.basename(path))[0]
        group = re.sub(r"[_-]?\d+$", "", name) or name
        results[group].append((status, misreads, 0))
        if status != "solved":
            save_failure(img, name, status, res, truth)
    return results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", nargs="?", help="folder of real photos with .txt truths")
    ap.add_argument("--synth", type=int, help="synthetic photos per condition")
    ap.add_argument("--seed", type=int, default=0, help="same seed = same test images")
    args = ap.parse_args()
    if not args.folder and not args.synth:
        ap.error("give a folder of photos, or --synth N")

    import tensorflow as tf
    model = tf.keras.models.load_model("digits.keras")
    shutil.rmtree("eval_failures", ignore_errors=True)
    results = run_synth(model, args.synth, args.seed) if args.synth else run_real(model, args.folder)
    if not results:
        print("nothing to evaluate")
        return
    report(results)
    if os.path.isdir("eval_failures"):
        print("failures saved in eval_failures/")


if __name__ == "__main__":
    main()
