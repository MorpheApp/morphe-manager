package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.SectionCard
import app.morphe.manager.ui.screen.shared.WindowWidthSizeClass
import app.morphe.manager.ui.screen.shared.rememberWindowSize
import app.morphe.manager.util.darken
import app.morphe.manager.util.toColorOrNull

/**
 * Predefined accent color palette
 */
val THEME_PRESET_COLORS = listOf(
    Color(0xFF6750A4),
    Color(0xFF386641),
    Color(0xFF0061A4),
    Color(0xFF8E24AA),
    Color(0xFFEF6C00),
    Color(0xFF00897B),
    Color(0xFFD81B60),
    Color(0xFF5C6BC0),
    Color(0xFF43A047),
    Color(0xFFFF7043),
    Color(0xFF1DE9B6),
    Color(0xFFFFC400),
    Color(0xFF00B8D4),
    Color(0xFFBA68C8)
)

/**
 * Accent color selector with adaptive color grid
 */
@Composable
fun AccentColorSelector(
    selectedColorHex: String?,
    onColorSelected: (Color?) -> Unit,
    dynamicColorEnabled: Boolean
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 7
        WindowWidthSizeClass.Medium -> 9
        WindowWidthSizeClass.Expanded -> 11
    }

    val selectedArgb = selectedColorHex.toColorOrNull()?.toArgb()
    val isEnabled = !dynamicColorEnabled

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Color grid
            THEME_PRESET_COLORS.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        val isSelected = selectedArgb != null && preset.toArgb() == selectedArgb
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected)
                                        preset.darken(0.4f)
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    preset.copy(alpha = if (isEnabled) 1f else 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = isEnabled) {
                                    if (isEnabled) {
                                        onColorSelected(preset)
                                    }
                                }
                        )
                    }
                    // Fill remaining space if row is incomplete
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // "Not selected" button at the bottom
            CompactOptionCard(
                selected = selectedArgb == null,
                onClick = {
                    if (isEnabled) {
                        onColorSelected(null)
                    }
                },
                icon = Icons.Outlined.Close,
                label = stringResource(R.string.not_selected),
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
        }
    }
}
