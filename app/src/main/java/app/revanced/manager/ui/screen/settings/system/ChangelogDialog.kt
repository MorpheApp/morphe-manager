package app.revanced.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.ui.screen.home.ReleaseInfoSection
import app.revanced.manager.ui.screen.shared.*

/**
 * Changelog dialog
 * Displays the latest manager changelog using the same component as the update dialog
 */
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    releaseInfo: ReVancedAsset?
) {
    val textColor = LocalDialogTextColor.current

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
        if (releaseInfo == null) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
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
