/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.worker.UpdateCheckInterval
import app.morphe.manager.util.syncFcmTopics
import app.morphe.manager.worker.UpdateCheckWorker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch

/**
 * Returns true if Google Play Services is available and functional on this device.
 * When GMS is available, FCM is the primary notification channel and WorkManager
 * interval settings are not relevant to the user.
 */
private fun isGmsAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

/**
 * Updates section settings item for the Advanced tab.
 *
 * @param usePrereleases Current value of the prereleases preference.
 * @param onPrereleasesToggle Called when the prereleases switch is flipped.
 * @param prefs Full [PreferencesManager] used to read and write notification / interval prefs.
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

    // On GMS devices FCM handles all notification delivery.
    val hasGms = remember { isGmsAvailable(context) }

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
                    syncFcmTopics(notificationsEnabled = true, usePrereleases = usePrereleases)
                    if (!hasGms) UpdateCheckWorker.schedule(context, updateCheckInterval)
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
                if (!hasGms) UpdateCheckWorker.schedule(context, selected)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    // Use prereleases toggle
    RichSettingsItem(
        onClick = onPrereleasesToggle,
        showBorder = true,
        leadingContent = { MorpheIcon(icon = Icons.Outlined.Science) },
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
                    syncFcmTopics(newValue, usePrereleases)
                    if (newValue && !hasGms) UpdateCheckWorker.schedule(context, updateCheckInterval)
                    else UpdateCheckWorker.cancel(context)
                }
            }
        },
        showBorder = true,
        leadingContent = { MorpheIcon(icon = Icons.Outlined.NotificationsActive) },
        title = stringResource(R.string.settings_advanced_updates_background_notifications),
        subtitle = stringResource(
            if (hasGms) R.string.settings_advanced_updates_background_notifications_description_fcm
            else R.string.settings_advanced_updates_background_notifications_description
        ),
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
        visible = backgroundUpdateNotifications && !hasGms,
        enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
    ) {
        RichSettingsItem(
            onClick = { showIntervalDialog = true },
            showBorder = true,
            leadingContent = { MorpheIcon(icon = Icons.Outlined.Schedule) },
            title = stringResource(R.string.settings_advanced_update_interval),
            subtitle = stringResource(updateCheckInterval.labelResId)
        )
    }
}

/**
 * Dialog shown on Android 13+ when the user enables background notifications
 * and [Manifest.permission.POST_NOTIFICATIONS] has not yet been granted.
 */
@Composable
fun NotificationPermissionDialog(
    onDismissRequest: () -> Unit,
    onPermissionResult: (granted: Boolean) -> Unit,
    title: String = stringResource(R.string.notification_permission_dialog_title),
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult
    )

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = title,
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
 * Discrete-slider dialog to pick the background update check interval.
 */
@Composable
private fun UpdateCheckIntervalDialog(
    currentInterval: UpdateCheckInterval,
    onIntervalSelected: (UpdateCheckInterval) -> Unit,
    onDismiss: () -> Unit
) {
    val entries = UpdateCheckInterval.entries
    var sliderIndex by remember { mutableFloatStateOf(entries.indexOf(currentInterval).toFloat()) }
    val selectedInterval = entries[sliderIndex.toInt().coerceIn(entries.indices)]

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_update_interval_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onIntervalSelected(selectedInterval) },
                primaryIcon = Icons.Outlined.Check,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Current value chip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(selectedInterval.labelResId),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalDialogTextColor.current
                    )
                    Text(
                        text = stringResource(R.string.settings_advanced_update_interval_chip_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = sliderIndex,
                    onValueChange = { sliderIndex = it },
                    valueRange = 0f..(entries.size - 1).toFloat(),
                    steps = entries.size - 2, // n entries → n-2 internal steps
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(entries.first().labelResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                    Text(
                        text = stringResource(entries.last().labelResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            }

            // Battery optimisation warning
            InfoBadge(
                text = stringResource(R.string.settings_advanced_update_interval_battery_warning),
                style = InfoBadgeStyle.Warning,
                icon = Icons.Outlined.BatteryAlert
            )
        }
    }
}
