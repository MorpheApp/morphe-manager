/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Metaballs: every blob adds r² / d² to a field, and wherever the field passes 1 is wax. Two blobs
// close enough lift the field between them over that line too, which is what makes them merge.
// Each blob is stretched along y by its w, as wax is while it rises or sinks
private const val LAVA_SHADER = """
uniform float4 blobs[8];
uniform float4 colors[8];
uniform float alpha;

half4 main(float2 p) {
    float field = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float4 b = blobs[i];
        float2 d = p - b.xy;
        d.y /= b.w;
        float f = b.z * b.z / max(dot(d, d), 1.0);
        field += f;
        color += colors[i].rgb * f;
    }
    color /= max(field, 0.0001);
    // A crisp body with a faint halo around it, so the wax reads as lit from inside
    float body = smoothstep(0.85, 1.15, field);
    float halo = smoothstep(0.3, 0.9, field) * 0.3;
    float a = max(body, halo) * alpha;
    return half4(color * a, a);
}
"""

private const val BLOB_COUNT = 8

/**
 * Lava lamp background: wax blobs in the theme colors rise and sink at their own slow pace,
 * stretching as they move and pausing as they turn, and on Android 13 and up merge and part like
 * real wax. Older versions draw the same motion as soft blobs that only overlap.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion the lamp heats up: every blob swells, brightens and surges upward, then
 * settles back.
 */
@Composable
fun LavaBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val palette = themePalette()
    val waxAlpha = if (isDarkTheme()) 0.14f else 0.11f

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f
    )

    val time = rememberAnimatedTime(speedMultiplier)

    // heat 0→1: the lamp warms up on completion, then cools back down
    val heat = rememberCompletionPulse(patchingCompleted, riseMillis = 1100, fallMillis = 1600)

    val lava = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LavaShader() else null
    }
    val blobs = remember { LavaBlobs() }
    // Older versions draw each blob as a glow, one gradient per theme color built once
    val softGlows = remember(palette) {
        if (lava == null) List(3) { softGlowFor(palette[it]) } else null
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        blobs.update(
            t = time.value,
            size = size,
            density = density,
            tiltX = parallaxState.tiltX.value * density,
            tiltY = parallaxState.tiltY.value * density,
            heat = heat.value
        )
        val alpha = waxAlpha * (1f + heat.value * 0.8f)

        if (lava != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawRect(lava.brushFor(blobs, palette, alpha))
        } else if (softGlows != null) {
            drawSoftBlobs(blobs, softGlows, alpha)
        }
    }
}

/**
 * The blobs of the lamp at the current moment, in pixels, recomputed every frame from the clock
 * alone, so the lamp holds no state that could drift.
 */
private class LavaBlobs {
    val x = FloatArray(BLOB_COUNT)
    val y = FloatArray(BLOB_COUNT)
    val radius = FloatArray(BLOB_COUNT)
    val stretch = FloatArray(BLOB_COUNT)

    fun update(t: Float, size: Size, density: Float, tiltX: Float, tiltY: Float, heat: Float) {
        LAVA_BLOBS.forEachIndexed { i, blob ->
            // Rising and sinking along a sine: fastest mid-lamp, slowing to a pause at either end
            val climb = TWO_PI * t / blob.climbPeriod + blob.phase
            val sway = sin(TWO_PI * t / blob.swayPeriod + blob.phase * 1.7f)
            val breath = sin(TWO_PI * t / blob.breathPeriod + blob.phase * 0.6f)

            val parallax = blob.depth * 17f
            x[i] = (blob.x + 0.05f * sway) * size.width + tiltX * parallax
            y[i] = (0.5f - blob.travel * sin(climb) - heat * 0.12f) * size.height + tiltY * parallax
            radius[i] = blob.radius * density * (1f + 0.09f * breath) * (1f + heat * 0.35f)
            // Stretched tall while it moves, round again where it turns
            stretch[i] = 1f + 0.4f * abs(cos(climb))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LavaShader {
    private val shader = RuntimeShader(LAVA_SHADER)
    private val brush = ShaderBrush(shader)
    private val blobUniform = FloatArray(BLOB_COUNT * 4)
    private val colorUniform = FloatArray(BLOB_COUNT * 4)

    fun brushFor(blobs: LavaBlobs, palette: ThemePalette, alpha: Float): Brush {
        for (i in 0 until BLOB_COUNT) {
            blobUniform[i * 4] = blobs.x[i]
            blobUniform[i * 4 + 1] = blobs.y[i]
            blobUniform[i * 4 + 2] = blobs.radius[i]
            blobUniform[i * 4 + 3] = blobs.stretch[i]
            val color = palette[LAVA_BLOBS[i].colorIndex]
            colorUniform[i * 4] = color.red
            colorUniform[i * 4 + 1] = color.green
            colorUniform[i * 4 + 2] = color.blue
            colorUniform[i * 4 + 3] = 1f
        }
        shader.setFloatUniform("blobs", blobUniform)
        shader.setFloatUniform("colors", colorUniform)
        shader.setFloatUniform("alpha", alpha)
        return brush
    }
}

/** The same lamp without a shader: soft stretched blobs that overlap instead of merging. */
private fun DrawScope.drawSoftBlobs(blobs: LavaBlobs, glows: List<Brush>, alpha: Float) {
    for (i in 0 until BLOB_COUNT) {
        val radius = blobs.radius[i] * 1.3f
        drawRadialGlow(
            glow = glows[LAVA_BLOBS[i].colorIndex],
            center = Offset(blobs.x[i], blobs.y[i]),
            radiusX = radius,
            radiusY = radius * blobs.stretch[i],
            alpha = alpha
        )
    }
}

private fun softGlowFor(color: Color) = unitRadialGlow(
    0f to color,
    0.6f to color.copy(alpha = 0.8f),
    1f to Color.Transparent
)

/**
 * One blob of wax.
 *
 * @param x Resting horizontal position, as a fraction of the width.
 * @param travel How far it climbs above and sinks below the middle, as a fraction of the height.
 * @param radius Size in dp.
 * @param climbPeriod Milliseconds for a full rise and fall, slow and different for every blob.
 */
private class LavaBlob(
    val x: Float,
    val travel: Float,
    val radius: Float,
    val climbPeriod: Float,
    val swayPeriod: Float,
    val breathPeriod: Float,
    val phase: Float,
    val depth: Float,
    val colorIndex: Int
)

private val LAVA_BLOBS = listOf(
    LavaBlob(0.22f, 0.36f, 64f, 34000f, 13000f, 9000f, 0.0f, 0.8f, 0),
    LavaBlob(0.70f, 0.40f, 52f, 41000f, 15000f, 11000f, 2.1f, 0.6f, 2),
    LavaBlob(0.45f, 0.30f, 46f, 29000f, 11000f, 8000f, 4.0f, 0.5f, 1),
    LavaBlob(0.82f, 0.34f, 58f, 37000f, 17000f, 10000f, 1.2f, 0.7f, 0),
    LavaBlob(0.15f, 0.42f, 42f, 45000f, 12000f, 12000f, 3.3f, 0.4f, 2),
    LavaBlob(0.58f, 0.38f, 60f, 32000f, 14000f, 9500f, 5.1f, 0.65f, 1),
    LavaBlob(0.35f, 0.44f, 38f, 39000f, 16000f, 8500f, 0.7f, 0.45f, 0),
    LavaBlob(0.90f, 0.28f, 44f, 43000f, 10000f, 10500f, 2.8f, 0.55f, 1)
)
