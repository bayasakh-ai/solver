package com.bay.sudokusolver

/*
 * Sudoku geometry shared by the hint engine, the game board and Check. Cells are
 * 0..80 row by row; rows, columns and boxes are 0..8 (boxes left to right, top to
 * bottom), as in the hint data of design/screens/flow. Pure Kotlin (iOS port).
 */

enum class UnitType { ROW, COLUMN, BOX }

/** One row, column or box. */
data class SudokuUnit(val type: UnitType, val index: Int) {
    val cells: List<Int>
        get() = when (type) {
            UnitType.ROW -> List(9) { index * 9 + it }
            UnitType.COLUMN -> List(9) { it * 9 + index }
            UnitType.BOX -> List(9) { k -> (index / 3 * 3 + k / 3) * 9 + index % 3 * 3 + k % 3 }
        }
}

object Cells {
    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = row(i) / 3 * 3 + col(i) / 3

    fun rowOf(i: Int) = SudokuUnit(UnitType.ROW, row(i))
    fun colOf(i: Int) = SudokuUnit(UnitType.COLUMN, col(i))
    fun boxOf(i: Int) = SudokuUnit(UnitType.BOX, box(i))

    /** Two different cells that share a row, column or box. */
    fun sees(a: Int, b: Int) = a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))

    /** The 20 cells each cell sees. */
    val peers: List<IntArray> = List(81) { i -> (0 until 81).filter { sees(i, it) }.toIntArray() }

    /** Boxes first, then rows, then columns: the order people scan in. */
    val units: List<SudokuUnit> = listOf(UnitType.BOX, UnitType.ROW, UnitType.COLUMN).flatMap { t -> List(9) { SudokuUnit(t, it) } }

    /** Digits 1..9 that [grid] allows in empty cell [i]. */
    fun candidates(grid: IntArray, i: Int): List<Int> {
        if (grid[i] != 0) return emptyList()
        var used = 0
        for (p in peers[i]) used = used or (1 shl grid[p])
        return (1..9).filter { used and (1 shl it) == 0 }
    }

    /** "Row 5, column 4" (1-based for people). */
    fun name(i: Int) = "row ${row(i) + 1}, column ${col(i) + 1}"
}
