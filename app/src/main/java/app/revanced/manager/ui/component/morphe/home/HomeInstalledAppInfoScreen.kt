package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.AppLabel
import app.revanced.manager.ui.component.AppliedPatchBundleUi
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.InstallResult
import app.revanced.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.revanced.manager.ui.viewmodel.InstalledAppInfoViewModel.MountOperation
import app.revanced.manager.ui.viewmodel.MountWarningReason
import app.revanced.manager.util.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Navigation event sealed class for cleaner navigation handling
 */
sealed class InstalledAppInfoNavEvent {
    data class NavigateToPatcher(
        val packageName: String,
        val version: String,
        val filePath: String,
        val patches: PatchSelection,
        val options: Options
    ) : InstalledAppInfoNavEvent()

    data class TriggerPatchFlow(val originalPackageName: String) : InstalledAppInfoNavEvent()
    data object NavigateBack : InstalledAppInfoNavEvent()
}

/**
 * Simplified HomeInstalledAppInfoScreen
 * Uses single navigation callback instead of multiple specific callbacks
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeInstalledAppInfoScreen(
    packageName: String,
    onNavigationEvent: (InstalledAppInfoNavEvent) -> Unit,
    viewModel: InstalledAppInfoViewModel = koinViewModel { parametersOf(packageName) }
) {
    val context = LocalContext.current
    val installedApp = viewModel.installedApp
    val appInfo = viewModel.appInfo
    val appliedPatches = viewModel.appliedPatches

    // Dialog states
    var showAppliedPatchesDialog by remember { mutableStateOf(false) }
    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMountWarningDialog by remember { mutableStateOf(false) }

    // Collect bundle data
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val bundleSources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val availablePatches by patchBundleRepository.bundleInfoFlow
        .collectAsStateWithLifecycle(emptyMap())
        .let { remember(it.value) { derivedStateOf { it.value.values.sumOf { bundle -> bundle.patches.size } } } }

    // Build applied bundles summary
    val selectionPayload = installedApp?.selectionPayload
    val savedBundleVersions = remember(selectionPayload) {
        selectionPayload?.bundles.orEmpty().associate { it.bundleUid to it.version }
    }

    val appliedBundles = remember(appliedPatches, bundleInfo, bundleSources, context, savedBundleVersions) {
        if (appliedPatches.isNullOrEmpty()) return@remember emptyList()

        runCatching {
            appliedPatches.entries.mapNotNull { (bundleUid, patches) ->
                if (patches.isEmpty()) return@mapNotNull null
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

                val missingNames = patches.toList().sorted().filterNot { patchName ->
                    patchInfos.any { it.name == patchName }
                }.distinct()

                AppliedPatchBundleUi(
                    uid = bundleUid,
                    title = title,
                    version = savedBundleVersions[bundleUid]?.takeUnless { it.isBlank() } ?: info?.version,
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

    val bundlesUsedSummary = remember(appliedBundles) {
        if (appliedBundles.isEmpty()) ""
        else appliedBundles.joinToString("\n") { bundle ->
            val version = bundle.version?.takeIf { it.isNotBlank() }
            if (version != null) "${bundle.title} ($version)" else bundle.title
        }
    }

    // Export functionality
    val exportFormat by viewModel.exportFormat.collectAsStateWithLifecycle()
    val exportMetadata = remember(
        installedApp?.currentPackageName,
        appInfo?.versionName,
        appliedBundles,
        appInfo
    ) {
        if (installedApp == null) return@remember null
        val label = appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: installedApp.currentPackageName
        val bundleVersions = appliedBundles.mapNotNull { it.version?.takeIf(String::isNotBlank) }
        val bundleNames = appliedBundles.map { it.title }.filter(String::isNotBlank)
        PatchedAppExportData(
            appName = label,
            packageName = installedApp.currentPackageName,
            appVersion = appInfo?.versionName ?: installedApp.version,
            patchBundleVersions = bundleVersions,
            patchBundleNames = bundleNames
        )
    }
    val exportFileName = remember(exportMetadata, exportFormat) {
        exportMetadata?.let { ExportNameFormatter.format(exportFormat, it) } ?: "export.apk"
    }

    val exportSavedLauncher = rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
        viewModel.exportSavedApp(uri)
    }

    val installResult = viewModel.installResult
    val isInstalling = viewModel.isInstalling
    val isMounted = viewModel.isMounted
    val mountOperation = viewModel.mountOperation

    // Set back click handler
    SideEffect {
        viewModel.onBackClick = { onNavigationEvent(InstalledAppInfoNavEvent.NavigateBack) }
    }

    // Handle install result
    LaunchedEffect(installResult) {
        when (installResult) {
            is InstallResult.Success -> {
                context.toast(installResult.message)
                viewModel.clearInstallResult()
            }
            is InstallResult.Failure -> {
                context.toast(installResult.message)
                viewModel.clearInstallResult()
            }
            null -> {}
        }
    }

    // Mount warning dialog
    if (showMountWarningDialog && viewModel.mountWarning != null) {
        MorpheDialog(
            onDismissRequest = {
                viewModel.clearMountWarning()
                showMountWarningDialog = false
            },
            title = stringResource(R.string.warning),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(android.R.string.ok),
                    onPrimaryClick = {
                        viewModel.performMountWarningAction()
                        showMountWarningDialog = false
                    },
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = {
                        viewModel.clearMountWarning()
                        showMountWarningDialog = false
                    }
                )
            }
        ) {
            val secondaryColor = LocalDialogSecondaryTextColor.current
            Text(
                text = stringResource(
                    when (viewModel.mountWarning?.reason) {
                        MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP ->
                            R.string.installer_mount_warning_install
                        MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP ->
                            R.string.installer_mount_mismatch_install
                        else -> R.string.installer_mount_mismatch_title
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor
            )
        }
    }

    // Applied patches dialog
    if (showAppliedPatchesDialog && appliedPatches != null) {
        AppliedPatchesDialog(
            bundles = appliedBundles,
            onDismiss = { showAppliedPatchesDialog = false }
        )
    }

    // Uninstall confirmation
    if (showUninstallConfirm) {
        MorpheDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = stringResource(R.string.uninstall),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.uninstall),
                    onPrimaryClick = {
                        viewModel.uninstall()
                        showUninstallConfirm = false
                    },
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { showUninstallConfirm = false }
                )
            }
        ) {
            val secondaryColor = LocalDialogSecondaryTextColor.current
            Text(
                text = stringResource(R.string.uninstall_app_confirmation),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor
            )
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        MorpheDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.delete),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.delete),
                    onPrimaryClick = {
                        if (installedApp?.installType == InstallType.SAVED) {
                            viewModel.removeSavedApp()
                        } else {
                            viewModel.deleteSavedCopy()
                        }
                        showDeleteDialog = false
                    },
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { showDeleteDialog = false }
                )
            }
        ) {
            val secondaryColor = LocalDialogSecondaryTextColor.current
            Text(
                text = if (installedApp?.installType == InstallType.SAVED) {
                    stringResource(R.string.saved_app_delete_confirmation)
                } else {
                    stringResource(R.string.saved_copy_delete_confirmation)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor
            )
        }
    }

    // Repatch Expert Mode Dialog
    if (viewModel.showRepatchDialog) {
        val allowIncompatible by viewModel.allowIncompatiblePatches.collectAsStateWithLifecycle()

        ExpertModeDialog(
            bundles = viewModel.repatchBundles,
            selectedPatches = viewModel.repatchPatches,
            options = viewModel.repatchOptions,
            onPatchToggle = { bundleUid, patchName ->
                viewModel.toggleRepatchPatch(bundleUid, patchName)
            },
            onOptionChange = { bundleUid, patchName, optionKey, value ->
                viewModel.updateRepatchOption(bundleUid, patchName, optionKey, value)
            },
            onResetOptions = { bundleUid, patchName ->
                viewModel.resetRepatchOptions(bundleUid, patchName)
            },
            onDismiss = {
                viewModel.dismissRepatchDialog()
            },
            onProceed = {
                viewModel.proceedWithRepatch(
                    patches = viewModel.repatchPatches,
                    options = viewModel.repatchOptions
                ) { pkgName, originalFile, patches, options ->
                    onNavigationEvent(
                        InstalledAppInfoNavEvent.NavigateToPatcher(
                            packageName = pkgName,
                            version = originalFile.name
                                .substringAfterLast("_")
                                .substringBeforeLast("_original.apk"),
                            filePath = originalFile.absolutePath,
                            patches = patches,
                            options = options
                        )
                    )
                }
            },
            allowIncompatible = allowIncompatible
        )
    }

    // Main content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (installedApp == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App header
                AppHeader(
                    appInfo = appInfo,
                    packageName = packageName
                )

                // Status badges
                AppStatusBadges(
                    installType = installedApp.installType,
                    isMounted = isMounted,
                    isInstalling = isInstalling,
                    mountOperation = mountOperation
                )

                // Info section
                AppInfoSection(
                    installedApp = installedApp,
                    appInfo = appInfo,
                    appliedPatches = appliedPatches,
                    onShowPatches = { showAppliedPatchesDialog = true },
                    bundlesUsedSummary = bundlesUsedSummary
                )

                // Actions section
                AppActionsSection(
                    viewModel = viewModel,
                    installedApp = installedApp,
                    availablePatches = availablePatches,
                    onPatchClick = { originalPkgName ->
                        onNavigationEvent(InstalledAppInfoNavEvent.TriggerPatchFlow(originalPkgName))
                    },
                    onUninstall = { showUninstallConfirm = true },
                    onDelete = { showDeleteDialog = true },
                    onMountWarning = { showMountWarningDialog = true },
                    onExport = { exportSavedLauncher.launch(exportFileName) },
                    onNavigationEvent = onNavigationEvent
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    appInfo: PackageInfo?,
    packageName: String
) {
    MorpheCard(
        elevation = 3.dp,
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(
                packageInfo = appInfo,
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )

            AppLabel(
                packageInfo = appInfo,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                defaultText = packageName
            )
        }
    }
}

@Composable
private fun AppStatusBadges(
    installType: InstallType,
    isMounted: Boolean,
    isInstalling: Boolean,
    mountOperation: MountOperation?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Install type badge
        InfoBadge(
            text = stringResource(installType.stringResource),
            style = InfoBadgeStyle.Success,
            icon = null,
            isCompact = true
        )

        // Mount status
        if (installType == InstallType.MOUNT) {
            val (text, style) = when {
                mountOperation == MountOperation.MOUNTING ->
                    stringResource(R.string.mounting_ellipsis) to InfoBadgeStyle.Warning

                mountOperation == MountOperation.UNMOUNTING ->
                    stringResource(R.string.unmounting) to InfoBadgeStyle.Warning

                isMounted ->
                    stringResource(R.string.mounted) to InfoBadgeStyle.Success

                else ->
                    stringResource(R.string.not_mounted) to InfoBadgeStyle.Default
            }

            InfoBadge(
                text = text,
                style = style,
                icon = null,
                isCompact = true
            )
        }

        // Installing badge
        if (isInstalling) {
            InfoBadge(
                text = stringResource(R.string.installing_ellipsis),
                style = InfoBadgeStyle.Success,
                icon = null,
                isCompact = true
            )
        }
    }
}

@Composable
private fun AppInfoSection(
    installedApp: InstalledApp,
    appInfo: PackageInfo?,
    appliedPatches: Map<Int, Set<String>>?,
    onShowPatches: () -> Unit,
    bundlesUsedSummary: String
) {
    MorpheCard(
        elevation = 2.dp,
        cornerRadius = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            CardHeader(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.information)
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                InfoRow(
                    label = stringResource(R.string.package_name),
                    value = installedApp.currentPackageName
                )

                if (installedApp.originalPackageName != installedApp.currentPackageName) {
                    MorpheSettingsDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.original_package_name),
                        value = installedApp.originalPackageName
                    )
                }

                MorpheSettingsDivider(modifier = Modifier.padding(vertical = 8.dp))

                InfoRow(
                    label = stringResource(R.string.version),
                    value = appInfo?.versionName ?: installedApp.version
                )

                MorpheSettingsDivider(modifier = Modifier.padding(vertical = 8.dp))

                InfoRow(
                    label = stringResource(R.string.install_type),
                    value = stringResource(installedApp.installType.stringResource)
                )

                if (appliedPatches != null && appliedPatches.values.any { it.isNotEmpty() }) {
                    MorpheSettingsDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val totalPatches = appliedPatches.values.sumOf { it.size }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.applied_patches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.patch_count,
                                    totalPatches,
                                    totalPatches
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        ActionPillButton(
                            onClick = onShowPatches,
                            icon = Icons.AutoMirrored.Outlined.List,
                            contentDescription = stringResource(R.string.view)
                        )
                    }
                }

                MorpheSettingsDivider(modifier = Modifier.padding(vertical = 8.dp))

                val bundleSummaryText = when {
                    appliedPatches == null -> stringResource(R.string.loading)
                    bundlesUsedSummary.isNotBlank() -> bundlesUsedSummary
                    else -> stringResource(R.string.no_patch_bundles_tracked)
                }

                InfoRow(
                    label = stringResource(R.string.patch_bundles_used),
                    value = bundleSummaryText
                )
            }
        }
    }
}

@Composable
private fun AppActionsSection(
    viewModel: InstalledAppInfoViewModel,
    installedApp: InstalledApp,
    availablePatches: Int,
    onPatchClick: (String) -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    onMountWarning: () -> Unit,
    onExport: () -> Unit,
    onNavigationEvent: (InstalledAppInfoNavEvent) -> Unit
) {
    MorpheCard(
        elevation = 2.dp,
        cornerRadius = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            CardHeader(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.actions)
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Patch button row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ActionCard(
                        text = stringResource(R.string.patch),
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { onPatchClick(installedApp.originalPackageName) },
                        enabled = availablePatches > 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Open/Export row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (installedApp.installType != InstallType.SAVED && viewModel.appInfo != null) {
                        ActionCard(
                            text = stringResource(R.string.open_app),
                            icon = Icons.Outlined.PlayArrow,
                            onClick = { viewModel.launch() },
                            enabled = viewModel.isInstalledOnDevice,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (viewModel.hasSavedCopy) {
                        ActionCard(
                            text = stringResource(R.string.export),
                            icon = Icons.Outlined.Save,
                            onClick = onExport,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Repatch/Install row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ActionCard(
                        text = stringResource(R.string.repatch),
                        icon = Icons.Outlined.Build,
                        onClick = {
                            viewModel.startRepatch { pkgName, originalFile, patches, options ->
                                onNavigationEvent(
                                    InstalledAppInfoNavEvent.NavigateToPatcher(
                                        packageName = pkgName,
                                        version = originalFile.name
                                            .substringAfterLast("_")
                                            .substringBeforeLast("_original.apk"),
                                        filePath = originalFile.absolutePath,
                                        patches = patches,
                                        options = options
                                    )
                                )
                            }
                        },
                        enabled = viewModel.hasOriginalApk,
                        modifier = Modifier.weight(1f)
                    )

                    if (installedApp.installType == InstallType.SAVED && viewModel.hasSavedCopy) {
                        val installText = if (viewModel.isInstalledOnDevice) {
                            stringResource(R.string.reinstall)
                        } else {
                            stringResource(R.string.install)
                        }
                        ActionCard(
                            text = installText,
                            icon = Icons.Outlined.RestartAlt,
                            onClick = {
                                if (viewModel.mountWarning != null) {
                                    onMountWarning()
                                } else {
                                    viewModel.installSavedApp()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (installedApp.installType == InstallType.MOUNT) {
                        ActionCard(
                            text = if (viewModel.isMounted) {
                                stringResource(R.string.remount_saved_app)
                            } else {
                                stringResource(R.string.mount)
                            },
                            icon = if (viewModel.isMounted) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.Check
                            },
                            onClick = {
                                if (viewModel.isMounted) {
                                    viewModel.remountSavedInstallation()
                                } else {
                                    viewModel.mountOrUnmount()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Uninstall/Delete row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewModel.isInstalledOnDevice) {
                        ActionCard(
                            text = stringResource(R.string.uninstall),
                            icon = Icons.Outlined.DeleteForever,
                            onClick = onUninstall,
                            isDestructive = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (viewModel.hasSavedCopy) {
                        ActionCard(
                            text = if (installedApp.installType == InstallType.SAVED) {
                                stringResource(R.string.delete)
                            } else {
                                stringResource(R.string.delete_saved_copy)
                            },
                            icon = Icons.Outlined.DeleteOutline,
                            onClick = onDelete,
                            isDestructive = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Warning badge
                AnimatedVisibility(
                    visible = !viewModel.hasOriginalApk,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    InfoBadge(
                        text = stringResource(R.string.morphe_repatch_requires_original),
                        style = InfoBadgeStyle.Warning,
                        icon = Icons.Outlined.Info,
                        isExpanded = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val containerColor = when {
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    val contentColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppliedPatchesDialog(
    bundles: List<AppliedPatchBundleUi>,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.applied_patches),
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            bundles.forEach { bundle ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val title = buildString {
                        append(bundle.title)
                        bundle.version?.takeIf { it.isNotBlank() }?.let {
                            append(" (")
                            append(it)
                            append(")")
                        }
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    bundle.patchInfos.forEach { patch ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = patch.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }
                    }

                    bundle.fallbackNames.forEach { patchName ->
                        Text(
                            text = "• $patchName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
