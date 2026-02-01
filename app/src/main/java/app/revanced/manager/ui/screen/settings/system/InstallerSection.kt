package app.revanced.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.ui.screen.shared.*
import app.revanced.manager.ui.viewmodel.InstallViewModel
import app.revanced.manager.ui.viewmodel.SettingsViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Installer section
 */
@Composable
fun InstallerSection(
    installerManager: InstallerManager,
    settingsViewModel: SettingsViewModel,
    onShowInstallerDialog: () -> Unit
) {
    val primaryPreference by settingsViewModel.prefs.installerPrimary.getAsState()
    val primaryToken = remember(primaryPreference) {
        installerManager.parseToken(primaryPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER

    // Installer entries with periodic updates
    var primaryEntries by remember(primaryToken) {
        mutableStateOf(
            ensureValidEntries(
                installerManager.listEntries(installTarget, includeNone = false),
                primaryToken,
                installerManager,
                installTarget
            )
        )
    }

    // Periodically update installer list
    LaunchedEffect(installTarget, primaryToken) {
        while (isActive) {
            primaryEntries = ensureValidEntries(
                installerManager.listEntries(installTarget, includeNone = false),
                primaryToken,
                installerManager,
                installTarget
            )
            delay(1_500)
        }
    }

    // Get current entry
    val primaryEntry = primaryEntries.find { it.token == primaryToken }
        ?: installerManager.describeEntry(primaryToken, installTarget)
        ?: primaryEntries.firstOrNull()

    if (primaryEntry != null) {
        InstallerSettingsItem(
            title = stringResource(R.string.installer),
            entry = primaryEntry,
            onClick = onShowInstallerDialog
        )
    }
}

/**
 * Container for installer selection dialog
 */
@Composable
fun InstallerSelectionDialogContainer(
    installerManager: InstallerManager,
    settingsViewModel: SettingsViewModel,
    rootInstaller: RootInstaller,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val primaryPreference by settingsViewModel.prefs.installerPrimary.getAsState()
    val primaryToken = remember(primaryPreference) {
        installerManager.parseToken(primaryPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER
    val options = ensureValidEntries(
        installerManager.listEntries(installTarget, includeNone = false),
        primaryToken,
        installerManager,
        installTarget
    )

    InstallerSelectionDialog(
        title = stringResource(R.string.installer),
        options = options,
        selected = primaryToken,
        onDismiss = onDismiss,
        onConfirm = { selection ->
            // Request root access only when 'Rooted mount installer' is selected
            if (selection == InstallerManager.Token.AutoSaved) {
                coroutineScope.launch(Dispatchers.IO) {
                    runCatching { rootInstaller.hasRootAccess() }
                }
            }

            settingsViewModel.setPrimaryInstaller(selection)
            onDismiss()
        },
        onOpenShizuku = installerManager::openShizukuApp
    )
}

/**
 * Installer settings item
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun InstallerSettingsItem(
    title: String,
    entry: InstallerManager.Entry,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Build supporting text from description and availability reason
    val supportingText = remember(entry) {
        buildList {
            entry.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            entry.availability.reason?.let { add(context.getString(it)) }
        }.joinToString("\n")
    }

    RichSettingsItem(
        onClick = onClick,
        leadingContent = {
            if (entry.icon != null &&
                (entry.token == InstallerManager.Token.Shizuku ||
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
 * Dialog for selecting installer
 */
@Composable
private fun InstallerSelectionDialog(
    title: String,
    options: List<InstallerManager.Entry>,
    selected: InstallerManager.Token,
    onDismiss: () -> Unit,
    onConfirm: (InstallerManager.Token) -> Unit,
    onOpenShizuku: (() -> Boolean)?
) {
    val shizukuPromptReasons = remember {
        setOf(
            R.string.installer_status_shizuku_not_running,
            R.string.installer_status_shizuku_permission
        )
    }

    var currentSelection by remember(selected) { mutableStateOf(selected) }

    // Ensure valid selection when options change
    LaunchedEffect(options, selected) {
        val tokens = options.map { it.token }
        if (currentSelection !in tokens) {
            currentSelection = options.firstOrNull { it.availability.available }?.token
                ?: tokens.firstOrNull()
                        ?: selected
        }
    }

    val confirmEnabled = options.find { it.token == currentSelection }?.availability?.available != false

    // Localized strings for accessibility
    val selectedState = stringResource(R.string.selected)
    val notSelectedState = stringResource(R.string.not_selected)
    val disabledState = stringResource(R.string.disabled)

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onConfirm(currentSelection) },
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
            options.forEach { option ->
                val enabled = option.availability.available
                val isSelected = currentSelection == option.token
                val showShizukuAction = option.token == InstallerManager.Token.Shizuku &&
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
                    onSelect = { if (enabled) currentSelection = option.token },
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
        }
    }
}

/**
 * Individual installer option for dialog
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun InstallerOptionItem(
    option: InstallerManager.Entry,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    stateDescription: String
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    // Build description with availability reason for disabled items
    val description = remember(option, enabled) {
        buildList {
            option.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (!enabled) {
                option.availability.reason?.let { add(context.getString(it)) }
            }
        }.joinToString("\n")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                this.stateDescription = stateDescription
            },
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> colors.surfaceVariant.copy(alpha = 0.5f)
            selected -> colors.primaryContainer
            else -> Color.Transparent
        },
        tonalElevation = if (selected && enabled) 1.dp else 0.dp,
        onClick = onSelect,
        enabled = enabled
    ) {
        IconTextRow(
            modifier = Modifier.padding(16.dp),
            leadingContent = {
                if (option.icon != null &&
                    (option.token == InstallerManager.Token.Shizuku ||
                            option.token is InstallerManager.Token.Component)
                ) {
                    InstallerIconPreview(
                        drawable = option.icon,
                        selected = selected,
                        enabled = enabled
                    )
                } else {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.primary,
                            unselectedColor = colors.onSurfaceVariant,
                            disabledSelectedColor = colors.onSurface.copy(alpha = 0.38f),
                            disabledUnselectedColor = colors.onSurface.copy(alpha = 0.38f)
                        )
                    )
                }
            },
            title = option.label,
            description = description.takeIf { it.isNotBlank() },
            trailingContent = null,
            titleStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
            ),
            descriptionStyle = MaterialTheme.typography.bodySmall.copy(
                color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f)
            )
        )
    }
}

/**
 * Installer icon preview component
 */
@Composable
private fun InstallerIconPreview(
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
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.primary.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }
}

/**
 * Helper function to ensure valid selection and remove duplicates
 */
private fun ensureValidEntries(
    entries: List<InstallerManager.Entry>,
    token: InstallerManager.Token,
    installerManager: InstallerManager,
    installTarget: InstallerManager.InstallTarget
): List<InstallerManager.Entry> {
    // Remove duplicates based on component name for Component tokens
    val normalized = buildList {
        val seen = mutableSetOf<Any>()
        entries.forEach { entry ->
            val key = when (val entryToken = entry.token) {
                is InstallerManager.Token.Component -> entryToken.componentName
                else -> entryToken
            }
            if (seen.add(key)) add(entry)
        }
    }

    // Check if current token is in the list
    val tokenExists = token == InstallerManager.Token.Internal ||
            token == InstallerManager.Token.AutoSaved ||
            normalized.any { tokensEqual(it.token, token) }

    // If token not in list, try to add its description
    return if (tokenExists) {
        normalized
    } else {
        installerManager.describeEntry(token, installTarget)
            ?.let { normalized + it }
            ?: normalized
    }
}

/**
 * Helper function to compare installer tokens
 */
private fun tokensEqual(a: InstallerManager.Token?, b: InstallerManager.Token?): Boolean = when {
    a === b -> true
    a == null || b == null -> false
    a is InstallerManager.Token.Component && b is InstallerManager.Token.Component ->
        a.componentName == b.componentName
    else -> false
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
        InstallerManager.Token.Shizuku -> stringResource(R.string.installer_shizuku_name)
        InstallerManager.Token.AutoSaved -> stringResource(R.string.installer_auto_saved_name)
        else -> stringResource(R.string.installer_internal_name)
    }

    val reasonText = state.reason?.let { stringResource(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.installer_unavailable_title, installerName),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.installer_unavailable_message, installerName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (reasonText != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = reasonText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (state.canOpenApp && state.installerToken == InstallerManager.Token.Shizuku) {
                    Text(
                        text = stringResource(R.string.installer_unavailable_shizuku_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.canOpenApp) {
                        OutlinedButton(
                            onClick = onOpenApp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (state.installerToken) {
                                    InstallerManager.Token.Shizuku -> stringResource(R.string.installer_action_open_shizuku)
                                    else -> stringResource(R.string.open)
                                }
                            )
                        }
                    }

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }

                // Secondary actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }

                    TextButton(
                        onClick = onUseFallback,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.installer_use_standard))
                    }
                }
            }
        },
        dismissButton = null
    )
}
