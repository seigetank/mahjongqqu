package com.mahjongqqu.engine

class BoardGenerator(
    private val config: BoardConfig = BoardConfig(),
    private val maxAttempts: Int = 32,
) {
    init {
        require(maxAttempts > 0) { "Max attempts must be positive." }
    }

    fun generate(seed: Seed): Board {
        for (attempt in 0 until maxAttempts) {
            val board = generateUnchecked(seed.derive(attempt))
            if (MoveFinder.hasAnyMove(board, config.targetSum, config.minSelectionTiles)) {
                return board
            }
        }

        error("Failed to generate a board with at least one valid move.")
    }

    private fun generateUnchecked(seed: Seed): Board {
        val random = DeterministicRandom(seed)
        val tiles = List(config.width * config.height) {
            Tile(
                value = weightedNumber(random),
                suit = weightedSuit(random),
            )
        }
        return Board(config.width, config.height, tiles)
    }

    private fun weightedNumber(random: DeterministicRandom): Int = weightedIndex(config.numberWeights, random) + 1

    private fun weightedSuit(random: DeterministicRandom): TileSuit =
        TileSuit.entries[weightedIndex(config.suitWeights, random)]

    private fun weightedIndex(weights: IntArray, random: DeterministicRandom): Int {
        val total = weights.sumOf { maxOf(it, 0) }
        var pick = random.nextInt(total)
        for (index in weights.indices) {
            val weight = maxOf(weights[index], 0)
            if (pick < weight) return index
            pick -= weight
        }
        return weights.lastIndex
    }
}
