/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.toast
import app.morphe.patcher.dex.BytecodeMode

/**
 * System tab content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCheck")
@Composable
fun SystemTabContent(
    settingsViewModel: SettingsViewModel,
    onShowInstallerDialog: () -> Unit,
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit,
    onExportDebugLogs: () -> Unit,
    onAboutClick: () -> Unit,
    onChangelogClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = settingsViewModel.prefs
    val useExpertMode by prefs.useExpertMode.getAsState()
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()
    val bytecodeMode by prefs.bytecodeModePreference.getAsState()

    val showProcessRuntimeDialog = remember { mutableStateOf(false) }
    val showBytecodeDialog = remember { mutableStateOf(false) }
    val showApkManagementDialog = remember { mutableStateOf<ApkManagementType?>(null) }
    val showPatchSelectionDialog = remember { mutableStateOf(false) }

    // Extract strings to avoid LocalContext issues
    val keystoreUnavailable = stringResource(R.string.settings_system_export_keystore_unavailable)

    // Storage counts
    val originalApkCount by settingsViewModel.originalApkCount.collectAsStateWithLifecycle()
    val patchedApkCount by settingsViewModel.patchedApkCount.collectAsStateWithLifecycle()
    val patchedPackagesCount by settingsViewModel.patchedPackagesCount.collectAsStateWithLifecycle()

    if (showProcessRuntimeDialog.value) {
        ProcessRuntimeDialog(
            currentEnabled = useProcessRuntime,
            currentLimit = memoryLimit,
            onDismiss = { showProcessRuntimeDialog.value = false },
            onEnabledChange = { settingsViewModel.setProcessRuntime(it) },
            onLimitChange = { settingsViewModel.setMemoryLimit(it) }
        )
    }

    if (showBytecodeDialog.value) {
        BytecodeModeDialog(
            current = bytecodeMode,
            onDismiss = { showBytecodeDialog.value = false },
            onSelect = { settingsViewModel.setBytecodeMode(it) }
        )
    }

    // APK management dialog
    showApkManagementDialog.value?.let { type ->
        ApkManagementDialog(
            type = type,
            onDismissRequest = { showApkManagementDialog.value = null }
        )
    }

    // Patch selection management dialog
    if (showPatchSelectionDialog.value) {
        PatchSelectionManagementDialog(
            settingsViewModel = settingsViewModel,
            importExportViewModel = importExportViewModel,
            onDismiss = { showPatchSelectionDialog.value = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Installers
        SectionTitle(
            text = stringResource(R.string.installer),
            icon = Icons.Outlined.InstallMobile
        )

        SectionCard {
            InstallerSection(
                settingsViewModel = settingsViewModel,
                onShowInstallerDialog = onShowInstallerDialog
            )
        }

        // Performance
        SectionTitle(
            text = stringResource(R.string.settings_system_performance),
            icon = Icons.Outlined.Speed
        )

        SectionCard {
            Column {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    RichSettingsItem(
                        onClick = { showProcessRuntimeDialog.value = true },
                        title = stringResource(R.string.settings_system_process_runtime),
                        subtitle = if (useProcessRuntime)
                            stringResource(
                                R.string.settings_system_process_runtime_enabled_description,
                                memoryLimit
                            )
                        else stringResource(R.string.settings_system_process_runtime_disabled_description),
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.Memory)
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoBadge(
                                    text = if (useProcessRuntime) stringResource(R.string.enabled)
                                    else stringResource(R.string.disabled),
                                    style = if (useProcessRuntime) InfoBadgeStyle.Primary else InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                                MorpheIcon(icon = Icons.Outlined.ChevronRight)
                            }
                        }
                    )
                } else {
                    IconTextRow(
                        modifier = Modifier.padding(16.dp),
                        leadingContent = {
                            MorpheIcon(
                                icon = Icons.Outlined.Memory,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        },
                        title = stringResource(R.string.settings_system_process_runtime),
                        description = stringResource(R.string.settings_system_process_runtime_description_not_available)
                    )
                }

                MorpheSettingsDivider()

                RichSettingsItem(
                    onClick = { showBytecodeDialog.value = true },
                    title = stringResource(R.string.settings_advanced_bytecode_mode),
                    subtitle = stringResource(bytecodeMode.labelRes()),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Code) },
                    trailingContent = { MorpheIcon(icon = Icons.Outlined.ChevronRight) }
                )
            }
        }

        // Import & Export (Expert mode only)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.settings_system_import_export),
                icon = Icons.Outlined.SwapHoriz
            )

            SectionCard {
                Column {
                    // Keystore Import
                    BaseSettingsItem(
                        onClick = onImportKeystore,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Key) },
                        title = stringResource(R.string.settings_system_import_keystore),
                        description = stringResource(R.string.settings_system_import_keystore_description)
                    )

                    MorpheSettingsDivider()

                    // Keystore Export
                    BaseSettingsItem(
                        onClick = {
                            if (!importExportViewModel.canExport()) {
                                context.toast(keystoreUnavailable)
                            } else {
                                onExportKeystore()
                            }
                        },
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Upload) },
                        title = stringResource(R.string.settings_system_export_keystore),
                        description = stringResource(R.string.settings_system_export_keystore_description)
                    )
                }
            }

            SectionCard {
                Column {
                    // Manager Settings Import
                    BaseSettingsItem(
                        onClick = onImportSettings,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Download) },
                        title = stringResource(R.string.settings_system_import_manager_settings),
                        description = stringResource(R.string.settings_system_import_manager_settings_description)
                    )

                    MorpheSettingsDivider()

                    // Manager Settings Export
                    BaseSettingsItem(
                        onClick = onExportSettings,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Upload) },
                        title = stringResource(R.string.settings_system_export_manager_settings),
                        description = stringResource(R.string.settings_system_export_manager_settings_description)
                    )
                }
            }
        }

        // Debug Logs (Expert mode only)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.settings_system_debug),
                icon = Icons.Outlined.BugReport
            )

            SectionCard {
                BaseSettingsItem(
                    onClick = onExportDebugLogs,
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Upload) },
                    title = stringResource(R.string.settings_system_export_debug_logs),
                    description = stringResource(R.string.settings_system_export_debug_logs_description)
                )
            }
        }

        // Storage Management Section
        SectionTitle(
            text = stringResource(R.string.settings_system_storage_management),
            icon = Icons.Outlined.Storage
        )

        SectionCard {
            Column {
                // Original APKs management
                RichSettingsItem(
                    onClick = { showApkManagementDialog.value = ApkManagementType.ORIGINAL },
                    title = stringResource(R.string.settings_system_original_apks_title),
                    subtitle = stringResource(R.string.settings_system_original_apks_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Storage)
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (originalApkCount > 0) {
                                InfoBadge(
                                    text = originalApkCount.toString(),
                                    style = InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                            }
                            MorpheIcon(icon = Icons.Outlined.ChevronRight)
                        }
                    }
                )

                MorpheSettingsDivider()

                // Patched APKs management
                RichSettingsItem(
                    onClick = { showApkManagementDialog.value = ApkManagementType.PATCHED },
                    title = stringResource(R.string.settings_system_patched_apks_title),
                    subtitle = stringResource(R.string.settings_system_patched_apks_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Apps)
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (patchedApkCount > 0) {
                                InfoBadge(
                                    text = patchedApkCount.toString(),
                                    style = InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                            }
                            MorpheIcon(icon = Icons.Outlined.ChevronRight)
                        }
                    }
                )

                // Patch Selections management (Expert mode only)
                if (useExpertMode) {
                    MorpheSettingsDivider()

                    RichSettingsItem(
                        onClick = { showPatchSelectionDialog.value = true },
                        title = stringResource(R.string.settings_system_patch_selections_title),
                        subtitle = stringResource(R.string.settings_system_patch_selections_description),
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.Tune)
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (patchedPackagesCount > 0) {
                                    InfoBadge(
                                        text = patchedPackagesCount.toString(),
                                        style = InfoBadgeStyle.Default,
                                        isCompact = true
                                    )
                                }
                                MorpheIcon(icon = Icons.Outlined.ChevronRight)
                            }
                        }
                    )
                }
            }
        }

        // About Section
        SectionTitle(
            text = stringResource(R.string.settings_system_about),
            icon = Icons.Outlined.Info
        )

        SectionCard {
            AboutSection(
                onAboutClick = onAboutClick,
                onChangelogClick = onChangelogClick
            )
        }
    }
}

/** Maps a [BytecodeMode] to its short display label string resource. */
private fun BytecodeMode.labelRes(): Int = when (this) {
    BytecodeMode.NONE -> R.string.settings_advanced_bytecode_mode_strip_fast
    BytecodeMode.STRIP_SAFE -> R.string.settings_advanced_bytecode_mode_strip_fast
    BytecodeMode.STRIP_FAST -> R.string.settings_advanced_bytecode_mode_strip_fast
    BytecodeMode.FULL -> R.string.settings_advanced_bytecode_mode_full
}
