package app.revanced.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.revanced.manager.ui.screen.shared.LocalDialogTextColor
import app.revanced.manager.ui.screen.shared.MorpheDialog
import app.revanced.manager.ui.screen.shared.MorpheDialogButtonRow
import app.revanced.manager.ui.screen.shared.PasswordField

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
        title = stringResource(R.string.import_keystore_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.import_keystore_dialog_button),
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
                text = stringResource(R.string.import_keystore_dialog_description),
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
                        stringResource(R.string.import_keystore_dialog_alias_field),
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
                        stringResource(R.string.import_keystore_dialog_password_field),
                        color = secondaryColor
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
