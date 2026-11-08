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
    """Find the puzzle's 4 corners. Tries the fast outer-border method first,
    then falls back to locating the grid from its cells."""
    corners = _find_by_border(th, min_area_ratio)
    if corners is None:
        rough = _find_by_cells(th)
        if rough is not None:
            corners = _refine_edges(th, order_corners(rough.astype(np.float32)))
    return corners


def _refine_edges(th, rough, pad=40, band=30):
    """Snap a rough quad onto the real border lines. Warp with the rough
    corners, then for each edge find the strongest line near where the border
    should be, separately in its two halves (so tilted edges are handled).
    Edges are intersected and mapped back to the photo."""
    S = WARP_SIZE
    dst = np.array([[pad, pad], [pad + S, pad], [pad + S, pad + S], [pad, pad + S]], np.float32)
    M = cv2.getPerspectiveTransform(rough, dst)
    w = cv2.warpPerspective(th, M, (S + 2 * pad, S + 2 * pad))
    halves = [(pad + S // 8, pad + S // 2), (pad + S // 2, pad + 7 * S // 8)]

    def strongest(profile, nominal):
        lo = nominal - band
        return lo + int(np.argmax(profile[lo:nominal + band]))

    lines = {}
    for name, nominal in (("left", pad), ("right", pad + S), ("top", pad), ("bottom", pad + S)):
        pts = []
        for a, b in halves:
            if name in ("left", "right"):
                x = strongest(w[a:b].sum(axis=0), nominal)
                pts.append((x, (a + b) / 2))
            else:
                y = strongest(w[:, a:b].sum(axis=1), nominal)
                pts.append(((a + b) / 2, y))
        lines[name] = pts

    def intersect(l1, l2):
        (x1, y1), (x2, y2) = l1
        (x3, y3), (x4, y4) = l2
        d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        a, b = x1 * y2 - y1 * x2, x3 * y4 - y3 * x4
        return ((a * (x3 - x4) - (x1 - x2) * b) / d, (a * (y3 - y4) - (y1 - y2) * b) / d)

    quad = np.array([[intersect(lines["top"], lines["left"])],
                     [intersect(lines["top"], lines["right"])],
                     [intersect(lines["bottom"], lines["right"])],
                     [intersect(lines["bottom"], lines["left"])]], np.float32)
    return cv2.perspectiveTransform(quad, np.linalg.inv(M)).reshape(4, 2)


def _find_by_border(th, min_area_ratio):
    """Largest convex 4-corner contour = the puzzle border. Fails when a page
    rule or text touches the border, because the shapes merge into one."""
    contours, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    h, w = th.shape
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:5]:
        if cv2.contourArea(c) < min_area_ratio * h * w:
            break
        approx = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(approx) == 4 and cv2.isContourConvex(approx):
            return order_corners(approx.reshape(4, 2).astype(np.float32))
    return None


def _find_by_cells(th, min_cells=40):
    """Find the grid from its cells: the inside of every cell is a small
    square hole. The biggest group of same-sized squares is the puzzle, and
    its outline gives the corners. Lines touching the grid don't matter here."""
    # thicken lines a little so tiny gaps in thin grid lines don't merge cells
    closed = cv2.dilate(th, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(closed, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    h, w = th.shape
    squares = []
    for c in contours:
        area = cv2.contourArea(c)
        if area < 0.0004 * h * w or area > 0.05 * h * w:
            continue
        approx = cv2.approxPolyDP(c, 0.1 * cv2.arcLength(c, True), True)
        x, y, bw, bh = cv2.boundingRect(c)
        if len(approx) == 4 and 0.6 < bw / bh < 1.6 and area > 0.6 * bw * bh:
            squares.append((area, c))
    if len(squares) < min_cells:
        return None
    areas = np.array([a for a, _ in squares])
    # area with the most similar-sized squares around it = the puzzle's cell size
    counts = [((areas > 0.6 * a) & (areas < 1.6 * a)).sum() for a in areas]
    ref = areas[int(np.argmax(counts))]
    cells = [c for a, c in squares if 0.6 * ref < a < 1.6 * ref]
    if len(cells) < min_cells:
        return None
    # the puzzle's cells sit together; drop stray squares far from the main cluster
    centers = np.array([c.reshape(-1, 2).mean(axis=0) for c in cells])
    med = np.median(centers, axis=0)
    dist = np.linalg.norm(centers - med, axis=1)
    cell_size = np.sqrt(ref)
    cells = [c for c, d in zip(cells, dist) if d < 7 * cell_size]
    if len(cells) < min_cells:
        return None
    return _fit_lattice(cells, cell_size)


def _fit_lattice(cells, s):
    """Give each found cell its (column, row) by walking from neighbour to
    neighbour, then fit a homography lattice -> image. This recovers the 4
    corners even when the corner cells themselves weren't found."""
    from collections import deque
    pts = np.array([c.reshape(-1, 2).mean(axis=0) for c in cells], np.float32)
    n = len(pts)
    start = int(np.argmin(np.linalg.norm(pts - np.median(pts, axis=0), axis=1)))
    idx = {start: (0, 0)}
    queue = deque([start])
    while queue:
        k = queue.popleft()
        d = pts - pts[k]
        dist = np.linalg.norm(d, axis=1)
        for m in np.where((dist > 0.6 * s) & (dist < 1.3 * s))[0]:
            if m in idx:
                continue
            dx, dy = d[m]
            i, j = idx[k]
            idx[m] = (i + int(np.sign(dx)), j) if abs(dx) > abs(dy) else (i, j + int(np.sign(dy)))
            queue.append(m)
    ij = np.array([idx[k] for k in idx], np.float32)
    ij -= ij.min(axis=0)
    if ij[:, 0].max() != 8 or ij[:, 1].max() != 8:
        return None  # couldn't see a full 9x9 span
    H, _ = cv2.findHomography(ij + 0.5, pts[list(idx)], cv2.RANSAC, 0.3 * s)
    if H is None:
        return None
    lattice_corners = np.array([[[0, 0]], [[9, 0]], [[9, 9]], [[0, 9]]], np.float32)
    return cv2.perspectiveTransform(lattice_corners, H).reshape(4, 2)


def warp(gray, corners, size=WARP_SIZE):
    dst = np.array([[0, 0], [size - 1, 0], [size - 1, size - 1], [0, size - 1]],
                   dtype=np.float32)
    M = cv2.getPerspectiveTransform(corners, dst)
    return cv2.warpPerspective(gray, M, (size, size)), M


def looks_dark(warped):
    """Dark mode check on the flattened grid: light digits on a dark background?
    Each cell votes on its own: is its ink the dark tail of its pixels (paper) or
    the bright tail (dark mode)? Per cell because a hard shadow makes half a paper
    page darker than 110, which fooled the old whole-image median rule.
    Mirrored 1:1 by Polarity.looksDark in Kotlin (same 'lower' percentile rule);
    export_polarity_cases.py + PolarityParityTest check they agree."""
    light, dark = cell_votes(warped)
    return dark > light


def cell_votes(warped, margin=5, min_spread=20):
    """(cells with dark ink on light, cells with light ink on dark). Flat cells don't vote."""
    step = warped.shape[0] // 9
    light = dark = 0
    for r in range(9):
        for c in range(9):
            cell = warped[r * step + margin:(r + 1) * step - margin,
                          c * step + margin:(c + 1) * step - margin]
            p2, med, p98 = np.percentile(cell, [2, 50, 98], method="lower")
            below, above = int(med) - int(p2), int(p98) - int(med)
            if max(below, above) < min_spread:
                continue  # flat (empty) cell: no evidence
            if below > above:
                light += 1
            else:
                dark += 1
    return light, dark


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


def binarize(gray_img):
    """Ink -> white, paper -> black. C=10 ignores paper texture and shading;
    the old C=2 turned paper grain in empty cells into fake digits.
    Used for BOTH real cells and training data, so they always match."""
    return cv2.adaptiveThreshold(gray_img, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                 cv2.THRESH_BINARY_INV, 21, 10)


def extract_cells(warped):
    """9x9 list: each entry is a 28x28 digit image or None (empty)."""
    th = binarize(warped)
    step = warped.shape[0] // 9
    return [[clean_cell(th[r * step:(r + 1) * step, c * step:(c + 1) * step])
             for c in range(9)] for r in range(9)]
