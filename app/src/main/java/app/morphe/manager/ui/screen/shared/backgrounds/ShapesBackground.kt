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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True 3D polyhedra floating in space with parallax effect.
 *
 * Each solid is defined by vertices in 3D object space, a face list (for filled rendering),
 * and an edge list (for wireframe overlay).
 * Solids drift along Lissajous paths (two independent sine frequencies per axis) so no
 * two ever follow the same trajectory.
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
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity    = 0.3f,
        context        = context,
        coroutineScope = coroutineScope
    )

    // Solid configurations
    // cx/cy   - base centre in normalised screen space [0..1]
    // fx1/fx2 - Lissajous X frequencies; fy1/fy2 - Lissajous Y frequencies
    // ampX/Y  - wander amplitude (normalised screen units)
    // rotSpeeds - per-axis rotation speed multipliers (X=nod, Y=yaw, Z=roll)
    // solidType - which polyhedron to render
    // depth   - parallax depth (0=none, 1=max)
    // scale   - rendered size in pixels
    // colorIdx - which theme color to use (0=primary, 1=secondary, 2=tertiary)
    val configs = remember {
        listOf(
            // Top band
            SolidConfig(0.15f, 0.13f, 1.00f, 1.35f, 0.85f, 1.20f, 0.090f, 0.060f, Vec3(0.30f, 0.80f, 0.50f), SolidType.CUBE,         0.80f, 138f, 0),
            SolidConfig(0.50f, 0.18f, 1.45f, 0.75f, 1.10f, 0.65f, 0.075f, 0.055f, Vec3(0.70f, 0.25f, 1.00f), SolidType.TETRAHEDRON,  0.60f, 162f, 1),
            SolidConfig(0.84f, 0.10f, 0.85f, 1.60f, 0.70f, 1.40f, 0.085f, 0.065f, Vec3(0.45f, 1.05f, 0.30f), SolidType.OCTAHEDRON,   0.55f, 144f, 2),
            // Middle band
            SolidConfig(0.10f, 0.42f, 1.20f, 0.90f, 1.30f, 0.80f, 0.080f, 0.070f, Vec3(0.90f, 0.40f, 0.70f), SolidType.PRISM,        0.45f, 150f, 2),
            SolidConfig(0.46f, 0.38f, 0.75f, 1.50f, 0.90f, 1.55f, 0.095f, 0.060f, Vec3(0.50f, 0.70f, 1.20f), SolidType.ICOSAHEDRON,  0.70f, 132f, 0),
            SolidConfig(0.82f, 0.50f, 1.55f, 0.80f, 1.20f, 0.75f, 0.085f, 0.075f, Vec3(1.00f, 0.30f, 0.60f), SolidType.CUBE,         0.55f, 125f, 1),
            // Bottom band
            SolidConfig(0.22f, 0.72f, 0.90f, 1.30f, 0.75f, 1.10f, 0.080f, 0.065f, Vec3(0.70f, 0.90f, 0.40f), SolidType.TETRAHEDRON,  0.50f, 156f, 2),
            SolidConfig(0.58f, 0.68f, 1.30f, 0.70f, 1.50f, 0.90f, 0.075f, 0.070f, Vec3(0.25f, 0.60f, 0.90f), SolidType.OCTAHEDRON,   0.45f, 140f, 0),
            SolidConfig(0.88f, 0.80f, 0.70f, 1.45f, 1.00f, 1.35f, 0.090f, 0.060f, Vec3(0.80f, 0.45f, 0.75f), SolidType.PRISM,        0.65f, 148f, 1),
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    val scatterProgress = remember { Animatable(0f) }
    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            scatterProgress.snapTo(0f)
            scatterProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            scatterProgress.animateTo(0f, tween(600,  easing = FastOutSlowInEasing))
        }
    }

    // Smoothed positions - lerped toward raw Lissajous targets each frame
    val smoothedPositions = remember { mutableStateListOf<Offset>() }
    if (smoothedPositions.isEmpty()) smoothedPositions.addAll(configs.map { Offset(it.cx, it.cy) })

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
        // Compute Lissajous target and lerp in one pass - avoids allocating a
        // temporary rawPositions list each frame.
        configs.forEachIndexed { i, c ->
            val px = c.cx + c.ampX * sin(t * twoPi * c.fx1 / base) +
                    c.ampX * 0.25f * sin(t * twoPi * c.fx2 / base + 1.4f)
            val py = c.cy + c.ampY * sin(t * twoPi * c.fy1 / base + 0.8f) +
                    c.ampY * 0.20f * cos(t * twoPi * c.fy2 / base + 0.3f)
            smoothedPositions[i] = lerp(smoothedPositions[i], Offset(px, py), 0.04f)
        }

        // Step 2: draw each solid
        configs.forEachIndexed { index, config ->
            val pos = smoothedPositions[index]

            val parallaxStrength = config.depth * 45f
            val baseCx = pos.x * size.width  + tiltX * parallaxStrength
            val baseCy = pos.y * size.height + tiltY * parallaxStrength

            // Scatter: fly outward from screen centre
            val dirX  = baseCx - screenCx
            val dirY  = baseCy - screenCy
            val eased = 1f - (1f - sp) * (1f - sp)
            val centerX = baseCx + dirX * eased * 1.4f
            val centerY = baseCy + dirY * eased * 1.4f

            // Rotation angles - each axis at its own speed
            val scatterBoost = sp * (4f + index * 0.3f)
            val angleX = t * config.rotSpeeds.x * 0.0004f + scatterBoost * 1.2f
            val angleY = t * config.rotSpeeds.y * 0.0003f + scatterBoost
            val angleZ = t * config.rotSpeeds.z * 0.0002f + scatterBoost * 0.8f

            val baseAlpha = if (sp > 0f) (1f - sp).coerceIn(0f, 1f) * 0.20f else 0.20f
            val color = when (config.colorIdx) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }

            drawSolid(
                solid   = config.solidType.def,
                angleX  = angleX,
                angleY  = angleY,
                angleZ  = angleZ,
                cx      = centerX,
                cy      = centerY,
                scale   = config.scale,
                color   = color,
                alpha   = baseAlpha
            )
        }
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun cross(o: Vec3)          = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        return Vec3(x / len, y / len, z / len)
    }
}

/** Rotates a vertex around X, Y, Z axes (intrinsic Tait-Bryan order). */
private fun rotateVertex(v: Vec3, cosX: Float, sinX: Float,
                         cosY: Float, sinY: Float,
                         cosZ: Float, sinZ: Float): Vec3 {
    // X axis (nod)
    val x1 = v.x
    val y1 = v.y * cosX - v.z * sinX
    val z1 = v.y * sinX + v.z * cosX
    // Y axis (yaw)
    val x2 =  x1 * cosY + z1 * sinY
    val z2 = -x1 * sinY + z1 * cosY
    // Z axis (roll)
    val x3 = x2 * cosZ - y1 * sinZ
    val y3 = x2 * sinZ + y1 * cosZ
    return Vec3(x3, y3, z2)
}

/** Perspective-projects a rotated vertex to screen space. */
private fun projectVertex(v: Vec3, cx: Float, cy: Float, scale: Float,
                          focalLength: Float = 2000f, cameraZ: Float = 4f): Offset {
    // Z contribution is fixed (not scaled by shape size) so perspective distortion
    // stays constant regardless of scale - prevents large solids looking trapezoidal.
    val perspective = focalLength / (focalLength + (cameraZ + v.z) * 70f)
    return Offset(cx + v.x * scale * perspective, cy + v.y * scale * perspective)
}

/**
 * Renders a polyhedron:
 * 1. Rotate all vertices.
 * 2. Compute face normals + depths, sort back-to-front.
 * 3. Fill visible faces (painter's order).
 * 4. Draw ALL edges exactly once via the deduplicated edge list -
 *    front edges at full alpha, back edges at ghost alpha.
 */
private fun DrawScope.drawSolid(
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

    // Step 1 - rotate all vertices, project all to screen
    val rotated   = solid.vertices.map { v -> rotateVertex(v, cosX, sinX, cosY, sinY, cosZ, sinZ) }
    val projected = rotated.map { v -> projectVertex(v, cx, cy, scale) }

    // Step 2 - compute face info
    data class FaceInfo(val indices: List<Int>, val depth: Float, val normalZ: Float)
    val faceInfos = solid.faces.map { idx ->
        val v0 = rotated[idx[0]]; val v1 = rotated[idx[1]]; val v2 = rotated[idx[2]]
        val normal = (v1 - v0).cross(v2 - v0).normalized()
        val depth  = idx.sumOf { rotated[it].z.toDouble() }.toFloat() / idx.size
        FaceInfo(idx, depth, normal.z)
    }
    // Stable back-to-front sort
    val sorted = faceInfos.sortedWith(compareByDescending { it.depth })

    // Step 3 - fill visible faces only
    sorted.forEach { face ->
        if (face.normalZ >= 0f) return@forEach // back-face cull
        val pts = face.indices.map { projected[it] }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, color.copy(alpha = alpha * 0.28f))
    }

    // Step 4 - draw every edge exactly once with depth-based alpha.
    // Avoid any per-face visibility check here - that causes flickering when a face
    // crosses the horizon (normalZ flips sign) and adjacent edges change alpha abruptly.
    // Instead, derive alpha smoothly from the average Z of the two endpoint vertices:
    //   z ranges roughly -1..+1 after rotation; map to [backAlpha..frontAlpha] linearly.
    solid.edges.forEach { (a, b) ->
        val avgZ      = (rotated[a].z + rotated[b].z) * 0.5f
        // Remap [-1,+1] → [0,1] then blend between ghost and solid alpha
        val t         = ((avgZ + 1f) * 0.5f).coerceIn(0f, 1f)
        val edgeAlpha = (alpha * 0.15f + t * alpha * 0.85f).coerceIn(0f, 1f)
        drawLine(
            color       = color.copy(alpha = edgeAlpha),
            start       = projected[a],
            end         = projected[b],
            strokeWidth = 2.0f
        )
    }
}

/**
 * A polyhedron defined by:
 * - [vertices]  - positions in normalised [-1,1] object space
 * - [faces]     - vertex index lists, each wound CCW when viewed from outside
 * - [edges]     - deduplicated pairs (a,b) with a<b, for wireframe drawing
 */
private data class SolidDef(
    val vertices: List<Vec3>,
    val faces: List<List<Int>>,
    val edges: List<Pair<Int,Int>>
) {
    companion object {
        /** Builds the deduplicated edge set automatically from a face list. */
        fun build(vertices: List<Vec3>, faces: List<List<Int>>): SolidDef {
            val edgeSet = LinkedHashSet<Pair<Int,Int>>()
            for (face in faces) {
                for (i in face.indices) {
                    val a = face[i]; val b = face[(i + 1) % face.size]
                    edgeSet += if (a < b) Pair(a, b) else Pair(b, a)
                }
            }
            return SolidDef(vertices, faces, edgeSet.toList())
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
        // All faces wound CCW when viewed from outside.
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

        // Uses the standard golden-ratio construction. All 20 faces wound CCW from outside.
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
        // Side faces wound CCW from outside.
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
                    listOf(2, 1, 0),    // bottom cap (CCW from below)
                    listOf(3, 4, 5),    // top cap    (CCW from above)
                    listOf(0, 1, 4, 3), // side A
                    listOf(1, 2, 5, 4), // side B
                    listOf(2, 0, 3, 5)  // side C
                )
            )
        }
    }
}

private data class SolidConfig(
    val cx: Float,          // Base centre X (normalised 0..1)
    val cy: Float,          // Base centre Y (normalised 0..1)
    val fx1: Float,         // Primary X Lissajous frequency
    val fx2: Float,         // Secondary X Lissajous frequency
    val fy1: Float,         // Primary Y Lissajous frequency
    val fy2: Float,         // Secondary Y Lissajous frequency
    val ampX: Float,        // Horizontal wander amplitude
    val ampY: Float,        // Vertical wander amplitude
    val rotSpeeds: Vec3,    // Per-axis rotation speed multipliers
    val solidType: SolidType,
    val depth: Float,       // Parallax depth
    val scale: Float,       // Rendered size in pixels
    val colorIdx: Int       // 0=primary, 1=secondary, 2=tertiary
)
