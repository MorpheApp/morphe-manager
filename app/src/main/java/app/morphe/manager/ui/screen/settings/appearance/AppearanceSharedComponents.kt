/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * Standard icon-based option card for appearance settings.
 * Used for backgrounds, themes, and other icon-based selections.
 */
@Composable
fun ModernIconOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val windowSize = rememberWindowSize()
    val iconSize = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 32.dp
        WindowWidthSizeClass.Medium -> 36.dp
        WindowWidthSizeClass.Expanded -> 40.dp
    }

    // Increase height in landscape to prevent text clipping
    val cardHeight = if (isLandscape()) 92.dp else 80.dp

    SelectionTile(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        stateDescription = stringResource(
            if (selected) R.string.selected else R.string.not_selected
        ),
        modifier = modifier.height(cardHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MorpheDefaults.ItemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 2, // Allow 2 lines for text wrapping
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
        }
    }
}

/**
 * Compact horizontal card for single-row selections.
 * Used for "Not selected" option in color picker.
 */
@Composable
fun CompactOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Increase height slightly in landscape for better text display
    val cardHeight = if (isLandscape()) 60.dp else 56.dp

    SelectionTile(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        stateDescription = stringResource(
            if (selected) R.string.selected else R.string.not_selected
        ),
        modifier = modifier.height(cardHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ItemSpacing),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MorpheIcon(
                icon = icon,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }.copy(alpha = if (enabled) 1f else 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
