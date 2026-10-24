/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.bundleAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.ownerAvatarUrl
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.sourceType
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.recommended
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SourceScope
import app.morphe.manager.ui.screen.patcher.UnusableOptionPathsDialog
import app.morphe.manager.ui.screen.settings.SettingsNavigation
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.sources.*
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Container for all home screen dialogs.
 *
 * @param settingsNavigation Where a dialog leads out to the settings. The dialogs are put away
 *   while the settings are open and come back as they stood once they close.
 */
@Composable
fun HomeDialogs(
    homeViewModel: HomeViewModel,
    storagePickerLauncher: () -> Unit,
    openBundlePicker: () -> Unit,
    patchesItem: MutableState<HomeAppItem?>,
    onSourceAdded: () -> Unit,
    settingsNavigation: SettingsNavigation
) {
    val scope = rememberCoroutineScope()
    val messages = koinInject<UserMessages>()
    val flow = homeViewModel.patchFlow

    // APK selection processing overlay - blocks interaction while APK is loaded/validated in background
    Overlay(visible = flow.processingApkSelection) {
        PulsingLogoWithCaption(caption = stringResource(R.string.processing_apk))
    }

    // Kept outside the dialogs so the picker state survives the download dialog's exit animation
    WithApkDownloadHelpers(
        host = flow,
        enabled = flow.dialogs.top.let { it == HomeDialog.ApkAvailability || it == HomeDialog.DownloadInstructions }
    ) { apkDownloadHelpers ->
        // The patch flow over the app information it may have started from, in the order they opened
        DialogStack(flow.dialogs.entries) { dialog ->
            PatchFlowDialog(
                dialog = dialog,
                flow = flow,
                homeViewModel = homeViewModel,
                storagePickerLauncher = storagePickerLauncher,
                apkDownloadHelpers = apkDownloadHelpers,
                onTrustApkDownloadHelpers = { settingsNavigation.openSubpage(SettingsSubpage.APK_DOWNLOAD_HELPERS) }
            )
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
        val app = source.scope as? SourceScope.App
        DeepLinkAddSourceDialog(
            url = source.url,
            name = source.name,
            scope = source.scope,
            // The source is not read yet, so a name another source knows wins over the one the link gives
            appName = app?.let { appMetadata[it.packageName]?.displayName ?: it.appName ?: it.packageName },
            appGradientColors = app?.let { appMetadata[it.packageName]?.gradientColors },
            isAdded = existingUid != null,
            isAppLeftOut = app != null && existingUid != null && existingUid in keptFrom[app.packageName].orEmpty(),
            onConfirm = { chooseApps -> homeViewModel.confirmDeepLinkSource(chooseApps) },
            onDismiss = { homeViewModel.dismissDeepLinkSource() }
        )
    }

    // .mpp file opened from file manager: Add or update bundle confirmation dialog
    DialogHost(target = homeViewModel.pendingMpp) { pending ->
        MppImportDialog(
            manifest = pending.manifest,
            fileName = pending.fileName,
            check = pending.check,
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
 * The [dialog] of the patch flow, or the app information it may have started from.
 *
 * @param onTrustApkDownloadHelpers Opens the settings where an installed helper is trusted.
 */
@Composable
private fun DialogHostScope.PatchFlowDialog(
    dialog: HomeDialog,
    flow: PatchFlow,
    homeViewModel: HomeViewModel,
    storagePickerLauncher: () -> Unit,
    apkDownloadHelpers: ApkDownloadHelperOffer,
    onTrustApkDownloadHelpers: () -> Unit
) {
    // Held so a dialog leaves as it stood, color included, as the run is let go of while it closes
    val session = held(flow.session)
    val dismiss = flow::dismiss
    // A file shared to the app starts the run without a session, so the app is named by package.
    // Observed, as the sources may still be naming their apps when the first dialog opens
    val appNames by homeViewModel.appDisplayNamesFlow.collectAsStateWithLifecycle()
    fun appName(packageName: String) = session?.appName ?: flow.appNameFor(packageName, appNames)

    when (dialog) {
        is HomeDialog.AppInfo -> {
            val installedAppInfoViewModel: InstalledAppInfoViewModel = koinViewModel(
                key = "${dialog.packageName}_${dialog.token}",
                parameters = { parametersOf(dialog.packageName) }
            )
            InstalledAppInfoDialog(
                packageName = dialog.packageName,
                onDismiss = flow::dismissInstalledAppInfo,
                onTriggerPatchFlow = flow::showPatchDialog,
                homeViewModel = homeViewModel,
                viewModel = installedAppInfoViewModel
            )
        }

        // Dialog 1: APK availability
        HomeDialog.ApkAvailability -> ApkAvailabilityDialog(
            appName = (session ?: return).appName,
            packageName = session.packageName,
            recommendedVersion = session.recommendedVersion,
            compatibleVersions = session.compatibleVersions,
            selectedDownloadVersion = session.selectedVersion,
            onVersionSelect = flow::selectVersion,
            usingMountInstall = flow.usingMountInstall,
            stockAppInstalled = session.stockAppInstalled == true,
            isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking(),
            savedApkInfo = session.savedApk,
            installedApkInfo = session.installedApk,
            installedAppVersion = session.installedVersion,
            hiddenSourceCount = session.hiddenSources,
            onDismiss = flow::backFromApkSource,
            onHaveApk = storagePickerLauncher,
            onNeedApk = { apkDownloadHelpers.showInstructions(flow::showDownloadInstructions) },
            onUseSaved = flow::handleSavedApkSelection,
            onUseInstalled = flow::handleInstalledApkSelection
        )

        // Dialog 2: Download instructions
        HomeDialog.DownloadInstructions -> {
            session ?: return
            // Resolve download button color: bundle declared → default
            val bundleMetadata by homeViewModel.bundleAppMetadataFlow.collectAsStateWithLifecycle()
            val metadata = session.packageName?.let { bundleMetadata[it] }

            DownloadInstructionsDialog(
                appName = session.appName,
                packageName = session.packageName,
                downloadUrl = session.downloadUrl,
                requestedVersion = session.requestedVersion,
                usingMountInstall = flow.usingMountInstall,
                stockAppInstalled = session.stockAppInstalled == true,
                downloadColor = metadata?.downloadColor ?: KnownApps.DEFAULT_DOWNLOAD_COLOR,
                // True when the patch bundle explicitly requires a split archive (APKM/APKS/XAPK).
                // In that case the APKMirror button label becomes "DOWNLOAD APK BUNDLE" to match the site.
                isApkBundle = metadata?.apkFileType?.isApk == false,
                onDismiss = flow::backFromApkSource,
                onOpenApkDownloadHelper = apkDownloadHelpers.open,
                onTrustApkDownloadHelpers = onTrustApkDownloadHelpers.takeIf { apkDownloadHelpers.hasUntrusted },
                onContinue = flow::handleDownloadInstructionsContinue
            )
        }

        // Dialog 3: File picker prompt
        HomeDialog.FilePickerPrompt -> {
            session ?: return
            val isOtherApps = session.packageName == null

            FilePickerPromptDialog(
                appName = session.appName,
                packageName = session.packageName,
                isOtherApps = isOtherApps,
                isLoadingInstalledApps = flow.loadingInstalledApps,
                onDismiss = flow::backFromApkSource,
                onOpenFilePicker = storagePickerLauncher,
                onUseInstalledApp = if (isOtherApps) flow::loadInstalledAppsForPicker else null
            )
        }

        // Dialog 3.5: Installed app picker (universal patches)
        HomeDialog.InstalledAppPicker -> InstalledAppPickerDialog(
            items = flow.installedAppsForPicker,
            isLoading = flow.loadingInstalledApps,
            onDismiss = flow::backFromApkSource,
            onSelect = flow::handleInstalledAppPickerSelection
        )

        is HomeDialog.UnsupportedVersion -> {
            val dialogState = dialog.state
            UnsupportedVersionWarningDialog(
                packageName = dialogState.packageName,
                version = dialogState.version,
                versionCode = dialogState.versionCode,
                recommendedVersion = dialogState.recommendedVersion,
                compatibleVersions = dialogState.compatibleVersions,
                isExperimental = dialogState.isExperimental,
                isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking(),
                onDismiss = dismiss,
                onProceed = { flow.proceedDespiteVersion(dialog) }
            )
        }

        is HomeDialog.ExperimentalVersion -> ExperimentalVersionWarningDialog(
            appName = appName(dialog.state.packageName),
            packageName = dialog.state.packageName,
            onDismiss = dismiss,
            onProceed = { flow.proceedDespiteVersion(dialog) }
        )

        is HomeDialog.WrongPackage -> WrongPackageDialog(
            expectedPackage = dialog.state.expectedPackage,
            actualPackage = dialog.state.actualPackage,
            onDismiss = dismiss
        )

        // Shown when every declared version requires a higher SDK
        is HomeDialog.NoCompatibleVersions -> NoCompatibleVersionsDialog(
            appName = appName(dialog.packageName),
            packageName = dialog.packageName,
            onDismiss = dismiss
        )

        // Shown when user picks a split APK for an app that prefers full APK
        is HomeDialog.SplitApkWarning -> SplitApkWarningDialog(
            packageName = dialog.packageName,
            onProceed = flow::proceedWithSplitApk,
            onPickAnother = storagePickerLauncher,
            onDismiss = dismiss
        )

        // Shown when the APK is not signed by the expected certificate
        is HomeDialog.InvalidSignature -> InvalidSignatureDialog(
            appName = dialog.state.appName,
            packageName = dialog.state.packageName,
            onPickAnother = storagePickerLauncher,
            onProceed = flow::proceedIgnoringSignature,
            onDismiss = dismiss
        )

        HomeDialog.MeteredPatching -> MeteredPatchingDialog(
            packageName = session?.packageName,
            onDismiss = dismiss,
            onRefreshAndPatch = flow::refreshBundlesAndContinuePatching,
            onPatchAnyway = flow::dismissMeteredPatchingDialogAndProceed
        )

        is HomeDialog.LowDiskSpace -> LowDiskSpaceDialog(
            packageName = session?.packageName,
            freeBytes = dialog.freeBytes,
            thresholdBytes = LOW_DISK_SPACE_THRESHOLD_BYTES,
            onDismiss = dismiss,
            onPatchAnyway = flow::dismissLowDiskSpaceDialogAndProceed
        )

        // Shown in simple mode when 2+ bundles have patches for the same app
        is HomeDialog.SimpleBundleSelect -> SimpleBundleSelectDialog(
            packageName = dialog.packageName,
            choices = dialog.candidates,
            versions = homeViewModel.compatibleVersions[dialog.packageName].orEmpty(),
            onSelect = { uid, rememberChoice -> flow.proceedWithSelectedBundle(dialog, uid, rememberChoice) },
            onDismiss = dismiss
        )

        // The dialogs raised over the selection wear the app's color, as the selection does
        HomeDialog.ExpertMode -> {
            val selection = flow.expertMode
            val selectedApp = held(selection.app) ?: return
            ProvideAccent(rememberAppColor(selectedApp.packageName)) {
                ExpertModeDialog(
                    packageName = selectedApp.packageName,
                    appName = appName(selectedApp.packageName),
                    appIcon = flow.expertModeAppIcon,
                    selection = ExpertPatchSelection(
                        allPatchesInfo = selection.allPatchesInfo,
                        options = selection.options,
                        totalSelectedCount = selection.totalSelectedCount,
                        newPatches = selection.newPatches,
                        savedPatches = selection.savedPatches,
                        lockStateOf = selection::lockState,
                        holdsUniversalPatches = selection::selectAllHoldsUniversal
                    ),
                    hasMultipleBundles = selection.hasMultipleBundles,
                    patchActions = ExpertPatchActions(
                        onPatchToggle = selection::toggle,
                        onSelectAll = selection::selectAll,
                        onDeselectAll = selection::deselectAll,
                        onResetToDefault = selection::resetToDefault,
                        onRestoreSaved = selection::restoreSaved,
                        onCopyFromBundle = selection::openCopy,
                        onOptionChange = selection::updateOption,
                        onResetOptions = selection::resetOptions
                    ),
                    usesPrerelease = { homeViewModel.getPatchSource(it)?.usesPrerelease == true },
                    hiddenSourceCount = selection.hiddenSources,
                    onShowHiddenSources = selection::revealHiddenSources,
                    onDismiss = flow::dismissExpertMode,
                    onProceed = flow::proceedExpertMode
                )

                // Raised over the selection, so closing it puts the user back in the dialog with the
                // offending option still there rather than dropping them out of the flow entirely
                DialogHost(target = selection.unreadablePaths.takeIf { it.isNotEmpty() }) { failures ->
                    UnusableOptionPathsDialog(
                        failures = failures,
                        onRetryAfterPermission = flow::proceedExpertMode,
                        canClearPaths = true,
                        onClearPaths = flow::proceedExpertModeWithoutUnreadablePaths,
                        onDismiss = selection::dismissUnreadablePaths
                    )
                }

                val copyTarget = selection.bundles
                    .firstOrNull { it.uid == selection.copy.targetBundleUid }
                    ?.let { targetBundle ->
                        CopySelectionTarget(
                            packageName = selectedApp.packageName,
                            bundleUid = targetBundle.uid,
                            bundleName = targetBundle.name,
                            appDisplayName = targetBundle.displayName ?: selectedApp.packageName
                        )
                    }
                DialogHost(target = copyTarget) { target ->
                    CopySelectionFromBundleDialog(
                        target = target,
                        candidates = selection.copy.candidates,
                        onConfirm = selection::applyCopy,
                        onDismiss = selection.copy::close
                    )
                }
            }
        }

        // Asked on a rooted device before patching, as the patch mode decides which patches apply
        HomeDialog.PrePatchInstaller -> PrePatchInstallerDialog(
            packageName = session?.packageName,
            onSelectMount = { flow.resolvePrePatchInstallerChoice(useMount = true) },
            onSelectStandard = { flow.resolvePrePatchInstallerChoice(useMount = false) },
            onDismiss = dismiss
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
                icon = MaterialSymbols.Rounded.Warning
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
                    icon = MaterialSymbols.Rounded.System_update_alt
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
                icon = MaterialSymbols.Rounded.Warning
            )
        }
    }
}

/**
 * Asks before adding a source handed over from outside Morphe, or updating one, by a link or by
 * a file opened in a file manager. Laid out like the result of a patching run: [icon] over
 * [heading] and the [question], then [details] on what would be added.
 *
 * [offersChooseApps] asks under the details whether to open the app list once the source has
 * loaded, for a source added whole. [warns] says to trust the source, which one already added was
 * trusted for. [confirmText] and [confirmIcon] name what confirming does, adding by default.
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
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.add),
    confirmIcon: ImageVector = MaterialSymbols.Rounded.Extension,
    confirmEnabled: Boolean = true
) {
    var chooseApps by rememberSaveable { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = onDismiss,
        footer = {
            AppDialogButtonRow(
                primaryText = confirmText,
                onPrimaryClick = { onConfirm(chooseApps) },
                primaryIcon = confirmIcon,
                primaryEnabled = confirmEnabled,
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
                        imageVector = MaterialSymbols.Rounded.Warning,
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
 * A link that narrows the source by [scope] lists what it adds instead of offering to choose: the
 * app named [appName], in [appGradientColors] while its icon is not on the device, or the universal
 * patches. From a source that [isAdded] already, that app is all there is left to do, and
 * [isAppLeftOut] says whether it needs bringing back.
 */
@Composable
fun DialogHostScope.DeepLinkAddSourceDialog(
    url: String,
    name: String?,
    scope: SourceScope,
    appName: String?,
    appGradientColors: List<Color>?,
    isAdded: Boolean,
    isAppLeftOut: Boolean,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val bringsAppBack = scope is SourceScope.App && isAdded
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
                mark = if (isAdded) MaterialSymbols.Rounded.Check else remoteLinkIcon(url),
                markColor = if (isAdded) SemanticTone.Success.accent else MaterialTheme.colorScheme.inverseSurface,
                pictureUrl = bundlePicture ?: ownerAvatarUrl(url),
                fallbackPictureUrl = ownerAvatarUrl(url).takeIf { bundlePicture != null }
            )
        },
        // The header names the source, so only what the link narrows it to is listed
        details = when (scope) {
            SourceScope.Whole -> null
            is SourceScope.App -> {
                {
                    DeepLinkScopeRow(
                        leadingContent = {
                            AppIcon(
                                packageName = scope.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(SourceRowIconSize),
                                placeholderGradientColors = appGradientColors
                                    ?: AppCardColorDefaults.defaultGradientColors
                            )
                        },
                        title = appName ?: scope.packageName,
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
            // Universal patches are never left out, so an added source brings them already
            SourceScope.Universal -> {
                {
                    DeepLinkScopeRow(
                        leadingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(SourceRowIconSize)
                            )
                        },
                        title = stringResource(R.string.expert_mode_universal_patches),
                        description = stringResource(
                            if (isAdded) R.string.sources_apps_brought else R.string.deep_link_add_source_only_universal
                        )
                    )
                }
            }
        },
        offersChooseApps = scope == SourceScope.Whole,
        warns = !bringsAppBack,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** The one row a narrowed deep link lists, for the part of the source it adds. */
@Composable
private fun DeepLinkScopeRow(
    leadingContent: @Composable () -> Unit,
    title: String,
    description: String
) {
    SettingsGroup {
        SettingsItemCard(onClick = null) {
            IconTextRow(
                modifier = Modifier.padding(Defaults.SpaceLarge),
                leadingContent = leadingContent,
                title = title,
                description = description
            )
        }
    }
}

/**
 * Confirmation dialog shown when a .mpp file is opened from a file manager, headed by the name its
 * manifest gives, or the file's while it is read.
 *
 * As in the add source dialog, a file that [check] finds to be another version of a local source
 * updates it, and one already added has nothing to confirm.
 */
@Composable
fun DialogHostScope.MppImportDialog(
    manifest: MppManifest?,
    fileName: String?,
    check: LocalFileCheck?,
    onConfirm: (chooseApps: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val versionLabel = stringResource(R.string.version)
    val authorLabel = stringResource(R.string.author)
    val sourceLabel = stringResource(R.string.patcher_field_source)
    val fileLabel = stringResource(R.string.file)
    val rows = buildList<@Composable () -> Unit> {
        manifest?.version?.let { version ->
            add { InfoRow(icon = MaterialSymbols.Rounded.Update, label = versionLabel, value = version.withVersionPrefix()) }
        }
        manifest?.author?.let { author ->
            add { InfoRow(icon = MaterialSymbols.Rounded.Person, label = authorLabel, value = author) }
        }
        manifest?.source?.let { source ->
            add { InfoRow(icon = MaterialSymbols.Rounded.Folder_code, label = sourceLabel, value = source.bareLink()) }
        }
        // The file only tells something the heading does not once the manifest names the bundle
        if (fileName != null && manifest?.name != null) add {
            InfoRow(icon = MaterialSymbols.Rounded.Description, label = fileLabel, value = fileName)
        }
    }

    val isUpdate = check is LocalFileCheck.Update
    val isAdded = check == LocalFileCheck.Duplicate

    AddSourceConfirmDialog(
        heading = manifest?.name ?: fileName ?: stringResource(R.string.deep_link_add_source_title),
        subheading = manifest?.description,
        question = when (check) {
            is LocalFileCheck.Update -> stringResource(R.string.mpp_import_update_message, check.title)
            LocalFileCheck.Duplicate -> stringResource(R.string.sources_management_already_exists)
            LocalFileCheck.NotBundle -> stringResource(R.string.mpp_import_unreadable)
            else -> stringResource(R.string.deep_link_add_source_message)
        },
        icon = {
            SourceIcon(
                icon = MaterialSymbols.Rounded.Extension,
                mark = when {
                    isUpdate -> MaterialSymbols.Rounded.Update
                    isAdded -> MaterialSymbols.Rounded.Check
                    else -> MaterialSymbols.Rounded.Draft.autoMirrored
                },
                markColor = if (isAdded) SemanticTone.Success.accent else MaterialTheme.colorScheme.inverseSurface
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
        // Shown while the file is read too, as most files opened add a source
        offersChooseApps = check == null || check is LocalFileCheck.New,
        warns = !isAdded,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = stringResource(if (isUpdate) R.string.update else R.string.add),
        confirmIcon = if (isUpdate) MaterialSymbols.Rounded.Update else MaterialSymbols.Rounded.Extension,
        confirmEnabled = check is LocalFileCheck.New || isUpdate
    )
}

/**
 * Dialog shown in Simple mode when 2+ patch sources have patches for the selected app.
 * Lets the user pick exactly one source to apply.
 *
 * The remember option settles the question for this app rather than only for this run, which the
 * caller answers by keeping the app from the sources that were turned down.
 *
 * @param choices Each source on offer with the patches it would apply.
 * @param versions The versions the sources cover, which tell each source's recommended one.
 * @param initiallySelected The source picked when the dialog opens, the first one otherwise.
 */
@Composable
fun DialogHostScope.SimpleBundleSelectDialog(
    packageName: String?,
    choices: List<Pair<PatchBundleInfo.Scoped, Set<String>>>,
    versions: List<BundledAppTarget>,
    onSelect: (uid: Int, rememberChoice: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initiallySelected: Int? = null
) {
    // Keyed by app: a dialog reopened for another one starts over rather than offer a source it lacks
    val selected = remember(packageName) {
        mutableStateOf(
            initiallySelected?.takeIf { uid -> choices.any { (bundle, _) -> bundle.uid == uid } }
                ?: choices.firstOrNull()?.first?.uid
        )
    }
    val rememberChoice = remember(packageName) { mutableStateOf(false) }
    val sourcesByUid = rememberSourcesByUid()
    val versionsBySource = remember(versions) { versions.groupBy { it.bundleUid } }

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
            choices.forEach { (bundle, patches) ->
                val isSelected = selected.value == bundle.uid
                // Drawn and colored the way the source list draws it, so the source is recognized
                // at a glance
                val source = sourcesByUid[bundle.uid]
                val accentColor = source?.let { rememberBundleAccent(it) }
                val displayTitle = source?.displayTitle ?: bundle.name
                val sourceType = source?.sourceType
                val patchCountText = pluralStringResource(
                    R.plurals.patch_count,
                    patches.size,
                    patches.size.toString()
                )
                val patchVersionText = (source?.version ?: bundle.version)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$patchLabel ${it.withVersionPrefix()}" }
                val recommendedVersionText = versionsBySource[bundle.uid].orEmpty().recommended()
                    ?.version
                    ?.let { "$recommendedVersionLabel v$it" }
                val sourceTypeLabel = sourceType?.let { type ->
                    when (type) {
                        BundleSourceType.PreInstalled -> preInstalledLabel
                        BundleSourceType.Remote -> remoteLabel
                        BundleSourceType.Local -> localLabel
                    }
                }
                val cardContentDescription = buildString {
                    append(displayTitle)
                    sourceTypeLabel?.let { append(", $it") }
                    append(", $patchCountText")
                    patchVersionText?.let { append(", $it") }
                    recommendedVersionText?.let { append(", $it") }
                }

                RadioSelectionCard(
                    selected = isSelected,
                    onSelect = { selected.value = bundle.uid },
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
                                text = displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = dialogTextColor(),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            sourceType?.let { type ->
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
        // picked rather than being one more of them. The cards above carry the same round
        // indicator, so the two kinds of choice in this dialog are told apart by their shape
        // rather than by two styles of box
        SelectionCheckRow(
            text = stringResource(R.string.home_simple_bundle_select_remember),
            checked = rememberChoice.value,
            onCheckedChange = { rememberChoice.value = it },
            modifier = Modifier.padding(top = Defaults.SpaceSmall)
        )
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
