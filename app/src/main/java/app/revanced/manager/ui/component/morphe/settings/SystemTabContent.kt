package app.revanced.manager.ui.component.morphe.settings

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
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.OriginalApkRepository
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * System tab content
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SystemTabContent(
    installerManager: InstallerManager,
    advancedViewModel: AdvancedSettingsViewModel,
    onShowInstallerDialog: () -> Unit,
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit,
    onAboutClick: () -> Unit,
    prefs: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val useExpertMode by prefs.useExpertMode.getAsState()
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()

    var showProcessRuntimeDialog by remember { mutableStateOf(false) }
    var showApkManagementDialog by remember { mutableStateOf<ApkManagementType?>(null) }

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
                advancedViewModel = advancedViewModel,
                onShowInstallerDialog = onShowInstallerDialog
            )
        }

        // Performance (Expert mode only)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.morphe_performance),
                icon = Icons.Outlined.Speed
            )

            SectionCard {
                RichSettingsItem(
                    onClick = { showProcessRuntimeDialog = true },
                    title = stringResource(R.string.morphe_process_runtime),
                    subtitle = if (useProcessRuntime)
                        stringResource(R.string.morphe_process_runtime_enabled_description, memoryLimit)
                    else stringResource(R.string.morphe_process_runtime_disabled_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Memory)
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoBadge(
                                text = if (useProcessRuntime) stringResource(R.string.morphe_enabled)
                                else stringResource(R.string.morphe_disabled),
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
                text = stringResource(R.string.import_export),
                icon = Icons.Outlined.SwapHoriz
            )

            SectionCard {
                Column {
                    // Keystore Import
                    BaseSettingsItem(
                        onClick = onImportKeystore,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Key) },
                        title = stringResource(R.string.import_keystore),
                        description = stringResource(R.string.import_keystore_description)
                    )

                    MorpheSettingsDivider()

                    // Keystore Export
                    BaseSettingsItem(
                        onClick = {
                            if (!importExportViewModel.canExport()) {
                                context.toast(context.getString(R.string.export_keystore_unavailable))
                            } else {
                                onExportKeystore()
                            }
                        },
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Upload) },
                        title = stringResource(R.string.export_keystore),
                        description = stringResource(R.string.export_keystore_description)
                    )
                }
            }

            SectionCard {
                Column {
                    // Manager Settings Import
                    BaseSettingsItem(
                        onClick = onImportSettings,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Download) },
                        title = stringResource(R.string.import_manager_settings),
                        description = stringResource(R.string.import_manager_settings_description)
                    )

                    MorpheSettingsDivider()

                    // Manager Settings Export
                    BaseSettingsItem(
                        onClick = onExportSettings,
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Upload) },
                        title = stringResource(R.string.export_manager_settings),
                        description = stringResource(R.string.export_manager_settings_description)
                    )
                }
            }
        }

        // Storage Management Section
        SectionTitle(
            text = stringResource(R.string.morphe_storage_management),
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
                    title = stringResource(R.string.morphe_original_apks_title),
                    subtitle = stringResource(R.string.morphe_original_apks_description),
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
                    title = stringResource(R.string.morphe_patched_apks_title),
                    subtitle = stringResource(R.string.morphe_patched_apks_description),
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
            }
        }

        // About Section
        SectionTitle(
            text = stringResource(R.string.about),
            icon = Icons.Outlined.Info
        )

        SectionCard {
            AboutSection(onAboutClick = onAboutClick)
        }
    }
}
