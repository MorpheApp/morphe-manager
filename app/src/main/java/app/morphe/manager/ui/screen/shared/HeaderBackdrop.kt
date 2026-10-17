/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeState

/**
 * The list a [ListDialog]'s header floats over, which the dialog's own lists hand over as they show,
 * see [DialogScrollIndicators]. A pager hands over the open page's list, which takes over from the
 * page left. The dialog cannot know it otherwise, as its content builds the list.
 */
@Stable
class HeaderBackdrop {
    private var source by mutableStateOf<ScrollableState?>(null)

    /**
     * Whether the list runs on under the header. False while no list is handed over, such as while
     * one loads, so the header rests bare until there is something to scroll under it.
     */
    val scrolledUnder: Boolean get() = source?.canScrollBackward ?: false

    internal fun track(state: ScrollableState) {
        source = state
    }

    internal fun release(state: ScrollableState) {
        if (source === state) source = null
    }
}

/** The backdrop of the [ListDialog] header over the list this is drawn in, null outside one. */
val LocalHeaderBackdrop = staticCompositionLocalOf<HeaderBackdrop?> { null }

/** Hands [state], the scroll of a [ListDialog]'s list, to the header floating over it. */
@Composable
internal fun TrackHeaderBackdrop(state: ScrollableState) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    DisposableEffect(backdrop, state) {
        backdrop.track(state)
        onDispose { backdrop.release(state) }
    }
}

/**
 * A header's band of [glass] in [shape], blurring [haze], laid down while [scrolledUnder] says the
 * content runs on under the header and faded out at rest. Laid behind the header's content, filling it.
 *
 * @param scrolledUnder Read as the content scrolls, though the band only follows it as it flips.
 */
@Composable
fun BoxScope.HeaderBand(
    glass: Color,
    shape: Shape,
    scrolledUnder: () -> Boolean,
    haze: HazeState? = LocalBackdropHaze.current
) {
    val current = rememberUpdatedState(scrolledUnder)
    // Only the flip recomposes, not every step of the scroll it is worked out from
    val under by remember { derivedStateOf { current.value() } }
    val shown by animateFloatAsState(
        targetValue = if (under) 1f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "header_band"
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = shown }
            .glassFill(glass, shape, haze)
    )
}
