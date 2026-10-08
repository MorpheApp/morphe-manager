/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.patcher.patch.installerTypeFor
import app.morphe.manager.ui.model.RenameWarning
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.home.AppLinksDialog
import app.morphe.manager.ui.screen.patcher.*
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.settings.system.InstallerSelectionDialog
import app.morphe.manager.ui.screen.settings.system.InstallerUnavailableDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.EventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/** An install held back until the user accepts that it lands beside the app rather than on it. */
private data class HeldInstall(
    val warning: RenameWarning,
    val start: () -> Unit
)

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
    onDeclineTour: () -> Unit = {},
    onChangePatches: () -> Unit = {}
) {
    // Worn by everything the screen shows, its dialogs included, down to the install button
    ProvideAccent(rememberAppColor(patcherViewModel.packageName)) {
        PatcherScreenContent(
            onBackClick = onBackClick,
            patcherViewModel = patcherViewModel,
            usingMountInstall = usingMountInstall,
            installViewModel = installViewModel,
            prefs = prefs,
            onBackgroundSpeedChange = onBackgroundSpeedChange,
            onPatchingCompleted = onPatchingCompleted,
            onStartTour = onStartTour,
            onDeclineTour = onDeclineTour,
            onChangePatches = onChangePatches
        )
    }
}

@Composable
private fun PatcherScreenContent(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    usingMountInstall: Boolean,
    installViewModel: InstallViewModel,
    prefs: PreferencesManager,
    onBackgroundSpeedChange: (Float) -> Unit,
    onPatchingCompleted: () -> Unit,
    onStartTour: () -> Unit,
    onDeclineTour: () -> Unit,
    onChangePatches: () -> Unit
) {
    val view = LocalView.current

    val patcherSucceeded by patcherViewModel.patcherSucceeded.collectAsStateWithLifecycle()

    // Remember patcher state
    val state = rememberPatcherScreenState(patcherViewModel)
    val scope = rememberCoroutineScope()
    val miniGameState = remember { MiniGameState(prefs, scope) }

    val isSaving by patcherViewModel.isSaving.collectAsStateWithLifecycle()

    val showLongStepWarning by patcherViewModel.showLongStepWarning.collectAsStateWithLifecycle()
    val showSuccessScreen = patcherViewModel.showSuccessScreen

    LaunchedEffect(showSuccessScreen) {
        if (showSuccessScreen) miniGameState.pauseActiveGame()
    }

    // A run that finishes mid-round waits for the player instead of taking the screen away. The
    // action bar below the game turns into an install button meanwhile, so the way on is in reach
    LaunchedEffect(miniGameState) {
        snapshotFlow { miniGameState.isPlaying }
            .collect { patcherViewModel.deferSuccessScreen(it) }
    }

    val reduceMotion = rememberAccessibilityEnabled()
    val displayProgress = rememberDisplayedPatchProgress(
        progress = { patcherViewModel.progress },
        succeeded = patcherSucceeded
    )

    PatchingBackgroundSpeedEffect(
        active = patcherSucceeded == null,
        progress = { displayProgress.target },
        onSpeedChange = onBackgroundSpeedChange
    )

    // Patching finished - the speed resets first, then the completion effect fires
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == true && patcherViewModel.patchingCompletedInForeground) {
            delay(300.milliseconds) // small pause so speed resets before effect fires
            onPatchingCompleted()
            // Haptic feedback
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // Get output file from viewModel
    val outputFile = patcherViewModel.outputFile

    val autoInstallAfterPatching by prefs.autoInstallAfterPatching.getAsState()
    val autoUninstallWithShizuku by prefs.autoUninstallWithShizuku.getAsState()
    val promptInstallerOnInstall by prefs.promptInstallerOnInstall.getAsState()

    // A build that answers to a package name of its own installs beside the app instead of
    // updating it, so every install path waits here until the user has been told once
    var renameConfirmed by rememberSaveable { mutableStateOf(false) }
    var renameDeclined by remember { mutableStateOf(false) }
    var heldInstall by remember { mutableStateOf<HeldInstall?>(null) }

    suspend fun startInstall(install: () -> Unit) {
        // A fresh attempt supersedes whatever the user answered to the previous one
        renameDeclined = false
        val warning = if (renameConfirmed) null else patcherViewModel.renameWarning()
        if (warning == null) {
            install()
        } else {
            heldInstall = HeldInstall(warning, install)
        }
    }

    // What the success screen's button starts: a mount, or an install that checks for a rename first
    fun installPatchedApp() {
        if (usingMountInstall) {
            installViewModel.installMount(
                outputFile = outputFile,
                inputFile = patcherViewModel.inputFile,
                inputIsTemporary = patcherViewModel.inputFileIsDisposable,
                packageName = patcherViewModel.packageName,
                onPersistApp = patcherViewModel::persistPatchedApp
            )
        } else {
            scope.launch {
                startInstall {
                    installViewModel.install(
                        outputFile = outputFile,
                        originalPackageName = patcherViewModel.packageName,
                        onPersistApp = patcherViewModel::persistPatchedApp
                    )
                }
            }
        }
    }

    // Auto-install: driven by ViewModel so it fires in the background even if the app is not
    // in the foreground when patching completes. UI-only guards checked here.
    LaunchedEffect(Unit) {
        patcherViewModel.autoInstallEvent.collect {
            if (usingMountInstall) return@collect
            if (installViewModel.installState !is InstallViewModel.InstallState.Ready) return@collect
            startInstall {
                installViewModel.install(
                    outputFile = outputFile,
                    originalPackageName = patcherViewModel.packageName,
                    onPersistApp = patcherViewModel::persistPatchedApp,
                    autoUninstallOnConflict = true
                )
            }
            // The installer owns the state from here, and an attempt that ends in nothing must
            // not leave the screen claiming an install forever
            patcherViewModel.autoInstallHandedOff()
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
            state.shownFailure = PatcherFailure.PATCHING
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

    KeepScreenOn(patcherViewModel.isPatching)

    val exportApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APK_MIMETYPE)
    ) { uri ->
        uri?.let { patcherViewModel.export(it) }
    }

    // Post-patch prompts follow a successful install
    val installState = installViewModel.installState
    val isInstalling by remember { derivedStateOf { installViewModel.installState is InstallViewModel.InstallState.Installing } }
    // Conflict is expected when patching from installed (non-root): handled via dialog instead of UI state
    val autoHandleConflict = patcherViewModel.patchedFromInstalledDevice && !usingMountInstall
    // The installer reports the app it installed even after the state has moved on
    val installedPackageName by remember { derivedStateOf { installViewModel.installedPackageName } }
    val targetInstalledPackage = installedPackageName ?: patcherViewModel.packageName

    // Re-signing drops the verified web links of the original publisher, worth a word once installed
    val appLinksManager: AppLinksManager = koinInject()
    var appLinksStatus by remember { mutableStateOf<AppLinksStatus?>(null) }
    var showAppLinksDialog by remember { mutableStateOf(false) }
    val ignoredAppLinksPackages by prefs.ignoredAppLinksPackages.getAsState()
    val linksOpenInBrowser = appLinksStatus?.opensInBrowser == true &&
            targetInstalledPackage !in ignoredAppLinksPackages

    val showInstalledSourceConflictDialog = remember { mutableStateOf(false) }

    // Named on the success screen so the finished app says what the install method left out
    var excludedPatches by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(usingMountInstall) {
        excludedPatches = patcherViewModel.unavailablePatchNames(installerTypeFor(usingMountInstall))
    }

    LaunchedEffect(installState, targetInstalledPackage) {
        if (installState is InstallViewModel.InstallState.Installed) {
            patcherViewModel.postPatchPrompts.trigger()
            appLinksStatus = appLinksManager.getStatus(targetInstalledPackage)
        }
        if (installState is InstallViewModel.InstallState.Conflict && autoHandleConflict) {
            showInstalledSourceConflictDialog.value = true
        }
    }

    val shownAppLinksStatus = appLinksStatus
    if (showAppLinksDialog && shownAppLinksStatus?.hasSupportedLinks == true) {
        AppLinksDialog(
            appLabel = patcherViewModel.exportMetadata?.appName ?: targetInstalledPackage,
            appInfo = null,
            accentColor = LocalAccent.current,
            packageName = targetInstalledPackage,
            status = shownAppLinksStatus,
            onRefresh = { appLinksStatus = appLinksManager.getStatus(targetInstalledPackage) },
            onDismiss = { showAppLinksDialog = false }
        )
    }

    if (showInstalledSourceConflictDialog.value) {
        val conflict = installState as? InstallViewModel.InstallState.Conflict

        SignatureConflictDialog(
            title = stringResource(R.string.patcher_installed_conflict_title),
            message = stringResource(R.string.patcher_installed_conflict_body),
            onUninstall = {
                showInstalledSourceConflictDialog.value = false
                conflict?.let {
                    installViewModel.requestUninstall(it.packageName, installAfterUninstall = true)
                }
            },
            onDismiss = {
                showInstalledSourceConflictDialog.value = false
                installViewModel.resetInstallState()
            },
            onIgnore = if (conflict?.canIgnoreSignatureMismatch == true) {
                {
                    showInstalledSourceConflictDialog.value = false
                    installViewModel.installIgnoringSignatureMismatch()
                }
            } else {
                null
            }
        )
    }

    PostPatchPromptDialogs(
        prompts = patcherViewModel.postPatchPrompts,
        onStartTour = onStartTour,
        onDeclineTour = onDeclineTour,
        onLeave = onBackClick
    )

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
        ConfirmDialog(
            title = title,
            message = stringResource(R.string.plugin_activity_dialog_body),
            primaryText = stringResource(R.string.continue_),
            isPrimaryDestructive = false,
            onConfirm = patcherViewModel::allowInteraction,
            onDismiss = patcherViewModel::rejectInteraction
        )
    }

    // Cancel patching confirmation dialog
    if (state.showCancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.patcher_stop_confirm_title),
            message = stringResource(R.string.patcher_stop_confirm_description),
            primaryText = stringResource(R.string.yes),
            secondaryText = stringResource(R.string.no),
            onConfirm = {
                state.showCancelDialog = false
                patcherViewModel.cancelPatching()
                onBackClick()
            },
            onDismiss = { state.showCancelDialog = false }
        )
    }

    // Missing patches pre-flight dialog
    // Shown when the saved selection names patches the sources no longer offer
    patcherViewModel.missingPatchWarning?.let { warning ->
        MissingPatchesDialog(
            patchNames = warning.patchNames,
            onContinue = patcherViewModel::continueWithoutMissingPatches,
            onDismiss = {
                patcherViewModel.dismissMissingPatchWarning()
                onBackClick()
            }
        )
    }

    // Option path pre-flight dialog
    // Shown when a patch option points at a path that is gone or cannot be read
    patcherViewModel.inaccessibleOptionPaths?.let { errorState ->
        UnusableOptionPathsDialog(
            failures = errorState.failures,
            onRetryAfterPermission = patcherViewModel::retryAfterPermission,
            canClearPaths = errorState.canClear,
            onClearPaths = patcherViewModel::clearInaccessibleOptionPaths,
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

    // Memory limit dialog, shown after the system killed the patcher process.
    // Waits for the error dialog to close: that one explains the failure this one offers a fix for
    if (state.shownFailure == null) {
        patcherViewModel.memoryAdjustmentDialog?.let { dialogState ->
            MemoryAdjustmentDialog(
                currentLimit = dialogState.currentLimit,
                suggestedLimit = dialogState.suggestedLimit,
                canAdjust = dialogState.canAdjust,
                finished = dialogState.finished,
                onApply = patcherViewModel::applyMemoryAdjustment,
                onDismiss = patcherViewModel::dismissMemoryAdjustment
            )
        }
    }

    // Where the finished APK will actually install, when that is not what the run was aimed at
    heldInstall?.let { held ->
        RenameWarningDialog(
            warning = held.warning,
            onContinue = {
                renameConfirmed = true
                heldInstall = null
                held.start()
            },
            onDismiss = {
                renameDeclined = true
                heldInstall = null
            }
        )
    }

    // Error dialog, for a failed run and a failed install alike
    state.shownFailure?.let { failure ->
        val errorMessage = when (failure) {
            PatcherFailure.PATCHING -> state.effectiveErrorMessage
            PatcherFailure.INSTALL -> (installState as? InstallViewModel.InstallState.Error)?.message.orEmpty()
        }
        PatcherErrorDialog(
            title = stringResource(
                when (failure) {
                    PatcherFailure.PATCHING -> R.string.patcher_failed_dialog_title
                    PatcherFailure.INSTALL -> R.string.patcher_install_error_title
                }
            ),
            errorMessage = errorMessage.ifBlank { unknownErrorText },
            errorInfo = state.errorInfo,
            onDismiss = { state.shownFailure = null }
        )
    }

    installViewModel.installerUnavailableDialog?.let { unavailable ->
        InstallerUnavailableDialog(
            state = unavailable,
            onOpenApp = installViewModel::openInstallerApp,
            onRetry = installViewModel::retryWithPreferredInstaller,
            onUseFallback = installViewModel::proceedWithFallbackInstaller,
            onDismiss = installViewModel::dismissInstallerUnavailableDialog
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
        val selectedInstallerToken = remember(primaryToken) {
            if (primaryToken == InstallerManager.Token.AutoSaved) {
                InstallerManager.Token.Internal
            } else {
                primaryToken
            }
        }

        // Installer entries with periodic updates
        var options by remember(selectedInstallerToken) {
            mutableStateOf(
                installerManager.ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false)
                        .filterNot { it.token == InstallerManager.Token.AutoSaved },
                    selectedInstallerToken,
                    installTarget
                )
            )
        }

        // Periodically update installer list for availability changes
        LaunchedEffect(installTarget, selectedInstallerToken) {
            while (isActive) {
                options = installerManager.ensureValidEntries(
                    installerManager.listEntries(installTarget, includeNone = false)
                        .filterNot { it.token == InstallerManager.Token.AutoSaved },
                    selectedInstallerToken,
                    installTarget
                )
                delay(1_500.milliseconds)
            }
        }

        InstallerSelectionDialog(
            options = options,
            selected = selectedInstallerToken,
            onDismiss = installViewModel::dismissInstallerSelectionDialog,
            onConfirm = { selectedToken ->
                installViewModel.proceedWithSelectedInstaller(selectedToken)
            },
            onOpenShizuku = installerManager::openShizukuApp,
            shizukuStatusProvider = {
                installerManager.shizukuStatus(InstallerManager.InstallTarget.PATCHER)
            },
            onRequestShizukuPermission = installerManager::requestShizukuPermission,
            autoInstallEnabled = autoInstallAfterPatching,
            onAutoInstallToggle = { enabled ->
                scope.launch { prefs.autoInstallAfterPatching.update(enabled) }
            },
            autoUninstallEnabled = autoUninstallWithShizuku,
            onAutoUninstallToggle = { enabled ->
                scope.launch { prefs.autoUninstallWithShizuku.update(enabled) }
            },
            installerPromptEnabled = promptInstallerOnInstall
        )
    }

    // Named on the success screen, read once since the run's selection no longer changes
    val patchSources by produceState(emptyList(), patcherViewModel) {
        value = patcherViewModel.collectSelectedBundleMetadata()
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val useExpertMode by prefs.useExpertMode.getAsState()

        // Retired for good once the user has taken the way back it points at
        val backToGameHintSeen by prefs.backToGameHintSeen.getAsState()
        val showBackToGameHint = useExpertMode && miniGameState.hasOpenGame && !backToGameHintSeen

        AnimatedContent(
            targetState = if (showSuccessScreen) state.currentPatcherState else PatcherState.IN_PROGRESS,
            transitionSpec = if (reduceMotion) {
                { EnterTransition.None togetherWith ExitTransition.None }
            } else {
                Animations.fadeCrossfade(800)
            },
            label = "patcher_state_animation"
        ) { patcherState ->
            when (patcherState) {
                PatcherState.IN_PROGRESS -> {
                    if (useExpertMode) {
                        ExpertPatchingInProgress(
                            progress = { displayProgress.value },
                            patchesProgress = patchesProgress,
                            patchProgress = patcherViewModel.patchRun,
                            packageName = patcherViewModel.packageName,
                            patcherSucceeded = patcherSucceeded,
                            miniGameState = miniGameState,
                            onCancelClick = { state.showCancelDialog = true },
                            onInstallClick = { patcherViewModel.showSuccess() },
                            onHomeClick = onBackClick
                        )
                    } else {
                        SimplePatchingInProgress(
                            progress = { displayProgress.value },
                            patchesProgress = patchesProgress,
                            patchProgress = patcherViewModel.patchRun,
                            packageName = patcherViewModel.packageName,
                            showLongStepWarning = showLongStepWarning,
                            onCancelClick = { state.showCancelDialog = true }
                        )
                    }
                }

                PatcherState.SUCCESS -> {
                    val effectiveIsInstalling = isInstalling || (
                            patcherViewModel.autoInstallPending &&
                                    patcherSucceeded == true &&
                                    !usingMountInstall &&
                                    installState is InstallViewModel.InstallState.Ready &&
                                    // Auto-install stops at the rename warning, so the screen must
                                    // not go on claiming install the user has yet to allow
                                    heldInstall == null && !renameDeclined
                            )
                    // The state the screen is drawn from answers two things the installer's own
                    // does not: an auto-install is under way before it is reported, and a conflict
                    // this run resolves by dialog is not a screen state at all
                    val shownInstallState = when {
                        effectiveIsInstalling -> installState as? InstallViewModel.InstallState.Installing
                            ?: InstallViewModel.InstallState.Installing()
                        installState is InstallViewModel.InstallState.Conflict && autoHandleConflict ->
                            InstallViewModel.InstallState.Ready
                        else -> installState
                    }

                    PatchingSuccess(
                        packageName = patcherViewModel.packageName,
                        version = patcherViewModel.version,
                        patchCount = patcherViewModel.patchCount,
                        sources = patchSources,
                        installState = shownInstallState,
                        installedPackageName = installedPackageName,
                        usingMountInstall = usingMountInstall,
                        excludedPatches = excludedPatches,
                        isExpertMode = useExpertMode,
                        showBackToGameHint = showBackToGameHint,
                        onConfigureAppLinks = { showAppLinksDialog = true }.takeIf { linksOpenInBrowser },
                        onLogsClick = {
                            // Only the hint that was actually on screen counts as found
                            if (showBackToGameHint) {
                                scope.launch { prefs.backToGameHintSeen.update(true) }
                            }
                            patcherViewModel.hideSuccessScreen()
                        },
                        onInstall = ::installPatchedApp,
                        onUninstall = { packageName ->
                            installViewModel.requestUninstall(packageName, installAfterUninstall = true)
                        },
                        onIgnoreSignatureMismatch = installViewModel::installIgnoringSignatureMismatch,
                        onOpen = {
                            installViewModel.openApp()
                        },
                        onShowInstallError = {
                            scope.launch {
                                // A run that patched fine has not collected these yet
                                if (state.errorInfo == null) state.errorInfo = patcherViewModel.buildErrorInfo()
                                state.shownFailure = PatcherFailure.INSTALL
                            }
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
                        packageName = patcherViewModel.packageName,
                        version = patcherViewModel.version,
                        patchCount = patcherViewModel.patchCount,
                        sources = patchSources,
                        errorMessage = state.errorMessage,
                        onHomeClick = onBackClick,
                        onErrorClick = { state.shownFailure = PatcherFailure.PATCHING },
                        // Simple mode keeps no selection of its own to return to
                        onChangePatchesClick = onChangePatches.takeIf { useExpertMode }
                    )
                }
            }
        }
    }
}
