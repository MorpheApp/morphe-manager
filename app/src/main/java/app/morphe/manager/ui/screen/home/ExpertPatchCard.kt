/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.patcher.patch.blocksToggle
import app.morphe.manager.ui.screen.shared.*
import org.koin.compose.koinInject

/**
 * Bundle controls: pill row with per-bundle bulk actions.
 *
 * A null [onCopyFromBundle] drops that pill, for the callers where a selection has nowhere
 * to be copied from.
 */
@Composable
internal fun BundlePatchControls(
    enabledCount: Int,
    totalCount: Int,
    holdsUniversalPatches: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onResetToDefault: () -> Unit,
    onRestoreSaved: () -> Unit,
    onCopyFromBundle: (() -> Unit)?,
    hasSavedSelection: Boolean,
    modifier: Modifier = Modifier,
    /** True when this "Enable all" tap would also enable the universal patches. */
    warnOnUniversalAll: Boolean = false
) {
    val selectAllLabel = stringResource(R.string.expert_mode_enable_all)
    val defaultLabel = stringResource(R.string.expert_mode_reset_to_default)
    val restoreLabel = stringResource(R.string.expert_mode_restore_saved)
    val deselectAllLabel = stringResource(R.string.expert_mode_disable_all)

    // The second "Enable all" tap applies every universal patch at once, which is a common
    // cause of conflicts and failed patching. Confirmation makes that risk explicit.
    var showUniversalAllWarning by remember { mutableStateOf(false) }

    // Universal patches stay off until a second tap, so the first one must not claim otherwise
    val enabledDone = if (holdsUniversalPatches) {
        stringResource(R.string.expert_mode_enable_all_universal_pending)
    } else {
        stringResource(R.string.expert_mode_enable_all_done)
    }
    val disabledDone = stringResource(R.string.expert_mode_disable_all_done)
    val resetDone = stringResource(R.string.expert_mode_reset_to_default_done)
    val restoredDone = stringResource(R.string.expert_mode_restore_saved_done)

    // Played by hand rather than on tap, since the tap may only open the warning below
    val selectAllConfirmation = rememberPillConfirmationState()
    val selectAll = {
        onSelectAll()
        selectAllConfirmation.show(enabledDone)
    }

    ActionPillRow(modifier = modifier) {
        ActionPillButton(
            onClick = {
                if (warnOnUniversalAll) showUniversalAllWarning = true else selectAll()
            },
            icon = Icons.Outlined.DoneAll,
            contentDescription = selectAllLabel,
            tooltip = selectAllLabel,
            confirmationState = selectAllConfirmation,
            enabled = enabledCount < totalCount
        )
        ActionPillButton(
            onClick = onResetToDefault,
            icon = Icons.Outlined.Recommend,
            contentDescription = defaultLabel,
            tooltip = defaultLabel,
            confirmation = resetDone
        )
        ActionPillButton(
            onClick = onRestoreSaved,
            icon = Icons.Outlined.History,
            contentDescription = restoreLabel,
            tooltip = restoreLabel,
            confirmation = restoredDone,
            enabled = hasSavedSelection
        )
        if (onCopyFromBundle != null) {
            val copyLabel = stringResource(R.string.expert_mode_copy_from_bundle)
            ActionPillButton(
                onClick = onCopyFromBundle,
                icon = Icons.Outlined.ContentCopy,
                contentDescription = copyLabel,
                tooltip = copyLabel
            )
        }
        ActionPillButton(
            onClick = onDeselectAll,
            icon = Icons.Outlined.ClearAll,
            contentDescription = deselectAllLabel,
            tooltip = deselectAllLabel,
            confirmation = disabledDone,
            enabled = enabledCount > 0,
            destructive = true
        )
    }

    DialogHost(visible = showUniversalAllWarning) {
        ConfirmDialog(
            title = stringResource(R.string.expert_mode_universal_all_warning_title),
            message = stringResource(R.string.expert_mode_universal_all_warning_message),
            primaryText = stringResource(R.string.enable),
            isPrimaryDestructive = false,
            onDismiss = { showUniversalAllWarning = false },
            onConfirm = {
                showUniversalAllWarning = false
                selectAll()
            }
        )
    }
}

/**
 * Tonal button colors for this screen: only the tint carries meaning, the disabled pair is the
 * same washed-out surface everywhere.
 */
@Composable
private fun tonalIconColors(
    container: Color,
    content: Color,
    disabledContent: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
) = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = container,
    contentColor = content,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    disabledContentColor = disabledContent
)

/**
 * Individual patch card with toggle and options button.
 *
 * @param shape Its place among the rows of its block, see [ListSegment].
 */
@Composable
internal fun PatchCard(
    patch: PatchInfo,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    buildsClone: Boolean = false,
    hasRequiredOptionsMissing: Boolean = false,
    hasCustomOptions: Boolean = false,
    lockState: PatchLockState = PatchLockState.NONE,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean,
    shape: CornerBasedShape? = null
) {
    // Localized strings for accessibility
    val settings = stringResource(R.string.settings)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val patchState = if (isEnabled) enabledState else disabledState
    val newLabel = stringResource(R.string.expert_mode_new_patches)
    val cloneLabel = stringResource(R.string.clone)
    val customizedLabel = stringResource(R.string.expert_mode_options_customized)
    // The card speaks for its whole contents, so the badges have to be read out here or not at all
    val badges = listOfNotNull(newLabel.takeIf { isNew }, cloneLabel.takeIf { buildsClone })
    val contentDesc = remember(patch.displayName, patchState, badges) {
        (listOf(patch.displayName, patchState) + badges).joinToString(", ")
    }

    val messages = koinInject<UserMessages>()
    val lockedMessage = when (lockState) {
        PatchLockState.LOCKED_ON  -> stringResource(R.string.expert_mode_patch_required_by_installer)
        PatchLockState.LOCKED_OFF -> stringResource(R.string.expert_mode_patch_unavailable_for_installer)
        PatchLockState.NONE       -> null
    }
    val onCardClick: () -> Unit = if (lockState.blocksToggle(isEnabled) && lockedMessage != null) {
        { messages.show(lockedMessage) }
    } else onToggle

    val colors = MaterialTheme.colorScheme
    val customizedAccent = LocalAccent.current
    val showMissingRequired = hasRequiredOptionsMissing && isEnabled
    // A missing required option blocks the run, so it outranks the tint that only reports
    // that the defaults were changed
    val showCustomOptions = hasCustomOptions && isEnabled && !showMissingRequired
    val optionsDesc = remember(patch.displayName, settings, customizedLabel, showCustomOptions) {
        listOfNotNull(patch.displayName, settings, customizedLabel.takeIf { showCustomOptions })
            .joinToString(", ")
    }
    val containerColor = when {
        isNew && isEnabled -> colors.tertiaryContainer.copy(alpha = 0.55f)
        isNew -> colors.tertiaryContainer.copy(alpha = 0.25f)
        isEnabled -> colors.surfaceColorAtElevation(2.dp)
        else -> colors.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
    }

    SettingsItemCard(
        onClick = onCardClick,
        color = containerColor,
        shape = shape,
        showBorder = true,
        borderColor = when {
            showMissingRequired -> colors.error.copy(alpha = 0.6f)
            !isEnabled -> colors.outlineVariant.copy(alpha = 0.5f)
            else -> colors.outlineVariant
        },
        modifier = modifier.semantics {
            stateDescription = patchState
            contentDescription = contentDesc
        }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceLarge),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dimming alone leaves the state easy to miss. The card is what toggles, and it
                // already reads the state out, so the indicator only shows it
                SelectionCheckIndicator(
                    state = if (isEnabled) ToggleableState.On else ToggleableState.Off,
                    enabled = !lockState.blocksToggle(isEnabled),
                    modifier = Modifier.padding(end = Defaults.SpaceMedium)
                )

                // Patch info, with the "New" badge inline
                PatchCardText(
                    name = patch.displayName,
                    description = patch.description,
                    dimmed = !isEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (hasOptions) 8.dp else 0.dp)
                ) {
                    if (isNew) {
                        StatusBadge(
                            text = newLabel,
                            tone = SemanticTone.Primary
                        )
                    }
                    // Read from how the patch declares its option, so it warns early without
                    // being relied on: the run itself is checked against the APK it produced
                    if (buildsClone) {
                        StatusBadge(
                            text = cloneLabel,
                            icon = Icons.Outlined.ContentCopy,
                            tone = SemanticTone.Warning
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
                                contentDescription = optionsDesc
                            },
                        enabled = isEnabled,
                        colors = tonalIconColors(
                            // Changed options take the app's color where the list has one, as its
                            // other controls do, while missing ones stay in the error palette
                            container = when {
                                showMissingRequired -> colors.errorContainer.copy(alpha = 0.8f)
                                showCustomOptions -> customizedAccent?.copy(alpha = AccentAlpha.LEAD)
                                    ?: colors.primaryContainer.copy(alpha = 0.7f)
                                else -> colors.secondaryContainer.copy(alpha = 0.6f)
                            },
                            content = when {
                                showMissingRequired -> colors.error
                                showCustomOptions -> if (customizedAccent != null) {
                                    colors.onBackground
                                } else {
                                    colors.onPrimaryContainer
                                }
                                else -> colors.onSecondaryContainer
                            },
                            disabledContent = colors.onSurfaceVariant.copy(alpha = 0.3f)
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

            if (lockedMessage != null) {
                SettingsDivider(fullWidth = true)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.onSurface.copy(alpha = 0.06f))
                        .padding(
                            horizontal = Defaults.SpaceLarge,
                            vertical = Defaults.SpaceSmall,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                    ) {
                        Icon(
                            imageVector = when (lockState) {
                                PatchLockState.LOCKED_ON  -> Icons.Outlined.Lock
                                PatchLockState.LOCKED_OFF -> Icons.Outlined.Block
                                PatchLockState.NONE       -> Icons.Outlined.Lock
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.onSurfaceVariant,
                        )
                        Text(
                            text = lockedMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
