/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.patcher.BatteryOptimizationDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * Consolidated automatic re-patching settings: the schedule itself, the conditions it waits
 * for, and the external trigger other apps can use to start a queue.
 */
@Composable
fun AutoPatchDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val prefs = settingsViewModel.prefs

    val autoPatchEnabled by prefs.autoPatchEnabled.getAsState()
    val autoPatchInterval by prefs.autoPatchInterval.getAsState()
    val autoPatchRequiresCharging by prefs.autoPatchRequiresCharging.getAsState()
    val autoPatchInstall by prefs.autoPatchInstall.getAsState()
    val externalBatchPatchEnabled by prefs.externalBatchPatchEnabled.getAsState()

    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    // Android defers background jobs in Doze and blocks the foreground service patching needs,
    // so turning the schedule on without this exemption would produce nothing
    if (showBatteryDialog) {
        BatteryOptimizationDialog(onResult = { showBatteryDialog = false })
    }

    if (showIntervalDialog) {
        UpdateCheckIntervalDialog(
            currentInterval = autoPatchInterval,
            title = stringResource(R.string.settings_advanced_auto_patch_interval),
            chipSubtitle = stringResource(R.string.settings_advanced_auto_patch_interval_chip_subtitle),
            onIntervalSelected = {
                settingsViewModel.selectAutoPatchInterval(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_auto_patch),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            SettingsGroup {
                SettingsSwitchItem(
                    checked = autoPatchEnabled,
                    onToggle = {
                        settingsViewModel.toggleAutoPatch(autoPatchEnabled)
                        val enabling = !autoPatchEnabled
                        if (enabling &&
                            powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true
                        ) {
                            showBatteryDialog = true
                        }
                    },
                    icon = Icons.Outlined.AutoMode,
                    title = stringResource(R.string.settings_advanced_auto_patch),
                    subtitle = stringResource(R.string.settings_advanced_auto_patch_description)
                )

                AnimatedVisibility(
                    visible = autoPatchEnabled,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Column {
                        SettingsDivider()

                        SettingsItem(
                            onClick = { showIntervalDialog = true },
                            title = stringResource(R.string.settings_advanced_auto_patch_interval),
                            subtitle = stringResource(autoPatchInterval.labelResId),
                            leadingContent = { ThemedIcon(icon = Icons.Outlined.Schedule) }
                        )

                        SettingsDivider()

                        SettingsSwitchItem(
                            checked = autoPatchRequiresCharging,
                            onToggle = { settingsViewModel.toggleAutoPatchCharging(autoPatchRequiresCharging) },
                            icon = Icons.Outlined.BatteryChargingFull,
                            title = stringResource(R.string.settings_advanced_auto_patch_charging),
                            subtitle = stringResource(R.string.settings_advanced_auto_patch_charging_description)
                        )

                        SettingsDivider()

                        SettingsSwitchItem(
                            checked = autoPatchInstall,
                            onToggle = { settingsViewModel.toggleAutoPatchInstall(autoPatchInstall) },
                            icon = Icons.Outlined.InstallMobile,
                            title = stringResource(R.string.settings_advanced_auto_patch_install),
                            subtitle = stringResource(R.string.settings_advanced_auto_patch_install_description)
                        )
                    }
                }
            }

            SettingsGroup {
                SettingsSwitchItem(
                    checked = externalBatchPatchEnabled,
                    onToggle = { settingsViewModel.toggleExternalBatchPatch(externalBatchPatchEnabled) },
                    icon = Icons.Outlined.Api,
                    title = stringResource(R.string.settings_advanced_external_batch_patch),
                    subtitle = stringResource(R.string.settings_advanced_external_batch_patch_description)
                )
            }
        }
    }
}
