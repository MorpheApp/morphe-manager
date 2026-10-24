/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

@Composable
private fun morpheDialogTextFieldColors(textColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textColor,
    unfocusedTextColor = textColor,
    disabledTextColor = textColor.copy(alpha = 0.6f),
    focusedBorderColor = textColor.copy(alpha = 0.5f),
    unfocusedBorderColor = textColor.copy(alpha = 0.2f),
    disabledBorderColor = textColor.copy(alpha = 0.1f),
    cursorColor = textColor,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLeadingIconColor = textColor.copy(alpha = 0.7f),
    unfocusedLeadingIconColor = textColor.copy(alpha = 0.5f),
    focusedTrailingIconColor = textColor.copy(alpha = 0.7f),
    unfocusedTrailingIconColor = textColor.copy(alpha = 0.5f),
    focusedLabelColor = textColor.copy(alpha = 0.7f),
    unfocusedLabelColor = textColor.copy(alpha = 0.5f),
    focusedPlaceholderColor = textColor.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = textColor.copy(alpha = 0.4f)
)

/**
 * Styled [OutlinedTextField] for dialogs with proper theming.
 * Supports password visibility toggle and clear button.
 */
@Composable
fun AppDialogTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    showClearButton: Boolean = false,
    onFolderPickerClick: (() -> Unit)? = null,
    onFilePickerClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    TextFieldLine {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (isPassword || showClearButton || onFolderPickerClick != null || onFilePickerClick != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                        modifier = Modifier.padding(end = Defaults.SpaceExtraSmall)
                    ) {
                        // Password visibility toggle
                        if (isPassword) {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        MaterialSymbols.Rounded.Visibility_off
                                    } else {
                                        MaterialSymbols.Rounded.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        stringResource(R.string.settings_system_hide_password_field)
                                    } else {
                                        stringResource(R.string.settings_system_show_password_field)
                                    }
                                )
                            }
                        }

                        // Clear button
                        if (showClearButton && value.isNotBlank()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }

                        // Folder picker button
                        if (onFolderPickerClick != null) {
                            IconButton(
                                onClick = onFolderPickerClick,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Folder_open,
                                    contentDescription = stringResource(R.string.select_folder)
                                )
                            }
                        }

                        // File picker button
                        if (onFilePickerClick != null) {
                            IconButton(
                                onClick = onFilePickerClick,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Draft.autoMirrored,
                                    contentDescription = stringResource(R.string.select_file)
                                )
                            }
                        }
                    }
                } else {
                    trailingIcon?.invoke()
                }
            },
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Defaults.CornerMedium),
            colors = morpheDialogTextFieldColors(dialogTextColor())
        )
    }
}

/**
 * Styled [OutlinedTextField] with dropdown menu support for dialogs.
 * Combines text input, folder picker, clear button, and dropdown selection.
 *
 * The field takes typed input and the arrow opens the presets, each announced on its own to a
 * screen reader. With [allowCustomValue] off the field is read-only and opens the presets itself.
 *
 * Until the field is focused it shows the name of the preset its value matches. Focusing it on a
 * preset starts an empty input, so a custom value replaces the preset rather than being typed
 * onto it, and an empty field shows the value in effect as its placeholder. A caller may turn an
 * empty value back into a default, so the typed text is kept by the field rather than read back
 * from [value], and clearing it leaves it empty.
 *
 * @param dropdownItems Map of display name to value shown in the dropdown menu.
 * @param allowCustomValue Whether a value besides [dropdownItems] can be typed in.
 */
@Composable
fun AppDialogDropdownTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    dropdownItems: Map<String, String>,
    allowCustomValue: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    showClearButton: Boolean = false,
    onFolderPickerClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var expanded by remember { mutableStateOf(false) }
    // Text typed while the field is focused, null otherwise
    var input by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val presetName = dropdownItems.entries.find { it.value == value }?.key
    val typed = input
    val displayValue = when {
        typed == null -> presetName ?: value
        // A cleared field the caller filled back with a preset stays cleared
        value == typed || (typed.isBlank() && presetName != null) -> typed
        // Set from outside while typing, such as by the folder picker
        else -> value
    }
    val inEffect = value.takeIf { it.isNotBlank() && it != displayValue } ?: presetName
    val changeInput: (String) -> Unit = { text ->
        if (input != null) input = text
        onValueChange(text)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        TextFieldLine {
            OutlinedTextField(
                value = displayValue,
                onValueChange = changeInput,
                readOnly = !allowCustomValue,
                label = label,
                placeholder = placeholder ?: inEffect?.let { text ->
                    { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                leadingIcon = leadingIcon,
                singleLine = singleLine,
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = Defaults.SpaceExtraSmall)
                    ) {
                        // Folder picker button
                        if (onFolderPickerClick != null) {
                            IconButton(
                                onClick = onFolderPickerClick,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Folder_open,
                                    contentDescription = stringResource(R.string.select_folder)
                                )
                            }
                        }

                        // Clear button, hidden while the field already shows nothing
                        if (showClearButton && displayValue.isNotBlank()) {
                            IconButton(
                                onClick = { changeInput("") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }

                        // Dropdown arrow. On an editable field it is the only anchor, so opening the
                        // presets leaves the field unfocused and the keyboard closed
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    if (allowCustomValue) {
                                        Modifier.menuAnchor(
                                            ExposedDropdownMenuAnchorType.SecondaryEditable,
                                            enabled
                                        )
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = if (expanded)
                                    MaterialSymbols.Rounded.Expand_less
                                else
                                    MaterialSymbols.Rounded.Expand_more,
                                contentDescription = null
                            )
                        }
                    }
                },
                enabled = enabled,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (allowCustomValue) {
                            Modifier.onFocusChanged { state ->
                                input = when {
                                    !state.isFocused -> null
                                    input != null -> input
                                    presetName != null -> ""
                                    else -> value
                                }
                            }
                        } else {
                            Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                        }
                    ),
                shape = RoundedCornerShape(Defaults.CornerMedium),
                colors = morpheDialogTextFieldColors(dialogTextColor())
            )
        }

        AppExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dropdownItems.forEach { (displayName, itemValue) ->
                AppDropdownMenuItem(
                    text = displayName,
                    selected = itemValue == value,
                    onClick = {
                        onValueChange(itemValue)
                        expanded = false
                        // A picked preset ends any typing, so the field shows its name again
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
