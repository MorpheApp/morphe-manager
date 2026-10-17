/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.colorpicker.*
import app.morphe.manager.util.contrastingContent
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startToEndGradient
import app.morphe.manager.util.toColorOrNull
import app.morphe.manager.util.toHexString
import app.morphe.manager.util.toHsv

/**
 * Switch offered above the picker controls for colors that can follow a value computed elsewhere.
 * While it is on the picker returns [token] instead of a hex value and the manual controls are
 * disabled, because there is nothing to pick.
 *
 * @param previewColor    Color the token currently resolves to, used to seed the manual controls
 *   when the switch is turned back off.
 * @param previewGradient Shown instead of [previewColor] when the token stands for a value that
 *   varies rather than a single color; needs at least two colors to render.
 */
data class ColorPickerToggle(
    val label: String,
    val description: String,
    val token: String,
    val previewColor: Color,
    val previewGradient: List<Color> = emptyList()
)

/**
 * Color picker dialog for custom color selection.
 *
 * Works in hue, saturation and value rather than in red, green and blue: those are the axes a
 * color is actually chosen along, and two of them fit one panel. Hex stays as the way to carry a
 * color in and out, and as the way to type an exact one.
 *
 * @param presets Offered above the panel for reaching a common color in one tap. Pass an empty
 *   list where a palette would only be noise.
 */
@Composable
fun DialogHostScope.ColorPickerDialog(
    title: String,
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    toggle: ColorPickerToggle? = null,
    presets: List<Color> = THEME_PRESET_COLORS
) {
    val initialToggle = toggle?.takeIf {
        currentColor.trim().equals(it.token, ignoreCase = true)
    }

    var useToggle by rememberSaveable(currentColor, toggle?.token) { mutableStateOf(initialToggle != null) }

    // A color following the toggle has no hex of its own, so the manual controls open on the
    // value it currently resolves to instead of on black
    val initial = remember(currentColor, toggle?.previewColor) {
        initialToggle?.previewColor ?: currentColor.toColorOrNull() ?: Color.Black
    }

    // Saved rather than merely remembered, a color half chosen being worth more than the one it
    // started from. Rotating still closes it, the settings tabs being rebuilt from a different
    // container in each orientation, which no state of this dialog's own can survive
    var hsv by rememberSaveable(stateSaver = HsvColor.Saver) {
        mutableStateOf(initial.toHsv().let { HsvColor(it.first, it.second, it.third) })
    }
    var hexInput by rememberSaveable { mutableStateOf(initial.toHexString()) }
    var isHexError by rememberSaveable { mutableStateOf(false) }

    /** Moving on the panel or the strip is what the hex readout follows, never the other way. */
    fun moveTo(updated: HsvColor) {
        hsv = updated
        hexInput = updated.color.toHexString()
        isHexError = false
    }

    val activeToggle = toggle?.takeIf { useToggle }
    val enabled = activeToggle == null
    val previewColor = activeToggle?.previewColor ?: hsv.color
    val previewGradient = activeToggle?.previewGradient?.takeIf { it.size > 1 }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onColorSelected(activeToggle?.token ?: hexInput) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val landscape = isLandscape()

        val panel: @Composable () -> Unit = {
            SaturationValuePanel(
                hsv = hsv,
                onChange = { saturation, value -> moveTo(hsv.copy(saturation = saturation, value = value)) },
                // Landscape gives it the whole column, which is still less height than portrait
                height = if (landscape) 150.dp else 200.dp,
                contentDescription = stringResource(R.string.color_picker_shade)
            )
        }

        val strip: @Composable () -> Unit = {
            HueSlider(
                hue = hsv.hue,
                onChange = { moveTo(hsv.copy(hue = it)) },
                contentDescription = stringResource(R.string.color_picker_hue)
            )
        }

        val swatches: @Composable () -> Unit = {
            if (presets.isNotEmpty()) {
                ColorPresetRow(
                    colors = presets,
                    selected = hsv.color,
                    onSelect = { preset ->
                        val (hue, saturation, value) = preset.toHsv()
                        moveTo(HsvColor(hue, saturation, value))
                    }
                )
            }
        }

        val hexField: @Composable () -> Unit = {
            AppDialogTextField(
                enabled = enabled,
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    val parsed = input.toColorOrNull()
                    if (parsed != null) {
                        // A gray types as hue 0, which would swing the panel back to red, so a
                        // color that carries no hue of its own keeps the one already on screen
                        val (hue, saturation, value) = parsed.toHsv()
                        hsv = HsvColor(if (saturation == 0f) hsv.hue else hue, saturation, value)
                        isHexError = false
                    } else {
                        isHexError = input.isNotEmpty() && !input.startsWith("@")
                    }
                },
                label = { Text(stringResource(R.string.hex_color)) },
                placeholder = { Text("#RRGGBB") },
                isError = isHexError
            )
        }

        val switch: @Composable () -> Unit = {
            if (toggle != null) {
                SettingsSwitchItem(
                    checked = useToggle,
                    onToggle = { useToggle = !useToggle },
                    title = toggle.label,
                    subtitle = toggle.description,
                    showBorder = true
                )
            }
        }

        val preview: @Composable () -> Unit = {
            ColorPreview(
                color = previewColor,
                gradient = previewGradient,
                label = activeToggle?.label ?: hexInput,
                height = if (landscape) 44.dp else 60.dp
            )
        }

        // Landscape leaves barely half the height this stacks into, and the panel cannot give way
        // to a scroll because it needs the drag itself. Side by side it fits, and everything that
        // does scroll ends up in the other column, where a swipe still reaches the dialog
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
            ) {
                FoldingVisibility(
                    visible = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    panel()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
                ) {
                    preview()
                    switch()
                    FoldingVisibility(visible = enabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
                            swatches()
                            strip()
                        }
                    }
                    hexField()
                }
            }
            return@AppDialog
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            preview()
            switch()

            // The manual controls have nothing to offer while the color follows the toggle, and
            // leaving them grayed out in place would only hold the room they need
            FoldingVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
                    swatches()
                    panel()
                    strip()
                }
            }

            hexField()
        }
    }
}

/**
 * The color as it will be used, captioned with what it is. A gradient carries its own meaning and
 * is labeled by the switch under it, so the caption only makes sense for a single picked color.
 */
@Composable
private fun ColorPreview(color: Color, gradient: List<Color>?, label: String, height: Dp) {
    val animated by animateColorAsState(color, label = "color_preview")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Defaults.CornerMedium))
            .then(
                if (gradient != null) {
                    Modifier.background(startToEndGradient(gradient, isRtl()))
                } else {
                    Modifier.background(animated)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (gradient == null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = animated.contrastingContent()
            )
        }
    }
}
