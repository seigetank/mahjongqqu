package com.mahjongqqu.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEngineTest {
    private val config = BoardConfig(width = 3, height = 2)

    @Test
    fun sameSeedGeneratesSameBoard() {
        val generator = BoardGenerator(config)
        val seed = Seed.fromText("stable-seed")

        val first = generator.generate(seed)
        val second = generator.generate(seed)

        assertEquals(first, second)
    }

    @Test
    fun differentSeedsGenerateDifferentBoards() {
        val generator = BoardGenerator(config)

        val first = generator.generate(Seed.fromText("a"))
        val second = generator.generate(Seed.fromText("b"))

        assertNotEquals(first, second)
    }

    @Test
    fun validSelectionClearsTilesAndScores() {
        val board = Board(
            width = 2,
            height = 1,
            tiles = listOf(
                Tile(3, TileSuit.MAN),
                Tile(7, TileSuit.PIN),
            ),
        )
        val state = GameState(
            mode = GameMode.RANDOM,
            seed = Seed.fromText("manual"),
            boardVersion = 1,
            scoreRuleVersion = 1,
            board = board,
        )
        val engine = GameEngine(BoardConfig(width = 2, height = 1))

        val outcome = engine.applySelection(state, Selection(0, 0, 0, 1), elapsedMs = 1000L)

        assertTrue(outcome.evaluation.valid)
        assertEquals(200L, outcome.scoreDelta)
        assertEquals(200L, outcome.nextState.score)
        assertEquals(2, outcome.nextState.clearedTileCount)
        assertTrue(outcome.nextState.board.tiles.all { it.cleared })
    }

    @Test
    fun invalidNonEmptySelectionResetsComboWithoutChangingBoard() {
        val board = Board(
            width = 2,
            height = 1,
            tiles = listOf(
                Tile(4, TileSuit.MAN),
                Tile(5, TileSuit.PIN),
            ),
        )
        val state = GameState(
            mode = GameMode.RANDOM,
            seed = Seed.fromText("manual"),
            boardVersion = 1,
            scoreRuleVersion = 1,
            board = board,
            combo = 2,
        )
        val engine = GameEngine(BoardConfig(width = 2, height = 1))

        val outcome = engine.applySelection(state, Selection(0, 0, 0, 1), elapsedMs = 1000L)

        assertFalse(outcome.evaluation.valid)
        assertEquals(0L, outcome.scoreDelta)
        assertEquals(0, outcome.nextState.combo)
        assertEquals(board, outcome.nextState.board)
    }

    @Test
    fun moveFinderDetectsRectangularSumTen() {
        val board = Board(
            width = 3,
            height = 2,
            tiles = listOf(
                Tile(9, TileSuit.MAN),
                Tile(1, TileSuit.PIN),
                Tile(5, TileSuit.SOU),
                Tile(8, TileSuit.MAN),
                Tile(2, TileSuit.PIN),
                Tile(6, TileSuit.SOU),
            ),
        )

        val move = MoveFinder.findFirstMove(board, targetSum = 10, minTiles = 2)

        assertNotNull(move)
    }
}
