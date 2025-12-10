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
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphePatcherScreen(
    onBackClick: () -> Unit,
    viewModel: PatcherViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val patcherSucceeded by viewModel.patcherSucceeded.observeAsState(null)
    val isRootMode by viewModel.prefs.useRootMode.getAsState()

    // Animated progress for smooth animation
    var targetProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    LaunchedEffect(viewModel.progress) {
        targetProgress = viewModel.progress
    }

    val patchesProgress = viewModel.patchesProgress

    var showErrorBottomSheet by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var hasPatchingError by rememberSaveable { mutableStateOf(false) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    // Installation dialog state
    var showInstallDialog by rememberSaveable { mutableStateOf(false) }
    var installDialogShownOnce by rememberSaveable { mutableStateOf(false) }
    var userCancelledInstall by rememberSaveable { mutableStateOf(false) }

    // Track install dialog state
    var installDialogState by rememberSaveable { mutableStateOf(InstallDialogState.INITIAL) }
    var isWaitingForUninstall by rememberSaveable { mutableStateOf(false) }
    var installErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Track if packageInstallerStatus was set
    var hadInstallerStatus by rememberSaveable { mutableStateOf(false) }

    // Monitor successful installation
    LaunchedEffect(viewModel.installedPackageName) {
        if (viewModel.installedPackageName != null) {
            // Installation succeeded, make sure dialog is closed
            showInstallDialog = false
            installDialogState = InstallDialogState.INITIAL
            isWaitingForUninstall = false
            hadInstallerStatus = false
            installErrorMessage = null
        }
    }

    LaunchedEffect(viewModel.packageInstallerStatus) {
        if (viewModel.packageInstallerStatus != null) {
            hadInstallerStatus = true
            val status = viewModel.packageInstallerStatus

            // Check if there's a conflict
            if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                // Dismiss any failure message that might have been set
                viewModel.dismissInstallFailureMessage()
                // Change dialog state to show conflict message
                installDialogState = InstallDialogState.CONFLICT
                installErrorMessage = null
                showInstallDialog = true // Show dialog with conflict message
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
                if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && isWaitingForUninstall) {
                    val pkg = intent.data?.schemeSpecificPart
                    val packageToUninstall = viewModel.exportMetadata?.packageName ?: viewModel.packageName
                    if (pkg == packageToUninstall) {
                        // Package was removed, change dialog state to show install button
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(500) // Wait for system dialog to close
                            isWaitingForUninstall = false
                            installDialogState = InstallDialogState.READY_TO_INSTALL
                            installErrorMessage = null
                            showInstallDialog = true
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

    val canInstall by remember {
        derivedStateOf {
            patcherSucceeded == true && !viewModel.isInstalling
        }
    }

    // Track if install button should be visible
    val shouldShowInstallButton by remember {
        derivedStateOf {
            patcherSucceeded == true // Always show Install button after successful patching
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

    var isSaving by rememberSaveable { mutableStateOf(false) }
    val exportApkLauncher = rememberLauncherForActivityResult(
        CreateDocument(APK_MIMETYPE)
    ) { uri ->
        if (uri != null && !isSaving) {
            isSaving = true
            viewModel.export(uri)
            viewModel.viewModelScope.launch {
                delay(2000)
                isSaving = false
            }
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

    // Monitor for patching errors (not installation errors)
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == false && !hasPatchingError) {
            hasPatchingError = true
            val steps = viewModel.steps
            val failedStep = steps.firstOrNull { it.state == State.FAILED }
            errorMessage = failedStep?.message ?: context.getString(R.string.morphe_patcher_unknown_error)
            showErrorBottomSheet = true
        }
    }

    // Auto-show install dialog after successful patching (only once)
    LaunchedEffect(patcherSucceeded, installDialogShownOnce) {
        if (patcherSucceeded == true && !installDialogShownOnce && !hasPatchingError) {
            installDialogShownOnce = true
            installDialogState = InstallDialogState.INITIAL
            installErrorMessage = null
            showInstallDialog = true
        }
    }

    BackHandler {
        if (patcherSucceeded == null) {
            // Show cancel dialog if patching is in progress
            showCancelDialog = true
        } else {
            // Allow normal back navigation if patching is complete or failed
            onBackClick()
        }
    }

    // Cancel patching confirmation dialog
    if (showCancelDialog) {
        CancelPatchingDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                onBackClick()
            }
        )
    }

    // Unified install dialog with state management
    if (showInstallDialog) {
        InstallDialog(
            state = installDialogState,
            isWaitingForUninstall = isWaitingForUninstall,
            isRootMode = isRootMode,
            errorMessage = installErrorMessage,
            onDismiss = {
                showInstallDialog = false
                installDialogState = InstallDialogState.INITIAL
                installErrorMessage = null
            },
            onInstall = {
                showInstallDialog = false
                userCancelledInstall = false
                installErrorMessage = null
                viewModel.install()
            },
            onUninstall = {
                isWaitingForUninstall = true
                hadInstallerStatus = false

                // Uninstall the conflicting patched package
                // exportMetadata contains info about the patched APK we're trying to install
                val packageToUninstall = viewModel.exportMetadata?.packageName ?: viewModel.packageName

                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageToUninstall")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                showInstallDialog = false
            },
            onCancel = {
                showInstallDialog = false
                installDialogState = InstallDialogState.INITIAL
                isWaitingForUninstall = false
                installErrorMessage = null
            }
        )
    }

    // Error bottom sheet
    if (showErrorBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showErrorBottomSheet = false },
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
                            text = errorMessage,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errorMessage))
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

    // Add handling for install failure message (only if no packageInstallerStatus)
    // Don't show if we're showing conflict dialog
    if (viewModel.packageInstallerStatus == null && !hadInstallerStatus && !showInstallDialog) {
        viewModel.installFailureMessage?.let { message ->
            LaunchedEffect(message) {
                installDialogState = InstallDialogState.ERROR
                installErrorMessage = message
                showInstallDialog = true
                viewModel.dismissInstallFailureMessage()
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

    // Main content
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content centered
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    patcherSucceeded == null -> {
                        PatchingInProgress(
                            progress = animatedProgress,
                            patchesProgress = patchesProgress,
                            downloadProgress = viewModel.downloadProgress,
                            viewModel = viewModel
                        )
                    }
                    patcherSucceeded == true -> {
                        PatchingSuccess(
                            isInstalling = viewModel.isInstalling,
                            installedPackageName = viewModel.installedPackageName,
                            userCancelledInstall = userCancelledInstall
                        )
                    }
                    else -> {
                        // Patching failed
                        PatchingFailed()
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
                    patcherSucceeded == true && !hasPatchingError -> {
                        FloatingActionButton(
                            onClick = {
                                if (!isSaving) {
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
                            onClick = { showCancelDialog = true },
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
                    hasPatchingError -> {
                        // Show error button only for patching errors
                        if (!showErrorBottomSheet) {
                            FloatingActionButton(
                                onClick = { showErrorBottomSheet = true },
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
                    shouldShowInstallButton -> {
                        FloatingActionButton(
                            onClick = {
                                if (canInstall) {
                                    if (viewModel.installedPackageName == null) {
                                        // Reset state and show install dialog
                                        installDialogState = InstallDialogState.INITIAL
                                        installErrorMessage = null
                                        userCancelledInstall = false
                                        showInstallDialog = true
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
                                    if (isRootMode) Icons.Outlined.FolderOpen else Icons.Outlined.FileDownload
                                } else {
                                    Icons.AutoMirrored.Outlined.OpenInNew
                                },
                                stringResource(
                                    if (viewModel.installedPackageName == null) {
                                        if (isRootMode) R.string.mount else R.string.install_app
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

// Helper enum for install dialog states
private enum class InstallDialogState {
    INITIAL,            // First time showing dialog - "Install app?"
    CONFLICT,           // Conflict detected - "Package conflict, need to uninstall"
    READY_TO_INSTALL,   // After uninstall - "Ready to install, press Install"
    ERROR               // Installation error - show error message with uninstall option
}

@Composable
private fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    downloadProgress: Pair<Long, Long?>? = null,
    viewModel: PatcherViewModel
) {
    val (completed, total) = patchesProgress

    // Track when download is complete to hide progress smoothly
    var isDownloadComplete by remember { mutableStateOf(false) }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress != null) {
            val (downloaded, totalSize) = downloadProgress
            // Check if download is complete
            if (totalSize != null && downloaded >= totalSize) {
                // Wait longer before hiding to show 100% completion
                delay(1500)
                isDownloadComplete = true
            } else {
                isDownloadComplete = false
            }
        } else {
            isDownloadComplete = false
        }
    }

    val context = LocalContext.current
    var currentMessage by remember {
        mutableStateOf(
            HomeAndPatcherMessages.getPatcherMessage(context)
        )
    }

    // Rotate messages every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            currentMessage = HomeAndPatcherMessages.getPatcherMessage(context)
        }
    }

    // Fixed layout to prevent shifting
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Fun message
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = stringResource(currentMessage),
                transitionSpec = {
                    fadeIn(animationSpec = tween(1000)) togetherWith
                            fadeOut(animationSpec = tween(1000))
                },
                label = "message_animation"
            ) { messageResId ->
                Text(
                    text = messageResId,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // Circular progress
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 12.dp,
            )

            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(
                        R.string.morphe_patcher_percentage,
                        (progress * 100).toInt()
                    ),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(
                        R.string.morphe_patcher_patches_progress,
                        completed,
                        total
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Fixed space for download progress bar and current step to prevent layout shifts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Download progress bar
            AnimatedVisibility(
                visible = downloadProgress != null && !isDownloadComplete,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(500)) + shrinkVertically(animationSpec = tween(500))
            ) {
                downloadProgress?.let { (downloaded, total) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (total != null && total > 0) {
                                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            strokeCap = StrokeCap.Round,
                        )

                        Text(
                            text = if (total != null) {
                                "${formatBytes(downloaded)} / ${formatBytes(total)}"
                            } else {
                                formatBytes(downloaded)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Current step indicator
            CurrentStepIndicator(viewModel = viewModel)
        }
    }
}

// Function to format bytes into a convenient format
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun PatchingSuccess(
    isInstalling: Boolean,
    installedPackageName: String?,
    userCancelledInstall: Boolean
) {
    // Determine current state for animation
    val currentState = when {
        isInstalling -> SuccessState.INSTALLING
        installedPackageName != null -> SuccessState.INSTALLED
        userCancelledInstall -> SuccessState.INSTALL_CANCELLED
        else -> SuccessState.COMPLETED
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Icon with gradient background (stays the same for success states, error for cancelled)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (userCancelledInstall)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (userCancelledInstall) Icons.Default.Close else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = if (userCancelledInstall)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))

        // Animated title text
        AnimatedContent(
            targetState = currentState,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(500))
            },
            label = "title_animation"
        ) { state ->
            Text(
                text = stringResource(
                    when (state) {
                        SuccessState.INSTALLING -> R.string.morphe_patcher_installing
                        SuccessState.INSTALLED -> R.string.morphe_patcher_success_title
                        SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_title
                        SuccessState.COMPLETED -> R.string.morphe_patcher_complete_title
                    }
                ),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (userCancelledInstall)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(8.dp))

        // Animated subtitle text (only for INSTALLING, INSTALLED, and CANCELLED states)
        AnimatedVisibility(
            visible = currentState != SuccessState.COMPLETED,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            AnimatedContent(
                targetState = currentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith
                            fadeOut(animationSpec = tween(500))
                },
                label = "subtitle_animation"
            ) { state ->
                Text(
                    text = stringResource(
                        when (state) {
                            SuccessState.INSTALLING -> R.string.morphe_patcher_installing_subtitle
                            SuccessState.INSTALLED -> R.string.morphe_patcher_success_subtitle
                            SuccessState.INSTALL_CANCELLED -> R.string.morphe_patcher_install_cancelled_subtitle
                            else -> R.string.morphe_patcher_installing_subtitle // fallback
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Helper enum for state tracking
private enum class SuccessState {
    INSTALLING,
    INSTALLED,
    INSTALL_CANCELLED,
    COMPLETED
}

@Composable
private fun PatchingFailed() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.morphe_patcher_failed_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.morphe_patcher_failed_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CurrentStepIndicator(viewModel: PatcherViewModel) {
    // Get current running step
    val currentStep by remember {
        derivedStateOf {
            viewModel.steps.firstOrNull { it.state == State.RUNNING }
        }
    }

    AnimatedContent(
        targetState = currentStep?.name,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith
                    fadeOut(animationSpec = tween(400))
        },
        label = "step_animation"
    ) { stepName ->
        if (stepName != null) {
            Text(
                text = stepName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun CancelPatchingDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.patcher_stop_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description
                Text(
                    text = stringResource(R.string.patcher_stop_confirm_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stop button
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.yes))
                    }

                    // Continue button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.no))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallDialog(
    state: InstallDialogState,
    isWaitingForUninstall: Boolean,
    errorMessage: String?,
    isRootMode: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit
) {
    val installButtonText = if (isRootMode) R.string.mount else R.string.install_app
    val installIcon = if (isRootMode) Icons.Outlined.FolderOpen else Icons.Outlined.FileDownload

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon based on state
                Surface(
                    shape = CircleShape,
                    color = when (state) {
                        InstallDialogState.CONFLICT, InstallDialogState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (state) {
                                InstallDialogState.CONFLICT, InstallDialogState.ERROR -> Icons.Outlined.Warning
                                else -> installIcon
                            },
                            contentDescription = null,
                            tint = when (state) {
                                InstallDialogState.CONFLICT, InstallDialogState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(
                        when (state) {
                            InstallDialogState.ERROR -> R.string.install_app_fail_title
                            else -> installButtonText
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description or error message
                if (state == InstallDialogState.ERROR && errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Box(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(
                                when (state) {
                                    InstallDialogState.INITIAL -> if (isRootMode)
                                        R.string.morphe_patcher_mount_dialog_message
                                    else
                                        R.string.morphe_patcher_install_dialog_message
                                    InstallDialogState.CONFLICT -> R.string.morphe_patcher_install_conflict_message
                                    InstallDialogState.READY_TO_INSTALL -> if (isRootMode)
                                        R.string.morphe_patcher_mount_ready_message
                                    else
                                        R.string.morphe_patcher_install_ready_message
                                    InstallDialogState.ERROR -> R.string.morphe_patcher_install_dialog_message
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Root mode warning
                        if (isRootMode && state == InstallDialogState.INITIAL) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.morphe_root_gmscore_excluded),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action button (Install/Mount or Uninstall)
                    when (state) {
                        InstallDialogState.INITIAL, InstallDialogState.READY_TO_INSTALL -> {
                            Button(
                                onClick = onInstall,
                                enabled = !isWaitingForUninstall,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    installIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(installButtonText))
                            }
                        }
                        InstallDialogState.CONFLICT, InstallDialogState.ERROR -> {
                            Button(
                                onClick = onUninstall,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.uninstall))
                            }
                        }
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
