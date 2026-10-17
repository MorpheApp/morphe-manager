/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.BundleSourceType
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.bundleAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.ownerAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.sourceType
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.recommended
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.patcher.UnusableOptionPathsDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.sources.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Container for all home screen dialogs.
 */
@Composable
fun HomeDialogs(
    homeViewModel: HomeViewModel,
    storagePickerLauncher: () -> Unit,
    openBundlePicker: () -> Unit,
    patchesItem: MutableState<HomeAppItem?>,
    onSourceAdded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val messages = koinInject<UserMessages>()
    // Kept outside the dialog so the picker state survives the download dialog's exit animation
    val openApkDownloadHelper = rememberApkDownloadHelperAction(
        host = homeViewModel,
        enabled = homeViewModel.showDownloadInstructionsDialog
    )

    // APK selection processing overlay - blocks interaction while APK is loaded/validated in background
    Overlay(visible = homeViewModel.processingApkSelection) {
        PulsingLogoWithCaption(caption = stringResource(R.string.processing_apk))
    }

    // Dialog 1: APK availability
    DialogHost(
        visible = homeViewModel.showApkAvailabilityDialog &&
                homeViewModel.pendingPackageName != null &&
                homeViewModel.pendingAppName != null,
        handsOver = homeViewModel.showDownloadInstructionsDialog
    ) {
        val appName = held(homeViewModel.pendingAppName) ?: return@DialogHost
        // Held so the dialog leaves as it stood, color included, as dismissing it clears the pending data
        val packageName = held(homeViewModel.pendingPackageName)
        val recommendedVersion = held(homeViewModel.pendingRecommendedVersion)
        val compatibleVersions = held(homeViewModel.pendingCompatibleVersions)
        val selectedDownloadVersion = held(homeViewModel.pendingSelectedDownloadVersion)
        val usingMountInstall = homeViewModel.usingMountInstall
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()
        val savedApkInfo = held(homeViewModel.pendingSavedApkInfo)
        val installedApkInfo = held(homeViewModel.pendingInstalledApkInfo)
        val installedAppVersion = held(homeViewModel.pendingInstalledAppVersion)
        val stockAppInstalled = held(homeViewModel.pendingStockAppInstalled) == true

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
                homeViewModel.showDownloadInstructionsDialog = true
                homeViewModel.resolveDownloadRedirect()
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
    DialogHost(
        visible = homeViewModel.showDownloadInstructionsDialog &&
                homeViewModel.pendingPackageName != null &&
                homeViewModel.pendingAppName != null,
        handsOver = homeViewModel.showFilePickerPromptDialog
    ) {
        val usingMountInstall = homeViewModel.usingMountInstall
        // Held so the color does not flicker during the exit animation
        val packageName = held(homeViewModel.pendingPackageName)
        // Held for the same reason, since the pending data is cleared as the dialog leaves
        val appName = held(homeViewModel.pendingAppName).orEmpty()
        val downloadUrl = held(homeViewModel.resolvedDownloadUrl)
        val stockAppInstalled = held(homeViewModel.pendingStockAppInstalled) == true
        // Settled in dialog 1 and held for the same reason, so the steps stay put on the way out
        val requestedVersion = held(
            (homeViewModel.pendingSelectedDownloadVersion ?: homeViewModel.pendingRecommendedVersion)?.version
        )

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
            downloadUrl = downloadUrl,
            requestedVersion = requestedVersion,
            usingMountInstall = usingMountInstall,
            stockAppInstalled = stockAppInstalled,
            downloadColor = downloadColor,
            isApkBundle = isApkBundle,
            onDismiss = {
                homeViewModel.showDownloadInstructionsDialog = false
                homeViewModel.cleanupPendingData()
            },
            onOpenApkDownloadHelper = openApkDownloadHelper,
            onContinue = homeViewModel::handleDownloadInstructionsContinue
        )
    }

    // Dialog 3: File picker prompt
    DialogHost(visible = homeViewModel.showFilePickerPromptDialog && homeViewModel.pendingAppName != null) {
        val appName = held(homeViewModel.pendingAppName) ?: return@DialogHost
        val packageName = held(homeViewModel.pendingPackageName)
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
    DialogHost(visible = homeViewModel.showInstalledAppPickerDialog) {
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
    DialogHost(target = homeViewModel.showUnsupportedVersionDialog) { dialogState ->
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
    DialogHost(target = homeViewModel.showExperimentalVersionDialog) { dialogState ->
        ExperimentalVersionWarningDialog(
            appName = dialogState.packageName.let { homeViewModel.bundleAppMetadataFlow.value[it]?.displayName ?: it },
            packageName = dialogState.packageName,
            onDismiss = { homeViewModel.dismissExperimentalVersionDialog() },
            onProceed = { homeViewModel.proceedWithExperimentalVersion() }
        )
    }

    // Wrong package dialog
    DialogHost(target = homeViewModel.showWrongPackageDialog) { dialogState ->
        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { homeViewModel.dismissWrongPackageDialog() }
        )
    }

    // No compatible versions dialog - shown when every declared version requires a higher SDK
    DialogHost(target = homeViewModel.showNoCompatibleVersionsDialog) { packageName ->
        val appName = homeViewModel.bundleAppMetadataFlow.value[packageName]?.displayName
            ?: KnownApps.getAppName(packageName)
        NoCompatibleVersionsDialog(
            appName = appName,
            packageName = packageName,
            onDismiss = { homeViewModel.showNoCompatibleVersionsDialog = null }
        )
    }

    // Split APK Warning Dialog - shown when user picks a split APK for an app that prefers full APK
    DialogHost(visible = homeViewModel.showSplitApkWarningDialog) {
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
    DialogHost(target = homeViewModel.showInvalidSignatureDialog) { dialogState ->
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
    DialogHost(visible = homeViewModel.showMeteredPatchingDialog) {
        MeteredPatchingDialog(
            packageName = homeViewModel.pendingPackageName,
            onDismiss = { homeViewModel.dismissMeteredPatchingDialog() },
            onRefreshAndPatch = { homeViewModel.refreshBundlesAndContinuePatching() },
            onPatchAnyway = { homeViewModel.dismissMeteredPatchingDialogAndProceed() }
        )
    }

    // Low Disk Space warning dialog
    DialogHost(visible = homeViewModel.showLowDiskSpaceDialog) {
        LowDiskSpaceDialog(
            packageName = homeViewModel.pendingPackageName,
            freeBytes = homeViewModel.lowDiskSpaceFreeBytes,
            thresholdBytes = homeViewModel.lowDiskSpaceThresholdBytes,
            onDismiss = { homeViewModel.dismissLowDiskSpaceDialog() },
            onPatchAnyway = { homeViewModel.dismissLowDiskSpaceDialogAndProceed() }
        )
    }

    // Installed App Info Dialog
    DialogHost(target = homeViewModel.showInstalledAppInfoDialog) { packageName ->
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
    DialogHost(visible = homeViewModel.showSimpleBundleSelectDialog) {
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
    DialogHost(visible = homeViewModel.showExpertModeDialog) {
        // The dialogs raised over the selection wear the app's color, as the selection does
        ProvideAccent(rememberAppColor(homeViewModel.expertModeSelectedApp?.packageName)) {
            ExpertModeDialog(
                packageName = homeViewModel.expertModeSelectedApp?.packageName.orEmpty(),
                appIcon = homeViewModel.expertModeAppIcon,
                selection = ExpertPatchSelection(
                    allPatchesInfo = homeViewModel.expertModeAllPatchesInfo,
                    options = homeViewModel.expertModeOptions,
                    totalSelectedCount = homeViewModel.expertModeTotalSelectedCount,
                    newPatches = homeViewModel.expertModeNewPatches,
                    savedPatches = homeViewModel.expertModeInitialPatches,
                    lockStateOf = homeViewModel::expertModeLockState,
                    holdsUniversalPatches = homeViewModel::expertModeSelectAllHoldsUniversal
                ),
                hasMultipleBundles = homeViewModel.expertModeHasMultipleBundles,
                patchActions = ExpertPatchActions(
                    onPatchToggle = homeViewModel::togglePatchInExpertMode,
                    onSelectAll = homeViewModel::expertModeSelectAll,
                    onDeselectAll = homeViewModel::expertModeDeselectAll,
                    onResetToDefault = homeViewModel::expertModeResetToDefault,
                    onRestoreSaved = homeViewModel::expertModeRestoreSaved,
                    onCopyFromBundle = homeViewModel::openExpertModeCopyDialog,
                    onOptionChange = homeViewModel::updateOptionInExpertMode,
                    onResetOptions = homeViewModel::resetOptionsInExpertMode
                ),
                usesPrerelease = { homeViewModel.getPatchSource(it)?.usesPrerelease == true },
                hiddenSourceCount = homeViewModel.expertModeHiddenSources,
                onShowHiddenSources = homeViewModel::revealHiddenExpertModeSources,
                onDismiss = homeViewModel::cleanupExpertModeData,
                onProceed = homeViewModel::proceedExpertMode
            )

            // Raised over the selection, so closing it puts the user back in the dialog with the
            // offending option still there rather than dropping them out of the flow entirely
            DialogHost(target = homeViewModel.expertModeUnreadablePaths.takeIf { it.isNotEmpty() }) { failures ->
                UnusableOptionPathsDialog(
                    failures = failures,
                    onRetryAfterPermission = { homeViewModel.proceedExpertMode() },
                    canClearPaths = true,
                    onClearPaths = { homeViewModel.clearExpertModeUnreadablePaths() },
                    onDismiss = { homeViewModel.dismissExpertModeUnreadablePaths() }
                )
            }

            val copyTarget = homeViewModel.expertModeSelectedApp?.let { selectedApp ->
                homeViewModel.expertModeBundles
                    .firstOrNull { it.uid == homeViewModel.expertModeCopy.targetBundleUid }
                    ?.let { targetBundle ->
                        CopySelectionTarget(
                            packageName = selectedApp.packageName,
                            bundleUid = targetBundle.uid,
                            bundleName = targetBundle.name,
                            appDisplayName = targetBundle.displayName ?: selectedApp.packageName
                        )
                    }
            }
            DialogHost(target = copyTarget) { target ->
                CopySelectionFromBundleDialog(
                    target = target,
                    candidates = homeViewModel.expertModeCopy.candidates,
                    onConfirm = { homeViewModel.applyExpertModeCopy(it) },
                    onDismiss = { homeViewModel.expertModeCopy.close() }
                )
            }
        }
    }

    // Add bundle dialog
    DialogHost(visible = homeViewModel.showAddSourceDialog) {
        AddSourceDialog(
            onDismiss = {
                homeViewModel.showAddSourceDialog = false
                homeViewModel.clearPickedBundles()
            },
            onRemoteSubmit = { urls, chooseApps ->
                homeViewModel.showAddSourceDialog = false
                homeViewModel.createRemoteSources(urls, chooseApps)
                onSourceAdded()
            },
            onLocalSubmit = { chooseApps ->
                homeViewModel.showAddSourceDialog = false
                homeViewModel.importPickedBundles(chooseApps)
                onSourceAdded()
            },
            onLocalPick = openBundlePicker,
            onLocalRemove = homeViewModel::unpickBundle,
            localFiles = homeViewModel.pickedBundleImports,
            onCheckUrl = homeViewModel.patchBundleRepository::checkRemoteUrl
        )
    }

    // Deep link: Add source confirmation dialog
    DialogHost(target = homeViewModel.deepLinkPendingSource) { source ->
        val sourceMuteRepository: SourceMuteRepository = koinInject()
        val appMetadata by homeViewModel.patchBundleRepository.allAppMetadata.collectAsStateWithLifecycle()
        val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())
        // Read again as the sources load, since a link that starts the app arrives before they do
        val sources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
        val existingUid = remember(source, sources) {
            homeViewModel.patchBundleRepository.existingSourceUid(source.url)
        }
        DeepLinkAddSourceDialog(
            url = source.url,
            name = source.name,
            packageName = source.packageName,
            // The source is not read yet, so a name another source knows wins over the one the link gives
            appName = source.packageName?.let { appMetadata[it]?.displayName ?: source.appName ?: it },
            appGradientColors = source.packageName?.let { appMetadata[it]?.gradientColors },
            isAdded = existingUid != null,
            isAppLeftOut = existingUid != null && existingUid in keptFrom[source.packageName].orEmpty(),
            onConfirm = { chooseApps -> homeViewModel.confirmDeepLinkSource(chooseApps) },
            onDismiss = { homeViewModel.dismissDeepLinkSource() }
        )
    }

    // .mpp file opened from file manager: Add bundle confirmation dialog
    DialogHost(target = homeViewModel.pendingMppUri) {
        MppImportDialog(
            manifest = homeViewModel.pendingMppManifest,
            fileName = homeViewModel.pendingMppFileName,
            onConfirm = { chooseApps -> homeViewModel.confirmMppImport(chooseApps) },
            onDismiss = { homeViewModel.dismissMppImport() }
        )
    }

    // App list of a source just added with "Choose apps" on
    val sources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle()
    DialogHost(target = sources.firstOrNull { it.uid == homeViewModel.sourceAppsDialogUid }) { source ->
        SourceAppsDialog(
            onDismissRequest = homeViewModel::dismissSourceApps,
            src = source
        )
    }

    // Rename bundle dialog
    DialogHost(target = homeViewModel.bundleToRename?.takeIf { homeViewModel.showRenameBundleDialog }) { bundle ->
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
                            messages.show(duplicateNameError)
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.NOT_FOUND -> {
                            messages.show(missingBundleError)
                        }
                    }
                }
            }
        )
    }

    // Patches preview dialog (swipe-right on home app card)
    DialogHost(target = patchesItem.value) { item ->
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
fun DialogHostScope.LowDiskSpaceDialog(
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
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
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
fun DialogHostScope.MeteredPatchingDialog(
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
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
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
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
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
 * Asks before adding a source handed over from outside Morphe, by a link or by a file opened in a
 * file manager. Laid out like the result of a patching run: [icon] over [heading] and the
 * [question], then [details] on what would be added.
 *
 * [offersChooseApps] asks under the details whether to open the app list once the source has
 * loaded, for a source added whole. [warns] says to trust the source, which one already added was
 * trusted for.
 */
@Composable
private fun DialogHostScope.AddSourceConfirmDialog(
    heading: String,
    subheading: String?,
    question: String,
    icon: @Composable () -> Unit,
    details: (@Composable () -> Unit)?,
    offersChooseApps: Boolean,
    warns: Boolean,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var chooseApps by rememberSaveable { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = onDismiss,
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
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = dialogTextColor(),
                    textAlign = TextAlign.Center
                )
                subheading?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogSecondaryTextColor(),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = dialogSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Defaults.SpaceSmall)
                )
            }

            details?.invoke()

            // A choice about what happens next, so it stays out of what is added
            if (offersChooseApps) {
                SelectionCheckRow(
                    text = stringResource(R.string.sources_dialog_choose_apps_description),
                    checked = chooseApps,
                    onCheckedChange = { chooseApps = it }
                )
            }

            if (warns) {
                // A line rather than a card, so it reads as a reminder and not as a second question
                val color = SemanticTone.Warning.accent
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Defaults.SpaceSmall)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.deep_link_add_source_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}

/** Size of the source's icon, the one a patching run's result gives the app's on a phone. */
private val SourceIconSize = 112.dp

/** Size of an app's icon in a row of what a link adds. */
private val SourceRowIconSize = 40.dp

/**
 * [icon] on a disc, covered by the picture at [pictureUrl], or at [fallbackPictureUrl] where that one
 * fails, once it loads, with [mark] cut into its corner the way the result of a patching run marks
 * the app's icon.
 */
@Composable
private fun SourceIcon(
    icon: ImageVector,
    mark: ImageVector,
    markColor: Color,
    pictureUrl: String? = null,
    fallbackPictureUrl: String? = null
) {
    MarkedIcon(
        mark = mark,
        markColor = markColor,
        iconSize = SourceIconSize,
        modifier = Modifier.padding(bottom = IconMarkOverhang)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            // Stands in until the picture loads, and for good when it does not
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.45f)
            )
            // Opaque, so an avatar with a transparent background does not show the host through it
            pictureUrl?.let {
                RemoteAvatar(
                    url = it,
                    fallbackUrl = fallbackPictureUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
        }
    }
}

/**
 * Confirmation dialog shown when the app is opened via a deep link to add a patch source, headed
 * by the repository under its owner's avatar as on the page the link comes from.
 *
 * A link opened for [packageName], named [appName], adds the source for that app alone, so it lists
 * the app instead of offering to choose, in [appGradientColors] while its icon is not on the device.
 * From a source that [isAdded] already, that app is all there is left to do, and [isAppLeftOut]
 * says whether it needs bringing back.
 */
@Composable
fun DialogHostScope.DeepLinkAddSourceDialog(
    url: String,
    name: String?,
    packageName: String?,
    appName: String?,
    appGradientColors: List<Color>?,
    isAdded: Boolean,
    isAppLeftOut: Boolean,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val bringsAppBack = packageName != null && isAdded
    // Links reach here for a repository only, which reads as owner/repo
    val repository = remoteLinkTitle(url)
    val patchBundleRepository: PatchBundleRepository = koinInject()
    // The picture a source ships beside its bundle, as an added source shows it
    val bundlePicture = remember(url) {
        runCatching { patchBundleRepository.normalizeRemoteBundleUrl(url) }.getOrNull()?.let(::bundleAvatarUrl)
    }

    AddSourceConfirmDialog(
        heading = name ?: repository.substringAfter('/'),
        subheading = url.bareLink().substringBefore('/') + "/" + repository.substringBefore('/'),
        question = stringResource(
            if (bringsAppBack) R.string.deep_link_add_source_app_back else R.string.deep_link_add_source_message
        ),
        icon = {
            SourceIcon(
                icon = remoteLinkIcon(url),
                mark = if (isAdded) Icons.Outlined.Check else remoteLinkIcon(url),
                markColor = if (isAdded) SemanticTone.Success.accent else MaterialTheme.colorScheme.inverseSurface,
                pictureUrl = bundlePicture ?: ownerAvatarUrl(url),
                fallbackPictureUrl = ownerAvatarUrl(url).takeIf { bundlePicture != null }
            )
        },
        // The header names the source, so only what the link narrows it to is listed
        details = if (packageName == null) null else {
            {
                SettingsGroup {
                    SettingsItemCard(onClick = null) {
                        IconTextRow(
                            modifier = Modifier.padding(Defaults.SpaceLarge),
                            leadingContent = {
                                AppIcon(
                                    packageName = packageName,
                                    contentDescription = null,
                                    modifier = Modifier.size(SourceRowIconSize),
                                    placeholderGradientColors = appGradientColors
                                        ?: AppCardColorDefaults.defaultGradientColors
                                )
                            },
                            title = appName ?: packageName,
                            description = stringResource(
                                when {
                                    !isAdded -> R.string.deep_link_add_source_only_app
                                    isAppLeftOut -> R.string.sources_apps_left_out
                                    else -> R.string.sources_apps_brought
                                }
                            )
                        )
                    }
                }
            }
        },
        offersChooseApps = packageName == null,
        warns = !bringsAppBack,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Confirmation dialog shown when a .mpp file is opened from a file manager, headed by the name its
 * manifest gives, or the file's while it is read.
 */
@Composable
fun DialogHostScope.MppImportDialog(
    manifest: MppManifest?,
    fileName: String?,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val versionLabel = stringResource(R.string.version)
    val authorLabel = stringResource(R.string.author)
    val sourceLabel = stringResource(R.string.patcher_field_source)
    val fileLabel = stringResource(R.string.file)
    val rows = buildList<@Composable () -> Unit> {
        manifest?.version?.let { version ->
            add { InfoRow(icon = Icons.Outlined.Update, label = versionLabel, value = version.withVersionPrefix()) }
        }
        manifest?.author?.let { author ->
            add { InfoRow(icon = Icons.Outlined.Person, label = authorLabel, value = author) }
        }
        manifest?.source?.let { source ->
            add { InfoRow(icon = Icons.Outlined.Source, label = sourceLabel, value = source.bareLink()) }
        }
        // The file only tells something the heading does not once the manifest names the bundle
        if (fileName != null && manifest?.name != null) add {
            InfoRow(icon = Icons.Outlined.Description, label = fileLabel, value = fileName)
        }
    }

    AddSourceConfirmDialog(
        heading = manifest?.name ?: fileName ?: stringResource(R.string.deep_link_add_source_title),
        subheading = manifest?.description,
        question = stringResource(R.string.deep_link_add_source_message),
        icon = {
            SourceIcon(
                icon = Icons.Outlined.Extension,
                mark = Icons.AutoMirrored.Outlined.InsertDriveFile,
                markColor = MaterialTheme.colorScheme.inverseSurface
            )
        },
        details = if (rows.isEmpty()) null else {
            {
                InfoPanel {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) SettingsDivider()
                        row()
                    }
                }
            }
        },
        offersChooseApps = true,
        warns = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
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
fun DialogHostScope.SimpleBundleSelectDialog(
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
        SettingsGroup(modifier = Modifier.selectableGroup()) {
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
                        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = candidate.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = dialogTextColor(),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            candidate.sourceType?.let { type ->
                                BundleTypeBadge(type)
                            }
                        }
                        Text(
                            text = patchCountText,
                            style = MaterialTheme.typography.bodySmall,
                            color = dialogSecondaryTextColor()
                        )
                        if (patchVersionText != null) {
                            Text(
                                text = patchVersionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryTextColor()
                            )
                        }
                        if (recommendedVersionText != null) {
                            Text(
                                text = recommendedVersionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryTextColor()
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
                modifier = Modifier.padding(top = Defaults.SpaceSmall)
            )
        }
    }
}

/**
 * Dialog shown on Android 11+ when install apps permission is needed.
 */
@Composable
fun DialogHostScope.Android11Dialog(
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
