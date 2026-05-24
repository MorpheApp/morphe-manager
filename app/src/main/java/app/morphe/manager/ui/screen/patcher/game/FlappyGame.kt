/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import kotlinx.coroutines.isActive
import kotlin.random.Random

// Bird is fixed at 22% from the left; Y coordinates are fractions of canvas height.
// X coordinates (pipes, BIRD_X) are fractions of canvas width
private const val BIRD_X = 0.22f
private const val BIRD_RADIUS = 0.05f      // fraction of canvas height
private const val PIPE_WIDTH = 0.15f       // fraction of canvas width
private const val PIPE_GAP = 0.30f         // gap height as fraction of canvas height
private const val PIPE_SPEED = 0.32f       // canvas-widths per second
private const val PIPE_SPAWN_MS = 2000L
private const val GRAVITY = 2.0f           // height-fraction per second squared
private const val TAP_IMPULSE = -1.0f      // height-fraction per second, upward
// Collision radius is smaller than the visual radius to be forgiving
private const val HIT_SHRINK = 0.75f

data class FlappyPipe(
    val x: Float,           // left edge, fraction of canvas width
    val gapCenter: Float,   // gap center, fraction of canvas height
    val passed: Boolean = false
)

@Stable
class FlappyGameState {
    var birdY by mutableFloatStateOf(0.45f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var pipes by mutableStateOf<List<FlappyPipe>>(emptyList())
        private set
    var score by mutableIntStateOf(0)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set

    private var lastTickMs = 0L
    private var lastSpawnMs = 0L

    fun tap() {
        if (isGameOver) { restart(); return }
        if (!isStarted) isStarted = true
        velocity = TAP_IMPULSE
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver) return
        val dt = if (lastTickMs == 0L) 0.016f else (nowMs - lastTickMs).coerceIn(1, 50) / 1000f
        lastTickMs = nowMs

        velocity += GRAVITY * dt
        birdY += velocity * dt

        if (birdY - BIRD_RADIUS < 0f || birdY + BIRD_RADIUS > 1f) {
            isGameOver = true; return
        }

        if (lastSpawnMs == 0L) lastSpawnMs = nowMs
        if (nowMs - lastSpawnMs >= PIPE_SPAWN_MS) {
            // Gap center constrained to 0.26..0.64 so pipes are always passable
            pipes = pipes + FlappyPipe(x = 1.05f, gapCenter = Random.nextFloat() * 0.38f + 0.26f)
            lastSpawnMs = nowMs
        }

        val hitR = BIRD_RADIUS * HIT_SHRINK
        var hit = false
        pipes = pipes.mapNotNull { pipe ->
            val nx = pipe.x - PIPE_SPEED * dt
            if (nx + PIPE_WIDTH < 0f) return@mapNotNull null
            val nowPassed = !pipe.passed && (nx + PIPE_WIDTH) < BIRD_X
            if (nowPassed) score++
            if (BIRD_X + hitR > nx && BIRD_X - hitR < nx + PIPE_WIDTH) {
                val gapTop = pipe.gapCenter - PIPE_GAP / 2f
                val gapBottom = pipe.gapCenter + PIPE_GAP / 2f
                if (birdY - hitR < gapTop || birdY + hitR > gapBottom) hit = true
            }
            pipe.copy(x = nx, passed = pipe.passed || nowPassed)
        }
        if (hit) isGameOver = true
    }

    fun restart() {
        birdY = 0.45f
        velocity = 0f
        pipes = emptyList()
        score = 0
        isGameOver = false
        isStarted = false
        lastTickMs = 0L
        lastSpawnMs = 0L
    }
}

@Composable
fun FlappyBirdGame(
    state: FlappyGameState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    extraActions: @Composable (() -> Unit)? = null
) {
    // Runs every vsync; tick() is a no-op when the game is paused or over
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { state.tick(it) }
        }
    }

    Column(modifier = modifier) {
        FlappyScoreRow(
            score = state.score,
            progress = progress,
            onRestart = state::restart,
            extraActions = extraActions
        )
        Spacer(Modifier.height(8.dp))
        FlappyCanvas(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun FlappyScoreRow(
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

@Composable
private fun FlappyCanvas(state: FlappyGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FlappySkyColor)
            .pointerInput(Unit) { detectTapGestures { state.tap() } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val capH = h * 0.035f
            val capExtra = w * 0.022f

            // Ground strip
            drawRect(
                color = FlappyGroundColor,
                topLeft = Offset(0f, h * 0.94f),
                size = Size(w, h * 0.06f)
            )

            // Pipes
            for (pipe in state.pipes) {
                val pLeft = pipe.x * w
                val pW = PIPE_WIDTH * w
                val gapTop = (pipe.gapCenter - PIPE_GAP / 2f) * h
                val gapBottom = (pipe.gapCenter + PIPE_GAP / 2f) * h

                // Top pipe body + cap
                if (gapTop > 0f) {
                    drawRect(FlappyPipeFill, topLeft = Offset(pLeft, 0f), size = Size(pW, gapTop))
                    drawRect(
                        FlappyPipeCap,
                        topLeft = Offset(pLeft - capExtra, gapTop - capH),
                        size = Size(pW + capExtra * 2f, capH)
                    )
                }

                // Bottom pipe body + cap
                if (gapBottom < h) {
                    drawRect(FlappyPipeFill, topLeft = Offset(pLeft, gapBottom), size = Size(pW, h - gapBottom))
                    drawRect(
                        FlappyPipeCap,
                        topLeft = Offset(pLeft - capExtra, gapBottom),
                        size = Size(pW + capExtra * 2f, capH)
                    )
                }
            }

            // Bird
            val bx = BIRD_X * w
            val by = state.birdY * h
            val br = BIRD_RADIUS * h

            drawCircle(FlappyBirdBody, radius = br, center = Offset(bx, by))

            // Eye (white + pupil)
            drawCircle(Color.White, radius = br * 0.38f, center = Offset(bx + br * 0.3f, by - br * 0.15f))
            drawCircle(Color(0xFF212121), radius = br * 0.2f, center = Offset(bx + br * 0.38f, by - br * 0.15f))

            // Beak (right-pointing triangle)
            drawPath(
                path = Path().apply {
                    moveTo(bx + br * 0.85f, by - br * 0.18f)
                    lineTo(bx + br * 1.55f, by)
                    lineTo(bx + br * 0.85f, by + br * 0.18f)
                    close()
                },
                color = FlappyBirdBeak
            )
        }

        // "Tap to fly" prompt shown before first tap
        if (!state.isStarted && !state.isGameOver) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_flappy_tap_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Game over overlay
        if (state.isGameOver) {
            GameOverOverlay(score = state.score, onRestart = state::restart, modifier = Modifier.fillMaxSize())
        }
    }
}

private val FlappySkyColor = Color(0xFF70C5CE)
private val FlappyGroundColor = Color(0xFFDED895)
private val FlappyPipeFill = Color(0xFF73C02A)
private val FlappyPipeCap = Color(0xFF5A9820)
private val FlappyBirdBody = Color(0xFFFDD835)
private val FlappyBirdBeak = Color(0xFFFF8F00)
