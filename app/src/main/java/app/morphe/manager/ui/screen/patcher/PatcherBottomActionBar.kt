package app.morphe.manager.ui.screen.patcher

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.BottomActionButton

/**
 * Patcher bottom action bar
 * Left: Cancel Patching | Center: Home | Right: Save / Error button
 */
@Composable
fun PatcherBottomActionBar(
    // Visibility control
    showCancelButton: Boolean = true,
    showHomeButton: Boolean = true,
    showSaveButton: Boolean = false,
    showErrorButton: Boolean = false,

    // Actions
    onCancelClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSaveClick: () -> Unit,
    onErrorClick: () -> Unit,

    // State
    isSaving: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Cancel button
        if (showCancelButton) {
            BottomActionButton(
                onClick = onCancelClick,
                icon = Icons.Default.Close,
                text = stringResource(android.R.string.cancel),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else Spacer(Modifier.weight(1f))

        // Center: Home button
        if (showHomeButton) {
            BottomActionButton(
                onClick = onHomeClick,
                icon = Icons.Default.Home,
                text = stringResource(R.string.home),
                modifier = Modifier.weight(1f)
            )
        } else Spacer(Modifier.weight(1f))

        // Right: Save / Error button
        if (showSaveButton || showErrorButton) {
            BottomActionButton(
                onClick = if (showErrorButton) onErrorClick else onSaveClick,
                icon = if (showErrorButton) Icons.Default.Error else Icons.Outlined.Save,
                text = if (showErrorButton) stringResource(R.string.error_)
                else stringResource(R.string.save),
                modifier = Modifier.weight(1f),
                containerColor = if (showErrorButton)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (showErrorButton)
                    MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                enabled = !isSaving,
                showProgress = isSaving && !showErrorButton
            )
        } else Spacer(Modifier.weight(1f))
    }
}
