/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Keystore Credentials Dialog.
 * Allows entering alias and password for keystore import.
 */
@Composable
fun DialogHostScope.KeystoreCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (alias: String, keyPassword: String) -> Unit
) {
    val alias = rememberSaveable { mutableStateOf("") }
    val pass = rememberSaveable { mutableStateOf("") }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_import_keystore_dialog_title),
        description = stringResource(R.string.settings_system_import_keystore_dialog_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.import_),
                onPrimaryClick = { onSubmit(alias.value, pass.value) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            // Alias Input
            AppDialogTextField(
                value = alias.value,
                onValueChange = { alias.value = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_alias_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Person,
                        contentDescription = null
                    )
                },
                showClearButton = true
            )

            // Password Input
            AppDialogTextField(
                value = pass.value,
                onValueChange = { pass.value = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_password_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Key,
                        contentDescription = null
                    )
                },
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
    }
}
