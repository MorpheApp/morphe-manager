/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
     * How far the list runs on under the header, see [hiddenAtStart]. None while no list is handed
     * over, such as while one loads, so the header rests bare until there is something under it.
     */
    val scrolledPast: Float get() = source?.hiddenAtStart() ?: 0f

    /** Whether the list rests at its start, as one still loading does. */
    val atStart: Boolean get() = source?.canScrollBackward != true

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
 * How far content scrolls under a header before its [HeaderBand] is fully down: the room a page
 * leaves between the header and its first line, so the band is whole by the time that line meets it.
 */
private val HeaderBandFadeDistance = Defaults.SpaceMedium

/**
 * A header's band of [glass] in [shape], blurring [haze], laid down as the content runs on under the
 * header and gone at rest. Laid behind the header's content, filling it.
 *
 * The band follows the scroll rather than easing in on a timer, so it is down by the time the content
 * reaches it however fast that comes, and lifts as smoothly as the content is scrolled back.
 *
 * @param scrolledPast How far the content runs on under the header, read as it draws so the scroll
 *   never recomposes the band.
 */
@Composable
fun BoxScope.HeaderBand(
    glass: Color,
    shape: Shape,
    scrolledPast: () -> Float,
    haze: HazeState? = LocalBackdropHaze.current
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = (scrolledPast() / HeaderBandFadeDistance.toPx()).coerceIn(0f, 1f) }
            .glassFill(glass, shape, haze)
    )
}
