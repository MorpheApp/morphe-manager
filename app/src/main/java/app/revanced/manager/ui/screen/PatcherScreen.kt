package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.screen.patcher.*
import app.revanced.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.revanced.manager.ui.screen.settings.system.ensureValidEntries
import app.revanced.manager.ui.screen.shared.InfoBadge
import app.revanced.manager.ui.screen.shared.InfoBadgeStyle
import app.revanced.manager.ui.screen.shared.MorpheCard
import app.revanced.manager.ui.screen.shared.MorpheSettingsDivider
import app.revanced.manager.ui.viewmodel.InstallViewModel
import app.revanced.manager.ui.viewmodel.PatcherViewModel
import app.revanced.manager.ui.viewmodel.SettingsViewModel
import app.revanced.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Simplified patcher screen with progress tracking
 * Shows patching progress, handles installation with pre-conflict detection, and provides export functionality
 */
@SuppressLint("LocalContextGetResourceValueCall", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatcherScreen(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    usingMountInstall: Boolean,
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val patcherSucceeded by patcherViewModel.patcherSucceeded.observeAsState(null)

    // Remember patcher state
    val state = rememberMorphePatcherState(patcherViewModel)

    // Animated progress with dual-mode animation
    var displayProgress by rememberSaveable { mutableFloatStateOf(patcherViewModel.progress) }
    var showLongStepWarning by rememberSaveable { mutableStateOf(false) }
    var showSuccessScreen by rememberSaveable { mutableStateOf(false) }

    val displayProgressAnimate by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Get output file from viewModel
    val outputFile = patcherViewModel.outputFile

    // Progress animation logic
    LaunchedEffect(patcherSucceeded) {
        var lastProgressUpdate = 0.0f
        var currentStepStartTime = System.currentTimeMillis()

        while (patcherSucceeded == null) {
            val now = System.currentTimeMillis()

            val actualProgress = patcherViewModel.progress
            if (lastProgressUpdate != actualProgress) {
                // Progress updated!
                lastProgressUpdate = actualProgress
                currentStepStartTime = now
                showLongStepWarning = false
                if (Log.isLoggable(tag, Log.DEBUG)) {
                    Log.d(tag, "Real progress update: ${(actualProgress * 1000).toInt() / 10.0f}%")
                }
            }

            val timeUntilStepShowsBePatient = 60 * 1000 // 60 seconds
            val timeSinceStepStarted = now - currentStepStartTime
            if (!showLongStepWarning && timeSinceStepStarted > timeUntilStepShowsBePatient) {
                showLongStepWarning = true
            }

            // When to stop using overcorrection of progress and always use the actual progress.
            val maxOverCorrectPercentage = 0.97

            if (actualProgress >= maxOverCorrectPercentage) {
                displayProgress = actualProgress
            } else {
                // Over estimate the progress by about 1% per second, but decays to
                // adding smaller adjustments each second until the current step completes
                fun overEstimateProgressAdjustment(secondsElapsed: Double): Double {
                    // Sigmoid curve. Give larger correct soon after the step starts but then flattens off.
                    val maximumValue = 25.0 // Up to 25% over correct
                    val timeConstant = 50.0 // Larger value = longer time until plateau
                    return maximumValue * (1 - exp(-secondsElapsed / timeConstant))
                }

                val secondsSinceStepStarted = timeSinceStepStarted / 1000.0
                val overEstimatedProgress = min(
                    maxOverCorrectPercentage,
                    actualProgress + 0.01 * overEstimateProgressAdjustment(secondsSinceStepStarted)
                ).toFloat()

                // Don't allow rolling back the progress if it went over,
                // and don't go over 98% unless the actual progress is that far
                displayProgress = max(displayProgress, overEstimatedProgress)
            }

            // Update four times a second
            delay(250)
        }

        // Patching completed - ensure progress reaches 100%
        if (patcherSucceeded == true) {
            displayProgress = 1.0f
            // Wait for animation to complete and add extra delay
            delay(2000) // Wait 2 seconds at 100% before showing success screen
            showSuccessScreen = true
        } else {
            // Failed - show immediately
            showSuccessScreen = true
        }
    }

    val patchesProgress = patcherViewModel.patchesProgress

    // Monitor for patching errors (not installation errors)
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == false && !state.hasPatchingError) {
            state.hasPatchingError = true
            val steps = patcherViewModel.steps
            val failedStep = steps.firstOrNull { it.state == State.FAILED }
            state.errorMessage = failedStep?.message
                ?: context.getString(R.string.patcher_unknown_error)
            state.showErrorBottomSheet = true
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
    val exportFormat = remember { patcherViewModel.prefs.patchedAppExportFormat.getBlocking() }
    val exportMetadata = patcherViewModel.exportMetadata
    val fallbackMetadata = remember(patcherViewModel.packageName, patcherViewModel.version) {
        PatchedAppExportData(
            appName = patcherViewModel.packageName,
            packageName = patcherViewModel.packageName,
            appVersion = patcherViewModel.version ?: "unspecified"
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
            // Use InstallViewModel for export
            installViewModel.export(outputFile, uri) { success ->
                patcherViewModel.viewModelScope.launch {
                    if (success) {
                        // Also save patched app metadata
                        patcherViewModel.persistPatchedApp(null, InstallType.SAVED)
                    }
                    delay(2000)
                    state.isSaving = false
                }
            }
        }
    }

    // Activity launcher for handling plugin activities or external installs
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = patcherViewModel::handleActivityResult
    )
    EventEffect(flow = patcherViewModel.launchActivityFlow) { intent ->
        activityLauncher.launch(intent)
    }

    // Activity prompt dialog
    patcherViewModel.activityPromptDialog?.let { title ->
        AlertDialog(
            onDismissRequest = patcherViewModel::rejectInteraction,
            confirmButton = {
                TextButton(onClick = patcherViewModel::allowInteraction) {
                    Text(stringResource(R.string.continue_))
                }
            },
            dismissButton = {
                TextButton(onClick = patcherViewModel::rejectInteraction) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(title) },
            text = { Text(stringResource(R.string.plugin_activity_dialog_body)) }
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

    // Error bottom sheet
    if (state.showErrorBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { state.showErrorBottomSheet = false },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error icon
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = stringResource(R.string.patcher_failed_dialog_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Error message card
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    MorpheCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(horizontal = 24.dp),
                        elevation = 2.dp,
                        cornerRadius = 16.dp
                    ) {
                        Column {
                            // Error log header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.patcher_error_log),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                InfoBadge(
                                    text = stringResource(R.string.patcher_error_technical),
                                    style = InfoBadgeStyle.Error,
                                    isCompact = true
                                )
                            }

                            MorpheSettingsDivider(fullWidth = true)

                            // Scrollable error message
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Copy button
                    FilledTonalButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(state.errorMessage))
                            context.toast(context.getString(R.string.patcher_error_copied))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(android.R.string.copy),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Close button
                    Button(
                        onClick = { state.showErrorBottomSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.close),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Installer selection dialog for patcher screen
    if (installViewModel.showInstallerSelectionDialog) {
        val installerManager: InstallerManager = koinInject()
        val settingsViewModel: SettingsViewModel = koinViewModel()

        val primaryPreference by settingsViewModel.prefs.installerPrimary.getAsState()
        val primaryToken = remember(primaryPreference) {
            installerManager.parseToken(primaryPreference)
        }

        val installTarget = InstallerManager.InstallTarget.PATCHER

        // Installer entries with periodic updates
        var options by remember(primaryToken) {
            mutableStateOf(
                ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false),
                    primaryToken,
                    installerManager,
                    installTarget
                )
            )
        }

        // Periodically update installer list for availability changes
        LaunchedEffect(installTarget, primaryToken) {
            while (isActive) {
                options = ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false),
                    primaryToken,
                    installerManager,
                    installTarget
                )
                delay(1_500)
            }
        }

        InstallerSelectionDialog(
            title = stringResource(R.string.installer_title),
            options = options,
            selected = primaryToken,
            onDismiss = installViewModel::dismissInstallerSelectionDialog,
            onConfirm = { selectedToken ->
                installViewModel.proceedWithSelectedInstaller(selectedToken)
            },
            onOpenShizuku = installerManager::openShizukuApp
        )
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = if (showSuccessScreen) state.currentPatcherState else PatcherState.IN_PROGRESS,
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
                        patcherViewModel = patcherViewModel,
                        showLongStepWarning = showLongStepWarning,
                        onCancelClick = { state.showCancelDialog = true },
                        onHomeClick = onBackClick
                    )
                }

                PatcherState.SUCCESS -> {
                    PatchingSuccess(
                        installViewModel = installViewModel,
                        usingMountInstall = usingMountInstall,
                        onInstall = {
                            if (usingMountInstall) {
                                // Mount install
                                val inputVersion = patcherViewModel.version
                                    ?: patcherViewModel.currentSelectedApp.version
                                    ?: "unknown"
                                installViewModel.installMount(
                                    outputFile = outputFile,
                                    inputFile = patcherViewModel.inputFile,
                                    packageName = patcherViewModel.packageName,
                                    inputVersion = inputVersion,
                                    onPersistApp = { pkg, type ->
                                        patcherViewModel.persistPatchedApp(pkg, type)
                                    }
                                )
                            } else {
                                // Regular install with pre-conflict check
                                installViewModel.install(
                                    outputFile = outputFile,
                                    originalPackageName = patcherViewModel.packageName,
                                    onPersistApp = { pkg, type ->
                                        patcherViewModel.persistPatchedApp(pkg, type)
                                    }
                                )
                            }
                        },
                        onUninstall = { packageName ->
                            installViewModel.requestUninstall(packageName)
                        },
                        onOpen = {
                            installViewModel.openApp()
                        },
                        onHomeClick = onBackClick,
                        onSaveClick = {
                            if (!state.isSaving) {
                                exportApkLauncher.launch(exportFileName)
                            }
                        },
                        isSaving = state.isSaving
                    )
                }

                PatcherState.FAILED -> {
                    PatchingFailed(
                        state = state,
                        onHomeClick = onBackClick
                    )
                }
            }
        }
    }
}
