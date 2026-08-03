/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.AppDialog
import app.morphe.manager.ui.screen.shared.AppDialogButtonRow

/**
 * Confirmation for a batch patch run requested by another app.
 *
 * Patching ends in installing software, so an external trigger always stops here first.
 * The user can trust the calling app once, which stores it in the allowlist.
 */
@Composable
fun ExternalBatchPatchDialog(
    callerPackage: String?,
    packageCount: Int,
    onConfirm: (trustCaller: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var trustCaller by remember { mutableStateOf(false) }
    val caller = callerPackage ?: stringResource(R.string.external_batch_patch_unknown_caller)

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.external_batch_patch_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                primaryIcon = Icons.Outlined.Check,
                onPrimaryClick = { onConfirm(trustCaller && callerPackage != null) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.external_batch_patch_description, caller),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = pluralStringResource(
                    R.plurals.batch_patch_ready_count,
                    packageCount,
                    packageCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (callerPackage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = trustCaller,
                        onCheckedChange = { trustCaller = it }
                    )
                    Text(
                        text = stringResource(R.string.external_batch_patch_trust_caller),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            }
        }
    }
}
