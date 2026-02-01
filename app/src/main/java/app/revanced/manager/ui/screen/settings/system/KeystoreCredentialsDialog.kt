package app.revanced.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.revanced.manager.ui.screen.shared.LocalDialogTextColor
import app.revanced.manager.ui.screen.shared.MorpheDialog
import app.revanced.manager.ui.screen.shared.MorpheDialogButtonRow

/**
 * Keystore Credentials Dialog
 * Allows entering alias and password for keystore import
 */
@Composable
fun KeystoreCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var alias by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_import_keystore_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.settings_system_import_keystore_dialog_button),
                onPrimaryClick = { onSubmit(alias, pass) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_system_import_keystore_dialog_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            // Alias Input
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = {
                    Text(
                        stringResource(R.string.settings_system_import_keystore_dialog_alias_field),
                        color = secondaryColor
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = textColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                    cursorColor = textColor
                )
            )

            // Password Input
            PasswordField(
                value = pass,
                onValueChange = { pass = it },
                label = {
                    Text(
                        stringResource(R.string.settings_system_import_keystore_dialog_password_field),
                        color = secondaryColor
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PasswordField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null
) {
    var visible by rememberSaveable {
        mutableStateOf(false)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        modifier = modifier,
        trailingIcon = {
            IconButton(onClick = {
                visible = !visible
            }) {
                val (icon, description) = remember(visible) {
                    if (visible) Icons.Outlined.VisibilityOff to R.string.settings_system_hide_password_field
                    else Icons.Outlined.Visibility to R.string.settings_system_show_password_field
                }
                Icon(icon, stringResource(description))
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    )
}
