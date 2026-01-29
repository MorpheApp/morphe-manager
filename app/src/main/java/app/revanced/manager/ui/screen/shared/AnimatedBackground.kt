package app.revanced.manager.ui.screen.shared

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import app.morphe.manager.R
import app.revanced.manager.ui.screen.shared.backgrounds.*

/**
 * Types of animated backgrounds available in the app
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.settings_appearance_background_circles),
    RINGS(R.string.settings_appearance_background_rings),
    WAVES(R.string.settings_appearance_background_waves),
    SPACE(R.string.settings_appearance_background_space),
    SHAPES(R.string.settings_appearance_background_shapes),
    SNOW(R.string.settings_appearance_background_snow),
    NONE(R.string.settings_appearance_background_none);

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
