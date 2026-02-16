package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Check if current orientation is landscape
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/**
 * Window size classes following Material Design 3 guidelines
 */
enum class WindowWidthSizeClass {
    /** Width < 600dp (phones in portrait) */
    Compact,

    /** 600dp ≤ width < 840dp (tablets in portrait, phones in landscape) */
    Medium,

    /** Width ≥ 840dp (tablets in landscape, desktops) */
    Expanded
}

enum class WindowHeightSizeClass {
    /** Height < 480dp */
    Compact,

    /** 480dp ≤ height < 900dp */
    Medium,

    /** Height ≥ 900dp */
    Expanded
}

/**
 * Window size data containing width and height classes
 */
data class WindowSize(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass
)

/**
 * Calculate window size class based on current configuration
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current

    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp

    return remember(widthDp, heightDp) {
        WindowSize(
            widthSizeClass = when {
                widthDp < 600.dp -> WindowWidthSizeClass.Compact
                widthDp < 840.dp -> WindowWidthSizeClass.Medium
                else -> WindowWidthSizeClass.Expanded
            },
            heightSizeClass = when {
                heightDp < 480.dp -> WindowHeightSizeClass.Compact
                heightDp < 900.dp -> WindowHeightSizeClass.Medium
                else -> WindowHeightSizeClass.Expanded
            }
        )
    }
}

/**
 * Helper to determine if we should use two-column layout
 */
val WindowSize.useTwoColumnLayout: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.Compact

/**
 * Helper to determine if we should use compact layout for dialogs
 */
val WindowSize.useCompactDialog: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

/**
 * Get recommended content padding based on window size
 */
val WindowSize.contentPadding: Dp
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Expanded -> 32.dp
    }

/**
 * Get recommended spacing between items based on window size
 */
val WindowSize.itemSpacing: Dp
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 12.dp
        WindowWidthSizeClass.Medium -> 16.dp
        WindowWidthSizeClass.Expanded -> 20.dp
    }
