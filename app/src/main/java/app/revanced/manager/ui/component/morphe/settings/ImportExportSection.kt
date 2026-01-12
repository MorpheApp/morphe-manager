package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheSettingsDivider
import app.revanced.manager.ui.component.morphe.shared.SettingsItemCard
import app.revanced.manager.ui.viewmodel.ImportExportViewModel
import app.revanced.manager.util.toast

/**
 * Import & Export section
 * Contains keystore import/export options
 */
@Composable
fun ImportExportSection(
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Keystore Import
        SettingsItemCard(
            onClick = onImportKeystore
        ) {
            IconTextRow(
                icon = Icons.Outlined.Key,
                title = stringResource(R.string.import_keystore),
                description = stringResource(R.string.import_keystore_description),
                modifier = Modifier.padding(16.dp),
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        MorpheSettingsDivider()

        // Keystore Export
        SettingsItemCard(
            onClick = {
                if (!importExportViewModel.canExport()) {
                    context.toast(context.getString(R.string.export_keystore_unavailable))
                } else {
                    onExportKeystore()
                }
            }
        ) {
            IconTextRow(
                icon = Icons.Outlined.Upload,
                title = stringResource(R.string.export_keystore),
                description = stringResource(R.string.export_keystore_description),
                modifier = Modifier.padding(16.dp),
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}
