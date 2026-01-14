package app.revanced.manager.ui.component.morphe.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Section 5: Bottom action bar
 * Three rectangular buttons with fixed position at bottom
 * Left: Installed/Patched Apps | Center: Bundles | Right: Settings
 */
@Composable
fun HomeBottomActionBar(
    onInstalledAppsClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Installed Apps button
        BottomActionButton(
            onClick = onInstalledAppsClick,
            icon = Icons.Outlined.Apps,
            text = stringResource(R.string.installed),
            modifier = Modifier.weight(1f)
        )

        // Center: Bundles button
        BottomActionButton(
            onClick = onBundlesClick,
            icon = Icons.Outlined.Source,
            text = stringResource(R.string.morphe_home_bundles),
            modifier = Modifier.weight(1f)
        )

        // Right: Settings button
        BottomActionButton(
            onClick = onSettingsClick,
            icon = Icons.Default.Settings,
            text = stringResource(R.string.settings),
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
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true,
    showProgress: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val view = LocalView.current

    Surface(
        onClick = {
            if (enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        },
        modifier = modifier.height(56.dp),
        shape = shape,
        color = containerColor.copy(alpha = if (enabled) 1f else 0.5f),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    contentColor.copy(alpha = if (enabled) 0.2f else 0.1f),
                    contentColor.copy(alpha = if (enabled) 0.1f else 0.05f)
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
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor.copy(alpha = if (enabled) 1f else 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
//            if (text != null) {
//                Spacer(Modifier.width(8.dp))
//                Text(
//                    text = text,
//                    style = MaterialTheme.typography.labelLarge,
//                    color = contentColor.copy(alpha = if (enabled) 1f else 0.5f)
//                )
//            }
        }
    }
}
