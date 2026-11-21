/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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

/** The window's size in dp. */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun windowSizeDp(): DpSize {
    // The configuration counts its dp at the display density, ignoring the interface scale the
    // activity context applies on top of it, so they are turned into pixels at that density first.
    // Not LocalWindowInfo.containerSize, which Compose measures when the configuration changes and
    // keeps the size from before a rotation where the window is resized only afterward
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val pixelsPerDp = Resources.getSystem().displayMetrics.density

    return with(density) {
        DpSize(
            (configuration.screenWidthDp * pixelsPerDp).toDp(),
            (configuration.screenHeightDp * pixelsPerDp).toDp()
        )
    }
}

/**
 * Calculate window size class based on current configuration
 */
@Composable
fun rememberWindowSize(): WindowSize {
    val (widthDp, heightDp) = windowSizeDp()

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
 * Keeps this within a centered content column of [maxWidth], filling a screen narrower than that,
 * so a page's or dialog's header, content and bars all line up.
 */
fun Modifier.contentColumn(maxWidth: Dp = Defaults.ContentMaxWidth): Modifier =
    widthIn(max = maxWidth).fillMaxWidth()

/**
 * Gutter each side of a list running the full [width], which centers a [Defaults.ContentMaxWidth]
 * column in it with its own [gutter] inside. The list itself stays as wide as the screen, so it
 * scrolls from anywhere on it and keeps its scrollbar at the edge.
 */
fun centeredGutter(width: Dp, gutter: Dp): Dp =
    maxOf(gutter, (width - Defaults.ContentMaxWidth) / 2 + gutter)

/** Whether the window is wide enough for a dialog to set its panes side by side, see [ListDialog]. */
@Composable
fun hasRoomForPanes(): Boolean = rememberWindowSize().widthSizeClass == WindowWidthSizeClass.Expanded

/**
 * Get recommended content padding based on window size
 */
val WindowSize.contentPadding: Dp
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> Defaults.SpaceLarge
        WindowWidthSizeClass.Medium -> Defaults.SpaceExtraLarge
        WindowWidthSizeClass.Expanded -> Defaults.SpaceExtraExtraLarge
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
