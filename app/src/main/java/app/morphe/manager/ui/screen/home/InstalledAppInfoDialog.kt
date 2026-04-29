/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.util.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
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
@SuppressLint("LocalContextGetResourceValueCheck")
@Composable
fun InstalledAppInfoDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onTriggerPatchFlow: (originalPackageName: String) -> Unit,
    homeViewModel: HomeViewModel,
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
    val pendingMountWarningAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Content entrance animation
    val entered = remember { mutableStateOf(false) }

    // Bundle data
    val appliedBundles by viewModel.appliedBundles.collectAsStateWithLifecycle()
    val bundlesUsedSummary by viewModel.bundlesUsedSummary.collectAsStateWithLifecycle()
    val availablePatches by viewModel.availablePatches.collectAsStateWithLifecycle()

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
            onOpenShizuku = installViewModel::openShizukuApp
        )
    }

    // Sub-dialogs
    if (showAppliedPatchesDialog.value && appliedPatches != null) {
        AppliedPatchesDialog(bundles = appliedBundles, onDismiss = { showAppliedPatchesDialog.value = false })
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

    // Expert Mode Repatch Dialog is rendered by HomeDialogs via homeViewModel.showExpertModeDialog.
    // Patch flow always starts with onTriggerPatchFlow → showPatchDialog → ApkAvailabilityDialog,
    // where the user picks the APK source. Expert mode dialog opens after APK selection.
    fun handlePatchClick() {
        onDismiss()
        onTriggerPatchFlow(viewModel.installedApp?.originalPackageName ?: return)
    }

    // Main Dialog
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = null,
        dismissOnClickOutside = true,
        noPadding = true,
        footer = null,
        onEntered = { entered.value = true }
    ) {
        if (isLoading || installedApp == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            val windowSize = rememberWindowSize()
            if (windowSize.useTwoColumnLayout) {
                // Tablet layout: left column has header + info, right column has actions
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left column: header + info section
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(contentType = "hero") {
                            AppHeroHeader(
                                appInfo = appInfo,
                                packageName = packageName,
                                installedApp = installedApp,
                                accentColor = appAccentColor,
                                compact = true,
                            )
                        }
                        item {
                            StaggeredItem(entered = entered.value, index = 1) {
                                InfoSection(
                                    installedApp = installedApp,
                                    appliedPatches = appliedPatches,
                                    bundlesUsedSummary = bundlesUsedSummary,
                                    onShowPatches = { showAppliedPatchesDialog.value = true },
                                )
                            }
                        }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }

                    // Right column: banners + actions centered vertically
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 20.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (viewModel.isAppDeleted) {
                            StaggeredItem(entered = entered.value, index = 1) {
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
                        }
                        if (hasUpdate && !viewModel.isAppDeleted) {
                            StaggeredItem(entered = entered.value, index = 2) {
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
                        }
                        StaggeredItem(entered = entered.value, index = 3) {
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
                                }
                            )
                        }
                        if (!viewModel.hasOriginalApk) {
                            StaggeredItem(entered = entered.value, index = 4) {
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
            } else {
                // Single-column layout for phones
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero header
                    item(contentType = "hero") {
                        AppHeroHeader(
                            appInfo = appInfo,
                            packageName = packageName,
                            installedApp = installedApp,
                            accentColor = appAccentColor,
                        )
                    }

                    // Stagger index counter: hero header is index 0 (animated independently),
                    // each subsequent visible item increments so the delay chain is always correct
                    // regardless of which optional banners are shown.
                    var staggerIndex = 1

                    // Deleted app warning banner
                    if (viewModel.isAppDeleted) {
                        val idx = staggerIndex++
                        item {
                            StaggeredItem(entered = entered.value, index = idx) {
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
                                    isError = true,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }

                    // Update available banner
                    if (hasUpdate && !viewModel.isAppDeleted) {
                        val idx = staggerIndex++
                        item {
                            StaggeredItem(entered = entered.value, index = idx) {
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
                                    isError = false,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }

                    // Info Section
                    val infoIdx = staggerIndex++
                    item {
                        StaggeredItem(entered = entered.value, index = infoIdx) {
                            InfoSection(
                                installedApp = installedApp,
                                appliedPatches = appliedPatches,
                                bundlesUsedSummary = bundlesUsedSummary,
                                onShowPatches = { showAppliedPatchesDialog.value = true },
                            )
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
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }

                    // Info about saved APK availability
                    if (!viewModel.hasOriginalApk) {
                        val idx = staggerIndex++
                        item {
                            StaggeredItem(entered = entered.value, index = idx) {
                                InfoBadge(
                                    text = stringResource(R.string.home_app_info_no_saved_apk),
                                    style = InfoBadgeStyle.Warning,
                                    icon = Icons.Outlined.Info,
                                    isExpanded = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }

                    // Bottom nav bar padding
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

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
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with icon
        Row(
            modifier = Modifier.wrapContentWidth(),
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
            accentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
    val isExtremeAccent = accentColor.luminance() !in 0.04f..0.92f
    val chipBg = if (isExtremeAccent) onHero.copy(alpha = 0.12f) else accentColor.copy(alpha = 0.18f)

    val iconSize = if (compact) 56.dp else 88.dp
    val iconCorner = if (compact) 14.dp else 22.dp
    val topPadding = if (compact) 8.dp else 12.dp
    val bottomPadding = if (compact) 10.dp else 16.dp
    val chipSpacerHeight = if (compact) 8.dp else 14.dp

    // Entrance animations (progress-based: 0f -> 1f).
    // One Float per visual group; alpha, offset and scale are derived via lerp
    // to avoid redundant Recomposition subscribers.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // Icon: spring with overshoot (first thing the eye sees, no delay needed).
    val iconProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "heroIconProgress"
    )

    // Name + version share one clock; stagger handled inside graphicsLayer via lerp
    val textProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 260, delayMillis = 60, easing = EaseOutCubic),
        label = "heroTextProgress"
    )

    // Both chips share one clock; chip 2 uses a clamped sub-range for its offset
    val chipsProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 240, delayMillis = 160, easing = EaseOutBack),
        label = "heroChipsProgress"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // Simple tinted background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(accentColor.copy(alpha = 0.12f))
        )
        // Radial glow from center-top for depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = bottomPadding)
        ) {
            val (chipIcon, chipLabel) = when (installedApp.installType) {
                InstallType.MOUNT   -> Icons.Outlined.Link to R.string.mount
                InstallType.SHIZUKU -> Icons.Outlined.Terminal to R.string.home_app_info_install_type_shizuku
                InstallType.CUSTOM  -> Icons.Outlined.Build to R.string.home_app_info_install_type_custom_installer
                InstallType.SAVED   -> Icons.Outlined.Save to R.string.saved
                InstallType.DEFAULT -> Icons.Outlined.InstallMobile to R.string.home_app_info_install_type_system_installer
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        InfoChip(icon = chipIcon, text = stringResource(chipLabel), bg = chipBg, fg = onHero)
                        installedApp.patchedAt?.let { ts ->
                            InfoChip(
                                icon = Icons.Outlined.Schedule,
                                text = getRelativeTimeString(ts),
                                bg = chipBg,
                                fg = onHero
                            )
                        }
                    }
                }
            }

            // Normal mode: chips on separate row below
            if (!compact) {
                Spacer(Modifier.height(chipSpacerHeight))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Animated chip 1
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationY = lerp(20f, 0f, chipsProgress)
                            alpha = chipsProgress.coerceIn(0f, 1f)
                        }
                    ) {
                        InfoChip(icon = chipIcon, text = stringResource(chipLabel), bg = chipBg, fg = onHero)
                    }
                    // Animated chip 2 (sub-range: starts when chip1 is 30% done)
                    installedApp.patchedAt?.let { ts ->
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val p = ((chipsProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                translationY = lerp(20f, 0f, p)
                                alpha = p
                            }
                        ) {
                            InfoChip(
                                icon = Icons.Outlined.Schedule,
                                text = getRelativeTimeString(ts),
                                bg = chipBg,
                                fg = onHero
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
private fun InfoChip(icon: ImageVector, text: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun InfoSection(
    installedApp: InstalledApp,
    appliedPatches: Map<Int, Set<String>>?,
    bundlesUsedSummary: String,
    onShowPatches: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        InfoRow(
            icon = Icons.Outlined.Inventory2,
            label = stringResource(R.string.package_name),
            value = installedApp.currentPackageName
        )

        if (installedApp.originalPackageName != installedApp.currentPackageName) {
            InfoRow(
                icon = Icons.Outlined.Category,
                label = stringResource(R.string.home_app_info_original_package_name),
                value = installedApp.originalPackageName
            )
        }

        if (apkSize != null) {
            InfoRow(
                icon = Icons.Outlined.SdCard,
                label = stringResource(R.string.home_app_info_apk_size),
                value = apkSize
            )
        }

        if (totalPatches > 0) {
            InfoRowWithAction(
                icon = Icons.Outlined.DoneAll,
                label = stringResource(R.string.home_app_info_applied_patches),
                value = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches),
                onAction = onShowPatches
            )
        }

        if (bundlesUsedSummary.isNotBlank()) {
            InfoRow(
                icon = Icons.Outlined.Source,
                label = stringResource(R.string.home_app_info_patch_source_used),
                value = bundlesUsedSummary
            )
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
            .padding(vertical = 8.dp),
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
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            contentDescription = stringResource(R.string.view)
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
    modifier: Modifier = Modifier
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

    // Show install/reinstall from saved copy when:
    // - installType is SAVED (normal saved app flow), or
    // - app was deleted from device but a saved patched APK still exists
    val showInstallFromSaved = viewModel.hasSavedCopy &&
            (installedApp.installType == InstallType.SAVED || viewModel.isAppDeleted)

    when {
        showInstallFromSaved -> {
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
        installedApp.installType == InstallType.SAVED -> Unit // hasSavedCopy is false, nothing to show
        else -> Unit
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        // Secondary + destructive - compact tile grid (2 per row)
        val tileActions = secondaryActions + destructiveActions
        if (tileActions.isNotEmpty()) {
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

/** Full-width primary button. */
@Composable
private fun PrimaryActionButton(
    action: ActionItem,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val dialogBg = MaterialTheme.colorScheme.surface
    val safeAccent = accentColor.ensureContrast(dialogBg)
    val onAccent = if (safeAccent.luminance() > 0.35f) Color(0xFF1A1A1A) else Color.White
    Surface(
        onClick = action.onClick,
        enabled = action.enabled && !action.isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = safeAccent,
        contentColor = onAccent
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoadingOrIcon(action.isLoading, action, onAccent)
            Spacer(Modifier.width(10.dp))
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

/** Square-ish tile button - icon on top, label below. Used for secondary/destructive actions. */
@Composable
private fun TileActionButton(
    action: ActionItem,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        action.isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        !action.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = when {
        action.isDestructive -> MaterialTheme.colorScheme.error
        !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Surface(
        onClick = action.onClick,
        enabled = action.enabled && !action.isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingOrIcon(action.isLoading, action, contentColor)
            Spacer(Modifier.height(5.dp))
            Text(
                text = action.text,
                style = MaterialTheme.typography.labelMedium,
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
