package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Apply appropriate width constraints based on orientation
 * Portrait: fill width with horizontal padding
 * Landscape: limited width for better readability
 */
@Composable
private fun Modifier.dialogWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) {
        this
            .widthIn(max = 600.dp)
            .padding(horizontal = 24.dp)
    } else {
        this
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    }
}

/**
 * Apply appropriate height constraints based on orientation
 * Portrait: expand to content size, max 90% of screen height
 * Landscape: limit height for better readability
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun Modifier.dialogHeight(): Modifier {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) {
        this.heightIn(max = 600.dp)
    } else {
        this.wrapContentHeight().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f)
    }
}

/**
 * Standardized dialog wrapper for Morphe UI
 * Provides consistent styling and responsive width across all dialogs
 * Supports scrollable content with fixed header and footer
 *
 * @param onDismissRequest Called when user dismisses the dialog
 * @param title Optional title - stays fixed at top
 * @param header Optional header content (icon + title) - stays fixed at top
 * @param footer Optional footer content (buttons) - stays fixed at bottom
 * @param content Scrollable dialog content
 */
@Composable
fun MorpheDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .dialogWidth()
                .dialogHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Fixed header
                Box(modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) {
                    when {
                        header != null -> {
                            Box(modifier = Modifier.padding(top = 24.dp)) {
                                header()
                            }
                        }
                        title != null -> {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Scrollable content
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(
                            top = if ((header == null) && (title == null)) 24.dp else 16.dp,
                            bottom = if (footer == null) 24.dp else 16.dp
                        )
                ) {
                    content()
                }

                // Fixed footer
                footer?.let {
                    Box(modifier = Modifier.padding(bottom = 24.dp, start = 24.dp, end = 24.dp)) {
                        footer()
                    }
                }
            }
        }
    }
}

/**
 * Container for all MorpheHomeScreen dialogs
 * Manages display of all dialog states
 */
@Composable
fun MorpheHomeDialogs(
    state: MorpheHomeState,
    usingMountInstall: Boolean
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
            }
        )
    }

    // Dialog 2: Download Instructions
    if (state.showDownloadInstructionsDialog && state.pendingPackageName != null && state.pendingAppName != null) {
        DownloadInstructionsDialog(
            appName = state.pendingAppName!!,
            recommendedVersion = state.pendingRecommendedVersion,
            usingMountInstall = usingMountInstall,
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
            packageName = dialogState.packageName,
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
        MorpheBundlePatchesSheet(
            onDismissRequest = { state.showPatchesSheet = false },
            src = state.apiBundle!!
        )
    }

    // Changelog bottom sheet
    if (state.showChangelogSheet && state.apiBundle != null) {
        val remoteBundle = state.apiBundle as? RemotePatchBundle
        if (remoteBundle != null) {
            MorpheBundleChangelogSheet(
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
            // Fixed footer - buttons
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                // Single row for landscape
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onHaveApk,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_yes))
                    }

                    Button(
                        onClick = onNeedApk,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_no))
                    }
                }
            } else {
                // Column for portrait
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onHaveApk,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_yes))
                    }

                    Button(
                        onClick = onNeedApk,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_no))
                    }
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.morphe_home_apk_availability_dialog_description_simple,
                    appName,
                    recommendedVersion ?: stringResource(R.string.any_version)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Root mode warning
            if (usingMountInstall) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_root_install_apk_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
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
            // Fixed footer - button
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.morphe_home_download_instructions_continue))
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.morphe_home_download_instructions_description,
                    appName,
                    recommendedVersion ?: stringResource(R.string.any_version)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Step-by-step instructions card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morphe_home_download_instructions_steps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Step 1
                    InstructionStep(
                        number = "1",
                        text = stringResource(R.string.morphe_home_download_instructions_step1)
                    )

                    // Step 2 - with styled "DOWNLOAD APK" button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "2",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.morphe_home_download_instructions_step2_part1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            // Styled "DOWNLOAD APK" button - clickable with toast
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    onClick = {
                                        context.toast(context.getString(R.string.morphe_home_download_instructions_download_button_toast))
                                    },
                                    shape = RoundedCornerShape(1.dp),
                                    color = Color(0xFFFF0034) // Red color matching APKMirror
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "DOWNLOAD APK", // APKMirror does not have localization
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Step 3
                    InstructionStep(
                        number = "3",
                        text = stringResource(
                            if (usingMountInstall) {
                                R.string.morphe_home_download_instructions_step3_mount
                            } else {
                                R.string.morphe_home_download_instructions_step3
                            }
                        )
                    )

                    // Step 4
                    InstructionStep(
                        number = "4",
                        text = stringResource(
                            if (usingMountInstall) {
                                R.string.morphe_home_download_instructions_step4_mount
                            } else {
                                R.string.morphe_home_download_instructions_step4
                            }

                        )
                    )
                }
            }

            // Important note for non-mount install
            if (!usingMountInstall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.morphe_home_download_instructions_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Helper composable for instruction steps
 * Displays numbered step with text
 */
@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
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
            // Fixed footer - button
            Button(
                onClick = onOpenFilePicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.morphe_home_file_picker_prompt_open))
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.morphe_home_file_picker_prompt_description, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Unsupported version warning dialog
 * Shown when selected APK version has no compatible patches
 */
@Composable
private fun UnsupportedVersionWarningDialog(
    packageName: String,
    version: String,
    recommendedVersion: String?,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_patcher_unsupported_version_dialog_title),
        footer = {
            // Fixed footer - buttons
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                // Single row for landscape
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onProceed,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(R.string.morphe_patcher_unsupported_version_dialog_proceed))
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            } else {
                // Column for portrait
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(R.string.morphe_patcher_unsupported_version_dialog_proceed))
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Package Name
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.package_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }

                    // Selected Version
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.morphe_patcher_selected_version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = version,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Recommended Version (if available)
                    if (recommendedVersion != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.morphe_home_recommended_version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = recommendedVersion,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
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
            // Fixed footer - button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.morphe_patcher_wrong_package_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.morphe_patcher_expected_package),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = expectedPackage,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 20.sp
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.morphe_patcher_selected_package),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = actualPackage,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
