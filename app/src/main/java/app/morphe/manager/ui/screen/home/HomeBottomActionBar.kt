package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Section 5: Bottom action bar
 * Bundles and Settings buttons positioned left and right
 */
@Composable
fun HomeBottomActionBar(
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isExpertModeEnabled: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Sources button
            BottomActionButton(
                onClick = onBundlesClick,
                icon = Icons.Outlined.Source,
                text = stringResource(R.string.sources_management_title),
                modifier = Modifier.weight(1f)
            )

            // Right: Settings button with expert mode indicator
            BottomActionButton(
                onClick = onSettingsClick,
                icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
                text = stringResource(R.string.settings),
                isExpertMode = isExpertModeEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual bottom action button
 * Rectangular shape with rounded corners
 */
@Composable
fun BottomActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    isExpertMode: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val view = LocalView.current

    // Use expert mode colors if enabled
    val finalContainerColor = containerColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val finalContentColor = contentColor ?: if (isExpertMode) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    // Build content description for accessibility
    val contentDesc = buildString {
        text?.let { append(it) }
        if (isExpertMode) {
            append(", ")
            append(stringResource(R.string.settings_advanced_expert_mode))
        }
        if (showProgress) {
            append(", ")
            append(stringResource(R.string.loading))
        }
    }

    Surface(
        onClick = {
            if (enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDesc
                if (showProgress) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        shape = shape,
        color = finalContainerColor.copy(alpha = if (enabled) 1f else 0.5f),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    finalContentColor.copy(alpha = if (enabled) 0.2f else 0.1f),
                    finalContentColor.copy(alpha = if (enabled) 0.1f else 0.05f)
                )
            )
        ),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = finalContentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
