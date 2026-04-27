/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.backgrounds.*

/**
 * Types of animated backgrounds available in the app.
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.settings_appearance_background_circles),
    RINGS(R.string.settings_appearance_background_rings),
    MESH(R.string.settings_appearance_background_mesh),
    SPACE(R.string.settings_appearance_background_space),
    SHAPES(R.string.settings_appearance_background_shapes),
    SNOW(R.string.settings_appearance_background_snow),
    GRID(R.string.settings_appearance_background_grid),
    PARTICLES(R.string.settings_appearance_background_particles),
    NONE(R.string.settings_appearance_background_none),
    RANDOM(R.string.settings_appearance_background_random);

    companion object {
        val DEFAULT = CIRCLES

        /** All types that can be picked when RANDOM is active (excludes NONE and RANDOM itself). */
        val RANDOMIZABLE: List<BackgroundType> = entries.filter { it != NONE && it != RANDOM }
    }
}

/**
 * Animated background with multiple visual styles.
 * Creates subtle floating effects that can be used across all screens.
 *
 * When [type] is [BackgroundType.RANDOM], [resolvedType] must be provided —
 * it holds the already-resolved random type from the ViewModel so the choice
 * stays stable for the current session/interval.
 */
@Composable
@SuppressLint("ModifierParameter")
fun AnimatedBackground(
    type: BackgroundType = BackgroundType.CIRCLES,
    resolvedType: BackgroundType? = null,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val effectiveType = if (type == BackgroundType.RANDOM) {
        resolvedType ?: BackgroundType.CIRCLES
    } else {
        type
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        when (effectiveType) {
            BackgroundType.CIRCLES -> CirclesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.RINGS -> RingsBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.MESH -> MeshBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.SPACE -> SpaceBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.SHAPES -> ShapesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.SNOW -> SnowBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.GRID -> GridBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.PARTICLES -> ParticlesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = speedMultiplier,
                patchingCompleted = patchingCompleted
            )
            BackgroundType.NONE -> Unit
            // effectiveType is never RANDOM (resolved above), but the branch is required for exhaustiveness
            BackgroundType.RANDOM -> Unit
        }
    }
}
