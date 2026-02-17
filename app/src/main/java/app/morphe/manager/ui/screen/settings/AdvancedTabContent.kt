/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.settings.advanced.GitHubPatSettingsItem
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.worker.UpdateCheckWorker
import kotlinx.coroutines.launch

/**
 * Advanced tab content
 */
@Composable
fun AdvancedTabContent(
    usePrereleases: State<Boolean>,
    patchOptionsViewModel: PatchOptionsViewModel,
    homeViewModel: HomeViewModel,
    prefs: PreferencesManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()
    val backgroundUpdateNotifications by prefs.backgroundUpdateNotifications.getAsState()

    // Dialog state for notification permission (Android 13+)
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    // Check if POST_NOTIFICATIONS permission is already granted
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Auto-granted on Android < 13
        }
    }

    // Dialog state for expert mode
    var showExpertModeNotice by remember { mutableStateOf(false) }
    var showExpertModeDialog by remember { mutableStateOf(false) }
    var previousExpertMode by remember { mutableStateOf(useExpertMode) }
    val gitHubPat by prefs.gitHubPat.getAsState()
    val includeGitHubPatInExports by prefs.includeGitHubPatInExports.getAsState()

    // Detect expert mode changes
    LaunchedEffect(useExpertMode) {
        if (useExpertMode && !previousExpertMode) {
            // Expert mode was just enabled
            showExpertModeNotice = true
        }
        previousExpertMode = useExpertMode
    }

    // Localized strings for accessibility
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    // Notification permission dialog (Android 13+)
    if (showNotificationPermissionDialog) {
        NotificationPermissionDialog(
            onDismissRequest = {
                // User cancelled — revert the preference back to OFF
                scope.launch { prefs.backgroundUpdateNotifications.update(false) }
                showNotificationPermissionDialog = false
            },
            onPermissionResult = { granted ->
                showNotificationPermissionDialog = false
                if (granted) {
                    // Permission granted — schedule the worker
                    UpdateCheckWorker.schedule(context)
                } else {
                    // Permission denied — revert the preference back to OFF
                    scope.launch { prefs.backgroundUpdateNotifications.update(false) }
                }
            }
        )
    }

    // Expert mode confirmation dialog
    if (showExpertModeDialog) {
        ExpertModeConfirmationDialog(
            onDismiss = { showExpertModeDialog = false },
            onConfirm = {
                scope.launch {
                    prefs.useExpertMode.update(true)
                }
                showExpertModeDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Updates section
        SectionTitle(
            text = stringResource(R.string.settings_advanced_updates),
            icon = Icons.Outlined.Update
        )

        // Use prereleases toggle
        RichSettingsItem(
            onClick = {
                val newValue = !usePrereleases.value
                scope.launch {
                    prefs.usePatchesPrereleases.update(newValue)
                    prefs.useManagerPrereleases.update(newValue)
                    prefs.managerAutoUpdates.update(newValue)
                    homeViewModel.updateMorpheBundleWithChangelogClear()
                    homeViewModel.checkForManagerUpdates()
                    patchOptionsViewModel.refresh()
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Science)
            },
            title = stringResource(R.string.settings_advanced_updates_use_prereleases),
            subtitle = stringResource(R.string.settings_advanced_updates_use_prereleases_description),
            trailingContent = {
                Switch(
                    checked = usePrereleases.value,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription = if (usePrereleases.value) enabledState else disabledState
                    }
                )
            }
        )

        // Background update notifications toggle
        RichSettingsItem(
            onClick = {
                val newValue = !backgroundUpdateNotifications
                if (newValue && !hasNotificationPermission()) {
                    // Need to request permission first - save preference optimistically,
                    // dialog will revert it if the user denies
                    scope.launch { prefs.backgroundUpdateNotifications.update(true) }
                    showNotificationPermissionDialog = true
                } else {
                    scope.launch {
                        prefs.backgroundUpdateNotifications.update(newValue)
                        if (newValue) UpdateCheckWorker.schedule(context)
                        else UpdateCheckWorker.cancel(context)
                    }
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.NotificationsActive)
            },
            title = stringResource(R.string.settings_advanced_updates_background_notifications),
            subtitle = stringResource(R.string.settings_advanced_updates_background_notifications_description),
            trailingContent = {
                Switch(
                    checked = backgroundUpdateNotifications,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription =
                            if (backgroundUpdateNotifications) enabledState else disabledState
                    }
                )
            }
        )

        // Expert settings section
        SectionTitle(
            text = stringResource(R.string.settings_advanced_expert),
            icon = Icons.Outlined.Engineering
        )

        RichSettingsItem(
            onClick = {
                if (!useExpertMode) {
                    // Show confirmation dialog when enabling expert mode
                    showExpertModeDialog = true
                } else {
                    // Disable without confirmation
                    scope.launch {
                        prefs.useExpertMode.update(false)
                    }
                }
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Psychology)
            },
            title = stringResource(R.string.settings_advanced_expert_mode),
            subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
            trailingContent = {
                Switch(
                    checked = useExpertMode,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription = if (useExpertMode) enabledState else disabledState
                    }
                )
            }
        )

        Crossfade(
            targetState = useExpertMode,
            label = "expert_mode_crossfade"
        ) { expertMode ->
            if (expertMode) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // GitHub PAT
                    GitHubPatSettingsItem(
                        currentPat = gitHubPat,
                        currentIncludeInExport = includeGitHubPatInExports,
                        onSave = { pat, include ->
                            scope.launch {
                                prefs.gitHubPat.update(pat)
                                prefs.includeGitHubPatInExports.update(include)
                            }
                        }
                    )

                    // Strip unused native libraries
                    RichSettingsItem(
                        onClick = {
                            scope.launch {
                                prefs.stripUnusedNativeLibs.update(!stripUnusedNativeLibs)
                            }
                        },
                        showBorder = true,
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.LayersClear)
                        },
                        title = stringResource(R.string.settings_advanced_strip_unused_libs),
                        subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description),
                        trailingContent = {
                            Switch(
                                checked = stripUnusedNativeLibs,
                                onCheckedChange = null,
                                modifier = Modifier.semantics {
                                    stateDescription =
                                        if (stripUnusedNativeLibs) enabledState else disabledState
                                }
                            )
                        }
                    )

                    // Expert mode notice shown once after enabling
                    if (showExpertModeNotice) {
                        InfoBadge(
                            icon = Icons.Outlined.Info,
                            text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
                            style = InfoBadgeStyle.Warning,
                            isExpanded = true
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Patch Options (Simple mode only)
                    SectionTitle(
                        text = stringResource(R.string.settings_advanced_patch_options),
                        icon = Icons.Outlined.Tune
                    )

                    PatchOptionsSection(
                        patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                        patchOptionsViewModel = patchOptionsViewModel,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }
    }
}

/**
 * Dialog to confirm enabling Expert mode
 */
@Composable
private fun ExpertModeConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_expert_mode_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.enable),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(R.string.settings_advanced_expert_mode_dialog_message),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Dialog shown when the user enables background update notifications on Android 13+
 * (API 33+), where [Manifest.permission.POST_NOTIFICATIONS] must be requested at runtime.
 *
 * On older Android versions this dialog is never shown - the permission is granted
 * automatically at install time.
 * Show this dialog when 'backgroundUpdateNotifications' is toggled ON and the
 * permission has not yet been granted. After the user taps "Allow", the system permission
 * dialog is displayed. If permission is denied, the preference is reverted to OFF.
 *
 * @param onDismissRequest Called when the dialog is dismissed without granting permission.
 *                         Should revert the preference back to OFF.
 * @param onPermissionResult Called with the result of the system permission request.
 *                           true = granted, false = denied.
 */
@Composable
fun NotificationPermissionDialog(
    onDismissRequest: () -> Unit,
    onPermissionResult: (granted: Boolean) -> Unit
) {
    // Launcher for the system POST_NOTIFICATIONS permission dialog
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult
    )

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.notification_permission_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.allow),
                onPrimaryClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Pre-API 33: permission auto-granted, treat as success
                        onPermissionResult(true)
                    }
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismissRequest
            )
        }
    ) {
        Text(
            text = stringResource(R.string.notification_permission_dialog_description),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
