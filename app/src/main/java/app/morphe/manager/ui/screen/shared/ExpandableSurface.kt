/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Expandable surface with a header icon, title, and collapsible content. */
@Composable
fun ExpandableSurface(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    initialExpanded: Boolean = false,
    headerTint: Color = dialogTextColor()
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius)),
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
        color = headerTint.copy(alpha = 0.05f),
        border = CardBorder.tinted(headerTint)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Click target only on the header so expanded content stays independently focusable for screen readers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    tint = dialogTextColor().copy(alpha = 0.7f),
                    announced = true
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                content()
            }
        }
    }
}
