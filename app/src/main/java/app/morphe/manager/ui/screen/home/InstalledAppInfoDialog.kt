package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

data class AppliedPatchBundleUi(
    val uid: Int,
    val title: String,
    val version: String?,
    val patchInfos: List<PatchInfo>,
    val fallbackNames: List<String>,
    val bundleAvailable: Boolean
)

/**
 * Dialog for installed app info and actions
 */
@SuppressLint("LocalContextGetResourceValueCheck")
@Composable
fun InstalledAppInfoDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onNavigateToPatcher: (packageName: String, version: String, filePath: String, patches: PatchSelection, options: Options) -> Unit,
    onTriggerPatchFlow: (originalPackageName: String) -> Unit,
    viewModel: InstalledAppInfoViewModel = koinViewModel(
        key = packageName,
        parameters = { parametersOf(packageName) }
    ),
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val installedApp = viewModel.installedApp
    val appInfo = viewModel.appInfo
    val appliedPatches = viewModel.appliedPatches
    val isLoading = viewModel.isLoading

    // Get installation state
    val installState = installViewModel.installState
    val isInstalling = installState is InstallViewModel.InstallState.Installing
    val mountOperation = installViewModel.mountOperation

    // Get HomeViewModel for update status
    val homeViewModel: HomeViewModel = koinViewModel()
    val hasUpdate = homeViewModel.appUpdatesAvailable[packageName] == true

    // Dialog states
    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAppliedPatchesDialog by remember { mutableStateOf(false) }
    var showMountWarningDialog by remember { mutableStateOf(false) }
    var pendingMountWarningAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Bundle data
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val bundleSources by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val availablePatches by patchBundleRepository.bundleInfoFlow
        .collectAsStateWithLifecycle(emptyMap())
        .let { remember(it.value) { derivedStateOf { it.value.values.sumOf { bundle -> bundle.patches.size } } } }

    // Extract strings to avoid LocalContext issues
    val fallbackNameDefault = stringResource(R.string.home_app_info_patches_name_default)
    val fallbackNameGeneric = stringResource(R.string.home_app_info_patches_name_fallback)
    val exportSuccessMessage = stringResource(R.string.save_apk_success)
    val exportFailedMessage = stringResource(R.string.saved_app_export_failed)

    // Build applied bundles summary with stored versions
    val appliedBundles by produceState<List<AppliedPatchBundleUi>>(
        initialValue = emptyList(),
        appliedPatches,
        bundleInfo,
        bundleSources,
        installedApp,
        fallbackNameDefault,
        fallbackNameGeneric
    ) {
        if (appliedPatches.isNullOrEmpty() || installedApp == null) {
            value = emptyList()
            return@produceState
        }

        // Get stored bundle versions from database
        val storedVersions = withContext(Dispatchers.IO) {
            viewModel.getStoredBundleVersions()
        }

        value = appliedPatches.entries.mapNotNull { (bundleUid, patches) ->
            if (patches.isEmpty()) return@mapNotNull null
            val info = bundleInfo[bundleUid]
            val source = bundleSources.firstOrNull { it.uid == bundleUid }
            val fallbackName = if (bundleUid == 0) {
                fallbackNameDefault
            } else {
                fallbackNameGeneric
            }
            val title = source?.displayTitle ?: info?.name ?: "$fallbackName (#$bundleUid)"

            // Use stored version from DB, fallback to current version
            val version = storedVersions[bundleUid] ?: info?.version

            val patchInfos = info?.patches?.filter { it.name in patches }?.distinctBy { it.name }?.sortedBy { it.name } ?: emptyList()
            val missingNames = patches.toList().sorted().filterNot { name -> patchInfos.any { it.name == name } }.distinct()
            AppliedPatchBundleUi(
                uid = bundleUid,
                title = title,
                version = version,
                patchInfos = patchInfos,
                fallbackNames = missingNames,
                bundleAvailable = info != null
            )
        }.sortedBy { it.title }
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
        val savedFile = viewModel.savedApkFile()
        if (savedFile != null && uri != null) {
            installViewModel.export(savedFile, uri) { success ->
                if (success) {
                    context.toast(exportSuccessMessage)
                } else {
                    context.toast(exportFailedMessage)
                }
            }
        }
    }

    // Refresh app state on every launch
    LaunchedEffect(Unit) {
        viewModel.refreshCurrentAppState()
    }

    // Set back click handler
    SideEffect { viewModel.onBackClick = onDismiss }

    // Handle install result
    LaunchedEffect(installState) {
        when (installState) {
            is InstallViewModel.InstallState.Installed -> {
                // Installation succeeded - update install type in database and refresh UI
                val finalPackageName = installState.packageName
                val newInstallType = when (installViewModel.currentInstallType) {
                    InstallType.MOUNT -> InstallType.MOUNT
                    InstallType.SHIZUKU -> InstallType.SHIZUKU
                    InstallType.CUSTOM -> InstallType.CUSTOM
                    else -> InstallType.DEFAULT
                }
                viewModel.updateInstallType(finalPackageName, newInstallType)
            }
            is InstallViewModel.InstallState.Error -> {
                // Show error toast
                context.toast(installState.message)
            }
            else -> {}
        }
    }

    // Installer unavailable dialog
    installViewModel.installerUnavailableDialog?.let { dialogState ->
        InstallerUnavailableDialog(
            state = dialogState,
            onOpenApp = installViewModel::openInstallerApp,
            onRetry = installViewModel::retryWithPreferredInstaller,
            onUseFallback = installViewModel::proceedWithFallbackInstaller,
            onDismiss = installViewModel::dismissInstallerUnavailableDialog
        )
    }

    // Sub-dialogs
    if (showAppliedPatchesDialog && appliedPatches != null) {
        AppliedPatchesDialog(bundles = appliedBundles, onDismiss = { showAppliedPatchesDialog = false })
    }

    // Mount warning dialog
    if (showMountWarningDialog) {
        MountWarningDialog(
            onConfirm = {
                showMountWarningDialog = false
                pendingMountWarningAction?.invoke()
                pendingMountWarningAction = null
            },
            onDismiss = {
                showMountWarningDialog = false
                pendingMountWarningAction = null
            }
        )
    }

    UninstallConfirmDialog(
        show = showUninstallConfirm,
        onConfirm = {
            viewModel.uninstall()
            showUninstallConfirm = false
        },
        onDismiss = { showUninstallConfirm = false }
    )

    DeleteConfirmDialog(
        show = showDeleteDialog,
        isSavedOnly = installedApp?.installType == InstallType.SAVED,
        appInfo = viewModel.appInfo,
        appLabel = viewModel.appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString(),
        onConfirm = {
            viewModel.removeAppCompletely()
            showDeleteDialog = false
        },
        onDismiss = {
            showDeleteDialog = false
        }
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
                        installedApp?.version ?: "unknown",
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
        compactPadding = true,
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

                // Deleted App Warning Banner
                AnimatedVisibility(
                    visible = viewModel.isAppDeleted,
                    enter = fadeIn(animationSpec = tween(durationMillis = 400)) +
                            expandVertically(animationSpec = tween(durationMillis = 400)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300)) +
                            shrinkVertically(animationSpec = tween(durationMillis = 300))
                ) {
                    WarningBanner(
                        icon = Icons.Outlined.Warning,
                        title = stringResource(R.string.home_app_info_app_deleted_warning),
                        description = stringResource(R.string.home_app_info_app_deleted_description),
                        buttonText = stringResource(R.string.patch),
                        buttonIcon = Icons.Outlined.AutoFixHigh,
                        onClick = {
                            onDismiss()
                            onTriggerPatchFlow(installedApp.originalPackageName)
                        },
                        isError = true
                    )
                }

                // Update Available Banner
                AnimatedVisibility(
                    visible = hasUpdate && !viewModel.isAppDeleted,
                    enter = fadeIn(animationSpec = tween(durationMillis = 400)) +
                            expandVertically(animationSpec = tween(durationMillis = 400)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300)) +
                            shrinkVertically(animationSpec = tween(durationMillis = 300))
                ) {
                    WarningBanner(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.home_app_info_patch_update_available),
                        description = stringResource(R.string.home_app_info_patch_update_available_description),
                        buttonText = stringResource(R.string.patch),
                        buttonIcon = Icons.Outlined.AutoFixHigh,
                        onClick = {
                            onDismiss()
                            onTriggerPatchFlow(installedApp.originalPackageName)
                        },
                        isError = false
                    )
                }

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
                    installViewModel = installViewModel,
                    installedApp = installedApp,
                    availablePatches = availablePatches,
                    isInstalling = isInstalling,
                    mountOperation = mountOperation,
                    hasUpdate = hasUpdate,
                    onPatchClick = {
                        onDismiss()
                        onTriggerPatchFlow(installedApp.originalPackageName)
                    },
                    onUninstall = { showUninstallConfirm = true },
                    onDelete = { showDeleteDialog = true },
                    onExport = { exportSavedLauncher.launch(exportFileName) },
                    onShowMountWarning = { action ->
                        pendingMountWarningAction = action
                        showMountWarningDialog = true
                    }
                )

                // Info about saved APK availability
                if (!viewModel.hasOriginalApk) {
                    InfoBadge(
                        text = stringResource(R.string.home_app_info_no_saved_apk),
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

/**
 * Unified banner component for warnings and updates
 */
@Composable
private fun WarningBanner(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonIcon: ImageVector,
    onClick: () -> Unit,
    isError: Boolean = false
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    MorpheCard(
        cornerRadius = 12.dp,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with icon
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            // Action button
            ActionButton(
                text = buttonText,
                icon = buttonIcon,
                onClick = onClick,
                isPrimary = true,
                isHighlighted = true,
                modifier = Modifier.fillMaxWidth()
            )
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
                    label = stringResource(R.string.home_app_info_original_package_name),
                    value = installedApp.originalPackageName
                )
            }

            MorpheSettingsDivider(fullWidth = true)

            // Install type
            InfoRow(
                label = stringResource(R.string.home_app_info_install_type),
                value = stringResource(installedApp.installType.stringResource)
            )

            // Patched date (if available)
            installedApp.patchedAt?.let { timestamp ->
                MorpheSettingsDivider(fullWidth = true)
                InfoRow(
                    label = stringResource(R.string.home_app_info_patched_at),
                    value = getRelativeTimeString(timestamp)
                )
            }

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
                            text = stringResource(R.string.home_app_info_applied_patches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches),
                            style = MaterialTheme.typography.bodySmall,
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
                    label = stringResource(R.string.home_app_info_patch_source_used),
                    value = bundlesUsedSummary
                )
            }
        }
    }
}

@Composable
private fun ActionsSection(
    viewModel: InstalledAppInfoViewModel,
    installViewModel: InstallViewModel,
    installedApp: InstalledApp,
    availablePatches: Int,
    isInstalling: Boolean,
    mountOperation: InstallViewModel.MountOperation?,
    hasUpdate: Boolean,
    onPatchClick: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShowMountWarning: (action: () -> Unit) -> Unit
) {
    // Collect all available actions
    val primaryActions = mutableListOf<ActionItem>()
    val secondaryActions = mutableListOf<ActionItem>()
    val destructiveActions = mutableListOf<ActionItem>()

    // Primary actions - Single Patch button that triggers APK selection dialog
    // The dialog will show "Use saved APK" option if original APK exists
    if (!hasUpdate && !viewModel.isAppDeleted) { // Hide the Patch button if there is a banner with its own button
        primaryActions.add(
            ActionItem(
                text = stringResource(R.string.patch),
                icon = Icons.Outlined.AutoFixHigh,
                onClick = onPatchClick,
                enabled = availablePatches > 0
            )
        )
    }

    // Secondary actions
    if (installedApp.installType != InstallType.SAVED && viewModel.appInfo != null && viewModel.isInstalledOnDevice) {
        secondaryActions.add(
            ActionItem(
                text = stringResource(R.string.open),
                icon = Icons.AutoMirrored.Outlined.Launch,
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
                stringResource(R.string.install)
            }
            secondaryActions.add(
                ActionItem(
                    text = installText,
                    icon = Icons.Outlined.InstallMobile,
                    onClick = {
                        val savedFile = viewModel.savedApkFile()
                        if (savedFile != null) {
                            val installAction = {
                                installViewModel.install(
                                    outputFile = savedFile,
                                    originalPackageName = installedApp.originalPackageName,
                                    onPersistApp = { pkg, type ->
                                        // Callback will be called after successful installation
                                        // The LaunchedEffect handler will update the install type
                                        true
                                    }
                                )
                            }

                            // Check if mount warning is needed
                            if (viewModel.primaryInstallerIsMount && installedApp.installType != InstallType.MOUNT) {
                                // Show mount warning dialog
                                onShowMountWarning(installAction)
                            } else if (!viewModel.primaryInstallerIsMount && installedApp.installType == InstallType.MOUNT) {
                                // Show mount mismatch warning
                                onShowMountWarning(installAction)
                            } else {
                                // No warning needed, install directly
                                installAction()
                            }
                        }
                    },
                    isLoading = isInstalling
                )
            )
        }
        installedApp.installType == InstallType.MOUNT -> {
            val isMountLoading = mountOperation != null
            secondaryActions.add(
                ActionItem(
                    text = if (viewModel.isMounted) stringResource(R.string.remount) else stringResource(R.string.mount),
                    icon = if (viewModel.isMounted) Icons.Outlined.Refresh else Icons.Outlined.Link,
                    onClick = {
                        if (viewModel.isMounted) {
                            installViewModel.remount(
                                packageName = installedApp.currentPackageName,
                                version = installedApp.version
                            )
                        } else {
                            installViewModel.mount(
                                packageName = installedApp.currentPackageName,
                                version = installedApp.version
                            )
                        }
                    },
                    isLoading = isMountLoading
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
        destructiveActions.add(
            ActionItem(
                text = stringResource(R.string.delete),
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
    val isDestructive: Boolean = false,
    val isLoading: Boolean = false
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
                isLoading = action.isLoading,
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
    isPrimary: Boolean = false,
    isLoading: Boolean = false,
    isHighlighted: Boolean = false
) {
    val containerColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primary
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isPrimary -> MaterialTheme.colorScheme.primaryContainer
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    }

    val contentColor = when {
        isHighlighted -> MaterialTheme.colorScheme.onPrimary
        isDestructive -> MaterialTheme.colorScheme.error
        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
            text = stringResource(R.string.installer_mount_warning_install),
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
            text = stringResource(R.string.home_app_info_uninstall_app_confirmation),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    show: Boolean,
    isSavedOnly: Boolean,
    appInfo: PackageInfo?,
    appLabel: String?,
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Icon
            AppIcon(
                packageInfo = appInfo,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            // App Name
            if (appLabel != null) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalDialogTextColor.current,
                    textAlign = TextAlign.Center
                )
            }

            // What will be deleted
            DeletionWarningBox(
                warningText = stringResource(R.string.home_app_info_remove_app_warning)
            ) {
                if (isSavedOnly) {
                    // Saved app - only delete patched APK
                    DeleteListItem(
                        icon = Icons.Outlined.Delete,
                        text = stringResource(R.string.home_app_info_delete_item_patched_apk)
                    )
                } else {
                    // Full deletion
                    DeleteListItem(
                        icon = Icons.Outlined.Storage,
                        text = stringResource(R.string.home_app_info_delete_item_database)
                    )
                    DeleteListItem(
                        icon = Icons.Outlined.Android,
                        text = stringResource(R.string.home_app_info_delete_item_patched_apk)
                    )
                    DeleteListItem(
                        icon = Icons.Outlined.FilePresent,
                        text = stringResource(R.string.home_app_info_delete_item_original_apk)
                    )
                }
            }

            // Description
            if (!isSavedOnly) {
                InfoBadge(
                    text = stringResource(R.string.home_app_info_delete_preservation_note),
                    style = InfoBadgeStyle.Warning,
                    icon = Icons.Outlined.Info,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
        title = stringResource(R.string.home_app_info_applied_patches),
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
