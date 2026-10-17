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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Has a scrolling list that runs on under bars at its ends bring a focused row, such as a search
 * field, out from under them rather than up to the list's own edge, [top] and [bottom] in from it.
 *
 * Every scrolling container under it reads the insets, whichever way it scrolls, so one that is not
 * the list's own, such as a text field's line, sets them aside with [WithoutBringIntoViewInsets].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideBringIntoViewInsets(top: Dp, bottom: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val outer = withoutInsets(LocalBringIntoViewSpec.current)
    val spec = remember(outer, top, bottom, density) {
        InsetBringIntoViewSpec(outer, with(density) { top.toPx() }, with(density) { bottom.toPx() })
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Leaves the insets of [ProvideBringIntoViewInsets] out of what scrolls within [content], which is
 * not the list they belong to: a text field's own line, or a dialog's window over the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithoutBringIntoViewInsets(content: @Composable () -> Unit) {
    val spec = LocalBringIntoViewSpec.current
    if (spec !is InsetBringIntoViewSpec) return content()
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec.outer, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
private fun withoutInsets(spec: BringIntoViewSpec): BringIntoViewSpec =
    (spec as? InsetBringIntoViewSpec)?.outer ?: spec

/** The scroll into view [outer] gives, measured within the part of the list the bars leave clear. */
@OptIn(ExperimentalFoundationApi::class)
private class InsetBringIntoViewSpec(
    val outer: BringIntoViewSpec,
    private val top: Float,
    private val bottom: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        outer.calculateScrollDistance(offset - top, size, (containerSize - top - bottom).coerceAtLeast(size))
}
