package app.revanced.manager.ui.screen

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import androidx.lifecycle.viewModelScope
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.InstallerStatusDialog
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.ExportNameFormatter
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.toast
import app.universal.revanced.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick patcher screen with simplified UI - fullscreen without top bar
 * Shows a large circular progress indicator with witty messages
 * Automatically installs the patched app when done
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPatcherScreen(
    onBackClick: () -> Unit,
    viewModel: PatcherViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val patcherSucceeded by viewModel.patcherSucceeded.observeAsState(null)

    // Same logic as original PatcherScreen
    val canInstall by remember {
        derivedStateOf {
            patcherSucceeded == true && (viewModel.installedPackageName != null || !viewModel.isInstalling)
        }
    }

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
    var hasError by rememberSaveable { mutableStateOf(false) }

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

    // Monitor for errors
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == false && !hasError) {
            hasError = true
            val steps = viewModel.steps
            val failedStep = steps.firstOrNull { it.state == app.revanced.manager.ui.model.State.FAILED }
            errorMessage = failedStep?.message ?: context.getString(R.string.quick_patcher_unknown_error)
            showErrorBottomSheet = true
        }
    }

    // Auto-install after successful patching
    LaunchedEffect(patcherSucceeded, viewModel.isInstalling, viewModel.installedPackageName) {
        if (patcherSucceeded == true && !viewModel.isInstalling && viewModel.installedPackageName == null && !hasError) {
            viewModel.install()
        }
    }

    BackHandler {
        onBackClick()
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
                    text = stringResource(R.string.quick_patcher_failed_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errorMessage))
                        context.toast(context.getString(R.string.quick_patcher_error_copied))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quick_patcher_copy_error))
                }

                TextButton(
                    onClick = { showErrorBottomSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.quick_patcher_close))
                }
            }
        }
    }

    // Add handling for installer status dialog (from PatcherScreen)
    viewModel.packageInstallerStatus?.let {
        InstallerStatusDialog(it, viewModel, viewModel::dismissPackageInstallerDialog)
    }

    // Add handling for memory adjustment dialog (from PatcherScreen)
    viewModel.memoryAdjustmentDialog?.let { state ->
        val message = if (state.adjusted) {
            stringResource(
                R.string.patcher_memory_adjustment_message_reduced,
                state.previousLimit,
                state.newLimit
            )
        } else {
            stringResource(
                R.string.patcher_memory_adjustment_message_no_change,
                state.previousLimit
            )
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissMemoryAdjustmentDialog,
            title = { Text(stringResource(R.string.patcher_memory_adjustment_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::retryAfterMemoryAdjustment) {
                    Text(stringResource(R.string.patcher_memory_adjustment_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMemoryAdjustmentDialog) {
                    Text(stringResource(R.string.patcher_memory_adjustment_dismiss))
                }
            }
        )
    }

    // Add handling for missing patch dialog (from PatcherScreen)
    viewModel.missingPatchDialog?.let { state ->
        val patchList = state.patchNames.joinToString(separator = "\n• ", prefix = "• ")
        AlertDialog(
            onDismissRequest = viewModel::dismissMissingPatchDialog,
            title = { Text(stringResource(R.string.patcher_missing_patch_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.patcher_missing_patch_message,
                        patchList
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissMissingPatchDialog
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Add handling for install failure message (from PatcherScreen)
    viewModel.installFailureMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInstallFailureMessage,
            title = { Text(stringResource(R.string.install_app_fail_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissInstallFailureMessage) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Add handling for install status (from PatcherScreen)
    viewModel.installStatus?.let { status ->
        when (status) {
            PatcherViewModel.InstallCompletionStatus.InProgress -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is PatcherViewModel.InstallCompletionStatus.Success -> {
                LaunchedEffect(status) {
                    viewModel.clearInstallStatus()
                }
            }

            is PatcherViewModel.InstallCompletionStatus.Failure -> {
                if (viewModel.installFailureMessage == null) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissInstallFailureMessage,
                        title = { Text(stringResource(R.string.install_app_fail_title)) },
                        text = { Text(status.message) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissInstallFailureMessage) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    )
                }
            }
        }
    }

    // Add activity launcher for handling plugin activities or external installs (from PatcherScreen)
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = viewModel::handleActivityResult
    )
    EventEffect(flow = viewModel.launchActivityFlow) { intent ->
        activityLauncher.launch(intent)
    }

    // Add activity prompt dialog (from PatcherScreen)
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
                            patchesProgress = patchesProgress
                        )
                    }
                    patcherSucceeded == true && !hasError -> {
                        PatchingSuccess(
                            isInstalling = viewModel.isInstalling,
                            installedPackageName = viewModel.installedPackageName
                        )
                    }
                    else -> {
                        PatchingFailed()
                    }
                }
            }

            // Floating action buttons - bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: Save APK or Home button
                when {
                    patcherSucceeded == true && !hasError -> {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    if (!isSaving) {
                                        exportApkLauncher.launch(exportFileName)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Icon(Icons.Outlined.Save, stringResource(R.string.quick_patcher_save_apk))
                            }

                            FloatingActionButton(
                                onClick = onBackClick,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.Home, "Home")
                            }
                        }
                    }
                    patcherSucceeded == null -> {
                        // Cancel button during patching
                        FloatingActionButton(
                            onClick = onBackClick,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                    hasError -> {
                        // Home button after error
                        FloatingActionButton(
                            onClick = onBackClick,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.Home, "Home")
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Right: Install, Copy Error, or Show Error button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        hasError -> {
                            // Copy error button (above show error)
                            if (!showErrorBottomSheet) {
                                FloatingActionButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(errorMessage))
                                        context.toast(context.getString(R.string.quick_patcher_error_copied))
                                    },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Icon(Icons.Default.ContentCopy, stringResource(R.string.quick_patcher_copy_error))
                                }
                            }

                            // Show error button
                            if (!showErrorBottomSheet) {
                                FloatingActionButton(
                                    onClick = { showErrorBottomSheet = true },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ) {
                                    Icon(Icons.Default.Error, stringResource(R.string.quick_patcher_show_error))
                                }
                            }
                        }
                        // Show install button using same logic as original PatcherScreen
                        canInstall -> {
                            FloatingActionButton(
                                onClick = {
                                    if (viewModel.installedPackageName == null) {
                                        viewModel.install()
                                    } else {
                                        viewModel.open()
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    if (viewModel.installedPackageName == null)
                                        Icons.Outlined.FileDownload
                                    else
                                        Icons.Outlined.FileDownload,
                                    stringResource(
                                        if (viewModel.installedPackageName == null)
                                            R.string.install_app
                                        else
                                            R.string.open_app
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>
) {
    val (completed, total) = patchesProgress

    // Witty messages from strings
    val wittyMessages = remember {
        listOf(
            R.string.quick_patcher_message_1,
            R.string.quick_patcher_message_2,
            R.string.quick_patcher_message_3,
            R.string.quick_patcher_message_4,
            R.string.quick_patcher_message_5,
            R.string.quick_patcher_message_6,
            R.string.quick_patcher_message_7,
            R.string.quick_patcher_message_8,
            R.string.quick_patcher_message_9,
            R.string.quick_patcher_message_10
        )
    }

    var currentMessageIndex by remember { mutableStateOf(0) }

    // Rotate messages every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            currentMessageIndex = (currentMessageIndex + 1) % wittyMessages.size
        }
    }

    // Fixed layout to prevent shifting
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Witty message - fixed height box to prevent shifting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = wittyMessages[currentMessageIndex],
                transitionSpec = {
                    fadeIn(animationSpec = tween(1000)) togetherWith
                            fadeOut(animationSpec = tween(1000))
                },
                label = "message_animation"
            ) { messageResId ->
                Text(
                    text = stringResource(messageResId),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // Circular progress - fixed size
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
                        R.string.quick_patcher_percentage,
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
                        R.string.quick_patcher_patches_progress,
                        completed,
                        total
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatchingSuccess(
    isInstalling: Boolean,
    installedPackageName: String?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "success_animation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        when {
            isInstalling -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp
                    )
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.quick_patcher_installing),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.quick_patcher_installing_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            installedPackageName != null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.quick_patcher_success_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.quick_patcher_success_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.quick_patcher_complete_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
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
            text = stringResource(R.string.quick_patcher_failed_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.quick_patcher_failed_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
