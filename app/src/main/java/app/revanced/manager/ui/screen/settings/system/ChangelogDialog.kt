package app.revanced.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.screen.home.ReleaseInfoSection
import app.revanced.manager.ui.screen.shared.*
import app.revanced.manager.ui.viewmodel.UpdateViewModel

/**
 * Changelog dialog
 * Displays the changelog for currently installed manager version
 */
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel
) {
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    // Load current version changelog when dialog opens
    LaunchedEffect(Unit) {
        updateViewModel.loadCurrentVersionChangelog()
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.changelog),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val releaseInfo = updateViewModel.currentVersionReleaseInfo

        if (releaseInfo == null) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.changelog_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor
                    )
                }
            }
        } else {
            // Reuse the same component from update dialog
            ReleaseInfoSection(
                releaseInfo = releaseInfo,
                textColor = textColor
            )
        }
    }
}
