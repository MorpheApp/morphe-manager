/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import kotlin.math.abs
import kotlin.random.Random

private const val BOARD_SIZE = 4

@Stable
class Game2048State {
    private var _board by mutableStateOf(emptyBoard())
    private var _score by mutableIntStateOf(0)
    private var _isGameOver by mutableStateOf(false)
    private var _hasWon by mutableStateOf(false)

    // board is read-only from outside; only accessed within this file via BoardGrid
    val board: Array<IntArray> get() = _board
    val score: Int get() = _score
    val isGameOver: Boolean get() = _isGameOver
    // hasWon stays true even after game over so the win message persists until restart
    val hasWon: Boolean get() = _hasWon

    init {
        spawnTile()
        spawnTile()
    }

    fun move(direction: Direction) {
        val snapshot = _board.map { it.copyOf() }.toTypedArray()
        val next = snapshot.map { it.copyOf() }.toTypedArray()
        var gained = 0

        when (direction) {
            Direction.LEFT  -> for (r in 0 until BOARD_SIZE) {
                val (row, pts) = slide(next[r])
                next[r] = row; gained += pts
            }
            Direction.RIGHT -> for (r in 0 until BOARD_SIZE) {
                val (row, pts) = slide(next[r].reversedArray())
                next[r] = row.reversedArray(); gained += pts
            }
            Direction.UP    -> for (c in 0 until BOARD_SIZE) {
                val (col, pts) = slide(IntArray(BOARD_SIZE) { next[it][c] })
                for (r in 0 until BOARD_SIZE) next[r][c] = col[r]; gained += pts
            }
            // DOWN: read column bottom-to-top so slide() always works left-to-right,
            // then reverse the result back into place
            Direction.DOWN  -> for (c in 0 until BOARD_SIZE) {
                val (col, pts) = slide(IntArray(BOARD_SIZE) { next[BOARD_SIZE - 1 - it][c] })
                val rev = col.reversedArray()
                for (r in 0 until BOARD_SIZE) next[r][c] = rev[r]; gained += pts
            }
        }

        if (!snapshot.contentDeepEquals(next)) {
            _board = next
            _score += gained
            if (!_hasWon && next.any { row -> row.any { it >= 2048 } }) _hasWon = true
            spawnTile()
            checkGameOver()
        }
    }

    fun restart() {
        _board = emptyBoard()
        _score = 0
        _isGameOver = false
        _hasWon = false
        spawnTile()
        spawnTile()
    }

    // Slides a single row/column left: collapses zeros, merges adjacent equal pairs
    // once per pair (left-to-right), then pads with zeros on the right.
    // Returns the new line and the score gained from merges
    private fun slide(line: IntArray): Pair<IntArray, Int> {
        val packed = line.filter { it != 0 }
        val out = mutableListOf<Int>()
        var score = 0
        var i = 0
        while (i < packed.size) {
            if (i + 1 < packed.size && packed[i] == packed[i + 1]) {
                val v = packed[i] * 2
                out += v; score += v; i += 2
            } else {
                out += packed[i++]
            }
        }
        repeat(BOARD_SIZE - out.size) { out += 0 }
        return out.toIntArray() to score
    }

    // Spawns a new tile (90 % chance: 2, 10 % chance: 4) on a random empty cell.
    private fun spawnTile() {
        val empties = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            if (_board[r][c] == 0) empties += r to c
        }
        if (empties.isEmpty()) return
        val (r, c) = empties[Random.nextInt(empties.size)]
        val next = _board.map { it.copyOf() }.toTypedArray()
        next[r][c] = if (Random.nextFloat() < 0.9f) 2 else 4
        _board = next
    }

    private fun checkGameOver() {
        if (_board.any { row -> row.any { it == 0 } }) return
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            val v = _board[r][c]
            if (c + 1 < BOARD_SIZE && _board[r][c + 1] == v) return
            if (r + 1 < BOARD_SIZE && _board[r + 1][c] == v) return
        }
        _isGameOver = true
    }

    private fun emptyBoard() = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
}

enum class Direction { LEFT, RIGHT, UP, DOWN }

// Minimum drag distance (px) before a swipe is recognized as a move
private const val SwipeThresholdPx = 40f
private val TileGap = 8.dp

@Composable
fun Game2048Board(
    state: Game2048State,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    extraActions: @Composable (() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        // 56.dp = ScoreRow height (44dp) + spacing between row and board (12dp)
        val boardSize = minOf(maxWidth, (maxHeight - 56.dp).coerceAtLeast(120.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScoreRow(score = state.score, progress = progress, onRestart = state::restart, extraActions = extraActions)

            BoardGrid(state = state, size = boardSize)

            if (state.hasWon && !state.isGameOver) {
                Text(
                    text = stringResource(R.string.mini_game_2048_win),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEDC22E)
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(
    score: Int,
    progress: Float?,
    onRestart: () -> Unit,
    extraActions: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameChip(verticalPadding = 8.dp) {
            Text(stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        if (progress != null) {
            GameChip(verticalPadding = 8.dp) {
                Text("${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        GameChip(onClick = onRestart) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        extraActions?.invoke()
    }
}

@Composable
private fun BoardGrid(state: Game2048State, size: Dp) {
    val tileSize = (size - TileGap * 5) / 4
    val boardBg = Color(0xFFBBADA0)
    val emptyTileBg = Color(0xFFCDC1B4)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(boardBg)
            .pointerInput(Unit) {
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = {
                        if (abs(drag.x) > abs(drag.y)) {
                            if (drag.x > SwipeThresholdPx) state.move(Direction.RIGHT)
                            else if (drag.x < -SwipeThresholdPx) state.move(Direction.LEFT)
                        } else {
                            if (drag.y > SwipeThresholdPx) state.move(Direction.DOWN)
                            else if (drag.y < -SwipeThresholdPx) state.move(Direction.UP)
                        }
                    }
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap)
        ) {
            for (r in 0 until BOARD_SIZE) {
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    for (c in 0 until BOARD_SIZE) {
                        TileCell(value = state.board[r][c], size = tileSize, emptyColor = emptyTileBg)
                    }
                }
            }
        }

        if (state.isGameOver) {
            GameOverOverlay(score = state.score, onRestart = state::restart, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun TileCell(value: Int, size: Dp, emptyColor: Color) {
    val bg by animateColorAsState(
        targetValue = if (value == 0) emptyColor else tileBackground(value),
        animationSpec = tween(120),
        label = "tile_bg"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (value > 0) {
            Text(
                text = value.toString(),
                color = tileForeground(value),
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    value < 100  -> 24.sp
                    value < 1000 -> 20.sp
                    else         -> 14.sp
                }
            )
        }
    }
}

// Classic 2048 tile palette
private fun tileBackground(value: Int): Color = when (value) {
    2    -> Color(0xFFEEE4DA)
    4    -> Color(0xFFEDE0C8)
    8    -> Color(0xFFF2B179)
    16   -> Color(0xFFF59563)
    32   -> Color(0xFFF67C5F)
    64   -> Color(0xFFF65E3B)
    128  -> Color(0xFFEDCF72)
    256  -> Color(0xFFEDCC61)
    512  -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}

private fun tileForeground(value: Int): Color = if (value <= 4) Color(0xFF776E65) else Color.White
