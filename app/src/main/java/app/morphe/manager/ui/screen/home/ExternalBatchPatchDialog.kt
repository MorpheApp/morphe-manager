/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.dialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.AppDialog
import app.morphe.manager.ui.screen.shared.AppDialogButtonRow
import app.morphe.manager.ui.screen.shared.SelectionCheckRow

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
        description = stringResource(R.string.external_batch_patch_description, caller),
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
                text = pluralStringResource(
                    R.plurals.batch_patch_ready_count,
                    packageCount,
                    packageCount.toString()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = dialogSecondaryTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (callerPackage != null) {
                SelectionCheckRow(
                    text = stringResource(R.string.external_batch_patch_trust_caller),
                    checked = trustCaller,
                    onCheckedChange = { trustCaller = it }
                )
            }
        }
    }
}
