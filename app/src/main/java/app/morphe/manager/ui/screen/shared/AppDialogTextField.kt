/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

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
    val textColor = LocalDialogTextColor.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (isPassword || showClearButton || onFolderPickerClick != null || onFilePickerClick != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    // Password visibility toggle
                    if (isPassword) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.settings_system_hide_password_field)
                                } else {
                                    stringResource(R.string.settings_system_show_password_field)
                                },
                                tint = textColor.copy(alpha = 0.7f)
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
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = textColor.copy(alpha = 0.7f)
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
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.select_folder),
                                tint = textColor.copy(alpha = 0.7f)
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
                                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                contentDescription = stringResource(R.string.select_file),
                                tint = textColor.copy(alpha = 0.7f)
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
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
        colors = morpheDialogTextFieldColors(textColor)
    )
}

/**
 * Search field for dialog lists, backed by an opaque surface so list items
 * scrolling underneath a sticky header stay hidden.
 *
 * @param requestFocus Opens the keyboard on first composition, for fields revealed by a toggle.
 */
@Composable
fun AppDialogSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = {
                // The label already announces the field, so the icon stays decorative
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            showClearButton = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .focusRequester(focusRequester)
        )
    }
}

/**
 * Collapsible [AppDialogSearchTextField] for the sticky header of a dialog list.
 *
 * Kept as its own composable so no layout scope is in scope at the [AnimatedVisibility] call:
 * inside `stickyHeader` the innermost receiver is `LazyItemScope`, and an enclosing `ColumnScope`
 * would otherwise pull in the scoped overload, which cannot be called there.
 *
 * The field never asks the list to scroll it into view. It sits at the top, stuck to the list or
 * above it, so it is always in view, yet a list places a sticky row by its spot at its head: a
 * field taking focus deep into the list would scroll the list all the way back up to it.
 *
 * @param modifier Applied to the field, so it comes and goes along with it.
 */
@Composable
fun AppDialogSearchHeader(
    visible: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        AppDialogSearchTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            requestFocus = true,
            modifier = modifier.then(StayInPlaceElement)
        )
    }
}

/** Takes in the requests to bring what it wraps into view, and passes none of them on. */
private data object StayInPlaceElement : ModifierNodeElement<StayInPlaceNode>() {
    override fun create() = StayInPlaceNode()
    override fun update(node: StayInPlaceNode) = Unit
}

private class StayInPlaceNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = Unit
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
    val textColor = LocalDialogTextColor.current

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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    // Folder picker button
                    if (onFolderPickerClick != null) {
                        IconButton(
                            onClick = onFolderPickerClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.select_folder),
                                tint = textColor.copy(alpha = 0.7f)
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
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = textColor.copy(alpha = 0.7f)
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
                                Icons.Outlined.ExpandLess
                            else
                                Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.7f)
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
            shape = RoundedCornerShape(Defaults.CompactCornerRadius),
            colors = morpheDialogTextFieldColors(textColor)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dropdownItems.forEach { (displayName, itemValue) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onValueChange(itemValue)
                        expanded = false
                        // A picked preset ends any typing, so the field shows its name again
                        focusManager.clearFocus()
                    },
                    leadingIcon = if (itemValue == value) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}
