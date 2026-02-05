package app.revanced.manager.ui.screen.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.OriginalApkRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.ui.screen.settings.system.*
import app.revanced.manager.ui.screen.shared.*
import app.revanced.manager.ui.viewmodel.SettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * System tab content
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCheck")
@Composable
fun SystemTabContent(
    installerManager: InstallerManager,
    settingsViewModel: SettingsViewModel,
    onShowInstallerDialog: () -> Unit,
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit,
    onExportDebugLogs: () -> Unit,
    onAboutClick: () -> Unit,
    onChangelogClick: () -> Unit,
    prefs: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val useExpertMode by prefs.useExpertMode.getAsState()
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()

    var showProcessRuntimeDialog by remember { mutableStateOf(false) }
    var showApkManagementDialog by remember { mutableStateOf<ApkManagementType?>(null) }
    var showPatchSelectionDialog by remember { mutableStateOf(false) }

    // Extract strings to avoid LocalContext issues
    val keystoreUnavailable = stringResource(R.string.settings_system_export_keystore_unavailable)

    // Process runtime dialog
    if (showProcessRuntimeDialog) {
        ProcessRuntimeDialog(
            currentEnabled = useProcessRuntime,
            currentLimit = memoryLimit,
            onDismiss = { showProcessRuntimeDialog = false },
            onConfirm = { enabled, limit ->
                scope.launch {
                    prefs.useProcessRuntime.update(enabled)
                    prefs.patcherProcessMemoryLimit.update(limit)
                    showProcessRuntimeDialog = false
                }
            }
        )
    }

    // APK management dialog
    showApkManagementDialog?.let { type ->
        ApkManagementDialog(
            type = type,
            onDismissRequest = { showApkManagementDialog = null }
        )
    }

    // Patch selection management dialog
    if (showPatchSelectionDialog) {
        PatchSelectionManagementDialog(
            onDismissRequest = { showPatchSelectionDialog = false }
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
                installerManager = installerManager,
                settingsViewModel = settingsViewModel,
                onShowInstallerDialog = onShowInstallerDialog
            )
        }

        // Performance (Expert mode only)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.settings_system_performance),
                icon = Icons.Outlined.Speed
            )

            SectionCard {
                RichSettingsItem(
                    onClick = { showProcessRuntimeDialog = true },
                    title = stringResource(R.string.settings_system_process_runtime),
                    subtitle = if (useProcessRuntime)
                        stringResource(R.string.settings_system_process_runtime_enabled_description, memoryLimit)
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
                val originalApkRepository: OriginalApkRepository = koinInject()
                val allOriginalApks by originalApkRepository.getAll().collectAsStateWithLifecycle(emptyList())
                val originalApkCount = allOriginalApks.size

                RichSettingsItem(
                    onClick = { showApkManagementDialog = ApkManagementType.ORIGINAL },
                    title = stringResource(R.string.settings_system_original_apks_title),
                    subtitle = stringResource(R.string.settings_system_original_apks_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.FolderOpen)
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
                val installedAppRepository: InstalledAppRepository = koinInject()
                val allInstalledApps by installedAppRepository.getAll().collectAsStateWithLifecycle(emptyList())
                val patchedApkCount = allInstalledApps.size

                RichSettingsItem(
                    onClick = { showApkManagementDialog = ApkManagementType.PATCHED },
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

                MorpheSettingsDivider()

                // Patch Selections management with grouped count
                val selectionRepository: PatchSelectionRepository = koinInject()
                val optionsRepository: PatchOptionsRepository = koinInject()
                val packagesWithSelection by selectionRepository.getPackagesWithSavedSelection()
                    .collectAsStateWithLifecycle(emptySet())
                val packagesWithOptions by optionsRepository.getPackagesWithSavedOptions()
                    .collectAsStateWithLifecycle(emptySet())

                // Calculate grouped count
                var groupedSelectionsCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(packagesWithSelection, packagesWithOptions) {
                    val allPackages = packagesWithSelection + packagesWithOptions
                    if (allPackages.isEmpty()) {
                        groupedSelectionsCount = 0
                    } else {
                        withContext(Dispatchers.IO) {
                            val packageGroups = groupPackagesByOriginal(
                                allPackages,
                                installedAppRepository
                            )
                            groupedSelectionsCount = packageGroups.size
                        }
                    }
                }

                RichSettingsItem(
                    onClick = { showPatchSelectionDialog = true },
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
                            if (groupedSelectionsCount > 0) {
                                InfoBadge(
                                    text = groupedSelectionsCount.toString(),
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
