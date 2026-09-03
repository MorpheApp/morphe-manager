/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.BottomActionBar
import app.morphe.manager.ui.screen.shared.BottomActionButton
import app.morphe.manager.ui.screen.shared.BottomActionTone
import app.morphe.manager.ui.screen.shared.Defaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Patcher bottom action bar.
 * Left: Cancel Patching | Center: Home | Right: Save / Error button.
 *
 * Pass a zero [horizontalPadding] where the bar sits in a column that is inset already, so the
 * two insets do not stack and the buttons keep the edges of the content above them.
 */
@Composable
fun PatcherBottomActionBar(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Defaults.ContentPadding,

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
    isSaving: Boolean = false
) {
    // Tracks the brief "Copied!" feedback state on the copy button
    val copied = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Only the buttons the current state calls for are emitted, and each one takes an equal
    // share of the row, so two actions split it in half and the third slots in between them
    val leadingLabel = when {
        showInstallButton -> stringResource(R.string.install)
        showCancelButton -> stringResource(android.R.string.cancel)
        showLogsButton -> stringResource(R.string.logs)
        else -> ""
    }
    val homeLabel = if (showHomeButton && !showInstallButton) stringResource(R.string.home) else ""
    val trailingLabel = when {
        showCopyLogsButton -> stringResource(android.R.string.copy)
        showErrorButton -> stringResource(R.string.error_)
        showSaveButton -> stringResource(R.string.save)
        else -> ""
    }
    val labels = remember(leadingLabel, homeLabel, trailingLabel) {
        listOf(leadingLabel, homeLabel, trailingLabel).filter { it.isNotEmpty() }
    }

    BottomActionBar(modifier = modifier, labels = labels, horizontalPadding = horizontalPadding) {
        // Left: Install / Cancel / Logs button
        if (showInstallButton) {
            BottomActionButton(
                onClick = onInstallClick,
                icon = Icons.Outlined.InstallMobile,
                text = leadingLabel,
                showLabel = showLabels,
                tone = BottomActionTone.Accent
            )
        } else if (showCancelButton) {
            BottomActionButton(
                onClick = onCancelClick,
                icon = Icons.Default.Close,
                text = leadingLabel,
                showLabel = showLabels,
                tone = BottomActionTone.Destructive
            )
        } else if (showLogsButton) {
            BottomActionButton(
                onClick = onLogsClick,
                icon = Icons.AutoMirrored.Outlined.Article,
                text = leadingLabel,
                showLabel = showLabels
            )
        }

        // Center: Home button
        if (showHomeButton && !showInstallButton) {
            BottomActionButton(
                onClick = onHomeClick,
                icon = Icons.Default.Home,
                text = homeLabel,
                showLabel = showLabels
            )
        }

        // Right: Save / Error / Copy logs button
        if (showCopyLogsButton) {
            BottomActionButton(
                onClick = {
                    onCopyLogsClick()
                    scope.launch {
                        copied.value = true
                        delay(2.seconds)
                        copied.value = false
                    }
                },
                icon = Icons.Default.ContentCopy,
                text = trailingLabel,
                showLabel = showLabels,
                // The tone alone reports the copy, so the label keeps a stable width
                tone = if (copied.value) BottomActionTone.Highlight else BottomActionTone.Neutral
            )
        } else if (showSaveButton || showErrorButton) {
            BottomActionButton(
                onClick = if (showErrorButton) onErrorClick else onSaveClick,
                icon = if (showErrorButton) Icons.Default.Error else Icons.Outlined.Save,
                text = trailingLabel,
                showLabel = showLabels,
                tone = if (showErrorButton) BottomActionTone.Destructive else BottomActionTone.Neutral,
                enabled = !isSaving,
                showProgress = isSaving && !showErrorButton
            )
        }
    }
}
