/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.toast

/**
 * Bundle controls: three action buttons (Select All / Default / Deselect All).
 */
@Composable
internal fun BundlePatchControls(
    enabledCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onResetToDefault: () -> Unit,
    onRestoreSaved: () -> Unit,
    hasSavedSelection: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Shows a confirmation toast with [doneMessage] and then executes [action]
    fun withToast(doneMessage: String, action: () -> Unit): () -> Unit = {
        context.toast(doneMessage)
        action()
    }

    val selectAllLabel = stringResource(R.string.expert_mode_enable_all)
    val defaultLabel = stringResource(R.string.expert_mode_reset_to_default)
    val restoreLabel = stringResource(R.string.expert_mode_restore_saved)
    val deselectAllLabel = stringResource(R.string.expert_mode_disable_all)

    val enabledDone = stringResource(R.string.expert_mode_enable_all_done)
    val disabledDone = stringResource(R.string.expert_mode_disable_all_done)
    val resetDone = stringResource(R.string.expert_mode_reset_to_default_done)
    val restoredDone = stringResource(R.string.expert_mode_restore_saved_done)

    ActionPillRow(modifier = modifier) {
        ActionPillButton(
            onClick = withToast(enabledDone, onSelectAll),
            icon = Icons.Outlined.DoneAll,
            contentDescription = selectAllLabel,
            tooltip = selectAllLabel,
            enabled = enabledCount < totalCount,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(resetDone, onResetToDefault),
            icon = Icons.Outlined.Recommend,
            contentDescription = defaultLabel,
            tooltip = defaultLabel,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(restoredDone, onRestoreSaved),
            icon = Icons.Outlined.History,
            contentDescription = restoreLabel,
            tooltip = restoreLabel,
            enabled = hasSavedSelection,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(disabledDone, onDeselectAll),
            icon = Icons.Outlined.ClearAll,
            contentDescription = deselectAllLabel,
            tooltip = deselectAllLabel,
            enabled = enabledCount > 0,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
    }
}

/**
 * Individual patch card with toggle and options button.
 */
@Composable
internal fun PatchCard(
    patch: PatchInfo,
    isEnabled: Boolean,
    isNew: Boolean = false,
    hasRequiredOptionsMissing: Boolean = false,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean
) {
    // Localized strings for accessibility
    val settings = stringResource(R.string.settings)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val patchState = if (isEnabled) enabledState else disabledState
    val contentDesc = remember(patch.name, patchState) { "${patch.name}, $patchState" }

    val colors = MaterialTheme.colorScheme
    val showErrorBorder = hasRequiredOptionsMissing && isEnabled
    val containerColor = when {
        isNew && isEnabled -> colors.tertiaryContainer.copy(alpha = 0.55f)
        isNew -> colors.tertiaryContainer.copy(alpha = 0.25f)
        isEnabled -> colors.surfaceColorAtElevation(2.dp)
        else -> colors.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
    }

    SettingsItemCard(
        onClick = onToggle,
        color = containerColor,
        borderWidth = 1.dp,
        borderColor = when {
            showErrorBorder -> colors.error.copy(alpha = 0.6f)
            !isEnabled -> colors.outlineVariant.copy(alpha = 0.5f)
            else -> colors.outlineVariant
        },
        modifier = Modifier.semantics {
            stateDescription = patchState
            contentDescription = contentDesc
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Patch info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (hasOptions) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Name row: patch name + "New" badge inline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = patch.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled)
                            LocalDialogTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNew) {
                        InfoBadge(
                            text = stringResource(R.string.expert_mode_new_patches),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )
                    }
                }

                if (!patch.description.isNullOrBlank()) {
                    Text(
                        text = patch.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled)
                            LocalDialogSecondaryTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.4f)
                    )
                }
            }

            // Options button (only enabled if patch is enabled)
            if (hasOptions) {
                FilledTonalIconButton(
                    onClick = {
                        // Prevent click propagation to card
                        onConfigureOptions()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            contentDescription = "${patch.name}, $settings"
                        },
                    enabled = isEnabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (hasRequiredOptionsMissing && isEnabled)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = if (hasRequiredOptionsMissing && isEnabled)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
