package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.SavedApkInfo
import app.morphe.manager.util.AppPackages
import app.morphe.manager.util.htmlAnnotatedString
import app.morphe.manager.util.toast
import kotlinx.coroutines.*

/**
 * Container for all MorpheHomeScreen dialogs
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

    // Dialog 1: APK Availability
    AnimatedVisibility(
        visible = homeViewModel.showApkAvailabilityDialog && homeViewModel.pendingPackageName != null && homeViewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (homeViewModel.showDownloadInstructionsDialog) 0 else 200))
    ) {
        val appName = homeViewModel.pendingAppName ?: return@AnimatedVisibility
        val recommendedVersion = homeViewModel.pendingRecommendedVersion
        val compatibleVersions = homeViewModel.pendingCompatibleVersions
        val usingMountInstall = homeViewModel.usingMountInstall
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()
        val savedApkInfo = homeViewModel.pendingSavedApkInfo

        ApkAvailabilityDialog(
            appName = appName,
            recommendedVersion = recommendedVersion,
            compatibleVersions = compatibleVersions,
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

    // Dialog 2: Download Instructions
    AnimatedVisibility(
        visible = homeViewModel.showDownloadInstructionsDialog && homeViewModel.pendingPackageName != null && homeViewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (homeViewModel.showFilePickerPromptDialog) 0 else 200))
    ) {
        val usingMountInstall = homeViewModel.usingMountInstall
        // Remember packageName to prevent color flickering during exit animation
        val packageName = remember { homeViewModel.pendingPackageName }

        DownloadInstructionsDialog(
            usingMountInstall = usingMountInstall,
            packageName = packageName,
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

    // Dialog 3: File Picker Prompt
    AnimatedVisibility(
        visible = homeViewModel.showFilePickerPromptDialog && homeViewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
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

    // Unsupported Version Dialog
    AnimatedVisibility(
        visible = homeViewModel.showUnsupportedVersionDialog != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val dialogState = homeViewModel.showUnsupportedVersionDialog ?: return@AnimatedVisibility
        val isExpertMode = homeViewModel.prefs.useExpertMode.getBlocking()

        UnsupportedVersionWarningDialog(
            version = dialogState.version,
            recommendedVersion = dialogState.recommendedVersion,
            allCompatibleVersions = dialogState.allCompatibleVersions,
            isExpertMode = isExpertMode,
            onDismiss = {
                homeViewModel.showUnsupportedVersionDialog = null
                homeViewModel.pendingSelectedApp?.let { app ->
                    if (app is SelectedApp.Local && app.temporary) {
                        app.file.delete()
                    }
                }
                homeViewModel.pendingSelectedApp = null
            },
            onProceed = {
                homeViewModel.showUnsupportedVersionDialog = null
                homeViewModel.pendingSelectedApp?.let { app ->
                    CoroutineScope(Dispatchers.Main).launch {
                        homeViewModel.startPatchingWithApp(app, true)
                        homeViewModel.pendingSelectedApp = null
                    }
                }
            }
        )
    }

    // Wrong Package Dialog
    AnimatedVisibility(
        visible = homeViewModel.showWrongPackageDialog != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val dialogState = homeViewModel.showWrongPackageDialog ?: return@AnimatedVisibility

        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { homeViewModel.showWrongPackageDialog = null }
        )
    }

    // Expert Mode Dialog
    AnimatedVisibility(
        visible = homeViewModel.showExpertModeDialog && homeViewModel.expertModeSelectedApp != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val selectedApp = homeViewModel.expertModeSelectedApp ?: return@AnimatedVisibility
        val allowIncompatible = homeViewModel.prefs.disablePatchVersionCompatCheck.getBlocking()

        ExpertModeDialog(
            bundles = homeViewModel.expertModeBundles,
            selectedPatches = homeViewModel.expertModePatches,
            options = homeViewModel.expertModeOptions,
            onPatchToggle = { bundleUid, patchName ->
                homeViewModel.togglePatchInExpertMode(bundleUid, patchName)
            },
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
                val finalPatches = homeViewModel.expertModePatches
                val finalOptions = homeViewModel.expertModeOptions

                homeViewModel.showExpertModeDialog = false

                scope.launch(Dispatchers.IO) {
                    homeViewModel.saveOptions(selectedApp.packageName, finalOptions)

                    withContext(Dispatchers.Main) {
                        homeViewModel.proceedWithPatching(selectedApp, finalPatches, finalOptions)
                        homeViewModel.cleanupExpertModeData()
                    }
                }
            },
            allowIncompatible = allowIncompatible
        )
    }

    // Bundle management sheet
    if (homeViewModel.showBundleManagementSheet) {
        BundleManagementSheet(
            onDismissRequest = { homeViewModel.showBundleManagementSheet = false },
            onAddBundle = {
                homeViewModel.showBundleManagementSheet = false
                homeViewModel.showAddBundleDialog = true
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
            }
        )
    }

    // Add bundle dialog
    if (homeViewModel.showAddBundleDialog) {
        AddBundleDialog(
            onDismiss = {
                homeViewModel.showAddBundleDialog = false
                homeViewModel.selectedBundleUri = null
                homeViewModel.selectedBundlePath = null
            },
            onLocalSubmit = {
                homeViewModel.showAddBundleDialog = false
                homeViewModel.selectedBundleUri?.let { uri ->
                    homeViewModel.createLocalSource(uri)
                }
                homeViewModel.selectedBundleUri = null
                homeViewModel.selectedBundlePath = null
            },
            onRemoteSubmit = { url ->
                homeViewModel.showAddBundleDialog = false
                homeViewModel.createRemoteSource(url, true)
            },
            onLocalPick = {
                openBundlePicker()
            },
            selectedLocalPath = homeViewModel.selectedBundlePath
        )
    }

    // Rename bundle dialog
    if (homeViewModel.showRenameBundleDialog && homeViewModel.bundleToRename != null) {
        val bundle = homeViewModel.bundleToRename!!

        RenameBundleDialog(
            initialValue = bundle.displayName.orEmpty(),
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
 * Dialog 1: Initial "Do you have the APK?" dialog
 */
@Composable
private fun ApkAvailabilityDialog(
    appName: String,
    recommendedVersion: String?,
    compatibleVersions: List<String>,
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
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main action buttons
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.home_apk_availability_no),
                    onPrimaryClick = onNeedApk,
                    primaryIcon = Icons.Outlined.Download,
                    secondaryText = stringResource(R.string.home_apk_availability_yes),
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Description text
            if (isExpertMode && compatibleVersions.isNotEmpty()) {
                // Expert mode with versions: show list of versions
                Text(
                    text = stringResource(
                        R.string.home_apk_availability_dialog_expert,
                        appName
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                // Unified version list card
                VersionListCard(
                    versions = compatibleVersions,
                    recommendedIndex = 0
                )
            } else {
                // Simple mode or single version: show card with unpatched badge
                Text(
                    text = stringResource(
                        R.string.home_apk_availability_dialog_simple,
                        appName
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                val versionToShow = recommendedVersion ?: stringResource(R.string.any_version)
                VersionListCard(
                    versions = listOf(versionToShow),
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
 * Dialog 2: Download instructions dialog
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun DownloadInstructionsDialog(
    usingMountInstall: Boolean,
    packageName: String?,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    // Get button color based on package name
    val buttonColor = packageName?.let { AppPackages.getDownloadColor(it) }
        ?: AppPackages.YOUTUBE_DOWNLOAD_COLOR

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
                            color = buttonColor
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
                                    text = "DOWNLOAD APK",
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
 * Dialog 3: File picker prompt dialog
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
                text = stringResource(
                    if (isOtherApps) {
                        R.string.home_file_picker_prompt_open_apk
                    } else {
                        R.string.home_file_picker_prompt_open_downloaded_apk
                    }
                ),
                onClick = onOpenFilePicker,
                icon = Icons.Outlined.FolderOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = if (isOtherApps) {
                stringResource(R.string.home_select_any_apk_description)
            } else {
                stringResource(R.string.home_file_picker_prompt_description, appName)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Unsupported version warning dialog
 */
@Composable
private fun UnsupportedVersionWarningDialog(
    version: String,
    recommendedVersion: String?,
    allCompatibleVersions: List<String>,
    isExpertMode: Boolean,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_dialog_unsupported_version_dialog_title),
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
                text = stringResource(R.string.home_dialog_unsupported_version_dialog_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selected version (red card)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.home_selected_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
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
                                color = MaterialTheme.colorScheme.error
                            )

                            InfoBadge(
                                text = stringResource(R.string.home_dialog_unsupported_version_unsupported_label),
                                style = InfoBadgeStyle.Error,
                                isCompact = true
                            )
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
                            recommendedIndex = 0,
                            isCompatible = true
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
                            isCompatible = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wrong package dialog
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
 * Unified version list card component
 */
@Composable
private fun VersionListCard(
    versions: List<String>,
    recommendedIndex: Int = 0,
    isCompatible: Boolean = false,
    showUnpatchedBadge: Boolean = false,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Version number
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == recommendedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )

                    // Badge
                    if (index == recommendedIndex && !showUnpatchedBadge) {
                        InfoBadge(
                            text = stringResource(R.string.home_apk_availability_recommended_label),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )
                    } else if (showUnpatchedBadge && versions.size == 1) {
                        InfoBadge(
                            text = stringResource(R.string.home_apk_availability_unpatched_label),
                            style = InfoBadgeStyle.Warning,
                            isCompact = true
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
