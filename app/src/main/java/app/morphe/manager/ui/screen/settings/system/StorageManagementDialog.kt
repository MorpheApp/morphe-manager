/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.repository.StorageStats
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.StorageManagementViewModel
import app.morphe.manager.util.formatBytes
import app.morphe.manager.util.toast
import org.koin.androidx.compose.koinViewModel

@Composable
fun StorageManagementDialog(
    onDismissRequest: () -> Unit,
    viewModel: StorageManagementViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val nothingToClearText = stringResource(R.string.settings_system_storage_nothing_to_clear)
    val clearedTemplate = stringResource(R.string.settings_system_storage_cleared)

    val onCleared: (Long) -> Unit = { freed ->
        val message = if (freed <= 0L) nothingToClearText
        else clearedTemplate.format(formatBytes(freed))
        context.toast(message)
    }

    var apkDialog by remember { mutableStateOf<ApkManagementType?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    // Incremented on manual refresh to re-key the histogram and replay its entry animation.
    var histogramNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    apkDialog?.let { type ->
        ApkManagementDialog(
            type = type,
            onDismissRequest = {
                apkDialog = null
                viewModel.refresh()
            }
        )
    }

    if (showClearAllConfirm) {
        ClearCachesConfirmationDialog(
            totalBytes = stats.totalCacheBytes,
            onDismiss = { showClearAllConfirm = false },
            onConfirm = {
                showClearAllConfirm = false
                viewModel.clearAllCaches(onCleared)
            }
        )
    }

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_system_storage_management_title),
        padding = DialogPadding.Compact,
        titleTrailingContent = {
            DialogTitleAction(
                icon = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = {
                    viewModel.refresh()
                    histogramNonce++
                }
            )
        },
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            key(histogramNonce) {
                StorageHistogram(
                    used = stats.appUsedBytes,
                    deviceFreeBytes = stats.deviceFreeBytes,
                    segments = stats.toSegments()
                )
            }

            SettingsGroup {
                DataRow(
                    icon = Icons.Outlined.Storage,
                    accentColor = StorageColors.OriginalApks,
                    title = stringResource(R.string.settings_system_original_apks_title),
                    description = stringResource(R.string.settings_system_original_apks_description),
                    onClick = { apkDialog = ApkManagementType.ORIGINAL }
                )
                MorpheSettingsDivider()
                DataRow(
                    icon = Icons.Outlined.Apps,
                    accentColor = StorageColors.PatchedApks,
                    title = stringResource(R.string.settings_system_patched_apks_title),
                    description = stringResource(R.string.settings_system_patched_apks_description),
                    onClick = { apkDialog = ApkManagementType.PATCHED }
                )
            }

            SettingsGroup {
                CacheActionRow(
                    icon = Icons.Outlined.CloudDownload,
                    accentColor = StorageColors.HttpCache,
                    title = stringResource(R.string.settings_system_storage_http_cache_title),
                    description = stringResource(R.string.settings_system_storage_http_cache_description),
                    enabled = stats.httpCacheBytes > 0L,
                    onClear = { viewModel.clearHttpCache(onCleared) }
                )
                MorpheSettingsDivider()
                CacheActionRow(
                    icon = Icons.Outlined.Share,
                    accentColor = StorageColors.InstallerShare,
                    title = stringResource(R.string.settings_system_storage_installer_cache_title),
                    description = stringResource(R.string.settings_system_storage_installer_cache_description),
                    enabled = stats.installerShareBytes > 0L,
                    onClear = { viewModel.clearInstallerShareCache(onCleared) }
                )
                MorpheSettingsDivider()
                CacheActionRow(
                    icon = Icons.Outlined.Build,
                    accentColor = StorageColors.PatcherWorkspace,
                    title = stringResource(R.string.settings_system_storage_patcher_workspace_title),
                    description = stringResource(R.string.settings_system_storage_patcher_workspace_description),
                    enabled = stats.patcherWorkspaceBytes > 0L,
                    onClear = { viewModel.clearPatcherWorkspace(onCleared) }
                )
                MorpheSettingsDivider()
                CacheActionRow(
                    icon = Icons.Outlined.HourglassEmpty,
                    accentColor = StorageColors.Temporary,
                    title = stringResource(R.string.settings_system_storage_temporary_title),
                    description = stringResource(R.string.settings_system_storage_temporary_description),
                    enabled = stats.temporaryBytes > 0L,
                    onClear = { viewModel.clearTemporary(onCleared) }
                )
                MorpheSettingsDivider()
                CacheActionRow(
                    icon = Icons.Outlined.DeleteSweep,
                    accentColor = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.settings_system_storage_clear_all),
                    description = stringResource(R.string.settings_system_storage_clear_all_description),
                    enabled = stats.totalCacheBytes > 0L,
                    onClear = { showClearAllConfirm = true }
                )
            }

            SettingsGroup {
                SettingsItem(
                    onClick = { openAndroidAppStorage(context) },
                    title = stringResource(R.string.settings_system_storage_open_app_info_title),
                    subtitle = stringResource(R.string.settings_system_storage_open_app_info_description),
                    leadingContent = { MorpheIcon(icon = Icons.AutoMirrored.Outlined.Launch) }
                )
            }
        }
    }
}

private fun openAndroidAppStorage(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) { /* OEM without the standard app info screen */ }
}

@Composable
private fun DataRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    SettingsItem(
        onClick = onClick,
        title = title,
        subtitle = description,
        leadingContent = { MorpheIcon(icon = icon, tint = accentColor) }
    )
}

@Composable
private fun CacheActionRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    enabled: Boolean,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MorpheDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
    ) {
        IconTextRow(
            leadingContent = { MorpheIcon(icon = icon, tint = accentColor) },
            title = title,
            description = description
        )
        CardActionRow(
            actions = listOf(
                CardAction(
                    icon = Icons.Outlined.DeleteSweep,
                    label = stringResource(R.string.clear),
                    onClick = onClear,
                    enabled = enabled,
                    destructive = true
                )
            )
        )
    }
}

@Composable
private fun ClearCachesConfirmationDialog(
    totalBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_storage_clear_all),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.settings_system_storage_clear_all),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = stringResource(R.string.settings_system_storage_clear_all_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )
            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_apks_size, formatBytes(totalBytes))
            ) {
                DeleteListItem(
                    icon = Icons.Outlined.CloudDownload,
                    text = stringResource(R.string.settings_system_storage_http_cache_title)
                )
                DeleteListItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.settings_system_storage_installer_cache_title)
                )
                DeleteListItem(
                    icon = Icons.Outlined.Build,
                    text = stringResource(R.string.settings_system_storage_patcher_workspace_title)
                )
                DeleteListItem(
                    icon = Icons.Outlined.HourglassEmpty,
                    text = stringResource(R.string.settings_system_storage_temporary_title)
                )
            }
        }
    }
}

@Composable
private fun StorageStats.toSegments(): List<StorageSegment> {
    val originalLabel = stringResource(R.string.settings_system_original_apks_title)
    val patchedLabel = stringResource(R.string.settings_system_patched_apks_title)
    val bundlesLabel = stringResource(R.string.settings_system_storage_patch_bundles_title)
    val keystoreLabel = stringResource(R.string.settings_system_storage_keystore_title)
    val appDataLabel = stringResource(R.string.settings_system_storage_app_data_title)
    val httpLabel = stringResource(R.string.settings_system_storage_http_cache_title)
    val shareLabel = stringResource(R.string.settings_system_storage_installer_cache_title)
    val patcherLabel = stringResource(R.string.settings_system_storage_patcher_workspace_title)
    val tempLabel = stringResource(R.string.settings_system_storage_temporary_title)
    return listOf(
        StorageSegment("original", originalLabel, originalApksBytes, StorageColors.OriginalApks),
        StorageSegment("patched", patchedLabel, patchedApksBytes, StorageColors.PatchedApks),
        StorageSegment("bundles", bundlesLabel, patchBundlesBytes, StorageColors.PatchBundles),
        StorageSegment("keystore", keystoreLabel, keystoreBytes, StorageColors.Keystore),
        StorageSegment("appdata", appDataLabel, appDataBytes, StorageColors.AppData),
        StorageSegment("http", httpLabel, httpCacheBytes, StorageColors.HttpCache),
        StorageSegment("share", shareLabel, installerShareBytes, StorageColors.InstallerShare),
        StorageSegment("patcher", patcherLabel, patcherWorkspaceBytes, StorageColors.PatcherWorkspace),
        StorageSegment("temp", tempLabel, temporaryBytes, StorageColors.Temporary)
    )
}
