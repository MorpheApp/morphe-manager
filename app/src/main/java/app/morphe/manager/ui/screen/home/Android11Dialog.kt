package app.morphe.manager.ui.screen.home

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
 * Dialog shown on Android 11+ when install apps permission is needed
 */
@Composable
fun Android11Dialog(
    onDismissRequest: () -> Unit,
    onContinue: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.android_11_bug_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                onPrimaryClick = onContinue,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismissRequest
            )
        }
    ) {
        Text(
            text = stringResource(R.string.android_11_bug_dialog_description),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
