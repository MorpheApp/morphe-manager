/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

/**
 * Dialog asking for one line of text, such as a name or a link, confirmed from its button or the
 * keyboard. What the text has to be is up to the caller, through [confirmEnabled] and [supporting].
 *
 * @param confirmText What confirming does, such as "Rename" or "Create".
 * @param supporting Shown under the field, such as why the text cannot be used.
 */
@Composable
fun DialogHostScope.TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supporting: (@Composable () -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val confirm = {
        if (confirmEnabled) {
            keyboardController?.hide()
            onConfirm()
        }
    }
    val dismiss = {
        keyboardController?.hide()
        onDismiss()
    }

    AppDialog(
        onDismissRequest = dismiss,
        title = title,
        description = description,
        footer = {
            AppDialogButtonRow(
                primaryText = confirmText,
                onPrimaryClick = confirm,
                primaryEnabled = confirmEnabled,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = dismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)) {
            AppDialogTextField(
                value = value,
                onValueChange = onValueChange,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                leadingIcon = { Icon(leadingIcon, contentDescription = null) },
                isError = isError,
                showClearButton = true,
                keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth()
            )
            supporting?.invoke()
        }
    }
}
