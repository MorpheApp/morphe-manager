/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Animated low-poly mesh background with chaotic 3D motion, its faces shaded by the light they
 * catch so the waves read as relief.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion a circular ripple wave propagates from the mesh center outward,
 * displacing nodes along the Z-axis, then decays back to zero.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.4f,
        context = context
    )

    // Generate mesh grid - random offsets and Z amplitudes per node
    val meshNodes = remember { generateMeshGrid() }

    // time accumulates in ms at speed 1x
    val time = rememberAnimatedTime(speedMultiplier)

    // rippleProgress 0→1: a circular pulse wave sweeps from mesh center to edges,
    // lifting nodes along Z before the amplitude decays back to zero.
    // Linear easing keeps the wave front at constant speed.
    val rippleProgress = rememberCompletionPulse(
        patchingCompleted,
        riseMillis = 1200,
        fallMillis = 500,
        riseEasing = LinearEasing
    )

    // Every node is projected once per frame into these, then shared by the cells around it
    val projected = remember { MeshProjection(meshNodes.size) }
    val trianglePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width  = size.width
        val height = size.height
        val tiltX  = parallaxState.tiltX.value
        val tiltY  = parallaxState.tiltY.value
        val rp     = rippleProgress.value
        val strokeWidth = 1.2f * density

        // 3D projection parameters
        val cameraZ  = 1.6f
        val tiltRad  = Math.toRadians(MESH_GRID_TILT_DEGREES).toFloat()
        val cosTilt  = cos(tiltRad)
        val sinTilt  = sin(tiltRad)

        // Time runs one way only: half a turn of every wave per 20 000 ms at speed 1x. A clock that
        // ran back and forth would turn every node around at the same instant
        val t = time.value * PI.toFloat() / 20000f

        meshNodes.forEachIndexed { i, node ->
            // Unique frequencies and phases for each node - creates chaotic, non-repeating motion
            val xFreq  = 1.0f + node.baseX * 0.5f
            val yFreq  = 1.2f + node.baseY * 0.6f
            val zFreq  = 0.8f + (node.baseX + node.baseY) * 0.4f
            val xPhase = node.baseX * 2f * PI.toFloat()
            val yPhase = node.baseY * 3f * PI.toFloat()
            val zPhase = (node.baseX + node.baseY) * 1.5f * PI.toFloat()

            // Calculate position with sine/cosine for smooth looping
            val x = node.baseX + node.offsetX * sin(t * xFreq + xPhase)
            val y = node.baseY + node.offsetY * cos(t * yFreq + yPhase)

            // Ripple: a circular wave front sweeps from center (0.5, 0.5).
            // Wave front travels at 1.6 units/sec; width = 0.25 units.
            val distFromCentre = sqrt(
                (node.baseX - 0.5f) * (node.baseX - 0.5f) +
                        (node.baseY - 0.5f) * (node.baseY - 0.5f)
            ) * sqrt(2f)
            val waveFront  = rp * 1.6f
            val waveWidth  = 0.25f
            val localPhase = (waveFront - distFromCentre) / waveWidth
            val rippleZ    = if (rp > 0f && localPhase in 0f..1f)
                sin(localPhase * PI.toFloat()) * 0.35f * (1f - rp * 0.5f)
            else 0f

            val z = node.zAmplitude * sin(t * zFreq + zPhase) + rippleZ

            // Normalize coordinates
            val normalizedX = (x - 0.5f) * 3.8f
            val normalizedY = (y - 0.5f) * 2.8f

            // Apply tilt rotation
            val rotatedY = normalizedY * cosTilt - z * sinTilt
            val rotatedZ = normalizedY * sinTilt + z * cosTilt
            projected.wx[i] = normalizedX
            projected.wy[i] = rotatedY
            projected.wz[i] = rotatedZ

            // Parallax effect
            val parallaxStrength = node.baseDepth * 60f

            // Perspective projection
            val perspective = cameraZ / (cameraZ - rotatedZ)

            // Convert to screen coordinates
            projected.sx[i] = normalizedX * perspective * width  * 0.48f + width  * 0.5f + tiltX * parallaxStrength
            projected.sy[i] = rotatedY    * perspective * height * 0.48f + height * 0.5f + tiltY * parallaxStrength
        }

        val sx = projected.sx; val sy = projected.sy

        // Faces first, lit by how squarely they turn to the light, so every line sits on top of them
        meshNodes.forEachIndexed { index, node ->
            val row = index / MESH_COLS
            val col = index % MESH_COLS
            if (row >= MESH_ROWS - 1 || col >= MESH_COLS - 1) return@forEachIndexed
            val color = cellColor(row, col, primaryColor, secondaryColor, tertiaryColor)
            val alpha = 0.14f + node.baseDepth * 0.05f
            fillTriangle(trianglePath, projected, index, index + 1, index + MESH_COLS, color, alpha)
            fillTriangle(trianglePath, projected, index + 1, index + MESH_COLS + 1, index + MESH_COLS, color, alpha)
        }

        // Each node owns the edge to its right, the one below it and its cell's diagonal, so every
        // shared edge is drawn once and inner lines read as bright as the border
        meshNodes.forEachIndexed { index, node ->
            val row = index / MESH_COLS
            val col = index % MESH_COLS
            val hasRight = col < MESH_COLS - 1
            val hasBelow = row < MESH_ROWS - 1
            val color = cellColor(row, col, primaryColor, secondaryColor, tertiaryColor)
                .copy(alpha = 0.14f + node.baseDepth * 0.05f)
            val from = Offset(sx[index], sy[index])

            if (hasRight) {
                drawLine(color, from, Offset(sx[index + 1], sy[index + 1]), strokeWidth)
            }
            if (hasBelow) {
                val below = index + MESH_COLS
                drawLine(color, from, Offset(sx[below], sy[below]), strokeWidth)
            }
            if (hasRight && hasBelow) {
                val right = index + 1
                val below = index + MESH_COLS
                drawLine(color, Offset(sx[right], sy[right]), Offset(sx[below], sy[below]), strokeWidth)
            }
        }
    }
}

/** Color of the cell at [row], [col], cycling through the three theme colors across the grid. */
private fun cellColor(row: Int, col: Int, primary: Color, secondary: Color, tertiary: Color) =
    when ((row + col) % 3) {
        0    -> primary
        1    -> secondary
        else -> tertiary
    }

// Faces turned toward this direction catch the most light: up and to the left on screen, and toward
// the camera, which sits on +z
private const val MESH_LIGHT_X = -0.4f
private const val MESH_LIGHT_Y = -0.6f
private const val MESH_LIGHT_Z = 0.7f

/**
 * Fills the triangle [a], [b], [c] of [projected], at [alpha] scaled by the light it catches, so the
 * waves of the mesh read as relief rather than as a flat net.
 */
private fun DrawScope.fillTriangle(
    path: Path,
    projected: MeshProjection,
    a: Int, b: Int, c: Int,
    color: Color,
    alpha: Float
) {
    val wx = projected.wx; val wy = projected.wy; val wz = projected.wz
    val e1x = wx[b] - wx[a]; val e1y = wy[b] - wy[a]; val e1z = wz[b] - wz[a]
    val e2x = wx[c] - wx[a]; val e2y = wy[c] - wy[a]; val e2z = wz[c] - wz[a]
    var nx = e1y * e2z - e1z * e2y
    var ny = e1z * e2x - e1x * e2z
    var nz = e1x * e2y - e1y * e2x
    // Whichever way the triangle is wound, light the side facing the camera
    if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
    val light = (nx * MESH_LIGHT_X + ny * MESH_LIGHT_Y + nz * MESH_LIGHT_Z) / len

    // A flat mesh already catches most of the light, so only what differs from that shows
    val shade = ((light - 0.55f) / 0.4f).coerceIn(0f, 1f)

    path.rewind()
    path.moveTo(projected.sx[a], projected.sy[a])
    path.lineTo(projected.sx[b], projected.sy[b])
    path.lineTo(projected.sx[c], projected.sy[c])
    path.close()
    drawPath(path, color.copy(alpha = alpha * (0.08f + 0.5f * shade)))
}

/** Per-node buffers a frame is projected into: the tilted 3D position and where it lands on screen. */
private class MeshProjection(size: Int) {
    val wx = FloatArray(size)
    val wy = FloatArray(size)
    val wz = FloatArray(size)
    val sx = FloatArray(size)
    val sy = FloatArray(size)
}

private const val MESH_ROWS = 12
private const val MESH_COLS = 12
private const val MESH_GRID_TILT_DEGREES = 10.0

/**
 * Generate mesh grid with varying depth.
 * Random offsets and Z amplitudes create unique motion per node.
 */
private fun generateMeshGrid(): List<MeshNode> {
    val nodes = mutableListOf<MeshNode>()

    for (row in 0 until MESH_ROWS) {
        for (col in 0 until MESH_COLS) {
            val baseX = col / (MESH_COLS - 1f)
            val baseY = row / (MESH_ROWS - 1f)

            // Random offset for chaotic movement
            val offsetX    = (Random.nextFloat() - 0.5f) * 0.04f
            val offsetY    = (Random.nextFloat() - 0.5f) * 0.04f
            val zAmplitude = Random.nextFloat() * 0.15f

            // Calculate depth based on distance from center
            val centerDistX = (baseX - 0.5f) * 2f
            val centerDistY = (baseY - 0.5f) * 2f
            val baseDepth   = sqrt(centerDistX * centerDistX + centerDistY * centerDistY) / sqrt(2f)

            nodes.add(MeshNode(
                baseX      = baseX,
                baseY      = baseY,
                offsetX    = offsetX,
                offsetY    = offsetY,
                baseDepth  = baseDepth,
                zAmplitude = zAmplitude
            ))
        }
    }

    return nodes
}

private data class MeshNode(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val baseDepth: Float, // For parallax effect
    val zAmplitude: Float // For wave animation
)
