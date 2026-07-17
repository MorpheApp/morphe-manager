/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/**
 * Installer section.
 */
@Composable
fun InstallerSection(
    settingsViewModel: SettingsViewModel,
    onShowInstallerDialog: () -> Unit,
    onInstallerItemPositioned: ((Rect) -> Unit)? = null
) {
    val primaryPreference by settingsViewModel.prefs.installerPrimary.getAsState()
    val primaryToken = remember(primaryPreference) {
        settingsViewModel.parseInstallerToken(primaryPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER

    // Installer entries with periodic updates
    val primaryEntries = remember(primaryToken) {
        mutableStateOf(settingsViewModel.getInstallerEntries(installTarget, primaryToken))
    }

    // Periodically update installer list
    LaunchedEffect(installTarget, primaryToken) {
        while (isActive) {
            primaryEntries.value = settingsViewModel.getInstallerEntries(installTarget, primaryToken)
            delay(1.5.seconds)
        }
    }

    // Get current entry
    val primaryEntry = primaryEntries.value.find { it.token == primaryToken }
        ?: settingsViewModel.describeInstallerEntry(primaryToken, installTarget)
        ?: primaryEntries.value.firstOrNull()

    if (primaryEntry != null) {
        Box(
            modifier = if (onInstallerItemPositioned != null)
                Modifier.onGloballyPositioned { coords -> onInstallerItemPositioned(coords.boundsInWindow()) }
            else Modifier
        ) {
            InstallerSettingsItem(
                title = stringResource(R.string.installer_title),
                entry = primaryEntry,
                onClick = onShowInstallerDialog
            )
        }
    }
}

/**
 * Container for installer selection dialog.
 */
@Composable
fun InstallerSelectionDialogContainer(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val primaryPreference by settingsViewModel.prefs.installerPrimary.getAsState()
    val primaryToken = remember(primaryPreference) {
        settingsViewModel.parseInstallerToken(primaryPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER
    val options = remember(primaryToken) {
        settingsViewModel.getInstallerEntries(installTarget, primaryToken)
    }

    val autoInstallEnabled by settingsViewModel.prefs.autoInstallWithShizuku.getAsState()
    val promptEnabled by settingsViewModel.prefs.promptInstallerOnInstall.getAsState()

    InstallerSelectionDialog(
        title = stringResource(R.string.installer_title),
        options = options,
        selected = primaryToken,
        onDismiss = onDismiss,
        onConfirm = { selection ->
            settingsViewModel.confirmInstallerSelection(selection)
            onDismiss()
        },
        onOpenShizuku = settingsViewModel::openShizukuApp,
        autoInstallEnabled = autoInstallEnabled,
        onAutoInstallToggle = settingsViewModel::setAutoInstallWithShizuku,
        installerPromptEnabled = promptEnabled,
        onInstallerPromptToggle = settingsViewModel::setPromptInstallerOnInstall
    )
}

/**
 * Installer settings item.
 */
@Composable
private fun InstallerSettingsItem(
    title: String,
    entry: InstallerManager.Entry,
    onClick: () -> Unit
) {
    val availabilityReasonText = entry.availability.reason?.let { stringResource(it) }

    // Build supporting text from description and availability reason
    val supportingText = remember(entry, availabilityReasonText) {
        buildList {
            entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            availabilityReasonText?.let { add(it) }
        }.joinToString("\n")
    }

    SettingsItem(
        onClick = onClick,
        leadingContent = {
            if (entry.icon != null &&
                (entry.token == InstallerManager.Token.Shizuku ||
                        entry.token == InstallerManager.Token.ShizukuPlayStore ||
                        entry.token == InstallerManager.Token.PlayStore ||
                        entry.token == InstallerManager.Token.RootPlayStore ||
                        entry.token is InstallerManager.Token.Component)
            ) {
                Image(
                    painter = rememberDrawablePainter(drawable = entry.icon),
                    contentDescription = null,
                    modifier = Modifier.size(MorpheDefaults.IconSize),
                    alpha = if (entry.availability.available) 1f else 0.4f
                )
            } else {
                MorpheIcon(icon = Icons.Outlined.Android)
            }
        },
        title = title,
        subtitle = supportingText.takeIf { it.isNotEmpty() }
    )
}

/**
 * Dialog for selecting installer.
 */
@Composable
fun InstallerSelectionDialog(
    title: String,
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)?,
    autoInstallEnabled: Boolean = false,
    onAutoInstallToggle: ((Boolean) -> Unit)? = null,
    installerPromptEnabled: Boolean = false,
    onInstallerPromptToggle: ((Boolean) -> Unit)? = null
) {
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    val currentSelection = remember(selected) { mutableStateOf(selected) }

    // Ensure valid selection when options change
    LaunchedEffect(options, selected) {
        val tokens = options.map { it.token }
        if (currentSelection.value !in tokens) {
            currentSelection.value = options.firstOrNull { it.availability.available }?.token
                ?: tokens.firstOrNull()
                        ?: selected
        }
    }

    val confirmEnabled = options.find { it.token == currentSelection.value }?.availability?.available != false

    // Warn once when the user is switching TO a Play Store variant they didn't have before
    var pendingPlayStoreConfirm by remember { mutableStateOf<InstallerManager.Token?>(null) }

    // Localized strings for accessibility
    val selectedState = stringResource(R.string.selected)
    val notSelectedState = stringResource(R.string.not_selected)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.confirm),
                onPrimaryClick = {
                    val token = currentSelection.value
                    val isPlayStoreVariant = token == InstallerManager.Token.PlayStore ||
                            token == InstallerManager.Token.RootPlayStore ||
                            token == InstallerManager.Token.ShizukuPlayStore
                    if (isPlayStoreVariant && token != selected) {
                        pendingPlayStoreConfirm = token
                    } else {
                        onConfirm(token)
                    }
                },
                primaryEnabled = confirmEnabled,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        compactPadding = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val enabled = option.availability.available
                val isSelected = currentSelection.value == option.token
                val isShizukuOption = option.token == InstallerManager.Token.Shizuku ||
                        option.token == InstallerManager.Token.ShizukuPlayStore
                val showShizukuAction = isShizukuOption &&
                        option.availability.reason in shizukuPromptReasons &&
                        onOpenShizuku != null

                // Build state description for accessibility
                val stateDesc = buildString {
                    append(if (isSelected) selectedState else notSelectedState)
                    if (!enabled) {
                        append(", ")
                        append(disabledState)
                    }
                }

                InstallerOptionItem(
                    option = option,
                    selected = isSelected,
                    enabled = enabled,
                    onSelect = { if (enabled) currentSelection.value = option.token },
                    stateDescription = stateDesc
                )

                if (showShizukuAction) {
                    TextButton(
                        onClick = { runCatching { onOpenShizuku.invoke() } },
                        modifier = Modifier.padding(start = 56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.installer_action_open_shizuku),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            MorpheSettingsDivider(fullWidth = true)

            if (onInstallerPromptToggle != null || onAutoInstallToggle != null) {
                SettingsGroup {
                    AnimatedVisibility(
                        visible = (currentSelection.value == InstallerManager.Token.Shizuku ||
                                currentSelection.value == InstallerManager.Token.ShizukuPlayStore) &&
                                onAutoInstallToggle != null,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
                    ) {
                        Column {
                            SettingsItem(
                                onClick = {
                                    val newValue = !autoInstallEnabled
                                    onAutoInstallToggle?.invoke(newValue)
                                    if (newValue && installerPromptEnabled) {
                                        onInstallerPromptToggle?.invoke(false)
                                    }
                                },
                                leadingContent = { MorpheIcon(icon = Icons.Outlined.Bolt, size = 28.dp) },
                                title = stringResource(R.string.settings_auto_install_with_shizuku),
                                subtitle = stringResource(R.string.settings_auto_install_with_shizuku_description),
                                trailingContent = {
                                    MorpheSwitch(
                                        checked = autoInstallEnabled,
                                        onCheckedChange = null,
                                        modifier = Modifier.semantics {
                                            stateDescription = if (autoInstallEnabled) enabledState else disabledState
                                        }
                                    )
                                }
                            )
                            MorpheSettingsDivider()
                        }
                    }

                    if (onInstallerPromptToggle != null) {
                        SettingsItem(
                            onClick = {
                                val newValue = !installerPromptEnabled
                                onInstallerPromptToggle(newValue)
                                if (newValue && autoInstallEnabled) {
                                    onAutoInstallToggle?.invoke(false)
                                }
                            },
                            leadingContent = { MorpheIcon(icon = Icons.Outlined.Android, size = 28.dp) },
                            title = stringResource(R.string.settings_prompt_installer_on_install),
                            subtitle = stringResource(R.string.settings_prompt_installer_on_install_description),
                            trailingContent = {
                                MorpheSwitch(
                                    checked = installerPromptEnabled,
                                    onCheckedChange = null,
                                    modifier = Modifier.semantics {
                                        stateDescription = if (installerPromptEnabled) enabledState else disabledState
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    pendingPlayStoreConfirm?.let { token ->
        PlayStoreInstallerWarningDialog(
            onConfirm = {
                pendingPlayStoreConfirm = null
                onConfirm(token)
            },
            onDismiss = { pendingPlayStoreConfirm = null }
        )
    }
}

/**
 * Single installer option item in dialog.
 */
@Composable
fun InstallerOptionItem(
    option: InstallerManager.Entry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    stateDescription: String
) {
    val colors = MaterialTheme.colorScheme

    // Build description with availability reason for disabled items
    val description = remember(option, enabled) {
        option.description?.takeIf { it.isNotBlank() }
    }

    val reasonResId = option.availability.reason
    val reasonText = if (!enabled && reasonResId != null) stringResource(reasonResId) else null

    val hasCustomIcon = option.icon != null &&
        (option.token == InstallerManager.Token.Shizuku ||
                option.token == InstallerManager.Token.ShizukuPlayStore ||
                option.token == InstallerManager.Token.PlayStore ||
                option.token == InstallerManager.Token.RootPlayStore ||
                option.token is InstallerManager.Token.Component)

    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        enabled = enabled,
        stateDescription = stateDescription,
        modifier = Modifier.padding(vertical = 2.dp),
        leadingContent = if (hasCustomIcon) {
            {
                SelectionLeadingBox(selected = selected, enabled = enabled) {
                    Image(
                        painter = rememberDrawablePainter(drawable = option.icon),
                        contentDescription = null,
                        modifier = Modifier.size(MorpheDefaults.IconSize),
                        alpha = if (enabled) 1f else 0.4f
                    )
                }
            }
        } else null
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            IconTextRow(
                leadingContent = null,
                title = option.label,
                description = description,
                titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                descriptionColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
                trailingContent = null
            )

            if (reasonText != null) {
                InfoBadge(
                    text = reasonText,
                    style = InfoBadgeStyle.Warning,
                    icon = Icons.Outlined.Warning,
                    isCompact = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Dialog shown when user's preferred installer (Shizuku/Root) is unavailable.
 */
@Composable
fun InstallerUnavailableDialog(
    state: InstallViewModel.InstallerUnavailableState,
    onOpenApp: () -> Unit,
    onRetry: () -> Unit,
    onUseFallback: () -> Unit,
    onDismiss: () -> Unit
) {
    val installerName = when (state.installerToken) {
        InstallerManager.Token.Shizuku -> stringResource(R.string.home_app_info_install_type_shizuku)
        InstallerManager.Token.ShizukuPlayStore -> stringResource(R.string.home_app_info_install_type_shizuku_play_store)
        InstallerManager.Token.AutoSaved -> stringResource(R.string.installer_auto_saved_name)
        InstallerManager.Token.RootPlayStore -> stringResource(R.string.home_app_info_install_type_root_play_store)
        else -> stringResource(R.string.home_app_info_install_type_system_installer)
    }

    val reasonText = state.reason?.let { stringResource(it) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_unavailable_title, installerName),
        footer = {
            MorpheDialogButtonColumn {
                // Primary action - Retry
                MorpheDialogButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )

                // Secondary action - Open app (if available)
                if (state.canOpenApp) {
                    MorpheDialogButton(
                        text = when (state.installerToken) {
                            InstallerManager.Token.Shizuku -> stringResource(R.string.installer_action_open_shizuku)
                            InstallerManager.Token.ShizukuPlayStore -> stringResource(R.string.installer_action_open_shizuku)
                            else -> stringResource(R.string.open)
                        },
                        onClick = onOpenApp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Fallback option
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.installer_use_standard),
                    onClick = onUseFallback,
                    modifier = Modifier.fillMaxWidth()
                )

                // Cancel
                MorpheDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main message
            Text(
                text = stringResource(R.string.installer_unavailable_message, installerName),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            // Error reason badge
            if (reasonText != null) {
                InfoBadge(
                    text = reasonText,
                    style = InfoBadgeStyle.Error,
                    icon = Icons.Outlined.Warning,
                    isExpanded = true
                )
            }

            // Shizuku-specific hint
            if (state.canOpenApp &&
                (state.installerToken == InstallerManager.Token.Shizuku ||
                        state.installerToken == InstallerManager.Token.ShizukuPlayStore)
            ) {
                InfoBadge(
                    text = stringResource(R.string.installer_unavailable_shizuku_hint),
                    style = InfoBadgeStyle.Primary,
                    isExpanded = true
                )
            }
        }
    }
}

/**
 * Warning shown when the user picks a Play Store install variant.
 *
 * Recording Google Play Store as the installation source lets Play Store offer updates for the patched
 * app - accepting the update overwrites the patched APK with the stock one and loses the patches.
 */
@Composable
fun PlayStoreInstallerWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_play_store_warning_title),
        footer = {
            MorpheDialogButtonColumn {
                MorpheDialogButton(
                    text = stringResource(R.string.installer_play_store_warning_continue),
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth()
                )
                MorpheDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.installer_play_store_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            InfoBadge(
                text = stringResource(R.string.installer_play_store_warning_risk),
                style = InfoBadgeStyle.Error,
                icon = Icons.Outlined.Warning,
                isExpanded = true
            )
        }
    }
}

/**
 * Dialog shown to root device users before patching to choose between Root Mount and Standard Install.
 *
 * The installation method directly affects how the APK is patched:
 * - **Root Mount** excludes the GmsCore support patch - the mounted APK replaces the
 *   stock APK in-place via bind-mount, so the original Google services remain available
 *   and GmsCore would actually interfere.
 * - **Standard Install** includes GmsCore support - the patched APK is installed as a
 *   separate app that needs the microG / GmsCore bridge to communicate with Google services.
 *
 * Because of this, the choice cannot be deferred to after patching. The user must decide now so
 * the correct set of patches is applied.
 */
@Composable
fun PrePatchInstallerDialog(
    onSelectMount: () -> Unit,
    onSelectStandard: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.root_pre_patch_installer_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.cancel),
                onPrimaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description
            Text(
                text = stringResource(R.string.root_pre_patch_installer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            // Root Mount option
            InstallerOptionCard(
                icon = Icons.Outlined.Link,
                title = stringResource(R.string.root_pre_patch_installer_mount_title),
                description = stringResource(R.string.root_pre_patch_installer_mount_description),
                onClick = onSelectMount
            )

            // Standard Install option
            InstallerOptionCard(
                icon = Icons.Outlined.InstallMobile,
                title = stringResource(R.string.root_pre_patch_installer_standard_title),
                description = stringResource(R.string.root_pre_patch_installer_standard_description),
                onClick = onSelectStandard
            )

            // Info hint
            InfoBadge(
                text = stringResource(R.string.root_pre_patch_installer_hint),
                style = InfoBadgeStyle.Primary,
                icon = Icons.Outlined.Info,
                isExpanded = true
            )
        }
    }
}

/**
 * Clickable card representing an installer option in the pre-patch dialog.
 */
@Composable
private fun InstallerOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MorpheIcon(
                icon = icon,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
