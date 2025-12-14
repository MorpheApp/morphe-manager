package app.revanced.manager.ui.screen

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.AppInfo
import app.revanced.manager.ui.component.AppliedPatchBundleUi
import app.revanced.manager.ui.component.AppliedPatchesDialog
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.SegmentedButton
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.revanced.manager.ui.viewmodel.InstallResult
import app.revanced.manager.ui.viewmodel.MountWarningAction
import app.revanced.manager.ui.viewmodel.MountWarningReason
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstalledAppInfoScreen(
    onPatchClick: (packageName: String, selection: PatchSelection?) -> Unit,
    onBackClick: () -> Unit,
    viewModel: InstalledAppInfoViewModel
) {
    val context = LocalContext.current
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val prefs: PreferencesManager = koinInject()
    val bundleInfo by patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val bundleSources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val allowUniversalPatches by prefs.disableUniversalPatchCheck.getAsState()
    val exportFormat by prefs.patchedAppExportFormat.getAsState()
    var showAppliedPatchesDialog by rememberSaveable { mutableStateOf(false) }
    var showUniversalBlockedDialog by rememberSaveable { mutableStateOf(false) }
    var showLeaveInstallDialog by rememberSaveable { mutableStateOf(false) }
    val appliedSelection = viewModel.appliedPatches
    val isInstalledOnDevice = viewModel.isInstalledOnDevice
    val selectionPayload = viewModel.installedApp?.selectionPayload
    val savedBundleVersions = remember(selectionPayload) {
        selectionPayload?.bundles.orEmpty().associate { it.bundleUid to it.version }
    }

    val appliedBundles = remember(appliedSelection, bundleInfo, bundleSources, context, savedBundleVersions) {
        if (appliedSelection.isNullOrEmpty()) return@remember emptyList<AppliedPatchBundleUi>()

        runCatching {
            appliedSelection.entries.mapNotNull { (bundleUid, patches) ->
                if (patches.isEmpty()) return@mapNotNull null
                val patchNames = patches.toList().sorted()
                val info = bundleInfo[bundleUid]
                val source = bundleSources.firstOrNull { it.uid == bundleUid }
                val fallbackName = if (bundleUid == 0)
                    context.getString(R.string.patches_name_default)
                else
                    context.getString(R.string.patches_name_fallback)

                val title = source?.displayTitle
                    ?: info?.name
                    ?: "$fallbackName (#$bundleUid)"

                val patchInfos = info?.patches
                    ?.filter { it.name in patches }
                    ?.distinctBy { it.name }
                    ?.sortedBy { it.name }
                    ?: emptyList()

                val missingNames = patchNames.filterNot { patchName ->
                    patchInfos.any { it.name == patchName }
                }.distinct()

                AppliedPatchBundleUi(
                    uid = bundleUid,
                    title = title,
                    version = savedBundleVersions[bundleUid]?.takeUnless { it.isNullOrBlank() } ?: info?.version,
                    patchInfos = patchInfos,
                    fallbackNames = missingNames,
                    bundleAvailable = info != null
                )
            }.sortedBy { it.title }
        }.getOrElse { error ->
            Log.e(tag, "Failed to build applied bundle summary", error)
            emptyList()
        }
    }

    val globalUniversalPatchNames = remember(bundleInfo) {
        bundleInfo.values
            .flatMap { it.patches }
            .filter { it.compatiblePackages == null }
            .mapTo(mutableSetOf()) { it.name.lowercase() }
    }

    val appliedBundlesContainUniversal = remember(appliedBundles, globalUniversalPatchNames) {
        appliedBundles.any { bundle ->
            val hasByMetadata = bundle.patchInfos.any { it.compatiblePackages == null }
            val fallbackMatch = bundle.fallbackNames.any { name ->
                globalUniversalPatchNames.contains(name.lowercase())
            }
            hasByMetadata || fallbackMatch
        }
    }

    val appliedSelectionContainsUniversal = remember(appliedSelection, globalUniversalPatchNames) {
        appliedSelection?.values?.any { patches ->
            patches.any { globalUniversalPatchNames.contains(it.lowercase()) }
        } ?: false
    }

    val bundlesUsedSummary = remember(appliedBundles) {
        if (appliedBundles.isEmpty()) ""
        else appliedBundles.joinToString("\n") { bundle ->
            val version = bundle.version?.takeIf { it.isNotBlank() }
            if (version != null) "${bundle.title} ($version)" else bundle.title
        }
    }

    val exportSavedLauncher =
        rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
            viewModel.exportSavedApp(uri)
        }

    SideEffect {
        viewModel.onBackClick = onBackClick
    }

    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }

    if (showUninstallDialog)
        UninstallDialog(
            onDismiss = { showUninstallDialog = false },
            onConfirm = { viewModel.uninstall() }
        )

    if (showAppliedPatchesDialog && appliedSelection != null) {
        AppliedPatchesDialog(
            bundles = appliedBundles,
            onDismissRequest = { showAppliedPatchesDialog = false }
        )
    }

    if (showUniversalBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showUniversalBlockedDialog = false },
            confirmButton = {
                TextButton(onClick = { showUniversalBlockedDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.universal_patches_profile_blocked_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.universal_patches_app_blocked_description,
                        stringResource(R.string.universal_patches_safeguard)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    val installResult = viewModel.installResult
    if (installResult != null) {
        val (titleRes, message) = when (installResult) {
            is InstallResult.Success -> R.string.install_app_success to installResult.message
            is InstallResult.Failure -> R.string.install_app_fail_title to installResult.message
        }
        AlertDialog(
            onDismissRequest = viewModel::clearInstallResult,
            confirmButton = {
                TextButton(onClick = viewModel::clearInstallResult) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(titleRes)) },
            text = { Text(message) }
        )
    }

    viewModel.mountVersionMismatchMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMountVersionMismatch,
            confirmButton = {
                TextButton(onClick = viewModel::dismissMountVersionMismatch) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.mount_version_mismatch_title)) },
            text = { Text(message) }
        )
    }

    val mountWarning = viewModel.mountWarning
    if (mountWarning != null) {
        val (descriptionRes, titleRes) = when (mountWarning.reason) {
            MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP ->
                when (mountWarning.action) {
                    MountWarningAction.INSTALL -> R.string.installer_mount_warning_install
                    MountWarningAction.UPDATE -> R.string.installer_mount_warning_update
                    MountWarningAction.UNINSTALL -> R.string.installer_mount_warning_uninstall
                } to R.string.installer_mount_warning_title

            MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP ->
                when (mountWarning.action) {
                    MountWarningAction.INSTALL -> R.string.installer_mount_mismatch_install
                    MountWarningAction.UPDATE -> R.string.installer_mount_mismatch_update
                    MountWarningAction.UNINSTALL -> R.string.installer_mount_mismatch_uninstall
                } to R.string.installer_mount_mismatch_title
        }

        AlertDialog(
            onDismissRequest = viewModel::clearMountWarning,
            confirmButton = {
                TextButton(onClick = viewModel::clearMountWarning) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(titleRes)) },
            text = {
                Text(
                    text = stringResource(descriptionRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_info),
                scrollBehavior = scrollBehavior,
                onBackClick = {
                    if (viewModel.isInstalling) showLeaveInstallDialog = true else onBackClick()
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        BackHandler {
            if (viewModel.isInstalling) showLeaveInstallDialog = true else onBackClick()
        }
        ColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val installedApp = viewModel.installedApp ?: return@ColumnWithScrollbar

            AppInfo(
                appInfo = viewModel.appInfo,
                placeholderLabel = null
            ) {
                Text(installedApp.version, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

                if (installedApp.installType == InstallType.MOUNT) {
                    val mountStatusText = when (viewModel.mountOperation) {
                        InstalledAppInfoViewModel.MountOperation.UNMOUNTING -> stringResource(R.string.unmounting)
                        InstalledAppInfoViewModel.MountOperation.MOUNTING -> stringResource(R.string.mounting_ellipsis)
                        null -> if (viewModel.isMounted) {
                            stringResource(R.string.mounted)
                        } else {
                            stringResource(R.string.not_mounted)
                        }
                    }
                    Text(
                        text = mountStatusText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

    val exportMetadata = remember(
        installedApp.currentPackageName,
        installedApp.version,
        appliedBundles,
        viewModel.appInfo
    ) {
        val label = viewModel.appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: installedApp.currentPackageName
        val bundleVersions = appliedBundles.mapNotNull { it.version?.takeIf(String::isNotBlank) }
        val bundleNames = appliedBundles.map { it.title }.filter(String::isNotBlank)
        PatchedAppExportData(
            appName = label,
            packageName = installedApp.currentPackageName,
            appVersion = installedApp.version,
            patchBundleVersions = bundleVersions,
            patchBundleNames = bundleNames
        )
    }
    val exportFileName = remember(exportMetadata, exportFormat) {
        ExportNameFormatter.format(exportFormat, exportMetadata)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        val installType = installedApp.installType
        val hasRoot = viewModel.rootInstaller.hasRootAccess()
        val rootRequiredText = stringResource(R.string.installer_status_requires_root)

        if (viewModel.appInfo != null) {
            SegmentedButton(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = stringResource(R.string.open_app),
                onClick = viewModel::launch,
                enabled = isInstalledOnDevice
            )
        }

        when (installType) {
            InstallType.DEFAULT,
            InstallType.CUSTOM,
            InstallType.SHIZUKU -> {
                if (viewModel.hasSavedCopy) {
                    val installAction: () -> Unit = {
                        if (viewModel.primaryInstallerIsMount && installType != InstallType.MOUNT) {
                            val action = if (isInstalledOnDevice) MountWarningAction.UPDATE else MountWarningAction.INSTALL
                            viewModel.showMountWarning(action, MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP)
                        } else {
                            viewModel.installSavedApp()
                        }
                    }

                    SegmentedButton(
                        icon = if (isInstalledOnDevice) Icons.Outlined.Update else Icons.Outlined.InstallMobile,
                        text = if (isInstalledOnDevice) stringResource(R.string.update) else stringResource(R.string.install_saved_app),
                        onClick = installAction,
                        onLongClick = if (isInstalledOnDevice) viewModel::uninstall else null
                    )
                    SegmentedButton(
                        icon = Icons.Outlined.Save,
                        text = stringResource(R.string.export),
                        onClick = { exportSavedLauncher.launch(exportFileName) }
                    )
                } else {
                    if (isInstalledOnDevice) {
                        SegmentedButton(
                            icon = Icons.Outlined.Delete,
                            text = stringResource(R.string.uninstall),
                            onClick = viewModel::uninstall
                        )
                    }
                }
                SegmentedButton(
                    icon = Icons.Outlined.Update,
                    text = stringResource(R.string.repatch),
                    onClick = {
                        if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                            showUniversalBlockedDialog = true
                        } else {
                            onPatchClick(installedApp.originalPackageName, appliedSelection)
                        }
                    }
                )
                if (viewModel.hasSavedCopy) {
                    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
                    if (showDeleteConfirmation) {
                        ConfirmDialog(
                            onDismiss = { showDeleteConfirmation = false },
                            onConfirm = {
                                showDeleteConfirmation = false
                                viewModel.deleteSavedEntry()
                            },
                            title = stringResource(R.string.delete_saved_entry_title),
                            description = stringResource(R.string.delete_saved_entry_description),
                            icon = Icons.Outlined.Delete
                        )
                    }
                    SegmentedButton(
                        icon = Icons.Outlined.Delete,
                        text = stringResource(R.string.delete),
                        onClick = { showDeleteConfirmation = true }
                    )
                }
            }

            InstallType.MOUNT -> {
                var showUnmountConfirmation by rememberSaveable { mutableStateOf(false) }

                if (showUnmountConfirmation) {
                    ConfirmDialog(
                        onDismiss = { showUnmountConfirmation = false },
                        onConfirm = {
                            showUnmountConfirmation = false
                            viewModel.unmountSavedInstallation()
                        },
                        title = stringResource(R.string.unmount),
                        description = stringResource(R.string.unmount_confirm_description),
                        icon = Icons.Outlined.Circle
                    )
                }

                SegmentedButton(
                    icon = Icons.Outlined.SettingsBackupRestore,
                    text = if (viewModel.isMounted) stringResource(R.string.remount_saved_app) else stringResource(R.string.mount),
                    onClick = {
                        if (!hasRoot) {
                            Toast
                                .makeText(context, rootRequiredText, Toast.LENGTH_SHORT)
                                .show()
                            return@SegmentedButton
                        }
                        if (!viewModel.primaryInstallerIsMount) {
                            val action = if (viewModel.isMounted) MountWarningAction.UPDATE else MountWarningAction.INSTALL
                            viewModel.showMountWarning(action, MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP)
                        } else {
                            if (viewModel.isMounted) viewModel.remountSavedInstallation() else viewModel.mountOrUnmount()
                        }
                    },
                    onLongClick = if (viewModel.isMounted) {
                        {
                            if (!hasRoot) {
                                Toast
                                    .makeText(context, rootRequiredText, Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                if (!viewModel.primaryInstallerIsMount) {
                                    viewModel.showMountWarning(MountWarningAction.UNINSTALL, MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP)
                                } else {
                                    showUnmountConfirmation = true
                                }
                            }
                        }
                    } else null
                )
                SegmentedButton(
                    icon = Icons.Outlined.Save,
                    text = stringResource(R.string.export),
                    onClick = { exportSavedLauncher.launch(exportFileName) }
                )
                SegmentedButton(
                    icon = Icons.Outlined.Update,
                    text = stringResource(R.string.repatch),
                    onClick = {
                        if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                            showUniversalBlockedDialog = true
                        } else {
                            onPatchClick(installedApp.originalPackageName, appliedSelection)
                        }
                    }
                )
                var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
                if (showDeleteConfirmation) {
                    ConfirmDialog(
                        onDismiss = { showDeleteConfirmation = false },
                        onConfirm = {
                            showDeleteConfirmation = false
                            viewModel.deleteSavedEntry()
                        },
                        title = stringResource(R.string.delete_saved_entry_title),
                        description = stringResource(R.string.delete_saved_entry_description),
                        icon = Icons.Outlined.Delete
                    )
                }
                SegmentedButton(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(R.string.delete),
                    onClick = { showDeleteConfirmation = true }
                )
            }

            InstallType.SAVED -> {
                SegmentedButton(
                    icon = Icons.Outlined.Save,
                    text = stringResource(R.string.export),
                    onClick = { exportSavedLauncher.launch(exportFileName) }
                )

                var showSavedUninstallDialog by rememberSaveable { mutableStateOf(false) }
                if (showSavedUninstallDialog) {
                    val confirmTitle = stringResource(R.string.saved_app_uninstall_title)
                    val confirmDescription = stringResource(R.string.saved_app_uninstall_description)
                    ConfirmDialog(
                        onDismiss = { showSavedUninstallDialog = false },
                        onConfirm = {
                            showSavedUninstallDialog = false
                            viewModel.uninstallSavedInstallation()
                        },
                        title = confirmTitle,
                        description = confirmDescription,
                        icon = Icons.Outlined.Delete
                    )
                }

                val installText = if (isInstalledOnDevice) {
                    stringResource(R.string.update_saved_app)
                } else {
                    stringResource(R.string.install_saved_app)
                }
                SegmentedButton(
                    icon = Icons.Outlined.InstallMobile,
                    text = installText,
                    onClick = {
                        if (viewModel.primaryInstallerIsMount) {
                            val action = if (isInstalledOnDevice) MountWarningAction.UPDATE else MountWarningAction.INSTALL
                            viewModel.showMountWarning(action, MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP)
                        } else {
                            viewModel.installSavedApp()
                        }
                    },
                    onLongClick = if (isInstalledOnDevice) {
                        {
                            if (viewModel.primaryInstallerIsMount) {
                                viewModel.showMountWarning(MountWarningAction.UNINSTALL, MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP)
                            } else {
                                showSavedUninstallDialog = true
                            }
                        }
                    } else null
                )

                val deleteAction = viewModel::removeSavedApp
                val deleteTitle = stringResource(R.string.delete_saved_app_title)
                val deleteDescription = stringResource(R.string.delete_saved_app_description)
                val deleteLabel = stringResource(R.string.delete)
                var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
                if (showDeleteConfirmation) {
                    ConfirmDialog(
                        onDismiss = { showDeleteConfirmation = false },
                        onConfirm = {
                            showDeleteConfirmation = false
                            deleteAction()
                        },
                        title = deleteTitle,
                        description = deleteDescription,
                        icon = Icons.Outlined.Delete
                    )
                }
                SegmentedButton(
                    icon = Icons.Outlined.Delete,
                    text = deleteLabel,
                    onClick = { showDeleteConfirmation = true }
                )

                SegmentedButton(
                    icon = Icons.Outlined.Update,
                    text = stringResource(R.string.repatch),
                    onClick = {
                        if (!allowUniversalPatches && (appliedBundlesContainUniversal || appliedSelectionContainsUniversal)) {
                            showUniversalBlockedDialog = true
                        } else {
                            onPatchClick(installedApp.originalPackageName, appliedSelection)
                        }
                    }
                )
            }
        }
    }

            Column(
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                SettingsListItem(
                    modifier = Modifier.clickable(
                        enabled = appliedSelection != null
                    ) { showAppliedPatchesDialog = true },
                    headlineContent = stringResource(R.string.applied_patches),
                    supportingContent = when (val selection = appliedSelection) {
                        null -> stringResource(R.string.loading)
                        else -> {
                            val count = selection.values.sumOf { it.size }
                            pluralStringResource(
                                id = R.plurals.patch_count,
                                count,
                                count
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowRight,
                            contentDescription = stringResource(R.string.view_applied_patches)
                        )
                    }
                )

                SettingsListItem(
                    headlineContent = stringResource(R.string.package_name),
                    supportingContent = installedApp.currentPackageName
                )

                if (installedApp.originalPackageName != installedApp.currentPackageName) {
                    SettingsListItem(
                        headlineContent = stringResource(R.string.original_package_name),
                        supportingContent = installedApp.originalPackageName
                    )
                }

                SettingsListItem(
                    headlineContent = stringResource(R.string.install_type),
                    supportingContent = when (installedApp.installType) {
                        InstallType.MOUNT -> stringResource(R.string.install_type_mount_label)
                        InstallType.SHIZUKU -> stringResource(R.string.install_type_shizuku_label)
                        InstallType.DEFAULT, InstallType.CUSTOM -> when (viewModel.primaryInstallerToken) {
                            InstallerManager.Token.Internal -> stringResource(R.string.install_type_system_installer)
                            InstallerManager.Token.AutoSaved -> stringResource(R.string.install_type_mount_label)
                            is InstallerManager.Token.Component,
                            InstallerManager.Token.Shizuku,
                            InstallerManager.Token.None -> stringResource(R.string.install_type_custom_installer)
                        }

                        InstallType.SAVED -> stringResource(installedApp.installType.stringResource)
                    }
                )

                val bundleSummaryText = when {
                    appliedSelection == null -> stringResource(R.string.loading)
                    bundlesUsedSummary.isNotBlank() -> bundlesUsedSummary
                    else -> stringResource(R.string.no_patch_bundles_tracked)
                }
                SettingsListItem(
                    headlineContent = stringResource(R.string.patch_bundles_used),
                    supportingContent = bundleSummaryText
                )
            }
        }
    }

    if (showLeaveInstallDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveInstallDialog = false },
            title = { Text(stringResource(R.string.patcher_install_in_progress_title)) },
            text = {
                Text(
                    stringResource(R.string.patcher_install_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveInstallDialog = false
                        viewModel.cancelOngoingInstall()
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.patcher_install_in_progress_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveInstallDialog = false }) {
                    Text(stringResource(R.string.patcher_install_in_progress_stay))
                }
            }
        )
    }
}

@Composable
fun UninstallDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.unpatch_app)) },
    text = { Text(stringResource(R.string.unpatch_description)) },
    confirmButton = {
        TextButton(
            onClick = {
                onConfirm()
                onDismiss()
            }
        ) {
            Text(stringResource(android.R.string.ok))
        }
    },
    dismissButton = {
        TextButton(
            onClick = onDismiss
        ) {
            Text(stringResource(android.R.string.cancel))
        }
    }
)

