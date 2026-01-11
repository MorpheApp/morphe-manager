package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.component.morphe.shared.SectionCard
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import app.revanced.manager.ui.viewmodel.ImportExportViewModel

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
    onAboutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Installers
        SectionTitle(stringResource(R.string.installer))

        SectionCard {
            InstallerSection(
                installerManager = installerManager,
                advancedViewModel = advancedViewModel,
                onShowInstallerDialog = onShowInstallerDialog
            )
        }

        // Import & Export
        SectionTitle(stringResource(R.string.import_export))

        SectionCard {
            ImportExportSection(
                importExportViewModel = importExportViewModel,
                onImportKeystore = onImportKeystore,
                onExportKeystore = onExportKeystore
            )
        }

        // About
        SectionTitle(stringResource(R.string.about))

        SectionCard {
            AboutSection(onAboutClick = onAboutClick)
        }
    }
}
