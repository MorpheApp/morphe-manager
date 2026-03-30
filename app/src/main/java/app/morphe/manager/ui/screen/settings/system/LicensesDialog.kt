/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.LocalDialogTextColor
import app.morphe.manager.ui.screen.shared.MorpheDialog
import app.morphe.manager.ui.screen.shared.MorpheDialogOutlinedButton
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors

/**
 * Licenses dialog.
 * Shows open-source library licenses via aboutlibraries.
 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    MorpheDialog(
        title = stringResource(R.string.opensource_licenses),
        onDismissRequest = onDismiss,
        scrollable = false, // LibrariesContainer has its own LazyColumn
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        compactPadding = true
    ) {
        val textColor = LocalDialogTextColor.current

        // Libraries list
        val lazyListState = rememberLazyListState()
        val libraries by produceLibraries(R.raw.aboutlibraries)

        val chipColors = LibraryDefaults.chipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        Box(modifier = Modifier.weight(1f)) {
            LibrariesContainer(
                modifier = Modifier.fillMaxSize(),
                libraries = libraries,
                lazyListState = lazyListState,
                colors = LibraryDefaults.libraryColors(
                    libraryBackgroundColor = MaterialTheme.colorScheme.background,
                    libraryContentColor = textColor,
                    versionChipColors = chipColors,
                    licenseChipColors = chipColors,
                    fundingChipColors = chipColors,
                )
            )
        }
    }
}
