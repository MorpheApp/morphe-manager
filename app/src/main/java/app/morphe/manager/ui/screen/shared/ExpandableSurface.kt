/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/** Expandable surface with a header icon, title, and collapsible content. */
@Composable
fun ExpandableSurface(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = MaterialSymbols.Rounded.Info,
    initialExpanded: Boolean = false,
    headerTint: Color = dialogTextColor()
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    // The header is pressed and focused on the surface's own shape, so either fits its corners
    val shape = RoundedCornerShape(Defaults.CornerMedium)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = headerTint.copy(alpha = 0.05f),
        border = CardBorder.tinted(headerTint)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Click target only on the header so expanded content stays independently focusable for screen readers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shapedClickable(shape) { expanded = !expanded }
                    .padding(Defaults.SpaceMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = headerTint,
                        modifier = Modifier.size(Defaults.IconSizeSmall)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = headerTint
                    )
                }

                ExpandChevron(
                    expanded = expanded,
                    modifier = Modifier.size(Defaults.IconSizeSmall),
                    announced = true
                )
            }

            // Expandable content
            FoldingVisibility(visible = expanded) {
                content()
            }
        }
    }
}
