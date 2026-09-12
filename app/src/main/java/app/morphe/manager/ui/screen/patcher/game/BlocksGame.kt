/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private const val BLOCKS_COLS = 12
private const val BLOCKS_ROWS = 18

/** How long a completed row stays lit before the stack falls into it. */
private val BLOCKS_CLEAR_FLASH = 180.milliseconds

// Classic scoring: one line is worth far less than four at once, scaled by the level
private val BLOCKS_LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)
private const val BLOCKS_LINES_PER_LEVEL = 10

/**
 * A tetromino in its spawn rotation, laid out inside a [box] by [box] grid.
 * Only the spawn layout is stored; the other rotations are derived from it at runtime.
 */
private class Tetromino(val box: Int, val cells: List<Pair<Int, Int>>, val color: Color)

// Shapes are named after the letters they resemble, and take their colors from the app's
// own palette rather than the arcade one every falling-block game is expected to wear
private val TETROMINOES = listOf(
    Tetromino(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), Color(0xFF5C6BC0)), // I
    Tetromino(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), Color(0xFF26A69A)), // O
    Tetromino(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFFEC407A)), // T
    Tetromino(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), Color(0xFFFFB74D)), // S
    Tetromino(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), Color(0xFF9CCC65)), // Z
    Tetromino(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFFB0BEC5)), // J
    Tetromino(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFF7E57C2))  // L
)

// Offsets tried in order when a rotation lands in a wall or in the stack, so a piece
// turning next to an obstacle is nudged aside instead of refusing to turn
private val BLOCKS_KICKS = intArrayOf(0, -1, 1, -2, 2)

// Rows a downward swipe has to cover before it counts as a drop rather than a nudge
private const val BLOCKS_HARD_DROP_CELLS = 4

// Travel a drag needs before it is read as sideways or downward, in cells
private const val BLOCKS_AXIS_LOCK_CELLS = 0.6f

@Stable
class BlocksGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {}
) : MiniGameStateBase {
    // Locked cells only; the falling piece is tracked separately so gravity does not
    // rewrite the whole board on every tick
    var board by mutableStateOf(emptyBoard())
        private set
    var pieceType by mutableIntStateOf(0)
        private set
    var pieceRotation by mutableIntStateOf(0)
        private set
    var pieceX by mutableIntStateOf(0)
        private set
    var pieceY by mutableIntStateOf(0)
        private set
    /** Counts the pieces spawned, which is how a gesture tells that the one it started on landed. */
    var pieceCount by mutableIntStateOf(0)
        private set
    var lines by mutableIntStateOf(0)
        private set

    /** Rows that are complete and lit, waiting for [finishClear] to take them out. */
    var clearingRows by mutableStateOf<List<Int>>(emptyList())
        private set
    private val points = GameScore(initialHighScore, onHighScoreUpdated)
    override val score get() = points.value
    override val highScore get() = points.high
    override var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    override var isPaused by mutableStateOf(false)
        private set

    val level: Int get() = 1 + lines / BLOCKS_LINES_PER_LEVEL

    val tickMs: Long get() = max(110L, 550L - (level - 1) * 45L)

    init {
        spawn()
    }

    /** Cells the falling piece occupies, in board coordinates. */
    val pieceCells: List<Pair<Int, Int>>
        get() = rotatedCells(pieceType, pieceRotation).map { (x, y) -> pieceX + x to pieceY + y }

    /** Row the falling piece would reach if dropped now, which is where a hard drop puts it. */
    private val landingY: Int
        get() {
            var y = pieceY
            while (!collides(pieceType, pieceRotation, pieceX, y + 1)) y++
            return y
        }

    fun move(dx: Int) {
        if (!canPlay()) return
        if (!collides(pieceType, pieceRotation, pieceX + dx, pieceY)) pieceX += dx
    }

    fun rotate() {
        if (!canPlay()) return
        val next = (pieceRotation + 1) % 4
        for (kick in BLOCKS_KICKS) {
            if (!collides(pieceType, next, pieceX + kick, pieceY)) {
                pieceRotation = next
                pieceX += kick
                return
            }
        }
    }

    /**
     * Drops the piece one row on demand, worth a point so pushing down stays rewarding.
     * Landing it is left to gravity, so a piece pushed to the floor mid-swipe does not lock
     * under the finger and hand the rest of the swipe to the piece that follows it.
     */
    fun softDrop() {
        if (!canPlay()) return
        if (!collides(pieceType, pieceRotation, pieceX, pieceY + 1)) {
            pieceY++
            points.value++
        }
    }

    fun hardDrop() {
        if (!canPlay()) return
        val landing = landingY
        points.value += (landing - pieceY) * 2
        pieceY = landing
        lock()
    }

    fun tick() {
        if (!canPlay()) return
        if (collides(pieceType, pieceRotation, pieceX, pieceY + 1)) lock() else pieceY++
    }

    /** Marks the round as running, which the first gesture on the board does. */
    fun start() {
        if (!isGameOver) isStarted = true
    }

    override fun restart() {
        board = emptyBoard()
        lines = 0
        pieceCount = 0
        clearingRows = emptyList()
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
        spawn()
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false }

    /** True while completed rows are lit, which holds the board still until they are taken out. */
    val isClearing get() = clearingRows.isNotEmpty()

    private fun canPlay() = isStarted && !isGameOver && !isPaused && !isClearing

    // Writes the falling piece into the board. Completed rows are only lit here: they are lit
    // long enough to be seen, and [finishClear] is what takes them out and brings the next piece
    private fun lock() {
        val next = board.map { it.copyOf() }.toTypedArray()
        for ((x, y) in pieceCells) {
            if (y in 0 until BLOCKS_ROWS && x in 0 until BLOCKS_COLS) next[y][x] = pieceType + 1
        }
        board = next
        val completed = (0 until BLOCKS_ROWS).filter { r -> next[r].all { it != 0 } }
        if (completed.isEmpty()) spawn() else clearingRows = completed
    }

    /** Takes the lit rows out, drops the stack into the gap and brings in the next piece. */
    fun finishClear() {
        val cleared = clearingRows
        if (cleared.isEmpty()) return
        val kept = board.filterIndexed { r, _ -> r !in cleared }
        lines += cleared.size
        points.value += BLOCKS_LINE_SCORES[cleared.size] * level
        clearingRows = emptyList()
        val top = BLOCKS_ROWS - kept.size
        board = Array(BLOCKS_ROWS) { r -> if (r < top) IntArray(BLOCKS_COLS) else kept[r - top] }
        spawn()
    }

    private fun spawn() {
        pieceType = Random.nextInt(TETROMINOES.size)
        pieceCount++
        pieceRotation = 0
        pieceX = (BLOCKS_COLS - TETROMINOES[pieceType].box) / 2
        pieceY = 0
        if (collides(pieceType, pieceRotation, pieceX, pieceY)) {
            points.commit()
            isGameOver = true
        }
    }

    private fun collides(type: Int, rotation: Int, ox: Int, oy: Int) =
        rotatedCells(type, rotation).any { (cx, cy) ->
            val x = ox + cx
            val y = oy + cy
            x !in 0 until BLOCKS_COLS || y >= BLOCKS_ROWS || (y >= 0 && board[y][x] != 0)
        }

    private fun emptyBoard() = Array(BLOCKS_ROWS) { IntArray(BLOCKS_COLS) }
}

// Rotates the spawn layout clockwise inside its own box, which keeps every rotation
// within the same footprint no matter how many turns are applied
private fun rotatedCells(type: Int, rotation: Int): List<Pair<Int, Int>> {
    val piece = TETROMINOES[type]
    var cells = piece.cells
    repeat(rotation % 4) {
        cells = cells.map { (x, y) -> (piece.box - 1 - y) to x }
    }
    return cells
}

@Composable
fun BlocksGame(state: BlocksGameState) {
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(state.tickMs.milliseconds)
            state.tick()
        }
    }
    LaunchedEffect(state.isClearing) {
        if (state.isClearing) {
            delay(BLOCKS_CLEAR_FLASH)
            state.finishClear()
        }
    }
    BlocksCanvas(state = state, modifier = Modifier.fillMaxSize())
}

@Composable
private fun BlocksCanvas(state: BlocksGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .background(BlocksBg)
            .pointerInput(Unit) {
                // Rotation is the gesture that has to land instantly, so it stays a plain
                // tap and the drop is left to a swipe
                detectTapGestures(onTap = { state.start(); state.rotate() })
            }
            .pointerInput(Unit) {
                // A drag walks the piece cell by cell, so the same swipe covers the same
                // number of columns whatever the screen density
                val step = min(size.width / BLOCKS_COLS, size.height / BLOCKS_ROWS).toFloat()
                var drag = Offset.Zero
                var total = Offset.Zero
                var isVertical: Boolean? = null
                var piece = 0
                detectDragGestures(
                    onDragStart = {
                        drag = Offset.Zero
                        total = Offset.Zero
                        isVertical = null
                        piece = state.pieceCount
                        state.start()
                    },
                    onDragEnd = {
                        val ownPiece = state.pieceCount == piece
                        if (ownPiece && isVertical == true && total.y > step * BLOCKS_HARD_DROP_CELLS) {
                            state.hardDrop()
                        }
                    }
                ) { change, amount ->
                    change.consume()
                    total += amount
                    // The piece the gesture started on can land under the finger, and what is
                    // left of the swipe belongs to it, not to the one that takes its place
                    if (state.pieceCount != piece) return@detectDragGestures
                    // A swipe across the board always carries some downward drift, so the
                    // gesture commits to the axis it started along and ignores the other one
                    if (isVertical == null && total.getDistance() >= step * BLOCKS_AXIS_LOCK_CELLS) {
                        isVertical = abs(total.y) > abs(total.x)
                        drag = Offset.Zero
                    }
                    when (isVertical) {
                        true -> {
                            // Upward travel is dropped rather than banked, so a swipe up followed
                            // by one down still drops on the second half of the gesture
                            drag = Offset(0f, max(0f, drag.y + amount.y))
                            while (drag.y >= step) {
                                state.softDrop()
                                drag = drag.copy(y = drag.y - step)
                            }
                        }
                        false -> {
                            drag = Offset(drag.x + amount.x, 0f)
                            while (abs(drag.x) >= step) {
                                val dir = if (drag.x > 0) 1 else -1
                                state.move(dir)
                                drag = drag.copy(x = drag.x - dir * step)
                            }
                        }
                        null -> Unit
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = min(size.width / BLOCKS_COLS, size.height / BLOCKS_ROWS)
            val boardW = cell * BLOCKS_COLS
            val boardH = cell * BLOCKS_ROWS
            val left = (size.width - boardW) / 2f
            val top = (size.height - boardH) / 2f

            drawRect(BlocksFieldBg, topLeft = Offset(left, top), size = Size(boardW, boardH))

            for (c in 0..BLOCKS_COLS) {
                val x = left + c * cell
                drawLine(BlocksGrid, Offset(x, top), Offset(x, top + boardH), 0.5f)
            }
            for (r in 0..BLOCKS_ROWS) {
                val y = top + r * cell
                drawLine(BlocksGrid, Offset(left, y), Offset(left + boardW, y), 0.5f)
            }

            for (r in 0 until BLOCKS_ROWS) {
                for (c in 0 until BLOCKS_COLS) {
                    val value = state.board[r][c]
                    if (value != 0) {
                        drawBlock(TETROMINOES[value - 1].color, left + c * cell, top + r * cell, cell)
                    }
                }
            }

            if (!state.isClearing) {
                for ((x, y) in state.pieceCells) {
                    if (y >= 0) {
                        drawBlock(TETROMINOES[state.pieceType].color, left + x * cell, top + y * cell, cell)
                    }
                }
            }

            for (row in state.clearingRows) {
                drawRect(
                    color = BlocksClearFlash,
                    topLeft = Offset(left, top + row * cell),
                    size = Size(boardW, cell)
                )
            }
        }

        if (!state.isStarted && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_blocks_tap_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawBlock(color: Color, x: Float, y: Float, cell: Float) {
    val pad = cell * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cell - pad * 2, cell - pad * 2),
        cornerRadius = CornerRadius(cell * 0.28f, cell * 0.28f)
    )
    // Inset square rather than a bevel, so a block reads as one flat tile from any distance
    val inset = cell * 0.3f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(x + inset, y + inset),
        size = Size(cell - inset * 2, cell - inset * 2),
        cornerRadius = CornerRadius(cell * 0.1f, cell * 0.1f)
    )
}

private val BlocksBg      = Color(0xFF11131C)
private val BlocksFieldBg = Color(0xFF1B1E2B)
private val BlocksGrid    = Color(0x14FFFFFF)
private val BlocksClearFlash = Color(0xCCFFFFFF)
