/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository.ImportMode
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.isDarkBackground

/**
 * Two-choice dialog that asks the user how an import should be applied:
 * Replace overwrites current state to match the backup exactly (destructive);
 * Merge only adds missing entries and preserves everything that already exists.
 */
@Composable
fun ImportModeDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onDismiss: () -> Unit,
    onSelect: (ImportMode) -> Unit,
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(titleRes),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

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
    val textColor = LocalDialogTextColor.current
    val isDark = !textColor.isDarkBackground()
    val accent = if (isDestructive) {
        Color.Red.copy(alpha = if (isDark) 0.85f else 0.75f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderColor = accent.copy(alpha = if (isDark) 0.4f else 0.3f)
    val containerColor = accent.copy(alpha = if (isDark) 0.14f else 0.08f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }
    }
}
