/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

private const val SNAKE_GRID = 20

@Stable
class SnakeGameState {
    var snake by mutableStateOf(listOf(10 to 10, 9 to 10, 8 to 10))
        private set
    var food by mutableStateOf(15 to 10)
        private set
    private var direction = SnakeDir.RIGHT
    private var pendingDir = SnakeDir.RIGHT
    var score by mutableIntStateOf(0)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set

    val tickMs: Long get() = max(80L, 180L - score * 5L)

    fun swipe(dir: SnakeDir) {
        if (dir != direction.opposite) pendingDir = dir
        if (!isStarted) isStarted = true
    }

    fun tick() {
        if (!isStarted || isGameOver) return
        direction = pendingDir
        val (hx, hy) = snake.first()
        val newHead = when (direction) {
            SnakeDir.UP    -> hx to hy - 1
            SnakeDir.DOWN  -> hx to hy + 1
            SnakeDir.LEFT  -> hx - 1 to hy
            SnakeDir.RIGHT -> hx + 1 to hy
        }
        if (newHead.first !in 0 until SNAKE_GRID || newHead.second !in 0 until SNAKE_GRID || snake.contains(newHead)) {
            isGameOver = true; return
        }
        val ateFood = newHead == food
        snake = if (ateFood) listOf(newHead) + snake else listOf(newHead) + snake.dropLast(1)
        if (ateFood) { score++; spawnFood() }
    }

    fun restart() {
        snake = listOf(10 to 10, 9 to 10, 8 to 10)
        food = 15 to 10
        direction = SnakeDir.RIGHT
        pendingDir = SnakeDir.RIGHT
        score = 0
        isGameOver = false
        isStarted = false
    }

    private fun spawnFood() {
        val occupied = snake.toHashSet()
        val empty = (0 until SNAKE_GRID).flatMap { x -> (0 until SNAKE_GRID).map { y -> x to y } }
            .filter { it !in occupied }
        if (empty.isNotEmpty()) food = empty[Random.nextInt(empty.size)]
    }
}

enum class SnakeDir {
    UP, DOWN, LEFT, RIGHT;
    val opposite: SnakeDir get() = when (this) {
        UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT
    }
}

@Composable
fun SnakeGame(
    state: SnakeGameState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    extraActions: @Composable (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(state.tickMs)
            state.tick()
        }
    }

    Column(modifier = modifier) {
        SnakeScoreRow(score = state.score, progress = progress, onRestart = state::restart, extraActions = extraActions)
        Spacer(Modifier.height(8.dp))
        SnakeCanvas(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun SnakeScoreRow(
    score: Int,
    progress: Float?,
    onRestart: () -> Unit,
    extraActions: @Composable (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameChip(verticalPadding = 8.dp) {
            Text(
                stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (progress != null) {
            GameChip(verticalPadding = 8.dp) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GameChip(onClick = onRestart) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        extraActions?.invoke()
    }
}

private const val SNAKE_SWIPE_THRESHOLD = 30f

@Composable
private fun SnakeCanvas(state: SnakeGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SnakeBg)
            .pointerInput(Unit) {
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = {
                        if (abs(drag.x) > abs(drag.y)) {
                            if (drag.x > SNAKE_SWIPE_THRESHOLD) state.swipe(SnakeDir.RIGHT)
                            else if (drag.x < -SNAKE_SWIPE_THRESHOLD) state.swipe(SnakeDir.LEFT)
                        } else {
                            if (drag.y > SNAKE_SWIPE_THRESHOLD) state.swipe(SnakeDir.DOWN)
                            else if (drag.y < -SNAKE_SWIPE_THRESHOLD) state.swipe(SnakeDir.UP)
                        }
                    }
                ) { change, amount -> change.consume(); drag += amount }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / SNAKE_GRID
            val cellH = size.height / SNAKE_GRID
            val pad = cellW * 0.1f
            val corner = cellW * 0.3f

            for (i in 0..SNAKE_GRID) {
                drawLine(SnakeGrid, Offset(i * cellW, 0f), Offset(i * cellW, size.height), 0.5f)
                drawLine(SnakeGrid, Offset(0f, i * cellH), Offset(size.width, i * cellH), 0.5f)
            }

            val (fx, fy) = state.food
            drawCircle(SnakeFood, cellW / 2 - pad, Offset(fx * cellW + cellW / 2, fy * cellH + cellH / 2))

            state.snake.forEachIndexed { i, (x, y) ->
                drawRoundRect(
                    color = if (i == 0) SnakeHead else SnakeBody,
                    topLeft = Offset(x * cellW + pad, y * cellH + pad),
                    size = Size(cellW - pad * 2, cellH - pad * 2),
                    cornerRadius = CornerRadius(corner, corner)
                )
            }
        }

        if (!state.isStarted && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_snake_swipe_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (state.isGameOver) {
            GameOverOverlay(score = state.score, onRestart = state::restart, modifier = Modifier.fillMaxSize())
        }
    }
}

private val SnakeBg   = Color(0xFF1A1A2E)
private val SnakeGrid = Color(0x22FFFFFF)
private val SnakeHead = Color(0xFF4CAF50)
private val SnakeBody = Color(0xFF81C784)
private val SnakeFood = Color(0xFFFF5252)
