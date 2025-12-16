package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Floating action buttons for MorpheHomeScreen
 * Displays update, bundles, and settings buttons
 */
@Composable
fun MorpheFloatingButtons(
    onUpdateClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasManagerUpdate: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Settings FAB - top right, circular
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            // Main settings button
            SmallFloatingActionButton(
                onClick = onSettingsClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Update and Source FABs - bottom right
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 48.dp), // Added 48dp bottom padding
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Update FAB with badge for manager updates
            if (hasManagerUpdate) {
                FloatingActionButton(
                    onClick = onUpdateClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    BadgedBox(
                        badge = {
                            Badge(modifier = Modifier.size(6.dp))
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Update,
                            contentDescription = stringResource(R.string.update)
                        )
                    }
                }
            }

            // Source/Bundles FAB
            FloatingActionButton(
                onClick = onBundlesClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    Icons.Outlined.Source,
                    contentDescription = stringResource(R.string.morphe_home_bundles)
                )
            }
        }
    }
}
