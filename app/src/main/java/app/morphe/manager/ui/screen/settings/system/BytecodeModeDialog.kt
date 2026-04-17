/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.patcher.dex.BytecodeMode

/**
 * Dialog for selecting the bytecode processing mode.
 */
@Composable
fun BytecodeModeDialog(
    current: BytecodeMode,
    onDismiss: () -> Unit,
    onSelect: (BytecodeMode) -> Unit,
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_bytecode_mode),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_advanced_bytecode_mode_dialog_description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
            )

            BytecodeModeOption(
                titleRes = R.string.settings_advanced_bytecode_mode_strip_fast_label,
                subtitleRes = R.string.settings_advanced_bytecode_mode_strip_fast_description,
                isSelected = current == BytecodeMode.STRIP_FAST,
                onSelect = { onSelect(BytecodeMode.STRIP_FAST) },
            )

            BytecodeModeOption(
                titleRes = R.string.settings_advanced_bytecode_mode_full,
                subtitleRes = R.string.settings_advanced_bytecode_mode_full_description,
                isSelected = current == BytecodeMode.FULL,
                onSelect = { onSelect(BytecodeMode.FULL) },
            )
        }
    }
}

@Composable
private fun BytecodeModeOption(
    titleRes: Int,
    subtitleRes: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    RichSettingsItem(
        onClick = onSelect,
        showBorder = true,
        leadingContent = {
            MorpheIcon(
                icon = if (isSelected) Icons.Outlined.RadioButtonChecked
                else Icons.Outlined.RadioButtonUnchecked,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = null,
        title = stringResource(titleRes),
        subtitle = stringResource(subtitleRes),
    )
}
