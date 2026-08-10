/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One card of a [CardSelectorRow]. */
data class CardSelectorOption(
    val label: String,
    val icon: ImageVector
)

/**
 * Row of equally wide cards for switching between a few modes inside a dialog.
 *
 * Dialogs render over a translucent background where a tab indicator washes out, so the
 * selected mode is carried by the fill and a stronger border instead.
 */
@Composable
fun CardSelectorRow(
    options: List<CardSelectorOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    ),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    Color.Transparent,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 0.5.dp,
                    color = if (isSelected)
                        LocalDialogTextColor.current.copy(alpha = 0.5f)
                    else
                        LocalDialogTextColor.current.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemedIcon(
                        icon = option.icon,
                        tint = if (isSelected)
                            LocalDialogTextColor.current
                        else
                            LocalDialogTextColor.current.copy(alpha = 0.4f)
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected)
                            LocalDialogTextColor.current
                        else
                            LocalDialogTextColor.current.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
