/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.app.Activity
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.manager.InstallerPreferenceTokens
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.patcher.*
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.settings.advanced.NotificationPermissionDialog
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.screen.shared.MorpheDialog
import app.morphe.manager.ui.screen.shared.MorpheDialogButtonRow
import app.morphe.manager.ui.screen.shared.rememberAccessibilityEnabled
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.EventEffect
import app.morphe.manager.util.tag
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Patcher screen with progress tracking.
 * Shows patching progress, handles installation with pre-conflict detection, and provides export functionality.
 */
@Composable
fun PatcherScreen(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    usingMountInstall: Boolean,
    installViewModel: InstallViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    onBackgroundSpeedChange: (Float) -> Unit = {},
    onPatchingCompleted: () -> Unit = {},
    onStartTour: () -> Unit = {},
    onDeclineTour: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    val patcherSucceeded by patcherViewModel.patcherSucceeded.observeAsState(null)

    // Remember patcher state
    val state = rememberMorphePatcherState(patcherViewModel)
    val scope = rememberCoroutineScope()
    val miniGameState = remember { MiniGameState(prefs, scope) }

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
    val showSuccessScreen = patcherViewModel.showSuccessScreen

    LaunchedEffect(showSuccessScreen) {
        if (showSuccessScreen) miniGameState.pauseActiveGame()
    }

    // Skip the 1.5s tween on every progress tick when TalkBack is active so the main thread
    // isn't constantly busy interpolating and can serve accessibility events instead
    val reduceMotion = rememberAccessibilityEnabled()
    val displayProgressAnimate by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Drive background speed: ramps 1x→3x during patching, resets on completion/failure.
    // Uses a coroutine loop so speed tracks displayProgress in real time without recomposition churn
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == null) {
            // Exponential moving average to smooths sudden progress jumps
            var movingAverage = 0.0f
            // Lower factor has more abrupt animation changes
            val smoothingFactor = 0.25f
            // Patching in progress - poll displayProgress every 250ms (same cadence as progress loop)
            while (true) {
                movingAverage = (1 - smoothingFactor) * movingAverage +
                        smoothingFactor * displayProgress
                onBackgroundSpeedChange(1 + movingAverage)
                delay(250.milliseconds)
            }
        } else {
            // Patching finished - reset speed then fire completion effect
            onBackgroundSpeedChange(1f)
            if (patcherSucceeded == true && patcherViewModel.patchingCompletedInForeground) {
                delay(300.milliseconds) // small pause so speed resets before effect fires
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

    val autoInstallWithShizuku by prefs.autoInstallWithShizuku.getAsState()
    val primaryInstallerPref by prefs.installerPrimary.getAsState()
    val promptInstallerOnInstall by prefs.promptInstallerOnInstall.getAsState()

    // Auto-install: driven by ViewModel so it fires in the background even if the app is not
    // in the foreground when patching completes. UI-only guards checked here.
    LaunchedEffect(Unit) {
        patcherViewModel.autoInstallEvent.collect {
            if (usingMountInstall) return@collect
            if (installViewModel.installState !is InstallViewModel.InstallState.Ready) return@collect
            installViewModel.install(
                outputFile = outputFile,
                originalPackageName = patcherViewModel.packageName,
                onPersistApp = { pkg, type -> patcherViewModel.persistPatchedApp(pkg, type) }
            )
        }
    }

    // Progress animation logic: drives displayProgress and showSuccessScreen
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

            // When to stop using overcorrection of progress and always use the actual progress
            val maxOverCorrectPercentage = 0.97

            if (actualProgress >= maxOverCorrectPercentage) {
                displayProgress = actualProgress
            } else {
                // Overestimate the progress by about 1% per second, but decays to
                // adding smaller adjustments each second until the current step completes
                fun overEstimateProgressAdjustment(secondsElapsed: Double): Double {
                    // Sigmoid curve. Give larger correct soon after the step starts but then flattens off
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
            delay(250.milliseconds)
        }

        // Patching completed - ensure progress reaches 100%
        if (patcherSucceeded == true) {
            displayProgress = 1.0f
        }
    }

    val patchesProgress = patcherViewModel.patchesProgress
    val unknownErrorText = stringResource(R.string.patcher_unknown_error)

    // Monitor for patching errors (not installation errors)
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == false && !state.hasPatchingError) {
            state.hasPatchingError = true
            val steps = patcherViewModel.steps
            val failedStep = steps.firstOrNull { it.state == State.FAILED }
            state.errorMessage = failedStep?.message.orEmpty()
            state.errorInfo = patcherViewModel.buildErrorInfo()
            state.showErrorDialog = true
        }
    }

    BackHandler {
        if (patcherViewModel.isPatching) {
            // Show cancel dialog if patching is in progress
            state.showCancelDialog = true
        } else {
            // Allow normal back navigation if patching is complete or failed
            onBackClick()
        }
    }

    // Keep screen on during patching
    if (patcherViewModel.isPatching) {
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
    // Conflict is expected when patching from installed (non-root): handled via dialog instead of UI state
    val autoHandleConflict = patcherViewModel.patchedFromInstalledDevice && !usingMountInstall
    val isConflict by remember { derivedStateOf {
        installViewModel.installState is InstallViewModel.InstallState.Conflict && !autoHandleConflict
    } }
    val installedPackageName by remember { derivedStateOf { installViewModel.installedPackageName } }
    val conflictPackageName by remember { derivedStateOf { (installViewModel.installState as? InstallViewModel.InstallState.Conflict)?.packageName } }
    val errorMessage by remember { derivedStateOf { (installViewModel.installState as? InstallViewModel.InstallState.Error)?.message } }

    val showInstalledSourceConflictDialog = remember { mutableStateOf(false) }
    val shouldPromptTour by patcherViewModel.shouldPromptTour.collectAsStateWithLifecycle()

    LaunchedEffect(installState) {
        if (installState is InstallViewModel.InstallState.Installed) {
            patcherViewModel.triggerPostInstallPromptsIfNeeded()
        }
        if (installState is InstallViewModel.InstallState.Conflict && autoHandleConflict) {
            showInstalledSourceConflictDialog.value = true
        }
    }

    if (showInstalledSourceConflictDialog.value) {
        InstalledSourceConflictDialog(
            onUninstall = {
                showInstalledSourceConflictDialog.value = false
                conflictPackageName?.let { installViewModel.requestUninstall(it) }
            },
            onDismiss = {
                showInstalledSourceConflictDialog.value = false
                installViewModel.resetInstallState()
            }
        )
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

    // Tour prompt dialog shown after first successful install
    if (shouldPromptTour) {
        MorpheDialog(
            onDismissRequest = {
                patcherViewModel.consumeTourPrompt()
                onDeclineTour()
            },
            title = stringResource(R.string.tour_prompt_title),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.tour_prompt_confirm),
                    onPrimaryClick = {
                        patcherViewModel.consumeTourPrompt()
                        onStartTour()
                        onBackClick()
                    },
                    secondaryText = stringResource(R.string.skip),
                    onSecondaryClick = {
                        patcherViewModel.consumeTourPrompt()
                        onDeclineTour()
                        onBackClick()
                    }
                )
            }
        ) {
            Text(
                text = stringResource(R.string.tour_prompt_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
                patcherViewModel.cancelPatching()
                onBackClick()
            }
        )
    }

    // Storage permission pre-flight dialog.
    // Shown when a patch option points to an external path the app cannot read
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

    // Patcher version incompatibility pre-flight dialog.
    // Shown when a bundle's Patcher-Version is newer than what the manager ships
    patcherViewModel.incompatiblePatcherVersion?.let { state ->
        IncompatiblePatcherVersionDialog(
            bundleName = state.bundleName,
            requiredVersion = state.requiredVersion,
            onDismiss = {
                patcherViewModel.dismissIncompatiblePatcherVersion()
                onBackClick()
            }
        )
    }

    // Battery optimization pre-flight dialog.
    // Shown once when the app is not excluded from battery optimization
    if (patcherViewModel.batteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onResult = patcherViewModel::onBatteryOptimizationDialogResult
        )
    }

    // Error dialog
    if (state.showErrorDialog) {
        PatcherErrorDialog(
            errorMessage = state.effectiveErrorMessage.ifBlank { unknownErrorText },
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
                delay(1_500.milliseconds)
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
            onOpenShizuku = installerManager::openShizukuApp,
            autoInstallEnabled = autoInstallWithShizuku,
            onAutoInstallToggle = { enabled ->
                scope.launch { prefs.autoInstallWithShizuku.update(enabled) }
            },
            installerPromptEnabled = promptInstallerOnInstall
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
            transitionSpec = if (reduceMotion) {
                { EnterTransition.None togetherWith ExitTransition.None }
            } else {
                MorpheAnimations.fadeCrossfade(800)
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
                            miniGameState = miniGameState,
                            onCancelClick = { state.showCancelDialog = true },
                            onInstallClick = { patcherViewModel.showSuccess() },
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
                    val effectiveIsInstalling = isInstalling || (
                        autoInstallWithShizuku &&
                        primaryInstallerPref == InstallerPreferenceTokens.SHIZUKU &&
                        patcherSucceeded == true &&
                        !usingMountInstall &&
                        !promptInstallerOnInstall &&
                        installState is InstallViewModel.InstallState.Ready
                    )
                    PatchingSuccess(
                        isInstalling = effectiveIsInstalling,
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
                        onLogsClick = { patcherViewModel.hideSuccessScreen() },
                        onInstall = {
                            if (usingMountInstall) {
                                // Mount install
                                val inputVersion = patcherViewModel.version
                                    ?: patcherViewModel.currentSelectedApp.version
                                    ?: "unknown"
                                installViewModel.installMount(
                                    outputFile = outputFile,
                                    inputFile = patcherViewModel.inputFile,
                                    inputIsTemporary = patcherViewModel.inputFileIsDisposable,
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
