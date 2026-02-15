package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

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
            MorpheDialogTextField(
                value = alias,
                onValueChange = { alias = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_alias_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f)
                    )
                },
                showClearButton = true
            )

            // Password Input
            MorpheDialogTextField(
                value = pass,
                onValueChange = { pass = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_password_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f)
                    )
                },
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
    }
}
