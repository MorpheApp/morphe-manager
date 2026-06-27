/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.MANAGER_REPO_URL
import app.morphe.manager.util.releasePageUrl

/**
 * Changelog dialog.
 * Displays the changelog for the currently installed manager version, with an optional
 * "Show older releases" expander that lazy-loads the rest of the history below.
 */
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel
) {
    val textColor = LocalDialogTextColor.current
    val entry = updateViewModel.currentVersionChangelogEntry
    val installedVersion = BuildConfig.VERSION_NAME.removePrefix("v")

    LaunchedEffect(Unit) {
        updateViewModel.loadCurrentVersionChangelog()
    }
    DisposableEffect(Unit) {
        onDispose { updateViewModel.resetOlderManagerEntries() }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.changelog),
        scrollable = false,
        footer = {
            MorpheDialogButtonColumn {
                ChangelogButton(
                    pageUrl = entry?.version?.let { releasePageUrl(MANAGER_REPO_URL, it) },
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
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (entry == null) {
                item("changelog_loading") { ChangelogSectionLoading() }
            } else {
                item("changelog_installed_${entry.version}") {
                    ChangelogEntrySection(
                        entry = entry,
                        headerIcon = Icons.Outlined.NewReleases,
                        textColor = textColor
                    )
                }
                changelogOlderItems(
                    entries = updateViewModel.olderManagerEntries,
                    isLoading = updateViewModel.isLoadingOlderEntries,
                    onExpand = {
                        updateViewModel.loadOlderManagerEntries(exclude = setOf(installedVersion))
                    },
                    textColor = textColor
                )
            }
        }
    }

    MorpheOverlay(visible = updateViewModel.isLoadingOlderEntries) {
        PulsingLogoIndicator()
    }
}
