package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.WindowWidthSizeClass
import app.morphe.manager.ui.screen.shared.isLandscape
import app.morphe.manager.ui.screen.shared.rememberWindowSize

/**
 * Standard icon-based option card for appearance settings
 * Used for backgrounds, themes, and other icon-based selections
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
    val selectedText = stringResource(R.string.selected)
    val notSelectedText = stringResource(R.string.not_selected)

    val windowSize = rememberWindowSize()
    val iconSize = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 32.dp
        WindowWidthSizeClass.Medium -> 36.dp
        WindowWidthSizeClass.Expanded -> 40.dp
    }

    // Increase height in landscape to prevent text clipping
    val cardHeight = if (isLandscape()) 92.dp else 80.dp

    Surface(
        modifier = modifier.height(cardHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.RadioButton
                    stateDescription = if (selected) selectedText else notSelectedText
                },
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
 * Compact horizontal card for single-row selections
 * Used for "Not selected" option in color picker
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
    val selectedText = stringResource(R.string.selected)
    val notSelectedText = stringResource(R.string.not_selected)

    // Increase height slightly in landscape for better text display
    val cardHeight = if (isLandscape()) 60.dp else 56.dp

    Surface(
        modifier = modifier.height(cardHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.RadioButton
                    stateDescription = if (selected) selectedText else notSelectedText
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier.size(24.dp)
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
