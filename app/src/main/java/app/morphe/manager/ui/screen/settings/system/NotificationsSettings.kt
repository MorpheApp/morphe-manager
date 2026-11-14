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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.displayName
import app.morphe.manager.util.openAppNotificationSettings
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.tryLaunch
import app.morphe.manager.worker.ScheduleInterval
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/**
 * Consolidated notification settings: background update alerts with their check interval, and patcher
 * completion sounds, on a page of their own, see [SettingsSubpage.NOTIFICATIONS].
 */
@Composable
fun NotificationsSettings(settingsViewModel: SettingsViewModel = koinViewModel()) {
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
    var showSourcePickerFor by remember { mutableStateOf<SoundKind?>(null) }
    // Saved, as the pickers are activities of their own and the manager may be recreated meanwhile
    var pendingRingtoneFor by rememberSaveable { mutableStateOf<SoundKind?>(null) }
    var pendingFileFor by rememberSaveable { mutableStateOf<SoundKind?>(null) }

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

    DialogHost(visible = showPermissionDialog) {
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

    DialogHost(target = showSourcePickerFor) { target ->
        val currentUri = when (target) {
            SoundKind.Success -> successSoundUri
            SoundKind.Error -> errorSoundUri
        }
        SoundSourceDialog(
            onDismiss = { showSourcePickerFor = null },
            onPickRingtone = {
                showSourcePickerFor = null
                pendingRingtoneFor = target
                val intent = ringtonePickerIntent(currentUri.takeIf { it.isNotBlank() }?.toUri(), ringtoneTitle)
                // Builds without a ringtone picker, as some TV ones, still have the file one
                if (!ringtoneLauncher.tryLaunch(intent)) {
                    pendingRingtoneFor = null
                    pendingFileFor = target
                    launchFilePicker()
                }
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

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_section_updates),
            icon = MaterialSymbols.Rounded.System_update
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
                    icon = MaterialSymbols.Rounded.Notifications_active,
                    title = stringResource(R.string.settings_advanced_updates_background_notifications),
                    subtitle = stringResource(
                        if (settingsViewModel.hasGms)
                            R.string.settings_advanced_updates_background_notifications_description_fcm
                        else
                            R.string.settings_advanced_updates_background_notifications_description
                    )
                )

                FoldingVisibility(visible = backgroundUpdateNotifications) {
                    Column {
                        // FCM pushes updates as they land, so the polling interval only matters without GMS
                        if (!settingsViewModel.hasGms) {
                            UpdateIntervalSetting(
                                selected = updateCheckInterval,
                                onSelect = settingsViewModel::selectUpdateInterval
                            )
                        }

                        // Manager and patch updates have channels of their own, tuned where Android keeps them
                        SettingsItem(
                            onClick = { context.openAppNotificationSettings() },
                            leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Category) },
                            title = stringResource(R.string.settings_system_notifications_categories),
                            subtitle = stringResource(R.string.settings_system_notifications_categories_description),
                            // Leaves for the system settings rather than going on within the manager
                            trailingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored) }
                        )
                    }
                }
            }

            // Polling is what a vendor's battery saver cuts short, which pushed updates are spared
            FoldingVisibility(visible = backgroundUpdateNotifications && !settingsViewModel.hasGms) {
                SettingsFooter(
                    text = stringResource(R.string.settings_advanced_update_interval_battery_warning),
                    tone = SemanticTone.Warning
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_section_patcher),
            icon = MaterialSymbols.Rounded.Build
        ) {
            SettingsGroup {
                SettingsSwitchItem(
                    checked = completionSound,
                    onToggle = { settingsViewModel.setPatcherCompletionSound(!completionSound) },
                    icon = MaterialSymbols.Rounded.Volume_up.autoMirrored,
                    title = stringResource(R.string.settings_system_patcher_completion_sound),
                    subtitle = stringResource(R.string.settings_system_patcher_completion_sound_description)
                )

                // Tones only play with the completion sound on, so picking one is offered under it
                FoldingVisibility(visible = completionSound) {
                    Column {
                        SoundSelectorItem(
                            title = stringResource(R.string.settings_system_notifications_success_sound),
                            icon = MaterialSymbols.Rounded.Check_circle,
                            currentUri = successSoundUri,
                            defaultLabel = defaultLabel,
                            onClick = { showSourcePickerFor = SoundKind.Success }
                        )
                        SoundSelectorItem(
                            title = stringResource(R.string.settings_system_notifications_error_sound),
                            icon = MaterialSymbols.Rounded.Error,
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
private fun DialogHostScope.SoundSourceDialog(
    onDismiss: () -> Unit,
    onPickRingtone: () -> Unit,
    onPickFile: () -> Unit,
    onReset: () -> Unit,
    canReset: Boolean
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_notifications_sound_picker_title),
        // Each source is picked from the list itself, so all that is left here is to leave
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = onPickRingtone,
                title = stringResource(R.string.settings_system_notifications_sound_pick_ringtone),
                leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Music_note) }
            )
            SettingsItem(
                onClick = onPickFile,
                title = stringResource(R.string.settings_system_notifications_sound_pick_file),
                leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Folder_open) }
            )
            if (canReset) {
                SettingsItem(
                    onClick = onReset,
                    title = stringResource(R.string.settings_system_notifications_sound_reset),
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Restart_alt) }
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
fun DialogHostScope.NotificationPermissionDialog(
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

/** How often the background update check runs, picked in place among the few there are. */
@Composable
private fun UpdateIntervalSetting(
    selected: ScheduleInterval,
    onSelect: (ScheduleInterval) -> Unit
) {
    val title = stringResource(R.string.settings_advanced_update_interval)
    SearchableSetting(listOf(title)) {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            IconTextRow(
                leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Schedule) },
                title = title
            )
            OptionGroup(items = ScheduleInterval.entries) { interval, shape, source, itemModifier ->
                ModernIconOptionCard(
                    selected = interval == selected,
                    onClick = { onSelect(interval) },
                    icon = interval.icon,
                    label = stringResource(interval.labelResId),
                    modifier = itemModifier,
                    compact = true,
                    shape = shape,
                    interactionSource = source
                )
            }
        }
    }
}

private val ScheduleInterval.icon: ImageVector
    get() = when (this) {
        ScheduleInterval.HOURLY -> MaterialSymbols.Rounded.Timer
        ScheduleInterval.DAILY -> MaterialSymbols.Rounded.Today
        ScheduleInterval.WEEKLY -> MaterialSymbols.Rounded.Date_range
        ScheduleInterval.MONTHLY -> MaterialSymbols.Rounded.Calendar_month
    }
