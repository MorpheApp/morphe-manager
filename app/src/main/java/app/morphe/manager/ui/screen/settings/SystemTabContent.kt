/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.SectionCard
import app.morphe.manager.ui.screen.shared.SectionTitle
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * System tab content.
 */
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
    onChangelogClick: () -> Unit,
    onStartTour: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    onInstallerSectionPositioned: ((Rect) -> Unit)? = null,
    onInstallerScrollTarget: ((Int) -> Unit)? = null,
    onProcessRuntimePositioned: ((Rect) -> Unit)? = null,
    onProcessRuntimeScrollTarget: ((Int) -> Unit)? = null,
    onFilePickerPositioned: ((Rect) -> Unit)? = null,
    onFilePickerScrollTarget: ((Int) -> Unit)? = null
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Installers
        SectionTitle(
            text = stringResource(R.string.installer),
            icon = Icons.Outlined.InstallMobile
        )

        SectionCard(
            modifier = if (onInstallerScrollTarget != null) Modifier.onGloballyPositioned { coords ->
                onInstallerScrollTarget(coords.boundsInParent().top.roundToInt())
            } else Modifier
        ) {
            InstallerSection(
                settingsViewModel = settingsViewModel,
                onShowInstallerDialog = onShowInstallerDialog,
                onInstallerItemPositioned = onInstallerSectionPositioned
            )
        }

        // Performance
        Box(
            modifier = if (onProcessRuntimeScrollTarget != null) Modifier.onGloballyPositioned { coords ->
                onProcessRuntimeScrollTarget(coords.boundsInParent().top.roundToInt())
            } else Modifier
        ) {
            PerformanceSection(
                settingsViewModel = settingsViewModel,
                onProcessRuntimePositioned = onProcessRuntimePositioned
            )
        }

        // Import & Export (Expert mode only)
        if (useExpertMode) {
            ImportExportSection(
                importExportViewModel = importExportViewModel,
                onImportKeystore = onImportKeystore,
                onExportKeystore = onExportKeystore,
                onImportSettings = onImportSettings,
                onExportSettings = onExportSettings,
                onExportDebugLogs = onExportDebugLogs
            )
        }

        // Files & Storage
        Box(
            modifier = if (onFilePickerScrollTarget != null) Modifier.onGloballyPositioned { coords ->
                onFilePickerScrollTarget(coords.boundsInParent().top.roundToInt())
            } else Modifier
        ) {
            FilesAndStorageSection(
                settingsViewModel = settingsViewModel,
                importExportViewModel = importExportViewModel,
                onFilePickerPositioned = onFilePickerPositioned
            )
        }

        // About
        SectionTitle(
            text = stringResource(R.string.settings_system_about),
            icon = Icons.Outlined.Info
        )

        SectionCard {
            AboutSection(
                onAboutClick = onAboutClick,
                onChangelogClick = onChangelogClick,
                onStartTour = onStartTour
            )
        }
    }
}
