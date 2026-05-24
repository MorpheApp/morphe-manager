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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.random.Random

// All positional constants are fractions of canvas dimensions
private const val DINO_X      = 0.12f  // left edge of dino
private const val DINO_W      = 0.07f  // width as fraction of canvas width
private const val DINO_H      = 0.13f  // height as fraction of canvas height
private const val GROUND_Y    = 0.78f  // y where ground surface starts
private const val CACTUS_W    = 0.06f  // cactus width as fraction of canvas width
private const val BASE_SPEED  = 0.35f  // canvas-widths per second at start
private const val MAX_SPEED   = 0.80f  // upper speed cap
private const val GRAVITY     = 2.8f   // canvas-height per second squared
private const val JUMP_VEL    = 1.05f  // upward velocity on tap (canvas-height per second)

data class DinoObstacle(
    val x: Float,       // left edge, fraction of canvas width
    val height: Float   // fraction of canvas height; max 0.15 keeps it always clearable
)

@Stable
class DinoGameState {
    var dinoJump by mutableFloatStateOf(0f)  // upward offset from ground, fraction of canvas height
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var obstacles by mutableStateOf<List<DinoObstacle>>(emptyList())
        private set
    var score by mutableIntStateOf(0)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    var legPhase by mutableIntStateOf(0)
        private set

    private var lastTickMs = 0L
    private var lastObstacleMs = 0L
    private var distanceTraveled = 0f
    private var elapsedSec = 0f

    fun tap() {
        if (isGameOver) { restart(); return }
        if (!isStarted) isStarted = true
        if (dinoJump == 0f) velocity = JUMP_VEL
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver) return
        val dt = if (lastTickMs == 0L) 0.016f else (nowMs - lastTickMs).coerceIn(1, 50) / 1000f
        lastTickMs = nowMs
        elapsedSec += dt

        val speed = min(BASE_SPEED + elapsedSec * 0.03f, MAX_SPEED)
        distanceTraveled += speed * dt
        score = (distanceTraveled * 100).toInt()

        // Leg animation when on ground
        if (dinoJump == 0f) legPhase = ((nowMs / 150) % 2).toInt()

        // Jump physics: velocity is upward (positive), gravity reduces it
        if (dinoJump > 0f || velocity > 0f) {
            velocity -= GRAVITY * dt
            dinoJump = maxOf(0f, dinoJump + velocity * dt)
            if (dinoJump == 0f) velocity = 0f
        }

        // Spawn obstacles at decreasing intervals as game progresses
        if (lastObstacleMs == 0L) lastObstacleMs = nowMs
        val spawnGap = maxOf(1400L, (2800L - elapsedSec * 30).toLong())
        if (nowMs - lastObstacleMs >= spawnGap) {
            obstacles = obstacles + DinoObstacle(
                x = 1.02f,
                height = Random.nextFloat() * 0.05f + 0.10f  // 0.10..0.15 (always clearable)
            )
            lastObstacleMs = nowMs
        }

        // Move obstacles and check collision (all in canvas-fraction space)
        val dinoLeft   = DINO_X + DINO_W * 0.1f
        val dinoRight  = DINO_X + DINO_W * 0.9f
        val dinoBottom = GROUND_Y - dinoJump - DINO_H * 0.1f
        var hit = false
        val moved = mutableListOf<DinoObstacle>()
        for (obs in obstacles) {
            val nx = obs.x - speed * dt
            if (nx + CACTUS_W <= 0f) continue
            if (dinoRight > nx && dinoLeft < nx + CACTUS_W && dinoBottom > GROUND_Y - obs.height) hit = true
            moved += obs.copy(x = nx)
        }
        obstacles = moved
        if (hit) isGameOver = true
    }

    fun restart() {
        dinoJump = 0f
        velocity = 0f
        obstacles = emptyList()
        score = 0
        isGameOver = false
        isStarted = false
        lastTickMs = 0L
        lastObstacleMs = 0L
        distanceTraveled = 0f
        elapsedSec = 0f
        legPhase = 0
    }
}

@Composable
fun DinoGame(
    state: DinoGameState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    extraActions: @Composable (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { state.tick(it) }
        }
    }

    Column(modifier = modifier) {
        DinoScoreRow(score = state.score, progress = progress, onRestart = state::restart, extraActions = extraActions)
        Spacer(Modifier.height(8.dp))
        DinoCanvas(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun DinoScoreRow(
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
private fun DinoCanvas(state: DinoGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DinoBg)
            .pointerInput(Unit) { detectTapGestures { state.tap() } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gy = GROUND_Y * h

            // Ground
            drawRect(DinoGround, Offset(0f, gy), Size(w, h - gy))
            drawLine(DinoGroundLine, Offset(0f, gy), Offset(w, gy), strokeWidth = 2f)

            // Obstacles (cacti)
            for (obs in state.obstacles) {
                val ox = obs.x * w
                val ow = CACTUS_W * w
                val oh = obs.height * h
                // Trunk
                drawRect(DinoCactus, Offset(ox + ow * 0.35f, gy - oh), Size(ow * 0.30f, oh))
                // Left arm
                drawRect(DinoCactus, Offset(ox, gy - oh * 0.62f), Size(ow * 0.37f, oh * 0.18f))
                // Right arm
                drawRect(DinoCactus, Offset(ox + ow * 0.63f, gy - oh * 0.52f), Size(ow * 0.37f, oh * 0.18f))
            }

            // Dino
            val dx = DINO_X * w
            val dy = (GROUND_Y - DINO_H) * h - state.dinoJump * h
            val dw = DINO_W * w
            val dh = DINO_H * h
            val dinoBottom = dy + dh

            // Body
            drawRect(DinoBody, Offset(dx, dy + dh * 0.36f), Size(dw * 0.85f, dh * 0.64f))
            // Head
            drawRect(DinoBody, Offset(dx + dw * 0.18f, dy), Size(dw * 0.82f, dh * 0.44f))
            // Eye (light circle with dark pupil)
            drawCircle(DinoBg, dw * 0.11f, Offset(dx + dw * 0.80f, dy + dh * 0.15f))
            drawCircle(DinoEye, dw * 0.06f, Offset(dx + dw * 0.84f, dy + dh * 0.17f))
            // Legs (alternating run animation when on ground, tucked when jumping)
            if (state.dinoJump > 0f) {
                drawRect(DinoBody, Offset(dx + dw * 0.52f, dinoBottom - dh * 0.22f), Size(dw * 0.17f, dh * 0.20f))
                drawRect(DinoBody, Offset(dx + dw * 0.22f, dinoBottom - dh * 0.20f), Size(dw * 0.17f, dh * 0.18f))
            } else {
                val leg1Y = dinoBottom - dh * 0.20f - if (state.legPhase == 0) 0f else dh * 0.12f
                val leg2Y = dinoBottom - dh * 0.20f - if (state.legPhase == 1) 0f else dh * 0.12f
                drawRect(DinoBody, Offset(dx + dw * 0.52f, leg1Y), Size(dw * 0.17f, dh * 0.20f))
                drawRect(DinoBody, Offset(dx + dw * 0.22f, leg2Y), Size(dw * 0.17f, dh * 0.20f))
            }
        }

        if (!state.isStarted && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_dino_tap_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DinoBg.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DinoBody.copy(alpha = 0.22f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (state.isGameOver) {
            GameOverOverlay(score = state.score, onRestart = state::restart, modifier = Modifier.fillMaxSize())
        }
    }
}

private val DinoBg         = Color(0xFFF5F0E8)
private val DinoGround     = Color(0xFFE0D8CC)
private val DinoGroundLine = Color(0xFFBBB5A8)
private val DinoCactus     = Color(0xFF3E7D40)
private val DinoBody       = Color(0xFF424242)
private val DinoEye        = Color(0xFF212121)
