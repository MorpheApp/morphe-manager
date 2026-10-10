/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.displayName
import app.morphe.manager.util.openAppNotificationSettings
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.worker.UpdateCheckInterval
import kotlin.math.roundToInt

/**
 * Consolidated notification settings: background update alerts with their check interval, and patcher
 * completion sounds.
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

    val defaultLabel = stringResource(R.string.settings_system_notifications_sound_default)
    val ringtoneTitle = stringResource(R.string.settings_system_notifications_ringtone_picker_title)

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
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

    if (showIntervalDialog) {
        UpdateCheckIntervalDialog(
            currentInterval = updateCheckInterval,
            onIntervalSelected = {
                settingsViewModel.selectUpdateInterval(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_notifications),
        description = stringResource(R.string.settings_system_notifications_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            SettingsGroup {
                SettingsSwitchItem(
                    checked = backgroundUpdateNotifications,
                    onToggle = {
                        settingsViewModel.toggleBackgroundNotifications(
                            currentValue = backgroundUpdateNotifications,
                            useManagerPrereleases = useManagerPrereleases,
                            patchesPrereleaseIds = patchesPrereleaseIds,
                            updateCheckInterval = updateCheckInterval,
                            onShowPermissionDialog = { showPermissionDialog = true }
                        )
                    },
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.settings_advanced_updates_background_notifications),
                    subtitle = stringResource(
                        if (settingsViewModel.hasGms)
                            R.string.settings_advanced_updates_background_notifications_description_fcm
                        else
                            R.string.settings_advanced_updates_background_notifications_description
                    )
                )

                AnimatedVisibility(
                    visible = backgroundUpdateNotifications,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Column {
                        // FCM pushes updates as they land, so the polling interval only matters without GMS
                        if (!settingsViewModel.hasGms) {
                            SettingsDivider()

                            SettingsItem(
                                onClick = { showIntervalDialog = true },
                                leadingContent = { ThemedIcon(icon = Icons.Outlined.Schedule) },
                                title = stringResource(R.string.settings_advanced_update_interval),
                                subtitle = stringResource(updateCheckInterval.labelResId)
                            )
                        }

                        SettingsDivider()

                        // Manager and patch updates have channels of their own, tuned where Android keeps them
                        SettingsItem(
                            onClick = { context.openAppNotificationSettings() },
                            leadingContent = { ThemedIcon(icon = Icons.AutoMirrored.Outlined.Launch) },
                            title = stringResource(R.string.settings_system_notifications_categories),
                            subtitle = stringResource(R.string.settings_system_notifications_categories_description)
                        )
                    }
                }
            }

            SettingsGroup {
                SettingsSwitchItem(
                    checked = completionSound,
                    onToggle = { settingsViewModel.setPatcherCompletionSound(!completionSound) },
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = stringResource(R.string.settings_system_patcher_completion_sound),
                    subtitle = stringResource(R.string.settings_system_patcher_completion_sound_description)
                )

                // Tones only play with the completion sound on, so picking one is offered under it
                AnimatedVisibility(
                    visible = completionSound,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Column {
                        SettingsDivider()
                        SoundSelectorItem(
                            title = stringResource(R.string.settings_system_notifications_success_sound),
                            icon = Icons.Outlined.CheckCircle,
                            currentUri = successSoundUri,
                            defaultLabel = defaultLabel,
                            onClick = { showSourcePickerFor = SoundKind.Success }
                        )
                        SettingsDivider()
                        SoundSelectorItem(
                            title = stringResource(R.string.settings_system_notifications_error_sound),
                            icon = Icons.Outlined.ErrorOutline,
                            currentUri = errorSoundUri,
                            defaultLabel = defaultLabel,
                            onClick = { showSourcePickerFor = SoundKind.Error }
                        )
                    }
                }
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
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val subtitle = remember(currentUri) {
        if (currentUri.isBlank()) defaultLabel else ringtoneDisplayName(context, currentUri) ?: currentUri
    }
    SettingsItem(
        onClick = onClick,
        title = title,
        subtitle = subtitle,
        leadingContent = { ThemedIcon(icon = icon) }
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_notifications_sound_picker_title),
        footer = {
            AppDialogButtonRow(
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
                leadingContent = { ThemedIcon(icon = Icons.Outlined.MusicNote) }
            )
            SettingsDivider()
            SettingsItem(
                onClick = onPickFile,
                title = stringResource(R.string.settings_system_notifications_sound_pick_file),
                leadingContent = { ThemedIcon(icon = Icons.Outlined.FolderOpen) }
            )
            if (canReset) {
                SettingsDivider()
                SettingsItem(
                    onClick = onReset,
                    title = stringResource(R.string.settings_system_notifications_sound_reset),
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.RestartAlt) }
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

/**
 * Dialog shown on Android 13+ when the user enables background notifications
 * and [Manifest.permission.POST_NOTIFICATIONS] has not yet been granted.
 */
@Composable
fun NotificationPermissionDialog(
    onDismissRequest: () -> Unit,
    onPermissionResult: (granted: Boolean) -> Unit,
    title: String = stringResource(R.string.notification_permission_dialog_title),
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult
    )

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        description = stringResource(R.string.notification_permission_dialog_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.allow),
                onPrimaryClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onPermissionResult(true)
                    }
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismissRequest
            )
        }
    )
}

/** Discrete-slider dialog to pick how often the background update check runs. */
@Composable
private fun UpdateCheckIntervalDialog(
    currentInterval: UpdateCheckInterval,
    onIntervalSelected: (UpdateCheckInterval) -> Unit,
    onDismiss: () -> Unit
) {
    val title = stringResource(R.string.settings_advanced_update_interval_dialog_title)
    val chipSubtitle = stringResource(R.string.settings_advanced_update_interval_chip_subtitle)
    val entries = UpdateCheckInterval.entries
    val sliderState = rememberSliderState(
        value = entries.indexOf(currentInterval).toFloat(),
        steps = entries.size - 2, // n entries → n-2 internal steps
        trackRange = 0f..(entries.size - 1).toFloat()
    )
    val selectedInterval = entries[sliderState.value.roundToInt().coerceIn(entries.indices)]

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onIntervalSelected(selectedInterval) },
                primaryIcon = Icons.Outlined.Check,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current value chip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = CardBorder.tinted(MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(selectedInterval.labelResId),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = dialogTextColor()
                    )
                    Text(
                        text = chipSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSecondaryTextColor(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    state = sliderState,
                    onValueChange = { sliderState.value = it },
                    modifier = Modifier.fillMaxWidth()
                )

                SliderScaleLabels(
                    start = stringResource(entries.first().labelResId),
                    end = stringResource(entries.last().labelResId)
                )
            }

            // Battery optimization warning
            Notice(
                text = stringResource(R.string.settings_advanced_update_interval_battery_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.BatteryAlert,
                density = NoticeDensity.Compact
            )
        }
    }
}
