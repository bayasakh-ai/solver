"""Stage-by-stage look at why a real photo does (or doesn't) solve.

  python inspect_photo.py photos/puzzle1.jpg [more photos ...]

Runs the same process() as run.py / evaluate.py, then compares each stage with the
truth files next to the photo:
  <name>.txt       printed digits (the puzzle), same format evaluate.py uses
  <name>.user.txt  optional: the user's handwritten entries ('.' elsewhere)
Prints: grid found?, polarity, per printed/handwritten cell: right / misread /
missed (seen as empty) / extra (digit read where the truth is empty), confidences,
and — when there are user entries — whether reading only the printed cells solves.
Ink: printed cells kept / pen cells ignored, with the chroma margin of each side to
the thresholds in ink.py (only pieces clean_cell would keep are counted).
Writes eval_failures/<name>_inspect.png:
  photo + found grid | warped grid as read | ignored ink | binarized cells | read vs truth
The ignored-ink panel paints removed user-ink pixels magenta and rings cells with an
unsure piece (the Check screen asks the user about those).
In the last panel wrong cells are shaded and show the true digit small in the
corner; printed truth is bold, handwritten truth thin. No truth file -> the read
grid is shown without judging."""
import os
import sys

import cv2
import numpy as np

from evaluate import load_truth
from grid import binarize, cell_votes, preprocess
from ink import UNSURE_CHROMA, USER, USER_CHROMA, kind
from run import process, self_correct

OUT_DIR = "eval_failures"
H = 450  # panel height


def load_truths(path):
    base = os.path.splitext(path)[0]
    printed = load_truth(base + ".txt") if os.path.exists(base + ".txt") else None
    user = load_truth(base + ".user.txt") if os.path.exists(base + ".user.txt") else None
    return printed, user


def report_cells(read, conf, expected, mask, label):
    """Counts for one kind of cell (printed / handwritten / should-be-empty)."""
    n = int(mask.sum())
    if n == 0:
        return
    if label in ("empty", "pen ignored"):
        extra = mask & (read != 0)
        print(f"  {label:12s} {n:2d} cells: {int(extra.sum())} read as a digit (extra)")
        return
    missed = mask & (read == 0)
    wrong = mask & (read != 0) & (read != expected)
    right = mask & (read == expected)
    mean = lambda m: f"{conf[m].mean():.2f}" if m.any() else "  - "
    print(f"  {label:12s} {n:2d} cells: right {int(right.sum()):2d}, misread {int(wrong.sum()):2d}, "
          f"missed {int(missed.sum())}  | mean confidence right {mean(right)}, wrong {mean(wrong)}")


def report_ink(ink, read, printed_mask, user_mask):
    """Printed-kept / pen-ignored counts and how close each side came to a threshold."""
    if ink is None:
        print("  ink: gray image, no colour to judge")
        return
    if not ink["applied"]:
        print("  ink: removal SKIPPED (safety net: fewer than 17 printed cells would remain)")
    unsure = [f"r{r + 1}c{c + 1}" for r, c in zip(*np.nonzero(ink["unsure"]))]
    print(f"  ink: user ink removed in {int(ink['removed'].sum())} cells; unsure cells "
          f"{len(unsure)}{' ' + ' '.join(unsure) if unsure else ''}")
    if printed_mask is None:
        return
    chroma = lambda mask: [ch for r, c in zip(*np.nonzero(mask))
                           for p, _ in ink["pieces"][r][c] if p.counts for ch in [p.chroma]]
    removed_printed = printed_mask & ink["removed"] & np.array(
        [[any(p.counts and kind(p.chroma) == USER for p, _ in ink["pieces"][r][c]) for c in range(9)]
         for r in range(9)])
    kept = int((printed_mask & (read != 0) & ~removed_printed).sum())
    pc = chroma(printed_mask)
    print(f"    printed cells kept {kept}/{int(printed_mask.sum())}; printed piece chroma max "
          f"{max(pc, default=0):.1f} (margin {UNSURE_CHROMA - max(pc, default=0):.1f} to unsure "
          f"{UNSURE_CHROMA:.0f}, {USER_CHROMA - max(pc, default=0):.1f} to user {USER_CHROMA:.0f})")
    if user_mask.any():
        uc = chroma(user_mask)
        ignored = int((user_mask & (read == 0)).sum())
        print(f"    pen cells ignored {ignored}/{int(user_mask.sum())}; pen piece chroma min "
              f"{min(uc, default=0):.1f} (margin {min(uc, default=0) - USER_CHROMA:.1f} above user "
              f"{USER_CHROMA:.0f})")
    dark = [dk for r, c in zip(*np.nonzero(printed_mask))
            for p, _ in ink["pieces"][r][c] if p.counts for dk in [p.darkness]]
    if dark:
        print(f"    (darkness, not used: printed {min(dark):.2f}-{max(dark):.2f}"
              + (lambda d: f", pen {min(d):.2f}-{max(d):.2f})" if d else ")")(
                  [dk for r, c in zip(*np.nonzero(user_mask))
                   for p, _ in ink["pieces"][r][c] if p.counts for dk in [p.darkness]]))


def ink_panel(warped, ink):
    p = cv2.cvtColor(warped, cv2.COLOR_GRAY2BGR)
    if ink is None:
        return p
    p[ink["mask"]] = (255, 0, 255)
    for r, c in zip(*np.nonzero(ink["unsure"])):
        cv2.circle(p, (c * 50 + 25, r * 50 + 25), 22, (0, 140, 255), 2)
    if not ink["applied"]:
        cv2.putText(p, "skipped", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 2)
    return p


def panel(img, height=H):
    if img.ndim == 2:
        img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    return cv2.resize(img, (max(1, int(img.shape[1] * height / img.shape[0])), height))


def board_panel(read, expected, printed_mask, user_mask):
    b = np.full((H, H, 3), 255, np.uint8)
    for i in range(10):
        t = 3 if i % 3 == 0 else 1
        cv2.line(b, (i * 50, 0), (i * 50, H), (0, 0, 0), t)
        cv2.line(b, (0, i * 50), (H, i * 50), (0, 0, 0), t)
    for r in range(9):
        for c in range(9):
            x, y = c * 50, r * 50
            ok = expected is None or read[r, c] == expected[r, c]
            if user_mask[r, c] and expected is not None and expected[r, c] == 0:
                ok = read[r, c] == 0  # ignored pen cell: right when nothing was read
            if not ok:
                cv2.rectangle(b, (x + 2, y + 2), (x + 48, y + 48), (180, 180, 255), -1)
            txt = str(read[r, c]) if read[r, c] else ("-" if not ok else "")
            bold = 2 if printed_mask[r, c] else 1
            cv2.putText(b, txt, (x + 15, y + 36), cv2.FONT_HERSHEY_SIMPLEX, 1.0,
                        (0, 0, 0) if ok else (0, 0, 200), bold, cv2.LINE_AA)
            if not ok:
                truth_txt = str(expected[r, c]) if expected[r, c] else "."
                cv2.putText(b, truth_txt, (x + 36, y + 14), cv2.FONT_HERSHEY_SIMPLEX,
                            0.45, (0, 120, 0), 1, cv2.LINE_AA)
    return b


def inspect(path, model):
    name = os.path.splitext(os.path.basename(path))[0]
    img = cv2.imread(path)
    if img is None:
        print(f"{path}: can't open")
        return
    printed_t, user_t = load_truths(path)
    print(f"\n== {name}  ({img.shape[1]}x{img.shape[0]}, median {int(np.median(img))})")
    res = process(img, model)
    gap = np.full((H, 10, 3), 255, np.uint8)
    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, f"{name}_inspect.png")

    if "corners" not in res:  # no grid: show the thresholded image the search ran on
        print(f"  grid: NOT FOUND (both polarities) -> status {res['status']}")
        _, th = preprocess(img)
        cv2.imwrite(out_path, np.hstack([panel(img), gap, panel(th)]))
        print(f"  saved {out_path}")
        return

    corners = res["corners"]
    print(f"  grid: found in the {'inverted' if res['inverted'] else 'normal'} image, corners "
          f"{np.round(corners).astype(int).tolist()}")
    light, dark = cell_votes(res["warped"])  # warped is already the polarity that was read
    print(f"  cells read dark-on-light (vote after any inversion: light {light}, dark {dark})")

    read = np.array(res["read"])
    conf = np.zeros((9, 9))
    for (r, c), p in res["probs"].items():
        conf[r, c] = p.max()

    expected = printed_mask = user_mask = None
    if printed_t is not None:
        printed_arr = np.array(printed_t)
        user_arr = np.array(user_t) if user_t is not None else np.zeros((9, 9), int)
        printed_mask, user_mask = printed_arr > 0, user_arr > 0
        # user ink removed: pen cells should read empty; otherwise they should read the pen digit
        ignoring = res.get("ink") is not None and res["ink"]["applied"]
        expected = printed_arr if ignoring else np.where(printed_mask, printed_arr, user_arr)
        report_cells(read, conf, expected, printed_mask, "printed")
        report_cells(read, conf, expected, user_mask, "pen ignored" if ignoring else "handwritten")
        report_cells(read, conf, expected, ~(printed_mask | user_mask), "empty")
        for r in range(9):
            for c in range(9):
                if read[r, c] != expected[r, c]:
                    kind = "printed" if printed_mask[r, c] else "pen" if user_mask[r, c] else "empty"
                    want = expected[r, c] or "empty"
                    got = f"{read[r, c]} ({conf[r, c]:.0%})" if read[r, c] else "empty"
                    print(f"    r{r + 1}c{c + 1} ({kind}) truth {want}, read {got}")
    else:
        printed_mask = user_mask = np.zeros((9, 9), bool)
        print("  no truth file: showing the reading only")

    report_ink(res.get("ink"), read, printed_mask if printed_t is not None else None, user_mask)

    errors = "" if expected is None else f", reading differs from truth in {int((read != expected).sum())} cells"
    fixes = res.get("fixes") or []
    print(f"  status: {res['status']}{errors}, self-correction fixes: {len(fixes)} (max 2)")

    if user_t is not None:  # what the handwriting feature would give us
        only = [[int(read[r, c]) if printed_mask[r, c] else 0 for c in range(9)] for r in range(9)]
        probs = {rc: p for rc, p in res["probs"].items() if printed_mask[rc]}
        _, sol, _ = self_correct(only, probs)
        full = np.where(printed_mask, np.array(printed_t), np.array(user_t))
        print(f"  reading printed cells only -> solved: {sol is not None}; "
              f"matches printed+user truth: {sol is not None and np.array_equal(np.array(sol), full)}")

    photo = img.copy()
    cv2.polylines(photo, [corners.astype(np.int32)], True, (0, 200, 0), max(2, img.shape[0] // 200))
    sheet = np.hstack([panel(photo), gap, panel(res["warped"]), gap,
                       panel(ink_panel(res["warped"], res.get("ink"))), gap, panel(binarize(res["warped"])),
                       gap, board_panel(read, expected, printed_mask, user_mask)])
    cv2.imwrite(out_path, sheet)
    print(f"  saved {out_path}")


def main(paths):
    if not paths:
        sys.exit(__doc__)
    import tensorflow as tf
    model = tf.keras.models.load_model("digits.keras")
    for p in paths:
        inspect(p, model)


if __name__ == "__main__":
    main(sys.argv[1:])
