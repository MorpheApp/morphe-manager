/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** True when this direction mirrors the layout, for draw scopes that carry the direction themselves. */
val LayoutDirection.isRtl: Boolean get() = this == LayoutDirection.Rtl

/** True when the UI is mirrored, which brushes and raw pixel offsets have to account for themselves. */
@Composable
@ReadOnlyComposable
fun isRtl(): Boolean = LocalLayoutDirection.current.isRtl

/**
 * X coordinate [inset] in from the start edge of a box [width] wide, since draw coordinates are
 * always physical.
 */
fun startEdgeX(width: Float, rtl: Boolean, inset: Float = 0f): Float = if (rtl) width - inset else inset

/** X coordinate [inset] in from the end edge of a box [width] wide. */
fun endEdgeX(width: Float, rtl: Boolean, inset: Float = 0f): Float = if (rtl) inset else width - inset

/**
 * Horizontal gradient that runs from the start edge to the end edge, since [Brush.horizontalGradient]
 * itself always sweeps from the physical left to the physical right.
 */
fun startToEndGradient(colors: List<Color>, rtl: Boolean): Brush =
    Brush.horizontalGradient(if (rtl) colors.reversed() else colors)

/** Two stop [startToEndGradient], for the common fade between an edge tint and transparency. */
fun startToEndGradient(startColor: Color, endColor: Color, rtl: Boolean): Brush =
    startToEndGradient(listOf(startColor, endColor), rtl)
