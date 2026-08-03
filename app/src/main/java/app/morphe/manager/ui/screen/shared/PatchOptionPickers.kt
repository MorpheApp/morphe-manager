/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Header row shown above a picker button (folder/file/image options).
 * Renders the option title, an optional "*" marker for required options,
 * and switches to the theme's error color when the option is required but empty.
 */
@Composable
fun PickerFieldHeader(title: String, required: Boolean, isInvalid: Boolean) {
    Text(
        text = if (required) "$title *" else title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (isInvalid) MaterialTheme.colorScheme.error else LocalDialogTextColor.current,
    )
}

/**
 * Picker row: the main "select…" outlined button plus an inline trailing
 * [Icons.Outlined.Clear] icon button. The clear button is only rendered when
 * [selectedPath] is not blank.
 */
@Composable
fun PickerButtonRow(
    label: String,
    selectedPath: String,
    icon: ImageVector,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDialogOutlinedButton(
            text = label,
            textSuffix = selectedPath.takeIf { it.isNotBlank() },
            icon = icon,
            onClick = onPick,
            modifier = Modifier.weight(1f),
        )

        if (selectedPath.isNotBlank()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.clear),
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}
