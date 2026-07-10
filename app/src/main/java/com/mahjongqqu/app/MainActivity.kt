package com.mahjongqqu.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahjongqqu.engine.Board
import com.mahjongqqu.engine.BoardConfig
import com.mahjongqqu.engine.GameAction
import com.mahjongqqu.engine.GameEngine
import com.mahjongqqu.engine.GameMode
import com.mahjongqqu.engine.GameState
import com.mahjongqqu.engine.GameStatus
import com.mahjongqqu.engine.Scoring
import com.mahjongqqu.engine.Seed
import com.mahjongqqu.engine.Selection
import com.mahjongqqu.engine.TileSuit
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MahjongqquApp()
        }
    }
}

private enum class Screen {
    HOME,
    PLAY,
    RESULT,
    LEADERBOARD,
    SETTINGS,
}

private data class MahjongTileImages(
    val front: ImageBitmap,
    val faces: Map<TileSuit, List<ImageBitmap>>,
)

@Composable
private fun MahjongqquApp() {
    val colors = lightColorScheme(
        primary = Color(0xFF116A6B),
        secondary = Color(0xFFB9462C),
        tertiary = Color(0xFF4F6F52),
        background = Color(0xFFF8F3EA),
        surface = Color(0xFFFFFCF5),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF23201C),
        onSurface = Color(0xFF23201C),
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MahjongqquRoot()
        }
    }
}

@Composable
private fun MahjongqquRoot() {
    val context = LocalContext.current
    val localBestStore = remember { LocalBestStore(context.applicationContext) }
    val boardConfig = remember { BoardConfig() }
    val engine = remember { GameEngine(boardConfig) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedMode by remember { mutableStateOf(GameMode.RANDOM) }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var resultState by remember { mutableStateOf<GameState?>(null) }
    var actionLog by remember { mutableStateOf<List<GameAction>>(emptyList()) }
    var resultActionCount by remember { mutableStateOf(0) }
    var remainingTimeMs by remember { mutableLongStateOf(120_000L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }

    fun finishGame(state: GameState) {
        val finalState = if (state.status == GameStatus.PLAYING) engine.timeUp(state) else state
        resultState = finalState
        resultActionCount = actionLog.size
        gameState = finalState
        screen = Screen.RESULT
        coroutineScope.launch {
            localBestStore.saveBestScore(finalState.mode, finalState.score)
        }
    }

    fun startGame(mode: GameMode) {
        selectedMode = mode
        remainingTimeMs = 120_000L
        elapsedMs = 0L
        actionLog = emptyList()
        resultActionCount = 0
        gameState = engine.newGame(mode, seedForMode(mode))
        resultState = null
        screen = Screen.PLAY
    }

    LaunchedEffect(screen, gameState?.boardIndex) {
        if (screen != Screen.PLAY) return@LaunchedEffect
        var lastTick = SystemClock.elapsedRealtime()
        while (screen == Screen.PLAY && remainingTimeMs > 0L) {
            delay(50L)
            val now = SystemClock.elapsedRealtime()
            val delta = now - lastTick
            lastTick = now
            remainingTimeMs = (remainingTimeMs - delta).coerceAtLeast(0L)
            elapsedMs += delta

            val current = gameState ?: continue
            if (current.status == GameStatus.TIME_UP || remainingTimeMs == 0L) {
                finishGame(engine.timeUp(current))
                break
            }
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onStart = ::startGame,
            onLeaderboard = { screen = Screen.LEADERBOARD },
            onSettings = { screen = Screen.SETTINGS },
        )

        Screen.PLAY -> {
            val state = gameState
            if (state == null) {
                screen = Screen.HOME
            } else {
                PlayScreen(
                    state = state,
                    boardConfig = boardConfig,
                    remainingTimeMs = remainingTimeMs,
                    elapsedMs = elapsedMs,
                    hapticsEnabled = hapticsEnabled,
                    onPause = { screen = Screen.HOME },
                    onSelection = { selection ->
                        val outcome = engine.applySelection(state, selection, elapsedMs)
                        var nextState = outcome.nextState
                        if (outcome.evaluation.valid) {
                            actionLog = actionLog + GameAction(
                                sequence = actionLog.size,
                                boardIndex = state.boardIndex,
                                startRow = selection.startRow,
                                startColumn = selection.startColumn,
                                endRow = selection.endRow,
                                endColumn = selection.endColumn,
                                elapsedMs = elapsedMs,
                            )
                        }
                        if (outcome.evaluation.valid && hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (!outcome.evaluation.valid && outcome.evaluation.selectedTiles.isNotEmpty() && hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        if (nextState.status == GameStatus.NO_MOVES) {
                            if (nextState.mode == GameMode.ENDLESS) {
                                nextState = engine.nextEndlessBoard(nextState)
                                gameState = nextState
                            } else {
                                gameState = nextState
                                finishGame(nextState)
                            }
                        } else {
                            gameState = nextState
                        }
                    },
                )
            }
        }

        Screen.RESULT -> ResultScreen(
            state = resultState,
            actionCount = resultActionCount,
            bestScoreStore = localBestStore,
            onRetry = { resultState?.mode?.let(::startGame) ?: startGame(selectedMode) },
            onHome = { screen = Screen.HOME },
            onLeaderboard = { screen = Screen.LEADERBOARD },
        )

        Screen.LEADERBOARD -> LeaderboardScreen(
            bestScoreStore = localBestStore,
            onBack = { screen = Screen.HOME },
        )

        Screen.SETTINGS -> SettingsScreen(
            hapticsEnabled = hapticsEnabled,
            soundEnabled = soundEnabled,
            onHapticsChanged = { hapticsEnabled = it },
            onSoundChanged = { soundEnabled = it },
            onBack = { screen = Screen.HOME },
        )
    }
}

@Composable
private fun HomeScreen(
    onStart: (GameMode) -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "마탱이",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "사각형으로 패를 선택해 합계 10을 만드세요.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeButton("랜덤 타임어택", "매 판 다른 시드로 120초 도전", GameMode.RANDOM, onStart)
        ModeButton("주간 챌린지", "이번 주 모든 이용자가 같은 판으로 경쟁", GameMode.WEEKLY, onStart)
        ModeButton("무한 모드", "시간 안에 가능한 많은 판을 연속 클리어", GameMode.ENDLESS, onStart)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = onLeaderboard, modifier = Modifier.fillMaxWidth()) {
            Text("랭킹 / 로컬 최고 기록")
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("설정")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "현재 빌드는 오프라인 플레이와 로컬 기록을 지원합니다. 공식 랭킹은 서버 세션 연결 후 활성화됩니다.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    mode: GameMode,
    onStart: (GameMode) -> Unit,
) {
    Button(onClick = { onStart(mode) }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayScreen(
    state: GameState,
    boardConfig: BoardConfig,
    remainingTimeMs: Long,
    elapsedMs: Long,
    hapticsEnabled: Boolean,
    onPause: () -> Unit,
    onSelection: (Selection) -> Unit,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val dragSelection = selectionFromOffsets(dragStart, dragEnd, state.board, canvasSize)
    val preview = dragSelection?.let { Scoring.evaluateSelection(state.board, it, boardConfig) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatBlock("시간", formatTime(remainingTimeMs))
            StatBlock("점수", state.score.toString())
            StatBlock("콤보", "x${state.combo.coerceAtLeast(1)}")
            OutlinedButton(onClick = onPause) {
                Text("나가기")
            }
        }

        Text(
            text = "선택 합계: ${preview?.sum ?: 0}",
            fontWeight = FontWeight.Bold,
            color = if (preview?.valid == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MahjongBoardCanvas(
                board = state.board,
                selection = dragSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(state.board.width.toFloat() / state.board.height.toFloat())
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(state.board, canvasSize, hapticsEnabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragEnd = offset
                            },
                            onDrag = { change, _ ->
                                dragEnd = change.position
                            },
                            onDragCancel = {
                                dragStart = null
                                dragEnd = null
                            },
                            onDragEnd = {
                                val selection = selectionFromOffsets(dragStart, dragEnd, state.board, canvasSize)
                                dragStart = null
                                dragEnd = null
                                if (selection != null) {
                                    onSelection(selection)
                                }
                            },
                        )
                    },
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(modeLabel(state.mode), fontWeight = FontWeight.Bold)
            Text("제거 ${state.clearedTileCount}")
            Text("판 ${state.boardIndex + 1}")
            Text("경과 ${formatTime(elapsedMs)}")
        }
    }
}

@Composable
private fun MahjongBoardCanvas(
    board: Board,
    selection: Selection?,
    modifier: Modifier = Modifier,
) {
    val tileImages = rememberMahjongTileImages()

    Canvas(
        modifier = modifier
            .background(Color(0xFFDDD2C0)),
    ) {
        val tileWidth = size.width / board.width
        val tileHeight = size.height / board.height
        val gap = minOf(tileWidth, tileHeight) * 0.08f
        val selectedCells = selection?.let { board.selectedTiles(it).map { cellTile -> cellTile.cell }.toSet() }.orEmpty()

        for (row in 0 until board.height) {
            for (column in 0 until board.width) {
                val tile = board.tileAt(row, column) ?: continue
                val left = column * tileWidth + gap
                val top = row * tileHeight + gap
                val rectSize = Size(tileWidth - gap * 2f, tileHeight - gap * 2f)
                val corner = CornerRadius(minOf(tileWidth, tileHeight) * 0.12f)

                if (tile.cleared) {
                    drawRoundRect(
                        color = Color(0xFFB8AE9E).copy(alpha = 0.28f),
                        topLeft = Offset(left, top),
                        size = rectSize,
                        cornerRadius = corner,
                    )
                    continue
                }

                val isSelected = selectedCells.any { it.row == row && it.column == column }
                drawMahjongTileImage(
                    image = tileImages.front,
                    topLeft = Offset(left, top),
                    size = rectSize,
                )

                if (isSelected) {
                    drawRoundRect(
                        color = Color(0xFFFFD35C).copy(alpha = 0.36f),
                        topLeft = Offset(left, top),
                        size = rectSize,
                        cornerRadius = corner,
                    )
                }

                drawMahjongTileImage(
                    image = tileImages.faces.getValue(tile.suit)[tile.value - 1],
                    topLeft = Offset(left, top),
                    size = rectSize,
                )

                drawRoundRect(
                    color = if (isSelected) Color(0xFF116A6B) else Color(0xFF8C7B68),
                    topLeft = Offset(left, top),
                    size = rectSize,
                    cornerRadius = corner,
                    style = Stroke(width = if (isSelected) 3f else 1.5f),
                )
            }
        }
    }
}

@Composable
private fun ResultScreen(
    state: GameState?,
    actionCount: Int,
    bestScoreStore: LocalBestStore,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    if (state == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("결과가 없습니다.")
            Button(onClick = onHome) { Text("홈") }
        }
        return
    }

    val bestScore by bestScoreStore.bestScoreFlow(state.mode).collectAsState(initial = 0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("결과", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        ResultRow("모드", modeLabel(state.mode))
        ResultRow("최종 점수", state.score.toString())
        ResultRow("로컬 최고점", maxOf(bestScore, state.score).toString())
        ResultRow("제거한 패", state.clearedTileCount.toString())
        ResultRow("완료 판", (state.boardIndex + 1).toString())
        ResultRow("성공 액션", actionCount.toString())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) { Text("다시 도전") }
            OutlinedButton(onClick = onLeaderboard) { Text("랭킹") }
            OutlinedButton(onClick = onHome) { Text("홈") }
        }
    }
}

@Composable
private fun LeaderboardScreen(
    bestScoreStore: LocalBestStore,
    onBack: () -> Unit,
) {
    val modes = listOf(GameMode.RANDOM, GameMode.WEEKLY, GameMode.ENDLESS)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("로컬 기록", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(modes) { mode ->
                val bestScore by bestScoreStore.bestScoreFlow(mode).collectAsState(initial = 0L)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(modeLabel(mode), fontWeight = FontWeight.Bold)
                        Text(bestScore.toString())
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로")
        }
    }
}

@Composable
private fun SettingsScreen(
    hapticsEnabled: Boolean,
    soundEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("설정", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        SettingSwitch("햅틱", hapticsEnabled, onHapticsChanged)
        SettingSwitch("효과음", soundEnabled, onSoundChanged)
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로")
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun rememberMahjongTileImages(): MahjongTileImages {
    val front = ImageBitmap.imageResource(id = R.drawable.tile_front)
    val manTiles = listOf(
        ImageBitmap.imageResource(id = R.drawable.tile_man_1),
        ImageBitmap.imageResource(id = R.drawable.tile_man_2),
        ImageBitmap.imageResource(id = R.drawable.tile_man_3),
        ImageBitmap.imageResource(id = R.drawable.tile_man_4),
        ImageBitmap.imageResource(id = R.drawable.tile_man_5),
        ImageBitmap.imageResource(id = R.drawable.tile_man_6),
        ImageBitmap.imageResource(id = R.drawable.tile_man_7),
        ImageBitmap.imageResource(id = R.drawable.tile_man_8),
        ImageBitmap.imageResource(id = R.drawable.tile_man_9),
    )
    val pinTiles = listOf(
        ImageBitmap.imageResource(id = R.drawable.tile_pin_1),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_2),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_3),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_4),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_5),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_6),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_7),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_8),
        ImageBitmap.imageResource(id = R.drawable.tile_pin_9),
    )
    val souTiles = listOf(
        ImageBitmap.imageResource(id = R.drawable.tile_sou_1),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_2),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_3),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_4),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_5),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_6),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_7),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_8),
        ImageBitmap.imageResource(id = R.drawable.tile_sou_9),
    )

    return remember(front, manTiles, pinTiles, souTiles) {
        MahjongTileImages(
            front = front,
            faces = mapOf(
                TileSuit.MAN to manTiles,
                TileSuit.PIN to pinTiles,
                TileSuit.SOU to souTiles,
            ),
        )
    }
}

private fun DrawScope.drawMahjongTileImage(
    image: ImageBitmap,
    topLeft: Offset,
    size: Size,
) {
    val maxHeight = size.height * 0.96f
    val maxWidth = size.width * 0.96f
    val sourceRatio = image.width.toFloat() / image.height.toFloat()
    val scaledWidthFromHeight = maxHeight * sourceRatio
    val drawWidth: Float
    val drawHeight: Float

    if (scaledWidthFromHeight <= maxWidth) {
        drawWidth = scaledWidthFromHeight
        drawHeight = maxHeight
    } else {
        drawWidth = maxWidth
        drawHeight = maxWidth / sourceRatio
    }

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            x = (topLeft.x + (size.width - drawWidth) / 2f).roundToInt(),
            y = (topLeft.y + (size.height - drawHeight) / 2f).roundToInt(),
        ),
        dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

private fun selectionFromOffsets(
    start: Offset?,
    end: Offset?,
    board: Board,
    canvasSize: IntSize,
): Selection? {
    if (start == null || end == null) return null
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null

    fun offsetToCell(offset: Offset): Pair<Int, Int> {
        val column = floor(offset.x.coerceIn(0f, canvasSize.width - 1f) / canvasSize.width * board.width)
            .toInt()
            .coerceIn(0, board.width - 1)
        val row = floor(offset.y.coerceIn(0f, canvasSize.height - 1f) / canvasSize.height * board.height)
            .toInt()
            .coerceIn(0, board.height - 1)
        return row to column
    }

    val (startRow, startColumn) = offsetToCell(start)
    val (endRow, endColumn) = offsetToCell(end)
    return Selection(startRow, startColumn, endRow, endColumn)
}

private fun seedForMode(mode: GameMode): Seed {
    val zone = ZoneId.of("Asia/Seoul")
    val now = LocalDate.now(zone)
    val week = WeekFields.ISO.weekOfWeekBasedYear()
    val year = WeekFields.ISO.weekBasedYear()
    return when (mode) {
        GameMode.RANDOM -> Seed.fromText("random-${System.nanoTime()}")
        GameMode.WEEKLY -> Seed.fromText("weekly-${now.get(year)}-${now.get(week)}")
        GameMode.ENDLESS -> Seed.fromText("endless-${System.nanoTime()}")
    }
}

private fun modeLabel(mode: GameMode): String = when (mode) {
    GameMode.RANDOM -> "랜덤 타임어택"
    GameMode.WEEKLY -> "주간 챌린지"
    GameMode.ENDLESS -> "무한 모드"
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, remainder)
}
