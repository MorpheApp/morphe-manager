/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
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
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.compose.koinInject

/**
 * Buttons turning every patch in view on and off, for the source the selection panel is headed
 * with. Each stays where it is and does the same thing, out of reach while there is nothing for it.
 *
 * Turning all on is staged as a run sets it up: the regular patches first, the universal ones on
 * the next tap and after a warning, as enabling every one of them is a common cause of conflicts.
 *
 * @param canEnableMore Whether a patch in view is off and free to be turned on.
 * @param canDisable Whether a patch in view is on and free to be turned off.
 * @param holdsUniversalPatches Whether turning all on leaves the universal patches for the next tap.
 * @param warnOnUniversalAll Whether this tap would turn the universal patches on.
 */
@Composable
internal fun BulkSelectActions(
    canEnableMore: Boolean,
    canDisable: Boolean,
    holdsUniversalPatches: Boolean,
    warnOnUniversalAll: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val messages = koinInject<UserMessages>()
    val pendingUniversal = stringResource(R.string.expert_mode_enable_all_universal_pending)
    var showUniversalAllWarning by remember { mutableStateOf(false) }

    val selectAll = {
        onSelectAll()
        // Nothing on screen may change with this tap, so it says what the next one does
        if (holdsUniversalPatches) messages.show(pendingUniversal)
    }

    TitleAction(
        icon = MaterialSymbols.Rounded.Remove_done,
        contentDescription = stringResource(R.string.disable_all),
        onClick = onDeselectAll,
        style = TitleActionStyle.Neutral,
        enabled = canDisable
    )
    TitleAction(
        icon = MaterialSymbols.Rounded.Done_all,
        contentDescription = stringResource(R.string.enable_all),
        onClick = { if (warnOnUniversalAll) showUniversalAllWarning = true else selectAll() },
        style = TitleActionStyle.Neutral,
        enabled = canEnableMore
    )

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
                    state = ToggleableState(isEnabled),
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
                            icon = MaterialSymbols.Rounded.Content_copy,
                            tone = SemanticTone.Warning
                        )
                    }
                }

                // Options button (only enabled if patch is enabled)
                if (hasOptions) {
                    // The card already names the patch, so the tooltip only says what the button opens
                    IconTooltip(text = settings) {
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
                                imageVector = MaterialSymbols.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                                PatchLockState.LOCKED_ON  -> MaterialSymbols.Rounded.Lock
                                PatchLockState.LOCKED_OFF -> MaterialSymbols.Rounded.Block
                                PatchLockState.NONE       -> MaterialSymbols.Rounded.Lock
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
