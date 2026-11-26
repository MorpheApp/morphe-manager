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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.patcher.patch.installerTypeFor
import app.morphe.manager.ui.screen.home.AppLinksDialog
import app.morphe.manager.ui.screen.patcher.*
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.EventEffect
import app.morphe.manager.util.rememberDocumentSaver
import app.morphe.manager.util.shareApks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Patcher screen with progress tracking.
 * Shows patching progress, handles installation with pre-conflict detection, and provides export functionality.
 */
@Composable
fun PatcherScreen(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    installViewModel: InstallViewModel,
    prefs: PreferencesManager = koinInject(),
    onBackgroundSpeedChange: (Float) -> Unit = {},
    onPatchingCompleted: () -> Unit = {},
    onStartTour: () -> Unit = {},
    onDeclineTour: () -> Unit = {},
    onChangePatches: () -> Unit = {},
    onOpenGames: (() -> Unit)? = null
) {
    // Worn by everything the screen shows, its dialogs included, down to the install button
    ProvideAccent(rememberAppColor(patcherViewModel.packageName)) {
        PatcherScreenContent(
            onBackClick = onBackClick,
            patcherViewModel = patcherViewModel,
            installViewModel = installViewModel,
            prefs = prefs,
            onBackgroundSpeedChange = onBackgroundSpeedChange,
            onPatchingCompleted = onPatchingCompleted,
            onStartTour = onStartTour,
            onDeclineTour = onDeclineTour,
            onChangePatches = onChangePatches,
            onOpenGames = onOpenGames
        )
    }
}

@Composable
private fun PatcherScreenContent(
    onBackClick: () -> Unit,
    patcherViewModel: PatcherViewModel,
    installViewModel: InstallViewModel,
    prefs: PreferencesManager,
    onBackgroundSpeedChange: (Float) -> Unit,
    onPatchingCompleted: () -> Unit,
    onStartTour: () -> Unit,
    onDeclineTour: () -> Unit,
    onChangePatches: () -> Unit,
    onOpenGames: (() -> Unit)?
) {
    val view = LocalView.current
    val context = LocalContext.current
    val usingMountInstall = patcherViewModel.usingMountInstall

    val patcherSucceeded by patcherViewModel.patcherSucceeded.collectAsStateWithLifecycle()

    // The error dialog open, if any, kept through a rotation or a font change
    var shownFailure by rememberSaveable { mutableStateOf<PatcherFailure?>(null) }
    var errorInfo by remember { mutableStateOf<PatcherErrorInfo?>(null) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isSaving by patcherViewModel.isSaving.collectAsStateWithLifecycle()

    val showLongStepWarning by patcherViewModel.showLongStepWarning.collectAsStateWithLifecycle()
    val showSuccessScreen = patcherViewModel.showSuccessScreen

    // Whatever an install has to ask waits for the success screen, the one place an install
    // started by the user comes from as well, rather than popping up over the logs
    val installPromptsShown = showSuccessScreen

    val reduceMotion = rememberAccessibilityEnabled()
    val displayProgress = rememberDisplayedPatchProgress(
        run = patcherViewModel.patchRun,
        succeeded = patcherSucceeded
    )

    PatchingBackgroundSpeedEffect(
        active = patcherSucceeded == null,
        progress = { displayProgress.target },
        onSpeedChange = onBackgroundSpeedChange
    )

    // The buzz marks the moment the run ends, so a screen composed again after it, by a rotation
    // or on the way back from the games, stays quiet while the completion effect is restored
    var completionFelt by rememberSaveable { mutableStateOf(false) }

    // Patching finished - the speed resets first, then the completion effect fires
    LaunchedEffect(patcherSucceeded) {
        if (patcherSucceeded == true && patcherViewModel.patchingCompletedInForeground) {
            delay(300.milliseconds) // small pause so speed resets before effect fires
            onPatchingCompleted()
            // Haptic feedback
            if (!completionFelt) {
                completionFelt = true
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    val patchesProgress = patcherViewModel.patchesProgress
    val unknownErrorText = stringResource(R.string.patcher_unknown_error)

    // A failed run opens the error dialog once. The event is not heard again by a recreated
    // screen, so a rotation or a font change leaves a dismissed dialog closed
    EventEffect(flow = patcherViewModel.patchingFailedEvent) {
        errorInfo = patcherViewModel.buildErrorInfo()
        shownFailure = PatcherFailure.PATCHING
    }

    // The diagnostics are not kept through a recreated screen, so a dialog left open reads them again
    LaunchedEffect(shownFailure) {
        if (shownFailure != null && errorInfo == null) errorInfo = patcherViewModel.buildErrorInfo()
    }

    BackHandler {
        if (patcherViewModel.isPatching) {
            // Show cancel dialog if patching is in progress
            showCancelDialog = true
        } else {
            // Allow normal back navigation if patching is complete or failed
            onBackClick()
        }
    }

    KeepScreenOn(patcherViewModel.isPatching)

    val exportApk = rememberDocumentSaver(APK_MIMETYPE, patcherViewModel::export)

    // Post-patch prompts follow a successful install
    val installState = installViewModel.installState
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

    DialogHost(target = appLinksStatus?.takeIf { showAppLinksDialog && it.hasSupportedLinks }) { shownAppLinksStatus ->
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

    DialogHost(visible = showInstalledSourceConflictDialog.value && installPromptsShown) {
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

    if (installPromptsShown) {
        PostPatchPromptDialogs(
            prompts = patcherViewModel.postPatchPrompts,
            onStartTour = onStartTour,
            onDeclineTour = onDeclineTour,
            onLeave = onBackClick
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
    DialogHost(target = patcherViewModel.activityPromptDialog) { title ->
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
    DialogHost(visible = showCancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.patcher_stop_title),
            message = stringResource(R.string.patcher_stop_discards),
            primaryText = stringResource(R.string.stop),
            // Cancel would read as cancelling the patching itself
            secondaryText = stringResource(R.string.patcher_keep_patching),
            onConfirm = {
                showCancelDialog = false
                patcherViewModel.cancelPatching()
                onBackClick()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    // Missing patches pre-flight dialog
    // Shown when the saved selection names patches the sources no longer offer
    DialogHost(target = patcherViewModel.missingPatchWarning) { warning ->
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
    DialogHost(target = patcherViewModel.inaccessibleOptionPaths) { failures ->
        UnusableOptionPathsDialog(
            failures = failures,
            onRetryAfterPermission = patcherViewModel::retryAfterPermission,
            onClearPaths = patcherViewModel::clearInaccessibleOptionPaths,
            onDismiss = {
                patcherViewModel.dismissInaccessibleOptionPathsError()
                onBackClick()
            }
        )
    }

    // Patcher version incompatibility pre-flight dialog.
    // Shown when a bundle's Patcher-Version is newer than what the manager ships
    DialogHost(target = patcherViewModel.incompatiblePatcherVersion) { state ->
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
    DialogHost(visible = patcherViewModel.batteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onResult = patcherViewModel::onBatteryOptimizationDialogResult
        )
    }

    // Where the finished APK will actually install, when that is not what the run was aimed at.
    // A build that answers to a package name of its own installs beside the app instead of
    // updating it, so every install path waits on this until the user has been told once
    DialogHost(target = patcherViewModel.heldInstallWarning?.takeIf { installPromptsShown }) { warning ->
        RenameWarningDialog(
            warning = warning,
            onContinue = patcherViewModel::confirmRename,
            onDismiss = patcherViewModel::declineRename
        )
    }

    // Error dialog, for a failed run and a failed install alike
    DialogHost(target = shownFailure) { failure ->
        val errorMessage = when (failure) {
            PatcherFailure.PATCHING -> patcherViewModel.failureDetails
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
            errorInfo = errorInfo,
            onDismiss = { shownFailure = null }
        )
    }

    if (installPromptsShown) InstallerFlowDialogs(installViewModel)

    // Named on the success screen, read once since the run's selection no longer changes
    val patchSources by produceState(emptyList(), patcherViewModel) {
        value = patcherViewModel.collectSelectedBundleMetadata()
    }
    val patchedAppSummary = PatchedAppSummary(
        // The build as it stands until it is installed, then the app it became
        packageName = targetInstalledPackage,
        packageInfo = patcherViewModel.patchedPackageInfo.takeIf { installedPackageName == null },
        version = patcherViewModel.version,
        patchCount = patcherViewModel.patchCount,
        sources = patchSources
    )

    val autoInstallUnderWay = patcherViewModel.autoInstallPending &&
            patcherSucceeded == true &&
            installState is InstallViewModel.InstallState.Ready &&
            // Auto-install stops at the rename warning, so the screen must
            // not go on claiming install the user has yet to allow
            patcherViewModel.heldInstallWarning == null && !patcherViewModel.renameDeclined
    // The state the result screen and the way back to it are drawn from answers two things the
    // installer's own does not: an auto-install is under way before it is reported, and a
    // conflict this run resolves by dialog is not a screen state at all
    val shownInstallState = when {
        autoInstallUnderWay -> InstallViewModel.InstallState.Installing()
        installState is InstallViewModel.InstallState.Conflict && autoHandleConflict ->
            InstallViewModel.InstallState.Ready
        else -> installState
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val useExpertMode by prefs.useExpertMode.getAsState()

        AnimatedContent(
            targetState = if (showSuccessScreen) PatcherState.of(patcherSucceeded) else PatcherState.IN_PROGRESS,
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
                            onCancelClick = { showCancelDialog = true },
                            resultButton = resultButton(shownInstallState, installedPackageName, usingMountInstall),
                            onResultClick = { patcherViewModel.showSuccess() },
                            onGamesClick = onOpenGames
                        )
                    } else {
                        SimplePatchingInProgress(
                            progress = { displayProgress.value },
                            patchesProgress = patchesProgress,
                            patchProgress = patcherViewModel.patchRun,
                            packageName = patcherViewModel.packageName,
                            showLongStepWarning = showLongStepWarning,
                            onCancelClick = { showCancelDialog = true },
                            onGamesClick = onOpenGames
                        )
                    }
                }

                PatcherState.SUCCESS -> {
                    PatchingSuccess(
                        summary = patchedAppSummary,
                        installState = shownInstallState,
                        installedPackageName = installedPackageName,
                        usingMountInstall = usingMountInstall,
                        installActions = InstallResultActions(
                            onInstall = patcherViewModel::installPatchedApp,
                            onUninstall = { packageName ->
                                installViewModel.requestUninstall(packageName, installAfterUninstall = true)
                            },
                            onIgnoreSignatureMismatch = installViewModel::installIgnoringSignatureMismatch,
                            onOpen = installViewModel::openApp,
                            onShowInstallError = {
                                scope.launch {
                                    // A run that patched fine has not collected these yet
                                    if (errorInfo == null) errorInfo = patcherViewModel.buildErrorInfo()
                                    shownFailure = PatcherFailure.INSTALL
                                }
                            }
                        ),
                        excludedPatches = excludedPatches,
                        isExpertMode = useExpertMode,
                        onConfigureAppLinks = { showAppLinksDialog = true }.takeIf { linksOpenInBrowser },
                        onLogsClick = patcherViewModel::hideSuccessScreen,
                        onHomeClick = onBackClick,
                        onSaveClick = {
                            if (!isSaving) {
                                exportApk(patcherViewModel.exportFileName)
                            }
                        },
                        onShareClick = {
                            scope.launch {
                                // Shared under the name a save would give it, not the working one
                                val name = patcherViewModel.resolvedExportFileName()
                                context.shareApks(listOf(patcherViewModel.outputFile)) { name }
                            }
                        },
                        isSaving = isSaving,
                        onGamesClick = onOpenGames
                    )
                }

                PatcherState.FAILED -> {
                    PatchingFailed(
                        summary = patchedAppSummary,
                        errorMessage = patcherViewModel.failureMessage,
                        failedPatch = patcherViewModel.patchRun.failedPatch,
                        onHomeClick = onBackClick,
                        onErrorClick = { shownFailure = PatcherFailure.PATCHING },
                        // Simple mode keeps no selection of its own to return to
                        onChangePatchesClick = onChangePatches.takeIf { useExpertMode },
                        onGamesClick = onOpenGames
                    )
                }
            }
        }
    }
}
