/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.BottomActionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    showCopyLogsButton: Boolean = false,
    showLogsButton: Boolean = false,
    showInstallButton: Boolean = false,

    // Actions
    onCancelClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSaveClick: () -> Unit,
    onErrorClick: () -> Unit,
    onCopyLogsClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onInstallClick: () -> Unit = {},

    // State
    isSaving: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    // Tracks the brief "Copied!" feedback state on the copy button
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Install / Cancel / Logs button
        if (showInstallButton) {
            BottomActionButton(
                onClick = onInstallClick,
                icon = Icons.Outlined.InstallMobile,
                text = stringResource(R.string.install),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else if (showCancelButton) {
            BottomActionButton(
                onClick = onCancelClick,
                icon = Icons.Default.Close,
                text = stringResource(android.R.string.cancel),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else if (showLogsButton) {
            BottomActionButton(
                onClick = onLogsClick,
                icon = Icons.AutoMirrored.Outlined.Article,
                text = stringResource(R.string.logs),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else Spacer(Modifier.weight(1f))

        // Center: Home button
        if (showHomeButton && !showInstallButton) {
            BottomActionButton(
                onClick = onHomeClick,
                icon = Icons.Default.Home,
                text = stringResource(R.string.home),
                modifier = Modifier.weight(1f)
            )
        } else Spacer(Modifier.weight(1f))

        // Right: Save / Error / Copy logs button
        if (showCopyLogsButton) {
            BottomActionButton(
                onClick = {
                    onCopyLogsClick()
                    scope.launch {
                        copied = true
                        delay(2000)
                        copied = false
                    }
                },
                icon = Icons.Default.ContentCopy,
                text = if (copied) stringResource(android.R.string.copy) + "  ✓"
                else stringResource(android.R.string.copy),
                modifier = Modifier.weight(1f),
                containerColor = if (copied)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (copied)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else if (showSaveButton || showErrorButton) {
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
