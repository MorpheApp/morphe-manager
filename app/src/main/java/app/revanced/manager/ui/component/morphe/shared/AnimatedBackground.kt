package app.revanced.manager.ui.component.morphe.shared

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.backgrounds.*

/**
 * Types of animated backgrounds available in the app
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.morphe_background_type_circles),
    RINGS(R.string.morphe_background_type_rings),
    WAVES(R.string.morphe_background_type_waves),
    PARTICLES(R.string.morphe_background_type_particles),
    SHAPES(R.string.morphe_background_type_shapes),
    NONE(R.string.morphe_background_type_none);

    companion object {
        val DEFAULT = CIRCLES
    }
}

/**
 * Animated background with multiple visual styles
 * Creates subtle floating effects that can be used across all screens
 */
@Composable
@SuppressLint("ModifierParameter")
fun AnimatedBackground(
    type: BackgroundType = BackgroundType.CIRCLES,
    modifier: Modifier = Modifier
) {
    when (type) {
        BackgroundType.CIRCLES -> CirclesBackground(modifier)
        BackgroundType.RINGS -> RingsBackground(modifier)
        BackgroundType.WAVES -> WavesBackground(modifier)
        BackgroundType.PARTICLES -> ParticlesBackground(modifier)
        BackgroundType.SHAPES -> ShapesBackground(modifier)
        BackgroundType.NONE -> {} // No background
    }
}
