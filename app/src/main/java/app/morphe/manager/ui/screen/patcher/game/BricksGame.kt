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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Everything is a fraction of the canvas, which is square, so a distance means the
// same thing on both axes and the layout survives any screen size
private const val PADDLE_WIDTH = 0.22f
private const val PADDLE_HEIGHT = 0.022f
private const val PADDLE_Y = 0.93f
private const val BALL_RADIUS = 0.016f

private const val BRICK_ROWS = 5
private const val BRICK_COLS = 7
private const val BRICK_AREA_LEFT = 0.03f
private const val BRICK_AREA_TOP = 0.10f
private const val BRICK_ROW_HEIGHT = 0.05f
private const val BRICK_GAP = 0.008f

private const val BALL_SPEED_START = 0.62f
private const val BALL_SPEED_PER_WAVE = 0.05f
private const val BALL_SPEED_MAX = 1.05f

// How far the bounce angle tilts when the ball lands on the very edge of the paddle,
// which is the only aim the player has
private const val PADDLE_MAX_BOUNCE_RAD = 1.05f

private const val BRICKS_LIVES = 3
private const val BRICK_POINTS = 10
private const val WAVE_BONUS = 50

@Stable
class BricksGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {}
) : MiniGameStateBase {
    var paddleX by mutableFloatStateOf(0.5f)
        private set
    var ballX by mutableFloatStateOf(0.5f)
        private set
    var ballY by mutableFloatStateOf(PADDLE_Y - BALL_RADIUS)
        private set
    var bricks by mutableStateOf(fullWave())
        private set
    var lives by mutableIntStateOf(BRICKS_LIVES)
        private set
    var isLaunched by mutableStateOf(false)
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

    private var velocityX = 0f
    private var velocityY = 0f
    private var speed = BALL_SPEED_START
    private var lastTickMs = 0L

    /** Slides the paddle by [delta] of the board's width, carrying a ball that is still on it. */
    fun movePaddleBy(delta: Float) {
        paddleX = (paddleX + delta).coerceIn(PADDLE_WIDTH / 2f, 1f - PADDLE_WIDTH / 2f)
        if (!isLaunched) ballX = paddleX
    }

    fun launch() {
        if (isGameOver || isLaunched) return
        isStarted = true
        isLaunched = true
        // Always upward, tilted just enough that the first brick is never straight overhead
        velocityX = speed * 0.45f
        velocityY = -speed * 0.89f
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver || isPaused || !isLaunched) return
        val dt = if (lastTickMs == 0L) 0.016f else (nowMs - lastTickMs).coerceIn(1, 50) / 1000f
        lastTickMs = nowMs

        val prevY = ballY
        ballX += velocityX * dt
        ballY += velocityY * dt

        if (ballX - BALL_RADIUS < 0f) {
            ballX = BALL_RADIUS; velocityX = abs(velocityX)
        } else if (ballX + BALL_RADIUS > 1f) {
            ballX = 1f - BALL_RADIUS; velocityX = -abs(velocityX)
        }
        if (ballY - BALL_RADIUS < 0f) {
            ballY = BALL_RADIUS; velocityY = abs(velocityY)
        }

        hitBrick(prevY)
        bouncePaddle()

        if (ballY - BALL_RADIUS > 1f) loseLife()
    }

    override fun restart() {
        bricks = fullWave()
        lives = BRICKS_LIVES
        speed = BALL_SPEED_START
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
        lastTickMs = 0L
        resetBall()
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false; lastTickMs = 0L }

    // Bricks are a flat grid, so the ball's own position tells which one it is inside
    private fun hitBrick(prevY: Float) {
        // Checked before the cast, which would fold a ball just outside the grid onto its first cell
        val cellX = (ballX - BRICK_AREA_LEFT) / brickWidth()
        val cellY = (ballY - BRICK_AREA_TOP) / BRICK_ROW_HEIGHT
        if (cellX < 0f || cellY < 0f) return
        val col = cellX.toInt()
        val row = cellY.toInt()
        if (col >= BRICK_COLS || row >= BRICK_ROWS) return
        val index = row * BRICK_COLS + col
        if (!bricks[index]) return

        bricks = bricks.copyOf().also { it[index] = false }
        points.value += BRICK_POINTS

        // A ball that was already inside the brick's rows came in from the side,
        // so only then is the horizontal direction the one to flip
        val rowTop = BRICK_AREA_TOP + row * BRICK_ROW_HEIGHT
        val enteredFromSide = prevY > rowTop && prevY < rowTop + BRICK_ROW_HEIGHT
        if (enteredFromSide) velocityX = -velocityX else velocityY = -velocityY

        if (bricks.none { it }) nextWave()
    }

    private fun bouncePaddle() {
        if (velocityY <= 0f) return
        val paddleTop = PADDLE_Y
        if (ballY + BALL_RADIUS < paddleTop || ballY - BALL_RADIUS > paddleTop + PADDLE_HEIGHT) return
        val offset = (ballX - paddleX) / (PADDLE_WIDTH / 2f)
        if (abs(offset) > 1f + BALL_RADIUS / (PADDLE_WIDTH / 2f)) return

        val angle = offset.coerceIn(-1f, 1f) * PADDLE_MAX_BOUNCE_RAD
        velocityX = speed * sin(angle)
        velocityY = -speed * cos(angle)
        ballY = paddleTop - BALL_RADIUS
    }

    private fun nextWave() {
        points.value += WAVE_BONUS
        speed = (speed + BALL_SPEED_PER_WAVE).coerceAtMost(BALL_SPEED_MAX)
        bricks = fullWave()
        resetBall()
    }

    private fun loseLife() {
        lives--
        if (lives <= 0) {
            points.commit()
            isGameOver = true
        } else {
            resetBall()
        }
    }

    private fun resetBall() {
        isLaunched = false
        paddleX = 0.5f
        ballX = 0.5f
        ballY = PADDLE_Y - BALL_RADIUS
        velocityX = 0f
        velocityY = 0f
    }

    private fun fullWave() = BooleanArray(BRICK_ROWS * BRICK_COLS) { true }
}

private fun brickWidth() = (1f - BRICK_AREA_LEFT * 2f) / BRICK_COLS

@Composable
fun BricksGame(state: BricksGameState) {
    // Runs every vsync; tick() is a no-op while the ball sits on the paddle
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { state.tick(it) }
        }
    }
    BricksCanvas(state = state, modifier = Modifier.fillMaxSize())
}

@Composable
private fun BricksCanvas(state: BricksGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .background(BricksBg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { state.launch() })
            }
            .pointerInput(Unit) {
                // The paddle tracks how far the finger travels instead of jumping to it, so
                // taking hold of the board anywhere leaves the paddle where the player left it
                detectDragGestures { change, amount ->
                    change.consume()
                    state.movePaddleBy(amount.x / size.width)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val brickW = brickWidth()

            for (row in 0 until BRICK_ROWS) {
                for (col in 0 until BRICK_COLS) {
                    if (!state.bricks[row * BRICK_COLS + col]) continue
                    val left = (BRICK_AREA_LEFT + col * brickW + BRICK_GAP / 2f) * w
                    val top = (BRICK_AREA_TOP + row * BRICK_ROW_HEIGHT + BRICK_GAP / 2f) * h
                    drawRoundRect(
                        color = BrickColors[row],
                        topLeft = Offset(left, top),
                        size = Size((brickW - BRICK_GAP) * w, (BRICK_ROW_HEIGHT - BRICK_GAP) * h),
                        cornerRadius = CornerRadius(w * 0.008f, w * 0.008f)
                    )
                }
            }

            drawRoundRect(
                color = BricksPaddle,
                topLeft = Offset((state.paddleX - PADDLE_WIDTH / 2f) * w, PADDLE_Y * h),
                size = Size(PADDLE_WIDTH * w, PADDLE_HEIGHT * h),
                cornerRadius = CornerRadius(PADDLE_HEIGHT * h / 2f, PADDLE_HEIGHT * h / 2f)
            )

            drawCircle(
                color = BricksBall,
                radius = BALL_RADIUS * w,
                center = Offset(state.ballX * w, state.ballY * h)
            )

            // Remaining lives, kept in the strip below the paddle so they never overlap play
            repeat(state.lives) { i ->
                drawCircle(
                    color = BricksLife,
                    radius = w * 0.011f,
                    center = Offset(w * (0.04f + i * 0.04f), h * 0.98f)
                )
            }
        }

        if (!state.isLaunched && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_bricks_tap_to_start),
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

private val BricksBg     = Color(0xFF101828)
private val BricksPaddle = Color(0xFF00AFAE)
private val BricksBall   = Color(0xFFF5F7FA)
private val BricksLife   = Color(0xFF5CE8E7)

// Warm at the top, cool at the bottom, so the rows still read apart in monochrome
private val BrickColors = listOf(
    Color(0xFFEF5350),
    Color(0xFFFFA726),
    Color(0xFFFDD835),
    Color(0xFF66BB6A),
    Color(0xFF42A5F5)
)
