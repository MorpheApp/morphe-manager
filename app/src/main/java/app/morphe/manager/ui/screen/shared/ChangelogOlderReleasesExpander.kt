/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.util.ChangelogEntry

/**
 * Adds a "Show older releases" section to a [LazyListScope].
 *
 * Emits items into the caller's `LazyColumn` so each entry composes only when scrolled
 * into view. Markdown bodies render via their built-in async loading state, so the main
 * thread is never blocked parsing many entries up front.
 */
fun LazyListScope.changelogOlderItems(
    entries: List<ChangelogEntry>?,
    isLoading: Boolean,
    onExpand: () -> Unit,
    textColor: Color,
) {
    item("changelog_older_divider") {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = MorpheDefaults.ContentPaddingSmall,
                    bottom = MorpheDefaults.ContentPadding
                ),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }

    when {
        entries == null -> item("changelog_older_button") {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.changelog_show_older),
                onClick = onExpand,
                icon = Icons.Outlined.History,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        entries.isEmpty() -> item("changelog_older_empty") {
            Text(
                text = stringResource(R.string.changelog_older_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        else -> itemsIndexed(
            items = entries,
            key = { index, _ -> "changelog_older_$index" }
        ) { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        top = MorpheDefaults.ContentPaddingSmall,
                        bottom = MorpheDefaults.ContentPadding
                    ),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            ChangelogEntrySection(
                entry = entry,
                headerIcon = Icons.Outlined.History,
                textColor = textColor
            )
        }
    }
}
