package com.mahjongqqu.engine

object Scoring {
    fun evaluateSelection(board: Board, selection: Selection, config: BoardConfig): MoveEvaluation {
        val selectedTiles = board.selectedTiles(selection)
        val sum = selectedTiles.sumOf { it.tile.value }
        val valid = selectedTiles.size >= config.minSelectionTiles && sum == config.targetSum
        return MoveEvaluation(selection.normalized(), selectedTiles, sum, valid)
    }

    fun scoreFor(tiles: List<Tile>, comboMultiplier: Int): Long {
        require(comboMultiplier >= 1) { "Combo multiplier must be at least 1." }
        val clearedCount = tiles.size
        val base = clearedCount * 100L
        val multiTileBonus = if (clearedCount > 2) {
            val extra = clearedCount - 2L
            extra * extra * 50L
        } else {
            0L
        }
        val suitBonus = suitBonus(base, tiles)
        return (base + multiTileBonus + suitBonus) * comboMultiplier
    }

    private fun suitBonus(base: Long, tiles: List<Tile>): Long {
        val suits = tiles.map { it.suit }.toSet()
        return when {
            suits.size == 1 -> base * 20L / 100L
            suits.size == TileSuit.entries.size -> base * 10L / 100L
            else -> 0L
        }
    }
}
