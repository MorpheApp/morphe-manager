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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

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
                    tint = dialogTextColor().copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Dropdown for an option that declares a set of values. Used both while patching and by the
 * simple mode option dialogs, so an option is offered the same way wherever it is edited.
 *
 * A value the option declares as null stands for "let the patch decide", so picking it clears
 * the stored value instead of writing a blank the patch would reject as invalid.
 *
 * @param allowCustomValue Whether a value besides [presets] can be typed in.
 */
@Composable
fun DropdownOptionField(
    value: String,
    presets: Map<String, Any?>,
    allowCustomValue: Boolean = true,
    onValueChange: (Any?) -> Unit
) {
    // Convert presets to String map for dropdown: display name -> value as string
    val dropdownItems = presets.mapValues { it.value?.toString().orEmpty() }

    AppDialogDropdownTextField(
        value = value,
        onValueChange = { newValue ->
            // The dropdown hands back what is rendered, so the declared value is matched
            // the same way. Anything else is text the user typed in the field
            val declared = presets.entries.find { it.value?.toString().orEmpty() == newValue }

            onValueChange(
                if (declared != null) declared.value else newValue.takeIf { it.isNotBlank() }
            )
        },
        dropdownItems = dropdownItems,
        allowCustomValue = allowCustomValue
    )
}
