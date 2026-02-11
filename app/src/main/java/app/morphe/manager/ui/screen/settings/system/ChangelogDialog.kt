package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.ReleaseInfoSection
import app.morphe.manager.ui.screen.shared.LocalDialogTextColor
import app.morphe.manager.ui.screen.shared.MorpheDialog
import app.morphe.manager.ui.screen.shared.MorpheDialogOutlinedButton
import app.morphe.manager.ui.screen.shared.ShimmerChangelog
import app.morphe.manager.ui.viewmodel.UpdateViewModel

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
            // Shimmer loading state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show shimmer changelog item
                ShimmerChangelog()
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
