package app.revanced.manager.ui.component.morphe.shared

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Automatically adapts layout based on device orientation
 */
@Composable
fun AdaptiveLayout(
    portraitContent: @Composable () -> Unit,
    landscapeContent: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        landscapeContent()
    } else {
        portraitContent()
    }
}

/**
 * Check if current orientation is landscape
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
