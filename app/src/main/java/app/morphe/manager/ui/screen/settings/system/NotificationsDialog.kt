/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.net.toUri
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.advanced.NotificationPermissionDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.displayName
import app.morphe.manager.util.rememberAdaptiveFilePicker

/**
 * Consolidated notification settings: background update alerts and patcher completion sounds.
 */
@Composable
fun NotificationsDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = settingsViewModel.prefs

    val backgroundUpdateNotifications by prefs.backgroundUpdateNotifications.getAsState()
    val useManagerPrereleases by prefs.useManagerPrereleases.getAsState()
    val patchesPrereleaseIds by prefs.bundlePrereleasesEnabled.getAsState()
    val updateCheckInterval by prefs.updateCheckInterval.getAsState()

    val completionSound by prefs.patcherCompletionSound.getAsState()
    val successSoundUri by prefs.patcherSuccessSoundUri.getAsState()
    val errorSoundUri by prefs.patcherErrorSoundUri.getAsState()

    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val defaultLabel = stringResource(R.string.settings_system_notifications_sound_default)
    val ringtoneTitle = stringResource(R.string.settings_system_notifications_ringtone_picker_title)

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSourcePickerFor by remember { mutableStateOf<SoundKind?>(null) }
    var pendingRingtoneFor by remember { mutableStateOf<SoundKind?>(null) }
    var pendingFileFor by remember { mutableStateOf<SoundKind?>(null) }

    fun applySound(kind: SoundKind, uri: String) = when (kind) {
        SoundKind.Success -> settingsViewModel.setPatcherSuccessSoundUri(uri)
        SoundKind.Error -> settingsViewModel.setPatcherErrorSoundUri(uri)
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingRingtoneFor
        pendingRingtoneFor = null
        if (result.resultCode != Activity.RESULT_OK || target == null) return@rememberLauncherForActivityResult
        val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        applySound(target, picked?.toString().orEmpty())
    }

    val launchFilePicker = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("audio/*"),
        onResult = { uri ->
            val target = pendingFileFor
            pendingFileFor = null
            if (uri == null || target == null) return@rememberAdaptiveFilePicker
            // Content URIs from SAF are ephemeral by default; try to hold on to the grant so the
            // patcher can still open the file after a restart. File URIs from Morphe's picker
            // ignore this call
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            applySound(target, uri.toString())
        }
    )

    if (showPermissionDialog) {
        NotificationPermissionDialog(
            onDismissRequest = {
                settingsViewModel.onNotificationPermissionDismissed()
                showPermissionDialog = false
            },
            onPermissionResult = { granted ->
                settingsViewModel.onNotificationPermissionResult(
                    granted = granted,
                    useManagerPrereleases = useManagerPrereleases,
                    patchesPrereleaseIds = patchesPrereleaseIds,
                    updateCheckInterval = updateCheckInterval
                )
                showPermissionDialog = false
            }
        )
    }

    showSourcePickerFor?.let { target ->
        val currentUri = when (target) {
            SoundKind.Success -> successSoundUri
            SoundKind.Error -> errorSoundUri
        }
        SoundSourceDialog(
            onDismiss = { showSourcePickerFor = null },
            onPickRingtone = {
                showSourcePickerFor = null
                pendingRingtoneFor = target
                ringtoneLauncher.launch(
                    ringtonePickerIntent(currentUri.takeIf { it.isNotBlank() }?.toUri(), ringtoneTitle)
                )
            },
            onPickFile = {
                showSourcePickerFor = null
                pendingFileFor = target
                launchFilePicker()
            },
            onReset = {
                showSourcePickerFor = null
                applySound(target, "")
            },
            canReset = currentUri.isNotBlank()
        )
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_notifications),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            SettingsGroup {
                SettingsItem(
                    onClick = {
                        settingsViewModel.toggleBackgroundNotifications(
                            currentValue = backgroundUpdateNotifications,
                            useManagerPrereleases = useManagerPrereleases,
                            patchesPrereleaseIds = patchesPrereleaseIds,
                            updateCheckInterval = updateCheckInterval,
                            onShowPermissionDialog = { showPermissionDialog = true }
                        )
                    },
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.NotificationsActive) },
                    title = stringResource(R.string.settings_advanced_updates_background_notifications),
                    subtitle = stringResource(
                        if (settingsViewModel.hasGms)
                            R.string.settings_advanced_updates_background_notifications_description_fcm
                        else
                            R.string.settings_advanced_updates_background_notifications_description
                    ),
                    trailingContent = {
                        MorpheSwitch(
                            checked = backgroundUpdateNotifications,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription =
                                    if (backgroundUpdateNotifications) enabledState else disabledState
                            }
                        )
                    }
                )

                MorpheSettingsDivider()

                SettingsItem(
                    onClick = { settingsViewModel.setPatcherCompletionSound(!completionSound) },
                    leadingContent = { MorpheIcon(icon = Icons.AutoMirrored.Outlined.VolumeUp) },
                    title = stringResource(R.string.settings_system_patcher_completion_sound),
                    subtitle = stringResource(R.string.settings_system_patcher_completion_sound_description),
                    trailingContent = {
                        MorpheSwitch(
                            checked = completionSound,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (completionSound) enabledState else disabledState
                            }
                        )
                    }
                )
            }

            SettingsGroup {
                SoundSelectorItem(
                    title = stringResource(R.string.settings_system_notifications_success_sound),
                    icon = Icons.Outlined.CheckCircle,
                    currentUri = successSoundUri,
                    defaultLabel = defaultLabel,
                    enabled = completionSound,
                    onClick = { showSourcePickerFor = SoundKind.Success }
                )
                MorpheSettingsDivider()
                SoundSelectorItem(
                    title = stringResource(R.string.settings_system_notifications_error_sound),
                    icon = Icons.Outlined.ErrorOutline,
                    currentUri = errorSoundUri,
                    defaultLabel = defaultLabel,
                    enabled = completionSound,
                    onClick = { showSourcePickerFor = SoundKind.Error }
                )
            }
        }
    }
}

@Composable
private fun SoundSelectorItem(
    title: String,
    icon: ImageVector,
    currentUri: String,
    defaultLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val subtitle = remember(currentUri) {
        if (currentUri.isBlank()) defaultLabel else ringtoneDisplayName(context, currentUri) ?: currentUri
    }
    SettingsItem(
        onClick = { if (enabled) onClick() },
        title = title,
        subtitle = subtitle,
        leadingContent = { MorpheIcon(icon = icon) }
    )
}

@Composable
private fun SoundSourceDialog(
    onDismiss: () -> Unit,
    onPickRingtone: () -> Unit,
    onPickFile: () -> Unit,
    onReset: () -> Unit,
    canReset: Boolean
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_notifications_sound_picker_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.cancel),
                onPrimaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = onPickRingtone,
                title = stringResource(R.string.settings_system_notifications_sound_pick_ringtone),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.MusicNote) }
            )
            MorpheSettingsDivider()
            SettingsItem(
                onClick = onPickFile,
                title = stringResource(R.string.settings_system_notifications_sound_pick_file),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.FolderOpen) }
            )
            if (canReset) {
                MorpheSettingsDivider()
                SettingsItem(
                    onClick = onReset,
                    title = stringResource(R.string.settings_system_notifications_sound_reset),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.RestartAlt) }
                )
            }
        }
    }
}

private enum class SoundKind { Success, Error }

private fun ringtonePickerIntent(existing: Uri?, title: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
    }

private fun ringtoneDisplayName(context: Context, uriString: String): String? {
    val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        ?: uri.displayName(context.contentResolver)
}
