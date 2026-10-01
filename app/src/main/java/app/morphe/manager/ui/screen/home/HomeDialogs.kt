/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.BundleSourceType
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.sourceType
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.bundles.recommended
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.apkdownload.ApkCaptureDownloader
import app.morphe.manager.ui.screen.apkdownload.ApkDownloadProgressOverlay
import app.morphe.manager.ui.screen.apkdownload.rememberApkDownloadCaptureAction
import app.morphe.manager.ui.screen.patcher.UnusableOptionPathsDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

/**
 * Container for all home screen dialogs.
 */
@Composable
fun HomeDialogs(
    homeViewModel: HomeViewModel,
    storagePickerLauncher: () -> Unit,
    openBundlePicker: () -> Unit,
    patchesItem: MutableState<HomeAppItem?>,
    globalOnboardingState: GlobalOnboardingState? = null
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Kept outside the dialog so the picker state survives the download dialog's exit animation
    val openApkDownloadHelper = rememberApkDownloadHelperAction(
        host = homeViewModel,
        enabled = homeViewModel.showDownloadInstructionsDialog
    )

    // Opens the resolved download page in the capture WebView. The file it takes is then fed
    // back through the same intake a hand-picked APK goes through, so every check that guards a
    // manual selection still applies to one this app fetched itself.
    val captureApkInApp = rememberApkDownloadCaptureAction(
        downloadUrl = homeViewModel.resolvedDownloadUrl,
        appName = homeViewModel.pendingAppName.orEmpty(),
    ) { capture ->
        val appName = homeViewModel.pendingAppName.orEmpty()
        ApkCaptureDownloader.start(
            context = context,
            scope = scope,
            capture = capture,
            appName = appName,
        ) { uri ->
            if (uri == null) {
                // Names the reason while this is being tested. The exception's own message says
                // more than "it failed" ever could, and reaching an adb console is not something
                // to ask of whoever is holding the phone
                val reason = ApkCaptureDownloader.lastError
                context.toast(
                    if (reason.isNullOrBlank()) context.getString(R.string.apk_capture_download_failed)
                    else context.getString(R.string.apk_capture_download_failed_reason, reason)
                )
                return@start
            }
            // Through the ordinary intake, so every check that guards a hand-picked APK applies
            homeViewModel.showDownloadInstructionsDialog = false
            homeViewModel.handleApkSelection(uri)
        }
    }

    // Drawn over every screen in this tree, so a transfer started here stays visible after the
    // dialog that began it is dismissed
    ApkDownloadProgressOverlay(onCancel = { ApkCaptureDownloader.cancel() })

    // APK selection processing overlay - blocks interaction while APK is loaded/validated in background
    Overlay(visible = homeViewModel.processingApkSelection) {        PulsingLogoWithCaption(caption = stringResource(R.string.processing_apk))
    }

    // Dialog 1: APK availability
    AnimatedVisibility(
        visible = homeViewModel.showApkAvailabilityDialog &&
                homeViewModel.pendingPackageName != null &&
                homeViewModel.pendingAppName != null,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut(if (homeViewModel.showDownloadInstructionsDialog) 0 else Defaults.ANIMATION_DURATION)
    ) {
        val appName = homeViewModel.pendingAppName ?: return@AnimatedVisibility
        // Remembered so the color holds through the exit animation, as the download dialog's does
        val packageName = remember { homeViewModel.pendingPackageName }
        val recommendedVersion = homeViewModel.pendingRecommendedVersion
        val compatibleVersions = homeViewModel.pendingCompatibleVersions
        val selectedDownloadVersion = homeViewModel.pendingSelectedDownloadVersion
        val usingMountInstall = homeViewModel.usingMountInstall
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()
        val savedApkInfo = homeViewModel.pendingSavedApkInfo
        val installedApkInfo = homeViewModel.pendingInstalledApkInfo
        val installedAppVersion = homeViewModel.pendingInstalledAppVersion
        val stockAppInstalled = homeViewModel.pendingStockAppInstalled == true

        ApkAvailabilityDialog(
            appName = appName,
            packageName = packageName,
            recommendedVersion = recommendedVersion,
            compatibleVersions = compatibleVersions,
            selectedDownloadVersion = selectedDownloadVersion,
            onVersionSelect = { homeViewModel.pendingSelectedDownloadVersion = it },
            usingMountInstall = usingMountInstall,
            stockAppInstalled = stockAppInstalled,
            isExpertMode = isExpertMode,
            savedApkInfo = savedApkInfo,
            installedApkInfo = installedApkInfo,
            installedAppVersion = installedAppVersion,
            onDismiss = {
                homeViewModel.showApkAvailabilityDialog = false
                homeViewModel.cleanupPendingData()
            },
            onHaveApk = {
                homeViewModel.showApkAvailabilityDialog = false
                storagePickerLauncher()
            },
            onNeedApk = {
                homeViewModel.showApkAvailabilityDialog = false
                scope.launch {
                    delay(50.milliseconds)
                    homeViewModel.showDownloadInstructionsDialog = true
                    homeViewModel.resolveDownloadRedirect()
                }
            },
            onUseSaved = {
                homeViewModel.handleSavedApkSelection()
            },
            onUseInstalled = {
                homeViewModel.handleInstalledApkSelection()
            }
        )
    }

    // Dialog 2: Download instructions
    AnimatedVisibility(
        visible = homeViewModel.showDownloadInstructionsDialog &&
                homeViewModel.pendingPackageName != null &&
                homeViewModel.pendingAppName != null,
        enter = Animations.overlayEnter,
        exit = Animations.fadeOut(if (homeViewModel.showFilePickerPromptDialog) 0 else Defaults.ANIMATION_DURATION)
    ) {
        val usingMountInstall = homeViewModel.usingMountInstall
        // Remember packageName to prevent color flickering during exit animation
        val packageName = remember { homeViewModel.pendingPackageName }
        // Remembered for the same reason, since the pending data is cleared as the dialog leaves
        val appName = remember { homeViewModel.pendingAppName.orEmpty() }
        // Settled in dialog 1 and remembered for the same reason, so the steps stay put on the way out
        val requestedVersion = remember {
            (homeViewModel.pendingSelectedDownloadVersion ?: homeViewModel.pendingRecommendedVersion)?.version
        }

        // Resolve download button color: bundle declared → default
        val bundleMetadata by homeViewModel.bundleAppMetadataFlow.collectAsStateWithLifecycle()
        val downloadColor = remember(packageName, bundleMetadata) {
            bundleMetadata[packageName ?: ""]?.downloadColor
                ?: KnownApps.DEFAULT_DOWNLOAD_COLOR
        }
        // True when the patch bundle explicitly requires a split archive (APKM/APKS/XAPK).
        // In that case the APKMirror button label becomes "DOWNLOAD APK BUNDLE" to match the site.
        val isApkBundle = remember(packageName, bundleMetadata) {
            bundleMetadata[packageName ?: ""]?.apkFileType?.isApk == false
        }

        DownloadInstructionsDialog(
            appName = appName,
            packageName = packageName,
            downloadUrl = homeViewModel.resolvedDownloadUrl,
            requestedVersion = requestedVersion,
            usingMountInstall = usingMountInstall,
            stockAppInstalled = homeViewModel.pendingStockAppInstalled == true,
            downloadColor = downloadColor,
            isApkBundle = isApkBundle,
            onDismiss = {
                homeViewModel.showDownloadInstructionsDialog = false
                homeViewModel.cleanupPendingData()
            },
            onOpenApkDownloadHelper = openApkDownloadHelper,
            onCaptureApkInApp = captureApkInApp
        ) {
            homeViewModel.handleDownloadInstructionsContinue { url ->
                try {
                    uriHandler.openUri(url)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    // Dialog 3: File picker prompt
    AnimatedVisibility(
        visible = homeViewModel.showFilePickerPromptDialog && homeViewModel.pendingAppName != null,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        val appName = homeViewModel.pendingAppName ?: return@AnimatedVisibility
        val packageName = remember { homeViewModel.pendingPackageName }
        val isOtherApps = packageName == null

        FilePickerPromptDialog(
            appName = appName,
            packageName = packageName,
            isOtherApps = isOtherApps,
            isLoadingInstalledApps = homeViewModel.loadingInstalledApps,
            onDismiss = {
                homeViewModel.showFilePickerPromptDialog = false
                homeViewModel.cleanupPendingData()
            },
            onOpenFilePicker = {
                homeViewModel.showFilePickerPromptDialog = false
                storagePickerLauncher()
            },
            onUseInstalledApp = if (isOtherApps) {
                { homeViewModel.loadInstalledAppsForPicker() }
            } else null
        )
    }

    // Dialog 3.5: Installed app picker (universal patches)
    AnimatedVisibility(
        visible = homeViewModel.showInstalledAppPickerDialog,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        InstalledAppPickerDialog(
            items = homeViewModel.installedAppsForPicker,
            isLoading = homeViewModel.loadingInstalledApps,
            onDismiss = {
                homeViewModel.showInstalledAppPickerDialog = false
                homeViewModel.cleanupPendingData()
            },
            onSelect = { homeViewModel.handleInstalledAppPickerSelection(it) }
        )
    }

    // Unsupported version dialog
    AnimatedVisibility(
        visible = homeViewModel.showUnsupportedVersionDialog != null,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        val dialogState = homeViewModel.showUnsupportedVersionDialog ?: return@AnimatedVisibility
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()

        UnsupportedVersionWarningDialog(
            packageName = dialogState.packageName,
            version = dialogState.version,
            versionCode = dialogState.versionCode,
            recommendedVersion = dialogState.recommendedVersion?.version,
            allCompatibleVersions = dialogState.compatibleVersionNames,
            versionDescriptions = dialogState.compatibleVersionDescriptions,
            compatibleVersionCodes = dialogState.compatibleVersionCodes,
            experimentalVersions = homeViewModel.getExperimentalVersionsForPackage(dialogState.packageName),
            isExperimental = dialogState.isExperimental,
            isExpertMode = isExpertMode,
            onDismiss = { homeViewModel.dismissUnsupportedVersionDialog() },
            onProceed = { homeViewModel.proceedWithUnsupportedVersion() }
        )
    }

    // Experimental version warning dialog
    AnimatedVisibility(
        visible = homeViewModel.showExperimentalVersionDialog != null,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        val dialogState = homeViewModel.showExperimentalVersionDialog ?: return@AnimatedVisibility

        ExperimentalVersionWarningDialog(
            appName = dialogState.packageName.let { homeViewModel.bundleAppMetadataFlow.value[it]?.displayName ?: it },
            packageName = dialogState.packageName,
            onDismiss = { homeViewModel.dismissExperimentalVersionDialog() },
            onProceed = { homeViewModel.proceedWithExperimentalVersion() }
        )
    }

    // Wrong package dialog
    AnimatedVisibility(
        visible = homeViewModel.showWrongPackageDialog != null,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        val dialogState = homeViewModel.showWrongPackageDialog ?: return@AnimatedVisibility

        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { homeViewModel.dismissWrongPackageDialog() }
        )
    }

    // No compatible versions dialog - shown when every declared version requires a higher SDK
    AnimatedVisibility(
        visible = homeViewModel.showNoCompatibleVersionsDialog != null,
        enter = Animations.overlayEnter,
        exit = Animations.overlayExit
    ) {
        val packageName = homeViewModel.showNoCompatibleVersionsDialog ?: return@AnimatedVisibility
        val appName = homeViewModel.bundleAppMetadataFlow.value[packageName]?.displayName
            ?: KnownApps.getAppName(packageName)
        NoCompatibleVersionsDialog(
            appName = appName,
            packageName = packageName,
            onDismiss = { homeViewModel.showNoCompatibleVersionsDialog = null }
        )
    }

    // Split APK Warning Dialog - shown when user picks a split APK for an app that prefers full APK
    if (homeViewModel.showSplitApkWarningDialog) {
        val appName = homeViewModel.pendingAppName ?: ""
        SplitApkWarningDialog(
            appName = appName,
            packageName = homeViewModel.pendingPackageName,
            onProceed = { homeViewModel.proceedWithSplitApk() },
            onPickAnother = {
                homeViewModel.dismissSplitApkWarning()
                storagePickerLauncher()
            },
            onDismiss = { homeViewModel.dismissSplitApkWarning() }
        )
    }

    // Invalid Signature Dialog - shown when the APK is not signed by the expected certificate
    homeViewModel.showInvalidSignatureDialog?.let { dialogState ->
        InvalidSignatureDialog(
            appName = dialogState.appName,
            packageName = homeViewModel.pendingPackageName,
            onPickAnother = {
                homeViewModel.dismissInvalidSignatureDialog()
                storagePickerLauncher()
            },
            onProceed = { homeViewModel.proceedIgnoringSignature() },
            onDismiss = { homeViewModel.dismissInvalidSignatureDialog() }
        )
    }

    // Metered Data dialog
    if (homeViewModel.showMeteredPatchingDialog) {
        MeteredPatchingDialog(
            packageName = homeViewModel.pendingPackageName,
            onDismiss = { homeViewModel.dismissMeteredPatchingDialog() },
            onRefreshAndPatch = { homeViewModel.refreshBundlesAndContinuePatching() },
            onPatchAnyway = { homeViewModel.dismissMeteredPatchingDialogAndProceed() }
        )
    }

    // Low Disk Space warning dialog
    if (homeViewModel.showLowDiskSpaceDialog) {
        LowDiskSpaceDialog(
            packageName = homeViewModel.pendingPackageName,
            freeBytes = homeViewModel.lowDiskSpaceFreeBytes,
            thresholdBytes = homeViewModel.lowDiskSpaceThresholdBytes,
            onDismiss = { homeViewModel.dismissLowDiskSpaceDialog() },
            onPatchAnyway = { homeViewModel.dismissLowDiskSpaceDialogAndProceed() }
        )
    }

    // Installed App Info Dialog
    homeViewModel.showInstalledAppInfoDialog?.let { packageName ->
        key(packageName, homeViewModel.installedAppDialogToken) {
            val installedAppInfoViewModel: InstalledAppInfoViewModel = koinViewModel(
                key = "${packageName}_${homeViewModel.installedAppDialogToken}",
                parameters = { parametersOf(packageName) }
            )
            InstalledAppInfoDialog(
                packageName = packageName,
                onDismiss = homeViewModel::dismissInstalledAppInfo,
                onTriggerPatchFlow = { originalPackageName, repatchedPackageName ->
                    homeViewModel.showPatchDialog(
                        packageName = originalPackageName,
                        repatchedPackageName = repatchedPackageName
                    )
                },
                homeViewModel = homeViewModel,
                viewModel = installedAppInfoViewModel
            )
        }
    }

    // Simple mode bundle selection dialog - shown when 2+ bundles have patches for the same app
    if (homeViewModel.showSimpleBundleSelectDialog) {
        val candidates = homeViewModel.simpleBundleSelectCandidates
        val versionsBySource = homeViewModel.pendingPackageName
            ?.let { homeViewModel.compatibleVersions[it] }
            .orEmpty()
            .groupBy { it.bundleUid }
        SimpleBundleSelectDialog(
            packageName = homeViewModel.pendingPackageName,
            candidates = candidates.map { (bundle, patches) ->
                val source = homeViewModel.getPatchSource(bundle.uid)
                SimpleBundleCandidate(
                    uid = bundle.uid,
                    displayTitle = source?.displayTitle
                        ?: homeViewModel.getBundleDisplayName(bundle.uid)
                        ?: bundle.name,
                    patchCount = patches.size,
                    recommendedVersion = versionsBySource[bundle.uid].orEmpty().recommended()?.version,
                    patchVersion = source?.version ?: bundle.version,
                    sourceType = source?.sourceType
                )
            },
            onSelect = { uid, rememberChoice ->
                homeViewModel.proceedWithSelectedBundle(uid, rememberChoice)
            },
            onDismiss = { homeViewModel.dismissSimpleBundleSelectDialog() },
            canRemember = true
        )
    }

    // Expert Mode Dialog
    if (homeViewModel.showExpertModeDialog) {
        // The dialogs raised over the selection wear the app's color, as the selection does
        ProvideAccent(rememberAppColor(homeViewModel.expertModeSelectedApp?.packageName)) {
            // Reading the property re-walks and re-sorts every bundle's patches, so it is taken once
            val allPatchesInfo = homeViewModel.expertModeAllPatchesInfo
            ExpertModeDialog(
                packageName = homeViewModel.expertModeSelectedApp?.packageName.orEmpty(),
                appIcon = homeViewModel.expertModeAppIcon,
                newPatches = homeViewModel.expertModeNewPatches,
                options = homeViewModel.expertModeOptions,
                allPatchesInfo = allPatchesInfo,
                totalSelectedCount = homeViewModel.expertModeTotalSelectedCount,
                totalPatchesCount = allPatchesInfo.sumOf { (_, patches) -> patches.size },
                hasMultipleBundles = homeViewModel.expertModeHasMultipleBundles,
                patchActions = ExpertPatchActions(
                    onPatchToggle = { bundleUid, patchName ->
                        homeViewModel.togglePatchInExpertMode(bundleUid, patchName)
                    },
                    onSelectAll = { bundleUid, patches ->
                        homeViewModel.expertModeSelectAll(bundleUid, patches)
                    },
                    onDeselectAll = { bundleUid, patches ->
                        homeViewModel.expertModeDeselectAll(bundleUid, patches)
                    },
                    onResetToDefault = { bundleUid ->
                        homeViewModel.expertModeResetToDefault(bundleUid)
                    },
                    onRestoreSaved = { bundleUid ->
                        homeViewModel.expertModeRestoreSaved(bundleUid)
                    },
                    onCopyFromBundle = { bundleUid ->
                        homeViewModel.openExpertModeCopyDialog(bundleUid)
                    },
                    onOptionChange = { bundleUid, patchName, optionKey, value ->
                        homeViewModel.updateOptionInExpertMode(bundleUid, patchName, optionKey, value)
                    },
                    onResetOptions = { bundleUid, patchName ->
                        homeViewModel.resetOptionsInExpertMode(bundleUid, patchName)
                    }
                ),
                savedPatches = homeViewModel.expertModeInitialPatches,
                lockStateOf = homeViewModel::expertModeLockState,
                holdsUniversalPatches = homeViewModel::expertModeSelectAllHoldsUniversal,
                prereleaseBundleUids = allPatchesInfo.mapNotNull { (bundle, _) ->
                    bundle.uid.takeIf { homeViewModel.getPatchSource(it)?.usesPrerelease == true }
                }.toSet(),
                hiddenSourceCount = homeViewModel.expertModeHiddenSources,
                onShowHiddenSources = {
                    homeViewModel.revealHiddenExpertModeSources()
                },
                onDismiss = {
                    homeViewModel.cleanupExpertModeData()
                },
                onProceed = {
                    homeViewModel.proceedExpertMode()
                }
            )

            // Raised over the selection, so closing it puts the user back in the dialog with the
            // offending option still there rather than dropping them out of the flow entirely
            homeViewModel.expertModeUnreadablePaths.takeIf { it.isNotEmpty() }?.let { failures ->
                UnusableOptionPathsDialog(
                    failures = failures,
                    onRetryAfterPermission = { homeViewModel.proceedExpertMode() },
                    canClearPaths = true,
                    onClearPaths = { homeViewModel.clearExpertModeUnreadablePaths() },
                    onDismiss = { homeViewModel.dismissExpertModeUnreadablePaths() }
                )
            }

            homeViewModel.expertModeCopy.targetBundleUid?.let { targetUid ->
                val selectedApp = homeViewModel.expertModeSelectedApp ?: return@let
                val targetBundle = homeViewModel.expertModeBundles.firstOrNull { it.uid == targetUid }
                    ?: return@let
                val appDisplayName = targetBundle.displayName ?: selectedApp.packageName
                CopySelectionFromBundleDialog(
                    target = CopySelectionTarget(
                        packageName = selectedApp.packageName,
                        bundleUid = targetUid,
                        bundleName = targetBundle.name,
                        appDisplayName = appDisplayName
                    ),
                    candidates = homeViewModel.expertModeCopy.candidates,
                    onConfirm = { homeViewModel.applyExpertModeCopy(it) },
                    onDismiss = { homeViewModel.expertModeCopy.close() }
                )
            }
        }
    }

    // Replacing the file of a local source keeps its uid, so the patch selection and options
    // stay attached instead of being stranded on a freshly added second source
    val openLocalBundleUpdatePicker = rememberAdaptiveFilePicker(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onResult = { uri ->
            val uid = homeViewModel.localBundleUpdateUid
            homeViewModel.localBundleUpdateUid = null
            if (uri != null && uid != null) homeViewModel.updateLocalSource(uid, uri)
        }
    )

    // Bundle management sheet
    if (homeViewModel.showBundleManagementSheet) {
        BundleManagementSheet(
            onDismissRequest = { homeViewModel.showBundleManagementSheet = false },
            // The sheet stays under the dialog, so nothing shows through while it opens
            onAddSource = { homeViewModel.showAddSourceDialog = true },
            onDelete = { bundle ->
                scope.launch {
                    homeViewModel.patchBundleRepository.remove(bundle)
                }
            },
            onDisable = { bundle ->
                scope.launch {
                    homeViewModel.patchBundleRepository.disable(bundle)
                }
            },
            onUpdate = { bundle ->
                if (bundle is RemotePatchBundle) {
                    scope.launch {
                        // A source that failed to load may hold a broken jar of the current version,
                        // which a plain version check would keep
                        homeViewModel.patchBundleRepository.update(
                            bundle,
                            force = bundle.state is PatchBundleSource.State.Failed,
                            showToast = true
                        )
                    }
                } else {
                    homeViewModel.localBundleUpdateUid = bundle.uid
                    openLocalBundleUpdatePicker()
                }
            },
            onRename = { bundle ->
                homeViewModel.bundleToRename = bundle
                homeViewModel.showRenameBundleDialog = true
            },
            onReorder = { orderedUids ->
                scope.launch {
                    homeViewModel.patchBundleRepository.reorderBundles(orderedUids)
                }
            },
            globalOnboardingState = globalOnboardingState
        )
    }

    // Add bundle dialog
    if (homeViewModel.showAddSourceDialog) {
        AddSourceDialog(
            onDismiss = {
                homeViewModel.showAddSourceDialog = false
                homeViewModel.clearPickedBundles()
            },
            onRemoteSubmit = { urls, chooseApps ->
                homeViewModel.showAddSourceDialog = false
                homeViewModel.showBundleManagementSheet = false
                homeViewModel.createRemoteSources(urls, chooseApps)
            },
            onLocalSubmit = { chooseApps ->
                homeViewModel.showAddSourceDialog = false
                homeViewModel.showBundleManagementSheet = false
                homeViewModel.importPickedBundles(chooseApps)
            },
            onLocalPick = openBundlePicker,
            onLocalRemove = homeViewModel::unpickBundle,
            localFiles = homeViewModel.pickedBundleImports,
            onCheckUrl = homeViewModel.patchBundleRepository::checkRemoteUrl
        )
    }

    // Deep link: Add bundle confirmation dialog
    homeViewModel.deepLinkPendingBundle?.let { bundle ->
        DeepLinkAddSourceDialog(
            url = bundle.url,
            name = bundle.name,
            onConfirm = { chooseApps -> homeViewModel.confirmDeepLinkBundle(chooseApps) },
            onDismiss = { homeViewModel.dismissDeepLinkBundle() }
        )
    }

    // .mpp file opened from file manager: Add bundle confirmation dialog
    homeViewModel.pendingMppUri?.let {
        MppImportDialog(
            manifest = homeViewModel.pendingMppManifest,
            fileName = homeViewModel.pendingMppFileName,
            onConfirm = { chooseApps -> homeViewModel.confirmMppImport(chooseApps) },
            onDismiss = { homeViewModel.dismissMppImport() }
        )
    }

    // App list of a source just added with "Choose apps" on
    homeViewModel.sourceAppsDialogUid?.let { uid ->
        val sources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
        val source = sources.firstOrNull { it.uid == uid }
        if (source != null) {
            SourceAppsDialog(
                onDismissRequest = homeViewModel::dismissSourceApps,
                src = source
            )
        }
    }

    // Rename bundle dialog
    if (homeViewModel.showRenameBundleDialog && homeViewModel.bundleToRename != null) {
        val bundle = homeViewModel.bundleToRename!!
        val duplicateNameError = stringResource(R.string.sources_dialog_duplicate_name_error)
        val missingBundleError = stringResource(R.string.sources_dialog_missing_error)

        RenameBundleDialog(
            initialValue = bundle.displayTitle,
            onDismissRequest = {
                homeViewModel.showRenameBundleDialog = false
                homeViewModel.bundleToRename = null
            },
            onConfirm = { value ->
                scope.launch {
                    val result = homeViewModel.patchBundleRepository.setDisplayName(
                        bundle.uid,
                        value.trim().ifEmpty { null }
                    )
                    when (result) {
                        PatchBundleRepository.DisplayNameUpdateResult.SUCCESS,
                        PatchBundleRepository.DisplayNameUpdateResult.NO_CHANGE -> {
                            homeViewModel.showRenameBundleDialog = false
                            homeViewModel.bundleToRename = null
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.DUPLICATE -> {
                            context.toast(duplicateNameError)
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.NOT_FOUND -> {
                            context.toast(missingBundleError)
                        }
                    }
                }
            }
        )
    }

    // Patches preview dialog (swipe-right on home app card)
    patchesItem.value?.let { item ->
        // Cards without bundle metadata were patched via "Other apps" with universal patches -
        // show what was actually applied instead of an empty "available" list
        val isUniversalOnly = remember(item.packageName) {
            item.installedApp != null &&
                    item.packageName !in homeViewModel.bundleAppMetadataFlow.value
        }
        // Null until the applied patches are read back, which the dialog shows as loading
        // rather than as a list that happens to be empty
        val patchesByBundle: Map<Int, List<PatchInfo>>? = if (isUniversalOnly) {
            produceState<Map<Int, List<PatchInfo>>?>(initialValue = null, item.packageName) {
                value = homeViewModel.getAppliedPatchesForPackage(item.packageName)
            }.value
        } else {
            remember(item.packageName) {
                homeViewModel.getPatchesForPackage(item.packageName)
            }
        }
        val bundleNames = remember(patchesByBundle) {
            patchesByBundle.orEmpty().keys.associateWith { uid ->
                homeViewModel.getBundleDisplayName(uid) ?: uid.toString()
            }
        }
        AppPatchesDialog(
            item = item,
            patchesByBundle = patchesByBundle.orEmpty(),
            bundleNames = bundleNames,
            isLoading = patchesByBundle == null,
            onDismiss = { patchesItem.value = null }
        )
    }
}

/**
 * Warning dialog shown before patching starts when the device has less free storage than
 * [thresholdBytes].
 */
@Composable
fun LowDiskSpaceDialog(
    packageName: String?,
    freeBytes: Long,
    thresholdBytes: Long,
    onDismiss: () -> Unit,
    onPatchAnyway: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_low_disk_space_dialog_title),
        description = stringResource(
            R.string.home_low_disk_space_dialog_message,
            formatGigabytes(freeBytes),
            formatGigabytes(thresholdBytes)
        ),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onPatchAnyway,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Notice(
                text = stringResource(R.string.home_low_disk_space_dialog_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )
        }
    }
}

/**
 * Dialog shown when the user tries to patch while there is a pending bundle update
 * that has not been downloaded yet because the device is on a metered (mobile data).
 */
@Composable
fun MeteredPatchingDialog(
    packageName: String?,
    onDismiss: () -> Unit,
    onRefreshAndPatch: () -> Unit,
    onPatchAnyway: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_outdated_patches_dialog_title),
        description = stringResource(R.string.home_outdated_patches_dialog_message),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppDialogButton(
                    text = stringResource(R.string.home_outdated_patches_dialog_update_and_patch),
                    onClick = onRefreshAndPatch,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.SystemUpdateAlt
                )
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                    onPrimaryClick = onPatchAnyway,
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = onDismiss
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Notice(
                text = stringResource(R.string.home_outdated_patches_dialog_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )
        }
    }
}

/**
 * Confirmation dialog shown when the app is opened via a deep link to add a patch bundle.
 * Displays the URL (and optional name) and asks the user to confirm before adding.
 */
@Composable
fun DeepLinkAddSourceDialog(
    url: String,
    name: String?,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var chooseApps by rememberSaveable { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.deep_link_add_source_title),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = { onConfirm(chooseApps) },
                primaryIcon = Icons.Outlined.Extension,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val avatarUrl = remember(url) {
                runCatching {
                    val uri = URI(url)
                    val owner = uri.path.trim('/').split('/').firstOrNull()
                    val isGitLab = uri.host?.contains("gitlab.com", ignoreCase = true) == true
                    if (owner != null) {
                        if (isGitLab) "https://unavatar.io/gitlab/$owner"
                        else "https://github.com/$owner.png"
                    } else null
                }.getOrNull()
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                if (avatarUrl != null) {
                    RemoteAvatar(
                        url = avatarUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.deep_link_add_source_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Bundle details card
            LabeledSection(title = name) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = LocalDialogSecondaryTextColor.current,
                    modifier = Modifier.padding(horizontal = Defaults.ContentPadding)
                )
            }

            Notice(
                text = stringResource(R.string.deep_link_add_source_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )

            ChooseAppsToggle(
                checked = chooseApps,
                onCheckedChange = { chooseApps = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Confirmation dialog shown when a .mpp file is opened from a file manager.
 */
@Composable
fun MppImportDialog(
    manifest: MppManifest?,
    fileName: String?,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var chooseApps by rememberSaveable { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.deep_link_add_source_title),
        description = stringResource(R.string.deep_link_add_source_message),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = { onConfirm(chooseApps) },
                primaryIcon = Icons.Outlined.Extension,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Bundle details card, without a body when the name is all there is
            val displayName = manifest?.name ?: fileName
            val hasDetails = manifest?.description != null || manifest?.version != null ||
                    manifest?.author != null || manifest?.source != null ||
                    (fileName != null && manifest?.name != null)
            SectionCard(accentColor = LocalAccent.current) {
                Column {
                    if (displayName != null) CardHeader(title = displayName)
                    if (hasDetails) Column(
                        modifier = Modifier.padding(Defaults.ContentPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Description
                        manifest.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Metadata row: version, author
                        if (manifest.version != null || manifest.author != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                manifest.version?.let { version ->
                                    StatusBadge(
                                        text = "v$version",
                                        icon = Icons.Outlined.NewReleases,
                                        tone = SemanticTone.Primary
                                    )
                                }
                                manifest.author?.let { author ->
                                    StatusBadge(
                                        text = author,
                                        icon = Icons.Outlined.Person,
                                        tone = SemanticTone.Neutral
                                    )
                                }
                            }
                        }

                        // Source URL
                        manifest.source?.let { source ->
                            Text(
                                text = source,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = LocalDialogSecondaryTextColor.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Filename (always shown as secondary info)
                        if (fileName != null && manifest.name != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = LocalDialogSecondaryTextColor.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Notice(
                text = stringResource(R.string.deep_link_add_source_warning),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )

            ChooseAppsToggle(
                checked = chooseApps,
                onCheckedChange = { chooseApps = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A single selectable bundle entry for [SimpleBundleSelectDialog]. */
data class SimpleBundleCandidate(
    val uid: Int,
    val displayTitle: String,
    val patchCount: Int,
    val recommendedVersion: String? = null,
    val patchVersion: String? = null,
    val sourceType: BundleSourceType? = null
)

/**
 * Dialog shown in Simple mode when 2+ patch sources have patches for the selected app.
 * Lets the user pick exactly one source to apply.
 *
 * [canRemember] offers to settle the question for this app rather than only for this run, which
 * the caller answers by keeping the app from the sources that were turned down. Callers picking a
 * source for one run of their own leave it off.
 */
@Composable
fun SimpleBundleSelectDialog(
    packageName: String?,
    candidates: List<SimpleBundleCandidate>,
    onSelect: (uid: Int, rememberChoice: Boolean) -> Unit,
    onDismiss: () -> Unit,
    canRemember: Boolean = false
) {
    val selected = remember { mutableStateOf(candidates.firstOrNull()?.uid) }
    val rememberChoice = remember { mutableStateOf(false) }
    val sourcesByUid = rememberSourcesByUid()

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_simple_bundle_select_title),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                onPrimaryClick = { selected.value?.let { onSelect(it, rememberChoice.value) } },
                primaryEnabled = selected.value != null,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            val preInstalledLabel = stringResource(R.string.sources_dialog_preinstalled)
            val remoteLabel = stringResource(R.string.sources_dialog_remote)
            val localLabel = stringResource(R.string.sources_dialog_local)
            val patchLabel = stringResource(R.string.patches)
            val recommendedVersionLabel = stringResource(R.string.home_recommended_version)
            candidates.forEach { candidate ->
                val isSelected = selected.value == candidate.uid
                // Drawn and colored the way the source list draws it, so the source is recognized
                // at a glance
                val source = sourcesByUid[candidate.uid]
                val accentColor = source?.let { rememberBundleAccent(it) }
                val patchCountText = pluralStringResource(
                    R.plurals.patch_count,
                    candidate.patchCount,
                    candidate.patchCount.toString()
                )
                val patchVersionText = candidate.patchVersion
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$patchLabel v${it.removePrefix("v")}" }
                val recommendedVersionText = candidate.recommendedVersion
                    ?.let { "$recommendedVersionLabel v$it" }
                val sourceTypeLabel = candidate.sourceType?.let { type ->
                    when (type) {
                        BundleSourceType.PreInstalled -> preInstalledLabel
                        BundleSourceType.Remote -> remoteLabel
                        BundleSourceType.Local -> localLabel
                    }
                }
                val cardContentDescription = buildString {
                    append(candidate.displayTitle)
                    sourceTypeLabel?.let { append(", $it") }
                    append(", $patchCountText")
                    patchVersionText?.let { append(", $it") }
                    recommendedVersionText?.let { append(", $it") }
                }

                RadioSelectionCard(
                    selected = isSelected,
                    onSelect = { selected.value = candidate.uid },
                    contentDescription = cardContentDescription,
                    accentColor = accentColor
                ) {
                    if (source != null) {
                        BundleIcon(bundle = source, modifier = Modifier.size(40.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = candidate.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalDialogTextColor.current,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            candidate.sourceType?.let { type ->
                                BundleTypeBadge(type)
                            }
                        }
                        Text(
                            text = patchCountText,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                        if (patchVersionText != null) {
                            Text(
                                text = patchVersionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        }
                        if (recommendedVersionText != null) {
                            Text(
                                text = recommendedVersionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        }
                    }
                }
            }
        }

        // Outside the group above, since this answers what to do with the sources that were not
        // picked rather than being one more of them
        if (canRemember) {
            // The cards above carry the same round indicator, so the two kinds of choice in
            // this dialog are told apart by their shape rather than by two styles of box
            SelectionCheckRow(
                text = stringResource(R.string.home_simple_bundle_select_remember),
                checked = rememberChoice.value,
                onCheckedChange = { rememberChoice.value = it },
                modifier = Modifier.padding(top = Defaults.ContentPaddingSmall)
            )
        }
    }
}

/**
 * Dialog shown on Android 11+ when install apps permission is needed.
 */
@Composable
fun Android11Dialog(
    onDismissRequest: () -> Unit,
    onContinue: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.android_11_bug_dialog_title),
        description = stringResource(R.string.android_11_bug_dialog_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.continue_),
                onPrimaryClick = onContinue,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismissRequest
            )
        }
    )
}
