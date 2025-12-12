package app.revanced.manager.ui.screen

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.patcher.*
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp

/**
 * MorphePatcherScreen - Simplified patcher screen with progress tracking
 * Shows patching progress, handles installation, and provides export functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphePatcherScreen(
    onBackClick: () -> Unit,
    viewModel: PatcherViewModel,
    usingMountInstall : Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val patcherSucceeded by viewModel.patcherSucceeded.observeAsState(null)

    // Remember patcher state
    val state = rememberMorphePatcherState(viewModel)

    // Animated progress with dual-mode animation: slow crawl + fast catch-up
    var displayProgress by remember { mutableStateOf(viewModel.progress) }
    var showLongStepWarning by remember { mutableStateOf(false) }

    val displayProgressAnimate by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Dual-mode animation: always crawls forward, but accelerates when catching up
    LaunchedEffect(patcherSucceeded) {
        var lastCompletedStep  = 0
        var currentStepStartTime = System.currentTimeMillis()

        while (patcherSucceeded == null) {
            val now = System.currentTimeMillis()

            val currentCompletedStep = viewModel.getCurrentStepIndex()
            if (lastCompletedStep != currentCompletedStep) {
                // New step!
                lastCompletedStep = currentCompletedStep
                currentStepStartTime = now
                showLongStepWarning = false
            }

            val timeUntilStepShowsBePatient = 40 * 1000 // 40 seconds
            val timeSinceStepStarted = now - currentStepStartTime
            if (!showLongStepWarning && timeSinceStepStarted > timeUntilStepShowsBePatient) {
                showLongStepWarning = true
            }

            val actualProgress = viewModel.progress
            if (actualProgress >= 0.98f) {
                displayProgress = actualProgress
            } else {
                // Over estimate the progress by about 1% per second, but decays to
                // adding smaller adjustments each second until the current step completes
                fun overEstimateProgressAdjustment(secondsElapsed: Long): Float {
                    // Sigmoid curve. Allows up to 10% over actual progress then flattens off.
                    // https://desmos.com/calculator/16p60zu48m
                    val maximumValue = 15.0f // Up to 15% over correct
                    val timeConstant = 20.0f // Larger value = longer time until plateau
                    return maximumValue * (1 - exp(-secondsElapsed / timeConstant))
                }

                val secondsSinceStepStarted = timeSinceStepStarted / 1000
                val overEstimatedProgress = actualProgress +  0.01f * overEstimateProgressAdjustment(
                    secondsSinceStepStarted
                )

                // Don't allow rolling back the progress if it went over,
                // and don't go over 98% unless the actual progress is that far
                displayProgress = clamp(
                    displayProgress,
                    overEstimatedProgress,
                    0.98f
                )
            }

            // Update four times a second
            delay(250)
        }
    }

    val patchesProgress = viewModel.patchesProgress

    // Monitor for patching errors (not installation errors)
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == false && !state.hasPatchingError) {
            state.hasPatchingError = true
            val steps = viewModel.steps
            val failedStep = steps.firstOrNull { it.state == State.FAILED }
            state.errorMessage = failedStep?.message ?: context.getString(R.string.morphe_patcher_unknown_error)
            state.showErrorBottomSheet = true
        }
    }

    // Monitor successful installation
    LaunchedEffect(viewModel.installedPackageName) {
        if (viewModel.installedPackageName != null) {
            // Installation succeeded, make sure dialog is closed
            state.showInstallDialog = false
            state.installDialogState = InstallDialogState.INITIAL
            state.isWaitingForUninstall = false
            state.hadInstallerStatus = false
            state.installErrorMessage = null
        }
    }

    // Monitor package installer status
    LaunchedEffect(viewModel.packageInstallerStatus) {
        if (viewModel.packageInstallerStatus != null) {
            state.hadInstallerStatus = true
            val status = viewModel.packageInstallerStatus

            // Check if there's a conflict
            if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                // Dismiss any failure message that might have been set
                viewModel.dismissInstallFailureMessage()
                // Change dialog state to show conflict message
                state.installDialogState = InstallDialogState.CONFLICT
                state.installErrorMessage = null
                state.showInstallDialog = true // Show dialog with conflict message
                viewModel.dismissPackageInstallerDialog()
            } else if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // For other errors (except pending user action), keep the dialog hidden and let installFailureMessage handle it
                viewModel.dismissPackageInstallerDialog()
            } else {
                // STATUS_PENDING_USER_ACTION - waiting for user to grant permission, don't treat as error
                viewModel.dismissPackageInstallerDialog()
                viewModel.dismissInstallFailureMessage() // Clear any premature error messages
            }
        }
    }

    // Monitor package removal during uninstall for reinstall
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && state.isWaitingForUninstall) {
                    val pkg = intent.data?.schemeSpecificPart
                    val packageToUninstall = viewModel.exportMetadata?.packageName ?: viewModel.packageName
                    if (pkg == packageToUninstall) {
                        // Package was removed, change dialog state to show install button
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(500) // Wait for system dialog to close
                            state.isWaitingForUninstall = false
                            state.installDialogState = InstallDialogState.READY_TO_INSTALL
                            state.installErrorMessage = null
                            state.showInstallDialog = true
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("MorphePatcherScreen", "Failed to unregister receiver", e)
            }
        }
    }

    // Auto-show install dialog after successful patching (only once)
    LaunchedEffect(patcherSucceeded, state.installDialogShownOnce) {
        if (patcherSucceeded == true && !state.installDialogShownOnce && !state.hasPatchingError) {
            state.installDialogShownOnce = true
            state.installDialogState = InstallDialogState.INITIAL
            state.installErrorMessage = null
            state.showInstallDialog = true
        }
    }

    // Handle install failure message (only if no packageInstallerStatus)
    // Don't show if we're showing conflict dialog
    if (viewModel.packageInstallerStatus == null && !state.hadInstallerStatus && !state.showInstallDialog) {
        viewModel.installFailureMessage?.let { message ->
            LaunchedEffect(message) {
                state.installDialogState = InstallDialogState.ERROR
                state.installErrorMessage = message
                state.showInstallDialog = true
                viewModel.dismissInstallFailureMessage()
            }
        }
    }

    BackHandler {
        if (patcherSucceeded == null) {
            // Show cancel dialog if patching is in progress
            state.showCancelDialog = true
        } else {
            // Allow normal back navigation if patching is complete or failed
            onBackClick()
        }
    }

    // Keep screen on during patching
    if (patcherSucceeded == null) {
        DisposableEffect(Unit) {
            val window = (context as Activity).window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Export APK setup
    val exportFormat = remember { viewModel.prefs.patchedAppExportFormat.getBlocking() }
    val exportMetadata = viewModel.exportMetadata
    val fallbackMetadata = remember(viewModel.packageName, viewModel.version) {
        PatchedAppExportData(
            appName = viewModel.packageName,
            packageName = viewModel.packageName,
            appVersion = viewModel.version ?: "unspecified"
        )
    }
    val exportFileName = remember(exportFormat, exportMetadata, fallbackMetadata) {
        ExportNameFormatter.format(exportFormat, exportMetadata ?: fallbackMetadata)
    }

    val exportApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APK_MIMETYPE)
    ) { uri ->
        if (uri != null && !state.isSaving) {
            state.isSaving = true
            viewModel.export(uri)
            viewModel.viewModelScope.launch {
                delay(2000)
                state.isSaving = false
            }
        }
    }

    // Add activity launcher for handling plugin activities or external installs
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = viewModel::handleActivityResult
    )
    EventEffect(flow = viewModel.launchActivityFlow) { intent ->
        activityLauncher.launch(intent)
    }

    // Add activity prompt dialog
    viewModel.activityPromptDialog?.let { title ->
        AlertDialog(
            onDismissRequest = viewModel::rejectInteraction,
            confirmButton = {
                TextButton(
                    onClick = viewModel::allowInteraction
                ) {
                    Text(stringResource(R.string.continue_))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::rejectInteraction
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(title) },
            text = {
                Text(stringResource(R.string.plugin_activity_dialog_body))
            }
        )
    }

    // Cancel patching confirmation dialog
    if (state.showCancelDialog) {
        CancelPatchingDialog(
            onDismiss = { state.showCancelDialog = false },
            onConfirm = {
                state.showCancelDialog = false
                onBackClick()
            }
        )
    }

    // Unified install dialog with state management
    if (state.showInstallDialog) {
        InstallDialog(
            state = state.installDialogState,
            isWaitingForUninstall = state.isWaitingForUninstall,
            usingMountInstall = usingMountInstall,
            errorMessage = state.installErrorMessage,
            onDismiss = {
                state.showInstallDialog = false
                state.installDialogState = InstallDialogState.INITIAL
                state.installErrorMessage = null
            },
            onInstall = {
                state.showInstallDialog = false
                state.userCancelledInstall = false
                state.installErrorMessage = null
                viewModel.install()
            },
            onUninstall = {
                state.isWaitingForUninstall = true
                state.hadInstallerStatus = false

                // Uninstall the conflicting patched package
                val packageToUninstall = viewModel.exportMetadata?.packageName ?: viewModel.packageName

                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageToUninstall")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                state.showInstallDialog = false
            }
        ) {
            state.showInstallDialog = false
            state.installDialogState = InstallDialogState.INITIAL
            state.isWaitingForUninstall = false
            state.installErrorMessage = null
        }
    }

    // Error bottom sheet
    if (state.showErrorBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { state.showErrorBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = stringResource(R.string.morphe_patcher_failed_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = state.errorMessage,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(state.errorMessage))
                        context.toast(context.getString(R.string.morphe_patcher_error_copied))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.morphe_patcher_copy_error))
                }
            }
        }
    }

    // Main content
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content centered
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.currentPatcherState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(800)) togetherWith
                                fadeOut(animationSpec = tween(800))
                    },
                    label = "patcher_state_animation"
                ) { patcherState ->
                    when (patcherState) {
                        PatcherState.IN_PROGRESS -> {
                            PatchingInProgress(
                                progress = displayProgressAnimate,
                                patchesProgress = patchesProgress,
                                downloadProgress = viewModel.downloadProgress,
                                viewModel = viewModel,
                                showLongStepWarning = showLongStepWarning
                            )
                        }
                        PatcherState.SUCCESS -> {
                            PatchingSuccess(
                                isInstalling = viewModel.isInstalling,
                                installedPackageName = viewModel.installedPackageName,
                                userCancelledInstall = state.userCancelledInstall
                            )
                        }
                        PatcherState.FAILED -> {
                            PatchingFailed()
                        }
                    }
                }
            }

            // Floating action buttons - bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: Save APK button or empty space for symmetry
                when {
                    patcherSucceeded == true && !state.hasPatchingError -> {
                        FloatingActionButton(
                            onClick = {
                                if (!state.isSaving) {
                                    exportApkLauncher.launch(exportFileName)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Outlined.Save, stringResource(R.string.morphe_patcher_save_apk))
                        }
                    }
                    patcherSucceeded == null -> {
                        // Cancel button during patching
                        FloatingActionButton(
                            onClick = { state.showCancelDialog = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                    else -> {
                        // Empty spacer for symmetry
                        Spacer(Modifier.size(56.dp))
                    }
                }

                // Center: Home button (only show when patching is complete)
                if (patcherSucceeded != null) {
                    FloatingActionButton(
                        onClick = onBackClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Home, "Home")
                    }
                } else {
                    // Empty spacer during patching
                    Spacer(Modifier.size(56.dp))
                }

                // Right: Install or Show Error button
                when {
                    state.hasPatchingError -> {
                        // Show error button only for patching errors
                        if (!state.showErrorBottomSheet) {
                            FloatingActionButton(
                                onClick = { state.showErrorBottomSheet = true },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Icon(Icons.Default.Error, stringResource(R.string.morphe_patcher_show_error))
                            }
                        } else {
                            // Empty spacer for symmetry when error sheet is shown
                            Spacer(Modifier.size(56.dp))
                        }
                    }
                    // Show install button - always visible when patching succeeded
                    state.shouldShowInstallButton -> {
                        FloatingActionButton(
                            onClick = {
                                if (state.canInstall) {
                                    if (viewModel.installedPackageName == null) {
                                        // Reset state and show install dialog
                                        state.resetInstallDialog()
                                    } else {
                                        viewModel.open()
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                if (viewModel.installedPackageName == null) {
                                    if (usingMountInstall) Icons.Outlined.FolderOpen else Icons.Outlined.FileDownload
                                } else {
                                    Icons.AutoMirrored.Outlined.OpenInNew
                                },
                                stringResource(
                                    if (viewModel.installedPackageName == null) {
                                        if (usingMountInstall) R.string.mount else R.string.install_app
                                    } else {
                                        R.string.open_app
                                    }
                                )
                            )
                        }
                    }
                    else -> {
                        // Empty spacer for symmetry
                        Spacer(Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}
