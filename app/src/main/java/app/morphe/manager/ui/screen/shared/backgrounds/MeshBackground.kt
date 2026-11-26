/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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
    val palette = themePalette()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.4f
    )

    // Keeps each node's random motion the same when a resize lays the grid out again
    val seed = remember { Random.nextInt() }

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

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                // The grid is laid over the canvas grown by the farthest parallax shift on every
                // side, so no lean of the device can pull its border into view
                val layout = MeshLayout(size, parallaxState.maxTilt * MESH_PARALLAX_DP * density, seed)
                val trianglePath = Path()
                onDrawBehind {
                    drawMesh(
                        layout = layout,
                        trianglePath = trianglePath,
                        timeMs = time.value,
                        rippleProgress = rippleProgress.value,
                        tiltX = parallaxState.tiltX.value * density,
                        tiltY = parallaxState.tiltY.value * density,
                        palette = palette
                    )
                }
            }
    )
}

private fun DrawScope.drawMesh(
    layout: MeshLayout,
    trianglePath: Path,
    timeMs: Float,
    rippleProgress: Float,
    tiltX: Float,
    tiltY: Float,
    palette: ThemePalette
) {
    val nodes = layout.nodes
    val cols = layout.cols
    val rows = layout.rows
    val projected = layout.projection
    val strokeWidth = 1.2f * density
    val centerX = size.width * 0.5f
    val centerY = size.height * 0.5f

    // Time runs one way only: half a turn of every wave per 20 000 ms at speed 1x. A clock that
    // ran back and forth would turn every node around at the same instant
    val t = timeMs * PI.toFloat() / 20000f

    nodes.forEachIndexed { i, node ->
        // Unique frequencies and phases for each node - creates chaotic, non-repeating motion
        val xFreq  = 1.0f + node.u * 0.5f
        val yFreq  = 1.2f + node.v * 0.6f
        val zFreq  = 0.8f + (node.u + node.v) * 0.4f
        val xPhase = node.u * TWO_PI
        val yPhase = node.v * 3f * PI.toFloat()
        val zPhase = (node.u + node.v) * 1.5f * PI.toFloat()

        // Calculate position with sine/cosine for smooth looping
        val x = node.worldX + node.offsetX * sin(t * xFreq + xPhase)
        val y = node.worldY + node.offsetY * cos(t * yFreq + yPhase)

        // Ripple: a circular wave front sweeps from the center and reaches the corners at
        // 0.625 progress; width = 0.25 of the way there
        val waveFront  = rippleProgress * 1.6f
        val waveWidth  = 0.25f
        val localPhase = (waveFront - node.baseDepth) / waveWidth
        val rippleZ    = if (rippleProgress > 0f && localPhase in 0f..1f)
            sin(localPhase * PI.toFloat()) * 0.35f * (1f - rippleProgress * 0.5f)
        else 0f

        val z = node.zAmplitude * sin(t * zFreq + zPhase) + rippleZ

        // Apply tilt rotation
        val rotatedY = y * MESH_COS_TILT - z * MESH_SIN_TILT
        val rotatedZ = y * MESH_SIN_TILT + z * MESH_COS_TILT
        projected.wx[i] = x
        projected.wy[i] = rotatedY
        projected.wz[i] = rotatedZ

        // Parallax effect
        val parallaxStrength = node.baseDepth * MESH_PARALLAX_DP

        // Perspective projection
        val perspective = MESH_CAMERA_Z / (MESH_CAMERA_Z - rotatedZ)

        // Convert to screen coordinates
        projected.sx[i] = x * perspective * layout.scale + centerX + tiltX * parallaxStrength
        projected.sy[i] = (rotatedY * perspective + layout.offsetY) * layout.scale + centerY +
                tiltY * parallaxStrength
    }

    val sx = projected.sx; val sy = projected.sy

    // Faces first, lit by how squarely they turn to the light, so every line sits on top of them
    nodes.forEachIndexed { index, node ->
        val row = index / cols
        val col = index % cols
        if (row >= rows - 1 || col >= cols - 1) return@forEachIndexed
        val color = palette[row + col]
        val alpha = 0.14f + node.baseDepth * 0.05f
        fillTriangle(trianglePath, projected, index, index + 1, index + cols, color, alpha)
        fillTriangle(trianglePath, projected, index + 1, index + cols + 1, index + cols, color, alpha)
    }

    // Each node owns the edge to its right, the one below it and its cell's diagonal, so every
    // shared edge is drawn once and inner lines read as bright as the border
    nodes.forEachIndexed { index, node ->
        val row = index / cols
        val col = index % cols
        val hasRight = col < cols - 1
        val hasBelow = row < rows - 1
        val color = palette[row + col]
            .copy(alpha = 0.14f + node.baseDepth * 0.05f)
        val from = Offset(sx[index], sy[index])

        if (hasRight) {
            drawLine(color, from, Offset(sx[index + 1], sy[index + 1]), strokeWidth)
        }
        if (hasBelow) {
            val below = index + cols
            drawLine(color, from, Offset(sx[below], sy[below]), strokeWidth)
        }
        if (hasRight && hasBelow) {
            val right = index + 1
            val below = index + cols
            drawLine(color, Offset(sx[right], sy[right]), Offset(sx[below], sy[below]), strokeWidth)
        }
    }
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

private const val MESH_CAMERA_Z = 1.6f
private val MESH_TILT_RADIANS = Math.toRadians(10.0).toFloat()
private val MESH_COS_TILT = cos(MESH_TILT_RADIANS)
private val MESH_SIN_TILT = sin(MESH_TILT_RADIANS)

// How far the nodes nearest the corners follow a full tilt
private const val MESH_PARALLAX_DP = 20f

// Side of one square cell in world units. The grid adds cells along whichever side is longer instead
// of stretching them, so the mesh keeps its proportions in any orientation
private const val MESH_CELL = 0.2f

// World units the grid spans across and down a frame as long as its longest side. Past the screen
// itself, that leaves room for the perspective to shrink the far edge and for the nodes to wander
private const val MESH_SPAN_X = 3.2f
private const val MESH_SPAN_Y = 2.6f

// One world unit spans this share of the frame's longest side on screen
private const val MESH_SCALE = 0.48f

/**
 * The grid laid out for a canvas of [size], overscanned by [margin] pixels on every side, with the
 * random motion of each node drawn from [seed].
 */
private class MeshLayout(size: Size, margin: Float, seed: Int) {
    val cols: Int
    val rows: Int

    /** Pixels per world unit, the same along both axes. */
    val scale: Float

    /**
     * World offset that centers the flat grid on screen. The tilt swells the near edge and shrinks
     * the far one, so without it the far edge would sit much closer to the top of the screen.
     */
    val offsetY: Float

    val nodes: List<MeshNode>
    val projection: MeshProjection

    init {
        val frameWidth  = size.width  + 2f * margin
        val frameHeight = size.height + 2f * margin
        val longSide = max(frameWidth, frameHeight).coerceAtLeast(1f)
        scale = MESH_SCALE * longSide

        val cellsX = ceil(MESH_SPAN_X * frameWidth  / longSide / MESH_CELL).toInt().coerceAtLeast(1)
        val cellsY = ceil(MESH_SPAN_Y * frameHeight / longSide / MESH_CELL).toInt().coerceAtLeast(1)
        cols = cellsX + 1
        rows = cellsY + 1
        val halfWidth  = cellsX * MESH_CELL / 2f
        val halfHeight = cellsY * MESH_CELL / 2f

        offsetY = -(projectFlatY(-halfHeight) + projectFlatY(halfHeight)) / 2f

        val cornerDistance = hypot(halfWidth, halfHeight)
        nodes = List(rows * cols) { index ->
            val row = index / cols
            val col = index % cols
            val worldX = col * MESH_CELL - halfWidth
            val worldY = row * MESH_CELL - halfHeight
            val random = Random(seed + row * 7919 + col * 104729)

            MeshNode(
                worldX     = worldX,
                worldY     = worldY,
                u          = col / (cols - 1f).coerceAtLeast(1f),
                v          = row / (rows - 1f).coerceAtLeast(1f),
                // Random offset for chaotic movement, up to a fifth of a cell each way
                offsetX    = (random.nextFloat() - 0.5f) * 0.44f * MESH_CELL,
                offsetY    = (random.nextFloat() - 0.5f) * 0.44f * MESH_CELL,
                zAmplitude = random.nextFloat() * 0.15f,
                // Distance from the center, 0 there and 1 in the corners
                baseDepth  = hypot(worldX, worldY) / cornerDistance
            )
        }
        projection = MeshProjection(nodes.size)
    }

    /** Where a node at rest at world height [y] lands on screen, in world units from the center. */
    private fun projectFlatY(y: Float): Float {
        val rotatedZ = y * MESH_SIN_TILT
        return y * MESH_COS_TILT * MESH_CAMERA_Z / (MESH_CAMERA_Z - rotatedZ)
    }
}

private class MeshNode(
    val worldX: Float,
    val worldY: Float,
    val u: Float, // Column as a share of the grid width, seeds the node's wave frequencies and phases
    val v: Float, // Row as a share of the grid height, likewise
    val offsetX: Float,
    val offsetY: Float,
    val zAmplitude: Float, // For wave animation
    val baseDepth: Float   // For parallax effect and the ripple
)
