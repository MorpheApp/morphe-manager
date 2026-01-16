package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toast
import com.topjohnwu.superuser.internal.Utils.context
import kotlinx.coroutines.launch

/**
 * System tab content
 */
@Composable
fun SystemTabContent(
    installerManager: InstallerManager,
    advancedViewModel: AdvancedSettingsViewModel,
    onShowInstallerDialog: (InstallerDialogTarget) -> Unit,
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onAboutClick: () -> Unit,
    prefs: PreferencesManager
) {
    val scope = rememberCoroutineScope()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val useProcessRuntime by prefs.useProcessRuntime.getAsState()
    val memoryLimit by prefs.patcherProcessMemoryLimit.getAsState()

    var showProcessRuntimeDialog by remember { mutableStateOf(false) }

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

        // Performance (only show in Expert mode)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.morphe_performance),
                icon = Icons.Outlined.Speed
            )

            SectionCard {
                RichSettingsItem(
                    onClick = { showProcessRuntimeDialog = true },
                    showBorder = false,
                    title = stringResource(R.string.morphe_process_runtime),
                    subtitle = if (useProcessRuntime) {
                        stringResource(R.string.morphe_process_runtime_enabled_description, memoryLimit)
                    } else {
                        stringResource(R.string.morphe_process_runtime_disabled_description)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoBadge(
                                text = if (useProcessRuntime) stringResource(R.string.morphe_enabled)
                                else stringResource(R.string.morphe_disabled),
                                style = if (useProcessRuntime) InfoBadgeStyle.Primary
                                else InfoBadgeStyle.Default,
                                icon = null,
                                isCompact = true
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }

        // Import & Export (only show in Expert mode)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.import_export),
                icon = Icons.Outlined.SwapHoriz
            )

            SectionCard {
                // Keystore Import
                SettingsItem(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.import_keystore),
                    description = stringResource(R.string.import_keystore_description),
                    onClick = onImportKeystore
                )

                MorpheSettingsDivider()

                // Keystore Export
                SettingsItem(
                    icon = Icons.Outlined.Upload,
                    title = stringResource(R.string.export_keystore),
                    description = stringResource(R.string.export_keystore_description),
                    onClick = {
                        if (!importExportViewModel.canExport()) {
                            context.toast(context.getString(R.string.export_keystore_unavailable))
                        } else {
                            onExportKeystore()
                        }
                    }
                )
            }
        }

        // About
        SectionTitle(
            text = stringResource(R.string.about),
            icon = Icons.Outlined.Info
        )

        SectionCard {
            AboutSection(onAboutClick = onAboutClick)
        }
    }

    // Process Runtime Dialog
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
}
