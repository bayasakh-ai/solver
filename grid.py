"""Grid detection, perspective warp, and cell extraction.
Port these functions 1:1 to Kotlin with OpenCV for Android."""
import cv2
import numpy as np

from ink import Piece, USER, cell_unsure, kind, neutral_distance, use_removal

WARP_SIZE = 450  # 9 * 50 px cells
DIGIT_SIZE = 28  # classifier input size
MIN_GRID_AREA = 0.1    # border method: grid outline >= this share of the frame
HULL_FALLBACK = True  # after the cell method: hull of the biggest contour + edge snapping
SMALL_GRID_AREA = 0.03  # last resort, after the cell method: a smaller border (small grid, measured: below ~6% of a 640x480 frame digits stop reading)
MIN_CELL_AREA = 0.0004  # cell method: one cell's hole >= this share of the frame
GRID_CHECK = True  # a found quad counts only if its flattened image shows the box lines
BOX_LINES = 0.93   # looks_like_grid: 3 of the 4 inner box lines cover >= this share (gentle threshold)


def preprocess(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) if img.ndim == 3 else img
    blur = cv2.GaussianBlur(gray, (7, 7), 0)
    th = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                               cv2.THRESH_BINARY_INV, 11, 2)
    return gray, th


def order_corners(pts):
    """Corners clockwise around their centre (TL, TR, BR, BL on screen), starting at
    the one nearest the top-left (smallest x + y). Ordered by angle so each corner is
    used once: picking each by its own argmin/argmax chose the same corner twice for a
    grid at ~45 degrees, and the warp collapsed to a blank image (rotate_steep).
    Which corner is really 'up' is decided later by the reading (orientation.py).
    Mirrored by GridDetector.orderCorners -> Orientation.clockwiseFromTopLeft."""
    pts = np.asarray(pts, dtype=np.float32)
    c = pts.mean(axis=0)
    order = sorted(range(4), key=lambda i: np.arctan2(pts[i, 1] - c[1], pts[i, 0] - c[0]))
    sums = [pts[i, 0] + pts[i, 1] for i in order]
    start = sums.index(min(sums))
    return pts[[order[(start + j) % 4] for j in range(4)]]


def find_grid(th, accept=None):
    """Find the puzzle's 4 corners. Tries the fast outer-border method first,
    then falls back to locating the grid from its cells, then to the hull of a
    decorated border, then to a smaller border (small grid).
    accept(corners) -> bool judges every candidate (see grid_accept): a rejected one
    is skipped and the search goes on, so a book cover or a keyboard is not a grid
    and a real grid next to one still gets its turn."""
    ok = accept if accept is not None and GRID_CHECK else (lambda corners: True)
    corners = _find_by_border(th, MIN_GRID_AREA, ok)
    if corners is None:
        rough = _find_by_cells(th)
        if rough is not None:
            corners = _refine_edges(th, order_corners(rough.astype(np.float32)))
            if not ok(corners):
                corners = None
    if corners is None and HULL_FALLBACK:
        rough = _find_by_hull(th, MIN_GRID_AREA)
        if rough is not None:
            corners = _refine_edges(th, rough)
            if not ok(corners):
                corners = None
    if corners is None and SMALL_GRID_AREA < MIN_GRID_AREA:
        corners = _find_by_border(th, SMALL_GRID_AREA, ok, below=MIN_GRID_AREA)
    return corners


def line_ink(warped):
    """Ink for the grid check only: a gentler threshold than binarize() (C=2, not 10), so
    blurred and far-away grid lines still show. Never used for reading digits."""
    return cv2.adaptiveThreshold(warped, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                 cv2.THRESH_BINARY_INV, 21, 2)


def box_line_score(ink, tol=9, widen=5):
    """How grid-like a flattened quad is, from its line_ink(): the second weakest of the
    4 inner box lines (1/3 and 2/3 across, both directions), so 3 of the 4 must show
    (glare or a finger may hide one). A line's strength = the best share of its length
    that is ink, among the columns (rows) within tol px of where it should be; ink
    within widen px sideways counts (lines aren't perfectly straight).
    Only the thick box lines: thin cell lines of some screen sudokus are too faint,
    and small grids lose them to blur.
    Measured (2026-11-14, 640 px frames): 270 solved grids (photos + synth) >= 0.976;
    446 more real grids at 30-100% size with Gaussian blur sigma 0-2: 23 below 0.93;
    376 false quads from the owner's no-grid photos (book cover, title, keyboard;
    jittered, shrunk, blurred) <= 0.77 but one at 0.907.
    The first version used binarize() (C=10) and the weakest line at 0.6: fine on sharp
    photos, but it rejected 147 of those 716 real grids (blur, distance), so on the
    phone the grid was found late and only up close. Mirrored by GridCheck.boxLineScore."""
    n = ink.shape[0]
    lines = []
    for axis in (0, 1):  # 0: vertical lines (share of each column), 1: horizontal
        wide = cv2.dilate(ink, np.ones((1, widen) if axis == 0 else (widen, 1), np.uint8)) > 0
        share = wide.mean(axis=axis)
        for i in (3, 6):
            c = i * n // 9
            lines.append(float(share[c - tol:c + tol + 1].max()))
    return sorted(lines)[1]


def looks_like_grid(warped):
    """warped: flattened quad, dark ink on light (after the looks_dark flip)."""
    return box_line_score(line_ink(warped)) >= BOX_LINES


def grid_accept(gray):
    """accept() for find_grid: flatten the candidate from gray, make it dark-on-light
    like the reader will, and ask looks_like_grid."""
    def accept(corners):
        w = descreen(warp(gray, np.asarray(corners, np.float32))[0])
        return looks_like_grid(255 - w if looks_dark(w) else w)
    return accept


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


def _find_by_border(th, min_area_ratio, accept=lambda corners: True, below=None):
    """Largest convex 4-corner contour = the puzzle border. Fails when a page
    rule or text touches the border, because the shapes merge into one.
    below: skip contours of this share or more (the first pass already judged them)."""
    contours, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    h, w = th.shape
    for c in sorted(contours, key=cv2.contourArea, reverse=True)[:5]:
        if cv2.contourArea(c) < min_area_ratio * h * w:
            break
        if below is not None and cv2.contourArea(c) >= below * h * w:
            continue
        approx = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(approx) == 4 and cv2.isContourConvex(approx):
            corners = order_corners(approx.reshape(4, 2).astype(np.float32))
            if accept(corners):
                return corners
    return None


def _find_by_hull(th, min_area_ratio):
    """Rough corners for a border that isn't a clean quad (a decorated frame whose
    ends overhang the corners, knots along the edges): the convex hull of the
    biggest contour, simplified to 4 points. _refine_edges then snaps each edge to
    the strongest line near it."""
    contours, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    h, w = th.shape
    c = max(contours, key=cv2.contourArea)
    if cv2.contourArea(c) < min_area_ratio * h * w:
        return None
    hull = cv2.convexHull(c)
    for eps in (0.02, 0.04, 0.06, 0.08, 0.1):
        approx = cv2.approxPolyDP(hull, eps * cv2.arcLength(hull, True), True)
        if len(approx) == 4:
            return order_corners(approx.reshape(4, 2).astype(np.float32))
        if len(approx) < 4:
            return None
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
        if area < MIN_CELL_AREA * h * w or area > 0.05 * h * w:
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


DESCREEN = None  # e.g. ("blur", 5), ("down", 0.5) or ("gated", 5, 20): see descreen()


def cell_texture(warped, margin=5):
    """Lower quartile of the cells' p2-p98 spread: the background texture of the
    (mostly empty) cells. Paper ~7-13, moire on a screen photographed at full
    resolution ~20-50 (measured on photos/ and synth)."""
    step = warped.shape[0] // 9
    spreads = []
    for r in range(9):
        for c in range(9):
            cell = warped[r * step + margin:(r + 1) * step - margin, c * step + margin:(c + 1) * step - margin]
            p2, p98 = np.percentile(cell, [2, 98])
            spreads.append(p98 - p2)
    return float(np.percentile(spreads, 25))


def descreen(warped, how=None):
    """Soften screen moire on the flattened grid before anything reads it (dark-mode
    vote, orientation, cells). binarize() itself stays as in training.
    ("blur", k): Gaussian k x k. ("down", s): shrink by s (area average), enlarge back.
    ("gated", k, t): Gaussian k x k only when cell_texture >= t (screen moire)."""
    how = DESCREEN if how is None else how
    if not how:
        return warped
    kind, v = how[0], how[1]
    if kind == "gated":  # blur only grids that look like a screen's moire
        return cv2.GaussianBlur(warped, (v, v), 0) if cell_texture(warped) >= how[2] else warped
    if kind == "blur":
        return cv2.GaussianBlur(warped, (v, v), 0)
    small = cv2.resize(warped, None, fx=v, fy=v, interpolation=cv2.INTER_AREA)
    return cv2.resize(small, warped.shape[::-1], interpolation=cv2.INTER_LINEAR)


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


def cell_backgrounds(warped, margin=5):
    """Median luma of each cell's interior (same interior as cell_votes), 81 ints,
    row-major. Empty cells -> the paper/screen behind the answer digit, which picks
    its colour step (answer_ink.step_for). Digits are a minority of a cell's pixels,
    so the median ignores them. Pass the warp in camera polarity.
    Mirrored by CellBackground.medians in AnswerInk.kt; export_background_cases.py +
    BackgroundParityTest check they agree exactly."""
    step = warped.shape[0] // 9
    out = []
    for r in range(9):
        for c in range(9):
            cell = warped[r * step + margin:(r + 1) * step - margin,
                          c * step + margin:(c + 1) * step - margin]
            out.append(int(np.percentile(cell, 50, method="lower")))
    return out


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


def chroma_planes(img, corners):
    """(Cr, Cb) of the grid, warped like the gray image, or None for a gray photo.
    Taken at HALF resolution, like the phone's YUV_420_888 U/V planes, so the
    numbers the thresholds were set on match what the app measures."""
    if img.ndim == 2:
        return None
    ycc = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
    h, w = ycc.shape[:2]
    half = [cv2.resize(ycc[..., k], (w // 2, h // 2), interpolation=cv2.INTER_AREA) for k in (1, 2)]
    return tuple(warp(p, corners / 2)[0] for p in half)


def _lower_median(values):
    """Median as an element of the data (lower middle), easy to match in Kotlin."""
    return float(np.sort(values, axis=None)[(values.size - 1) // 2])


def cell_pieces(bin_cell, gray_cell, cr_cell, cb_cell):
    """Stats of each ink piece in one cell, on the SAME crop and components as
    clean_cell. Returns (pieces, labels, offset): pieces = [(label, ink.Piece, raw)],
    Piece.counts = clean_cell would keep the piece (not a speck / line fragment),
    raw = (ink Cr, ink Cb, paper Cr, paper Cb offsets from 128, inkY/paperY): the
    inputs of Piece.chroma = ink.neutral_distance(*raw), how far the piece's colour is
    from any neutral (black/gray) ink.
    darkness: (paperY - inkY) / paperY, reported only (see ink.py)."""
    h, w = bin_cell.shape
    m = int(0.12 * h)
    inner = bin_cell[m:h - m, m:w - m]
    ih, iw = inner.shape
    n, labels, stats, cents = cv2.connectedComponentsWithStats(inner, connectivity=8)
    paper = labels == 0
    crop = lambda a: a[m:h - m, m:w - m]
    g, cr, cb = crop(gray_cell), crop(cr_cell), crop(cb_cell)
    if paper.any():
        py, pcr, pcb = (_lower_median(a[paper]) for a in (g, cr, cb))
    else:  # cell completely inked: no local paper, compare with neutral grey
        py, pcr, pcb = 255.0, 128.0, 128.0
    pieces = []
    for i in range(1, n):
        area = stats[i, cv2.CC_STAT_AREA]
        if area < 0.01 * ih * iw:
            continue  # noise speck: clean_cell ignores it anyway
        sel = labels == i
        darkness = (py - _lower_median(g[sel])) / max(py, 1.0)
        raw = (_lower_median(cr[sel]) - 128, _lower_median(cb[sel]) - 128,
               pcr - 128, pcb - 128, 1.0 - darkness)
        cx, cy = cents[i]
        counts = bool(abs(cx - iw / 2) <= iw * 0.35 and abs(cy - ih / 2) <= ih * 0.35)
        pieces.append((i, Piece(neutral_distance(*raw), darkness, counts), raw))
    return pieces, labels, m


def extract_cells(warped, chroma=None):
    """Returns (cells, ink). cells: 9x9 list of 28x28 digit images or None (empty).
    With chroma planes (from chroma_planes), user ink (coloured pen) is removed from
    the binarized cells BEFORE clean_cell, so the model only sees printed digits and
    binarize/clean_cell stay exactly as in training. ink = None without chroma, else
    a dict: applied (False = safety net kept everything), removed / unsure (9x9 bool),
    pieces (9x9 lists of (ink.Piece, raw)), mask (removed pixels)."""
    th = binarize(warped)
    step = warped.shape[0] // 9
    sl = lambda r, c: np.s_[r * step:(r + 1) * step, c * step:(c + 1) * step]
    plain = lambda: [[clean_cell(th[sl(r, c)]) for c in range(9)] for r in range(9)]
    if chroma is None:
        return plain(), None
    cr, cb = chroma
    cleaned = th.copy()
    removed = np.zeros((9, 9), bool)
    unsure = np.zeros((9, 9), bool)
    pieces = [[None] * 9 for _ in range(9)]
    for r in range(9):
        for c in range(9):
            ps, labels, m = cell_pieces(th[sl(r, c)], warped[sl(r, c)], cr[sl(r, c)], cb[sl(r, c)])
            pieces[r][c] = [(p, raw) for _, p, raw in ps]
            user = [i for i, p, _ in ps if kind(p.chroma) == USER]
            if user:
                inner = cleaned[sl(r, c)][m:step - m, m:step - m]  # view into cleaned
                inner[np.isin(labels, user)] = 0
                removed[r, c] = True
            unsure[r, c] = cell_unsure([p for _, p, _ in ps])
    cells = [[clean_cell(cleaned[sl(r, c)]) for c in range(9)] for r in range(9)]
    left = sum(x is not None for row in cells for x in row)
    applied = use_removal(left)
    if not applied:
        cells, removed, unsure = plain(), np.zeros((9, 9), bool), np.zeros((9, 9), bool)
    ink = {"applied": applied, "removed": removed, "unsure": unsure, "pieces": pieces,
           "printed_cells_left": left, "mask": (th > 0) & (cleaned == 0)}
    return cells, ink
