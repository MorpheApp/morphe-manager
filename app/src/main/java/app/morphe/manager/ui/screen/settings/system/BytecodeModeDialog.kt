/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                .padding(vertical = MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            Text(
                text = stringResource(R.string.settings_advanced_bytecode_mode_dialog_description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
            )

            RadioSelectionCard(
                selected = current == BytecodeMode.STRIP_FAST,
                onSelect = { onSelect(BytecodeMode.STRIP_FAST) },
                title = stringResource(R.string.settings_advanced_bytecode_mode_strip_fast_label),
                description = stringResource(R.string.settings_advanced_bytecode_mode_strip_fast_description)
            )

            RadioSelectionCard(
                selected = current == BytecodeMode.FULL,
                onSelect = { onSelect(BytecodeMode.FULL) },
                title = stringResource(R.string.settings_advanced_bytecode_mode_full),
                description = stringResource(R.string.settings_advanced_bytecode_mode_full_description)
            )
        }
    }
}
