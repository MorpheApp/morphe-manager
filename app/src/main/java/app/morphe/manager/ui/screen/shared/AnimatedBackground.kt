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
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.backgrounds.*
import app.morphe.manager.ui.theme.SeasonalEvent

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
    LAVA(R.string.settings_appearance_background_lava),
    MATRIX(R.string.settings_appearance_background_matrix),
    HALLOWEEN(R.string.settings_appearance_background_halloween),
    FIREWORKS(R.string.settings_appearance_background_fireworks),
    NONE(R.string.settings_appearance_background_none),
    RANDOM(R.string.settings_appearance_background_random);

    companion object {
        val DEFAULT = CIRCLES

        /** Types the picker keeps out of sight until they are unlocked. */
        val HIDDEN: Set<BackgroundType> = setOf(MATRIX)

        /** Types worn only during their [SeasonalEvent], never picked by hand or by the shuffle. */
        val SEASONAL: Set<BackgroundType> = setOf(HALLOWEEN, FIREWORKS)

        /** All types that can be picked when RANDOM is active (excludes NONE and RANDOM itself). */
        val RANDOMIZABLE: List<BackgroundType> =
            entries.filter { it != NONE && it != RANDOM && it !in HIDDEN && it !in SEASONAL }

        /**
         * The pool RANDOM draws from. A hidden type joins it only once unlocked, otherwise the
         * shuffle would hand out a background the picker still refuses to show.
         */
        fun randomizable(includeHidden: Boolean): List<BackgroundType> =
            if (includeHidden) RANDOMIZABLE + HIDDEN else RANDOMIZABLE
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
    speedMultiplier: () -> Float = { 1f },
    patchingCompleted: () -> Boolean = { false }
) {
    val effectiveType = if (type == BackgroundType.RANDOM) {
        resolvedType ?: BackgroundType.CIRCLES
    } else {
        type
    }

    val resolvedSpeed = speedMultiplier()
    val resolvedPatchingCompleted = patchingCompleted()

    // No background blends across its own layers, so clipping alone is enough. Compositing offscreen
    // would allocate a full-screen render target and re-composite it every frame for nothing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        when (effectiveType) {
            BackgroundType.CIRCLES -> CirclesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.RINGS -> RingsBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MESH -> MeshBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SPACE -> SpaceBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SHAPES -> ShapesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SNOW -> SnowBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.GRID -> GridBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.PARTICLES -> ParticlesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.LAVA -> LavaBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MATRIX -> MatrixBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.HALLOWEEN -> HalloweenBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.FIREWORKS -> FireworksBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.NONE -> Unit
            // effectiveType is never RANDOM (resolved above), but the branch is required for exhaustiveness
            BackgroundType.RANDOM -> Unit
        }
    }
}
