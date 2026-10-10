/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.KeystoreInputFormat
import app.morphe.manager.util.enumByNameOrNull

/**
 * Keystore Credentials Dialog.
 * Allows entering alias and password for keystore import.
 */
@Composable
fun KeystoreCredentialsDialog(
    onDismiss: () -> Unit,
    initialFormat: KeystoreInputFormat = KeystoreInputFormat.KEYSTORE,
    onSubmit: (alias: String, keyPassword: String, storePassword: String, format: KeystoreInputFormat) -> Unit
) {
    var alias by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable(initialFormat) { mutableStateOf(initialFormat) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_import_keystore_dialog_title),
        description = stringResource(R.string.settings_system_import_keystore_dialog_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.import_),
                onPrimaryClick = { onSubmit(alias, pass, "", format) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Format selector
            val formatItems = remember {
                KeystoreInputFormat.entries.associate { it.displayName to it.name }
            }
            AppDialogDropdownTextField(
                value = format.name,
                onValueChange = { name ->
                    format = enumByNameOrNull<KeystoreInputFormat>(name) ?: format
                },
                dropdownItems = formatItems,
                allowCustomValue = false,
                label = { Text(stringResource(R.string.settings_system_import_keystore_dialog_format_field)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FolderZip,
                        contentDescription = null
                    )
                }
            )

            // Alias Input
            AppDialogTextField(
                value = alias,
                onValueChange = { alias = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_alias_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                showClearButton = true
            )

            // Password Input
            AppDialogTextField(
                value = pass,
                onValueChange = { pass = it },
                label = {
                    Text(stringResource(R.string.settings_system_import_keystore_dialog_password_field))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null
                    )
                },
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
    }
}
