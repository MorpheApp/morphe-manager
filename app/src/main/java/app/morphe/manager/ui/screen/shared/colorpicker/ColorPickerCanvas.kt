/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.screen.shared.Defaults

/** Where the color sits, in the terms the two controls below are laid out in. */
@Immutable
data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    val color: Color get() = Color.hsv(hue.coerceIn(0f, 360f), saturation, value)

    companion object {
        /**
         * Carries hue in its own right rather than leaving it to be read back off the color, a gray
         * having none to recover.
         */
        val Saver = listSaver(
            save = { listOf(it.hue, it.saturation, it.value) },
            restore = { HsvColor(it[0], it[1], it[2]) }
        )
    }
}

private val MarkerRadius = 10.dp
private val MarkerStroke = 3.dp

/**
 * Exactly the handle's own diameter, so it fills the strip end to end. A ring is stroked centered
 * on its radius, which puts only half the width outside it.
 */
private val HueStripHeight = (MarkerRadius + MarkerStroke / 2) * 2

/** The hue wheel walked in even steps, which a sweep of stops approximates closely enough. */
private val HueStops = List(13) { Color.hsv(it * 30f, 1f, 1f) }

/**
 * Saturation across, value down, over a background of the pure [hsv] hue. Both a tap and a drag
 * report continuously, the handle being drawn from the value rather than held as state of its own.
 */
@Composable
fun SaturationValuePanel(
    hsv: HsvColor,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    contentDescription: String? = null
) {
    var size by remember { mutableStateOf(Size.Zero) }

    fun report(position: Offset) {
        if (size == Size.Zero) return
        onChange(
            (position.x / size.width).coerceIn(0f, 1f),
            1f - (position.y / size.height).coerceIn(0f, 1f)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Defaults.CornerMedium))
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .pointerInput(Unit) {
                detectTapGestures { report(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { report(it) }) { change, _ ->
                    change.consume()
                    report(change.position)
                }
            }
    ) {
        size = this.size

        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        // Straight off the value, so the handle is under the finger during a drag and already in
        // place when the dialog opens, rather than arriving from wherever it started
        drawMarker(
            center = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height),
            fill = hsv.color,
            radius = MarkerRadius.toPx()
        )
    }
}

/**
 * The hue wheel laid out flat. Saturation and value stay where they are, so the strip always shows
 * fully saturated colors and reads as a spectrum rather than as a slice of the current color.
 */
@Composable
fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = HueStripHeight,
    contentDescription: String? = null
) {
    var width by remember { mutableFloatStateOf(0f) }

    fun report(x: Float) {
        if (width <= 0f) return
        onChange((x / width).coerceIn(0f, 1f) * 360f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .pointerInput(Unit) {
                detectTapGestures { report(it.x) }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { report(it.x) }) { change, _ ->
                    change.consume()
                    report(change.position.x)
                }
            }
    ) {
        width = size.width

        // Rounded by the draw rather than by a clip, which would take the handle with it
        drawRoundRect(
            brush = Brush.horizontalGradient(HueStops),
            cornerRadius = CornerRadius(size.height / 2f)
        )

        drawMarker(
            center = Offset((hue / 360f) * size.width, size.height / 2f),
            fill = Color.hsv(hue, 1f, 1f),
            radius = MarkerRadius.toPx()
        )
    }
}

/**
 * The handle both controls share: the picked color ringed in white over black, which stays visible
 * on any part of either gradient. Kept a full ring inside the bounds, since a clipped handle reads
 * as a rendering fault rather than as a value at the end of its range.
 */
private fun DrawScope.drawMarker(center: Offset, fill: Color, radius: Float) {
    val stroke = MarkerStroke.toPx()
    val outer = radius + stroke / 2f
    val clamped = Offset(
        center.x.coerceIn(outer, (size.width - outer).coerceAtLeast(outer)),
        center.y.coerceIn(outer, (size.height - outer).coerceAtLeast(outer))
    )

    drawCircle(Color.Black.copy(alpha = 0.35f), outer, clamped)
    drawCircle(fill, radius, clamped)
    drawCircle(Color.White, radius, clamped, style = Stroke(stroke))
}
