/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.morphe.manager.R

/**
 * Conflict dialog shown when the patched APK carries a different certificate than the installed app.
 *
 * @param onIgnore Retries the install without uninstalling first, keeping the app data. Pass null
 * unless the device can actually complete such an install, because the platform rejects it
 * wherever the signature verification is intact.
 */
@Composable
fun SignatureConflictDialog(
    title: String,
    message: String,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    onIgnore: (() -> Unit)? = null
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = message,
        footer = {
            if (onIgnore == null) {
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.uninstall),
                    onPrimaryClick = onUninstall,
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = onDismiss
                )
            } else {
                AppDialogActions(
                    actions = listOf(
                        DialogAction(
                            text = stringResource(R.string.uninstall),
                            onClick = onUninstall,
                            isDestructive = true
                        ),
                        DialogAction(
                            text = stringResource(R.string.install_ignore_signature),
                            onClick = onIgnore
                        ),
                        DialogAction(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss
                        )
                    ),
                    layout = DialogButtonLayout.Vertical
                )
            }
        }
    ) {
        if (onIgnore != null) {
            Spacer(Modifier.height(Defaults.ContentPadding))

            Text(
                text = stringResource(R.string.install_ignore_signature_description),
                style = MaterialTheme.typography.bodyMedium,
                color = dialogSecondaryTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
