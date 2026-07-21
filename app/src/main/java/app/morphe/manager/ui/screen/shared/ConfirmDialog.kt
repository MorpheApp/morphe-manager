/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ConfirmDialog(
    title: String,
    message: AnnotatedString,
    primaryText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPrimaryDestructive: Boolean = true,
    secondaryText: String = stringResource(android.R.string.cancel)
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = primaryText,
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = isPrimaryDestructive,
                secondaryText = secondaryText,
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    primaryText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPrimaryDestructive: Boolean = true,
    secondaryText: String = stringResource(android.R.string.cancel)
) = ConfirmDialog(
    title = title,
    message = AnnotatedString(message),
    primaryText = primaryText,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    isPrimaryDestructive = isPrimaryDestructive,
    secondaryText = secondaryText
)
