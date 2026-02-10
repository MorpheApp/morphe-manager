package app.morphe.manager.ui.screen.patcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.MorpheDialog
import app.morphe.manager.ui.screen.shared.MorpheDialogButtonRow

/**
 * Cancel patching confirmation dialog
 * Warns user about stopping patching process
 */
@Composable
fun CancelPatchingDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_stop_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.yes),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(R.string.no),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(R.string.patcher_stop_confirm_description),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
