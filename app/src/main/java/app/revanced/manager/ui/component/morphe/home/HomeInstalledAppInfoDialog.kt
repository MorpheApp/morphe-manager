package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
import app.revanced.manager.ui.viewmodel.MountWarningReason
import app.revanced.manager.util.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Dialog for installed app info and actions
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun InstalledAppInfoDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onNavigateToPatcher: (packageName: String, version: String, filePath: String, patches: PatchSelection, options: Options) -> Unit,
    onTriggerPatchFlow: (originalPackageName: String) -> Unit,
    viewModel: InstalledAppInfoViewModel = koinViewModel(
        key = packageName,
        parameters = { parametersOf(packageName) }
    )
) {
    val context = LocalContext.current
    val installedApp = viewModel.installedApp
    val appInfo = viewModel.appInfo
    val appliedPatches = viewModel.appliedPatches
    val isLoading = viewModel.isLoading

    // Dialog states
    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMountWarningDialog by remember { mutableStateOf(false) }
    var showAppliedPatchesDialog by remember { mutableStateOf(false) }

    // Bundle data
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
                val fallbackName = if (bundleUid == 0) {
                    context.getString(R.string.patches_name_default)
                } else {
                    context.getString(R.string.patches_name_fallback)
                }
                val title = source?.displayTitle ?: info?.name ?: "$fallbackName (#$bundleUid)"
                val patchInfos = info?.patches?.filter { it.name in patches }?.distinctBy { it.name }?.sortedBy { it.name } ?: emptyList()
                val missingNames = patches.toList().sorted().filterNot { name -> patchInfos.any { it.name == name } }.distinct()
                AppliedPatchBundleUi(
                    uid = bundleUid,
                    title = title,
                    version = savedBundleVersions[bundleUid]?.takeUnless { it.isBlank() } ?: info?.version,
                    patchInfos = patchInfos,
                    fallbackNames = missingNames,
                    bundleAvailable = info != null
                )
            }.sortedBy { it.title }
        }.getOrElse { emptyList() }
    }

    // Bundle summary text
    val bundlesUsedSummary = remember(appliedBundles) {
        if (appliedBundles.isEmpty()) ""
        else appliedBundles.joinToString("\n") { bundle ->
            val version = bundle.version?.takeIf { it.isNotBlank() }
            if (version != null) "${bundle.title} ($version)" else bundle.title
        }
    }

    // Export functionality
    val exportFormat by viewModel.exportFormat.collectAsStateWithLifecycle()
    val exportMetadata = remember(installedApp?.currentPackageName, appInfo?.versionName, appliedBundles, appInfo) {
        if (installedApp == null) return@remember null
        val label = appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: installedApp.currentPackageName
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
        exportMetadata?.let { ExportNameFormatter.format(exportFormat, it) } ?: "morphe_export.apk"
    }
    val exportSavedLauncher = rememberLauncherForActivityResult(CreateDocument(APK_MIMETYPE)) { uri ->
        viewModel.exportSavedApp(uri)
    }

    val installResult = viewModel.installResult

    // Set back click handler
    SideEffect { viewModel.onBackClick = onDismiss }

    // Handle install result
    LaunchedEffect(installResult) {
        when (installResult) {
            is InstallResult.Success -> { context.toast(installResult.message); viewModel.clearInstallResult() }
            is InstallResult.Failure -> { context.toast(installResult.message); viewModel.clearInstallResult() }
            null -> {}
        }
    }

    // Sub-dialogs
    MountWarningDialog(
        show = showMountWarningDialog && viewModel.mountWarning != null,
        reason = viewModel.mountWarning?.reason,
        onConfirm = { viewModel.performMountWarningAction(); showMountWarningDialog = false },
        onDismiss = { viewModel.clearMountWarning(); showMountWarningDialog = false }
    )

    if (showAppliedPatchesDialog && appliedPatches != null) {
        AppliedPatchesDialog(bundles = appliedBundles, onDismiss = { showAppliedPatchesDialog = false })
    }

    UninstallConfirmDialog(
        show = showUninstallConfirm,
        onConfirm = { viewModel.uninstall(); showUninstallConfirm = false },
        onDismiss = { showUninstallConfirm = false }
    )

    DeleteConfirmDialog(
        show = showDeleteDialog,
        isSavedOnly = installedApp?.installType == InstallType.SAVED,
        onConfirm = {
            if (installedApp?.installType == InstallType.SAVED) viewModel.removeSavedApp()
            else viewModel.deleteSavedCopy()
            showDeleteDialog = false
        },
        onDismiss = { showDeleteDialog = false }
    )

    // Expert Mode Repatch Dialog
    if (viewModel.showRepatchDialog) {
        val allowIncompatible by viewModel.allowIncompatiblePatches.collectAsStateWithLifecycle()
        ExpertModeDialog(
            bundles = viewModel.repatchBundles,
            selectedPatches = viewModel.repatchPatches,
            options = viewModel.repatchOptions,
            onPatchToggle = { bundleUid, patchName -> viewModel.toggleRepatchPatch(bundleUid, patchName) },
            onOptionChange = { bundleUid, patchName, optionKey, value -> viewModel.updateRepatchOption(bundleUid, patchName, optionKey, value) },
            onResetOptions = { bundleUid, patchName -> viewModel.resetRepatchOptions(bundleUid, patchName) },
            onDismiss = { viewModel.dismissRepatchDialog() },
            onProceed = {
                viewModel.proceedWithRepatch(viewModel.repatchPatches, viewModel.repatchOptions) { pkgName, originalFile, patches, options ->
                    onNavigateToPatcher(
                        pkgName,
                        originalFile.name.substringAfterLast("_").substringBeforeLast("_original.apk"),
                        originalFile.absolutePath,
                        patches,
                        options
                    )
                }
            },
            allowIncompatible = allowIncompatible
        )
    }

    // Main Dialog
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = null,
        dismissOnClickOutside = true,
        footer = null
    ) {
        if (isLoading || installedApp == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App Header Card
                AppHeaderCard(
                    appInfo = appInfo,
                    packageName = packageName,
                    installedApp = installedApp
                )

                // Info Section
                InfoSection(
                    installedApp = installedApp,
                    appliedPatches = appliedPatches,
                    bundlesUsedSummary = bundlesUsedSummary,
                    onShowPatches = { showAppliedPatchesDialog = true }
                )

                // Actions Section
                ActionsSection(
                    viewModel = viewModel,
                    installedApp = installedApp,
                    availablePatches = availablePatches,
                    onPatchClick = { onTriggerPatchFlow(installedApp.originalPackageName) },
                    onRepatchClick = {
                        viewModel.startRepatch { pkgName, originalFile, patches, options ->
                            onNavigateToPatcher(
                                pkgName,
                                originalFile.name.substringAfterLast("_").substringBeforeLast("_original.apk"),
                                originalFile.absolutePath,
                                patches,
                                options
                            )
                        }
                    },
                    onUninstall = { showUninstallConfirm = true },
                    onDelete = { showDeleteDialog = true },
                    onMountWarning = { showMountWarningDialog = true },
                    onExport = { exportSavedLauncher.launch(exportFileName) }
                )

                // Warning for missing original APK
                if (!viewModel.hasOriginalApk) {
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
private fun AppHeaderCard(
    appInfo: PackageInfo?,
    packageName: String,
    installedApp: InstalledApp,
) {
    MorpheCard(
        cornerRadius = 16.dp,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            AppIcon(
                packageInfo = appInfo,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            // App details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLabel(
                    packageInfo = appInfo,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    defaultText = packageName
                )

                Text(
                    text = appInfo?.versionName?.let { "v$it" } ?: installedApp.version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }
    }
}

@Composable
private fun InfoSection(
    installedApp: InstalledApp,
    appliedPatches: Map<Int, Set<String>>?,
    bundlesUsedSummary: String,
    onShowPatches: () -> Unit
) {
    val totalPatches = appliedPatches?.values?.sumOf { it.size } ?: 0

    MorpheCard(
        cornerRadius = 16.dp,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Package name
            InfoRow(
                label = stringResource(R.string.package_name),
                value = installedApp.currentPackageName
            )

            // Original package (if different)
            if (installedApp.originalPackageName != installedApp.currentPackageName) {
                MorpheSettingsDivider(fullWidth = true)
                InfoRow(
                    label = stringResource(R.string.original_package_name),
                    value = installedApp.originalPackageName
                )
            }

            MorpheSettingsDivider(fullWidth = true)

            // Install type
            InfoRow(
                label = stringResource(R.string.install_type),
                value = stringResource(installedApp.installType.stringResource)
            )

            // Applied patches with icon button
            if (totalPatches > 0) {
                MorpheSettingsDivider(fullWidth = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.applied_patches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionPillButton(
                        onClick = onShowPatches,
                        icon = Icons.AutoMirrored.Outlined.List,
                        contentDescription = stringResource(R.string.view)
                    )
                }
            }

            // Bundles used
            if (bundlesUsedSummary.isNotBlank()) {
                MorpheSettingsDivider(fullWidth = true)
                InfoRow(
                    label = stringResource(R.string.patch_bundles_used),
                    value = bundlesUsedSummary
                )
            }
        }
    }
}

@Composable
private fun ActionsSection(
    viewModel: InstalledAppInfoViewModel,
    installedApp: InstalledApp,
    availablePatches: Int,
    onPatchClick: () -> Unit,
    onRepatchClick: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    onMountWarning: () -> Unit,
    onExport: () -> Unit
) {
    // Collect all available actions
    val primaryActions = mutableListOf<ActionItem>()
    val secondaryActions = mutableListOf<ActionItem>()
    val destructiveActions = mutableListOf<ActionItem>()

    // Primary actions
    primaryActions.add(
        ActionItem(
            text = stringResource(R.string.patch),
            icon = Icons.Outlined.AutoAwesome,
            onClick = onPatchClick,
            enabled = availablePatches > 0
        )
    )

    if (viewModel.hasOriginalApk) {
        primaryActions.add(
            ActionItem(
                text = stringResource(R.string.repatch),
                icon = Icons.Outlined.Build,
                onClick = onRepatchClick
            )
        )
    }

    // Secondary actions
    if (installedApp.installType != InstallType.SAVED && viewModel.appInfo != null && viewModel.isInstalledOnDevice) {
        secondaryActions.add(
            ActionItem(
                text = stringResource(R.string.open_app),
                icon = Icons.Outlined.PlayArrow,
                onClick = { viewModel.launch() }
            )
        )
    }

    if (viewModel.hasSavedCopy) {
        secondaryActions.add(
            ActionItem(
                text = stringResource(R.string.export),
                icon = Icons.Outlined.Save,
                onClick = onExport
            )
        )
    }

    when {
        installedApp.installType == InstallType.SAVED && viewModel.hasSavedCopy -> {
            val installText = if (viewModel.isInstalledOnDevice) {
                stringResource(R.string.reinstall)
            } else {
                stringResource(R.string.install_app)
            }
            secondaryActions.add(
                ActionItem(
                    text = installText,
                    icon = Icons.Outlined.Download,
                    onClick = { if (viewModel.mountWarning != null) onMountWarning() else viewModel.installSavedApp() }
                )
            )
        }
        installedApp.installType == InstallType.MOUNT -> {
            secondaryActions.add(
                ActionItem(
                    text = if (viewModel.isMounted) stringResource(R.string.remount_saved_app) else stringResource(R.string.mount),
                    icon = if (viewModel.isMounted) Icons.Outlined.Refresh else Icons.Outlined.Check,
                    onClick = { if (viewModel.isMounted) viewModel.remountSavedInstallation() else viewModel.mountOrUnmount() }
                )
            )
        }
    }

    // Destructive actions
    if (viewModel.isInstalledOnDevice) {
        destructiveActions.add(
            ActionItem(
                text = stringResource(R.string.uninstall),
                icon = Icons.Outlined.DeleteForever,
                onClick = onUninstall,
                isDestructive = true
            )
        )
    }

    if (viewModel.hasSavedCopy) {
        val deleteText = if (installedApp.installType == InstallType.SAVED) {
            stringResource(R.string.uninstall)
        } else {
            stringResource(R.string.delete)
        }
        destructiveActions.add(
            ActionItem(
                text = deleteText,
                icon = Icons.Outlined.DeleteOutline,
                onClick = onDelete,
                isDestructive = true
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary actions row
        if (primaryActions.isNotEmpty()) {
            ActionButtonsRow(actions = primaryActions, isPrimary = true)
        }

        // Secondary actions row
        if (secondaryActions.isNotEmpty()) {
            ActionButtonsRow(actions = secondaryActions, isPrimary = false)
        }

        // Destructive actions row
        if (destructiveActions.isNotEmpty()) {
            ActionButtonsRow(actions = destructiveActions, isPrimary = false)
        }
    }
}

private data class ActionItem(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false
)

@Composable
private fun ActionButtonsRow(
    actions: List<ActionItem>,
    isPrimary: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.forEach { action ->
            ActionButton(
                text = action.text,
                icon = action.icon,
                onClick = action.onClick,
                enabled = action.enabled,
                isDestructive = action.isDestructive,
                isPrimary = isPrimary && !action.isDestructive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    isPrimary: Boolean = false
) {
    val containerColor = when {
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isPrimary -> MaterialTheme.colorScheme.primaryContainer
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    }

    val contentColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MountWarningDialog(
    show: Boolean,
    reason: MountWarningReason?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.warning),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(
                when (reason) {
                    MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP -> R.string.installer_mount_warning_install
                    MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP -> R.string.installer_mount_mismatch_install
                    else -> R.string.installer_mount_mismatch_title
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

@Composable
private fun UninstallConfirmDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.uninstall),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.uninstall),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(R.string.uninstall_app_confirmation),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    show: Boolean,
    isSavedOnly: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = if (isSavedOnly) stringResource(R.string.saved_app_delete_confirmation)
            else stringResource(R.string.saved_copy_delete_confirmation),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
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
