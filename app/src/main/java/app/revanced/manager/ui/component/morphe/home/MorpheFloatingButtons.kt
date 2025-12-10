package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        // Settings FAB
        FloatingActionButton(
            onClick = onSettingsClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings)
            )
        }
    }
}
