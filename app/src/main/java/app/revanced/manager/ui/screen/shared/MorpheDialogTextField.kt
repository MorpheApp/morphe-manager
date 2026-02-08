package app.revanced.manager.ui.screen.shared

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Styled OutlinedTextField for dialogs with proper theming
 * Supports password visibility toggle and clear button
 */
@Composable
fun MorpheDialogTextField(
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
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
            if (isPassword || showClearButton || onFolderPickerClick != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Password visibility toggle
                    if (isPassword) {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
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
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Folder picker button
                    if (onFolderPickerClick != null) {
                        IconButton(onClick = onFolderPickerClick) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.patch_option_pick_folder),
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
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
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
    )
}
