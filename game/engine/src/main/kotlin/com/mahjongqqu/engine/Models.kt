package com.mahjongqqu.engine

import java.security.MessageDigest
import java.util.Base64

enum class GameMode {
    RANDOM,
    WEEKLY,
    ENDLESS,
}

enum class GameStatus {
    PLAYING,
    NO_MOVES,
    TIME_UP,
}

enum class TileSuit {
    MAN,
    PIN,
    SOU,
}

class Seed private constructor(val bytes: ByteArray) {
    init {
        require(bytes.isNotEmpty()) { "Seed must not be empty." }
    }

    fun toBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun derive(index: Int): Seed {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        digest.update(index.toByte())
        digest.update((index ushr 8).toByte())
        digest.update((index ushr 16).toByte())
        digest.update((index ushr 24).toByte())
        return Seed(digest.digest().copyOf(16))
    }

    override fun equals(other: Any?): Boolean = other is Seed && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = toBase64()

    companion object {
        fun fromBytes(bytes: ByteArray): Seed = Seed(bytes.copyOf())

        fun fromBase64(value: String): Seed = Seed(Base64.getUrlDecoder().decode(value))

        fun fromText(value: String): Seed {
            val digest = MessageDigest.getInstance("SHA-256")
            return Seed(digest.digest(value.toByteArray(Charsets.UTF_8)).copyOf(16))
        }
    }
}

data class BoardConfig(
    val width: Int = 10,
    val height: Int = 14,
    val targetSum: Int = 10,
    val minSelectionTiles: Int = 2,
    val numberWeights: IntArray = IntArray(9) { 1 },
    val suitWeights: IntArray = IntArray(TileSuit.entries.size) { 1 },
    val boardVersion: Int = 1,
) {
    init {
        require(width > 0) { "Board width must be positive." }
        require(height > 0) { "Board height must be positive." }
        require(targetSum > 0) { "Target sum must be positive." }
        require(minSelectionTiles >= 1) { "Minimum selected tile count must be positive." }
        require(numberWeights.size == 9) { "Number weights must contain 9 entries." }
        require(numberWeights.any { it > 0 }) { "At least one number weight must be positive." }
        require(suitWeights.size == TileSuit.entries.size) { "Suit weights must match TileSuit count." }
        require(suitWeights.any { it > 0 }) { "At least one suit weight must be positive." }
    }
}

data class ScoreRules(
    val comboWindowMs: Long = 2_500L,
    val maxComboMultiplier: Int = 3,
    val scoreRuleVersion: Int = 1,
) {
    init {
        require(comboWindowMs >= 0L) { "Combo window must not be negative." }
        require(maxComboMultiplier >= 1) { "Max combo multiplier must be at least 1." }
    }
}

data class Tile(
    val value: Int,
    val suit: TileSuit,
    val cleared: Boolean = false,
) {
    init {
        require(value in 1..9) { "Tile value must be between 1 and 9." }
    }
}

data class Cell(
    val row: Int,
    val column: Int,
)

data class CellTile(
    val cell: Cell,
    val tile: Tile,
)

data class Selection(
    val startRow: Int,
    val startColumn: Int,
    val endRow: Int,
    val endColumn: Int,
) {
    fun normalized(): Selection {
        val minRow = minOf(startRow, endRow)
        val maxRow = maxOf(startRow, endRow)
        val minColumn = minOf(startColumn, endColumn)
        val maxColumn = maxOf(startColumn, endColumn)
        return Selection(minRow, minColumn, maxRow, maxColumn)
    }
}

data class Board(
    val width: Int,
    val height: Int,
    val tiles: List<Tile>,
) {
    init {
        require(width > 0) { "Board width must be positive." }
        require(height > 0) { "Board height must be positive." }
        require(tiles.size == width * height) { "Tile count must match board dimensions." }
    }

    fun index(row: Int, column: Int): Int = row * width + column

    fun contains(row: Int, column: Int): Boolean = row in 0 until height && column in 0 until width

    fun tileAt(row: Int, column: Int): Tile? {
        if (!contains(row, column)) return null
        return tiles[index(row, column)]
    }

    fun selectedTiles(selection: Selection): List<CellTile> {
        val normalized = selection.normalized()
        val startRow = normalized.startRow.coerceIn(0, height - 1)
        val endRow = normalized.endRow.coerceIn(0, height - 1)
        val startColumn = normalized.startColumn.coerceIn(0, width - 1)
        val endColumn = normalized.endColumn.coerceIn(0, width - 1)

        val selected = ArrayList<CellTile>()
        for (row in startRow..endRow) {
            for (column in startColumn..endColumn) {
                val tile = tileAt(row, column) ?: continue
                if (!tile.cleared) {
                    selected += CellTile(Cell(row, column), tile)
                }
            }
        }
        return selected
    }

    fun clear(cells: Collection<Cell>): Board {
        if (cells.isEmpty()) return this
        val targetCells = cells.toHashSet()
        val nextTiles = tiles.mapIndexed { index, tile ->
            val row = index / width
            val column = index % width
            if (Cell(row, column) in targetCells) tile.copy(cleared = true) else tile
        }
        return copy(tiles = nextTiles)
    }

    fun activeTileCount(): Int = tiles.count { !it.cleared }
}

data class GameState(
    val mode: GameMode,
    val seed: Seed,
    val boardVersion: Int,
    val scoreRuleVersion: Int,
    val board: Board,
    val score: Long = 0L,
    val combo: Int = 0,
    val clearedTileCount: Int = 0,
    val boardIndex: Int = 0,
    val lastValidActionElapsedMs: Long? = null,
    val status: GameStatus = GameStatus.PLAYING,
)

data class GameAction(
    val sequence: Int,
    val boardIndex: Int,
    val startRow: Int,
    val startColumn: Int,
    val endRow: Int,
    val endColumn: Int,
    val elapsedMs: Long,
) {
    fun selection(): Selection = Selection(startRow, startColumn, endRow, endColumn)
}

data class MoveEvaluation(
    val selection: Selection,
    val selectedTiles: List<CellTile>,
    val sum: Int,
    val valid: Boolean,
)

data class MoveOutcome(
    val previousState: GameState,
    val nextState: GameState,
    val evaluation: MoveEvaluation,
    val scoreDelta: Long,
)
