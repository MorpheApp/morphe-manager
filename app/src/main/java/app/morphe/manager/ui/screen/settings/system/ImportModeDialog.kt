/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository.ImportMode
import app.morphe.manager.ui.screen.shared.*

/**
 * Two-choice dialog that asks the user how an import should be applied:
 * Replace overwrites current state to match the backup exactly (destructive);
 * Merge only adds missing entries and preserves everything that already exists.
 */
@Composable
fun DialogHostScope.ImportModeDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onDismiss: () -> Unit,
    onSelect: (ImportMode) -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(titleRes),
        description = stringResource(descriptionRes),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)) {
            ImportModeOption(
                icon = Icons.Outlined.SwapVert,
                title = stringResource(R.string.import_mode_replace_title),
                description = stringResource(R.string.import_mode_replace_description),
                isDestructive = true,
                onClick = { onSelect(ImportMode.Replace) }
            )

            ImportModeOption(
                icon = Icons.AutoMirrored.Outlined.MergeType,
                title = stringResource(R.string.import_mode_merge_title),
                description = stringResource(R.string.import_mode_merge_description),
                isDestructive = false,
                onClick = { onSelect(ImportMode.Merge) }
            )
        }
    }
}

@Composable
private fun ImportModeOption(
    icon: ImageVector,
    title: String,
    description: String,
    isDestructive: Boolean,
    onClick: () -> Unit,
) {
    val destructiveColor = dialogDestructiveColor()

    SettingsItemCard(
        onClick = onClick,
        showBorder = true,
        borderColor = if (isDestructive) {
            destructiveColor.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        IconTextRow(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            leadingContent = {
                ThemedIcon(
                    icon = icon,
                    tint = if (isDestructive) destructiveColor else MaterialTheme.colorScheme.primary
                )
            },
            title = title,
            description = description,
            titleColor = if (isDestructive) destructiveColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
