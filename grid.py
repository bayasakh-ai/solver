"""Grid detection, perspective warp, and cell extraction.
Port these functions 1:1 to Kotlin with OpenCV for Android."""
import cv2
import numpy as np

WARP_SIZE = 450  # 9 * 50 px cells
DIGIT_SIZE = 28  # classifier input size


def preprocess(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) if img.ndim == 3 else img
    blur = cv2.GaussianBlur(gray, (7, 7), 0)
    th = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                               cv2.THRESH_BINARY_INV, 11, 2)
    return gray, th


def order_corners(pts):
    """Return corners as top-left, top-right, bottom-right, bottom-left."""
    s = pts.sum(axis=1)
    d = np.diff(pts, axis=1).ravel()  # y - x
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)],
                     pts[np.argmax(s)], pts[np.argmax(d)]], dtype=np.float32)


def find_grid(th, min_area_ratio=0.1):
    """Largest convex 4-corner contour = the puzzle border. None if not found."""
    contours, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    h, w = th.shape
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:5]:
        if cv2.contourArea(c) < min_area_ratio * h * w:
            break
        approx = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(approx) == 4 and cv2.isContourConvex(approx):
            return order_corners(approx.reshape(4, 2).astype(np.float32))
    return None


def warp(gray, corners, size=WARP_SIZE):
    dst = np.array([[0, 0], [size - 1, 0], [size - 1, size - 1], [0, size - 1]],
                   dtype=np.float32)
    M = cv2.getPerspectiveTransform(corners, dst)
    return cv2.warpPerspective(gray, M, (size, size)), M


def clean_cell(cell, out=DIGIT_SIZE):
    """Binary cell (white ink on black) -> centered out x out digit, or None if empty.
    The SAME function is used when generating training data, so inputs match."""
    h, w = cell.shape
    m = int(0.12 * h)  # trim grid lines
    inner = cell[m:h - m, m:w - m]
    n, labels, stats, cents = cv2.connectedComponentsWithStats(inner, connectivity=8)
    ih, iw = inner.shape
    # Keep EVERY piece of the digit, not just the largest one: thin fonts often
    # break into several strokes, and a single stroke looks like a "1".
    keep, largest = [], 0
    for i in range(1, n):
        area = stats[i, cv2.CC_STAT_AREA]
        cx, cy = cents[i]
        if area < 0.01 * ih * iw:
            continue  # noise speck
        if abs(cx - iw / 2) > iw * 0.35 or abs(cy - ih / 2) > ih * 0.35:
            continue  # leftover grid-line fragment near the edge
        keep.append(i)
        largest = max(largest, area)
    if largest < 0.03 * ih * iw:
        return None  # empty cell
    mask = np.isin(labels, keep).astype(np.uint8) * 255
    ys, xs = np.nonzero(mask)
    x, y, bw, bh = xs.min(), ys.min(), xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
    digit = mask[y:y + bh, x:x + bw]
    s = out - 8  # 4 px padding like MNIST
    scale = s / max(bw, bh)
    nw, nh = max(1, int(bw * scale)), max(1, int(bh * scale))
    digit = cv2.resize(digit, (nw, nh), interpolation=cv2.INTER_AREA)
    canvas = np.zeros((out, out), np.uint8)
    ox, oy = (out - nw) // 2, (out - nh) // 2
    canvas[oy:oy + nh, ox:ox + nw] = digit
    return canvas


def extract_cells(warped):
    """9x9 list: each entry is a 28x28 digit image or None (empty)."""
    th = cv2.adaptiveThreshold(warped, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                               cv2.THRESH_BINARY_INV, 11, 2)
    step = warped.shape[0] // 9
    return [[clean_cell(th[r * step:(r + 1) * step, c * step:(c + 1) * step])
             for c in range(9)] for r in range(9)]