/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val MAX_ROCKETS = 6
private const val SPARKS_PER_BURST = 64
private const val MAX_SPARKS = SPARKS_PER_BURST * 10

// Below this speed a streak would be shorter than the spark itself, so only the dot is drawn
private const val STREAK_MIN_SPEED = 0.08f

// How long the flash at the heart of a burst lasts, in seconds
private const val FLASH_DURATION = 0.3f

// In screen widths per second, so a burst stays round on any screen
private const val ROCKET_GRAVITY = 0.6f
private const val SPARK_GRAVITY = 0.16f
private const val SPARK_DRAG = 1.3f

/**
 * New Year fireworks: rockets climb from the bottom edge and burst with a flash into sparks that
 * spread, sag under gravity and crackle out. Runs on the shared background step, so [speedMultiplier] makes the show
 * livelier without restarting it.
 * On patching completion the sky fills with a finale of rockets, then the show settles back.
 */
@Composable
fun FireworksBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val isDarkTheme = isDarkTheme()
    // Deeper colors on a light background, where pale sparks would wash out
    val palette = if (isDarkTheme) {
        listOf(Color(0xFFFFD54F), Color(0xFFFF6E6E), Color(0xFF64FFDA), Color(0xFFB388FF), Color(0xFF82B1FF), Color.White)
    } else {
        listOf(Color(0xFFE0A000), Color(0xFFD32F2F), Color(0xFF00897B), Color(0xFF7B1FA2), Color(0xFF1E63D6), Color(0xFF455A64))
    }
    val rocketColor = if (isDarkTheme) Color(0xFFFFF3D6) else Color(0xFF6D4C41)
    val context = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.2f,
        context = context
    )

    val finale = rememberCompletionPulse(patchingCompleted, riseMillis = 300, fallMillis = 2500)

    // The show is moved in place by the step, which bumps step once per pass so the canvas
    // redraws once, rather than a snapshot write per spark
    val show = remember { FireworksShow(palette.size) }
    val step = remember { mutableIntStateOf(0) }

    BackgroundStepEffect(speedMultiplier) { scaledMs, _ ->
        show.advance(scaledMs / 1000f, finale = finale.value > 0.05f)
        step.intValue++
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read so every step invalidates the draw
        step.intValue
        val width = size.width
        show.aspect = size.height / width
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val sparkRadius = 2.2f * density

        for (i in 0 until MAX_ROCKETS) {
            if (!show.rocketAlive[i]) continue
            val parallax = show.rocketDepth[i] * 30f
            val head = Offset(show.rocketX[i] * width + tiltX * parallax, show.rocketY[i] * width + tiltY * parallax)
            // A short exhaust trail hanging below the rocket, shrinking as it slows toward its peak
            val trail = (-show.rocketVy[i] * 0.06f).coerceAtLeast(0f) * width
            drawLine(
                color = rocketColor,
                start = head,
                end = Offset(head.x, head.y + trail),
                strokeWidth = sparkRadius,
                cap = StrokeCap.Round,
                alpha = 0.6f
            )
            drawCircle(rocketColor, radius = sparkRadius * 1.1f, center = head, alpha = 0.9f)
        }

        // The flash of each fresh burst, under its sparks
        for (i in 0 until MAX_ROCKETS) {
            val flash = show.flashLeft[i] / FLASH_DURATION
            if (flash <= 0f) continue
            val parallax = show.flashDepth[i] * 30f
            val center = Offset(show.flashX[i] * width + tiltX * parallax, show.flashY[i] * width + tiltY * parallax)
            val radius = width * 0.14f * (1.4f - flash * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette[show.flashColor[i]].copy(alpha = 0.35f * flash), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        for (i in 0 until MAX_SPARKS) {
            val life = show.sparkLife[i]
            if (life <= 0f) continue
            val fade = (life / show.sparkMaxLife[i]).pow(1.3f)
            // Spent sparks crackle out instead of dimming evenly
            val crackle = if (fade < 0.35f) 0.55f + 0.45f * sin(life * 60f + i) else 1f
            val alpha = (fade * crackle).coerceIn(0f, 1f)
            if (alpha < 0.02f) continue

            val parallax = show.sparkDepth[i] * 30f
            val x = show.sparkX[i] * width + tiltX * parallax
            val y = show.sparkY[i] * width + tiltY * parallax
            val color = palette[show.sparkColor[i]]
            val radius = sparkRadius * (0.6f + 0.6f * show.sparkDepth[i])

            // A streak along the spark's motion while it is still fast, a dot once it drifts
            val vx = show.sparkVx[i]; val vy = show.sparkVy[i]
            if (vx * vx + vy * vy > STREAK_MIN_SPEED * STREAK_MIN_SPEED) {
                val tail = Offset(x - vx * 0.05f * width, y - vy * 0.05f * width)
                drawLine(color, tail, Offset(x, y), strokeWidth = radius * 1.2f, cap = StrokeCap.Round, alpha = alpha * 0.6f)
            }
            drawCircle(color, radius = radius, center = Offset(x, y), alpha = alpha)
        }
    }
}

/**
 * Rockets and sparks in flat arrays, reused for the whole show so stepping and drawing allocate
 * nothing. Positions and velocities are in screen widths, so the height of the screen is [aspect].
 */
private class FireworksShow(private val colorCount: Int) {
    var aspect = 2f

    val rocketAlive = BooleanArray(MAX_ROCKETS)
    val rocketX = FloatArray(MAX_ROCKETS)
    val rocketY = FloatArray(MAX_ROCKETS)
    val rocketVy = FloatArray(MAX_ROCKETS)
    val rocketDepth = FloatArray(MAX_ROCKETS)
    private val rocketColor = IntArray(MAX_ROCKETS)

    val sparkX = FloatArray(MAX_SPARKS)
    val sparkY = FloatArray(MAX_SPARKS)
    val sparkVx = FloatArray(MAX_SPARKS)
    val sparkVy = FloatArray(MAX_SPARKS)
    val sparkLife = FloatArray(MAX_SPARKS)
    val sparkMaxLife = FloatArray(MAX_SPARKS)
    val sparkDepth = FloatArray(MAX_SPARKS)
    val sparkColor = IntArray(MAX_SPARKS)

    // One flash per rocket slot: a slot is only reused long after its flash has faded
    val flashLeft = FloatArray(MAX_ROCKETS)
    val flashX = FloatArray(MAX_ROCKETS)
    val flashY = FloatArray(MAX_ROCKETS)
    val flashDepth = FloatArray(MAX_ROCKETS)
    val flashColor = IntArray(MAX_ROCKETS)

    // Bursts take the next run of slots, overwriting the oldest sparks once the pool wraps
    private var sparkCursor = 0
    private var nextLaunchIn = 0.4f

    fun advance(dt: Float, finale: Boolean) {
        if (dt <= 0f) return

        nextLaunchIn -= dt
        if (nextLaunchIn <= 0f) {
            launch()
            nextLaunchIn = if (finale) 0.12f + Random.nextFloat() * 0.1f else 0.6f + Random.nextFloat() * 0.7f
        }

        for (i in 0 until MAX_ROCKETS) {
            if (flashLeft[i] > 0f) flashLeft[i] -= dt
            if (!rocketAlive[i]) continue
            rocketVy[i] += ROCKET_GRAVITY * dt
            rocketY[i] += rocketVy[i] * dt
            // Bursts at the top of its climb, the moment it stops rising
            if (rocketVy[i] >= 0f) {
                rocketAlive[i] = false
                burst(rocketX[i], rocketY[i], rocketDepth[i], rocketColor[i])
                flashLeft[i] = FLASH_DURATION
                flashX[i] = rocketX[i]
                flashY[i] = rocketY[i]
                flashDepth[i] = rocketDepth[i]
                flashColor[i] = rocketColor[i]
            }
        }

        val drag = (1f - SPARK_DRAG * dt).coerceAtLeast(0f)
        for (i in 0 until MAX_SPARKS) {
            if (sparkLife[i] <= 0f) continue
            sparkLife[i] -= dt
            sparkVx[i] *= drag
            sparkVy[i] = sparkVy[i] * drag + SPARK_GRAVITY * dt
            sparkX[i] += sparkVx[i] * dt
            sparkY[i] += sparkVy[i] * dt
        }
    }

    private fun launch() {
        val slot = (0 until MAX_ROCKETS).firstOrNull { !rocketAlive[it] } ?: return
        val startY = aspect + 0.02f
        // Peaks somewhere in the upper half, so the sparks have room to fall
        val peakY = aspect * (0.12f + Random.nextFloat() * 0.33f)
        rocketAlive[slot] = true
        rocketX[slot] = 0.12f + Random.nextFloat() * 0.76f
        rocketY[slot] = startY
        // The launch speed that brings the rocket to rest exactly at its peak under gravity
        rocketVy[slot] = -sqrt(2f * ROCKET_GRAVITY * (startY - peakY))
        rocketDepth[slot] = 0.4f + Random.nextFloat() * 0.6f
        rocketColor[slot] = Random.nextInt(colorCount)
    }

    private fun burst(x: Float, y: Float, depth: Float, color: Int) {
        val speed = (0.32f + Random.nextFloat() * 0.14f) * (0.7f + depth * 0.5f)
        // Now and then a second color is mixed into the burst
        val accent = if (Random.nextFloat() < 0.35f) Random.nextInt(colorCount) else color
        repeat(SPARKS_PER_BURST) { n ->
            val i = sparkCursor
            sparkCursor = (sparkCursor + 1) % MAX_SPARKS
            val angle = (n + Random.nextFloat() * 0.6f) / SPARKS_PER_BURST * 2f * PI.toFloat()
            // Spread through the whole ball rather than along one ring, leaning toward its rim
            val sparkSpeed = speed * (0.3f + 0.75f * sqrt(Random.nextFloat()))
            sparkX[i] = x
            sparkY[i] = y
            sparkVx[i] = cos(angle) * sparkSpeed
            sparkVy[i] = sin(angle) * sparkSpeed
            sparkMaxLife[i] = 1.3f + Random.nextFloat() * 0.7f
            sparkLife[i] = sparkMaxLife[i]
            sparkDepth[i] = depth
            sparkColor[i] = if (n % 3 == 0) accent else color
        }
    }
}
