package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.SectionCard
import app.revanced.manager.ui.component.morphe.shared.SectionTitle
import app.revanced.manager.ui.component.morphe.shared.SettingsItemCard
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
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

        // Performance  (only show in Expert mode)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.morphe_performance),
                icon = Icons.Outlined.Speed
            )

            SectionCard {
                SettingsItemCard(
                    onClick = { showProcessRuntimeDialog = true },
                    borderWidth = 0.dp
                ) {
                    IconTextRow(
                        icon = Icons.Outlined.Memory,
                        title = stringResource(R.string.morphe_process_runtime),
                        description = if (useProcessRuntime) {
                            stringResource(
                                R.string.morphe_process_runtime_enabled_description,
                                memoryLimit
                            )
                        } else {
                            stringResource(R.string.morphe_process_runtime_disabled_description)
                        },
                        modifier = Modifier.padding(16.dp),
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (useProcessRuntime) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.morphe_enabled),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            )
                                        )
                                    }
                                }
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
        }

        // Import & Export (only show in Expert mode)
        if (useExpertMode) {
            SectionTitle(
                text = stringResource(R.string.import_export),
                icon = Icons.Outlined.SwapHoriz
            )

            SectionCard {
                ImportExportSection(
                    importExportViewModel = importExportViewModel,
                    onImportKeystore = onImportKeystore,
                    onExportKeystore = onExportKeystore
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
