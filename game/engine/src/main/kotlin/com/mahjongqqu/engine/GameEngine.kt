package com.mahjongqqu.engine

class GameEngine(
    private val config: BoardConfig = BoardConfig(),
    private val scoreRules: ScoreRules = ScoreRules(),
    private val generator: BoardGenerator = BoardGenerator(config),
) {
    fun newGame(mode: GameMode, seed: Seed, boardIndex: Int = 0): GameState {
        val boardSeed = if (boardIndex == 0) seed else seed.derive(boardIndex)
        val board = generator.generate(boardSeed)
        return GameState(
            mode = mode,
            seed = seed,
            boardVersion = config.boardVersion,
            scoreRuleVersion = scoreRules.scoreRuleVersion,
            board = board,
            boardIndex = boardIndex,
        )
    }

    fun applySelection(state: GameState, selection: Selection, elapsedMs: Long): MoveOutcome {
        if (state.status != GameStatus.PLAYING) {
            val evaluation = Scoring.evaluateSelection(state.board, selection, config)
            return MoveOutcome(state, state, evaluation, 0L)
        }

        val evaluation = Scoring.evaluateSelection(state.board, selection, config)
        if (!evaluation.valid) {
            val nextCombo = if (evaluation.selectedTiles.isEmpty()) state.combo else 0
            val nextState = state.copy(combo = nextCombo)
            return MoveOutcome(state, nextState, evaluation, 0L)
        }

        val nextCombo = nextCombo(state, elapsedMs)
        val scoreDelta = Scoring.scoreFor(evaluation.selectedTiles.map { it.tile }, nextCombo)
        val clearedCells = evaluation.selectedTiles.map { it.cell }
        val nextBoard = state.board.clear(clearedCells)
        val nextStatus = if (MoveFinder.hasAnyMove(nextBoard, config.targetSum, config.minSelectionTiles)) {
            GameStatus.PLAYING
        } else {
            GameStatus.NO_MOVES
        }

        val nextState = state.copy(
            board = nextBoard,
            score = state.score + scoreDelta,
            combo = nextCombo,
            clearedTileCount = state.clearedTileCount + clearedCells.size,
            lastValidActionElapsedMs = elapsedMs,
            status = nextStatus,
        )

        return MoveOutcome(state, nextState, evaluation, scoreDelta)
    }

    fun nextEndlessBoard(state: GameState): GameState {
        require(state.mode == GameMode.ENDLESS) { "Only endless mode can advance to the next board." }
        val nextBoardIndex = state.boardIndex + 1
        val nextBoard = generator.generate(state.seed.derive(nextBoardIndex))
        return state.copy(
            board = nextBoard,
            boardIndex = nextBoardIndex,
            status = GameStatus.PLAYING,
            combo = 0,
            lastValidActionElapsedMs = null,
        )
    }

    fun timeUp(state: GameState): GameState = state.copy(status = GameStatus.TIME_UP)

    private fun nextCombo(state: GameState, elapsedMs: Long): Int {
        val lastElapsed = state.lastValidActionElapsedMs
        val stillCombo = lastElapsed != null && elapsedMs - lastElapsed <= scoreRules.comboWindowMs
        val rawCombo = if (stillCombo) state.combo + 1 else 1
        return rawCombo.coerceIn(1, scoreRules.maxComboMultiplier)
    }
}
