/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric shapes floating in space with parallax effect.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * Each shape moves along a Lissajous-like path (two independent sine waves per axis) so
 * no two shapes ever follow the same trajectory. A soft repulsion force pushes overlapping
 * shapes apart each frame, making them feel like they're avoiding each other.
 * Each shape is defined as 3D vertices transformed each frame using rotation matrices around
 * X, Y, Z axes, then perspective-projected to screen space.
 * On patching completion shapes spin up and scatter outward from screen centre, then drift
 * back to their positions.
 */
@Composable
fun ShapesBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    // Each shape has two independent sine frequencies per axis for Lissajous-like wandering,
    // unique 3D rotation speeds, size, stroke width, and depth for parallax.
    // Positions are normalised to [0..1] screen space and computed each frame - no fixed grid.
    val shapes = remember {
        listOf(
            //               cx     cy    fx1   fx2   fy1   fy2   ampX   ampY   rotSpeeds               type              depth  scale  stroke  color  alpha
            // Top third
            ShapeConfig(0.15f, 0.12f, 1.00f, 1.35f, 0.85f, 1.20f, 0.090f, 0.060f, Vec3(0.30f, 0.80f, 0.50f), ShapeType.TRIANGLE, 0.80f, 235f,  6.5f, 0, 0.20f),
            ShapeConfig(0.50f, 0.18f, 1.45f, 0.75f, 1.10f, 0.65f, 0.075f, 0.055f, Vec3(0.60f, 0.25f, 1.00f), ShapeType.HEXAGON,  0.65f, 205f,  3.5f, 1, 0.16f),
            ShapeConfig(0.84f, 0.10f, 0.85f, 1.60f, 0.70f, 1.40f, 0.085f, 0.065f, Vec3(0.45f, 1.05f, 0.30f), ShapeType.PENTAGON, 0.55f, 255f,  2.5f, 2, 0.14f),
            // Middle third
            ShapeConfig(0.10f, 0.42f, 1.20f, 0.90f, 1.30f, 0.80f, 0.080f, 0.070f, Vec3(0.90f, 0.40f, 0.70f), ShapeType.SQUARE,   0.45f, 220f,  7.0f, 2, 0.18f),
            ShapeConfig(0.45f, 0.38f, 0.75f, 1.50f, 0.90f, 1.55f, 0.095f, 0.060f, Vec3(0.50f, 0.70f, 1.20f), ShapeType.TRIANGLE, 0.70f, 265f,  4.0f, 0, 0.16f),
            ShapeConfig(0.82f, 0.50f, 1.55f, 0.80f, 1.20f, 0.75f, 0.085f, 0.075f, Vec3(1.00f, 0.30f, 0.60f), ShapeType.DIAMOND,  0.60f, 200f,  2.0f, 1, 0.15f),
            // Bottom third
            ShapeConfig(0.22f, 0.72f, 0.90f, 1.30f, 0.75f, 1.10f, 0.080f, 0.065f, Vec3(0.70f, 0.90f, 0.40f), ShapeType.PENTAGON, 0.50f, 242f,  5.5f, 1, 0.17f),
            ShapeConfig(0.58f, 0.68f, 1.30f, 0.70f, 1.50f, 0.90f, 0.075f, 0.070f, Vec3(0.25f, 0.60f, 0.90f), ShapeType.HEXAGON,  0.45f, 224f,  3.0f, 2, 0.15f),
            ShapeConfig(0.88f, 0.80f, 0.70f, 1.45f, 1.00f, 1.35f, 0.090f, 0.060f, Vec3(0.80f, 0.45f, 0.75f), ShapeType.TRIANGLE, 0.65f, 212f,  8.0f, 0, 0.21f),
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    // scatterProgress 0→1: shapes fly outward from screen centre with a spin boost, then fade.
    // animateTo(0f) after peak so shapes drift back smoothly.
    val scatterProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            scatterProgress.snapTo(0f)
            scatterProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
            // Smooth return - shapes drift back to their positions and fade in
            scatterProgress.animateTo(
                targetValue   = 0f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    val smoothedPositions = remember { mutableStateListOf<Offset>() }

    val initialRawPositions = remember {
        val t = 0f
        val twoPi = 2f * PI.toFloat()
        val basePeriod = 18000f

        shapes.map { config ->
            val px = config.cx + config.ampX * sin(t * twoPi * config.freqX1 / basePeriod) +
                    config.ampX * 0.25f * sin(t * twoPi * config.freqX2 / basePeriod + 1.4f)
            val py = config.cy + config.ampY * sin(t * twoPi * config.freqY1 / basePeriod + 0.8f) +
                    config.ampY * 0.20f * cos(t * twoPi * config.freqY2 / basePeriod + 0.3f)
            Offset(px, py)
        }
    }

    if (smoothedPositions.isEmpty()) {
        smoothedPositions.addAll(initialRawPositions)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t        = time.value
        val tiltX    = parallaxState.tiltX.value
        val tiltY    = parallaxState.tiltY.value
        val twoPi    = 2f * PI.toFloat()
        val sp       = scatterProgress.value
        val screenCx = size.width  * 0.5f
        val screenCy = size.height * 0.5f

        // Base period - all frequencies are multiples of this so motion stays smooth at any speed
        val basePeriod = 18000f

        // Step 1: compute raw Lissajous positions for every shape
        val rawPositions = shapes.map { config ->
            val px = config.cx + config.ampX * sin(t * twoPi * config.freqX1 / basePeriod) +
                    config.ampX * 0.25f * sin(t * twoPi * config.freqX2 / basePeriod + 1.4f)
            val py = config.cy + config.ampY * sin(t * twoPi * config.freqY1 / basePeriod + 0.8f) +
                    config.ampY * 0.20f * cos(t * twoPi * config.freqY2 / basePeriod + 0.3f)
            Offset(px, py)
        }

        shapes.forEachIndexed { i, _ ->
            val target = rawPositions[i]
            var current = smoothedPositions[i]

            current = lerp(current, target, 0.07f)

            val maxDelta = 0.0035f
            val dx = (current.x - smoothedPositions[i].x).coerceIn(-maxDelta, maxDelta)
            val dy = (current.y - smoothedPositions[i].y).coerceIn(-maxDelta, maxDelta)
            smoothedPositions[i] = Offset(smoothedPositions[i].x + dx, smoothedPositions[i].y + dy)
        }

        // Step 2: draw each shape at its final position
        shapes.forEachIndexed { index, config ->
            val pos = smoothedPositions[index]

            val parallaxStrength = config.depth * 45f

            val baseCx = pos.x * size.width  + tiltX * parallaxStrength
            val baseCy = pos.y * size.height + tiltY * parallaxStrength

            // Scatter: fly outward from screen centre in shape's own direction
            val dirX  = baseCx - screenCx
            val dirY  = baseCy - screenCy
            val eased = 1f - (1f - sp) * (1f - sp) // ease-out quad
            val centerX = baseCx + dirX * eased * 1.4f
            val centerY = baseCy + dirY * eased * 1.4f

            // 3D rotation - each axis accumulates at its own speed so shapes tumble uniquely
            val angleX = t * config.rotSpeeds.x * 0.0004f // nod (tilt forward/back)
            val angleY = t * config.rotSpeeds.y * 0.0003f // yaw (spin left/right)
            val angleZ = t * config.rotSpeeds.z * 0.0002f // roll

            // Extra spin boost during scatter for a "flung" feel
            val scatterBoost = sp * (4f + index * 0.3f)
            val totalAngleX = angleX + scatterBoost * 1.2f
            val totalAngleY = angleY + scatterBoost
            val totalAngleZ = angleZ + scatterBoost * 0.8f

            // Each shape has its own base alpha and color
            val alpha = if (sp > 0f) (1f - sp).coerceIn(0f, 1f) * config.alpha else config.alpha
            // Filled shapes need slightly more presence — boost alpha a touch
            val color = when (config.colorIndex) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }.copy(alpha = alpha)

            val projected = project3D(
                vertices    = config.type.baseVertices(),
                angleX      = totalAngleX,
                angleY      = totalAngleY,
                angleZ      = totalAngleZ,
                cx          = centerX,
                cy          = centerY,
                scale       = config.scale
            )
            drawShape3D(projected, color, strokeWidth = config.strokeWidth)
        }
    }
}

/** Minimal 3D vector used for vertices and rotation speed multipliers. */
private data class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * Applies rotation matrices around X, Y, Z axes (intrinsic Tait-Bryan order),
 * then perspective-projects each vertex to 2D screen space centred at ([cx], [cy]).
 */
private fun project3D(
    vertices: List<Vec3>,
    angleX: Float, angleY: Float, angleZ: Float,
    cx: Float, cy: Float,
    scale: Float = 150f,
    focalLength: Float = 600f
): List<Offset> {
    val cosX = cos(angleX); val sinX = sin(angleX)
    val cosY = cos(angleY); val sinY = sin(angleY)
    val cosZ = cos(angleZ); val sinZ = sin(angleZ)

    return vertices.map { v ->
        // Rotate around X axis (nod)
        val x1 = v.x
        val y1 = v.y * cosX - v.z * sinX
        val z1 = v.y * sinX + v.z * cosX

        // Rotate around Y axis (yaw)
        val x2 =  x1 * cosY + z1 * sinY
        val y2 =  y1
        val z2 = -x1 * sinY + z1 * cosY

        // Rotate around Z axis (roll)
        val x3 = x2 * cosZ - y2 * sinZ
        val y3 = x2 * sinZ + y2 * cosZ
        val z3 = z2

        // Perspective divide - camera placed at cameraZ so shapes never clip through it
        val cameraZ = 3f
        val perspective = focalLength / (focalLength + (cameraZ + z3) * scale * 0.5f)

        Offset(
            cx + x3 * scale * perspective,
            cy + y3 * scale * perspective
        )
    }
}

/** Draws a filled + stroked closed polygon from the projected 2D vertices. */
private fun DrawScope.drawShape3D(vertices: List<Offset>, color: Color, strokeWidth: Float) {
    if (vertices.size < 2) return
    val path = Path().apply {
        moveTo(vertices.first().x, vertices.first().y)
        for (i in 1 until vertices.size) lineTo(vertices[i].x, vertices[i].y)
        close()
    }
    // Fill at 40% of stroke alpha so shape reads clearly without being too heavy
    drawPath(path, color.copy(alpha = color.alpha * 0.4f))
    // Stroke on top for the crisp outline
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private enum class ShapeType {
    TRIANGLE,
    SQUARE,
    PENTAGON,
    HEXAGON,
    DIAMOND;

    /**
     * Returns base 3D vertices normalised to [-1, 1] lying in the XY plane (z = 0).
     * The 3D rotation in [project3D] lifts them out of the plane each frame.
     */
    fun baseVertices(): List<Vec3> = when (this) {
        TRIANGLE -> List(3) { i ->
            val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 3f
            Vec3(cos(a), sin(a), 0f)
        }
        SQUARE -> listOf(
            Vec3(-1f, -1f, 0f), Vec3( 1f, -1f, 0f),
            Vec3( 1f,  1f, 0f), Vec3(-1f,  1f, 0f)
        )
        PENTAGON -> List(5) { i ->
            val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 5f
            Vec3(cos(a), sin(a), 0f)
        }
        HEXAGON -> List(6) { i ->
            val a = i * 2f * PI.toFloat() / 6f
            Vec3(cos(a), sin(a), 0f)
        }
        DIAMOND -> listOf(
            Vec3( 0f,  -1.3f, 0f), Vec3( 0.8f, -0.2f, 0f),
            Vec3( 0.5f, 1.0f, 0f), Vec3(-0.5f,  1.0f, 0f),
            Vec3(-0.8f, -0.2f, 0f)
        )
    }
}

private data class ShapeConfig(
    val cx: Float,          // Base centre X (normalised 0..1)
    val cy: Float,          // Base centre Y (normalised 0..1)
    val freqX1: Float,      // Primary X-axis sine frequency multiplier
    val freqX2: Float,      // Secondary X-axis sine frequency multiplier (creates Lissajous path)
    val freqY1: Float,      // Primary Y-axis sine frequency multiplier
    val freqY2: Float,      // Secondary Y-axis sine frequency multiplier
    val ampX: Float,        // Horizontal wander amplitude (normalised screen units)
    val ampY: Float,        // Vertical wander amplitude (normalised screen units)
    val rotSpeeds: Vec3,    // Per-axis 3D rotation speed multipliers (x=nod, y=yaw, z=roll)
    val type: ShapeType,
    val depth: Float,       // Depth for parallax effect
    val scale: Float,       // Rendered size in pixels
    val strokeWidth: Float, // Outline thickness in pixels
    val colorIndex: Int,    // 0=primary, 1=secondary, 2=tertiary
    val alpha: Float        // Base opacity (0..1)
)
