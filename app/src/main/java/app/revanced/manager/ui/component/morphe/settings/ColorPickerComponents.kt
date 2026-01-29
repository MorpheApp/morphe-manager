package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import app.revanced.manager.ui.component.morphe.shared.LocalDialogSecondaryTextColor
import app.revanced.manager.ui.component.morphe.shared.MorpheDialog
import app.revanced.manager.ui.component.morphe.shared.MorpheDialogButtonRow
import app.revanced.manager.ui.component.morphe.shared.MorpheDialogTextField

/**
 * Color picker dialog for custom color selection
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
                tonalElevation = 2.dp
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
                        stringResource(R.string.morphe_hex_color),
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

/**
 * Color preview dot composable for theme presets
 */
@Composable
fun ColorPreviewDot(
    colorValue: String,
    modifier: Modifier = Modifier,
    size: Int = 24
) {
    val dotModifier = modifier
        .size(size.dp)
        .clip(RoundedCornerShape((size / 2).dp))

    when {
        // Material You - rainbow gradient
        colorValue.contains("system_neutral", ignoreCase = true) ||
                colorValue.contains("system_accent", ignoreCase = true) ||
                colorValue.contains("material_you", ignoreCase = true) -> {
            Box(
                modifier = dotModifier
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
            )
        }
        // Pure black
        colorValue == "@android:color/black" || colorValue == "#000000" || colorValue == "#FF000000" -> {
            Box(
                modifier = dotModifier
                    .background(Color.Black)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape((size / 2).dp))
            )
        }
        // White
        colorValue == "@android:color/white" || colorValue == "#FFFFFF" || colorValue == "#ffffff" || colorValue == "#FFFFFFFF" -> {
            Box(
                modifier = dotModifier
                    .background(Color.White)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape((size / 2).dp))
            )
        }
        // Hex color
        else -> {
            val color = parseHexToRgb(colorValue)?.let { (r, g, b) -> Color(r, g, b) }
            if (color != null) {
                Surface(
                    modifier = dotModifier,
                    shape = RoundedCornerShape((size / 2).dp),
                    color = color,
                    tonalElevation = 1.dp
                ) {}
            } else {
                // Fallback for unknown formats - show placeholder
                Box(
                    modifier = dotModifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.3f),
                                    Color.Gray.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * Parse color string to RGB float values (0-1 range)
 */
fun parseColorToRgb(color: String): Triple<Float, Float, Float> {
    return parseHexToRgb(color) ?: Triple(0f, 0f, 0f)
}

/**
 * Parse hex color string to RGB float values
 * Supports both #RRGGBB and #AARRGGBB formats
 */
fun parseHexToRgb(hex: String): Triple<Float, Float, Float>? {
    val cleanHex = hex.removePrefix("#")

    return try {
        when (cleanHex.length) {
            6 -> {
                // #RRGGBB format
                val r = cleanHex.take(2).toInt(16) / 255f
                val g = cleanHex.substring(2, 4).toInt(16) / 255f
                val b = cleanHex.substring(4, 6).toInt(16) / 255f
                Triple(r, g, b)
            }
            8 -> {
                // #AARRGGBB format - skip alpha channel
                val r = cleanHex.substring(2, 4).toInt(16) / 255f
                val g = cleanHex.substring(4, 6).toInt(16) / 255f
                val b = cleanHex.substring(6, 8).toInt(16) / 255f
                Triple(r, g, b)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Convert RGB float values to hex string
 */
fun rgbToHex(r: Float, g: Float, b: Float): String {
    val rInt = (r * 255).toInt().coerceIn(0, 255)
    val gInt = (g * 255).toInt().coerceIn(0, 255)
    val bInt = (b * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(rInt, gInt, bInt)
}
