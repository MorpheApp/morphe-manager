/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.morphe.manager.util.readableOn

/**
 * Tappable card showing a labeled bundle property ([title]) and its [value], trailed by a chevron.
 */
@Composable
fun BundleInfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val contentDesc = "$title: $value"
    val scheme = MaterialTheme.colorScheme

    // Disabling swaps the fill for a neutral one, so the content has to move with it
    val containerColor = if (enabled) {
        scheme.secondaryContainer
    } else {
        scheme.surfaceVariant.copy(alpha = 0.5f)
    }.distinctFromCard()
    val contentColor = (if (enabled) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        .readableOn(containerColor, scheme.surface)

    Surface(
        modifier = modifier.semantics {
            contentDescription = contentDesc
            role = Role.Button
        },
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
        color = containerColor,
        border = CardBorder.neutral,
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            ThemedIcon(
                icon = icon,
                size = Defaults.IconSizeSmall,
                tint = contentColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
                if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }

            if (enabled) {
                ForwardChevronIcon(size = Defaults.IconSizeSmall, tint = contentColor)
            }
        }
    }
}
