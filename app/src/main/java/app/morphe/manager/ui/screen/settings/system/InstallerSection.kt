/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    val autoUninstallEnabled by settingsViewModel.prefs.autoUninstallWithShizuku.getAsState()
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
        shizukuStatusProvider = settingsViewModel::getShizukuStatus,
        onRequestShizukuPermission = settingsViewModel::requestShizukuPermission,
        autoInstallEnabled = autoInstallEnabled,
        onAutoInstallToggle = settingsViewModel::setAutoInstallWithShizuku,
        autoUninstallEnabled = autoUninstallEnabled,
        onAutoUninstallToggle = settingsViewModel::setAutoUninstallWithShizuku,
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
                InstallerIconPreview(
                    drawable = entry.icon,
                    selected = true,
                    enabled = entry.availability.available
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
    shizukuStatusProvider: (() -> SessionInstaller.ShizukuStatus)? = null,
    onRequestShizukuPermission: (() -> Boolean)? = null,
    autoInstallEnabled: Boolean = false,
    onAutoInstallToggle: ((Boolean) -> Unit)? = null,
    autoUninstallEnabled: Boolean = false,
    onAutoUninstallToggle: ((Boolean) -> Unit)? = null,
    installerPromptEnabled: Boolean = false,
    onInstallerPromptToggle: ((Boolean) -> Unit)? = null
) {
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    val visibleOptions = remember(options) {
        options.filterNot { it.token.isCollapsedPlayStoreVariant() }
    }
    val currentSelection = remember(selected) { mutableStateOf(selected.baseInstallerToken()) }
    val selectedToken = currentSelection.value
    var installAsPlayStore by remember(selected) { mutableStateOf(selected.isPlayStoreModeToken()) }
    val effectiveToken = selectedToken.withPlayStoreMode(installAsPlayStore)
    var inlineShizukuStatus by remember { mutableStateOf<SessionInstaller.ShizukuStatus?>(null) }

    // Ensure valid selection when options change
    LaunchedEffect(visibleOptions, selected) {
        val tokens = visibleOptions.map { it.token }
        if (currentSelection.value !in tokens) {
            currentSelection.value = visibleOptions.firstOrNull { it.availability.available }?.token
                ?: tokens.firstOrNull()
                        ?: selected.baseInstallerToken()
        }
    }

    LaunchedEffect(selectedToken) {
        if (!selectedToken.supportsPlayStoreMode()) {
            installAsPlayStore = false
        }
    }

    LaunchedEffect(selectedToken, shizukuStatusProvider) {
        if (!selectedToken.isShizukuToken() || shizukuStatusProvider == null) {
            inlineShizukuStatus = null
            return@LaunchedEffect
        }

        while (isActive) {
            inlineShizukuStatus = withContext(Dispatchers.IO) { shizukuStatusProvider() }
            delay(1_500)
        }
    }

    val confirmEnabled = (options.find { it.token == effectiveToken }
        ?: visibleOptions.find { it.token == selectedToken })?.availability?.available != false

    var pendingPlayStoreModeConfirm by remember { mutableStateOf(false) }
    var pendingAutoUninstallConfirm by remember { mutableStateOf(false) }
    var showShizukuStatus by remember { mutableStateOf(false) }

    // Localized strings for accessibility
    val selectedState = stringResource(R.string.selected)
    val notSelectedState = stringResource(R.string.not_selected)
    val disabledState = stringResource(R.string.disabled)

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.confirm),
                onPrimaryClick = {
                    onConfirm(effectiveToken)
                },
                primaryEnabled = confirmEnabled,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleOptions.forEach { option ->
                val enabled = option.availability.available
                val isSelected = selectedToken == option.token
                val isShizukuOption = option.token.isShizukuToken()
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

                if (isSelected &&
                    isShizukuOption &&
                    inlineShizukuStatus != null &&
                    !inlineShizukuStatus!!.availability.available
                ) {
                    val status = inlineShizukuStatus!!
                    val statusText = status.availability.reason?.let { stringResource(it) }
                        ?: stringResource(R.string.installer_shizuku_status_issue)
                    InfoBadge(
                        text = statusText,
                        style = InfoBadgeStyle.Warning,
                        icon = Icons.Outlined.Warning,
                        isCompact = true,
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }

                if (isSelected && isShizukuOption && shizukuStatusProvider != null) {
                    TextButton(
                        onClick = { showShizukuStatus = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.installer_shizuku_status_action),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedToken.supportsPlayStoreMode() &&
                        options.any { it.token == selectedToken.withPlayStoreMode(true) },
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                MorpheDialogToggleRow(
                    icon = Icons.Outlined.Storefront,
                    title = stringResource(R.string.installer_play_store_mode),
                    description = stringResource(R.string.installer_play_store_mode_description),
                    checked = installAsPlayStore,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            pendingPlayStoreModeConfirm = true
                        } else {
                            installAsPlayStore = false
                        }
                    }
                )
            }

            // Auto-install toggle
            AnimatedVisibility(
                visible = selectedToken.isShizukuToken() &&
                        onAutoInstallToggle != null,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                MorpheDialogToggleRow(
                    icon = Icons.Outlined.Bolt,
                    title = stringResource(R.string.settings_auto_install_with_shizuku),
                    description = stringResource(R.string.settings_auto_install_with_shizuku_description),
                    checked = autoInstallEnabled,
                    onCheckedChange = { newValue ->
                        onAutoInstallToggle?.invoke(newValue)
                        // Mutually exclusive with Prompt on install
                        if (newValue && installerPromptEnabled) {
                            onInstallerPromptToggle?.invoke(false)
                        }
                    }
                )
            }

            // Auto-uninstall toggle
            AnimatedVisibility(
                visible = selectedToken.isShizukuToken() &&
                        autoInstallEnabled &&
                        onAutoUninstallToggle != null,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                MorpheDialogToggleRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.settings_auto_uninstall_with_shizuku),
                    description = stringResource(R.string.settings_auto_uninstall_with_shizuku_description),
                    checked = autoUninstallEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            pendingAutoUninstallConfirm = true
                        } else {
                            onAutoUninstallToggle?.invoke(false)
                        }
                    }
                )
            }

            // Prompt on install toggle
            if (onInstallerPromptToggle != null) {
                MorpheDialogToggleRow(
                    icon = Icons.Outlined.Android,
                    title = stringResource(R.string.settings_prompt_installer_on_install),
                    description = stringResource(R.string.settings_prompt_installer_on_install_description),
                    checked = installerPromptEnabled,
                    onCheckedChange = { newValue ->
                        onInstallerPromptToggle(newValue)
                        // Mutually exclusive with Auto-install
                        if (newValue && autoInstallEnabled) {
                            onAutoInstallToggle?.invoke(false)
                        }
                    }
                )
            }
        }
    }

    if (pendingPlayStoreModeConfirm) {
        PlayStoreInstallerWarningDialog(
            onConfirm = {
                pendingPlayStoreModeConfirm = false
                installAsPlayStore = true
            },
            onDismiss = { pendingPlayStoreModeConfirm = false }
        )
    }

    if (pendingAutoUninstallConfirm) {
        AutoUninstallWarningDialog(
            onConfirm = {
                pendingAutoUninstallConfirm = false
                onAutoUninstallToggle?.invoke(true)
            },
            onDismiss = { pendingAutoUninstallConfirm = false }
        )
    }

    if (showShizukuStatus && shizukuStatusProvider != null) {
        ShizukuStatusDialog(
            statusProvider = shizukuStatusProvider,
            onOpenShizuku = onOpenShizuku,
            onRequestPermission = onRequestShizukuPermission,
            onDismiss = { showShizukuStatus = false }
        )
    }
}

private fun InstallerManager.Token.baseInstallerToken(): InstallerManager.Token = when (this) {
    InstallerManager.Token.PlayStore -> InstallerManager.Token.Internal
    InstallerManager.Token.RootPlayStore -> InstallerManager.Token.AutoSaved
    InstallerManager.Token.ShizukuPlayStore -> InstallerManager.Token.Shizuku
    else -> this
}

private fun InstallerManager.Token.isCollapsedPlayStoreVariant(): Boolean =
    this == InstallerManager.Token.PlayStore ||
            this == InstallerManager.Token.RootPlayStore ||
            this == InstallerManager.Token.ShizukuPlayStore

private fun InstallerManager.Token.isPlayStoreModeToken(): Boolean =
    this == InstallerManager.Token.PlayStore ||
            this == InstallerManager.Token.RootPlayStore ||
            this == InstallerManager.Token.ShizukuPlayStore

private fun InstallerManager.Token.supportsPlayStoreMode(): Boolean =
    this == InstallerManager.Token.Internal ||
            this == InstallerManager.Token.AutoSaved ||
            this == InstallerManager.Token.Shizuku

private fun InstallerManager.Token.withPlayStoreMode(enabled: Boolean): InstallerManager.Token = when {
    this == InstallerManager.Token.Internal && enabled -> InstallerManager.Token.PlayStore
    this == InstallerManager.Token.AutoSaved && enabled -> InstallerManager.Token.RootPlayStore
    this == InstallerManager.Token.Shizuku && enabled -> InstallerManager.Token.ShizukuPlayStore
    else -> this
}

private fun InstallerManager.Token.isShizukuToken(): Boolean =
    baseInstallerToken() == InstallerManager.Token.Shizuku

@Composable
private fun AutoUninstallWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_auto_uninstall_warning_title),
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
                text = stringResource(R.string.settings_auto_uninstall_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            InfoBadge(
                text = stringResource(R.string.settings_auto_uninstall_warning_risk),
                style = InfoBadgeStyle.Error,
                icon = Icons.Outlined.Warning,
                isExpanded = true
            )
        }
    }
}

@Composable
private fun ShizukuStatusDialog(
    statusProvider: () -> SessionInstaller.ShizukuStatus,
    onOpenShizuku: (() -> Boolean)?,
    onRequestPermission: (() -> Boolean)?,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf<SessionInstaller.ShizukuStatus?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        status = withContext(Dispatchers.IO) { statusProvider() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1.seconds)
            status = withContext(Dispatchers.IO) { statusProvider() }
        }
    }

    val current = status
    val issueText = current?.availability?.reason?.let { stringResource(it) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_shizuku_status_title),
        footer = {
            MorpheDialogButtonColumn {
                if (current != null &&
                    current.installed &&
                    current.running &&
                    !current.permissionGranted &&
                    onRequestPermission != null
                ) {
                    MorpheDialogButton(
                        text = stringResource(R.string.installer_shizuku_request_permission),
                        onClick = {
                            runCatching { onRequestPermission.invoke() }
                            refreshKey++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Key
                    )
                }

                if (onOpenShizuku != null) {
                    MorpheDialogOutlinedButton(
                        text = stringResource(R.string.installer_action_open_shizuku),
                        onClick = { runCatching { onOpenShizuku.invoke() } },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Outlined.OpenInNew
                    )
                }

                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.refresh),
                    onClick = { refreshKey++ },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Refresh
                )

                MorpheDialogOutlinedButton(
                    text = stringResource(android.R.string.ok),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (current != null) {
                InfoBadge(
                    text = if (current.availability.available) {
                        stringResource(R.string.installer_shizuku_status_ready)
                    } else {
                        stringResource(R.string.installer_shizuku_status_issue)
                    },
                    style = if (current.availability.available) InfoBadgeStyle.Success else InfoBadgeStyle.Warning,
                    icon = if (current.availability.available) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    isExpanded = true
                )

                if (issueText != null) {
                    InfoBadge(
                        text = issueText,
                        style = InfoBadgeStyle.Primary,
                        icon = Icons.Outlined.Info,
                        isExpanded = true
                    )
                }

                ShizukuStatusRow(
                    label = stringResource(R.string.installer_shizuku_status_mode),
                    value = when (current.mode) {
                        SessionInstaller.ShizukuMode.Shizuku -> stringResource(R.string.home_app_info_install_type_shizuku)
                        SessionInstaller.ShizukuMode.Sui -> "Sui"
                    }
                )
                ShizukuStatusRow(
                    label = stringResource(R.string.installed),
                    value = statusYesNo(current.installed)
                )
                ShizukuStatusRow(
                    label = stringResource(R.string.installer_shizuku_status_supported),
                    value = statusYesNo(current.supported)
                )
                ShizukuStatusRow(
                    label = stringResource(R.string.installer_shizuku_status_running),
                    value = statusYesNo(current.running)
                )
                ShizukuStatusRow(
                    label = stringResource(R.string.installer_shizuku_status_permission),
                    value = if (current.permissionGranted) {
                        stringResource(R.string.installer_shizuku_status_granted)
                    } else {
                        stringResource(R.string.installer_shizuku_status_missing)
                    }
                )
                current.packageName?.let { provider ->
                    ShizukuStatusRow(
                        label = stringResource(R.string.installer_shizuku_status_provider),
                        value = provider
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun statusYesNo(value: Boolean): String =
    if (value) {
        stringResource(R.string.yes)
    } else {
        stringResource(R.string.no)
    }

@Composable
private fun ShizukuStatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDialogSecondaryTextColor.current,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalDialogTextColor.current
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
                InstallerIconPreview(
                    drawable = option.icon,
                    selected = selected,
                    enabled = enabled
                )
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
 * Installer icon preview component.
 */
@Composable
fun InstallerIconPreview(
    drawable: Drawable?,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) colors.surfaceVariant
                else colors.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = if (selected && enabled) 2.dp else 1.dp,
                color = when {
                    !enabled -> colors.outlineVariant.copy(alpha = 0.5f)
                    selected -> colors.primary
                    else -> colors.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable = drawable),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                alpha = if (enabled) 1f else 0.4f
            )
        } else {
            MorpheIcon(
                icon = Icons.Outlined.ChevronRight,
                tint = colors.primary.copy(alpha = if (enabled) 1f else 0.4f)
            )
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
 * Warning shown when the user enables Play Store install-source mode.
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
