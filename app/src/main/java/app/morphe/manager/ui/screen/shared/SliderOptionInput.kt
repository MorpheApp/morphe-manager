/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * A single value slider for a patch option declared with bounds, for example
 * `intSliderOption` or `floatSliderOption` in the patcher.
 *
 * The readout doubles as the button that reveals a text field, because a slider alone
 * cannot comfortably hit an exact value on a wide range.
 *
 * @param value The current value, already clamped into `[min, max]` by the caller.
 * @param isInteger Whether the value is shown and reported without decimals.
 * @param onValueChange Called once the user lets go of the thumb or leaves the input field,
 *   never on every pixel of a drag.
 */
@Composable
fun SliderOptionInput(
    title: String,
    description: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    required: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(snapSliderValue(value, min, max, step)) }

    // Follow the value when something else changes it, such as the reset action
    LaunchedEffect(value) {
        if (!dragging) position = snapSliderValue(value, min, max, step)
    }

    // A drag stays glued to the finger, only changes from elsewhere glide into place
    val glidingPosition by animateFloatAsState(
        targetValue = position,
        animationSpec = if (dragging) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sliderPosition"
    )

    SliderOptionFrame(
        title = title,
        description = description,
        required = required,
        min = min,
        max = max,
        step = step,
        isInteger = isInteger,
        editing = editing,
        readout = {
            SliderValuePill(
                label = formatSliderValue(position, isInteger, step),
                active = dragging || editing,
                onClick = { editing = !editing }
            )
        },
        input = {
            SliderTextInput(
                value = position,
                min = min,
                max = max,
                step = step,
                isInteger = isInteger,
                onCommit = {
                    position = it
                    onValueChange(it)
                }
            )
        }
    ) {
        Slider(
            value = if (dragging) position else glidingPosition,
            onValueChange = { raw ->
                dragging = true
                val snapped = snapSliderValue(raw, min, max, step)
                if (snapped != position) {
                    position = snapped
                    if (step != null) view.performSliderTick()
                }
            },
            onValueChangeFinished = {
                dragging = false
                onValueChange(position)
            },
            valueRange = min..max,
            steps = tickCount(min, max, step),
            colors = SliderDefaults.colors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A two thumb slider for a patch option declared as a range, for example `intRangeOption`
 * or `floatRangeOption` in the patcher.
 *
 * @param value The current range, already clamped into `[min, max]` by the caller.
 * @param onValueChange Called with the ordered pair once the user lets go of a thumb or
 *   leaves one of the input fields.
 */
@Composable
fun RangeSliderOptionInput(
    title: String,
    description: String,
    value: ClosedFloatingPointRange<Float>,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    required: Boolean = false,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var position by remember { mutableStateOf(snapSliderRange(value, min, max, step)) }

    LaunchedEffect(value) {
        if (!dragging) position = snapSliderRange(value, min, max, step)
    }

    SliderOptionFrame(
        title = title,
        description = description,
        required = required,
        min = min,
        max = max,
        step = step,
        isInteger = isInteger,
        editing = editing,
        readout = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SliderValuePill(
                    label = formatSliderValue(position.start, isInteger, step),
                    active = dragging || editing,
                    onClick = { editing = !editing }
                )
                SliderPairConnector()
                SliderValuePill(
                    label = formatSliderValue(position.endInclusive, isInteger, step),
                    active = dragging || editing,
                    onClick = { editing = !editing }
                )
            }
        },
        input = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                SliderTextInput(
                    value = position.start,
                    min = min,
                    max = position.endInclusive,
                    step = step,
                    isInteger = isInteger,
                    label = stringResource(R.string.patch_option_slider_range_start),
                    modifier = Modifier.weight(1f),
                    onCommit = {
                        position = it..position.endInclusive
                        onValueChange(position)
                    }
                )
                SliderTextInput(
                    value = position.endInclusive,
                    min = position.start,
                    max = max,
                    step = step,
                    isInteger = isInteger,
                    label = stringResource(R.string.patch_option_slider_range_end),
                    modifier = Modifier.weight(1f),
                    onCommit = {
                        position = position.start..it
                        onValueChange(position)
                    }
                )
            }
        }
    ) {
        RangeSlider(
            value = position,
            onValueChange = { raw ->
                dragging = true
                val snapped = snapSliderRange(raw, min, max, step)
                if (snapped != position) {
                    position = snapped
                    if (step != null) view.performSliderTick()
                }
            },
            onValueChangeFinished = {
                dragging = false
                onValueChange(position)
            },
            valueRange = min..max,
            steps = tickCount(min, max, step),
            colors = SliderDefaults.colors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Everything around the track, which is the same whether the option holds one value or two:
 * the title with its readout, the scale labels, the exact value input and the description.
 */
@Composable
private fun SliderOptionFrame(
    title: String,
    description: String,
    required: Boolean,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    editing: Boolean,
    readout: @Composable () -> Unit,
    input: @Composable () -> Unit,
    slider: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        SliderHeader(title = title, required = required, readout = readout)

        slider()

        SliderScaleLabels(
            start = formatSliderValue(min, isInteger, step),
            end = formatSliderValue(max, isInteger, step)
        )

        AnimatedVisibility(
            visible = editing,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            input()
        }

        SliderDescription(description)
    }
}

/**
 * Exact value input for a slider. What is typed is only reported once the field is left or
 * the keyboard action is confirmed, so a half typed number is never clamped away mid keystroke.
 * While the field is not focused it keeps following the thumb.
 */
@Composable
private fun SliderTextInput(
    value: Float,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    onCommit: (Float) -> Unit
) {
    var text by remember { mutableStateOf(formatSliderValue(value, isInteger, step)) }
    var focused by remember { mutableStateOf(false) }
    val parsed = text.toFloatOrNull()
    val isInvalid = parsed == null || parsed < min || parsed > max

    LaunchedEffect(value) {
        if (!focused) text = formatSliderValue(value, isInteger, step)
    }

    fun commit() {
        val snapped = snapSliderValue(parsed ?: value, min, max, step)
        text = formatSliderValue(snapped, isInteger, step)
        onCommit(snapped)
    }

    AppDialogTextField(
        modifier = modifier.onFocusChanged { state ->
            val wasFocused = focused
            focused = state.isFocused
            if (wasFocused && !state.isFocused) commit()
        },
        value = text,
        onValueChange = { text = it },
        label = label?.let { { Text(it) } },
        placeholder = {
            Text(
                stringResource(
                    if (isInteger) R.string.patch_option_enter_number
                    else R.string.patch_option_enter_decimal
                )
            )
        },
        isError = isInvalid,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { commit() })
    )
}
