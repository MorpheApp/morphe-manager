/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.util.parseColorToRgb
import app.morphe.manager.util.parseHexToRgb
import app.morphe.manager.util.requiresLightContent
import app.morphe.manager.util.rgbToHex

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
 */
@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    toggle: ColorPickerToggle? = null
) {
    val initialToggle = toggle?.takeIf {
        currentColor.trim().equals(it.token, ignoreCase = true)
    }

    var useToggle by remember(currentColor, toggle?.token) { mutableStateOf(initialToggle != null) }

    // A color following the toggle has no hex of its own, so the manual controls open on the
    // value it currently resolves to instead of on black
    val initialColor = remember(currentColor, toggle?.previewColor) {
        initialToggle?.previewColor?.let { Triple(it.red, it.green, it.blue) }
            ?: parseColorToRgb(currentColor)
    }

    var red by remember { mutableFloatStateOf(initialColor.first) }
    var green by remember { mutableFloatStateOf(initialColor.second) }
    var blue by remember { mutableFloatStateOf(initialColor.third) }
    var hexInput by remember { mutableStateOf(rgbToHex(initialColor.first, initialColor.second, initialColor.third)) }
    var isHexError by remember { mutableStateOf(false) }

    // Update hex when sliders change
    LaunchedEffect(red, green, blue) {
        hexInput = rgbToHex(red, green, blue)
        isHexError = false
    }

    val activeToggle = toggle?.takeIf { useToggle }
    val previewColor = activeToggle?.previewColor ?: Color(red, green, blue)
    val previewGradient = activeToggle?.previewGradient?.takeIf { it.size > 1 }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    onColorSelected(activeToggle?.token ?: hexInput)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Color preview. A gradient carries its own meaning and is labeled by the switch
            // below, so the hex readout only makes sense for a single picked color
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                    .then(
                        if (previewGradient != null) {
                            Modifier.background(Brush.horizontalGradient(previewGradient))
                        } else {
                            Modifier.background(previewColor)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (previewGradient == null) {
                    Text(
                        text = activeToggle?.label ?: hexInput,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (previewColor.requiresLightContent()) Color.White else Color.Black
                    )
                }
            }

            if (toggle != null) {
                SettingsSwitchItem(
                    checked = useToggle,
                    onToggle = { useToggle = !useToggle },
                    title = toggle.label,
                    subtitle = toggle.description,
                    showBorder = true
                )
            }

            // Hex input
            AppDialogTextField(
                enabled = activeToggle == null,
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    // Try to parse hex and update sliders
                    val parsed = parseHexToRgb(input)
                    if (parsed != null) {
                        red = parsed.first
                        green = parsed.second
                        blue = parsed.third
                        isHexError = false
                    } else {
                        isHexError = input.isNotEmpty() && !input.startsWith("@")
                    }
                },
                label = {
                    Text(
                        stringResource(R.string.hex_color),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(
                        "#RRGGBB",
                        color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.6f)
                    )
                },
                isError = isHexError
            )

            // RGB Sliders
            ColorSlider(
                label = "R",
                value = red,
                onValueChange = { red = it },
                color = Color.Red,
                enabled = activeToggle == null
            )

            ColorSlider(
                label = "G",
                value = green,
                onValueChange = { green = it },
                color = Color.Green,
                enabled = activeToggle == null
            )

            ColorSlider(
                label = "B",
                value = blue,
                onValueChange = { blue = it },
                color = Color.Blue,
                enabled = activeToggle == null
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = contentAlpha),
            modifier = Modifier.width(24.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.3f),
                disabledThumbColor = color.copy(alpha = contentAlpha),
                disabledActiveTrackColor = color.copy(alpha = contentAlpha),
                disabledInactiveTrackColor = color.copy(alpha = 0.3f * contentAlpha)
            )
        )
        Text(
            text = (value * 255).toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current.copy(alpha = contentAlpha),
            modifier = Modifier.width(32.dp)
        )
    }
}
