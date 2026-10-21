/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.system.BatteryOptimizationItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/**
 * How the patcher runs: the process it runs in, whether it keeps running in the background, how
 * much of the app it leaves out of the APK, and whether other apps may start it, on a page of
 * their own, see [SettingsSubpage.PATCHER]. A change made here is saved at once.
 */
@Composable
fun PatcherSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
    val prefs = settingsViewModel.prefs
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()
    val externalBatchPatchEnabled by prefs.externalBatchPatchEnabled.getAsState()

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_system_process_runtime),
            icon = MaterialSymbols.Rounded.Memory
        ) {
            if (settingsViewModel.jvmRuntimeAvailable) {
                JvmRuntimeSettings(settingsViewModel)
            } else {
                JvmRuntimeUnavailable()
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_patching),
            icon = MaterialSymbols.Rounded.Auto_fix_high
        ) {
            SettingsGroup {
                BatteryOptimizationItem()
                // Strip unused native libraries + filter split APKs for device
                SettingsSwitchItem(
                    checked = stripUnusedNativeLibs,
                    onToggle = { settingsViewModel.setStripUnusedNativeLibs(!stripUnusedNativeLibs) },
                    icon = MaterialSymbols.Rounded.Layers_clear,
                    title = stringResource(R.string.settings_advanced_strip_unused_libs),
                    subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description)
                )
                // Entry point other apps use to start a re-patch queue
                SettingsSwitchItem(
                    checked = externalBatchPatchEnabled,
                    onToggle = { settingsViewModel.toggleExternalBatchPatch(externalBatchPatchEnabled) },
                    icon = MaterialSymbols.Rounded.Api,
                    title = stringResource(R.string.settings_advanced_external_batch_patch),
                    subtitle = stringResource(R.string.settings_advanced_external_batch_patch_description)
                )
            }
        }
    }
}

/** Whether patching runs in the embedded JVM. */
@Composable
private fun JvmRuntimeSettings(settingsViewModel: SettingsViewModel) {
    val enabled by settingsViewModel.prefs.useJvmRuntime.getAsState()

    SettingsGroup {
        SettingsSwitchItem(
            checked = enabled,
            onToggle = { settingsViewModel.setJvmRuntime(!enabled) },
            icon = MaterialSymbols.Rounded.Call_split,
            title = stringResource(R.string.settings_system_jvm_runtime_enable),
            subtitle = stringResource(R.string.settings_system_jvm_runtime_description)
        )
    }
}

/** The JVM runtime greyed out, for a device the app ships no JVM for. */
@Composable
private fun JvmRuntimeUnavailable() {
    val title = stringResource(R.string.settings_system_jvm_runtime_enable)
    val description = stringResource(R.string.settings_system_jvm_runtime_unavailable)
    SettingsGroup {
        SearchableSetting(listOf(title, description)) {
            SettingsItemCard(onClick = null) {
                IconTextRow(
                    modifier = Modifier.padding(Defaults.SpaceLarge),
                    leadingContent = {
                        ThemedIcon(
                            icon = MaterialSymbols.Rounded.Call_split,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    },
                    title = title,
                    description = description
                )
            }
        }
    }
}
