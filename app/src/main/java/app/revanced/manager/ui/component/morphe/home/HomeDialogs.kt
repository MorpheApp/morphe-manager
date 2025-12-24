package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
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
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.htmlAnnotatedString
import app.revanced.manager.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Container for all MorpheHomeScreen dialogs
 */
@Composable
fun HomeDialogs(
    state: HomeStates
) {
    val uriHandler = LocalUriHandler.current

    // Dialog 1: APK Availability - "Do you have the APK?"
    if (state.showApkAvailabilityDialog && state.pendingPackageName != null && state.pendingAppName != null) {
        ApkAvailabilityDialog(
            appName = state.pendingAppName!!,
            recommendedVersion = state.pendingRecommendedVersion,
            usingMountInstall = state.usingMountInstall,
            onDismiss = {
                state.showApkAvailabilityDialog = false
                state.cleanupPendingData()
            },
            onHaveApk = {
                // User has APK - open file picker
                state.showApkAvailabilityDialog = false
                state.storagePickerLauncher.launch(APK_MIMETYPE)
            },
            onNeedApk = {
                // User needs APK - show download instructions
                state.showApkAvailabilityDialog = false
                state.showDownloadInstructionsDialog = true
                state.resolveDownloadRedirect()
            }
        )
    }

    // Dialog 2: Download Instructions
    if (state.showDownloadInstructionsDialog && state.pendingPackageName != null && state.pendingAppName != null) {
        DownloadInstructionsDialog(
            appName = state.pendingAppName!!,
            recommendedVersion = state.pendingRecommendedVersion,
            usingMountInstall = state.usingMountInstall,
            onDismiss = {
                state.showDownloadInstructionsDialog = false
                state.cleanupPendingData()
            }
        ) {
            state.handleDownloadInstructionsContinue(uriHandler)
        }
    }

    // Dialog 3: File Picker Prompt
    if (state.showFilePickerPromptDialog && state.pendingPackageName != null && state.pendingAppName != null) {
        FilePickerPromptDialog(
            appName = state.pendingAppName!!,
            onDismiss = {
                state.showFilePickerPromptDialog = false
                state.cleanupPendingData()
            },
            onOpenFilePicker = {
                state.showFilePickerPromptDialog = false
                state.storagePickerLauncher.launch(APK_MIMETYPE)
            }
        )
    }

    // Unsupported Version Dialog
    state.showUnsupportedVersionDialog?.let { dialogState ->
        UnsupportedVersionWarningDialog(
            version = dialogState.version,
            recommendedVersion = dialogState.recommendedVersion,
            onDismiss = {
                state.showUnsupportedVersionDialog = null
                // Clean up the pending app
                state.pendingSelectedApp?.let { app ->
                    if (app is SelectedApp.Local && app.temporary) {
                        app.file.delete()
                    }
                }
                state.pendingSelectedApp = null
            },
            onProceed = {
                state.showUnsupportedVersionDialog = null
                // Start patching with the already loaded app
                state.pendingSelectedApp?.let { app ->
                    CoroutineScope(Dispatchers.Main).launch {
                        state.startPatchingWithApp(app, true)
                        state.pendingSelectedApp = null
                    }
                }
            }
        )
    }

    // Wrong Package Dialog
    state.showWrongPackageDialog?.let { dialogState ->
        WrongPackageDialog(
            expectedPackage = dialogState.expectedPackage,
            actualPackage = dialogState.actualPackage,
            onDismiss = { state.showWrongPackageDialog = null }
        )
    }

    // Patches bottom sheet
    if (state.showPatchesSheet && state.apiBundle != null) {
        HomeBundlePatchesSheet(
            onDismissRequest = { state.showPatchesSheet = false },
            src = state.apiBundle!!
        )
    }

    // Changelog bottom sheet
    if (state.showChangelogSheet && state.apiBundle != null) {
        val remoteBundle = state.apiBundle as? RemotePatchBundle
        if (remoteBundle != null) {
            HomeBundleChangelogSheet(
                src = remoteBundle,
                onDismissRequest = { state.showChangelogSheet = false }
            )
        }
    }
}

/**
 * Dialog 1: Initial "Do you have the APK?" dialog
 * First step in APK selection process
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
 * Provides step-by-step guide for downloading APK from APKMirror
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun DownloadInstructionsDialog(
    appName: String,
    recommendedVersion: String?,
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
            // Steps
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

                // Step 1
                InstructionStep(
                    number = "1",
                    text = stringResource(R.string.morphe_home_download_instructions_step1),
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )

                // Step 2 with button preview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstructionStep(
                        number = "2",
                        text = stringResource(R.string.morphe_home_download_instructions_step2_part1),
                        textColor = textColor,
                        secondaryColor = secondaryColor
                    )

                    // APKMirror button preview
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = {
                                context.toast(context.getString(R.string.morphe_home_download_instructions_download_button_toast))
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
                                    text = "DOWNLOAD APK", // APKMirror does not have localization
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Step 3
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

                // Step 4
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

/**
 * Instruction step row
 * Displays numbered step with text
 */
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
 * Shown after browser opens, prompts user to select downloaded APK
 */
@Composable
private fun FilePickerPromptDialog(
    appName: String,
    onDismiss: () -> Unit,
    onOpenFilePicker: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_home_file_picker_prompt_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.morphe_home_file_picker_prompt_open),
                onClick = onOpenFilePicker,
                icon = Icons.Outlined.FolderOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(R.string.morphe_home_file_picker_prompt_description, appName),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Unsupported version warning dialog
 * Shown when selected APK version has no compatible patches
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

            // Version info
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selected Version
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

                // Recommended Version
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
 * Shown when selected APK doesn't match expected package name
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
