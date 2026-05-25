/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

data class DinoSeagull(
    val x: Float,         // left edge, fraction of canvas width
    val y: Float,         // center y, fraction of canvas height
    val wingPhase: Float  // 0..1, drives wing animation
)

data class DinoCloud(
    val x: Float,    // center x, fraction of canvas width
    val y: Float,    // center y, fraction of canvas height
    val scale: Float // size multiplier
)

@Stable
class DinoGameState : MiniGameStateBase {
    var dinoJump by mutableFloatStateOf(0f)  // upward offset from ground, fraction of canvas height
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var obstacles by mutableStateOf<List<DinoObstacle>>(emptyList())
        private set
    override var score by mutableIntStateOf(0)
        private set
    override var highScore by mutableIntStateOf(0)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    var legPhase by mutableIntStateOf(0)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var seagulls by mutableStateOf<List<DinoSeagull>>(emptyList())
        private set
    var clouds by mutableStateOf<List<DinoCloud>>(emptyList())
        private set

    private var lastTickMs = 0L
    private var lastObstacleMs = 0L
    private var distanceTraveled = 0f
    private var elapsedSec = 0f
    private var lastSeagullMs = 0L
    private var lastCloudMs = 0L

    fun tap() {
        if (isGameOver) { restart(); return }
        if (!isStarted) isStarted = true
        if (dinoJump == 0f) velocity = JUMP_VEL
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver || isPaused) return
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
        if (hit) {
            if (score > highScore) highScore = score
            isGameOver = true
        }

        // Clouds: slow decorative background elements
        if (lastCloudMs == 0L) lastCloudMs = nowMs
        clouds = clouds.mapNotNull { c ->
            val nx = c.x - speed * 0.15f * dt
            if (nx + c.scale * 0.22f < 0f) null else c.copy(x = nx)
        }
        if (nowMs - lastCloudMs >= 7000L) {
            clouds = clouds + DinoCloud(
                x = 1.08f,
                y = Random.nextFloat() * 0.20f + 0.04f,
                scale = Random.nextFloat() * 0.45f + 0.65f
            )
            lastCloudMs = nowMs
        }

        // Seagulls: occasional birds flying past in the sky
        seagulls = seagulls.mapNotNull { g ->
            val nx = g.x - speed * 0.65f * dt
            if (nx < -0.12f) null else g.copy(x = nx, wingPhase = (g.wingPhase + dt * 2.5f) % 1f)
        }
        if (lastSeagullMs == 0L) lastSeagullMs = nowMs
        val seagullGap = maxOf(2500L, (5000L - elapsedSec * 20).toLong())
        if (nowMs - lastSeagullMs >= seagullGap) {
            seagulls = seagulls + DinoSeagull(1.05f, Random.nextFloat() * 0.28f + 0.04f, 0f)
            lastSeagullMs = nowMs
        }
    }

    override fun restart() {
        dinoJump = 0f
        velocity = 0f
        obstacles = emptyList()
        score = 0
        isGameOver = false
        isStarted = false
        isPaused = false
        lastTickMs = 0L
        lastObstacleMs = 0L
        distanceTraveled = 0f
        elapsedSec = 0f
        legPhase = 0
        seagulls = emptyList()
        clouds = listOf(
            DinoCloud(0.25f, Random.nextFloat() * 0.16f + 0.04f, Random.nextFloat() * 0.4f + 0.7f),
            DinoCloud(0.68f, Random.nextFloat() * 0.16f + 0.04f, Random.nextFloat() * 0.4f + 0.7f)
        )
        lastSeagullMs = 0L
        lastCloudMs = 0L
    }

    fun pause() { if (isStarted && !isGameOver) isPaused = true }
    fun resume() { isPaused = false; lastTickMs = 0L }
}

@Composable
fun DinoGame(state: DinoGameState) {
    GameOverHaptic(state.isGameOver)
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { state.tick(it) }
        }
    }
    DinoCanvas(state = state, modifier = Modifier.fillMaxSize())
}

@Composable
private fun DinoCanvas(state: DinoGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DinoBg)
            .pointerInput(Unit) { detectTapGestures(onPress = { state.tap() }) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gy = GROUND_Y * h

            // Sky gradient
            drawRect(
                brush = Brush.verticalGradient(listOf(DinoSkyTop, DinoSkyHorizon), startY = 0f, endY = gy),
                topLeft = Offset.Zero,
                size = Size(w, gy)
            )

            // Ground
            drawRect(DinoGround, Offset(0f, gy), Size(w, h - gy))
            drawLine(DinoGroundLine, Offset(0f, gy), Offset(w, gy), strokeWidth = 2f)

            // Clouds
            for (cloud in state.clouds) {
                val cx = cloud.x * w
                val cy = cloud.y * h
                val r = cloud.scale * w * 0.055f
                drawCircle(DinoCloudColor, r, Offset(cx, cy))
                drawCircle(DinoCloudColor, r * 0.72f, Offset(cx - r * 0.85f, cy + r * 0.15f))
                drawCircle(DinoCloudColor, r * 0.72f, Offset(cx + r * 0.85f, cy + r * 0.15f))
                drawCircle(DinoCloudColor, r * 0.60f, Offset(cx - r * 0.42f, cy - r * 0.22f))
                drawCircle(DinoCloudColor, r * 0.55f, Offset(cx + r * 0.55f, cy - r * 0.18f))
            }

            // Seagulls: V-shaped birds with animated wings
            for (gull in state.seagulls) {
                val gx = gull.x * w
                val sy = gull.y * h
                val span = w * 0.028f
                val p = gull.wingPhase
                val dip = if (p < 0.5f) p * 2f else 2f - p * 2f  // 0 -> 1 -> 0 per cycle
                val tipY = sy - span * 0.55f * dip
                drawLine(DinoGullColor, Offset(gx - span, tipY), Offset(gx, sy), strokeWidth = h * 0.006f)
                drawLine(DinoGullColor, Offset(gx, sy), Offset(gx + span, tipY), strokeWidth = h * 0.006f)
            }

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

            // Tail (tapers toward the back-left, behind the body)
            drawRect(DinoBody, Offset(dx, dy + dh * 0.33f), Size(dw * 0.16f, dh * 0.18f))
            drawRect(DinoBody, Offset(dx, dy + dh * 0.49f), Size(dw * 0.10f, dh * 0.14f))
            // Body
            drawRect(DinoBody, Offset(dx + dw * 0.05f, dy + dh * 0.44f), Size(dw * 0.68f, dh * 0.35f))
            // Neck
            drawRect(DinoBody, Offset(dx + dw * 0.38f, dy + dh * 0.26f), Size(dw * 0.20f, dh * 0.22f))
            // Head (wide horizontal rectangle, upper-right)
            drawRect(DinoBody, Offset(dx + dw * 0.34f, dy), Size(dw * 0.66f, dh * 0.30f))
            // Upper jaw (extends fully to the right)
            drawRect(DinoBody, Offset(dx + dw * 0.60f, dy + dh * 0.28f), Size(dw * 0.40f, dh * 0.11f))
            // Lower jaw
            drawRect(DinoBody, Offset(dx + dw * 0.65f, dy + dh * 0.37f), Size(dw * 0.27f, dh * 0.09f))
            // Eye sclera (uses background color to punch a hole in the head block)
            drawCircle(DinoBg, dw * 0.105f, Offset(dx + dw * 0.81f, dy + dh * 0.10f))
            // Eye pupil
            drawCircle(DinoEye, dw * 0.065f, Offset(dx + dw * 0.83f, dy + dh * 0.11f))
            // Tiny T. rex arm
            drawRect(DinoBody, Offset(dx + dw * 0.51f, dy + dh * 0.57f), Size(dw * 0.15f, dh * 0.10f))
            drawRect(DinoBody, Offset(dx + dw * 0.60f, dy + dh * 0.65f), Size(dw * 0.12f, dh * 0.07f))
            // Legs: thick rectangular legs with forward-extending feet
            val legW      = dw * 0.19f
            val legH      = dh * 0.27f
            val footW     = dw * 0.27f
            val footH     = dh * 0.10f
            val backLegX  = dx + dw * 0.12f
            val frontLegX = dx + dw * 0.34f
            if (state.dinoJump > 0f) {
                drawRect(DinoBody, Offset(backLegX,  dinoBottom - legH), Size(legW, legH - footH))
                drawRect(DinoBody, Offset(backLegX,  dinoBottom - footH), Size(footW, footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - legH * 0.82f), Size(legW, legH * 0.82f - footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - footH), Size(footW, footH))
            } else {
                val backRaise  = if (state.legPhase == 1) dh * 0.12f else 0f
                val frontRaise = if (state.legPhase == 0) dh * 0.12f else 0f
                drawRect(DinoBody, Offset(backLegX,  dinoBottom - legH - backRaise),  Size(legW, legH - footH))
                drawRect(DinoBody, Offset(backLegX,  dinoBottom - footH - backRaise), Size(footW, footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - legH - frontRaise),  Size(legW, legH - footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - footH - frontRaise), Size(footW, footH))
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
            GameOverOverlay(
                score = state.score,
                highScore = state.highScore,
                onRestart = state::restart,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.isPaused) {
            GamePauseOverlay(onResume = state::resume, modifier = Modifier.fillMaxSize())
        }
    }
}

private val DinoBg         = Color(0xFFF5F0E8)
private val DinoGround     = Color(0xFFEADA90)
private val DinoGroundLine = Color(0xFFC8B060)
private val DinoCactus     = Color(0xFF3E7D40)
private val DinoBody       = Color(0xFF424242)
private val DinoEye        = Color(0xFF212121)
private val DinoSkyTop     = Color(0xFF5DA0CF)
private val DinoSkyHorizon = Color(0xFFB4D6EC)
private val DinoCloudColor = Color(0xFFFFFFFF)
private val DinoGullColor  = Color(0xFF4A7A99)
