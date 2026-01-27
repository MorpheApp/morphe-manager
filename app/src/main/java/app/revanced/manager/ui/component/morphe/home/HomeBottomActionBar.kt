package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
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
    Row(
        modifier = modifier
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
            text = stringResource(R.string.morphe_home_sources),
            modifier = Modifier.weight(1f)
        )

        // Right: Settings button with expert mode indicator
        BottomActionButton(
            onClick = onSettingsClick,
            icon = Icons.Outlined.Settings,
            text = stringResource(R.string.settings),
            showExpertBadge = isExpertModeEnabled,
            modifier = Modifier.weight(1f)
        )
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
    showExpertBadge: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val view = LocalView.current

    // Use expert mode colors if badge is shown
    val finalContainerColor = containerColor ?: if (showExpertBadge) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val finalContentColor = contentColor ?: if (showExpertBadge) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(modifier = modifier) {
        Surface(
            onClick = {
                if (enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
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
                        contentDescription = text,
                        tint = finalContentColor.copy(alpha = if (enabled) 1f else 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Expert mode indicator badge
        if (showExpertBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Engineering,
                    contentDescription = "Expert Mode",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
