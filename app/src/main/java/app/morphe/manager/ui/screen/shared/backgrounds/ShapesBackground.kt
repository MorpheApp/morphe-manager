/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True 3D polyhedra floating in space with parallax effect.
 *
 * Each solid is defined by vertices in 3D object space, a face list (for filled rendering),
 * and an edge list (for wireframe overlay).
 * Every solid moves in a way of its own: some wander along Lissajous paths, some circle an orbit,
 * some drift across the whole screen and come back round the other side. On top of that each one
 * spins faster and slower in turn and slowly comes closer and recedes, so no two ever move alike.
 *
 * On patching completion all solids burst outward and spin up, then drift smoothly back.
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

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity    = 0.3f,
        context        = context
    )

    // Solid configurations
    // cx/cy   - base center in normalized screen space [0..1]
    // fx1/fx2 - Lissajous X frequencies; fy1/fy2 - Lissajous Y frequencies
    // ampX/Y  - wander amplitude (normalized screen units)
    // rotSpeeds - per-axis rotation speed multipliers (X=nod, Y=yaw, Z=roll)
    // solidType - which polyhedron to render
    // depth   - parallax depth (0=none, 1=max)
    // scale   - rendered size in dp
    // colorIdx - which theme color to use (0=primary, 1=secondary, 2=tertiary)
    // motion  - how the solid travels, at its own period
    val configs = remember {
        listOf(
            // Top band
            SolidConfig(0.15f, 0.13f, 1.00f, 1.35f, 0.85f, 1.20f, 0.090f, 0.060f, Vec3(0.30f, 0.80f, 0.50f), SolidType.CUBE,         0.80f, 46f, 0,
                motion = SolidMotion.ORBIT_CLOCKWISE, motionPeriod = 46000f, spinPeriod = 17000f, depthPeriod = 31000f, phase = 0.0f),
            SolidConfig(0.50f, 0.18f, 1.45f, 0.75f, 1.10f, 0.65f, 0.075f, 0.055f, Vec3(0.70f, 0.25f, 1.00f), SolidType.TETRAHEDRON,  0.60f, 54f, 1,
                spinPeriod = 13000f, depthPeriod = 27000f, phase = 1.3f),
            SolidConfig(0.84f, 0.10f, 0.85f, 1.60f, 0.70f, 1.40f, 0.085f, 0.065f, Vec3(0.45f, 1.05f, 0.30f), SolidType.OCTAHEDRON,   0.55f, 48f, 2,
                motion = SolidMotion.DRIFT_LEFT, motionPeriod = 72000f, spinPeriod = 21000f, depthPeriod = 38000f, phase = 2.6f),
            // Middle band
            SolidConfig(0.10f, 0.42f, 1.20f, 0.90f, 1.30f, 0.80f, 0.080f, 0.070f, Vec3(0.90f, 0.40f, 0.70f), SolidType.PRISM,        0.45f, 50f, 2,
                spinPeriod = 19000f, depthPeriod = 24000f, phase = 3.9f),
            SolidConfig(0.46f, 0.38f, 0.75f, 1.50f, 0.90f, 1.55f, 0.095f, 0.060f, Vec3(0.50f, 0.70f, 1.20f), SolidType.ICOSAHEDRON,  0.70f, 44f, 0,
                motion = SolidMotion.ORBIT_COUNTERCLOCKWISE, motionPeriod = 58000f, spinPeriod = 23000f, depthPeriod = 35000f, phase = 5.2f),
            SolidConfig(0.82f, 0.50f, 1.55f, 0.80f, 1.20f, 0.75f, 0.085f, 0.075f, Vec3(1.00f, 0.30f, 0.60f), SolidType.CUBE,         0.55f, 42f, 1,
                spinPeriod = 15000f, depthPeriod = 29000f, phase = 0.7f),
            // Bottom band
            SolidConfig(0.22f, 0.72f, 0.90f, 1.30f, 0.75f, 1.10f, 0.080f, 0.065f, Vec3(0.70f, 0.90f, 0.40f), SolidType.TETRAHEDRON,  0.50f, 52f, 2,
                motion = SolidMotion.ORBIT_COUNTERCLOCKWISE, motionPeriod = 41000f, spinPeriod = 18000f, depthPeriod = 26000f, phase = 2.0f),
            SolidConfig(0.58f, 0.68f, 1.30f, 0.70f, 1.50f, 0.90f, 0.075f, 0.070f, Vec3(0.25f, 0.60f, 0.90f), SolidType.OCTAHEDRON,   0.45f, 47f, 0,
                motion = SolidMotion.DRIFT_RIGHT, motionPeriod = 86000f, spinPeriod = 20000f, depthPeriod = 33000f, phase = 4.4f),
            SolidConfig(0.88f, 0.80f, 0.70f, 1.45f, 1.00f, 1.35f, 0.090f, 0.060f, Vec3(0.80f, 0.45f, 0.75f), SolidType.PRISM,        0.65f, 49f, 1,
                spinPeriod = 16000f, depthPeriod = 37000f, phase = 3.1f),
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    val scatterProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1000, fallMillis = 600)

    // Smoothed positions - lerped toward raw Lissajous targets each frame. Plain arrays rather than
    // state: they are written while drawing, where a state write would only schedule another frame
    val smoothedX = remember { FloatArray(configs.size) { configs[it].cx } }
    val smoothedY = remember { FloatArray(configs.size) { configs[it].cy } }
    val scratch = remember { SolidScratch() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t        = time.value
        val tiltX    = parallaxState.tiltX.value
        val tiltY    = parallaxState.tiltY.value
        val twoPi    = 2f * PI.toFloat()
        val sp       = scatterProgress.value
        val screenCx = size.width  * 0.5f
        val screenCy = size.height * 0.5f
        val base     = 18000f

        // Step 1: update smoothed positions
        // Compute each solid's target and lerp in one pass - avoids allocating a
        // temporary rawPositions list each frame.
        configs.forEachIndexed { i, c ->
            val px: Float
            val py: Float
            when (c.motion) {
                SolidMotion.WANDER -> {
                    px = c.cx + c.ampX * sin(t * twoPi * c.fx1 / base) +
                            c.ampX * 0.25f * sin(t * twoPi * c.fx2 / base + 1.4f)
                    py = c.cy + c.ampY * sin(t * twoPi * c.fy1 / base + 0.8f) +
                            c.ampY * 0.20f * cos(t * twoPi * c.fy2 / base + 0.3f)
                }
                SolidMotion.ORBIT_CLOCKWISE, SolidMotion.ORBIT_COUNTERCLOCKWISE -> {
                    val direction = if (c.motion == SolidMotion.ORBIT_CLOCKWISE) 1f else -1f
                    val angle = direction * twoPi * t / c.motionPeriod + c.phase
                    px = c.cx + c.ampX * 1.3f * cos(angle)
                    py = c.cy + c.ampY * 1.6f * sin(angle)
                }
                SolidMotion.DRIFT_LEFT, SolidMotion.DRIFT_RIGHT -> {
                    // Crosses the whole width and comes back round from the other edge, gently
                    // bobbing on the way
                    val direction = if (c.motion == SolidMotion.DRIFT_RIGHT) 1f else -1f
                    val span = 1f + DRIFT_MARGIN * 2f
                    val travel = c.cx + DRIFT_MARGIN + direction * t / c.motionPeriod * span
                    px = travel - floor(travel / span) * span - DRIFT_MARGIN
                    py = c.cy + c.ampY * sin(t * twoPi * c.fy1 / base + c.phase)
                }
            }
            // Coming back round from the other edge is a jump, not a glide across the screen
            if (abs(px - smoothedX[i]) > 0.5f) smoothedX[i] = px
            smoothedX[i] += (px - smoothedX[i]) * 0.04f
            smoothedY[i] += (py - smoothedY[i]) * 0.04f
        }

        // Step 2: draw each solid
        configs.forEachIndexed { index, config ->
            // Slowly comes closer and recedes: larger, brighter and moving more with tilt up close
            val nearness = sin(twoPi * t / config.depthPeriod + config.phase)
            val parallaxStrength = config.depth * 45f * (1f + 0.4f * nearness)
            val baseCx = smoothedX[index] * size.width  + tiltX * parallaxStrength
            val baseCy = smoothedY[index] * size.height + tiltY * parallaxStrength

            // Scatter: fly outward from screen center
            val dirX  = baseCx - screenCx
            val dirY  = baseCy - screenCy
            val eased = 1f - (1f - sp) * (1f - sp)
            val centerX = baseCx + dirX * eased * 1.4f
            val centerY = baseCy + dirY * eased * 1.4f

            // Rotation angles - each axis at its own speed, which swells and ebbs over spinPeriod
            // between nearly still and almost twice as fast, never turning back
            val scatterBoost = sp * (4f + index * 0.3f)
            val spinPhase = twoPi * t / config.spinPeriod + config.phase
            val swell = 0.85f * config.spinPeriod / twoPi * sin(spinPhase)
            val rateX = config.rotSpeeds.x * 0.0004f
            val rateY = config.rotSpeeds.y * 0.0003f
            val rateZ = config.rotSpeeds.z * 0.0002f
            val angleX = rateX * (t + swell) + scatterBoost * 1.2f
            val angleY = rateY * (t + swell) + scatterBoost
            val angleZ = rateZ * (t + swell) + scatterBoost * 0.8f

            val restingAlpha = 0.20f * (1f + 0.25f * nearness)
            val baseAlpha = if (sp > 0f) (1f - sp).coerceIn(0f, 1f) * restingAlpha else restingAlpha
            val color = when (config.colorIdx) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }

            drawSolid(
                scratch = scratch,
                solid   = config.solidType.def,
                angleX  = angleX,
                angleY  = angleY,
                angleZ  = angleZ,
                cx      = centerX,
                cy      = centerY,
                scale   = config.scale * density * (1f + 0.18f * nearness),
                color   = color,
                alpha   = baseAlpha
            )
        }
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: Vec3)  = Vec3(x + o.x, y + o.y, z + o.z)
    fun cross(o: Vec3)          = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: Vec3)            = x * o.x + y * o.y + z * o.z
}

// Faces turned toward this direction catch the most light: up and to the left on screen, and
// toward the viewer, who looks along +z
private const val LIGHT_X = -0.45f
private const val LIGHT_Y = -0.55f
private const val LIGHT_Z = -0.70f

private const val PERSPECTIVE_FOCAL = 2000f
private const val PERSPECTIVE_CAMERA_Z = 4f

private const val MAX_VERTICES = 12
private const val MAX_FACES = 20

/**
 * Buffers every solid is rotated, projected and sorted in, reused across solids and frames so
 * drawing allocates nothing. Sized for the largest solid, the icosahedron.
 */
private class SolidScratch {
    val rx = FloatArray(MAX_VERTICES)
    val ry = FloatArray(MAX_VERTICES)
    val rz = FloatArray(MAX_VERTICES)
    val px = FloatArray(MAX_VERTICES)
    val py = FloatArray(MAX_VERTICES)
    val faceDepth = FloatArray(MAX_FACES)
    val faceLight = FloatArray(MAX_FACES)
    val order = IntArray(MAX_FACES)
    val path = Path()
}

/**
 * Renders a polyhedron:
 * 1. Rotate and perspective-project all vertices.
 * 2. Find the faces turned toward the viewer, with their depth and how much light they catch.
 * 3. Fill those faces back to front, brighter the more squarely they face the light.
 * 4. Draw ALL edges exactly once via the deduplicated edge list -
 *    front edges at full alpha, back edges at ghost alpha.
 */
private fun DrawScope.drawSolid(
    scratch: SolidScratch,
    solid: SolidDef,
    angleX: Float, angleY: Float, angleZ: Float,
    cx: Float, cy: Float,
    scale: Float,
    color: Color,
    alpha: Float
) {
    val cosX = cos(angleX); val sinX = sin(angleX)
    val cosY = cos(angleY); val sinY = sin(angleY)
    val cosZ = cos(angleZ); val sinZ = sin(angleZ)
    val rx = scratch.rx; val ry = scratch.ry; val rz = scratch.rz
    val px = scratch.px; val py = scratch.py

    // Step 1 - rotate around X (nod), Y (yaw), then Z (roll), and perspective-project
    solid.vertices.forEachIndexed { i, v ->
        val y1 = v.y * cosX - v.z * sinX
        val z1 = v.y * sinX + v.z * cosX
        val x2 =  v.x * cosY + z1 * sinY
        val z2 = -v.x * sinY + z1 * cosY
        rx[i] = x2 * cosZ - y1 * sinZ
        ry[i] = x2 * sinZ + y1 * cosZ
        rz[i] = z2
        // Z contribution is fixed (not scaled by shape size) so perspective distortion
        // stays constant regardless of scale - prevents large solids looking trapezoidal.
        val perspective = PERSPECTIVE_FOCAL / (PERSPECTIVE_FOCAL + (PERSPECTIVE_CAMERA_Z + z2) * 70f)
        px[i] = cx + rx[i] * scale * perspective
        py[i] = cy + ry[i] * scale * perspective
    }

    // Step 2 - faces point their normals outward and the viewer looks along +z, so a face is
    // visible when its normal has a negative z
    var visible = 0
    solid.faces.forEachIndexed { f, idx ->
        val a = idx[0]; val b = idx[1]; val c = idx[2]
        val e1x = rx[b] - rx[a]; val e1y = ry[b] - ry[a]; val e1z = rz[b] - rz[a]
        val e2x = rx[c] - rx[a]; val e2y = ry[c] - ry[a]; val e2z = rz[c] - rz[a]
        val nx = e1y * e2z - e1z * e2y
        val ny = e1z * e2x - e1x * e2z
        val nz = e1x * e2y - e1y * e2x
        if (nz >= 0f) return@forEachIndexed // back-face cull
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        var depth = 0f
        for (i in idx) depth += rz[i]
        scratch.faceDepth[f] = depth / idx.size
        scratch.faceLight[f] = ((nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / len).coerceAtLeast(0f)
        // Insertion sort into back-to-front order: never more than 20 faces
        var j = visible
        while (j > 0 && scratch.faceDepth[scratch.order[j - 1]] < scratch.faceDepth[f]) {
            scratch.order[j] = scratch.order[j - 1]
            j--
        }
        scratch.order[j] = f
        visible++
    }

    // Step 3 - fill, the side turned to the light reading brighter than the side turned away
    val path = scratch.path
    for (k in 0 until visible) {
        val f = scratch.order[k]
        val idx = solid.faces[f]
        path.rewind()
        path.moveTo(px[idx[0]], py[idx[0]])
        for (i in 1 until idx.size) path.lineTo(px[idx[i]], py[idx[i]])
        path.close()
        drawPath(path, color.copy(alpha = alpha * (0.12f + 0.42f * scratch.faceLight[f])))
    }

    // Step 4 - draw every edge exactly once with depth-based alpha.
    // Avoid any per-face visibility check here - that causes flickering when a face
    // crosses the horizon (normalZ flips sign) and adjacent edges change alpha abruptly.
    // Instead, derive alpha smoothly from the average Z of the two endpoint vertices:
    //   z ranges roughly -1...+1 after rotation, nearest at -1; map to [frontAlpha...backAlpha] linearly.
    for (e in solid.edgeStarts.indices) {
        val a = solid.edgeStarts[e]; val b = solid.edgeEnds[e]
        val avgZ      = (rz[a] + rz[b]) * 0.5f
        // Remap [+1,-1] → [0,1] then blend between ghost and solid alpha
        val t         = ((1f - avgZ) * 0.5f).coerceIn(0f, 1f)
        val edgeAlpha = (alpha * 0.15f + t * alpha * 0.85f).coerceIn(0f, 1f)
        drawLine(
            color       = color.copy(alpha = edgeAlpha),
            start       = Offset(px[a], py[a]),
            end         = Offset(px[b], py[b]),
            strokeWidth = 2.0f
        )
    }
}

/**
 * A polyhedron defined by:
 * - [vertices]  - positions in normalized [-1,1] object space, centered on the origin
 * - [faces]     - vertex index lists, each wound so its normal points outward
 * - [edgeStarts] / [edgeEnds] - deduplicated pairs with start < end, for wireframe drawing
 */
private class SolidDef(
    val vertices: List<Vec3>,
    val faces: List<IntArray>,
    val edgeStarts: IntArray,
    val edgeEnds: IntArray
) {
    companion object {
        /**
         * Builds the solid from a face list and derives the deduplicated edges. A face wound inward
         * is turned around, so culling and lighting hold however the list was written: on a convex
         * solid around the origin, every face points away from it.
         */
        fun build(vertices: List<Vec3>, faces: List<List<Int>>): SolidDef {
            val oriented = faces.map { face ->
                val v0 = vertices[face[0]]; val v1 = vertices[face[1]]; val v2 = vertices[face[2]]
                val normal = (v1 - v0).cross(v2 - v0)
                val centroid = face.map { vertices[it] }.reduce(Vec3::plus)
                if (normal.dot(centroid) < 0f) face.reversed() else face
            }
            val edgeSet = LinkedHashSet<Pair<Int, Int>>()
            for (face in oriented) {
                for (i in face.indices) {
                    val a = face[i]; val b = face[(i + 1) % face.size]
                    edgeSet += if (a < b) Pair(a, b) else Pair(b, a)
                }
            }
            return SolidDef(
                vertices = vertices,
                faces = oriented.map { it.toIntArray() },
                edgeStarts = edgeSet.map { it.first }.toIntArray(),
                edgeEnds = edgeSet.map { it.second }.toIntArray()
            )
        }
    }
}

private enum class SolidType {
    CUBE, TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON, PRISM;

    // Cached - built once at class-load time, never rebuilt during animation
    val def: SolidDef by lazy { buildDef() }

    private fun buildDef(): SolidDef = when (this) {

        // Vertices:  0=LBB  1=RBB  2=RTB  3=LTB  (B=back, F=front, L=left, R=right, T=top, Bo=bottom)
        //            4=LBF  5=RBF  6=RTF  7=LTF
        CUBE -> SolidDef.build(
            vertices = listOf(
                Vec3(-1f, -1f, -1f), Vec3( 1f, -1f, -1f),
                Vec3( 1f,  1f, -1f), Vec3(-1f,  1f, -1f),
                Vec3(-1f, -1f,  1f), Vec3( 1f, -1f,  1f),
                Vec3( 1f,  1f,  1f), Vec3(-1f,  1f,  1f)
            ),
            faces = listOf(
                listOf(0, 3, 2, 1), // back   (normal: 0,0,-1)
                listOf(4, 5, 6, 7), // front  (normal: 0,0,+1)
                listOf(0, 1, 5, 4), // bottom (normal: 0,-1,0)
                listOf(3, 7, 6, 2), // top    (normal: 0,+1,0)
                listOf(0, 4, 7, 3), // left   (normal:-1,0,0)
                listOf(1, 2, 6, 5)  // right  (normal:+1,0,0)
            )
        )

        TETRAHEDRON -> {
            val s = sqrt(2f / 3f)
            val t = sqrt(2f) / 3f
            val h = 1f / 3f
            SolidDef.build(
                vertices = listOf(
                    Vec3( 0f,      1f,       0f),
                    Vec3( 2f * s,  -h,       0f),
                    Vec3(-s,       -h,  sqrt(3f) * t),
                    Vec3(-s,       -h, -sqrt(3f) * t)
                ),
                faces = listOf(
                    listOf(0, 1, 2),
                    listOf(0, 2, 3),
                    listOf(0, 3, 1),
                    listOf(1, 3, 2)
                )
            )
        }

        OCTAHEDRON -> SolidDef.build(
            vertices = listOf(
                Vec3( 0f,  1f,  0f), // top
                Vec3( 1f,  0f,  0f), // right
                Vec3( 0f,  0f,  1f), // front
                Vec3(-1f,  0f,  0f), // left
                Vec3( 0f,  0f, -1f), // back
                Vec3( 0f, -1f,  0f)  // bottom
            ),
            faces = listOf(
                listOf(0, 1, 2), listOf(0, 2, 3),
                listOf(0, 3, 4), listOf(0, 4, 1),
                listOf(5, 2, 1), listOf(5, 3, 2),
                listOf(5, 4, 3), listOf(5, 1, 4)
            )
        )

        // Uses the standard golden-ratio construction.
        ICOSAHEDRON -> {
            val phi = (1f + sqrt(5f)) / 2f
            val n   = 1f / sqrt(1f + phi * phi)
            val b = n * phi
            SolidDef.build(
                vertices = listOf(
                    Vec3( 0f, n,  b), Vec3( 0f, -n,  b), Vec3( 0f, n, -b), Vec3( 0f, -n, -b),
                    Vec3(n,  b,  0f), Vec3(-n,  b,  0f), Vec3(n, -b,  0f), Vec3(-n, -b,  0f),
                    Vec3( b,  0f, n), Vec3(-b,  0f, n), Vec3( b,  0f, -n), Vec3(-b,  0f, -n)
                ),
                faces = listOf(
                    // Top cap (5 faces around vertex 4)
                    listOf(4, 0, 5), listOf(4, 5, 2), listOf(4, 2,10),
                    listOf(4,10, 8), listOf(4, 8, 0),
                    // Upper band
                    listOf(0, 8, 1), listOf(0, 1, 9), listOf(0, 9, 5),
                    listOf(5, 9,11), listOf(5,11, 2), listOf(2,11, 3),
                    listOf(2, 3,10), listOf(10, 3, 6),listOf(10, 6, 8),
                    listOf(8, 6, 1),
                    // Bottom cap (5 faces around vertex 7)
                    listOf(7, 1, 6), listOf(7, 6, 3), listOf(7, 3,11),
                    listOf(7,11, 9), listOf(7, 9, 1)
                )
            )
        }

        // Bottom cap: v0,v1,v2 (y=-h). Top cap: v3,v4,v5 (y=+h).
        PRISM -> {
            val r = 1f
            val h = 0.9f
            val verts: List<Vec3> = List(6) { i ->
                val ang = i % 3 * 2f * PI.toFloat() / 3f - PI.toFloat() / 6f
                val y   = if (i < 3) -h else h
                Vec3(cos(ang) * r, y, sin(ang) * r)
            }
            SolidDef.build(
                vertices = verts,
                faces = listOf(
                    listOf(2, 1, 0),    // bottom cap
                    listOf(3, 4, 5),    // top cap
                    listOf(0, 1, 4, 3), // side A
                    listOf(1, 2, 5, 4), // side B
                    listOf(2, 0, 3, 5)  // side C
                )
            )
        }
    }
}

private data class SolidConfig(
    val cx: Float,          // Base center X (normalized 0..1)
    val cy: Float,          // Base center Y (normalized 0..1)
    val fx1: Float,         // Primary X Lissajous frequency
    val fx2: Float,         // Secondary X Lissajous frequency
    val fy1: Float,         // Primary Y Lissajous frequency
    val fy2: Float,         // Secondary Y Lissajous frequency
    val ampX: Float,        // Horizontal wander amplitude
    val ampY: Float,        // Vertical wander amplitude
    val rotSpeeds: Vec3,    // Per-axis rotation speed multipliers
    val solidType: SolidType,
    val depth: Float,       // Parallax depth
    val scale: Float,       // Rendered size in dp
    val colorIdx: Int,      // 0=primary, 1=secondary, 2=tertiary
    val motion: SolidMotion = SolidMotion.WANDER,
    val motionPeriod: Float = 0f, // Milliseconds per orbit or per crossing of the screen
    val spinPeriod: Float,  // Milliseconds for the spin to swell and ebb once
    val depthPeriod: Float, // Milliseconds to come close and recede once
    val phase: Float        // Offsets every cycle of the solid, so they never move in step
)

/** How a solid travels across the screen. */
private enum class SolidMotion {
    /** Wanders around its place along a Lissajous path. */
    WANDER,
    ORBIT_CLOCKWISE,
    ORBIT_COUNTERCLOCKWISE,
    /** Crosses the whole width, then comes back round from the other edge. */
    DRIFT_LEFT,
    DRIFT_RIGHT
}

// How far past either edge a drifting solid travels before it comes back round, as a fraction
// of the width, so it leaves and enters fully out of view
private const val DRIFT_MARGIN = 0.15f
