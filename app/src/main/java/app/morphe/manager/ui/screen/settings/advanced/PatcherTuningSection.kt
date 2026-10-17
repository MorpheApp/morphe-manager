/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * How the patcher runs: the process it runs in, and how much of the app it leaves out of the APK.
 */
@Composable
fun PatcherTuningSection(settingsViewModel: SettingsViewModel) {
    val prefs = settingsViewModel.prefs
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()
    val heapLimitIgnored by prefs.patcherHeapLimitIgnored.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()

    val showProcessRuntimeDialog = remember { mutableStateOf(false) }

    DialogHost(visible = showProcessRuntimeDialog.value) {
        ProcessRuntimeDialog(
            currentEnabled = useProcessRuntime,
            currentLimit = memoryLimit,
            heapLimitIgnored = heapLimitIgnored,
            onDismiss = { showProcessRuntimeDialog.value = false },
            onEnabledChange = { settingsViewModel.setProcessRuntime(it) },
            onLimitChange = { settingsViewModel.setMemoryLimit(it) }
        )
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_patcher),
        icon = Icons.Outlined.Build
    ) {
        SettingsGroup {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SettingsItem(
                    onClick = { showProcessRuntimeDialog.value = true },
                    title = stringResource(R.string.settings_system_process_runtime),
                    subtitle = if (useProcessRuntime)
                        stringResource(
                            R.string.settings_system_process_runtime_enabled_description,
                            memoryLimit
                        )
                    else stringResource(R.string.settings_system_process_runtime_disabled_description),
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.Memory) },
                    statusContent = {
                        StatusCircleIcon(
                            icon = Icons.Outlined.Check,
                            containerColor = if (useProcessRuntime) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (useProcessRuntime) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                val title = stringResource(R.string.settings_system_process_runtime)
                val description = stringResource(R.string.settings_system_process_runtime_description_not_available)
                SearchableSetting(listOf(title, description)) {
                    SettingsItemCard(onClick = null) {
                        IconTextRow(
                            modifier = Modifier.padding(Defaults.SpaceLarge),
                            leadingContent = {
                                ThemedIcon(
                                    icon = Icons.Outlined.Memory,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            },
                            title = title,
                            description = description
                        )
                    }
                }
            }

            // Strip unused native libraries + filter split APKs for device
            SettingsSwitchItem(
                checked = stripUnusedNativeLibs,
                onToggle = {
                    settingsViewModel.setStripUnusedNativeLibs(!stripUnusedNativeLibs)
                },
                icon = Icons.Outlined.LayersClear,
                title = stringResource(R.string.settings_advanced_strip_unused_libs),
                subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description)
            )
        }
    }
}
