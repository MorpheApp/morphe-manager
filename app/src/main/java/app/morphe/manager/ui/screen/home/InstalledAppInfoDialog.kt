/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.*
import app.morphe.manager.domain.bundles.AppVersionStatus
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.bundles.versionStatus
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.util.NativeLibs
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.ui.viewmodel.labelRes
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** How a bundle that patched an app is named and versioned in its information dialog. */
data class AppliedBundleAttribution(
    val title: String,
    val version: String?
)

/**
 * Describes the bundle from the live source first, then from what patching recorded.
 * An internal uid is never part of the answer, so a deleted source reads as one.
 */
fun resolveAppliedBundleAttribution(
    sourceTitle: String?,
    bundleName: String?,
    bundleVersion: String?,
    storedVersion: String?,
    recorded: SelectionPayload.BundleSelection?,
    fallbackTitle: String
): AppliedBundleAttribution = AppliedBundleAttribution(
    title = sourceTitle?.takeUnless { it.isBlank() }
        ?: bundleName?.takeUnless { it.isBlank() }
        ?: recorded?.bundleName?.takeUnless { it.isBlank() }
        ?: fallbackTitle,
    version = storedVersion?.takeUnless { it.isBlank() }
        ?: recorded?.bundleVersion?.takeUnless { it.isBlank() }
        ?: bundleVersion?.takeUnless { it.isBlank() }
)

/**
 * Dialog for installed app info and actions.
 */
@Composable
fun InstalledAppInfoDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onTriggerPatchFlow: (originalPackageName: String, repatchedPackageName: String?) -> Unit,
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
    val mountStageLabel = installState.stageLabel()

    // Get update status from the shared HomeViewModel instance
    val appUpdates by homeViewModel.apps.appUpdatesAvailable.collectAsStateWithLifecycle()
    val appUpdate = appUpdates?.get(packageName)
    val hasUpdate = appUpdate != null
    // What the install itself is in speaks louder than what is pending for it, so a record in one
    // of those states is described by that rather than by the work waiting on it
    val isSettledInstall = !viewModel.isAppDeleted &&
            !viewModel.isInstallStateNotPatched &&
            !viewModel.isInstallStateUnknown
    val patchUpdatePending = hasUpdate && isSettledInstall

    // Where the install stands against the newest version the enabled sources cover. Keyed by the
    // package the sources know it as, which is the original one for a build that was renamed.
    val catalogPackageName = installedApp?.originalPackageName ?: packageName
    val supportedVersions by homeViewModel.recommendedVersionsFlow.collectAsStateWithLifecycle()
    val ignoredVersions by homeViewModel.apps.ignoredAppVersions.collectAsStateWithLifecycle()
    val supportedVersion = supportedVersions[catalogPackageName]
    val ignoredVersion = ignoredVersions[catalogPackageName]
    val installedVersionStatus = remember(supportedVersion, ignoredVersion, appInfo, installedApp) {
        versionStatus(
            installedVersion = appInfo?.versionName ?: installedApp?.version,
            supported = supportedVersion,
            ignoredVersion = ignoredVersion
        )
    }
    val versionBehind = installedVersionStatus?.takeIf { it.isBehind && isSettledInstall }
    // An install past everything the sources cover is regularly one the app updated itself out
    // from under, which is the record the unpatched banner is already about
    val versionAhead = installedVersionStatus?.takeIf { !it.isBehind && !viewModel.isAppDeleted }
    // Both reasons to rebuild are answered by one banner, which then carries the patch button
    // the actions below would otherwise repeat
    val showsRebuildBanner = patchUpdatePending || versionBehind != null
    // Spent on the version it names, so the banner comes back for whatever the sources support next
    val onIgnoreVersion = versionBehind?.let { status ->
        { homeViewModel.apps.ignoreSupportedVersion(catalogPackageName, status.supportedVersion) }
    }
    // Offered only while the turned-down version is still the one on offer, since a newer one
    // brings the banner back on its own and leaves nothing to undo
    val onStopIgnoringVersion = ignoredVersion
        ?.takeIf { it == supportedVersion?.version }
        ?.let { { homeViewModel.apps.stopIgnoringSupportedVersion(catalogPackageName) } }

    // Accent color resolution order: bundle metadata (appIconColor) -> default. Read from every
    // source, a disabled one included, so the app keeps its color with nothing left to patch it.
    // originalPackageName needed because metadata is keyed by original pkg, not patched.
    val bundleAppMetadata by homeViewModel.bundleAppMetadataFlow.collectAsStateWithLifecycle()
    val appAccentColor = rememberAppColor(viewModel.installedApp?.originalPackageName ?: packageName)
        ?: KnownApps.DEFAULT_DOWNLOAD_COLOR
    val infoAccentColor = ThemeTraitsDefaults.accentColor(appAccentColor)

    // Dialog states
    val showUninstallConfirm = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showAppliedPatchesDialog = remember { mutableStateOf(false) }
    val showAppLinksDialog = remember { mutableStateOf(false) }

    // The link selection is only ever changed on a system screen, which is left by coming back here
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAppLinks()
    }
    val changelogRequest = remember { mutableStateOf<BundleChangelogRequest?>(null) }
    val showMountWarningDialog = remember { mutableStateOf(false) }
    val signatureConflict = remember { mutableStateOf<InstallViewModel.InstallState.Conflict?>(null) }
    val pendingMountWarningAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Content entrance animation
    val entered = remember { mutableStateOf(false) }

    // Bundle data
    val sources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
    // Only a remote source has a changelog to read the pending update from
    val updateChangelogRequest = appUpdate
        ?.takeIf { update -> sources.any { it.uid == update.bundleUid && it is RemotePatchBundle } }
        ?.let {
            BundleChangelogRequest(
                bundleUid = it.bundleUid,
                sinceVersion = it.patchedWithVersion,
                subject = it.subject
            )
        }
    val onShowUpdateChangelog: (() -> Unit)? = updateChangelogRequest?.let { request ->
        { changelogRequest.value = request }
    }
    val appliedBundles by viewModel.appliedBundles.collectAsStateWithLifecycle()
    val bundlesUsedSummary by viewModel.bundlesUsedSummary.collectAsStateWithLifecycle()
    val availablePatches by viewModel.availablePatches.collectAsStateWithLifecycle()

    // Same order the home card resolves its name in, so a record reads the same in both places
    val allBundleAppMetadata by homeViewModel.allBundleAppMetadataFlow.collectAsStateWithLifecycle()
    val appLabel = remember(appInfo, installedApp, bundleAppMetadata, allBundleAppMetadata, packageName) {
        val original = installedApp?.originalPackageName ?: packageName
        appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: bundleAppMetadata[original]?.displayName
            ?: allBundleAppMetadata[original]?.displayName
            ?: installedApp?.appLabel
            ?: KnownApps.getAppName(packageName)
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
        }
    }

    // Set back click handler
    SideEffect {
        viewModel.onBackClick = onDismiss
        viewModel.onAppStateChanged = { pkg -> homeViewModel.apps.notifyAppStateChanged(pkg) }
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
                homeViewModel.apps.notifyAppStateChanged(finalPackageName)
            }
            is InstallViewModel.InstallState.Conflict -> {
                signatureConflict.value = installState
            }
            is InstallViewModel.InstallState.Error -> {
                // Show error toast
                context.toast(installState.message)
            }
            else -> {}
        }
    }

    // Head of the delete and uninstall confirmations
    @Composable
    fun AppConfirmSubject() {
        ConfirmSubject(name = appLabel) { modifier ->
            AppIcon(
                packageInfo = viewModel.appInfo,
                packageName = packageName,
                contentDescription = null,
                placeholderGradientColors = listOf(infoAccentColor),
                modifier = modifier
            )
        }
    }

    // Everything this dialog opens wears the app's color, as the dialog itself does
    ProvideAccent(infoAccentColor) {
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
                appInfo = appInfo,
                accentColor = appAccentColor,
                packageName = installedApp?.originalPackageName ?: packageName,
                bundles = appliedBundles,
                settingsViewModel = settingsViewModel,
                onDismiss = { showAppliedPatchesDialog.value = false }
            )
        }

        val appLinksStatus = viewModel.appLinksStatus
        if (showAppLinksDialog.value && appLinksStatus?.hasSupportedLinks == true) {
            AppLinksDialog(
                appLabel = appLabel,
                appInfo = appInfo,
                accentColor = appAccentColor,
                packageName = installedApp?.currentPackageName ?: packageName,
                status = appLinksStatus,
                onRefresh = viewModel::refreshAppLinks,
                onDismiss = { showAppLinksDialog.value = false }
            )
        }

        // What the pending patch update changed for this app
        BundleChangelogHost(
            request = changelogRequest.value,
            sources = sources,
            onDismissRequest = { changelogRequest.value = null }
        )

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

        if (showUninstallConfirm.value) {
            ConfirmDialog(
                title = stringResource(R.string.uninstall),
                message = stringResource(R.string.home_app_info_uninstall_app_confirmation),
                primaryText = stringResource(R.string.uninstall),
                subject = { AppConfirmSubject() },
                onConfirm = {
                    viewModel.uninstall()
                    showUninstallConfirm.value = false
                },
                onDismiss = { showUninstallConfirm.value = false }
            )
        }

        signatureConflict.value?.let { conflict ->
            SignatureConflictDialog(
                title = stringResource(R.string.patcher_conflict_title),
                message = stringResource(R.string.patcher_conflict_subtitle),
                onUninstall = {
                    signatureConflict.value = null
                    installViewModel.requestUninstall(conflict.packageName, installAfterUninstall = true)
                },
                onDismiss = {
                    signatureConflict.value = null
                    installViewModel.resetInstallState()
                },
                onIgnore = if (conflict.canIgnoreSignatureMismatch) {
                    {
                        signatureConflict.value = null
                        installViewModel.installIgnoringSignatureMismatch()
                    }
                } else {
                    null
                }
            )
        }

        if (showDeleteDialog.value) {
            val isSavedOnly = installedApp?.installType == InstallType.SAVED
            val items = buildList {
                if (isSavedOnly) {
                    add(ConfirmItem(Icons.Outlined.Delete, stringResource(R.string.home_app_info_delete_item_patched_apk)))
                } else {
                    // A record can outlive both archives, so only list the files that are there
                    add(ConfirmItem(Icons.Outlined.Storage, stringResource(R.string.home_app_info_delete_item_database)))
                    if (viewModel.hasSavedCopy) {
                        add(ConfirmItem(Icons.Outlined.Android, stringResource(R.string.home_app_info_delete_item_patched_apk)))
                    }
                    if (viewModel.deletesOriginalApk) {
                        add(ConfirmItem(Icons.Outlined.FilePresent, stringResource(R.string.home_app_info_delete_item_original_apk)))
                    }
                }
            }
            ConfirmDialog(
                title = stringResource(R.string.delete),
                primaryText = stringResource(R.string.delete),
                onConfirm = {
                    viewModel.removeAppCompletely()
                    showDeleteDialog.value = false
                },
                onDismiss = { showDeleteDialog.value = false },
                subject = { AppConfirmSubject() },
                items = items,
                itemsTitle = stringResource(R.string.home_app_info_remove_app_warning),
                notice = stringResource(R.string.home_app_info_delete_preservation_note).takeUnless { isSavedOnly }
            )
        }
    }

    // Patch flow always starts with onTriggerPatchFlow → showPatchDialog → ApkAvailabilityDialog,
    // where the user picks the APK source. Expert mode dialog opens after APK selection.
    // We do NOT call onDismiss() here. InstalledAppInfoDialog stays open (hidden behind
    // ApkAvailabilityDialog) and is dismissed in HomeViewModel.proceedWithPatching() right
    // before navigating to PatcherScreen. This eliminates the flash of background that would
    // appear between closing this dialog and opening the next one
    fun handlePatchClick() {
        val app = viewModel.installedApp ?: return
        onTriggerPatchFlow(app.originalPackageName, app.trackingKey)
    }

    // Main Dialog
    AppDialog(
        onDismissRequest = onDismiss,
        title = null,
        dismissOnClickOutside = true,
        padding = DialogPadding.None,
        accentColor = infoAccentColor,
        footer = null
    ) {
        AnimatedContent(
            targetState = isLoading || installedApp == null,
            // Content brings its own entrance through the header and the staggered items, so a
            // fade here would only stack on theirs and hold it back
            transitionSpec = {
                val enter = if (targetState) Animations.fadeIn else EnterTransition.None
                enter togetherWith Animations.fadeOut
            },
            modifier = Modifier.fillMaxSize(),
            label = "installedAppInfo"
        ) { loading ->
            // installedApp is re-checked here so the body below keeps its non-null smart cast
            if (loading || installedApp == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingLogoIndicator(delayed = true)
                }

                return@AnimatedContent
            }

            // Starts together with the dialog's own enter, and only once content is here to
            // animate, so a slow load does not skip the cascade
            LaunchedEffect(Unit) { entered.value = true }

            val windowSize = rememberWindowSize()
            val landscape = isLandscape()

            // Both layouts show the same pieces and only place them differently
            val relativeTime = remember(installedApp.patchedAt) { installedApp.patchedAt?.let { getRelativeTimeString(it) } }

            // What the install is, then when it was made. The clone chip sits between them: it
            // qualifies the install the same way its type does, rather than dating it
            val installBadges = buildList {
                add(installedApp.installType.badge.let { (icon, label) -> icon to stringResource(label) })
                if (installedApp.isClone) {
                    add(Icons.Outlined.ContentCopy to stringResource(R.string.clone))
                }
                relativeTime?.let { add(Icons.Outlined.Schedule to it) }
            }
            val badges = @Composable {
                installBadges.forEach { (icon, label) ->
                    AppAccentBadge(text = label, accentColor = infoAccentColor, icon = icon)
                }
            }
            val compactHeader = landscape && windowSize.widthSizeClass == WindowWidthSizeClass.Expanded

            // Writes the module again from the saved builds and mounts it, reinstalling the
            // matching original first when another version replaced it
            val reinstallSavedMount: () -> Unit = {
                viewModel.savedApkFile()?.let { savedFile ->
                    installViewModel.installSavedMount(
                        outputFile = savedFile,
                        packageName = installedApp.currentPackageName,
                        onPersistApp = { _, _ ->
                            viewModel.updateInstallType(
                                packageName = installedApp.currentPackageName,
                                newInstallType = InstallType.MOUNT
                            )
                            true
                        }
                    )
                }
            }
            // Installs the saved patched build. A mount build is always mounted, while any other
            // one warns first when the primary installer is mount
            val installSavedCopy: () -> Unit = {
                viewModel.savedApkFile()?.let { savedFile ->
                    val installAction = {
                        if (installedApp.reinstallsByMount(viewModel.primaryInstallerIsMount)) {
                            reinstallSavedMount()
                        } else {
                            // The install result handler records the install type once it lands
                            installViewModel.install(
                                outputFile = savedFile,
                                originalPackageName = installedApp.originalPackageName,
                                onPersistApp = { _, _ -> true }
                            )
                        }
                    }
                    if (viewModel.primaryInstallerIsMount && installedApp.installType != InstallType.MOUNT) {
                        pendingMountWarningAction.value = installAction
                        showMountWarningDialog.value = true
                    } else {
                        installAction()
                    }
                }
            }
            // Offered only once another version, usually a store update, replaced the mount. An
            // unmounted app still at its patched version is mounted again instead
            val installedVersion = appInfo?.versionName
            val mountRestore = if (
                viewModel.isInstallStateNotPatched &&
                installedApp.installType == InstallType.MOUNT &&
                viewModel.hasSavedCopy && viewModel.hasOriginalApk &&
                installedVersion != null && installedVersion != installedApp.version
            ) {
                MountRestore(
                    versions = installedVersion.withVersionPrefix() to installedApp.version.withVersionPrefix(),
                    label = mountStageLabel,
                    isRunning = isInstalling,
                    onRestore = reinstallSavedMount,
                    onOpenPlayStore = if (viewModel.isReplacedByPlayStore) {
                        { context.openPlayStorePage(installedApp.currentPackageName) }
                    } else null
                )
            } else null

            // The app's header pinned over its information. The header inset sets its band in from
            // the panel's edges, which it otherwise reaches out to and up under the status bar
            val infoPanel = @Composable { modifier: Modifier, headerInset: Dp, content: LazyListScope.() -> Unit ->
                Column(modifier = modifier) {
                    CompositionLocalProvider(LocalDialogHorizontalInset provides Defaults.ContentPadding) {
                        ListDialogHeader(
                            icon = { iconModifier ->
                                // Resolved by name when the record has no metadata to show. A record
                                // whose artifacts are gone carries no icon either, and the glass
                                // placeholder tinted to the app's accent is what the home card shows
                                // for it
                                AppIcon(
                                    packageInfo = appInfo,
                                    packageName = packageName,
                                    contentDescription = null,
                                    placeholderGradientColors = listOf(infoAccentColor),
                                    modifier = iconModifier.clip(RoundedCornerShape(DialogHeaderDefaults.IconCornerRadius))
                                )
                            },
                            title = appLabel,
                            subtitle = (appInfo?.versionName ?: installedApp.version).withVersionPrefix(),
                            accentColor = infoAccentColor,
                            // Wraps rather than clips: a clone carries a chip more than other installs do
                            badges = if (compactHeader) null else { { badges() } },
                            // Compact mode: chips column on the right
                            actions = if (compactHeader) {
                                {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        badges()
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = Defaults.ContentPadding + headerInset)
                        )
                    }

                    DialogLazyList(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = Defaults.ContentPaddingMedium),
                        content = content
                    )
                }
            }
            val actions = @Composable { modifier: Modifier ->
                ActionsSection(
                    // Left out where a banner above already carries it
                    primary = ActionItem(
                        text = stringResource(R.string.patch),
                        icon = Icons.Outlined.AutoFixHigh,
                        onClick = ::handlePatchClick,
                        enabled = availablePatches > 0
                    ).takeIf { !showsRebuildBanner && !viewModel.isAppDeleted },
                    tiles = installedAppTiles(
                        viewModel = viewModel,
                        installViewModel = installViewModel,
                        installedApp = installedApp,
                        restoreOfferedAbove = mountRestore != null,
                        onInstallSavedCopy = installSavedCopy,
                        onExport = { exportSavedLauncher.launch(exportFileName) },
                        onUninstall = { showUninstallConfirm.value = true },
                        onDelete = { showDeleteDialog.value = true }
                    ),
                    singleColumn = landscape,
                    modifier = modifier
                )
            }
            val noSavedApkNotice = @Composable { modifier: Modifier ->
                Notice(
                    text = stringResource(R.string.home_app_info_no_saved_apk),
                    tone = SemanticTone.Warning,
                    icon = Icons.Outlined.Info,
                    modifier = modifier
                )
            }
            val closeButton = @Composable { modifier: Modifier ->
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = modifier.fillMaxWidth()
                )
            }
            // Stagger index counter: hero header is index 0, the banners always 1, so later indices
            // hold whatever banners show. They share the info's row, since an empty row of their
            // own counts as scrolled past and fades the list's top while it rests there
            val infoItems: LazyListScope.() -> Unit = {
                item(key = "info") {
                    Column {
                        InstalledAppBanners(
                            viewModel = viewModel,
                            showsRebuildBanner = showsRebuildBanner,
                            versionBehind = versionBehind,
                            versionAhead = versionAhead,
                            entered = entered.value,
                            staggerIndex = 1,
                            accentColor = infoAccentColor,
                            mountRestore = mountRestore,
                            onPatch = { onTriggerPatchFlow(installedApp.originalPackageName, installedApp.trackingKey) },
                            onShowUpdateChangelog = onShowUpdateChangelog,
                            onIgnoreVersion = onIgnoreVersion,
                            onOpenAppLinks = { showAppLinksDialog.value = true },
                            modifier = Modifier.padding(horizontal = Defaults.ContentPadding)
                        )
                        StaggeredItem(entered = entered.value, index = 2) {
                            InfoSection(
                                installedApp = installedApp,
                                supportedVersion = supportedVersion?.version,
                                onStopIgnoringVersion = onStopIgnoringVersion,
                                appliedPatches = appliedPatches,
                                bundlesUsedSummary = bundlesUsedSummary,
                                appLinksStatus = viewModel.appLinksStatus,
                                onShowPatches = { showAppliedPatchesDialog.value = true },
                                onOpenAppLinks = { showAppLinksDialog.value = true },
                                accentColor = infoAccentColor,
                                modifier = Modifier
                                    .padding(horizontal = Defaults.ContentPadding)
                                    .padding(top = Defaults.ItemSpacing)
                            )
                        }
                    }
                }
            }

            if (landscape) {
                // Landscape layout: left sidebar has actions, right panel has header + info
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left sidebar: centered buttons (scrollable when height is small)
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = Defaults.ContentPadding)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val availableHeight = maxHeight
                            val sidebarScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = availableHeight)
                                    .verticalScrollFade(sidebarScrollState)
                                    .verticalScroll(sidebarScrollState),
                                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing, Alignment.CenterVertically)
                            ) {
                                StaggeredItem(entered = entered.value, index = 1) {
                                    actions(Modifier.fillMaxWidth())
                                }
                                if (!viewModel.hasOriginalApk) {
                                    StaggeredItem(entered = entered.value, index = 2) {
                                        noSavedApkNotice(Modifier)
                                    }
                                }
                                StaggeredItem(entered = entered.value, index = 1) {
                                    closeButton(Modifier)
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(vertical = Defaults.ContentPadding))

                    infoPanel(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                        Defaults.ContentPadding,
                        infoItems
                    )
                }
            } else {
                // Single-column layout for phones
                Column(modifier = Modifier.fillMaxSize()) {
                    infoPanel(Modifier.weight(1f).fillMaxWidth(), 0.dp) {
                        infoItems()

                        item(key = "actions") {
                            StaggeredItem(entered = entered.value, index = 3) {
                                actions(
                                    Modifier
                                        .padding(horizontal = Defaults.ContentPadding)
                                        .padding(top = Defaults.ItemSpacing)
                                )
                            }
                        }

                        if (!viewModel.hasOriginalApk) {
                            item(key = "no_saved_apk") {
                                StaggeredItem(entered = entered.value, index = 4) {
                                    noSavedApkNotice(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Defaults.ContentPadding)
                                            .padding(top = Defaults.ItemSpacing)
                                    )
                                }
                            }
                        }
                    }
                    StaggeredItem(entered = entered.value, index = 3) {
                        closeButton(
                            Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = Defaults.ContentPadding)
                                .padding(vertical = Defaults.ItemSpacing)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The banners above an installed app's information. Each carries the gap above it, so a hidden
 * one leaves no space behind in a list that spaces nothing of its own.
 */
@Composable
private fun InstalledAppBanners(
    viewModel: InstalledAppInfoViewModel,
    showsRebuildBanner: Boolean,
    versionBehind: AppVersionStatus?,
    versionAhead: AppVersionStatus?,
    entered: Boolean,
    staggerIndex: Int,
    accentColor: Color,
    mountRestore: MountRestore?,
    onPatch: () -> Unit,
    onShowUpdateChangelog: (() -> Unit)?,
    onIgnoreVersion: (() -> Unit)?,
    onOpenAppLinks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ignoredAppLinksPackages by viewModel.ignoredAppLinksPackages.collectAsStateWithLifecycle(emptySet())
    val showsAppLinksBanner = viewModel.appLinksStatus?.opensInBrowser == true &&
            viewModel.installedApp?.currentPackageName !in ignoredAppLinksPackages
    val patchAction = ActionItem(
        text = stringResource(R.string.patch),
        icon = Icons.Outlined.AutoFixHigh,
        onClick = onPatch
    )

    Column(modifier = modifier) {
        BannerSlot(
            visible = viewModel.isAppDeleted && !viewModel.isInstallStateNotPatched,
            entered = entered,
            staggerIndex = staggerIndex
        ) {
            WarningBanner(
                icon = Icons.Outlined.Warning,
                title = stringResource(R.string.home_app_info_app_deleted_warning),
                description = stringResource(R.string.home_app_info_app_deleted_description),
                action = patchAction,
                accentColor = accentColor,
                isError = true
            )
        }
        BannerSlot(
            visible = viewModel.isInstallStateNotPatched,
            entered = entered,
            staggerIndex = staggerIndex
        ) {
            WarningBanner(
                icon = Icons.Outlined.AutoFixHigh,
                title = stringResource(R.string.home_unpatched_version_installed),
                // Naming the store is what tells the user how to keep this from happening again
                description = stringResource(
                    when {
                        mountRestore == null -> R.string.home_app_info_not_patched_description
                        mountRestore.onOpenPlayStore != null -> R.string.home_app_info_mount_replaced_by_play_store_description
                        else -> R.string.home_app_info_mount_replaced_description
                    }
                ),
                action = mountRestore?.let {
                    ActionItem(
                        text = it.label ?: stringResource(R.string.restore),
                        icon = Icons.Outlined.Restore,
                        onClick = it.onRestore,
                        isLoading = it.isRunning
                    )
                } ?: patchAction,
                accentColor = accentColor,
                // Back to the patched version for a restore, else the version patching again reaches
                versions = mountRestore?.versions ?: versionAhead?.versions,
                versionsReadOutRes = if (mountRestore != null) R.string.home_app_info_version_restore
                else R.string.home_app_info_version_move,
                secondaryActions = if (mountRestore != null) {
                    listOfNotNull(
                        patchAction,
                        mountRestore.onOpenPlayStore?.let {
                            ActionItem(
                                text = stringResource(R.string.google_play),
                                icon = Icons.Outlined.Shop,
                                onClick = it
                            )
                        }
                    )
                } else {
                    emptyList()
                }
            )
        }
        BannerSlot(
            visible = viewModel.isInstallStateUnknown,
            entered = entered,
            staggerIndex = staggerIndex
        ) {
            Notice(
                text = stringResource(R.string.home_app_info_install_unverified),
                tone = SemanticTone.Warning,
                icon = Icons.AutoMirrored.Outlined.HelpOutline
            )
        }
        // Newer patches and a newer supported app version are both answered by rebuilding the
        // app, so they share a banner rather than stacking two with the same button
        BannerSlot(
            visible = showsRebuildBanner,
            entered = entered,
            staggerIndex = staggerIndex
        ) {
            WarningBanner(
                icon = Icons.Outlined.Update,
                title = stringResource(
                    if (versionBehind != null) R.string.home_app_info_app_update_available
                    else R.string.home_app_info_patch_update_available
                ),
                description = stringResource(
                    if (versionBehind != null) R.string.home_app_info_app_update_available_description
                    else R.string.home_app_info_patch_update_available_description
                ),
                versions = versionBehind?.versions,
                action = patchAction,
                accentColor = accentColor,
                isError = false,
                secondaryActions = listOfNotNull(
                    onShowUpdateChangelog?.let {
                        ActionItem(
                            text = stringResource(R.string.whats_new),
                            icon = Icons.Outlined.History,
                            onClick = it
                        )
                    },
                    onIgnoreVersion?.let {
                        ActionItem(
                            text = stringResource(R.string.ignore),
                            icon = Icons.Outlined.VisibilityOff,
                            onClick = it
                        )
                    }
                )
            )
        }
        // Last, since links opening in the browser is an inconvenience the app works fine with
        BannerSlot(
            visible = showsAppLinksBanner,
            entered = entered,
            staggerIndex = staggerIndex
        ) {
            WarningBanner(
                icon = Icons.Outlined.LinkOff,
                title = stringResource(R.string.app_links_unverified_banner_title),
                description = stringResource(R.string.app_links_unverified_banner_description),
                action = ActionItem(
                    text = stringResource(R.string.app_links_fix),
                    icon = Icons.Outlined.Link,
                    onClick = onOpenAppLinks
                ),
                accentColor = accentColor,
                secondaryActions = listOf(
                    ActionItem(
                        text = stringResource(R.string.ignore),
                        icon = Icons.Outlined.VisibilityOff,
                        onClick = viewModel::ignoreAppLinks
                    )
                )
            )
        }
    }
}

/**
 * Putting a replaced mount back from the saved builds. [versions] runs from the version installed
 * to the patched one, [label] names the step while it runs, and [onOpenPlayStore] is set when
 * Google Play did the replacing.
 */
private class MountRestore(
    val versions: Pair<String, String>,
    val label: String?,
    val isRunning: Boolean,
    val onRestore: () -> Unit,
    val onOpenPlayStore: (() -> Unit)?
)

/** One banner's place in the group, animated in and out of it. */
@Composable
private fun BannerSlot(
    visible: Boolean,
    entered: Boolean,
    staggerIndex: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        Column {
            Spacer(Modifier.height(Defaults.ItemSpacing))
            StaggeredItem(entered = entered, index = staggerIndex, content = content)
        }
    }
}

/** The two versions a status stands between, in the order a banner prints them. */
private val AppVersionStatus.versions: Pair<String, String>
    get() = installedVersion.withVersionPrefix() to supportedVersion.withVersionPrefix()

/**
 * The move a banner is offering, from the version installed to the one it would end up on. The
 * banner's own wording says which is which, so the line is unlabeled and read out labeled instead.
 */
@Composable
private fun VersionTransition(
    versions: Pair<String, String>,
    contentColor: Color,
    @StringRes readOutRes: Int,
    modifier: Modifier = Modifier
) {
    val (from, to) = versions
    // Points the way the language runs, so the move reads forwards in Arabic and Hebrew too
    val arrow = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→"
    val readOut = stringResource(readOutRes, from, to)

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = contentColor.copy(alpha = 0.75f))) { append(from) }
            withStyle(SpanStyle(color = contentColor.copy(alpha = 0.5f))) { append("  $arrow  ") }
            withStyle(SpanStyle(color = contentColor, fontWeight = FontWeight.Medium)) { append(to) }
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { contentDescription = readOut }
    )
}

/**
 * Unified banner component for warnings and updates.
 *
 * Neutral like the app's other cards, with its color on the edge: red for an [isError] banner, whose
 * heading takes the same red, the app's own otherwise. Its button is the app's either way, being the
 * way out rather than a destructive action.
 *
 * [versions] is the move being offered, from the version installed to the one it would end up on,
 * printed under the description as one line.
 */
@Composable
private fun WarningBanner(
    icon: ImageVector,
    title: String,
    description: String,
    action: ActionItem,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    versions: Pair<String, String>? = null,
    @StringRes versionsReadOutRes: Int = R.string.home_app_info_version_move,
    secondaryActions: List<ActionItem> = emptyList()
) {
    val fill = cardFill()
    val contentColor = MaterialTheme.colorScheme.onSurface
    // The warning is carried by the heading rather than the fill, which would only muddy the red
    val headingColor = if (isError) destructiveColor() else contentColor
    val borderColor = if (isError) destructiveEdgeColor() else appAccentBorder(accentColor)

    ProvideCardAccent(accentColor, fill) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Defaults.ItemSpacing))
                .cardBorder(CardBorder.of(borderColor), RoundedCornerShape(Defaults.ItemSpacing))
                .background(fill)
                .padding(Defaults.ItemSpacing),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with icon
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = headingColor,
                    modifier = Modifier.size(Defaults.ContentPadding)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = headingColor,
                    textAlign = TextAlign.Center
                )
            }

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // The versions and the extra actions depend on what the sources say, which can arrive
            // after the banner is already up
            val shownVersions = rememberLatest(versions)
            AnimatedVisibility(
                visible = versions != null,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                shownVersions?.let {
                    VersionTransition(versions = it, contentColor = contentColor, readOutRes = versionsReadOutRes)
                }
            }

            // Action button
            PrimaryActionButton(
                action = action,
                modifier = Modifier.fillMaxWidth()
            )

            // Side by side, because the banner's own button is the one meant to stand out and a
            // column of full-width buttons under it reads as three offers of equal weight
            val shownActions = rememberLatest(secondaryActions.takeIf { it.isNotEmpty() })
            AnimatedVisibility(
                visible = secondaryActions.isNotEmpty(),
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                ) {
                    shownActions?.forEach { action ->
                        // One that joins a row already on screen fades in rather than popping up
                        key(action.text) {
                            val appeared = remember { MutableTransitionState(false) }
                            appeared.targetState = true
                            AnimatedVisibility(
                                visibleState = appeared,
                                enter = Animations.fadeIn,
                                modifier = Modifier.weight(1f)
                            ) {
                                TileActionButton(
                                    action = action,
                                    horizontal = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The icon and label an install type is badged with in the app's header. */
private val InstallType.badge: Pair<ImageVector, Int>
    get() = when (this) {
        InstallType.MOUNT   -> Icons.Outlined.Link to R.string.mount
        InstallType.SHIZUKU -> Icons.Outlined.Terminal to R.string.home_app_info_install_type_shizuku
        InstallType.SHIZUKU_PLAY_STORE -> Icons.Outlined.Terminal to R.string.home_app_info_install_type_shizuku_play_store
        InstallType.PLAY_STORE -> Icons.Outlined.Shop to R.string.home_app_info_install_type_play_store
        InstallType.ROOT_PLAY_STORE -> Icons.Outlined.Security to R.string.home_app_info_install_type_root_play_store
        InstallType.CUSTOM  -> Icons.Outlined.Build to R.string.home_app_info_install_type_custom_installer
        InstallType.SAVED   -> Icons.Outlined.Save to R.string.saved
        InstallType.DEFAULT -> Icons.Outlined.InstallMobile to R.string.home_app_info_install_type_system_installer
    }

/**
 * Wraps content with a staggered entrance animation.
 * Uses a single progress float (0 to 1); alpha, offsetY and scale are
 * derived via [lerp] - one Recomposition subscriber instead of three.
 * Each item appears [index] * [Animations.STAGGER_STEP] ms after [entered] becomes true.
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
            durationMillis = Defaults.ANIMATION_DURATION,
            delayMillis = index * Animations.STAGGER_STEP,
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
    supportedVersion: String?,
    onStopIgnoringVersion: (() -> Unit)?,
    appliedPatches: Map<Int, Set<String>>?,
    bundlesUsedSummary: String,
    appLinksStatus: AppLinksStatus?,
    onShowPatches: () -> Unit,
    onOpenAppLinks: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val totalPatches = appliedPatches?.values?.sumOf { it.size } ?: 0
    val context = LocalContext.current

    // Read off the main thread: listing the native libraries walks the whole APK, which for the
    // largest apps holds the dialog's first frame back noticeably
    val installedApk by produceState<File?>(null, installedApp.currentPackageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getPackageInfo(installedApp.currentPackageName, 0)
                    .applicationInfo?.sourceDir?.let(::File)
            } catch (_: Exception) { null }
        }
    }

    // APK size from sourceDir
    val apkSize = remember(installedApk) { installedApk?.let { context.formatBytes(it.length()) } }

    val apkAbis by produceState(emptyList(), installedApk) {
        val apk = installedApk ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                NativeLibs.extractAbisFromApk(apk)
            } catch (_: Exception) { emptyList() }
        }
    }

    // Edged like the app's cards elsewhere, so the panel reads as part of the app's dialog
    SurfaceCard(
        cornerRadius = Defaults.CardCornerRadius,
        showBorder = true,
        borderColor = appAccentBorder(accentColor),
        color = cardFill(),
        modifier = modifier
    ) {
        Column {
            InfoRow(
                icon = Icons.Outlined.Inventory2,
                label = stringResource(R.string.package_name),
                value = installedApp.currentPackageName
            )

            if (installedApp.originalPackageName != installedApp.currentPackageName) {
                SettingsDivider()
                InfoRow(
                    icon = Icons.Outlined.Category,
                    label = stringResource(R.string.home_app_info_original_package_name),
                    value = installedApp.originalPackageName
                )
            }

            // Kept in place even while a banner above says the same thing, so the version the
            // sources cover can be looked up rather than only met as a warning. A version that
            // was turned down is where the offer is taken back up again
            InfoSlot(supportedVersion) { version ->
                val supportedVersionLabel = stringResource(R.string.home_app_info_newest_supported_version)
                if (onStopIgnoringVersion != null) {
                    InfoRowWithAction(
                        icon = Icons.Outlined.VisibilityOff,
                        label = supportedVersionLabel,
                        value = version.withVersionPrefix(),
                        onAction = onStopIgnoringVersion,
                        actionIcon = Icons.Outlined.Visibility,
                        actionContentDescription = stringResource(R.string.stop_ignoring)
                    )
                } else {
                    InfoRow(
                        icon = Icons.Outlined.Update,
                        label = supportedVersionLabel,
                        value = version.withVersionPrefix()
                    )
                }
            }

            InfoSlot(apkSize) { size ->
                InfoRow(
                    icon = Icons.Outlined.SdCard,
                    label = stringResource(R.string.home_app_info_apk_size),
                    value = size
                )
            }

            InfoSlot(apkAbis.takeIf { it.isNotEmpty() }) { abis ->
                InfoRow(
                    icon = Icons.Outlined.Memory,
                    label = stringResource(R.string.home_app_info_cpu_arch),
                    value = abis.joinToString(" • ")
                )
            }

            InfoSlot(totalPatches.takeIf { it > 0 }) { count ->
                InfoRowWithAction(
                    icon = Icons.Outlined.DoneAll,
                    label = stringResource(R.string.home_app_info_applied_patches),
                    value = pluralStringResource(R.plurals.patch_count, count, count.toString()),
                    onAction = onShowPatches
                )
            }

            InfoSlot(bundlesUsedSummary.takeIf { it.isNotBlank() }) { summary ->
                InfoRow(
                    icon = Icons.Outlined.Source,
                    label = stringResource(R.string.home_app_info_patch_source_used),
                    value = summary
                )
            }

            InfoSlot(appLinksStatus?.takeIf { it.hasSupportedLinks }) { status ->
                val linksValue = if (status.isFullyConfigured) {
                    pluralStringResource(
                        R.plurals.app_links_count_enabled,
                        status.domains.size,
                        status.domains.size
                    )
                } else {
                    pluralStringResource(
                        R.plurals.app_links_count_unverified,
                        status.unhandledDomains.size,
                        status.unhandledDomains.size
                    )
                }
                InfoRowWithAction(
                    icon = if (status.isFullyConfigured) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                    label = stringResource(R.string.app_links_title),
                    value = linksValue,
                    onAction = onOpenAppLinks,
                    actionIcon = Icons.Outlined.Settings,
                    actionContentDescription = stringResource(R.string.configure)
                )
            }
        }
    }
}

/**
 * A row of the info card shown while [value] is there. Most of what the card lists is read after
 * the dialog opens, so rows slide in as it arrives instead of pushing the card apart.
 */
@Composable
private fun <T : Any> InfoSlot(value: T?, content: @Composable (T) -> Unit) {
    val shown = rememberLatest(value)
    AnimatedVisibility(
        visible = value != null,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        shown?.let {
            Column {
                SettingsDivider()
                content(it)
            }
        }
    }
}

/** [value], or the last one it held once it is gone, so content animating out has something to show. */
@Composable
private fun <T : Any> rememberLatest(value: T?): T? {
    val latest = remember { LatestValue<T>() }
    if (value != null) latest.value = value
    return value ?: latest.value
}

private class LatestValue<T : Any> {
    var value: T? = null
}

@Composable
private fun InfoRowWithAction(
    icon: ImageVector,
    label: String,
    value: String,
    onAction: () -> Unit,
    actionIcon: ImageVector = Icons.AutoMirrored.Outlined.List,
    actionContentDescription: String = stringResource(R.string.view),
) {
    InfoRow(
        icon = icon,
        label = label,
        value = value,
        trailing = {
            // A plain pill takes the app's color the dialog hands it, as the pills on its cards do
            ActionPillButton(
                onClick = onAction,
                icon = actionIcon,
                contentDescription = actionContentDescription
            )
        }
    )
}

/**
 * The tiles under an installed app's information, in the order they are laid out: what can be
 * done with it first, the destructive actions last.
 */
@Composable
private fun installedAppTiles(
    viewModel: InstalledAppInfoViewModel,
    installViewModel: InstallViewModel,
    installedApp: InstalledApp,
    restoreOfferedAbove: Boolean,
    onInstallSavedCopy: () -> Unit,
    onExport: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit
): List<ActionItem> {
    val isInstalling = installViewModel.installState is InstallViewModel.InstallState.Installing
    val isMountBusy = installViewModel.mountOperation != null
    val stageLabel = installViewModel.installState.stageLabel()
    val packageName = installedApp.currentPackageName

    return buildList {
        if (installedApp.installType != InstallType.SAVED && viewModel.appInfo != null && viewModel.isInstalledOnDevice) {
            add(ActionItem(stringResource(R.string.open), Icons.AutoMirrored.Outlined.Launch, viewModel::launch))
        }

        if (viewModel.hasSavedCopy) {
            add(ActionItem(stringResource(R.string.export), Icons.Outlined.Save, onExport))
            add(
                ActionItem(
                    text = stageLabel ?: stringResource(
                        if (viewModel.isInstalledOnDevice) R.string.reinstall else R.string.install
                    ),
                    icon = Icons.Outlined.InstallMobile,
                    onClick = onInstallSavedCopy,
                    isLoading = isInstalling
                )
            )
        }

        // A plain mount or remount, while reinstalling the saved build is the tile above
        if (installedApp.installType == InstallType.MOUNT) {
            if (viewModel.isMounted) {
                add(
                    ActionItem(
                        text = stringResource(R.string.remount),
                        icon = Icons.Outlined.Refresh,
                        onClick = { installViewModel.remount(packageName, installedApp.version) },
                        isLoading = isMountBusy || isInstalling
                    )
                )
                add(
                    ActionItem(
                        text = stringResource(R.string.unmount),
                        icon = Icons.Outlined.LinkOff,
                        onClick = { installViewModel.unmount(packageName) },
                        isLoading = isMountBusy
                    )
                )
            } else if (!restoreOfferedAbove) {
                add(
                    ActionItem(
                        text = stringResource(R.string.mount),
                        icon = Icons.Outlined.Link,
                        onClick = { installViewModel.mount(packageName, installedApp.version) },
                        isLoading = isMountBusy || isInstalling
                    )
                )
            }
        }

        if (viewModel.isInstalledOnDevice) {
            add(
                ActionItem(
                    text = stringResource(R.string.uninstall),
                    icon = Icons.Outlined.DeleteForever,
                    onClick = onUninstall,
                    isDestructive = true
                )
            )
        }
        if (viewModel.canRemoveRecord) {
            add(
                ActionItem(
                    text = stringResource(R.string.delete),
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = onDelete,
                    isDestructive = true
                )
            )
        }
    }
}

/** The step a running mount install is on, in the words its buttons show. */
@Composable
private fun InstallViewModel.InstallState.stageLabel(): String? =
    (this as? InstallViewModel.InstallState.Installing)?.stage?.let { stringResource(it.labelRes) }

/** The [primary] button over the [tiles], which sit in pairs or, in a [singleColumn], one a row. */
@Composable
private fun ActionsSection(
    primary: ActionItem?,
    tiles: List<ActionItem>,
    singleColumn: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.animateContentSize(animationSpec = tween(Defaults.ANIMATION_DURATION)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        primary?.let { PrimaryActionButton(action = it, modifier = Modifier.fillMaxWidth()) }

        if (singleColumn) {
            tiles.forEach { action ->
                TileActionButton(action = action, horizontal = true, modifier = Modifier.fillMaxWidth())
            }
        } else {
            tiles.chunked(2).forEach { rowActions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowActions.forEach { action ->
                        TileActionButton(
                            action = action,
                            modifier = if (rowActions.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f)
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

/**
 * Shared Surface shell for all action buttons. Callers pick the colors of a button in reach, and
 * any button out of reach, primary included, takes the same muted look.
 */
@Composable
private fun ActionButton(
    action: ActionItem,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val isEnabled = action.enabled && !action.isLoading
    // A button still loading is busy rather than unavailable, so only a disabled one is muted
    val colors = MaterialTheme.colorScheme
    val container = if (action.enabled) containerColor else colors.surfaceVariant.copy(alpha = 0.4f)
    val content = if (action.enabled) contentColor else colors.onSurface.copy(alpha = 0.35f)
    val border = if (action.enabled) borderColor else colors.outlineVariant.copy(alpha = 0.2f)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = action.onClick,
        enabled = isEnabled,
        modifier = modifier
            .height(Defaults.TallTouchTarget)
            .pressScale(
                interactionSource = interactionSource,
                enabled = isEnabled,
                label = "info_action_press_scale"
            ),
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        color = container,
        contentColor = content,
        border = CardBorder.of(border),
        interactionSource = interactionSource
    ) {
        if (vertical) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingOrIcon(action.isLoading, action, content)
                Spacer(Modifier.height(3.dp))
                // Steps through a running mount install, which a fade carries from one to the next
                CrossfadeText(
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
                    .padding(horizontal = Defaults.ContentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoadingOrIcon(action.isLoading, action, content)
                Spacer(Modifier.width(Defaults.ContentPaddingSmall))
                CrossfadeText(
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

/** Full-width primary button in the app's color, see [LocalAccent]. */
@Composable
private fun PrimaryActionButton(
    action: ActionItem,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    // A step over the other tiles, which take the app's color at the band's fill, so it leads them
    val containerColor = accent?.copy(alpha = AccentAlpha.LEAD)
        ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    ActionButton(
        action = action,
        containerColor = containerColor,
        contentColor = appAccentContent(containerColor),
        borderColor = accent?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
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
    // One veil for every tile, so only the primary action wears a color and destructive ones their red
    ActionButton(
        action = action,
        containerColor = neutralVeil(),
        contentColor = when {
            action.isDestructive -> destructiveColor()
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        },
        borderColor = when {
            action.isDestructive -> destructiveEdgeColor()
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.warning),
        description = stringResource(R.string.installer_mount_warning_install),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    )
}

/**
 * What went into an installed app: its patches and their options, source by source, under the
 * same header the app's other dialogs carry.
 */
@Composable
private fun AppliedPatchesDialog(
    appLabel: String,
    appInfo: PackageInfo?,
    accentColor: Color,
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

    val patchCount = bundles.sumOf { it.patchInfos.size + it.fallbackNames.size }
    // Options are stored under the selection key, which is suffixed on duplicate names
    val entriesByBundle = remember(bundles, bundleOptionsMap) {
        bundles.map { bundle ->
            val displayNames = bundle.patchInfos.associate { it.name to it.displayName }
            val fallbackNames = bundle.fallbackNames.toSet()
            bundle to patchEntries(
                keys = bundle.patchInfos.map { it.name } + bundle.fallbackNames,
                options = bundleOptionsMap[bundle.uid].orEmpty(),
                displayName = displayNames::get,
                dimmed = { it in fallbackNames }
            )
        }
    }
    val copyToClipboard = rememberCopyToClipboard()
    val sourcesByUid = rememberSourcesByUid()

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier ->
            AppIcon(packageInfo = appInfo, packageName = packageName, contentDescription = null, modifier = modifier)
        },
        title = appLabel,
        subtitle = listOf(
            pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString()),
            bundles.singleOrNull()?.title
                ?: pluralStringResource(R.plurals.source_count, bundles.size, bundles.size.toString())
        ).joinToString(" · "),
        accentColor = accentColor,
        actions = listOf(
            DialogAction(
                text = stringResource(R.string.copy),
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    copyToClipboard(
                        patchListText(
                            title = appLabel,
                            lists = entriesByBundle.map { (bundle, entries) ->
                                listOfNotNull(bundle.title, bundle.version).joinToString(" ") to entries
                            }
                        )
                    )
                }
            )
        )
    ) {
        val multipleSources = bundles.size > 1
        entriesByBundle.forEach { (bundle, entries) ->
            // A lone source's count is already in the dialog header
            val source = sourcesByUid[bundle.uid]
            LabeledSection(
                title = if (multipleSources) bundle.title
                else stringResource(R.string.home_app_info_applied_patches),
                count = entries.size.takeIf { multipleSources },
                icon = Icons.Outlined.DoneAll,
                leading = source?.takeIf { multipleSources }?.let {
                    { BundleIcon(bundle = it, modifier = Modifier.size(24.dp)) }
                }
            ) {
                PatchEntryList(entries)
            }
        }
    }
}
