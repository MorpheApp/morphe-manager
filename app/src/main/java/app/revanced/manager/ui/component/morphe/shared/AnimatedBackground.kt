package app.revanced.manager.ui.component.morphe.shared

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.backgrounds.*

/**
 * Types of animated backgrounds available in the app
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.morphe_background_type_circles),
    RINGS(R.string.morphe_background_type_rings),
    WAVES(R.string.morphe_background_type_waves),
    SPACE(R.string.morphe_background_type_space),
    SHAPES(R.string.morphe_background_type_shapes),
    SNOW(R.string.morphe_background_type_snow),
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
    type: BackgroundType = BackgroundType.CIRCLES
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        when (type) {
            BackgroundType.CIRCLES -> CirclesBackground(Modifier.fillMaxSize())
            BackgroundType.RINGS -> RingsBackground(Modifier.fillMaxSize())
            BackgroundType.WAVES -> WavesBackground(Modifier.fillMaxSize())
            BackgroundType.SPACE -> SpaceBackground(Modifier.fillMaxSize())
            BackgroundType.SHAPES -> ShapesBackground(Modifier.fillMaxSize())
            BackgroundType.SNOW -> SnowBackground(Modifier.fillMaxSize())
            BackgroundType.NONE -> Unit
        }
    }
}
