/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.util.parseColorToRgb
import app.morphe.manager.util.parseHexToRgb
import app.morphe.manager.util.rgbToHex

/**
 * Color picker dialog for custom color selection.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Parse current color to RGB values
    val initialColor = remember(currentColor) {
        parseColorToRgb(currentColor)
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

    val previewColor = Color(red, green, blue)

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    onColorSelected(hexInput)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color preview
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = previewColor,
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hexInput,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (red + green + blue > 1.5f) Color.Black else Color.White
                    )
                }
            }

            // Hex input
            MorpheDialogTextField(
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
                color = Color.Red
            )

            ColorSlider(
                label = "G",
                value = green,
                onValueChange = { green = it },
                color = Color.Green
            )

            ColorSlider(
                label = "B",
                value = blue,
                onValueChange = { blue = it },
                color = Color.Blue
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(24.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.3f)
            )
        )
        Text(
            text = (value * 255).toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current,
            modifier = Modifier.width(32.dp)
        )
    }
}
