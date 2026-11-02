"""Bitmask backtracking solver with 'fewest candidates first' (MRV).
Returns up to `limit` solutions: 0 = invalid, 1 = unique, 2 = ambiguous (likely misread)."""
FULL = 0b1111111110  # bits 1..9


def solve(grid, limit=2):
    rows, cols, boxes = [0] * 9, [0] * 9, [0] * 9
    empties = []
    for r in range(9):
        for c in range(9):
            v = grid[r][c]
            if v:
                bit, b = 1 << v, (r // 3) * 3 + c // 3
                if rows[r] & bit or cols[c] & bit or boxes[b] & bit:
                    return []  # givens conflict -> misread digit
                rows[r] |= bit; cols[c] |= bit; boxes[b] |= bit
            else:
                empties.append((r, c))

    g = [row[:] for row in grid]
    sols = []

    def rec(remaining):
        if len(sols) >= limit:
            return
        if not remaining:
            sols.append([row[:] for row in g])
            return
        best_i, best_cand, best_n = -1, 0, 10
        for i, (r, c) in enumerate(remaining):
            cand = FULL & ~(rows[r] | cols[c] | boxes[(r // 3) * 3 + c // 3])
            n = bin(cand).count("1")
            if n < best_n:
                best_i, best_cand, best_n = i, cand, n
                if n <= 1:
                    break
        if best_n == 0:
            return
        r, c = remaining[best_i]
        b = (r // 3) * 3 + c // 3
        rest = remaining[:best_i] + remaining[best_i + 1:]
        cand = best_cand
        while cand:
            bit = cand & -cand
            cand ^= bit
            g[r][c] = bit.bit_length() - 1
            rows[r] |= bit; cols[c] |= bit; boxes[b] |= bit
            rec(rest)
            rows[r] ^= bit; cols[c] ^= bit; boxes[b] ^= bit
            g[r][c] = 0
            if len(sols) >= limit:
                return

    rec(empties)
    return sols


def is_plausible(grid):
    return sum(1 for row in grid for v in row if v) >= 17
