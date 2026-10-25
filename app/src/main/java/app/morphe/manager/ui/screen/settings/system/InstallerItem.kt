/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.installer.ShizukuEnvironment
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

/** The installer patched apps go through, as a row of a settings group opening its page. */
@Composable
fun InstallerItem(
    settingsViewModel: SettingsViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryToken = settingsViewModel.primaryInstallerToken()
    val installTarget = InstallerManager.InstallTarget.PATCHER
    val primaryEntries = rememberPolledInstallerEntries(primaryToken) {
        settingsViewModel.getInstallerEntries(installTarget, primaryToken)
    }

    val primaryEntry = primaryEntries.find { it.token == primaryToken }
        ?: settingsViewModel.describeInstallerEntry(primaryToken, installTarget)
        ?: primaryEntries.firstOrNull()

    if (primaryEntry != null) {
        InstallerSettingsItem(
            title = stringResource(R.string.settings_installation),
            entry = primaryEntry,
            onClick = onClick,
            modifier = modifier
        )
    }
}

/** The default installer, as the preference holding it reads now. */
@Composable
internal fun SettingsViewModel.primaryInstallerToken(): InstallerManager.Token {
    val preference by prefs.installerPrimary.getAsState()
    return remember(preference) { parseInstallerToken(preference) }
}

/** Installer entries from [provider], read again every so often so a change in availability shows up. */
@Composable
internal fun rememberPolledInstallerEntries(
    key: Any?,
    provider: () -> List<InstallerManager.Entry>
): List<InstallerManager.Entry> {
    val currentProvider by rememberUpdatedState(provider)
    var entries by remember(key) { mutableStateOf(provider()) }
    LaunchedEffect(key) {
        while (isActive) {
            delay(1.5.seconds)
            // Each read queries PackageManager for every installer, too slow to repeat on the main thread
            entries = withContext(Dispatchers.IO) { currentProvider() }
        }
    }
    return entries
}

/**
 * Installer settings item.
 */
@Composable
private fun InstallerSettingsItem(
    title: String,
    entry: InstallerManager.Entry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            if (entry.hasOwnIcon) {
                Image(
                    painter = rememberDrawablePainter(drawable = entry.icon),
                    contentDescription = null,
                    modifier = Modifier.size(Defaults.IconSize),
                    alpha = if (entry.availability.available) 1f else 0.4f
                )
            } else {
                ThemedIcon(icon = MaterialSymbols.Rounded.Android)
            }
        },
        title = title,
        subtitle = supportingText.takeIf { it.isNotEmpty() },
        modifier = modifier
    )
}

/** Whether the entry is drawn with the icon of the app behind it rather than a generic one. */
private val InstallerManager.Entry.hasOwnIcon: Boolean
    get() = icon != null &&
            (token == InstallerManager.Token.Shizuku ||
                    token == InstallerManager.Token.ShizukuPlayStore ||
                    token == InstallerManager.Token.PlayStore ||
                    token == InstallerManager.Token.RootPlayStore ||
                    token is InstallerManager.Token.Component)

/**
 * The installers to pick from, with Play Store mode for one that offers it. [selected] is the
 * installer in full, Play Store mode included, and so is what [onSelect] hands back. Each part
 * stands as a block of its own in what holds it, as the rows of a settings page do.
 */
@Composable
internal fun InstallerChoice(
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    onSelect: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)?,
    shizukuStatusProvider: (() -> SessionInstaller.ShizukuStatus)?,
    onRequestShizukuPermission: (() -> Boolean)?
) {
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    val visibleOptions = remember(options) {
        options.filterNot { it.token.isPlayStoreModeToken() }
    }
    val selectedToken = selected.baseInstallerToken()
    val installAsPlayStore = selected.isPlayStoreModeToken()
    var inlineShizukuStatus by remember { mutableStateOf<SessionInstaller.ShizukuStatus?>(null) }

    LaunchedEffect(selectedToken, shizukuStatusProvider) {
        if (!selectedToken.isShizukuToken() || shizukuStatusProvider == null) {
            inlineShizukuStatus = null
            return@LaunchedEffect
        }

        while (isActive) {
            inlineShizukuStatus = withContext(Dispatchers.IO) { shizukuStatusProvider() }
            delay(1.5.seconds)
        }
    }

    var pendingPlayStoreModeConfirm by remember { mutableStateOf(false) }
    var showShizukuStatus by remember { mutableStateOf(false) }

    // Localized strings for accessibility
    val selectedState = stringResource(R.string.state_selected)
    val notSelectedState = stringResource(R.string.state_not_selected)
    val disabledState = stringResource(R.string.disabled)

    SettingsGroup {
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

            val shizukuFooterText = if (isSelected &&
                isShizukuOption &&
                inlineShizukuStatus != null &&
                !inlineShizukuStatus!!.availability.available
            ) {
                inlineShizukuStatus!!.availability.reason?.let { stringResource(it) }
                    ?: stringResource(R.string.installer_shizuku_status_issue)
            } else null

            val showStatusAction =
                isSelected && isShizukuOption && shizukuStatusProvider != null
            val openLabel = stringResource(R.string.installer_action_open_shizuku)
            val statusLabel = stringResource(R.string.installer_shizuku_status_action)

            SearchableSetting(listOf(option.label, option.description)) {
                InstallerOptionItem(
                    option = option,
                    selected = isSelected,
                    enabled = enabled,
                    // Play Store mode carries over to another installer that offers it
                    onSelect = { if (enabled) onSelect(option.token.withPlayStoreMode(installAsPlayStore)) },
                    stateDescription = stateDesc,
                    footerText = shizukuFooterText,
                    // Shizuku's fixes sit in its own card, under what is wrong with it
                    footerActions = listOfNotNull(
                        CardAction(
                            icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                            label = openLabel,
                            onClick = { runCatching { onOpenShizuku?.invoke() } },
                            primary = true
                        ).takeIf { showShizukuAction },
                        CardAction(
                            icon = MaterialSymbols.Rounded.Info,
                            label = statusLabel,
                            onClick = { showShizukuStatus = true }
                        ).takeIf { showStatusAction }
                    )
                )
            }
        }
    }

    val moduleUnmountRisk = rememberModuleUnmountRisk()
    FoldingVisibility(visible = selectedToken == InstallerManager.Token.AutoSaved && moduleUnmountRisk) {
        SettingsFooter(
            text = stringResource(R.string.root_mount_module_unmount_warning),
            tone = SemanticTone.Warning
        )
    }

    val showPlayStoreToggle = selectedToken.supportsPlayStoreMode() &&
            options.any { it.token == selectedToken.withPlayStoreMode(true) }
    FoldingVisibility(visible = showPlayStoreToggle) {
        SettingsGroup {
            SettingsSwitchItem(
                checked = installAsPlayStore,
                onToggle = {
                    if (installAsPlayStore) {
                        onSelect(selectedToken)
                    } else {
                        pendingPlayStoreModeConfirm = true
                    }
                },
                leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Storefront, size = 28.dp) },
                title = stringResource(R.string.installer_play_store_mode),
                subtitle = stringResource(R.string.installer_play_store_mode_description)
            )
        }
    }

    DialogHost(visible = pendingPlayStoreModeConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.installer_play_store_warning_title),
            message = stringResource(R.string.installer_play_store_warning_message),
            primaryText = stringResource(R.string.installer_play_store_warning_continue),
            isPrimaryDestructive = false,
            // A risk the user can avoid by turning off updates, not a failure
            notice = stringResource(R.string.installer_play_store_warning_risk),
            onConfirm = {
                pendingPlayStoreModeConfirm = false
                onSelect(selectedToken.withPlayStoreMode(true))
            },
            onDismiss = { pendingPlayStoreModeConfirm = false }
        )
    }

    DialogHost(target = shizukuStatusProvider?.takeIf { showShizukuStatus }) { statusProvider ->
        ShizukuStatusDialog(
            statusProvider = statusProvider,
            onOpenShizuku = onOpenShizuku,
            onRequestPermission = onRequestShizukuPermission,
            onDismiss = { showShizukuStatus = false }
        )
    }
}

/**
 * Dialog for picking the installer a single install goes through, the default one preselected.
 */
@Composable
fun DialogHostScope.InstallerSelectionDialog(
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)?,
    shizukuStatusProvider: (() -> SessionInstaller.ShizukuStatus)? = null,
    onRequestShizukuPermission: (() -> Boolean)? = null
) {
    var choice by remember(selected) { mutableStateOf(selected) }

    // Ensure valid selection when options change
    LaunchedEffect(options, selected) {
        val visibleOptions = options.filterNot { it.token.isPlayStoreModeToken() }
        val tokens = visibleOptions.map { it.token }
        if (choice.baseInstallerToken() !in tokens) {
            choice = visibleOptions.firstOrNull { it.availability.available }?.token
                ?: tokens.firstOrNull()
                        ?: selected.baseInstallerToken()
        }
    }

    val confirmEnabled = (options.find { it.token == choice }
        ?: options.find { it.token == choice.baseInstallerToken() })?.availability?.available != false

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.confirm),
                onPrimaryClick = { onConfirm(choice) },
                primaryEnabled = confirmEnabled,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            InstallerChoice(
                options = options,
                selected = choice,
                onSelect = { choice = it },
                onOpenShizuku = onOpenShizuku,
                shizukuStatusProvider = shizukuStatusProvider,
                onRequestShizukuPermission = onRequestShizukuPermission
            )
        }
    }
}

private fun InstallerManager.Token.baseInstallerToken(): InstallerManager.Token = when (this) {
    InstallerManager.Token.PlayStore -> InstallerManager.Token.Internal
    InstallerManager.Token.RootPlayStore -> InstallerManager.Token.AutoSaved
    InstallerManager.Token.ShizukuPlayStore -> InstallerManager.Token.Shizuku
    else -> this
}

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

internal fun InstallerManager.Token.isShizukuToken(): Boolean =
    baseInstallerToken() == InstallerManager.Token.Shizuku

/**
 * Whether root comes from a manager that may hide module mounts from an app, which hides the
 * patched APK from it too. Magisk never does, and a NoMount redirect is no mount to hide, so this
 * stays false there, and until it is known.
 */
@Composable
private fun rememberModuleUnmountRisk(): Boolean {
    val rootInstaller: RootInstaller = koinInject()
    val hasRisk by produceState(false) {
        value = !rootInstaller.isMagisk() && !rootInstaller.isNoMountAvailable()
    }
    return hasRisk
}

/** The warning of [rememberModuleUnmountRisk], under a mount offered in a dialog. */
@Composable
private fun ModuleUnmountNotice() {
    FoldingVisibility(visible = rememberModuleUnmountRisk()) {
        Footnote(
            text = stringResource(R.string.root_mount_module_unmount_warning),
            tone = SemanticTone.Warning
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogHostScope.ShizukuStatusDialog(
    statusProvider: () -> SessionInstaller.ShizukuStatus,
    onOpenShizuku: (() -> Boolean)?,
    onRequestPermission: (() -> Boolean)?,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf<SessionInstaller.ShizukuStatus?>(null) }

    // Polled while open, so a permission granted or a service started in Shizuku shows up on its own
    LaunchedEffect(statusProvider) {
        while (isActive) {
            status = withContext(Dispatchers.IO) { statusProvider() }
            delay(1.seconds)
        }
    }

    val current = status
    val issueText = current?.availability?.reason?.let { stringResource(it) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_shizuku_status_title),
        footer = {
            AppDialogActions(
                actions = buildList {
                    if (current != null &&
                        current.installed &&
                        current.running &&
                        !current.permissionGranted &&
                        onRequestPermission != null
                    ) {
                        add(
                            DialogAction(
                                text = stringResource(R.string.installer_shizuku_request_permission),
                                onClick = { runCatching { onRequestPermission.invoke() } },
                                icon = MaterialSymbols.Rounded.Key
                            )
                        )
                    }

                    if (onOpenShizuku != null) {
                        add(
                            DialogAction(
                                text = stringResource(R.string.installer_action_open_shizuku),
                                onClick = { runCatching { onOpenShizuku.invoke() } },
                                icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                                emphasis = DialogActionEmphasis.Outlined
                            )
                        )
                    }

                    add(
                        DialogAction(
                            text = stringResource(R.string.close),
                            onClick = onDismiss,
                            emphasis = DialogActionEmphasis.Outlined
                        )
                    )
                },
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            if (current != null) {
                Notice(
                    text = if (current.availability.available) {
                        stringResource(R.string.installer_shizuku_status_ready)
                    } else {
                        stringResource(R.string.installer_shizuku_status_issue)
                    },
                    tone = if (current.availability.available) SemanticTone.Success else SemanticTone.Warning,
                    icon = if (current.availability.available) MaterialSymbols.Rounded.Check_circle else MaterialSymbols.Rounded.Warning
                )

                if (issueText != null) {
                    Footnote(
                        text = issueText,
                        tone = SemanticTone.Primary
                    )
                }

                if (current.installed) {
                    InfoPanel {
                        InfoRow(
                            icon = MaterialSymbols.Rounded.Tune,
                            label = stringResource(R.string.installer_shizuku_status_mode),
                            value = when (current.flavor) {
                                ShizukuEnvironment.Flavor.Shizuku -> stringResource(R.string.home_app_info_install_type_shizuku)
                                ShizukuEnvironment.Flavor.ShizukuPlus -> "Shizuku+"
                                ShizukuEnvironment.Flavor.Sui -> "Sui"
                            }
                        )
                        SettingsDivider()
                        InfoRow(
                            icon = MaterialSymbols.Rounded.Verified,
                            label = stringResource(R.string.installer_shizuku_status_supported),
                            value = statusYesNo(current.supported)
                        )
                        SettingsDivider()
                        InfoRow(
                            icon = MaterialSymbols.Rounded.Play_circle,
                            label = stringResource(R.string.installer_shizuku_status_running),
                            value = statusYesNo(current.running)
                        )
                        SettingsDivider()
                        InfoRow(
                            icon = MaterialSymbols.Rounded.Key,
                            label = stringResource(R.string.installer_shizuku_status_permission),
                            value = if (current.permissionGranted) {
                                stringResource(R.string.installer_shizuku_status_granted)
                            } else {
                                stringResource(R.string.installer_shizuku_status_missing)
                            }
                        )
                        current.packageName?.let { provider ->
                            SettingsDivider()
                            InfoRow(
                                icon = MaterialSymbols.Rounded.Widgets,
                                label = stringResource(R.string.installer_shizuku_status_provider),
                                value = provider
                            )
                        }
                    }
                }
            } else {
                LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(Defaults.SpaceLarge)
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

/**
 * Single installer option item in dialog.
 */
@Composable
private fun InstallerOptionItem(
    option: InstallerManager.Entry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    stateDescription: String,
    footerText: String? = null,
    footerActions: List<CardAction> = emptyList()
) {
    val colors = MaterialTheme.colorScheme

    // Build description with availability reason for disabled items
    val description = remember(option, enabled) {
        option.description?.takeIf { it.isNotBlank() }
    }

    val reasonResId = option.availability.reason
    val reasonText = if (!enabled && reasonResId != null) stringResource(reasonResId) else null

    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        enabled = enabled,
        hasWarning = footerText != null,
        stateDescription = stateDescription,
        leadingContent = if (option.hasOwnIcon) {
            {
                SelectionLeadingBox(selected = selected, enabled = enabled) {
                    Image(
                        painter = rememberDrawablePainter(drawable = option.icon),
                        contentDescription = null,
                        modifier = Modifier.size(Defaults.IconSize),
                        alpha = if (enabled) 1f else 0.4f
                    )
                }
            }
        } else null,
        footerContent = if (reasonText != null || footerText != null || footerActions.isNotEmpty()) {
            {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                ) {
                    (reasonText ?: footerText)?.let { text ->
                        val tint = if (footerText != null) {
                            SemanticTone.Warning.content
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Warning,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint
                            )
                        }
                    }
                    if (footerActions.isNotEmpty()) CardActionRow(actions = footerActions)
                }
            }
        } else null
    ) {
        IconTextRow(
            modifier = Modifier.weight(1f),
            leadingContent = null,
            title = option.label,
            description = description,
            titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            descriptionColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
            trailingContent = null
        )
    }
}

/**
 * Dialog shown when user's preferred installer (Shizuku/Root) is unavailable.
 */
@Composable
fun DialogHostScope.InstallerUnavailableDialog(
    state: InstallViewModel.InstallerUnavailableState,
    onOpenApp: () -> Unit,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.installer_unavailable_title, installerName),
        description = stringResource(R.string.installer_unavailable_message, installerName),
        footer = {
            AppDialogActions(
                actions = buildList {
                    // Primary action - Retry
                    add(
                        DialogAction(
                            text = stringResource(R.string.retry),
                            onClick = onRetry
                        )
                    )

                    // Secondary action - Open app (if available)
                    if (state.canOpenApp) {
                        add(
                            DialogAction(
                                text = when (state.installerToken) {
                                    InstallerManager.Token.Shizuku -> stringResource(R.string.installer_action_open_shizuku)
                                    InstallerManager.Token.ShizukuPlayStore -> stringResource(R.string.installer_action_open_shizuku)
                                    else -> stringResource(R.string.open)
                                },
                                onClick = onOpenApp,
                                emphasis = DialogActionEmphasis.Filled
                            )
                        )
                    }

                    // Any other installer for this install, the standard one among them
                    add(
                        DialogAction(
                            text = stringResource(R.string.installer_choose_another),
                            onClick = onChooseAnother
                        )
                    )

                    // Cancel
                    add(
                        DialogAction(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss
                        )
                    )
                },
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            // Error reason badge
            if (reasonText != null) {
                Notice(
                    text = reasonText,
                    tone = SemanticTone.Error,
                    icon = MaterialSymbols.Rounded.Warning
                )
            }

            // Shizuku-specific hint
            if (state.canOpenApp &&
                (state.installerToken == InstallerManager.Token.Shizuku ||
                        state.installerToken == InstallerManager.Token.ShizukuPlayStore)
            ) {
                Footnote(
                    text = stringResource(R.string.installer_unavailable_shizuku_hint),
                    tone = SemanticTone.Primary
                )
            }
        }
    }
}

/**
 * Dialog shown to root device users before patching to choose between root mount mode and
 * standard install mode.
 *
 * The patch mode is the install target patches declare their availability against, so it decides
 * which of them can run at all:
 * - **Root Mount** replaces the stock APK in-place via bind-mount, so the original Google services
 *   remain available and patches that bridge them declare themselves unavailable here.
 * - **Standard Install** produces a separate app, where those same patches are required instead.
 *
 * Because of this, the choice cannot be deferred to after patching. The user must decide now so
 * the correct set of patches is applied.
 */
@Composable
fun DialogHostScope.PrePatchInstallerDialog(
    packageName: String?,
    onSelectMount: () -> Unit,
    onSelectStandard: () -> Unit,
    onDismiss: () -> Unit,
    // The hint names the setting that raises the dialog, which is not what opened it everywhere
    showPromptHint: Boolean = true
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.root_pre_patch_installer_title),
        description = stringResource(R.string.root_pre_patch_installer_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.cancel),
                onPrimaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            // Root Mount option
            SettingsItem(
                onClick = onSelectMount,
                icon = MaterialSymbols.Rounded.Link,
                title = stringResource(R.string.root_pre_patch_installer_mount_title),
                subtitle = stringResource(R.string.root_pre_patch_installer_mount_description),
                showBorder = true
            )

            ModuleUnmountNotice()

            // Standard Install option
            SettingsItem(
                onClick = onSelectStandard,
                icon = MaterialSymbols.Rounded.Install_mobile,
                title = stringResource(R.string.root_pre_patch_installer_standard_title),
                subtitle = stringResource(R.string.root_pre_patch_installer_standard_description),
                showBorder = true
            )

            // Info hint
            if (showPromptHint) {
                Footnote(
                    text = stringResource(R.string.root_pre_patch_installer_hint),
                    tone = SemanticTone.Primary
                )
            }
        }
    }
}
