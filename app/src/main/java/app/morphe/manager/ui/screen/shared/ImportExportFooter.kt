/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R

/**
 * Footer of a dialog that moves data in and out of Morphe. The transfer pair shares a row and
 * close keeps its own, so dismissing is never mistaken for one of the two.
 *
 * @param onExport Null while there is nothing to export, which leaves import on its own.
 * @param exportLeads Puts export in the filled, leading spot, for data whose backup matters more
 *        than its restore.
 * @param enabled False while the dialog has nothing picked to move either way.
 */
@Composable
fun ImportExportFooter(
    onImport: () -> Unit,
    onExport: (() -> Unit)?,
    onClose: () -> Unit,
    exportLeads: Boolean = false,
    enabled: Boolean = true
) {
    val import = DialogAction(
        text = stringResource(R.string.import_),
        onClick = onImport,
        icon = Icons.Outlined.Download,
        enabled = enabled
    )
    val export = onExport?.let {
        DialogAction(
            text = stringResource(R.string.export),
            onClick = it,
            icon = Icons.Outlined.Upload,
            enabled = enabled
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
        if (export != null) {
            AppDialogActions(
                actions = if (exportLeads) listOf(export, import) else listOf(import, export),
                layout = DialogButtonLayout.Horizontal
            )
        } else {
            AppDialogActions(actions = listOf(import), layout = DialogButtonLayout.Vertical)
        }
        AppDialogActions(
            actions = listOf(
                DialogAction(
                    text = stringResource(R.string.close),
                    onClick = onClose,
                    emphasis = DialogActionEmphasis.Outlined
                )
            ),
            layout = DialogButtonLayout.Vertical
        )
    }
}
