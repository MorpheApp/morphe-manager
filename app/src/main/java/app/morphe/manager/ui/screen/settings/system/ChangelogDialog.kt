/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.util.MANAGER_REPO_URL
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.UpdateViewModel

/**
 * Changelog dialog.
 * Displays the changelog for currently installed manager version.
 */
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel
) {
    val textColor = LocalDialogTextColor.current
    val entry = updateViewModel.currentVersionChangelogEntry

    // Load current version changelog when dialog opens
    LaunchedEffect(Unit) {
        updateViewModel.loadCurrentVersionChangelog()
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.changelog),
        footer = {
            MorpheDialogButtonColumn {
                ChangelogButton(
                    pageUrl = entry?.version?.let {
                        val tag = if (it.startsWith("v")) it else "v$it"
                        "$MANAGER_REPO_URL/releases/tag/$tag"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                MorpheDialogButton(
                    text = stringResource(android.R.string.ok),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        if (entry == null) {
            ChangelogSectionLoading()
        } else {
            ChangelogEntrySection(
                entry = entry,
                headerIcon = Icons.Outlined.NewReleases,
                textColor = textColor
            )
        }
    }
}
