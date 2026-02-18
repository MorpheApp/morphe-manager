/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.worker.UpdateCheckInterval
import app.morphe.manager.worker.UpdateCheckWorker
import kotlinx.coroutines.launch

/**
 * Updates section settings item for the Advanced tab.
 *
 * @param usePrereleases Current value of the prereleases preference
 * @param onPrereleasesToggle Called when the prereleases switch is flipped
 * @param prefs Full [PreferencesManager] used to read and write notification / interval prefs
 */
@Composable
fun UpdatesSettingsItem(
    usePrereleases: Boolean,
    onPrereleasesToggle: () -> Unit,
    prefs: PreferencesManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backgroundUpdateNotifications by prefs.backgroundUpdateNotifications.getAsState()
    val updateCheckInterval by prefs.updateCheckInterval.getAsState()

    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    // Dialog visibility state
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }

    // Checks whether POST_NOTIFICATIONS is granted (Android 13+ only)
    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Auto-granted on Android < 13
        }

    if (showNotificationPermissionDialog) {
        NotificationPermissionDialog(
            onDismissRequest = {
                // User cancelled - revert the preference back to OFF
                scope.launch { prefs.backgroundUpdateNotifications.update(false) }
                showNotificationPermissionDialog = false
            },
            onPermissionResult = { granted ->
                showNotificationPermissionDialog = false
                if (granted) {
                    UpdateCheckWorker.schedule(context, updateCheckInterval)
                } else {
                    scope.launch { prefs.backgroundUpdateNotifications.update(false) }
                }
            }
        )
    }

    if (showIntervalDialog) {
        UpdateCheckIntervalDialog(
            currentInterval = updateCheckInterval,
            onIntervalSelected = { selected ->
                scope.launch { prefs.updateCheckInterval.update(selected) }
                UpdateCheckWorker.schedule(context, selected)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    // Use prereleases toggle
    RichSettingsItem(
        onClick = onPrereleasesToggle,
        showBorder = true,
        leadingContent = {
            MorpheIcon(icon = Icons.Outlined.Science)
        },
        title = stringResource(R.string.settings_advanced_updates_use_prereleases),
        subtitle = stringResource(R.string.settings_advanced_updates_use_prereleases_description),
        trailingContent = {
            Switch(
                checked = usePrereleases,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    stateDescription = if (usePrereleases) enabledState else disabledState
                }
            )
        }
    )

    // Background update notifications toggle
    RichSettingsItem(
        onClick = {
            val newValue = !backgroundUpdateNotifications
            if (newValue && !hasNotificationPermission()) {
                // Save optimistically - dialog reverts if permission is denied
                scope.launch { prefs.backgroundUpdateNotifications.update(true) }
                showNotificationPermissionDialog = true
            } else {
                scope.launch {
                    prefs.backgroundUpdateNotifications.update(newValue)
                    if (newValue) UpdateCheckWorker.schedule(context, updateCheckInterval)
                    else UpdateCheckWorker.cancel(context)
                }
            }
        },
        showBorder = true,
        leadingContent = {
            MorpheIcon(icon = Icons.Outlined.NotificationsActive)
        },
        title = stringResource(R.string.settings_advanced_updates_background_notifications),
        subtitle = stringResource(R.string.settings_advanced_updates_background_notifications_description),
        trailingContent = {
            Switch(
                checked = backgroundUpdateNotifications,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    stateDescription =
                        if (backgroundUpdateNotifications) enabledState else disabledState
                }
            )
        }
    )

    // Check frequency interval selector
    AnimatedVisibility(
        visible = backgroundUpdateNotifications,
        enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
    ) {
        RichSettingsItem(
            onClick = { showIntervalDialog = true },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Schedule)
            },
            title = stringResource(R.string.settings_advanced_update_interval),
            subtitle = stringResource(updateCheckInterval.labelResId)
        )
    }
}

/**
 * Dialog shown on Android 13+ when the user enables background notifications
 * and [Manifest.permission.POST_NOTIFICATIONS] has not yet been granted.
 *
 * Triggers the system permission request. If denied, [onPermissionResult] is
 * called with `false` and the caller is expected to revert the preference.
 */
@Composable
fun NotificationPermissionDialog(
    onDismissRequest: () -> Unit,
    onPermissionResult: (granted: Boolean) -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult
    )

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.notification_permission_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.allow),
                onPrimaryClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onPermissionResult(true)
                    }
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismissRequest
            )
        }
    ) {
        Text(
            text = stringResource(R.string.notification_permission_dialog_description),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Dialog to select how often the background update check should run.
 * Displays all [UpdateCheckInterval] entries as selectable rows with radio buttons.
 */
@Composable
private fun UpdateCheckIntervalDialog(
    currentInterval: UpdateCheckInterval,
    onIntervalSelected: (UpdateCheckInterval) -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_update_interval_dialog_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateCheckInterval.entries.forEach { interval ->
                val isSelected = interval == currentInterval
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onIntervalSelected(interval) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            LocalDialogTextColor.current.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = LocalDialogTextColor.current.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = stringResource(interval.labelResId),
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalDialogTextColor.current,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
