/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.worker.UpdateCheckInterval
import kotlin.math.roundToInt

/**
 * Updates section settings items for the Advanced tab.
 */
@Composable
fun UpdatesSettingsItem(
    settingsViewModel: SettingsViewModel,
    onManagerPrereleasesToggle: () -> Unit,
    onAutoPatchClick: () -> Unit
) {
    val prefs = settingsViewModel.prefs
    val backgroundUpdateNotifications by prefs.backgroundUpdateNotifications.getAsState()
    val updateCheckInterval by prefs.updateCheckInterval.getAsState()
    val allowMeteredUpdates by prefs.allowMeteredUpdates.getAsState()
    val autoPatchEnabled by prefs.autoPatchEnabled.getAsState()
    val autoPatchInterval by prefs.autoPatchInterval.getAsState()
    val useManagerPrereleases by prefs.useManagerPrereleases.getAsState()
    val usePatchesPrereleases by prefs.bundlePrereleasesEnabled.getAsState()
    val showIntervalDialog = remember { mutableStateOf(false) }

    if (showIntervalDialog.value) {
        UpdateCheckIntervalDialog(
            currentInterval = updateCheckInterval,
            onIntervalSelected = {
                settingsViewModel.selectUpdateInterval(it)
                showIntervalDialog.value = false
            },
            onDismiss = { showIntervalDialog.value = false }
        )
    }

    SettingsGroup {
        // Use manager prereleases toggle
        SettingsSwitchItem(
            checked = useManagerPrereleases,
            onToggle = {
                settingsViewModel.toggleManagerPrereleases(
                    currentValue = useManagerPrereleases,
                    backgroundNotificationsEnabled = backgroundUpdateNotifications,
                    patchesPrereleaseIds = usePatchesPrereleases,
                    onCheckUpdate = onManagerPrereleasesToggle
                )
            },
            icon = Icons.Outlined.Science,
            title = stringResource(R.string.settings_advanced_updates_use_prereleases),
            subtitle = stringResource(R.string.settings_advanced_updates_use_prereleases_description)
        )

        // Check frequency interval selector (non-GMS only), shown when background notifications
        // are enabled from the Notifications settings dialog
        AnimatedVisibility(
            visible = backgroundUpdateNotifications && !settingsViewModel.hasGms,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            Column {
                SettingsDivider()

                SettingsItem(
                    onClick = { showIntervalDialog.value = true },
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.Schedule) },
                    title = stringResource(R.string.settings_advanced_update_interval),
                    subtitle = stringResource(updateCheckInterval.labelResId)
                )
            }
        }

        SettingsDivider()

        // Allow updates on metered connections
        SettingsSwitchItem(
            checked = allowMeteredUpdates,
            onToggle = { settingsViewModel.toggleAllowMeteredUpdates(allowMeteredUpdates) },
            icon = Icons.Outlined.SignalCellularAlt,
            title = stringResource(R.string.settings_advanced_updates_allow_metered),
            subtitle = stringResource(R.string.settings_advanced_updates_allow_metered_description)
        )

        SettingsDivider()

        // Automatic re-patching, configured in its own dialog
        SettingsItem(
            onClick = onAutoPatchClick,
            title = stringResource(R.string.settings_advanced_auto_patch),
            subtitle = if (autoPatchEnabled) {
                stringResource(autoPatchInterval.labelResId)
            } else {
                stringResource(R.string.disabled)
            },
            leadingContent = { ThemedIcon(icon = Icons.Outlined.AutoMode) }
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

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        footer = {
            AppDialogButtonRow(
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
 * Discrete-slider dialog to pick a periodic background interval. Shared by the update check
 * and the automatic re-patch schedule, which is why the wording is passed in.
 */
@Composable
internal fun UpdateCheckIntervalDialog(
    currentInterval: UpdateCheckInterval,
    onIntervalSelected: (UpdateCheckInterval) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.settings_advanced_update_interval_dialog_title),
    chipSubtitle: String = stringResource(R.string.settings_advanced_update_interval_chip_subtitle)
) {
    val entries = UpdateCheckInterval.entries
    var sliderIndex by remember { mutableFloatStateOf(entries.indexOf(currentInterval).toFloat()) }
    val selectedInterval = entries[sliderIndex.roundToInt().coerceIn(entries.indices)]

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current value chip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
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
                        text = chipSubtitle,
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

                SliderScaleLabels(
                    start = stringResource(entries.first().labelResId),
                    end = stringResource(entries.last().labelResId)
                )
            }

            // Battery optimization warning
            Notice(
                text = stringResource(R.string.settings_advanced_update_interval_battery_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.BatteryAlert,
                density = NoticeDensity.Compact
            )
        }
    }
}
