/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.util.NativeLibStripper
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.*
import org.koin.androidx.compose.koinViewModel
import java.io.File

data class AppliedPatchBundleUi(
    val uid: Int,
    val title: String,
    val version: String?,
    val patchInfos: List<PatchInfo>,
    val fallbackNames: List<String>,
    val bundleAvailable: Boolean
)

/**
 * Dialog for installed app info and actions.
 */
@Composable
fun InstalledAppInfoDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onTriggerPatchFlow: (originalPackageName: String) -> Unit,
    homeViewModel: HomeViewModel,
    viewModel: InstalledAppInfoViewModel,
    installViewModel: InstallViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
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

    // Get update status from the shared HomeViewModel instance
    val appUpdates by homeViewModel.appUpdatesAvailable.collectAsStateWithLifecycle()
    val hasUpdate = appUpdates[packageName] == true

    // Accent color resolution order: bundle metadata (appIconColor) -> KnownApps.brandColor -> default.
    // originalPackageName needed because metadata is keyed by original pkg, not patched.
    val bundleAppMetadata by homeViewModel.bundleAppMetadataFlow.collectAsStateWithLifecycle()
    val appAccentColor: Color by remember(packageName) {
        derivedStateOf {
            val orig = viewModel.installedApp?.originalPackageName ?: packageName
            bundleAppMetadata[orig]?.downloadColor
                ?: KnownApps.fromPackage(orig)?.brandColor
                ?: KnownApps.DEFAULT_DOWNLOAD_COLOR
        }
    }

    // Dialog states
    val showUninstallConfirm = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showAppliedPatchesDialog = remember { mutableStateOf(false) }
    val showMountWarningDialog = remember { mutableStateOf(false) }
    val showSignatureConflictDialog = remember { mutableStateOf(false) }
    val conflictPackageName = remember { mutableStateOf<String?>(null) }
    val pendingMountWarningAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Content entrance animation
    val entered = remember { mutableStateOf(false) }

    // Bundle data
    val appliedBundles by viewModel.appliedBundles.collectAsStateWithLifecycle()
    val bundlesUsedSummary by viewModel.bundlesUsedSummary.collectAsStateWithLifecycle()
    val availablePatches by viewModel.availablePatches.collectAsStateWithLifecycle()

    val appLabel = remember(appInfo, packageName) {
        appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
    }

    // Export strings
    val exportSuccessMessage = stringResource(R.string.save_apk_success)
    val exportFailedMessage = stringResource(R.string.saved_app_export_failed)

    // Export file name
    val exportFileName = remember(installedApp?.currentPackageName, appInfo?.versionName, appliedBundles) {
        val app = installedApp ?: return@remember "morphe_export.apk"
        ExportNameFormatter.format(null, PatchedAppExportData(
            appName = appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString(),
            packageName = app.currentPackageName,
            appVersion = appInfo?.versionName ?: app.version,
            patchBundleVersions = appliedBundles.mapNotNull { it.version?.takeIf(String::isNotBlank) },
            patchBundleNames = appliedBundles.map { it.title }.filter(String::isNotBlank)
        ))
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

    var hadMountOperation by remember { mutableStateOf(false) }
    LaunchedEffect(mountOperation) {
        if (mountOperation != null) {
            hadMountOperation = true
        } else if (hadMountOperation) {
            hadMountOperation = false
            viewModel.refreshCurrentAppState()
            installedApp?.currentPackageName?.let(homeViewModel::notifyAppStateChanged)
        }
    }

    // Set back click handler
    SideEffect {
        viewModel.onBackClick = onDismiss
        viewModel.onAppStateChanged = { pkg -> homeViewModel.notifyAppStateChanged(pkg) }
    }

    // Handle install result
    LaunchedEffect(installState) {
        when (installState) {
            is InstallViewModel.InstallState.Installed -> {
                // Installation succeeded - update install type in database and refresh UI
                val finalPackageName = installState.packageName
                // InstallViewModel is a Koin singleton shared across dialogs; guard against
                // stale installation results from a previously installed app firing in this dialog
                val app = viewModel.installedApp
                if (app != null &&
                    finalPackageName != app.currentPackageName &&
                    finalPackageName != app.originalPackageName) {
                    return@LaunchedEffect
                }
                val newInstallType = when (installViewModel.currentInstallType) {
                    InstallType.MOUNT -> InstallType.MOUNT
                    InstallType.SHIZUKU -> InstallType.SHIZUKU
                    InstallType.SHIZUKU_PLAY_STORE -> InstallType.SHIZUKU_PLAY_STORE
                    InstallType.PLAY_STORE -> InstallType.PLAY_STORE
                    InstallType.ROOT_PLAY_STORE -> InstallType.ROOT_PLAY_STORE
                    InstallType.CUSTOM -> InstallType.CUSTOM
                    else -> InstallType.DEFAULT
                }
                viewModel.updateInstallType(finalPackageName, newInstallType)
                homeViewModel.notifyAppStateChanged(finalPackageName)
            }
            is InstallViewModel.InstallState.Conflict -> {
                conflictPackageName.value = installState.packageName
                showSignatureConflictDialog.value = true
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

    // Installer selection dialog (shown when promptInstallerOnInstall is enabled)
    if (installViewModel.showInstallerSelectionDialog) {
        val options = remember { installViewModel.getInstallerOptions() }
        val primaryToken = remember { installViewModel.getPrimaryInstallerToken() }
        InstallerSelectionDialog(
            title = stringResource(R.string.installer_title),
            options = options,
            selected = primaryToken,
            onDismiss = installViewModel::dismissInstallerSelectionDialog,
            onConfirm = { token ->
                installViewModel.proceedWithSelectedInstaller(token)
            },
            onOpenShizuku = installViewModel::openShizukuApp,
            shizukuStatusProvider = installViewModel::getShizukuStatus,
            onRequestShizukuPermission = installViewModel::requestShizukuPermission
        )
    }

    // Sub-dialogs
    if (showAppliedPatchesDialog.value && appliedPatches != null) {
        AppliedPatchesDialog(
            appLabel = appLabel,
            packageName = installedApp?.originalPackageName ?: packageName,
            bundles = appliedBundles,
            settingsViewModel = settingsViewModel,
            onDismiss = { showAppliedPatchesDialog.value = false }
        )
    }

    // Mount warning dialog
    if (showMountWarningDialog.value) {
        MountWarningDialog(
            onConfirm = {
                showMountWarningDialog.value = false
                pendingMountWarningAction.value?.invoke()
                pendingMountWarningAction.value = null
            },
            onDismiss = {
                showMountWarningDialog.value = false
                pendingMountWarningAction.value = null
            }
        )
    }

    UninstallConfirmDialog(
        show = showUninstallConfirm.value,
        onConfirm = {
            viewModel.uninstall()
            showUninstallConfirm.value = false
        },
        onDismiss = { showUninstallConfirm.value = false }
    )

    SignatureConflictDialog(
        show = showSignatureConflictDialog.value,
        onUninstall = {
            showSignatureConflictDialog.value = false
            conflictPackageName.value?.let {
                installViewModel.requestUninstall(it, installAfterUninstall = true)
            }
        },
        onDismiss = {
            showSignatureConflictDialog.value = false
            installViewModel.resetInstallState()
        }
    )

    DeleteConfirmDialog(
        show = showDeleteDialog.value,
        isSavedOnly = installedApp?.installType == InstallType.SAVED,
        appInfo = viewModel.appInfo,
        appLabel = viewModel.appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString(),
        onConfirm = {
            viewModel.removeAppCompletely()
            showDeleteDialog.value = false
        },
        onDismiss = {
            showDeleteDialog.value = false
        }
    )

    // Patch flow always starts with onTriggerPatchFlow → showPatchDialog → ApkAvailabilityDialog,
    // where the user picks the APK source. Expert mode dialog opens after APK selection.
    // We do NOT call onDismiss() here. InstalledAppInfoDialog stays open (hidden behind
    // ApkAvailabilityDialog) and is dismissed in HomeViewModel.proceedWithPatching() right
    // before navigating to PatcherScreen. This eliminates the flash of background that would
    // appear between closing this dialog and opening the next one
    fun handlePatchClick() {
        onTriggerPatchFlow(viewModel.installedApp?.originalPackageName ?: return)
    }

    // Main Dialog
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = null,
        dismissOnClickOutside = true,
        padding = DialogPadding.None,
        footer = null,
        onEntered = { entered.value = true }
    ) {
        if (isLoading || installedApp == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PulsingLogoIndicator()
            }
        } else {
            val windowSize = rememberWindowSize()
            if (isLandscape()) {
                // Landscape layout: left sidebar has actions, right panel has header + info
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left sidebar: centered buttons (scrollable when height is small)
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = MorpheDefaults.ContentPadding)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val availableHeight = maxHeight
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = availableHeight)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing, Alignment.CenterVertically)
                            ) {
                                StaggeredItem(entered = entered.value, index = 1) {
                                    ActionsSection(
                                        viewModel = viewModel,
                                        installViewModel = installViewModel,
                                        installedApp = installedApp,
                                        availablePatches = availablePatches,
                                        isInstalling = isInstalling,
                                        mountOperation = mountOperation,
                                        hasUpdate = hasUpdate,
                                        accentColor = appAccentColor,
                                        onPatchClick = { handlePatchClick() },
                                        onUninstall = { showUninstallConfirm.value = true },
                                        onDelete = { showDeleteDialog.value = true },
                                        onExport = { exportSavedLauncher.launch(exportFileName) },
                                        onShowMountWarning = { action ->
                                            pendingMountWarningAction.value = action
                                            showMountWarningDialog.value = true
                                        },
                                        singleColumn = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (!viewModel.hasOriginalApk) {
                                    StaggeredItem(entered = entered.value, index = 2) {
                                        InfoBadge(
                                            text = stringResource(R.string.home_app_info_no_saved_apk),
                                            style = InfoBadgeStyle.Warning,
                                            icon = Icons.Outlined.Info,
                                            isExpanded = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                StaggeredItem(entered = entered.value, index = 1) {
                                    MorpheDialogOutlinedButton(
                                        text = stringResource(R.string.close),
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(vertical = MorpheDefaults.ContentPadding))

                    // Right panel: header + banners + info
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = MorpheDefaults.ContentPaddingMedium),
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                    ) {
                        item(contentType = "hero") {
                            AppHeroHeader(
                                appInfo = appInfo,
                                packageName = packageName,
                                installedApp = installedApp,
                                accentColor = appAccentColor,
                                compact = windowSize.widthSizeClass == WindowWidthSizeClass.Expanded,
                                modifier = Modifier
                                    .padding(horizontal = MorpheDefaults.ContentPadding)
                                    .clip(RoundedCornerShape(bottomStart = MorpheDefaults.CardCornerRadius, bottomEnd = MorpheDefaults.CardCornerRadius))
                            )
                        }
                        item(key = "banners") {
                            Column(
                                modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                            ) {
                                AnimatedVisibility(
                                    visible = viewModel.isAppDeleted,
                                    enter = MorpheAnimations.expandFadeEnter,
                                    exit = MorpheAnimations.shrinkFadeExit
                                ) {
                                    StaggeredItem(entered = entered.value, index = 2) {
                                        WarningBanner(
                                            icon = Icons.Outlined.Warning,
                                            title = stringResource(R.string.home_app_info_app_deleted_warning),
                                            description = stringResource(R.string.home_app_info_app_deleted_description),
                                            buttonText = stringResource(R.string.patch),
                                            buttonIcon = Icons.Outlined.AutoFixHigh,
                                            onClick = { onTriggerPatchFlow(installedApp.originalPackageName) },
                                            accentColor = appAccentColor,
                                            isError = true
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = hasUpdate && !viewModel.isAppDeleted,
                                    enter = MorpheAnimations.expandFadeEnter,
                                    exit = MorpheAnimations.shrinkFadeExit
                                ) {
                                    StaggeredItem(entered = entered.value, index = 3) {
                                        WarningBanner(
                                            icon = Icons.Outlined.Update,
                                            title = stringResource(R.string.home_app_info_patch_update_available),
                                            description = stringResource(R.string.home_app_info_patch_update_available_description),
                                            buttonText = stringResource(R.string.patch),
                                            buttonIcon = Icons.Outlined.AutoFixHigh,
                                            onClick = { onTriggerPatchFlow(installedApp.originalPackageName) },
                                            accentColor = appAccentColor,
                                            isError = false
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            StaggeredItem(entered = entered.value, index = 4) {
                                InfoSection(
                                    installedApp = installedApp,
                                    appliedPatches = appliedPatches,
                                    bundlesUsedSummary = bundlesUsedSummary,
                                    onShowPatches = { showAppliedPatchesDialog.value = true },
                                    accentColor = appAccentColor,
                                    modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding)
                                )
                            }
                        }
                    }
                }
            } else {
                // Single-column layout for phones
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = MorpheDefaults.ContentPaddingMedium)
                    ) {
                        // Hero header
                        item(contentType = "hero") {
                            AppHeroHeader(
                                appInfo = appInfo,
                                packageName = packageName,
                                installedApp = installedApp,
                                accentColor = appAccentColor,
                                modifier = Modifier.clip(RoundedCornerShape(bottomStart = MorpheDefaults.CardCornerRadius, bottomEnd = MorpheDefaults.CardCornerRadius))
                            )
                        }

                        // Stagger index counter: hero header is index 0 (animated independently).
                        // Banner item always occupies index 1 (permanent item, AnimatedVisibility
                        // controls visibility) so subsequent indices are stable regardless of
                        // banner state - avoiding LazyColumn position-key conflicts
                        var staggerIndex = 2

                        // Warning banners (deleted / update)
                        item(key = "banner") {
                            Column {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = viewModel.isAppDeleted,
                                    enter = MorpheAnimations.expandFadeEnter,
                                    exit = MorpheAnimations.shrinkFadeExit
                                ) {
                                    Column {
                                        Spacer(Modifier.height(MorpheDefaults.ItemSpacing))
                                        StaggeredItem(entered = entered.value, index = 1) {
                                            WarningBanner(
                                                icon = Icons.Outlined.Warning,
                                                title = stringResource(R.string.home_app_info_app_deleted_warning),
                                                description = stringResource(R.string.home_app_info_app_deleted_description),
                                                buttonText = stringResource(R.string.patch),
                                                buttonIcon = Icons.Outlined.AutoFixHigh,
                                                onClick = {
                                                    onTriggerPatchFlow(installedApp.originalPackageName)
                                                },
                                                accentColor = appAccentColor,
                                                isError = true,
                                                modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding)
                                            )
                                        }
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = hasUpdate && !viewModel.isAppDeleted,
                                    enter = MorpheAnimations.expandFadeEnter,
                                    exit = MorpheAnimations.shrinkFadeExit
                                ) {
                                    Column {
                                        Spacer(Modifier.height(MorpheDefaults.ItemSpacing))
                                        StaggeredItem(entered = entered.value, index = 1) {
                                            WarningBanner(
                                                icon = Icons.Outlined.Update,
                                                title = stringResource(R.string.home_app_info_patch_update_available),
                                                description = stringResource(R.string.home_app_info_patch_update_available_description),
                                                buttonText = stringResource(R.string.patch),
                                                buttonIcon = Icons.Outlined.AutoFixHigh,
                                                onClick = {
                                                    onTriggerPatchFlow(installedApp.originalPackageName)
                                                },
                                                accentColor = appAccentColor,
                                                isError = false,
                                                modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Info Section
                        val infoIdx = staggerIndex++
                        item {
                            Box(modifier = Modifier.padding(top = MorpheDefaults.ItemSpacing)) {
                                StaggeredItem(entered = entered.value, index = infoIdx) {
                                    InfoSection(
                                        installedApp = installedApp,
                                        appliedPatches = appliedPatches,
                                        bundlesUsedSummary = bundlesUsedSummary,
                                        onShowPatches = { showAppliedPatchesDialog.value = true },
                                        accentColor = appAccentColor,
                                        modifier = Modifier.padding(horizontal = MorpheDefaults.ContentPadding)
                                    )
                                }
                            }
                        }

                        // Actions Section
                        val actionsIdx = staggerIndex++
                        item {
                            StaggeredItem(entered = entered.value, index = actionsIdx) {
                                ActionsSection(
                                    viewModel = viewModel,
                                    installViewModel = installViewModel,
                                    installedApp = installedApp,
                                    availablePatches = availablePatches,
                                    isInstalling = isInstalling,
                                    mountOperation = mountOperation,
                                    hasUpdate = hasUpdate,
                                    accentColor = appAccentColor,
                                    onPatchClick = { handlePatchClick() },
                                    onUninstall = { showUninstallConfirm.value = true },
                                    onDelete = { showDeleteDialog.value = true },
                                    onExport = { exportSavedLauncher.launch(exportFileName) },
                                    onShowMountWarning = { action ->
                                        pendingMountWarningAction.value = action
                                        showMountWarningDialog.value = true
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = MorpheDefaults.ContentPadding)
                                        .padding(top = MorpheDefaults.ItemSpacing)
                                )
                            }
                        }

                        // Info about saved APK availability
                        if (!viewModel.hasOriginalApk) {
                            val idx = staggerIndex
                            item {
                                StaggeredItem(entered = entered.value, index = idx) {
                                    InfoBadge(
                                        text = stringResource(R.string.home_app_info_no_saved_apk),
                                        style = InfoBadgeStyle.Warning,
                                        icon = Icons.Outlined.Info,
                                        isExpanded = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = MorpheDefaults.ContentPadding)
                                            .padding(top = MorpheDefaults.ItemSpacing)
                                    )
                                }
                            }
                        }
                    }
                    StaggeredItem(entered = entered.value, index = 3) {
                        MorpheDialogOutlinedButton(
                            text = stringResource(R.string.close),
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = MorpheDefaults.ContentPadding)
                                .padding(vertical = MorpheDefaults.ItemSpacing)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Color.accentContentColor(alpha: Float): Color =
    if (isExtremeAccent()) MaterialTheme.colorScheme.onSurfaceVariant
    else if (compositeOver(MaterialTheme.colorScheme.surface, alpha)
            .requiresLightContent()) Color.White else Color.Black

/**
 * Unified banner component for warnings and updates.
 */
@Composable
private fun WarningBanner(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonIcon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val baseColor = if (isError) MaterialTheme.colorScheme.error else accentColor
    val containerColor = if (baseColor.isExtremeAccent()) MaterialTheme.colorScheme.surfaceVariant else baseColor.copy(alpha = 0.15f)
    val contentColor = baseColor.accentContentColor(0.15f)
    val borderColor = if (baseColor.isExtremeAccent())
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    else
        baseColor.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorpheDefaults.ItemSpacing))
            .border(1.dp, borderColor, RoundedCornerShape(MorpheDefaults.ItemSpacing))
            .background(containerColor)
            .padding(MorpheDefaults.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with icon
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(MorpheDefaults.ContentPadding)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
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
        PrimaryActionButton(
            action = ActionItem(text = buttonText, icon = buttonIcon, onClick = onClick),
            accentColor = baseColor,
            contentColorOverride = contentColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Hero header for the app info dialog.
 */
@Composable
private fun AppHeroHeader(
    appInfo: PackageInfo?,
    packageName: String,
    installedApp: InstalledApp,
    accentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val onHero = MaterialTheme.colorScheme.onBackground
    val chipBg = if (accentColor.isExtremeAccent()) onHero.copy(alpha = 0.12f) else accentColor.copy(alpha = 0.18f)

    val iconSize = if (compact) 56.dp else 72.dp
    val iconCorner = if (compact) 14.dp else 22.dp

    // Entrance animations (progress-based: 0f -> 1f).
    // One Float per visual group; alpha, offset and scale are derived via lerp
    // to avoid redundant Recomposition subscribers.
    val entered = remember { mutableStateOf(false) }
    val relativeTime = remember(installedApp.patchedAt) { installedApp.patchedAt?.let { getRelativeTimeString(it) } }
    LaunchedEffect(Unit) { entered.value = true }

    // Icon: spring with overshoot (first thing the eye sees, no delay needed).
    val iconProgress by animateFloatAsState(
        targetValue = if (entered.value) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "heroIconProgress"
    )

    // Name + version share one clock; stagger handled inside graphicsLayer via lerp
    val textProgress by animateFloatAsState(
        targetValue = if (entered.value) 1f else 0f,
        animationSpec = tween(durationMillis = 260, delayMillis = 60, easing = EaseOutCubic),
        label = "heroTextProgress"
    )

    // Both chips share one clock; chip 2 uses a clamped sub-range for its offset
    val chipsProgress by animateFloatAsState(
        targetValue = if (entered.value) 1f else 0f,
        animationSpec = tween(durationMillis = 240, delayMillis = 160, easing = EaseOutBack),
        label = "heroChipsProgress"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // Flat tinted background
        val heroBg = if (accentColor.isExtremeAccent())
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
        else
            accentColor.copy(alpha = 0.15f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(heroBg)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = MorpheDefaults.ContentPadding,
                    vertical = MorpheDefaults.ContentPaddingSmall
                )
        ) {
            val (chipIcon, chipLabel) = when (installedApp.installType) {
                InstallType.MOUNT   -> Icons.Outlined.Link to R.string.mount
                InstallType.SHIZUKU -> Icons.Outlined.Terminal to R.string.home_app_info_install_type_shizuku
                InstallType.SHIZUKU_PLAY_STORE -> Icons.Outlined.Terminal to R.string.home_app_info_install_type_shizuku_play_store
                InstallType.PLAY_STORE -> Icons.Outlined.Shop to R.string.home_app_info_install_type_play_store
                InstallType.ROOT_PLAY_STORE -> Icons.Outlined.Security to R.string.home_app_info_install_type_root_play_store
                InstallType.CUSTOM  -> Icons.Outlined.Build to R.string.home_app_info_install_type_custom_installer
                InstallType.SAVED   -> Icons.Outlined.Save to R.string.saved
                InstallType.DEFAULT -> Icons.Outlined.InstallMobile to R.string.home_app_info_install_type_system_installer
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated app icon
                AppIcon(
                    packageInfo = appInfo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconCorner))
                        .graphicsLayer {
                            val s = lerp(0.6f, 1f, iconProgress)
                            scaleX = s
                            scaleY = s
                            alpha = iconProgress.coerceIn(0f, 1f)
                        }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
                ) {
                    // Animated app name (leads textProgress)
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = lerp(40f, 0f, textProgress)
                            alpha = textProgress.coerceIn(0f, 1f)
                        }
                    ) {
                        AppLabel(
                            packageInfo = appInfo,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = onHero
                            ),
                            defaultText = packageName
                        )
                    }
                    // Animated version (slightly behind name via sub-range)
                    Text(
                        text = appInfo?.versionName?.let { "v$it" } ?: installedApp.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHero.copy(alpha = 0.50f),
                        modifier = Modifier.graphicsLayer {
                            val p = ((textProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                            translationX = lerp(40f, 0f, p)
                            alpha = p
                        }
                    )
                }
                // Compact mode: chips column on the right
                if (compact) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            translationY = lerp(20f, 0f, chipsProgress)
                            alpha = chipsProgress.coerceIn(0f, 1f)
                        },
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
                        horizontalAlignment = Alignment.End
                    ) {
                        PillBadge(
                            text = stringResource(chipLabel),
                            icon = chipIcon,
                            containerColor = chipBg,
                            contentColor = onHero
                        )
                        if (relativeTime != null) {
                            PillBadge(
                                text = relativeTime,
                                icon = Icons.Outlined.Schedule,
                                containerColor = chipBg,
                                contentColor = onHero
                            )
                        }
                    }
                }
            }

            // Normal mode: chips on separate row below
            if (!compact) {
                Spacer(Modifier.height(MorpheDefaults.ContentPaddingSmall))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
                ) {
                    // Animated chip 1
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationY = lerp(20f, 0f, chipsProgress)
                            alpha = chipsProgress.coerceIn(0f, 1f)
                        }
                    ) {
                        PillBadge(
                            text = stringResource(chipLabel),
                            icon = chipIcon,
                            containerColor = chipBg,
                            contentColor = onHero
                        )
                    }
                    // Animated chip 2 (sub-range: starts when chip1 is 30% done)
                    if (relativeTime != null) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val p = ((chipsProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                translationY = lerp(20f, 0f, p)
                                alpha = p
                            }
                        ) {
                            PillBadge(
                                text = relativeTime,
                                icon = Icons.Outlined.Schedule,
                                containerColor = chipBg,
                                contentColor = onHero
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps content with a staggered entrance animation.
 * Uses a single progress float (0 to 1); alpha, offsetY and scale are
 * derived via lerp - one Recomposition subscriber instead of three.
 * Each item appears [index] * 60ms after [entered] becomes true.
 */
@Composable
private fun StaggeredItem(
    entered: Boolean,
    index: Int,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,
            delayMillis = index * 60,
            easing = EaseOutCubic
        ),
        label = "itemProgress$index"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = lerp(28f, 0f, progress)
            val s = lerp(0.97f, 1f, progress)
            scaleX = s
            scaleY = s
        }
    ) {
        content()
    }
}

@Composable
private fun InfoSection(
    installedApp: InstalledApp,
    appliedPatches: Map<Int, Set<String>>?,
    bundlesUsedSummary: String,
    onShowPatches: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val totalPatches = appliedPatches?.values?.sumOf { it.size } ?: 0
    val context = LocalContext.current

    // APK size from sourceDir
    val apkSize = remember(installedApp.currentPackageName) {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(installedApp.currentPackageName, 0)

            val bytes = File(
                info.applicationInfo?.sourceDir ?: return@remember null
            ).length()

            formatBytes(bytes)
        } catch (_: Exception) { null }
    }

    val apkAbis = remember(installedApp.currentPackageName) {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(installedApp.currentPackageName, 0)
            val sourceDir = info.applicationInfo?.sourceDir ?: return@remember emptyList<String>()
            NativeLibStripper.extractAbisFromApk(File(sourceDir))
        } catch (_: Exception) { emptyList() }
    }

    MorpheCard(
        cornerRadius = MorpheDefaults.ItemSpacing,
        borderWidth = 1.dp,
        modifier = modifier
    ) {
        Column {
            InfoRow(
                icon = Icons.Outlined.Inventory2,
                label = stringResource(R.string.package_name),
                value = installedApp.currentPackageName
            )

            if (installedApp.originalPackageName != installedApp.currentPackageName) {
                MorpheSettingsDivider()
                InfoRow(
                    icon = Icons.Outlined.Category,
                    label = stringResource(R.string.home_app_info_original_package_name),
                    value = installedApp.originalPackageName
                )
            }

            if (apkSize != null) {
                MorpheSettingsDivider()
                InfoRow(
                    icon = Icons.Outlined.SdCard,
                    label = stringResource(R.string.home_app_info_apk_size),
                    value = apkSize
                )
            }

            if (apkAbis.isNotEmpty()) {
                MorpheSettingsDivider()
                InfoRow(
                    icon = Icons.Outlined.Memory,
                    label = stringResource(R.string.home_app_info_cpu_arch),
                    value = apkAbis.joinToString(" • ")
                )
            }

            if (totalPatches > 0) {
                MorpheSettingsDivider()
                InfoRowWithAction(
                    icon = Icons.Outlined.DoneAll,
                    label = stringResource(R.string.home_app_info_applied_patches),
                    value = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches),
                    accentColor = accentColor,
                    onAction = onShowPatches
                )
            }

            if (bundlesUsedSummary.isNotBlank()) {
                MorpheSettingsDivider()
                InfoRow(
                    icon = Icons.Outlined.Source,
                    label = stringResource(R.string.home_app_info_patch_source_used),
                    value = bundlesUsedSummary
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MorpheDefaults.ItemSpacing, vertical = MorpheDefaults.ContentPaddingSmall),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoRowWithAction(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MorpheDefaults.ItemSpacing, vertical = MorpheDefaults.ContentPaddingSmall),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ActionPillButton(
            onClick = onAction,
            icon = Icons.AutoMirrored.Outlined.List,
            contentDescription = stringResource(R.string.view),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = accentColor.copy(alpha = 0.18f),
                contentColor = accentColor.accentContentColor(0.18f)
            )
        )
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
    accentColor: Color,
    onPatchClick: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShowMountWarning: (action: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    singleColumn: Boolean = false
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

    // Show install/reinstall from saved copy whenever the patched APK is available
    if (viewModel.hasSavedCopy) {
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
                                onPersistApp = { _, _ ->
                                    // Callback will be called after successful installation
                                    // The LaunchedEffect handler will update the installation type
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

    when (installedApp.installType) {
        InstallType.MOUNT -> {
            val isMountLoading = mountOperation != null
            if (viewModel.isMounted) {
                // Remount button
                secondaryActions.add(
                    ActionItem(
                        text = stringResource(R.string.remount),
                        icon = Icons.Outlined.Refresh,
                        onClick = {
                            installViewModel.remount(
                                packageName = installedApp.currentPackageName,
                                version = installedApp.version
                            )
                        },
                        isLoading = isMountLoading
                    )
                )
                // Unmount button
                secondaryActions.add(
                    ActionItem(
                        text = stringResource(R.string.unmount),
                        icon = Icons.Outlined.LinkOff,
                        onClick = {
                            installViewModel.unmount(
                                packageName = installedApp.currentPackageName
                            )
                        },
                        isLoading = isMountLoading
                    )
                )
            } else {
                // Mount button
                secondaryActions.add(
                    ActionItem(
                        text = stringResource(R.string.mount),
                        icon = Icons.Outlined.Link,
                        onClick = {
                            installViewModel.mount(
                                packageName = installedApp.currentPackageName,
                                version = installedApp.version
                            )
                        },
                        isLoading = isMountLoading
                    )
                )
            }
        }
        else -> Unit
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

    Column(modifier = modifier.animateContentSize(animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Primary actions row
        if (primaryActions.isNotEmpty()) {
            primaryActions.forEach { action ->
                PrimaryActionButton(
                    action = action,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Secondary + destructive
        val tileActions = secondaryActions + destructiveActions
        if (tileActions.isNotEmpty()) {
            if (singleColumn) {
                tileActions.forEach { action ->
                    TileActionButton(
                        action = action,
                        horizontal = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                tileActions.chunked(2).forEach { rowActions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowActions.forEachIndexed { _, action ->
                            TileActionButton(
                                action = action,
                                modifier = if (rowActions.size == 1) Modifier.fillMaxWidth()
                                else Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
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

/** Shared loading/icon content used by action buttons. */
@Composable
private fun LoadingOrIcon(isLoading: Boolean, action: ActionItem, tint: Color) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = tint
        )
    } else {
        Icon(action.icon, null, modifier = Modifier.size(22.dp))
    }
}

/** Shared Surface shell for all action buttons. Color computation lives in callers. */
@Composable
private fun ActionButton(
    action: ActionItem,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    Surface(
        onClick = action.onClick,
        enabled = action.enabled && !action.isLoading,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        if (vertical) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingOrIcon(action.isLoading, action, contentColor)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = action.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MorpheDefaults.ContentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoadingOrIcon(action.isLoading, action, contentColor)
                Spacer(Modifier.width(MorpheDefaults.ContentPaddingSmall))
                Text(
                    text = action.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Full-width primary button with accent color palette. */
@Composable
private fun PrimaryActionButton(
    action: ActionItem,
    accentColor: Color,
    modifier: Modifier = Modifier,
    contentColorOverride: Color? = null
) {
    val containerColor = if (accentColor.isExtremeAccent())
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    else
        accentColor.copy(alpha = 0.18f)
    ActionButton(
        action = action,
        containerColor = containerColor,
        contentColor = contentColorOverride ?: accentColor.accentContentColor(0.18f),
        borderColor = if (accentColor.isExtremeAccent())
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        else
            accentColor.copy(alpha = 0.35f),
        modifier = modifier
    )
}

/** Tile button - vertical (icon+label) for grids, horizontal (icon+label) for lists. */
@Composable
private fun TileActionButton(
    action: ActionItem,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    ActionButton(
        action = action,
        containerColor = when {
            action.isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            !action.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        contentColor = when {
            action.isDestructive -> MaterialTheme.colorScheme.error
            !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        },
        borderColor = when {
            action.isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            !action.enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        modifier = modifier,
        vertical = !horizontal
    )
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
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
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
private fun SignatureConflictDialog(
    show: Boolean,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_conflict_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.uninstall),
                onPrimaryClick = onUninstall,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(R.string.patcher_conflict_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

@Composable
private fun AppliedPatchesDialog(
    appLabel: String,
    packageName: String,
    bundles: List<AppliedPatchBundleUi>,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var bundleOptionsMap by remember { mutableStateOf<Map<Int, Map<String, Map<String, Any?>>>>(emptyMap()) }
    LaunchedEffect(bundles) {
        bundleOptionsMap = bundles.associate { bundle ->
            bundle.uid to settingsViewModel.loadPatchDetails(packageName, bundle.uid).optionsMap
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            HeroInfoCard(
                icon = Icons.Outlined.Extension,
                title = appLabel,
                subtitle = {
                    if (bundles.size == 1) {
                        Text(
                            text = bundles[0].title,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    } else {
                        Text(
                            text = pluralStringResource(R.plurals.source_count, bundles.size, bundles.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                }
            )

            bundles.forEach { bundle ->
                val bundleOptions = bundleOptionsMap[bundle.uid] ?: emptyMap()
                val patchCount = bundle.patchInfos.size + bundle.fallbackNames.size

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
                ) {
                    PatchBundleSection(
                        title = stringResource(R.string.home_app_info_applied_patches),
                        version = if (bundles.size > 1) bundle.title else null,
                        count = patchCount
                    ) {
                        bundle.patchInfos.forEach { patch ->
                            PatchNameRow(name = patch.name)
                        }
                        bundle.fallbackNames.forEach { patchName ->
                            PatchNameRow(name = patchName, dimmed = true)
                        }
                    }

                    if (bundleOptions.isNotEmpty()) {
                        PatchBundleSection(
                            title = stringResource(R.string.settings_system_patch_options_section),
                            count = bundleOptions.size
                        ) {
                            bundleOptions.entries.forEach { (patchName, options) ->
                                PatchOptionsGroup(patchName = patchName, options = options)
                            }
                        }
                    }
                }
            }
        }
    }
}
