/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Parts shared by every slider based option editor: the value readout, the header it sits in,
 * the scale labels and the arithmetic that keeps a value on the scale a patch declared.
 * [SliderOptionInput] and [RangeSliderOptionInput] are built from these.
 */

/**
 * Slider colors in [accentColor], as a switch that is on takes it, so the theme's blue does not sit
 * in a dialog of another hue. The theme's own where there is none.
 */
@Composable
fun accentSliderColors(accentColor: Color? = LocalAccent.current): SliderColors {
    val accent = usableAppAccent(accentColor) ?: return SliderDefaults.colors()
    return SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        activeTickColor = appAccentContent(accent),
        inactiveTrackColor = accent.copy(alpha = AccentAlpha.LEAD),
        inactiveTickColor = accent
    )
}

/** Above this many steps the tick marks turn into visual noise, so they are dropped */
private const val MAX_VISIBLE_TICKS = 20

/**
 * Absorbs the float error in a step count, so a scale like `0f..1f` in steps of `0.1f` still
 * reaches its upper bound. Matches the tolerance the patcher applies when validating.
 */
private const val STEP_EPSILON = 1e-4f

/**
 * Snaps [value] onto the scale defined by [step] and clamps it into `[min, max]`.
 * A null [step] means the slider is continuous, so the value is only clamped.
 *
 * Clamping happens in step counts rather than on the result, so an upper bound that is not
 * itself on the scale can never produce a value the option would reject.
 */
fun snapSliderValue(value: Float, min: Float, max: Float, step: Float?): Float {
    if (step == null || step <= 0f) return value.coerceIn(min, max)
    val lastStep = floor((max - min) / step + STEP_EPSILON).toInt()
    val steps = ((value - min) / step).roundToInt().coerceIn(0, lastStep)
    return (min + steps * step).coerceIn(min, max)
}

/** Snaps both ends of a range and keeps them ordered. */
internal fun snapSliderRange(
    value: ClosedFloatingPointRange<Float>,
    min: Float,
    max: Float,
    step: Float?
): ClosedFloatingPointRange<Float> {
    val start = snapSliderValue(value.start, min, max, step)
    val end = snapSliderValue(value.endInclusive, min, max, step)
    return if (start <= end) start..end else end..start
}

/** Number of decimals worth showing for a slider with the given [step]. */
private fun decimalsFor(step: Float?): Int = when {
    step == null -> 2
    step >= 1f -> 0
    step >= 0.1f -> 1
    step >= 0.01f -> 2
    else -> 3
}

/** Formats a slider value the way it is shown in the readout and in the input field. */
internal fun formatSliderValue(value: Float, isInteger: Boolean, step: Float?): String =
    if (isInteger) value.roundToInt().toString()
    else String.format(Locale.US, "%.${decimalsFor(step)}f", value)

/**
 * Tick marks to draw, or 0 for a continuous looking track. The slider quantizes the value
 * itself when this is not 0, which stays consistent with [snapSliderValue].
 */
internal fun tickCount(min: Float, max: Float, step: Float?): Int {
    if (step == null || step <= 0f) return 0
    val ticks = ((max - min) / step).roundToInt() - 1
    return if (ticks in 1..MAX_VISIBLE_TICKS) ticks else 0
}

/** The tick the platform uses for a slider crossing a step */
internal fun View.performSliderTick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

/**
 * The current value as a pill. It grows and takes on the primary container color while the
 * value is being changed, and opens the exact value input when tapped.
 *
 * The number itself is swapped without any transition. A drag walks through every step on the
 * way to the target, and animating each swap leaves the digits smeared over each other.
 */
@Composable
fun SliderValuePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillScale"
    )
    // A faint ground in the slider's color while it is dragged, as a picked menu entry has
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = AccentAlpha.STEP)
        else dialogTextColor().copy(alpha = 0.06f),
        label = "pillContainer"
    )
    val content = dialogTextColor()

    Surface(
        shape = Defaults.PillShape,
        color = container,
        modifier = Modifier
            .scale(scale)
            .shapedClickable(Defaults.PillShape, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content
            )

            Icon(
                imageVector = MaterialSymbols.Rounded.Edit,
                contentDescription = stringResource(R.string.patch_option_slider_edit),
                tint = content.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Joins the two readouts of a range, so the pair reads as one value rather than two */
@Composable
fun SliderPairConnector() {
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(1.5.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(1.dp)
            )
    )
}

/**
 * A scale slider flanked by the same icon twice, small on the left and large on the right,
 * which is how every "resize this image" control in the dialogs reads its range. The value is
 * clamped into [valueRange] before it is reported.
 *
 * @param trailing Extra actions after the track, typically a [SliderResetAction].
 */
@Composable
fun ScaleSliderRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector = MaterialSymbols.Rounded.Image,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceExtraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = dialogSecondaryTextColor()
        )
        Spacer(Modifier.width(Defaults.SpaceSmall))
        val sliderState = remember(valueRange) { SliderState(value = value, trackRange = valueRange) }
        // The caller owns the value, so anything that moves it elsewhere has to reach the state
        sliderState.value = value
        Slider(
            state = sliderState,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Defaults.SpaceSmall))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = dialogSecondaryTextColor()
        )
        trailing()
    }
}

/**
 * Reset button that appears next to a [ScaleSliderRow] once the transform moved away from its
 * default. The spacer sits inside the animation so the gap collapses with the button.
 */
@Composable
fun RowScope.SliderResetAction(
    visible: Boolean,
    contentDescription: String?,
    onReset: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Row {
            Spacer(Modifier.width(Defaults.SpaceSmall))
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Restart_alt,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = dialogTextColor()
                )
            }
        }
    }
}

/** One end of a slider's scale. */
@Composable
fun SliderScaleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = dialogSecondaryTextColor()
    )
}

/**
 * A setting picked on a slider, in a card of its own: the picked [value] captioned with [title],
 * over the [slider] flanked by the two ends of its scale, then any [footer] the setting needs.
 *
 * The footer is laid out without gaps of its own, so what it folds away leaves none behind;
 * each part of it brings its own top padding.
 */
@Composable
fun SettingsSliderCard(
    value: String,
    title: String,
    rangeStart: String,
    rangeEnd: String,
    slider: @Composable () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    SearchableSetting(listOf(title)) {
        SettingsItemCard(onClick = null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceLarge)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
                    InfoStatBox(
                        value = value,
                        subtitle = title,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = Defaults.SpaceSmall)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                    ) {
                        SliderScaleLabel(rangeStart)
                        Box(modifier = Modifier.weight(1f)) { slider() }
                        SliderScaleLabel(rangeEnd)
                    }
                }
                footer()
            }
        }
    }
}
