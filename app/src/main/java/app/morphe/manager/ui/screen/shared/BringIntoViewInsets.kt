/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Has a scrolling list that runs on under bars at its ends bring a focused row, such as a search
 * field, out from under them rather than up to the list's own edge, [top] and [bottom] in from it.
 * [extraTop] adds what eases in under the top bar, such as the alerts, read as a row is brought in.
 *
 * Every scrolling container under it reads the insets, whichever way it scrolls, so one that is not
 * the list's own sets them aside: a dialog over it with [WithoutBringIntoViewInsets], a text field's
 * line with [TextFieldLine].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideBringIntoViewInsets(
    top: Dp,
    bottom: Dp,
    extraTop: () -> Dp = { 0.dp },
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val outer = withoutInsets(LocalBringIntoViewSpec.current)
    // Read as a row is brought in, so insets that ease with their bars keep one spec for the list
    val currentTop by rememberUpdatedState(top)
    val currentBottom by rememberUpdatedState(bottom)
    val spec = remember(outer, extraTop, density) {
        InsetBringIntoViewSpec(
            outer,
            top = { with(density) { (currentTop + extraTop()).toPx() } },
            bottom = { with(density) { currentBottom.toPx() } }
        )
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Leaves the insets of [ProvideBringIntoViewInsets] out of what scrolls within [content], which is
 * not the list they belong to, such as a dialog over the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithoutBringIntoViewInsets(content: @Composable () -> Unit) {
    val spec = LocalBringIntoViewSpec.current
    if (spec !is InsetBringIntoViewSpec) return content()
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec.outer, content = content)
}

/**
 * Holds a text field in [content] to its own way of keeping the cursor in view, which is to lay
 * its line out around it. Its scroller would otherwise scroll toward the cursor too, find itself
 * already there, and hand what it could not scroll on to what scrolls around it: a pager would take
 * that and turn to the page beside. The insets of a list the field sits in go with it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextFieldLine(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides StayPutBringIntoViewSpec, content = content)
}

private object StayPutBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private fun withoutInsets(spec: BringIntoViewSpec): BringIntoViewSpec =
    (spec as? InsetBringIntoViewSpec)?.outer ?: spec

/** The scroll into view [outer] gives, measured within the part of the list the bars leave clear. */
private class InsetBringIntoViewSpec(
    val outer: BringIntoViewSpec,
    private val top: () -> Float,
    private val bottom: () -> Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = top()
        return outer.calculateScrollDistance(offset - top, size, (containerSize - top - bottom()).coerceAtLeast(size))
    }
}
