package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.shared.DialogButtonLayout
import app.revanced.manager.ui.component.morphe.shared.LocalDialogSecondaryTextColor
import app.revanced.manager.ui.component.morphe.shared.LocalDialogTextColor
import app.revanced.manager.ui.component.morphe.shared.MorpheDialog
import app.revanced.manager.ui.component.morphe.shared.MorpheDialogButton
import app.revanced.manager.ui.component.morphe.shared.MorpheDialogButtonRow
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.HomeViewModel
import app.revanced.manager.util.htmlAnnotatedString
import app.revanced.manager.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Container for all MorpheHomeScreen dialogs
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeDialogs(
    viewModel: HomeViewModel,
    dashboardViewModel: DashboardViewModel,
    storagePickerLauncher: () -> Unit,
    openBundlePicker: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog 1: APK Availability
    AnimatedVisibility(
        visible = viewModel.showApkAvailabilityDialog && viewModel.pendingPackageName != null && viewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (viewModel.showDownloadInstructionsDialog) 0 else 200))
    ) {
        val appName = viewModel.pendingAppName ?: return@AnimatedVisibility
        val recommendedVersion = viewModel.pendingRecommendedVersion
        val usingMountInstall = viewModel.usingMountInstall

        ApkAvailabilityDialog(
            appName = appName,
            recommendedVersion = recommendedVersion,
            usingMountInstall = usingMountInstall,
            onDismiss = {
                viewModel.showApkAvailabilityDialog = false
                viewModel.cleanupPendingData()
            },
            onHaveApk = {
                viewModel.showApkAvailabilityDialog = false
                storagePickerLauncher()
            },
            onNeedApk = {
                viewModel.showApkAvailabilityDialog = false
                scope.launch {
                    delay(50)
                    viewModel.showDownloadInstructionsDialog = true
                    viewModel.resolveDownloadRedirect()
                }
            }
        )
    }

    // Dialog 2: Download Instructions
    AnimatedVisibility(
        visible = viewModel.showDownloadInstructionsDialog && viewModel.pendingPackageName != null && viewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (viewModel.showFilePickerPromptDialog) 0 else 200))
    ) {
        val usingMountInstall = viewModel.usingMountInstall

        DownloadInstructionsDialog(
            usingMountInstall = usingMountInstall,
            onDismiss = {
                viewModel.showDownloadInstructionsDialog = false
                viewModel.cleanupPendingData()
            }
        ) {
            viewModel.handleDownloadInstructionsContinue { url ->
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
        visible = viewModel.showFilePickerPromptDialog && viewModel.pendingAppName != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val appName = viewModel.pendingAppName ?: return@AnimatedVisibility
        val isOtherApps = viewModel.pendingPackageName == null

        FilePickerPromptDialog(
            appName = appName,
            isOtherApps = isOtherApps,
            onDismiss = {
                viewModel.showFilePickerPromptDialog = false
                viewModel.cleanupPendingData()
            },
            onOpenFilePicker = {
                viewModel.showFilePickerPromptDialog = false
                storagePickerLauncher()
            }
        )
    }

    // Unsupported Version Dialog
    AnimatedVisibility(
        visible = viewModel.showUnsupportedVersionDialog != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val dialogState = viewModel.showUnsupportedVersionDialog ?: return@AnimatedVisibility

        UnsupportedVersionWarningDialog(
            version = dialogState.version,
            recommendedVersion = dialogState.recommendedVersion,
            onDismiss = {
                viewModel.showUnsupportedVersionDialog = null
                viewModel.pendingSelectedApp?.let { app ->
                    if (app is SelectedApp.Local && app.temporary) {
                        app.file.delete()
                    }
                }
                viewModel.pendingSelectedApp = null
            },
            onProceed = {
                viewModel.showUnsupportedVersionDialog = null
                viewModel.pendingSelectedApp?.let { app ->
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.startPatchingWithApp(app, true)
                        viewModel.pendingSelectedApp = null
                    }
                }
            }
        )
    }

    // Wrong Package Dialog
    AnimatedVisibility(
        visible = viewModel.showWrongPackageDialog != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val dialogState = viewModel.showWrongPackageDialog ?: return@AnimatedVisibility

        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { viewModel.showWrongPackageDialog = null }
        )
    }

    // Expert Mode Dialog
    AnimatedVisibility(
        visible = viewModel.showExpertModeDialog && viewModel.expertModeSelectedApp != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val selectedApp = viewModel.expertModeSelectedApp ?: return@AnimatedVisibility
        val allowIncompatible = dashboardViewModel.prefs.disablePatchVersionCompatCheck.getBlocking()

        ExpertModeDialog(
            bundles = viewModel.expertModeBundles,
            selectedPatches = viewModel.expertModePatches,
            options = viewModel.expertModeOptions,
            onPatchToggle = { bundleUid, patchName ->
                viewModel.togglePatchInExpertMode(bundleUid, patchName)
            },
            onOptionChange = { bundleUid, patchName, optionKey, value ->
                viewModel.updateOptionInExpertMode(bundleUid, patchName, optionKey, value)
            },
            onResetOptions = { bundleUid, patchName ->
                viewModel.resetOptionsInExpertMode(bundleUid, patchName)
            },
            onDismiss = {
                viewModel.cleanupExpertModeData()
            },
            onProceed = {
                val finalPatches = viewModel.expertModePatches
                val finalOptions = viewModel.expertModeOptions

                viewModel.showExpertModeDialog = false

                scope.launch(Dispatchers.IO) {
                    viewModel.saveOptions(selectedApp.packageName, finalOptions)

                    withContext(Dispatchers.Main) {
                        viewModel.proceedWithPatching(selectedApp, finalPatches, finalOptions)
                        viewModel.cleanupExpertModeData()
                    }
                }
            },
            allowIncompatible = allowIncompatible
        )
    }

    // Bundle management sheet
    if (viewModel.showBundleManagementSheet) {
        HomeBundleManagementSheet(
            onDismissRequest = { viewModel.showBundleManagementSheet = false },
            onAddBundle = {
                viewModel.showBundleManagementSheet = false
                viewModel.showAddBundleDialog = true
            },
            onDelete = { bundle ->
                scope.launch {
                    dashboardViewModel.patchBundleRepository.remove(bundle)
                }
            },
            onDisable = { bundle ->
                scope.launch {
                    dashboardViewModel.patchBundleRepository.disable(bundle)
                }
            },
            onUpdate = { bundle ->
                if (bundle is RemotePatchBundle) {
                    scope.launch {
                        dashboardViewModel.patchBundleRepository.update(bundle, showToast = true)
                    }
                }
            },
            onRename = { bundle ->
                viewModel.bundleToRename = bundle
                viewModel.showRenameBundleDialog = true
            }
        )
    }

    // Add bundle dialog
    if (viewModel.showAddBundleDialog) {
        MorpheAddBundleDialog(
            onDismiss = {
                viewModel.showAddBundleDialog = false
                viewModel.selectedBundleUri = null
                viewModel.selectedBundlePath = null
            },
            onLocalSubmit = {
                viewModel.showAddBundleDialog = false
                viewModel.selectedBundleUri?.let { uri ->
                    dashboardViewModel.createLocalSource(uri)
                }
                viewModel.selectedBundleUri = null
                viewModel.selectedBundlePath = null
            },
            onRemoteSubmit = { url ->
                viewModel.showAddBundleDialog = false
                dashboardViewModel.createRemoteSource(url, true)
            },
            onLocalPick = {
                openBundlePicker()
            },
            selectedLocalPath = viewModel.selectedBundlePath
        )
    }

    // Rename bundle dialog
    if (viewModel.showRenameBundleDialog && viewModel.bundleToRename != null) {
        val bundle = viewModel.bundleToRename!!

        MorpheRenameBundleDialog(
            initialValue = bundle.displayName.orEmpty(),
            onDismissRequest = {
                viewModel.showRenameBundleDialog = false
                viewModel.bundleToRename = null
            },
            onConfirm = { value ->
                scope.launch {
                    val result = dashboardViewModel.patchBundleRepository.setDisplayName(
                        bundle.uid,
                        value.trim().ifEmpty { null }
                    )
                    when (result) {
                        PatchBundleRepository.DisplayNameUpdateResult.SUCCESS,
                        PatchBundleRepository.DisplayNameUpdateResult.NO_CHANGE -> {
                            viewModel.showRenameBundleDialog = false
                            viewModel.bundleToRename = null
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.DUPLICATE -> {
                            context.toast(context.getString(R.string.patch_bundle_duplicate_name_error))
                        }
                        PatchBundleRepository.DisplayNameUpdateResult.NOT_FOUND -> {
                            context.toast(context.getString(R.string.patch_bundle_missing_error))
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
    usingMountInstall: Boolean,
    onDismiss: () -> Unit,
    onHaveApk: () -> Unit,
    onNeedApk: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_home_apk_availability_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.morphe_home_apk_availability_no),
                onPrimaryClick = onNeedApk,
                primaryIcon = Icons.Outlined.Download,
                secondaryText = stringResource(R.string.morphe_home_apk_availability_yes),
                onSecondaryClick = onHaveApk,
                secondaryIcon = Icons.Outlined.Check,
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    R.string.morphe_home_apk_availability_dialog_description_simple,
                    appName,
                    recommendedVersion ?: stringResource(R.string.any_version)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            // Root mode warning
            if (usingMountInstall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_root_install_apk_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                }
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
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_home_download_instructions_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.morphe_home_download_instructions_continue),
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
                    text = stringResource(R.string.morphe_home_download_instructions_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                InstructionStep(
                    number = "1",
                    text = stringResource(
                        R.string.morphe_home_download_instructions_step1,
                        stringResource(R.string.morphe_home_download_instructions_continue)
                    ),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstructionStep(
                        number = "2",
                        text = stringResource(R.string.morphe_home_download_instructions_step2_part1),
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
                                        R.string.morphe_home_download_instructions_download_button_toast
                                    ),
                                    duration = Toast.LENGTH_LONG
                                )
                            },
                            shape = RoundedCornerShape(1.dp),
                            color = Color(0xFFFF0034)
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
                                R.string.morphe_home_download_instructions_step3_mount
                            } else {
                                R.string.morphe_home_download_instructions_step3
                            }
                        )
                    ),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )

                InstructionStep(
                    number = "4",
                    text = stringResource(
                        if (usingMountInstall) R.string.morphe_home_download_instructions_step4_mount
                        else R.string.morphe_home_download_instructions_step4
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
                R.string.morphe_home_select_apk_title
            } else {
                R.string.morphe_home_file_picker_prompt_title
            }
        ),
        footer = {
            MorpheDialogButton(
                text = stringResource(
                    if (isOtherApps) {
                        R.string.morphe_home_file_picker_prompt_open_apk
                    } else {
                        R.string.morphe_home_file_picker_prompt_open_downloaded_apk
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
                stringResource(R.string.morphe_home_select_any_apk_description)
            } else {
                stringResource(R.string.morphe_home_file_picker_prompt_description, appName)
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
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patcher_unsupported_version_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.morphe_patcher_unsupported_version_dialog_proceed),
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
                text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_selected_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )
                    Text(
                        text = version,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Red.copy(alpha = 0.9f)
                    )
                }

                if (recommendedVersion != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.morphe_home_recommended_version),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )
                        Text(
                            text = recommendedVersion,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Green.copy(alpha = 0.9f)
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
        title = stringResource(R.string.morphe_patcher_wrong_package_title),
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
                text = stringResource(R.string.morphe_patcher_wrong_package_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_expected_package),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )
                    Text(
                        text = expectedPackage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Green.copy(alpha = 0.9f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_selected_package),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryColor
                    )
                    Text(
                        text = actualPackage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Red.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
