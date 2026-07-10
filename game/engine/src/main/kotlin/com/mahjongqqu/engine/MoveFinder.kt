package com.mahjongqqu.engine

object MoveFinder {
    fun hasAnyMove(board: Board, targetSum: Int, minTiles: Int): Boolean =
        findFirstMove(board, targetSum, minTiles) != null

    fun findFirstMove(board: Board, targetSum: Int, minTiles: Int): Selection? {
        val sums = PrefixGrid(board) { tile -> if (tile.cleared) 0 else tile.value }
        val counts = PrefixGrid(board) { tile -> if (tile.cleared) 0 else 1 }

        for (startRow in 0 until board.height) {
            for (endRow in startRow until board.height) {
                for (startColumn in 0 until board.width) {
                    for (endColumn in startColumn until board.width) {
                        val sum = sums.sum(startRow, startColumn, endRow, endColumn)
                        if (sum != targetSum) continue

                        val count = counts.sum(startRow, startColumn, endRow, endColumn)
                        if (count >= minTiles) {
                            return Selection(startRow, startColumn, endRow, endColumn)
                        }
                    }
                }
            }
        }

        return null
    }
}

private class PrefixGrid(
    private val board: Board,
    valueOf: (Tile) -> Int,
) {
    private val values: IntArray = IntArray((board.width + 1) * (board.height + 1))

    init {
        for (row in 1..board.height) {
            for (column in 1..board.width) {
                val tile = board.tileAt(row - 1, column - 1) ?: error("Invalid board coordinate.")
                val index = index(row, column)
                values[index] =
                    valueOf(tile) +
                    values[index(row - 1, column)] +
                    values[index(row, column - 1)] -
                    values[index(row - 1, column - 1)]
            }
        }
    }

    fun sum(startRow: Int, startColumn: Int, endRow: Int, endColumn: Int): Int {
        val top = startRow
        val left = startColumn
        val bottom = endRow + 1
        val right = endColumn + 1
        return values[index(bottom, right)] -
            values[index(top, right)] -
            values[index(bottom, left)] +
            values[index(top, left)]
    }

    private fun index(row: Int, column: Int): Int = row * (board.width + 1) + column
}
