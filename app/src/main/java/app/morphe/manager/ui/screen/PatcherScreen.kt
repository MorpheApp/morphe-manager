/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.patcher.*
import app.morphe.manager.ui.screen.settings.advanced.NotificationPermissionDialog
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.EventEffect
import app.morphe.manager.util.tag
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Patcher screen with progress tracking.
 * Shows patching progress, handles installation with pre-conflict detection, and provides export functionality.
 */
@SuppressLint("LocalContextGetResourceValueCall", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatcherScreen(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    usingMountInstall: Boolean,
    installViewModel: InstallViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    onBackgroundSpeedChange: (Float) -> Unit = {},
    onPatchingCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    val patcherSucceeded by patcherViewModel.patcherSucceeded.observeAsState(null)

    // Remember patcher state
    val state = rememberMorphePatcherState(patcherViewModel)

    // Notification prompt: driven by ViewModel after successful export or install
    val shouldPromptNotification by patcherViewModel.shouldPromptNotification.collectAsStateWithLifecycle()
    val isSaving by patcherViewModel.isSaving.collectAsStateWithLifecycle()

    val hasGms = remember {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    // Animated progress with dual-mode animation
    var displayProgress by rememberSaveable { mutableFloatStateOf(patcherViewModel.progress) }
    val showLongStepWarning by patcherViewModel.showLongStepWarning.collectAsStateWithLifecycle()
    var showSuccessScreen by rememberSaveable { mutableStateOf(false) }

    val displayProgressAnimate by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Drive background speed: ramps 1x→3x during patching, resets on completion/failure.
    // Uses a coroutine loop so speed tracks displayProgress in real time without recomposition churn.
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == null) {
            // Exponential moving average to smooths sudden progress jumps.
            var movingAverage = 0.0f
            // Lower factor has more abrupt animation changes.
            val smoothingFactor = 0.25f
            // Patching in progress - poll displayProgress every 250ms (same cadence as progress loop)
            while (true) {
                movingAverage = (1 - smoothingFactor) * movingAverage +
                        smoothingFactor * displayProgress
                onBackgroundSpeedChange(1 + movingAverage)
                delay(250)
            }
        } else {
            // Patching finished - reset speed then fire completion effect
            onBackgroundSpeedChange(1f)
            if (patcherSucceeded == true) {
                delay(300) // small pause so speed resets before effect fires
                onPatchingCompleted()
                // Haptic feedback
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    // Restore speed when leaving the screen
    DisposableEffect(Unit) {
        onDispose { onBackgroundSpeedChange(1f) }
    }

    // Get output file from viewModel
    val outputFile = patcherViewModel.outputFile

    // Progress animation logic: drives displayProgress and showSuccessScreen.
    LaunchedEffect(patcherSucceeded) {
        var lastProgressUpdate = 0.0f
        var currentStepStartTime = System.currentTimeMillis()

        while (patcherSucceeded == null) {
            val now = System.currentTimeMillis()

            val actualProgress = patcherViewModel.progress
            if (lastProgressUpdate != actualProgress) {
                lastProgressUpdate = actualProgress // Progress updated
                currentStepStartTime = now
                if (Log.isLoggable(tag, Log.DEBUG)) {
                    Log.d(tag, "Real progress update: ${(actualProgress * 1000).toInt() / 10.0f}%")
                }
            }

            // When to stop using overcorrection of progress and always use the actual progress.
            val maxOverCorrectPercentage = 0.97

            if (actualProgress >= maxOverCorrectPercentage) {
                displayProgress = actualProgress
            } else {
                // Overestimate the progress by about 1% per second, but decays to
                // adding smaller adjustments each second until the current step completes
                fun overEstimateProgressAdjustment(secondsElapsed: Double): Double {
                    // Sigmoid curve. Give larger correct soon after the step starts but then flattens off.
                    val maximumValue = 25.0 // Up to 25% over correct
                    val timeConstant = 50.0 // Larger value = longer time until plateau
                    return maximumValue * (1 - exp(-secondsElapsed / timeConstant))
                }

                val secondsSinceStepStarted = (now - currentStepStartTime) / 1000.0
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
            state.errorInfo = patcherViewModel.buildErrorInfo()
            state.showErrorDialog = true
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

    val exportApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APK_MIMETYPE)
    ) { uri ->
        uri?.let { patcherViewModel.export(it) }
    }

    // Trigger notification prompt after first successful install
    val installState = installViewModel.installState
    val isInstalling by remember { derivedStateOf { installViewModel.installState is InstallViewModel.InstallState.Installing } }
    val isInstalled by remember { derivedStateOf { installViewModel.installState is InstallViewModel.InstallState.Installed } }
    val isError by remember { derivedStateOf { installViewModel.installState is InstallViewModel.InstallState.Error } }
    val isConflict by remember { derivedStateOf { installViewModel.installState is InstallViewModel.InstallState.Conflict } }
    val installedPackageName by remember { derivedStateOf { installViewModel.installedPackageName } }
    val conflictPackageName by remember { derivedStateOf { (installViewModel.installState as? InstallViewModel.InstallState.Conflict)?.packageName } }
    val errorMessage by remember { derivedStateOf { (installViewModel.installState as? InstallViewModel.InstallState.Error)?.message } }

    LaunchedEffect(installState) {
        if (installState is InstallViewModel.InstallState.Installed) {
            patcherViewModel.triggerNotificationPromptIfNeeded()
        }
    }

    // Notification prompt dialog
    if (shouldPromptNotification) {
        NotificationPermissionDialog(
            title = stringResource(R.string.notification_post_patch_dialog_title),
            onDismissRequest = {
                patcherViewModel.onNotificationPermissionResult(
                    granted = false,
                    hasGms = hasGms
                )
                patcherViewModel.consumeNotificationPrompt()
            },
            onPermissionResult = { granted ->
                patcherViewModel.onNotificationPermissionResult(
                    granted = granted,
                    hasGms = hasGms
                )
                patcherViewModel.consumeNotificationPrompt()
            }
        )
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
                patcherViewModel.cancelPatching()
                onBackClick()
            }
        )
    }

    // Storage permission pre-flight dialog.
    // Shown when a patch option points to an external path the app cannot read.
    patcherViewModel.inaccessibleOptionPaths?.let { errorState ->
        StoragePermissionDialog(
            failures = errorState.failures,
            onRetryAfterPermission = patcherViewModel::retryAfterPermission,
            onDismiss = {
                patcherViewModel.dismissInaccessibleOptionPathsError()
                onBackClick()
            }
        )
    }

    // Error dialog
    if (state.showErrorDialog) {
        PatcherErrorDialog(
            errorMessage = state.effectiveErrorMessage,
            errorInfo = state.errorInfo,
            onDismiss = { state.showErrorDialog = false }
        )
    }

    // Installer selection dialog for patcher screen
    if (installViewModel.showInstallerSelectionDialog) {
        val installerManager: InstallerManager = koinInject()
        val primaryPreference by prefs.installerPrimary.getAsState()
        val primaryToken = remember(primaryPreference) {
            installerManager.parseToken(primaryPreference)
        }

        val installTarget = InstallerManager.InstallTarget.PATCHER

        // Installer entries with periodic updates
        var options by remember(primaryToken) {
            mutableStateOf(
                installerManager.ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false),
                    primaryToken,
                    installTarget
                )
            )
        }

        // Periodically update installer list for availability changes
        LaunchedEffect(installTarget, primaryToken) {
            while (isActive) {
                options = installerManager.ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false),
                    primaryToken,
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
        val useExpertMode by prefs.useExpertMode.getAsState()

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
                    if (useExpertMode) {
                        ExpertPatchingInProgress(
                            progress = displayProgressAnimate,
                            patchesProgress = patchesProgress,
                            patcherViewModel = patcherViewModel,
                            patcherSucceeded = patcherSucceeded,
                            onCancelClick = { state.showCancelDialog = true },
                            onInstallClick = { showSuccessScreen = true },
                            onHomeClick = onBackClick
                        )
                    } else {
                        SimplePatchingInProgress(
                            progress = displayProgressAnimate,
                            patchesProgress = patchesProgress,
                            patcherViewModel = patcherViewModel,
                            showLongStepWarning = showLongStepWarning,
                            onCancelClick = { state.showCancelDialog = true },
                            onHomeClick = onBackClick
                        )
                    }
                }

                PatcherState.SUCCESS -> {
                    PatchingSuccess(
                        isInstalling = isInstalling,
                        isInstalled = isInstalled,
                        isError = isError,
                        isConflict = isConflict,
                        installedPackageName = installedPackageName,
                        conflictPackageName = conflictPackageName,
                        errorMessage = errorMessage,
                        installerUnavailableDialog = installViewModel.installerUnavailableDialog,
                        onOpenInstallerApp = installViewModel::openInstallerApp,
                        onRetryInstaller = installViewModel::retryWithPreferredInstaller,
                        onUseFallbackInstaller = installViewModel::proceedWithFallbackInstaller,
                        onDismissInstallerDialog = installViewModel::dismissInstallerUnavailableDialog,
                        usingMountInstall = usingMountInstall,
                        isExpertMode = useExpertMode,
                        onLogsClick = { showSuccessScreen = false },
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
                                // Regular installation with pre-conflict check
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
                            if (!isSaving) {
                                exportApkLauncher.launch(patcherViewModel.exportFileName)
                            }
                        },
                        isSaving = isSaving
                    )
                }

                PatcherState.FAILED -> {
                    PatchingFailed(
                        onHomeClick = onBackClick,
                        onErrorClick = { state.showErrorDialog = true }
                    )
                }
            }
        }
    }
}
