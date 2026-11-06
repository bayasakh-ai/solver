package com.bay.sudokusolver

/**
 * Sudoku solver: bitmask backtracking, fewest-candidates-first. Port of solver.py.
 * A grid is an IntArray(81), row by row, 0 = empty cell.
 * solve() returns up to [limit] solutions: 0 = invalid, 1 = unique, 2 = ambiguous.
 */
object Solver {
    private const val FULL = 0b1111111110 // bits 1..9

    fun solve(grid: IntArray, limit: Int = 2): List<IntArray> {
        require(grid.size == 81) { "grid must have 81 cells" }
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        val empties = ArrayList<Int>()
        for (i in 0 until 81) {
            val v = grid[i]
            if (v == 0) {
                empties.add(i)
                continue
            }
            val r = i / 9
            val c = i % 9
            val b = (r / 3) * 3 + c / 3
            val bit = 1 shl v
            if ((rows[r] and bit) != 0 || (cols[c] and bit) != 0 || (boxes[b] and bit) != 0) {
                return emptyList() // givens conflict -> a digit was misread
            }
            rows[r] = rows[r] or bit
            cols[c] = cols[c] or bit
            boxes[b] = boxes[b] or bit
        }

        val g = grid.copyOf()
        val solutions = ArrayList<IntArray>()
        val remaining = empties.toIntArray()

        // remaining[0 until n] are the cells still to fill
        fun search(n: Int) {
            if (solutions.size >= limit) return
            if (n == 0) {
                solutions.add(g.copyOf())
                return
            }
            var bestK = -1
            var bestCand = 0
            var bestCount = 10
            for (k in 0 until n) {
                val i = remaining[k]
                val r = i / 9
                val c = i % 9
                val cand = FULL and (rows[r] or cols[c] or boxes[(r / 3) * 3 + c / 3]).inv()
                val count = Integer.bitCount(cand)
                if (count < bestCount) {
                    bestK = k; bestCand = cand; bestCount = count
                    if (count <= 1) break
                }
            }
            if (bestCount == 0) return

            // move the chosen cell to the end of the active range
            val i = remaining[bestK]
            remaining[bestK] = remaining[n - 1]
            remaining[n - 1] = i
            val r = i / 9
            val c = i % 9
            val b = (r / 3) * 3 + c / 3

            var cand = bestCand
            while (cand != 0) {
                val bit = cand and -cand
                cand = cand xor bit
                g[i] = Integer.numberOfTrailingZeros(bit)
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
                search(n - 1)
                rows[r] = rows[r] xor bit; cols[c] = cols[c] xor bit; boxes[b] = boxes[b] xor bit
                g[i] = 0
                if (solutions.size >= limit) break
            }

            // restore order for the caller's loop
            remaining[n - 1] = remaining[bestK]
            remaining[bestK] = i
        }

        search(remaining.size)
        return solutions
    }

    fun isPlausible(grid: IntArray): Boolean = grid.count { it != 0 } >= 17

    /** Parses "..5.31.4." style rows (dots or 0 = empty, spaces ignored). */
    fun parse(text: String): IntArray {
        val digits = text.filter { it == '.' || it.isDigit() }
        require(digits.length == 81) { "expected 81 cells, got ${digits.length}" }
        return IntArray(81) { if (digits[it] == '.') 0 else digits[it] - '0' }
    }

    fun format(grid: IntArray): String =
        (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString(" ") { c -> grid[r * 9 + c].let { if (it == 0) "." else "$it" } }
        }
}
