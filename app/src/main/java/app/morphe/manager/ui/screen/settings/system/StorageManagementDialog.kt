/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.morphe.manager.util.formatUsedFree
import app.morphe.manager.util.openAppDetailsSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Where the manager's storage goes and what clears it. The saved APKs of each kind open in place of
 * the overview, in the same dialog, see [ApkManagementPage].
 */
@Composable
fun DialogHostScope.StorageManagementDialog(
    onDismissRequest: () -> Unit,
    viewModel: StorageManagementViewModel = koinViewModel()
) {
    var apkPage by rememberSaveable { mutableStateOf<ApkManagementType?>(null) }
    DialogPages(page = apkPage, key = { it ?: "overview" }, modifier = Modifier.fillMaxSize()) { type ->
        if (type == null) {
            StorageOverview(viewModel = viewModel, onOpenApks = { apkPage = it }, onDismissRequest = onDismissRequest)
        } else {
            ApkManagementPage(type = type, onBack = { apkPage = null })
        }
    }
}

/** How much the manager holds and in what, with what clears it. Read again each time it shows. */
@Composable
private fun DialogHostScope.StorageOverview(
    viewModel: StorageManagementViewModel,
    onOpenApks: (ApkManagementType) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val nothingToClearText = stringResource(R.string.settings_system_storage_nothing_to_clear)
    val clearedTemplate = stringResource(R.string.settings_system_storage_cleared)

    val onCleared: (Long) -> Unit = { freed ->
        val message = if (freed <= 0L) nothingToClearText
        else clearedTemplate.format(context.formatBytes(freed))
        messages.show(message)
    }

    var showClearAllConfirm by remember { mutableStateOf(false) }
    // Incremented on manual refresh to re-key the histogram and replay its entry animation.
    var histogramNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    DialogHost(visible = showClearAllConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_system_storage_clear_all),
            message = stringResource(R.string.settings_system_storage_clear_all_confirm),
            primaryText = stringResource(R.string.clear),
            onConfirm = {
                showClearAllConfirm = false
                viewModel.clearAllCaches(onCleared)
            },
            onDismiss = { showClearAllConfirm = false },
            // Empty caches free nothing, so they are left out
            items = listOf(
                Triple(Icons.Outlined.CloudDownload, R.string.settings_system_storage_http_cache_title, stats.httpCacheBytes),
                Triple(Icons.Outlined.Share, R.string.settings_system_storage_installer_cache_title, stats.installerShareBytes),
                Triple(Icons.Outlined.Build, R.string.settings_system_storage_patcher_workspace_title, stats.patcherWorkspaceBytes),
                Triple(Icons.Outlined.HourglassEmpty, R.string.settings_system_storage_temporary_title, stats.temporaryBytes)
            ).filter { (_, _, bytes) -> bytes > 0 }.map { (icon, title, bytes) ->
                ConfirmItem(icon, stringResource(title), context.formatBytes(bytes))
            }
        )
    }

    // Nothing has been read until the first reading lands, and a real one always has room free
    val loading = stats == StorageStats.Empty

    ListDialog(
        onDismissRequest = onDismissRequest,
        header = {
            // Headed like the other lists, with how much Morphe holds against what is left free
            // where the subtitle goes, above the breakdown that scrolls under it
            val accent = MaterialTheme.colorScheme.primary
            ListDialogHeader(
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = Icons.Outlined.Storage, color = accent, modifier = modifier)
                },
                title = stringResource(R.string.settings_system_storage_management_title),
                subtitle = context.formatUsedFree(used = stats.appUsedBytes, free = stats.deviceFreeBytes),
                subtitleLoading = loading,
                accentColor = accent,
                alwaysShowBand = true
            ) {
                TitleAction(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    onClick = {
                        viewModel.refresh()
                        histogramNonce++
                    },
                    style = TitleActionStyle.Accent
                )
            }
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        DialogScrollColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            key(histogramNonce) {
                StorageHistogram(segments = stats.toSegments(), loading = loading)
            }

            SettingsGroup {
                DataRow(
                    icon = Icons.Outlined.Storage,
                    accentColor = StorageColors.OriginalApks,
                    title = stringResource(R.string.settings_system_original_apks_title),
                    description = stringResource(R.string.settings_system_original_apks_description),
                    onClick = { onOpenApks(ApkManagementType.ORIGINAL) }
                )
                DataRow(
                    icon = Icons.Outlined.Apps,
                    accentColor = StorageColors.PatchedApks,
                    title = stringResource(R.string.settings_system_patched_apks_title),
                    description = stringResource(R.string.settings_system_patched_apks_description),
                    onClick = { onOpenApks(ApkManagementType.PATCHED) }
                )
            }

            SettingsGroup {
                CacheActionRow(
                    icon = Icons.Outlined.CloudDownload,
                    accentColor = StorageColors.HttpCache,
                    title = stringResource(R.string.settings_system_storage_http_cache_title),
                    description = stringResource(R.string.settings_system_storage_http_cache_description),
                    bytes = stats.httpCacheBytes,
                    onClear = { viewModel.clearHttpCache(onCleared) }
                )
                CacheActionRow(
                    icon = Icons.Outlined.Share,
                    accentColor = StorageColors.InstallerShare,
                    title = stringResource(R.string.settings_system_storage_installer_cache_title),
                    description = stringResource(R.string.settings_system_storage_installer_cache_description),
                    bytes = stats.installerShareBytes,
                    onClear = { viewModel.clearInstallerShareCache(onCleared) }
                )
                CacheActionRow(
                    icon = Icons.Outlined.Build,
                    accentColor = StorageColors.PatcherWorkspace,
                    title = stringResource(R.string.settings_system_storage_patcher_workspace_title),
                    description = stringResource(R.string.settings_system_storage_patcher_workspace_description),
                    bytes = stats.patcherWorkspaceBytes,
                    onClear = { viewModel.clearPatcherWorkspace(onCleared) }
                )
                CacheActionRow(
                    icon = Icons.Outlined.HourglassEmpty,
                    accentColor = StorageColors.Temporary,
                    title = stringResource(R.string.settings_system_storage_temporary_title),
                    description = stringResource(R.string.settings_system_storage_temporary_description),
                    bytes = stats.temporaryBytes,
                    onClear = { viewModel.clearTemporary(onCleared) }
                )
                CacheActionRow(
                    icon = Icons.Outlined.DeleteSweep,
                    accentColor = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.settings_system_storage_clear_all),
                    description = stringResource(R.string.settings_system_storage_clear_all_description),
                    bytes = stats.totalCacheBytes,
                    onClear = { showClearAllConfirm = true }
                )
            }

            SettingsGroup {
                SettingsItem(
                    onClick = { context.openAppDetailsSettings() },
                    title = stringResource(R.string.settings_system_storage_open_app_info_title),
                    subtitle = stringResource(R.string.settings_system_storage_open_app_info_description),
                    leadingContent = { ThemedIcon(icon = Icons.AutoMirrored.Outlined.Launch) }
                )
            }
        }
    }
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
        leadingContent = { ThemedIcon(icon = icon, tint = accentColor) }
    )
}

@Composable
private fun CacheActionRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    bytes: Long,
    onClear: () -> Unit
) {
    SettingsItemCard(onClick = null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            IconTextRow(
                leadingContent = { ThemedIcon(icon = icon, tint = accentColor) },
                title = title,
                description = description
            )
            CardActionRow(
                actions = listOf(
                    CardAction(
                        icon = Icons.Outlined.DeleteSweep,
                        label = "${stringResource(R.string.clear)} (${LocalContext.current.formatBytes(bytes)})",
                        onClick = onClear,
                        enabled = bytes > 0L,
                        destructive = true
                    )
                )
            )
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
