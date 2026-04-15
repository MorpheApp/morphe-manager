/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundledAppTarget
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.SavedApkInfo
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.RemoteAvatar
import app.morphe.manager.util.htmlAnnotatedString
import app.morphe.manager.util.toast
import app.morphe.patcher.patch.AppTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Container for all MorpheHomeScreen dialogs.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeDialogs(
    homeViewModel: HomeViewModel,
    storagePickerLauncher: () -> Unit,
    openBundlePicker: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog 1: APK availability
    AnimatedVisibility(
        visible = homeViewModel.showApkAvailabilityDialog && homeViewModel.pendingPackageName != null && homeViewModel.pendingAppName != null,
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(if (homeViewModel.showDownloadInstructionsDialog) 0 else MorpheDefaults.ANIMATION_DURATION))
    ) {
        val appName = homeViewModel.pendingAppName ?: return@AnimatedVisibility
        val recommendedVersion = homeViewModel.pendingRecommendedVersion
        val compatibleVersions = homeViewModel.pendingCompatibleVersions
        val recommendedBundleVersions = homeViewModel.pendingRecommendedBundleVersions
        val selectedDownloadVersion = homeViewModel.pendingSelectedDownloadVersion
        val usingMountInstall = homeViewModel.usingMountInstall
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()
        val savedApkInfo = homeViewModel.pendingSavedApkInfo

        ApkAvailabilityDialog(
            appName = appName,
            recommendedVersion = recommendedVersion,
            compatibleVersions = compatibleVersions,
            recommendedBundleVersions = recommendedBundleVersions,
            selectedDownloadVersion = selectedDownloadVersion,
            onVersionSelect = { homeViewModel.pendingSelectedDownloadVersion = it },
            usingMountInstall = usingMountInstall,
            isExpertMode = isExpertMode,
            savedApkInfo = savedApkInfo,
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
                    delay(50)
                    homeViewModel.showDownloadInstructionsDialog = true
                    homeViewModel.resolveDownloadRedirect()
                }
            },
            onUseSaved = {
                homeViewModel.handleSavedApkSelection()
            }
        )
    }

    // Dialog 2: Download instructions
    AnimatedVisibility(
        visible = homeViewModel.showDownloadInstructionsDialog && homeViewModel.pendingPackageName != null && homeViewModel.pendingAppName != null,
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(if (homeViewModel.showFilePickerPromptDialog) 0 else MorpheDefaults.ANIMATION_DURATION))
    ) {
        val usingMountInstall = homeViewModel.usingMountInstall
        // Remember packageName to prevent color flickering during exit animation
        val packageName = remember { homeViewModel.pendingPackageName }

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
            usingMountInstall = usingMountInstall,
            downloadColor = downloadColor,
            isApkBundle = isApkBundle,
            onDismiss = {
                homeViewModel.showDownloadInstructionsDialog = false
                homeViewModel.cleanupPendingData()
            }
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
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
    ) {
        val appName = homeViewModel.pendingAppName ?: return@AnimatedVisibility
        val isOtherApps = homeViewModel.pendingPackageName == null

        FilePickerPromptDialog(
            appName = appName,
            isOtherApps = isOtherApps,
            onDismiss = {
                homeViewModel.showFilePickerPromptDialog = false
                homeViewModel.cleanupPendingData()
            },
            onOpenFilePicker = {
                homeViewModel.showFilePickerPromptDialog = false
                storagePickerLauncher()
            }
        )
    }

    // Unsupported version dialog
    AnimatedVisibility(
        visible = homeViewModel.showUnsupportedVersionDialog != null,
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
    ) {
        val dialogState = homeViewModel.showUnsupportedVersionDialog ?: return@AnimatedVisibility
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()

        UnsupportedVersionWarningDialog(
            version = dialogState.version,
            recommendedVersion = dialogState.recommendedVersion?.version,
            allCompatibleVersions = dialogState.allCompatibleVersions.map { it.version ?: "" }.filter { it.isNotEmpty() },
            versionDescriptions = dialogState.allCompatibleVersions
                .mapNotNull { target ->
                    val v = target.version ?: return@mapNotNull null
                    val d = target.description ?: return@mapNotNull null
                    v to d
                }
                .toMap(),
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
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
    ) {
        val dialogState = homeViewModel.showExperimentalVersionDialog ?: return@AnimatedVisibility

        ExperimentalVersionWarningDialog(
            appName = dialogState.packageName.let { homeViewModel.bundleAppMetadataFlow.value[it]?.displayName ?: it },
            onDismiss = { homeViewModel.dismissExperimentalVersionDialog() },
            onProceed = { homeViewModel.proceedWithExperimentalVersion() }
        )
    }

    // Wrong package dialog
    AnimatedVisibility(
        visible = homeViewModel.showWrongPackageDialog != null,
        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
    ) {
        val dialogState = homeViewModel.showWrongPackageDialog ?: return@AnimatedVisibility

        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { homeViewModel.dismissWrongPackageDialog() }
        )
    }

    // Split APK Warning Dialog - shown when user picks a split APK for an app that prefers full APK
    if (homeViewModel.showSplitApkWarningDialog) {
        val appName = homeViewModel.pendingAppName ?: ""
        SplitApkWarningDialog(
            appName = appName,
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
            onDismiss = { homeViewModel.dismissMeteredPatchingDialog() },
            onRefreshAndPatch = { homeViewModel.refreshBundlesAndContinuePatching() },
            onPatchAnyway = { homeViewModel.dismissMeteredPatchingDialogAndProceed() }
        )
    }

    // Low Disk Space warning dialog
    if (homeViewModel.showLowDiskSpaceDialog) {
        LowDiskSpaceDialog(
            freeGb = homeViewModel.lowDiskSpaceFreeGb,
            thresholdGb = homeViewModel.lowDiskSpaceThresholdGb,
            onDismiss = { homeViewModel.dismissLowDiskSpaceDialog() },
            onPatchAnyway = { homeViewModel.dismissLowDiskSpaceDialogAndProceed() }
        )
    }

    // Expert Mode Dialog
    if (homeViewModel.showExpertModeDialog) {
        ExpertModeDialog(
            newPatches = homeViewModel.expertModeNewPatches,
            options = homeViewModel.expertModeOptions,
            allPatchesInfo = homeViewModel.expertModeAllPatchesInfo,
            totalSelectedCount = homeViewModel.expertModeTotalSelectedCount,
            totalPatchesCount = homeViewModel.expertModeTotalPatchesCount,
            hasMultipleBundles = homeViewModel.expertModeHasMultipleBundles,
            onPatchToggle = { bundleUid, patchName ->
                homeViewModel.togglePatchInExpertMode(bundleUid, patchName)
            },
            onSelectAll = { bundleUid, patches ->
                homeViewModel.expertModeSelectAll(bundleUid, patches)
            },
            onDeselectAll = { bundleUid, patches ->
                homeViewModel.expertModeDeselectAll(bundleUid, patches)
            },
            onResetToDefault = { bundleUid, allPatches ->
                homeViewModel.expertModeResetToDefault(bundleUid, allPatches)
            },
            onRestoreSaved = { bundleUid ->
                homeViewModel.expertModeRestoreSaved(bundleUid)
            },
            savedPatches = homeViewModel.expertModeInitialPatches,
            onOptionChange = { bundleUid, patchName, optionKey, value ->
                homeViewModel.updateOptionInExpertMode(bundleUid, patchName, optionKey, value)
            },
            onResetOptions = { bundleUid, patchName ->
                homeViewModel.resetOptionsInExpertMode(bundleUid, patchName)
            },
            onDismiss = {
                homeViewModel.cleanupExpertModeData()
            },
            onProceed = {
                homeViewModel.proceedExpertMode()
            }
        )
    }

    // Bundle management sheet
    if (homeViewModel.showBundleManagementSheet) {
        BundleManagementSheet(
            onDismissRequest = { homeViewModel.showBundleManagementSheet = false },
            onAddSource = {
                homeViewModel.showBundleManagementSheet = false
                homeViewModel.showAddSourceDialog = true
            },
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
                        homeViewModel.patchBundleRepository.update(bundle, showToast = true)
                    }
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
            }
        )
    }

    // Add bundle dialog
    if (homeViewModel.showAddSourceDialog) {
        AddSourceDialog(
            onDismiss = {
                homeViewModel.showAddSourceDialog = false
                homeViewModel.selectedBundleUri = null
                homeViewModel.selectedBundlePath = null
            },
            onLocalSubmit = {
                homeViewModel.showAddSourceDialog = false
                homeViewModel.selectedBundleUri?.let { uri ->
                    homeViewModel.createLocalSource(uri)
                }
                homeViewModel.selectedBundleUri = null
                homeViewModel.selectedBundlePath = null
            },
            onRemoteSubmit = { url ->
                homeViewModel.showAddSourceDialog = false
                homeViewModel.createRemoteSource(url, true)
            },
            onLocalPick = {
                openBundlePicker()
            },
            selectedLocalPath = homeViewModel.selectedBundlePath
        )
    }

    // Deep link: Add bundle confirmation dialog
    homeViewModel.deepLinkPendingBundle?.let { bundle ->
        DeepLinkAddSourceDialog(
            url = bundle.url,
            name = bundle.name,
            onConfirm = { homeViewModel.confirmDeepLinkBundle() },
            onDismiss = { homeViewModel.dismissDeepLinkBundle() }
        )
    }

    // Rename bundle dialog
    if (homeViewModel.showRenameBundleDialog && homeViewModel.bundleToRename != null) {
        val bundle = homeViewModel.bundleToRename!!

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
                            context.toast(context.getString(R.string.sources_dialog_duplicate_name_error))
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.NOT_FOUND -> {
                            context.toast(context.getString(R.string.sources_dialog_missing_error))
                        }
                    }
                }
            }
        )
    }
}

/**
 * Dialog 1: Initial "Do you have the APK?" dialog.
 *
 * In expert mode the version list is selectable: the user can tap any version to set it as the
 * download target. [selectedDownloadVersion] reflects the current selection (defaults to
 * [recommendedVersion]); [onVersionSelect] propagates the change to the ViewModel.
 * In simple mode there is only one version and no selection UI is shown.
 */
@Composable
private fun ApkAvailabilityDialog(
    appName: String,
    recommendedVersion: AppTarget?,
    compatibleVersions: List<BundledAppTarget>,
    recommendedBundleVersions: Map<Int, AppTarget>,
    selectedDownloadVersion: AppTarget?,
    onVersionSelect: (AppTarget) -> Unit,
    usingMountInstall: Boolean,
    isExpertMode: Boolean,
    savedApkInfo: SavedApkInfo?,
    onDismiss: () -> Unit,
    onHaveApk: () -> Unit,
    onNeedApk: () -> Unit,
    onUseSaved: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_apk_availability_dialog_title),
        compactPadding = true,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main action buttons
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.home_apk_availability_yes),
                    onPrimaryClick = onNeedApk,
                    primaryIcon = Icons.Outlined.Download,
                    secondaryText = stringResource(R.string.home_apk_availability_no),
                    onSecondaryClick = onHaveApk,
                    secondaryIcon = Icons.Outlined.Check,
                    layout = DialogButtonLayout.Vertical
                )

                // Saved APK button (if available)
                if (savedApkInfo != null) {
                    MorpheDialogOutlinedButton(
                        text = stringResource(
                            R.string.home_apk_use_saved_with_version,
                            savedApkInfo.version
                        ),
                        onClick = onUseSaved,
                        icon = Icons.Outlined.History,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current
        val anyString = stringResource(R.string.any_version)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isExpertMode && compatibleVersions.isNotEmpty()) {
                // Expert mode: selectable version list
                Text(
                    text = htmlAnnotatedString(stringResource(
                        R.string.home_apk_availability_dialog_expert,
                        appName
                    )),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                if (compatibleVersions.size > 1) {
                    SelectableVersionListCard(
                        versions = compatibleVersions,
                        selectedVersion = selectedDownloadVersion,
                        recommendedBundleVersions = recommendedBundleVersions,
                        onVersionSelect = onVersionSelect,
                        anyString = anyString,
                        hasMultipleBundles = compatibleVersions.map { it.bundleUid }.distinct().size > 1
                    )
                } else {
                    VersionListCard(
                        versions = compatibleVersions.map { it.target.version ?: anyString },
                        experimentalVersions = compatibleVersions
                            .filter { it.target.isExperimental }
                            .mapNotNull { it.target.version }
                            .toSet(),
                        descriptions = compatibleVersions
                            .mapNotNull { b -> b.target.version?.let { v -> b.target.description?.let { d -> v to d } } }
                            .toMap()
                    )
                }
            } else {
                // Simple mode: single static version, no selection
                Text(
                    text = htmlAnnotatedString(stringResource(
                        R.string.home_apk_availability_dialog_simple,
                        appName
                    )),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                VersionListCard(
                    versions = listOf(recommendedVersion?.version ?: anyString),
                    showUnpatchedBadge = true
                )
            }

            // Root mode warning
            if (usingMountInstall) {
                InfoBadge(
                    text = stringResource(R.string.root_install_apk_required),
                    style = InfoBadgeStyle.Warning,
                    icon = Icons.Outlined.Warning,
                    isExpanded = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Dialog 2: Download instructions dialog.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun DownloadInstructionsDialog(
    usingMountInstall: Boolean,
    downloadColor: Color,
    isApkBundle: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_download_instructions_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.home_download_instructions_continue),
                onClick = onContinue,
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_download_instructions_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                InstructionStep(
                    number = "1",
                    text = stringResource(
                        R.string.home_download_instructions_step1,
                        stringResource(R.string.home_download_instructions_continue)
                    ),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstructionStep(
                        number = "2",
                        text = stringResource(R.string.home_download_instructions_step2_part1),
                        textColor = textColor,
                        secondaryColor = secondaryColor
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = {
                                context.toast(
                                    string = context.getString(
                                        R.string.home_download_instructions_download_button_toast
                                    ),
                                    duration = Toast.LENGTH_LONG
                                )
                            },
                            shape = RoundedCornerShape(1.dp),
                            color = downloadColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isApkBundle) "DOWNLOAD APK BUNDLE" else "DOWNLOAD APK",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                InstructionStep(
                    number = "3",
                    text = htmlAnnotatedString(
                        stringResource(
                            if (usingMountInstall) {
                                R.string.home_download_instructions_step3_mount
                            } else {
                                R.string.home_download_instructions_step3
                            }
                        )
                    ),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )

                InstructionStep(
                    number = "4",
                    text = stringResource(
                        if (usingMountInstall) R.string.home_download_instructions_step4_mount
                        else R.string.home_download_instructions_step4
                    ),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: AnnotatedString,
    textColor: Color,
    secondaryColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.6f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: String,
    textColor: Color,
    secondaryColor: Color
) {
    InstructionStep(
        number = number,
        text = AnnotatedString(text),
        textColor = textColor,
        secondaryColor = secondaryColor
    )
}

/**
 * Dialog 3: File picker prompt dialog.
 */
@Composable
private fun FilePickerPromptDialog(
    appName: String,
    isOtherApps: Boolean,
    onDismiss: () -> Unit,
    onOpenFilePicker: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (isOtherApps) {
                R.string.home_select_apk_title
            } else {
                R.string.home_file_picker_prompt_title
            }
        ),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.home_file_picker_prompt_open_apk),
                onClick = onOpenFilePicker,
                icon = Icons.Outlined.FolderOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = if (isOtherApps) {
                AnnotatedString(stringResource(R.string.home_select_any_apk_description))
            } else {
                htmlAnnotatedString(stringResource(R.string.home_file_picker_prompt_description, appName))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Unsupported version warning dialog.
 */
@Composable
private fun UnsupportedVersionWarningDialog(
    version: String,
    recommendedVersion: String?,
    allCompatibleVersions: List<String>,
    versionDescriptions: Map<String, String> = emptyMap(),
    experimentalVersions: Set<String> = emptySet(),
    isExperimental: Boolean = false,
    isExpertMode: Boolean,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_dialog_unsupported_version_dialog_title),
        compactPadding = true,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (isExperimental)
                        R.string.home_dialog_unsupported_version_experimental_description
                    else
                        R.string.home_dialog_unsupported_version_dialog_description
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selected version card
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.home_selected_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isExperimental)
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isExperimental)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.error
                            )

                            if (isExperimental) {
                                InfoBadge(
                                    text = stringResource(R.string.home_dialog_unsupported_version_experimental_label),
                                    style = InfoBadgeStyle.Warning,
                                    isCompact = true
                                )
                            } else {
                                InfoBadge(
                                    text = stringResource(R.string.home_dialog_unsupported_version_unsupported_label),
                                    style = InfoBadgeStyle.Error,
                                    isCompact = true
                                )
                            }
                        }
                    }
                }

                // Compatible versions section
                if (isExpertMode && allCompatibleVersions.isNotEmpty()) {
                    // Expert mode: show all compatible versions in unified card
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.home_dialog_unsupported_version_compatible_versions),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )

                        VersionListCard(
                            versions = allCompatibleVersions,
                            recommendedIndex = allCompatibleVersions
                                .indexOfFirst { it !in experimentalVersions }
                                .takeIf { it >= 0 } ?: 0,
                            isCompatible = true,
                            experimentalVersions = experimentalVersions,
                            descriptions = versionDescriptions
                        )
                    }
                } else if (recommendedVersion != null) {
                    // Simple mode or single version: show recommended version card
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.home_recommended_version),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )

                        VersionListCard(
                            versions = listOf(recommendedVersion),
                            recommendedIndex = 0,
                            isCompatible = true,
                            experimentalVersions = experimentalVersions
                        )
                    }
                }
            }
        }
    }
}

/**
 * Warning dialog shown when the selected APK's signing certificate does not match
 * the expected signatures declared in the patch bundle.
 */
@Composable
fun InvalidSignatureDialog(
    appName: String,
    onPickAnother: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_invalid_signature_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.home_split_apk_warning_pick_another),
                onPrimaryClick = onPickAnother,
                primaryIcon = Icons.Outlined.FolderOpen,
                secondaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onSecondaryClick = onProceed,
                isPrimaryDestructive = false,
                layout = DialogButtonLayout.Vertical,
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.GppBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = htmlAnnotatedString(
                    stringResource(R.string.home_invalid_signature_message, appName)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            InfoBadge(
                text = stringResource(R.string.home_invalid_signature_badge),
                style = InfoBadgeStyle.Error,
                icon = Icons.Outlined.Warning,
                isExpanded = true
            )
        }
    }
}

/**
 * Warning dialog shown when the user selects a split APK archive (.apks / .apkm / .xapk)
 * for an app that requires a full APK.
 */
@Composable
fun SplitApkWarningDialog(
    appName: String,
    onProceed: () -> Unit,
    onPickAnother: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_split_apk_warning_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                secondaryText = stringResource(R.string.home_split_apk_warning_pick_another),
                onSecondaryClick = onPickAnother,
                secondaryIcon = Icons.Outlined.FolderOpen,
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = htmlAnnotatedString(
                    stringResource(R.string.home_split_apk_warning_message, appName)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Warning dialog shown when the user selects an APK version that is marked experimental
 * in the patch bundle AND experimental-version mode is enabled for that bundle.
 */
@Composable
fun ExperimentalVersionWarningDialog(
    appName: String,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_experimental_app_version_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = htmlAnnotatedString(
                    stringResource(R.string.morphe_experimental_app_version_dialog_message, appName)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Wrong package dialog.
 */
@Composable
fun WrongPackageDialog(
    expectedPackage: String,
    actualPackage: String,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_dialog_wrong_package_title),
        compactPadding = true,
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_dialog_wrong_package_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Expected package (green card)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.home_dialog_expected_package),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = expectedPackage,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green.copy(alpha = 0.9f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Selected package (red card)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.home_dialog_selected_package),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = actualPackage,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Version list card where each row is tappable.
 * The selected version gets a checkmark; the recommended version is labelled when not selected.
 * Experimental versions are always labelled regardless of selection state.
 */
@Composable
private fun SelectableVersionListCard(
    versions: List<BundledAppTarget>,
    selectedVersion: AppTarget?,
    recommendedBundleVersions: Map<Int, AppTarget>,
    onVersionSelect: (AppTarget) -> Unit,
    anyString: String,
    hasMultipleBundles: Boolean,
    modifier: Modifier = Modifier
) {
    if (versions.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            var lastBundleUid = -1

            versions.forEachIndexed { index, bundled ->
                val target = bundled.target
                val versionString = target.version ?: anyString
                val isSelected = target.version != null && target.version == selectedVersion?.version
                val isRecommended = target.version != null &&
                        target.version == recommendedBundleVersions[bundled.bundleUid]?.version
                val recommendedLabel = stringResource(R.string.home_apk_availability_recommended_label)
                val experimentalLabel = stringResource(R.string.home_dialog_unsupported_version_experimental_label)
                val selectedLabel = stringResource(R.string.home_selected_version)

                // Bundle section header - only when multiple bundles are present and uid changes
                if (hasMultipleBundles && bundled.bundleUid != lastBundleUid) {
                    if (lastBundleUid != -1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                        Text(
                            text = bundled.bundleName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    lastBundleUid = bundled.bundleUid
                }

                val badge: @Composable (() -> Unit)? = when {
                    target.isExperimental -> ({
                        InfoBadge(
                            text = experimentalLabel,
                            style = InfoBadgeStyle.Warning,
                            isCompact = true
                        )
                    })
                    isRecommended -> ({
                        InfoBadge(
                            text = recommendedLabel,
                            style = InfoBadgeStyle.Default,
                            isCompact = true
                        )
                    })
                    else -> null
                }

                val rowContentDesc = buildString {
                    append(versionString)
                    when {
                        target.isExperimental -> append(", $experimentalLabel")
                        isRecommended -> append(", $recommendedLabel")
                    }
                    if (isSelected) append(", $selectedLabel")
                    target.description?.let { append(", $it") }
                    if (hasMultipleBundles) append(", ${bundled.bundleName}")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            onClick = { onVersionSelect(target) },
                            role = Role.RadioButton
                        )
                        .semantics { contentDescription = rowContentDesc }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkmark column - fixed width so text aligns across all rows
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = versionString,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    target.isExperimental -> MaterialTheme.colorScheme.tertiary
                                    else -> LocalDialogTextColor.current
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            badge?.invoke()
                        }
                        val description = target.description
                        if (description != null) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                        }
                    }
                }

                // Row divider - skip after last row in a bundle group (section divider handles it)
                val isLastInBundle = index == versions.lastIndex ||
                        (hasMultipleBundles && versions[index + 1].bundleUid != bundled.bundleUid)
                if (index < versions.lastIndex && !isLastInBundle) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}



@Composable
private fun VersionListCard(
    versions: List<String>,
    recommendedIndex: Int = 0,
    isCompatible: Boolean = false,
    showUnpatchedBadge: Boolean = false,
    experimentalVersions: Set<String> = emptySet(),
    descriptions: Map<String, String> = emptyMap(),
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    if (versions.isEmpty()) return

    val containerColor = if (isCompatible) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }

    val textColor = if (isCompatible) {
        Color.Green.copy(alpha = 0.9f)
    } else {
        LocalDialogTextColor.current
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            versions.forEachIndexed { index, version ->
                val isExperimentalVersion = version in experimentalVersions
                val versionDescription = descriptions[version]

                // Resolve badge once - drives both the badge composable and version text color
                val badge: @Composable (() -> Unit)? = when {
                    isExperimentalVersion -> ({
                        InfoBadge(
                            text = stringResource(R.string.home_dialog_unsupported_version_experimental_label),
                            style = InfoBadgeStyle.Warning,
                            isCompact = true
                        )
                    })
                    index == recommendedIndex && !showUnpatchedBadge -> ({
                        InfoBadge(
                            text = stringResource(R.string.home_apk_availability_recommended_label),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )
                    })
                    showUnpatchedBadge && versions.size == 1 -> ({
                        InfoBadge(
                            text = stringResource(R.string.home_apk_availability_unpatched_label),
                            style = InfoBadgeStyle.Warning,
                            isCompact = true
                        )
                    })
                    else -> null
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Version + optional badge inline
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = version,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (index == recommendedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (isExperimentalVersion)
                                MaterialTheme.colorScheme.tertiary
                            else
                                textColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        badge?.invoke()
                    }

                    // Optional per-version description
                    if (versionDescription != null) {
                        Text(
                            text = versionDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                }

                // Divider between versions
                if (index < versions.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * Warning dialog shown before patching starts when the device has less than [thresholdGb] GB of free storage.
 */
@Composable
fun LowDiskSpaceDialog(
    freeGb: Float,
    thresholdGb: Float,
    onDismiss: () -> Unit,
    onPatchAnyway: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_low_disk_space_dialog_title),
        footer = {
            MorpheDialogButtonRow(
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = stringResource(R.string.home_low_disk_space_dialog_message, freeGb, thresholdGb),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            InfoBadge(
                text = stringResource(R.string.home_low_disk_space_dialog_warning),
                style = InfoBadgeStyle.Warning,
                icon = Icons.Outlined.Warning,
                isExpanded = true
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
    onDismiss: () -> Unit,
    onRefreshAndPatch: () -> Unit,
    onPatchAnyway: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_outdated_patches_dialog_title),
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MorpheDialogButton(
                    text = stringResource(R.string.home_outdated_patches_dialog_update_and_patch),
                    onClick = onRefreshAndPatch,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.SystemUpdateAlt
                )
                MorpheDialogButtonRow(
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.SignalCellularAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = stringResource(R.string.home_outdated_patches_dialog_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            InfoBadge(
                text = stringResource(R.string.home_outdated_patches_dialog_warning),
                style = InfoBadgeStyle.Warning,
                icon = Icons.Outlined.Warning,
                isExpanded = true
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.deep_link_add_source_title),
        compactPadding = true,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = onConfirm,
                primaryIcon = Icons.Outlined.Extension,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val owner = remember(url) {
                runCatching { URI(url).path.trim('/').split('/').firstOrNull() }.getOrNull()
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                if (owner != null) {
                    RemoteAvatar(
                        url = "https://github.com/$owner.png",
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (name != null) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalDialogTextColor.current
                        )
                    }
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            }

            InfoBadge(
                text = stringResource(R.string.deep_link_add_source_warning),
                style = InfoBadgeStyle.Warning,
                icon = Icons.Outlined.Warning,
                isExpanded = true
            )
        }
    }
}
