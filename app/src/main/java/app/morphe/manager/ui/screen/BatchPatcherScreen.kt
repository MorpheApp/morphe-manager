/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.batch.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.ui.model.PatchRunProgress
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.patcher.ExpertPatchingInProgress
import app.morphe.manager.ui.screen.patcher.PatcherErrorDialog
import app.morphe.manager.ui.screen.patcher.PatcherErrorInfo
import app.morphe.manager.ui.screen.patcher.PatchingBackgroundSpeedEffect
import app.morphe.manager.ui.screen.patcher.PostPatchPromptDialogs
import app.morphe.manager.ui.screen.patcher.SimplePatchingInProgress
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.patcher.rememberDisplayedPatchProgress
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BatchPatcherViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Queue screen for patching several apps in a row.
 *
 * The screen has three faces driven by [BatchPhase]: a preflight list where blocked apps can
 * be fixed or dropped, a running view with the active app's progress, and a summary that
 * installs what was produced.
 */
@Composable
fun BatchPatcherScreen(
    targets: List<BatchTarget>,
    onBackClick: () -> Unit,
    onStartTour: () -> Unit = {},
    onDeclineTour: () -> Unit = {},
    viewModel: BatchPatcherViewModel = koinViewModel(),
    installViewModel: InstallViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    patchBundleRepository: PatchBundleRepository = koinInject(),
    onAppStateChanged: (String) -> Unit = {},
    onBackgroundSpeedChange: (Float) -> Unit = {},
    onPatchingCompleted: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val miniGameState = remember { MiniGameState(prefs, scope) }

    LaunchedEffect(targets) {
        viewModel.ensurePlan(targets)
    }

    KeepScreenOn(state?.isActive == true)

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = viewModel::onApkPicked
    )

    val startInstallQueue = rememberInstallQueue(
        installViewModel = installViewModel,
        completedPluralRes = R.plurals.batch_install_summary,
        onDrained = { installed -> if (installed > 0) viewModel.postPatchPrompts.trigger() }
    )

    InstallerFlowDialogs(installViewModel = installViewModel)

    PostPatchPromptDialogs(
        prompts = viewModel.postPatchPrompts,
        onStartTour = onStartTour,
        onDeclineTour = onDeclineTour,
        onLeave = onBackClick
    )

    val current = state
    // Apps already on the device drop out, so "Install all" means what is left and a card
    // that is done stops offering the button. One list drives both
    // Keyed by item rather than by package: an app queued in several cloned copies produces one
    // request per copy, and they all share the package name they were cloned from
    val installRequestsByItem: Map<String, InstallQueueRequest> = remember(current?.items) {
        current?.patchedItems.orEmpty().mapNotNull { item ->
            if (item.installOutcome == BatchInstallOutcome.INSTALLED) return@mapNotNull null
            val file = item.patchedFile ?: return@mapNotNull null
            item.id to InstallQueueRequest(
                file = file,
                originalPackageName = item.packageName,
                builtForMount = item.useMount,
                onPersistApp = { packageName, installType ->
                    viewModel.persistInstalled(item, packageName, installType)
                },
                onInstalled = { installedPackage ->
                    viewModel.markInstalled(item.id, installedPackage)
                    onAppStateChanged(installedPackage)
                },
                onFailed = { message -> viewModel.markInstallFailed(item.id, message) }
            )
        }.toMap()
    }
    val installRequests: List<InstallQueueRequest> = installRequestsByItem.values.toList()

    val useExpertMode by prefs.useExpertMode.getAsState()

    // Outlives its item so a round in play survives the gap between apps and the end of the queue
    var lastRun by remember { mutableStateOf<PatchRunProgress?>(null) }
    LaunchedEffect(current?.activeRun) {
        current?.activeRun?.let { lastRun = it }
    }
    val heldRun = lastRun?.takeIf { useExpertMode && miniGameState.isPlaying }

    // The app last patched keeps its name and color on the progress once the queue moves past it
    var lastPackageName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current?.activeItem?.packageName) {
        current?.activeItem?.packageName?.let { lastPackageName = it }
    }
    val shownPackageName = current?.activeItem?.packageName ?: lastPackageName

    // A queue that drains mid-round waits for the player, as a single run does. Keyed on the
    // phase so a retried queue waits again
    var summaryReleased by remember(current?.phase) { mutableStateOf(false) }
    val holdSummary = current?.phase == BatchPhase.FINISHED &&
            !summaryReleased &&
            heldRun != null
    LaunchedEffect(holdSummary) {
        if (!holdSummary) summaryReleased = true
    }

    // The queue's end gets the same finish as a single run, once per queue and only for one the
    // user watched run out. Opening the summary of a queue that ended earlier stays quiet
    val view = LocalView.current
    var watchedRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(current?.phase, holdSummary) {
        if (current?.phase == BatchPhase.RUNNING) watchedRunning = true
        if (current?.phase == BatchPhase.FINISHED && !holdSummary && watchedRunning) {
            watchedRunning = false
            // A queue stopped before its end is nothing to celebrate, whatever it got through
            if (current.finishedInForeground && current.succeeded > 0 && !current.wasStopped) {
                delay(300.milliseconds) // small pause so speed resets before effect fires
                onPatchingCompleted()
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    // Once per queue: a summary reopened or recreated after it would prompt for every app again
    LaunchedEffect(current?.phase, current?.policy, holdSummary) {
        if (current?.phase == BatchPhase.FINISHED &&
            !holdSummary &&
            !current.autoInstallStarted &&
            current.policy == BatchInstallPolicy.INSTALL_AFTER &&
            installRequests.isNotEmpty()
        ) {
            viewModel.markAutoInstallStarted()
            startInstallQueue(installRequests)
        }
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (current?.isActive == true) showCancelDialog = true else onBackClick()
    }

    DialogHost(visible = showCancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.batch_patch_stop_title),
            message = stringResource(R.string.batch_patch_stop_description),
            primaryText = stringResource(R.string.yes),
            secondaryText = stringResource(R.string.no),
            onConfirm = {
                showCancelDialog = false
                // A stopped queue must not wait for the round
                miniGameState.pauseActiveGame()
                viewModel.cancel()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    DialogHost(visible = showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.batch_queue_clear_title),
            message = stringResource(R.string.batch_queue_clear_description),
            primaryText = stringResource(R.string.yes),
            secondaryText = stringResource(R.string.no),
            onConfirm = {
                showClearDialog = false
                viewModel.clear()
                onBackClick()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    // Opened straight from the actions that need it rather than by watching state: the target
    // can repeat, and a repeated value is not an event a keyed effect would fire on again
    val attachApkTo = { itemId: String ->
        viewModel.requestAttach(itemId)
        openApkPicker()
    }

    // Blocks the screen while an APK given to an app is read and the app planned again against it
    Overlay(visible = viewModel.processingApk) {
        PulsingLogoWithCaption(caption = stringResource(R.string.processing_apk))
    }

    // The steps of getting a queued app its APK and patches share a stack, so each one takes
    // over from the one before rather than opening after it has gone
    val queueDialogs = listOfNotNull(
        QueueDialog.SourcePick.takeIf { viewModel.sourcePick != null },
        QueueDialog.ApkChoice.takeIf { viewModel.apkChoice != null },
        QueueDialog.ApkSearch.takeIf { viewModel.apkSearch != null },
        QueueDialog.AttachPrompt.takeIf { viewModel.attachPrompt != null },
        QueueDialog.Edit.takeIf { viewModel.edit != null }
    )

    // Kept outside the dialogs so the picker state survives the download dialog's exit animation
    WithApkDownloadHelpers(
        host = viewModel,
        enabled = viewModel.apkChoice != null || viewModel.apkSearch != null
    ) { apkDownloadHelpers ->
        DialogStack(queueDialogs) { dialog ->
            when (dialog) {
                // The same source question simple mode answers before a single-app patch, asked on
                // the way to the APK since the source settles which versions are worth looking for
                QueueDialog.SourcePick -> held(viewModel.sourcePick)?.let { pick ->
                    SimpleBundleSelectDialog(
                        packageName = pick.item.packageName,
                        choices = pick.choices,
                        versions = pick.versions,
                        onSelect = viewModel::pickSource,
                        onDismiss = viewModel::cancelSourcePick,
                        initiallySelected = pick.item.chosenBundleUid
                    )
                }

                // The single-app flow's own APK question, pointed at a queued app. It carries the
                // version list, so picking a specific or experimental version works here exactly as it
                // does there
                QueueDialog.ApkChoice -> held(viewModel.apkChoice)?.let { choice ->
                    ApkAvailabilityDialog(
                        appName = choice.item.appName,
                        packageName = choice.item.packageName,
                        recommendedVersion = choice.recommended,
                        compatibleVersions = choice.compatible,
                        selectedDownloadVersion = choice.selectedVersion,
                        onVersionSelect = viewModel::selectApkVersion,
                        usingMountInstall = choice.item.useMount,
                        stockAppInstalled = choice.hasStockInstall,
                        isExpertMode = useExpertMode,
                        savedApkInfo = choice.saved,
                        installedApkInfo = choice.installed,
                        installedAppVersion = choice.installedVersion,
                        hiddenSourceCount = choice.hiddenSources,
                        onDismiss = viewModel::cancelApkChoice,
                        onHaveApk = {
                            viewModel.cancelApkChoice()
                            attachApkTo(choice.item.id)
                        },
                        onNeedApk = {
                            apkDownloadHelpers.showInstructions {
                                viewModel.beginApkSearch(choice.item, choice.selectedVersion?.target?.version)
                            }
                        },
                        onUseSaved = { viewModel.useApkSource(preferInstalled = false) },
                        onUseInstalled = { viewModel.useApkSource(preferInstalled = true) }
                    )
                }

                // The same instructions the single-app flow shows before sending someone to download
                // an APK, pointed at a queued app. Continuing hands off the link and then opens the
                // file picker
                QueueDialog.ApkSearch -> held(viewModel.apkSearch)?.let { search ->
                    val bundleMetadata by patchBundleRepository.appMetadata.collectAsStateWithLifecycle()
                    val metadata = bundleMetadata[search.item.packageName]

                    DownloadInstructionsDialog(
                        appName = search.item.appName,
                        packageName = search.item.packageName,
                        downloadUrl = search.url,
                        requestedVersion = search.version,
                        usingMountInstall = search.item.useMount,
                        stockAppInstalled = search.item.source is BatchApkSource.Installed,
                        downloadColor = metadata?.downloadColor ?: KnownApps.DEFAULT_DOWNLOAD_COLOR,
                        isApkBundle = metadata?.apkFileType?.isApk == false,
                        onDismiss = viewModel::cancelApkSearch,
                        onOpenApkDownloadHelper = apkDownloadHelpers.open,
                        onContinue = viewModel::confirmApkSearch
                    )
                }

                // Waits on screen while the user is in the browser, so the picker opens on their tap
                // once they are back rather than behind whatever the browser put in front
                QueueDialog.AttachPrompt -> held(viewModel.attachPrompt)?.let { item ->
                    FilePickerPromptDialog(
                        appName = item.appName,
                        packageName = item.packageName,
                        isOtherApps = false,
                        isLoadingInstalledApps = false,
                        onDismiss = viewModel::dismissAttachPrompt,
                        onOpenFilePicker = {
                            viewModel.dismissAttachPrompt()
                            attachApkTo(item.id)
                        },
                        onUseInstalledApp = null
                    )
                }

                // The same dialog the single-app flow uses, pointed at one queued app instead of the
                // patcher, so the queue never has to grow a second patch list
                QueueDialog.Edit -> held(viewModel.edit)?.let { edit ->
                    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle()
                    val sourcesByUid = remember(sources) { sources.associateBy { it.uid } }
                    ExpertModeDialog(
                        packageName = edit.packageName,
                        appName = edit.appName,
                        selection = ExpertPatchSelection(
                            allPatchesInfo = edit.allPatchesInfo,
                            options = edit.options,
                            totalSelectedCount = edit.totalSelectedCount,
                            newPatches = edit.newPatches,
                            savedPatches = edit.savedSelection,
                            lockStateOf = edit::lockStateOf,
                            holdsUniversalPatches = edit::selectAllHoldsUniversal
                        ),
                        hasMultipleBundles = edit.hasMultipleBundles,
                        patchActions = ExpertPatchActions(
                            onPatchToggle = edit::togglePatch,
                            onSelectAll = edit::selectAll,
                            onDeselectAll = edit::deselectAll,
                            onResetToDefault = edit::resetToDefault,
                            onRestoreSaved = edit::restoreSaved,
                            onCopyFromBundle = viewModel::openEditCopyDialog,
                            onOptionChange = edit::updateOption,
                            onResetOptions = edit::resetOptions
                        ),
                        usesPrerelease = { sourcesByUid[it]?.usesPrerelease == true },
                        proceedText = stringResource(R.string.save),
                        // The queue combines sources by design, and the tabs make it plain enough
                        warnOnMultipleBundles = false,
                        onDismiss = viewModel::cancelEdit,
                        onProceed = viewModel::applyEdit
                    )

                    val copyTarget = edit.bundles
                        .firstOrNull { it.uid == viewModel.editCopy.targetBundleUid }
                        ?.let { bundle ->
                            CopySelectionTarget(
                                packageName = edit.configurationKey,
                                bundleUid = bundle.uid,
                                bundleName = bundle.name,
                                appDisplayName = edit.appName
                            )
                        }
                    DialogHost(target = copyTarget) { target ->
                        CopySelectionFromBundleDialog(
                            target = target,
                            candidates = viewModel.editCopy.candidates,
                            onConfirm = viewModel::applyEditCopy,
                            onDismiss = viewModel.editCopy::close
                        )
                    }
                }
            }
        }
    }

    // Saving a patched APK somewhere the user picks, the same export the single-app flow and
    // the saved APK list offer
    val context = LocalContext.current
    val exporter = rememberApkExporter { exported -> if (exported) viewModel.postPatchPrompts.trigger() }

    // Shared under the names a save would give them, so each app can be told apart where it lands
    fun share(items: List<BatchPatchItem>) {
        val names = items.mapNotNull { item -> item.patchedFile?.let { it to item.exportFileName() } }.toMap()
        scope.launch { context.shareApks(names.keys.toList()) { names.getValue(it) } }
    }

    // The question a rooted device asks before a single-app patch, asked here per queued app
    var targetItem by remember { mutableStateOf<BatchPatchItem?>(null) }
    DialogHost(target = targetItem) { item ->
        fun choose(useMount: Boolean) {
            targetItem = null
            viewModel.setInstallTarget(item.id, useMount)
        }
        PrePatchInstallerDialog(
            packageName = item.packageName,
            onSelectMount = { choose(useMount = true) },
            onSelectStandard = { choose(useMount = false) },
            onDismiss = { targetItem = null },
            showPromptHint = false
        )
    }

    // Cards clamp the failure text, so the full reason lives in the patcher's own error dialog
    var errorItem by remember { mutableStateOf<BatchPatchItem?>(null) }
    DialogHost(target = errorItem) { item ->
        PatcherErrorDialog(
            title = stringResource(R.string.patcher_failed_dialog_title),
            errorMessage = item.message ?: stringResource(R.string.patcher_unknown_error),
            errorInfo = PatcherErrorInfo(
                appName = item.appName,
                packageName = item.packageName,
                appVersion = item.version.orEmpty(),
                patchCount = item.selection.values.sumOf { it.size },
                failedPatch = item.failedPatch,
                bundles = item.bundles.map {
                    PatchSourceRef(name = it.name, version = null)
                },
                stripsNativeLibs = null
            ),
            onDismiss = { errorItem = null }
        )
    }

    val listState = rememberLazyListState()
    val shownRun = current?.activeRun ?: heldRun

    // Patching an app looks exactly like a single run, with a queue counter on top
    if (current != null && (current.phase == BatchPhase.RUNNING || holdSummary)) {
        // The preflight dialog is a separate window and cannot animate into this one, so the
        // patcher fades in on its own to soften the switch
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }

        AnimatedVisibility(visibleState = appear, enter = Animations.fadeIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                if (shownRun == null) {
                    BatchRunHeader(state = current)

                    // Between apps: the previous run is over and the next has not started, so
                    // there are no live steps to show
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PulsingLogoWithCaption(
                            caption = stringResource(R.string.batch_patch_preparing_next)
                        )
                    }
                } else {
                    // Nudged ahead between the patcher's coarse steps and eased, as a single run is
                    val displayProgress = rememberDisplayedPatchProgress(
                        progress = { shownRun.progress },
                        succeeded = if (holdSummary) true else null,
                        run = shownRun
                    )
                    // Each app ramps the background up from rest. A run held on screen past its
                    // end, for a round in play, is over and leaves the background at rest
                    PatchingBackgroundSpeedEffect(
                        active = current.activeRun != null,
                        progress = { displayProgress.target },
                        onSpeedChange = onBackgroundSpeedChange,
                        run = shownRun
                    )

                    if (useExpertMode) {
                        ExpertPatchingInProgress(
                            progress = { displayProgress.value },
                            patchesProgress = shownRun.patchesProgress,
                            patchProgress = shownRun,
                            packageName = shownPackageName,
                            patcherSucceeded = if (holdSummary) true else null,
                            miniGameState = miniGameState,
                            queueHeader = { BatchRunHeader(state = current) },
                            onCancelClick = { showCancelDialog = true },
                            onResultClick = { summaryReleased = true }
                        )
                    } else {
                        val longStepWarning by shownRun.showLongStepWarning.collectAsStateWithLifecycle()
                        SimplePatchingInProgress(
                            progress = { displayProgress.value },
                            patchesProgress = shownRun.patchesProgress,
                            patchProgress = shownRun,
                            packageName = shownPackageName,
                            showLongStepWarning = longStepWarning,
                            queueHeader = { BatchRunHeader(state = current) },
                            onCancelClick = { showCancelDialog = true }
                        )
                    }
                }
            }
        }
        return
    }

    // Clearing a finished run empties the state, which would otherwise put the planning
    // overlay back on screen for the length of the exit animation
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        closing = true
        if (current?.phase == BatchPhase.FINISHED) viewModel.clear()
        onBackClick()
    }
    val isPreflight = current?.phase == BatchPhase.PREFLIGHT
    val isFinished = current?.phase == BatchPhase.FINISHED
    val patchedItems = if (isFinished) current.patchedItems else emptyList()
    val hasPatched = patchedItems.isNotEmpty()
    val hasUnfinished = isFinished && current.items.any {
        it.state == BatchItemState.FAILED || it.state == BatchItemState.CANCELLED
    }

    val accent = MaterialTheme.colorScheme.primary
    DialogHost(visible = !closing) {
        ListDialog(
            onDismissRequest = close,
            header = {
                // Headed like the other lists, with how the run stands where the subtitle goes
                ListDialogHeader(
                    icon = { modifier ->
                        ListDialogHeaderIcon(icon = MaterialSymbols.Rounded.Auto_fix_high, color = accent, modifier = modifier)
                    },
                    title = stringResource(R.string.batch_patch_title),
                    subtitle = current?.let { batchSummary(it) }.orEmpty(),
                    subtitleLoading = current == null,
                    accentColor = accent,
                    actions = if (isPreflight || hasPatched || hasUnfinished) {
                        {
                            // Kept out of the footer, where the button beside Start only closes the screen
                            if (isPreflight) {
                                TitleAction(
                                    icon = MaterialSymbols.Rounded.Delete,
                                    contentDescription = stringResource(R.string.clear),
                                    onClick = { showClearDialog = true },
                                    style = TitleActionStyle.Destructive
                                )
                            }
                            // Every APK the run made in one go, for sending them on together
                            if (hasPatched) {
                                TitleAction(
                                    icon = MaterialSymbols.Rounded.Share,
                                    contentDescription = stringResource(R.string.share),
                                    onClick = { share(patchedItems) },
                                    style = TitleActionStyle.Accent
                                )
                            }
                            if (hasUnfinished) {
                                TitleAction(
                                    icon = MaterialSymbols.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.retry),
                                    onClick = viewModel::retryUnfinished,
                                    style = TitleActionStyle.Accent
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            },
            footer = {
                BatchDialogButtons(
                    state = current,
                    canInstall = installRequests.isNotEmpty(),
                    onStart = viewModel::start,
                    onInstallAll = { startInstallQueue(installRequests) },
                    onClose = close
                )
            }
        ) {
            DialogLazyList(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
            ) {
                if (current == null) return@DialogLazyList

                // Mixing sources is decided by the plan, not by the user, so this is the one place
                // it can be pointed out. Said once for the run rather than blocking the queue
                if (current.phase == BatchPhase.PREFLIGHT &&
                    current.runnable.any { it.selection.keys.size > 1 }
                ) {
                    item(key = "multiple_sources") {
                        Notice(
                            text = stringResource(R.string.batch_patch_multiple_sources),
                            icon = MaterialSymbols.Rounded.Info,
                            tone = SemanticTone.Warning,
                            density = NoticeDensity.Compact,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // Nothing was installed yet, so say where the APKs went
                if (current.phase == BatchPhase.FINISHED && current.patchedItems.isNotEmpty()) {
                    item(key = "saved_hint") {
                        Footnote(
                            text = stringResource(R.string.batch_patch_saved_hint),
                            icon = MaterialSymbols.Rounded.Save,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                if (current.phase == BatchPhase.PREFLIGHT) {
                    item {
                        BatchPolicyCard(
                            policy = current.policy,
                            mountCount = current.runnable.count { it.useMount },
                            runnableCount = current.runnable.size,
                            onPolicyChange = viewModel::setPolicy
                        )
                    }
                }

                items(current.items, key = { it.id }) { item ->
                    val request = installRequestsByItem[item.id]
                    BatchItemCard(
                        item = item,
                        editable = current.phase == BatchPhase.PREFLIGHT,
                        onSelectApk = { viewModel.beginApkChoice(item) },
                        onToggleExcluded = { viewModel.toggleExcluded(item.id) },
                        onForceVersion = { viewModel.forceVersion(item.id) },
                        onAcceptSignature = { viewModel.acceptUnverifiedSignature(item.id) },
                        // Asked only where mounting is possible at all, since nothing else has a choice
                        onChooseTarget = { targetItem = item }.takeIf { viewModel.canMount },
                        // Simple mode never exposes individual patches, and the options edited
                        // here would not be persisted for it. Nothing to choose from until an
                        // APK resolves either, the patch list is scoped to its exact version
                        onEditPatches = item.source
                            ?.takeIf { useExpertMode }
                            ?.let { { viewModel.beginEdit(item) } },
                        onInstall = request?.let { { startInstallQueue(listOf(it)) } },
                        onExport = item.patchedFile?.let { file -> { exporter.export(file, item.exportFileName()) } },
                        onShare = item.patchedFile?.let { { share(listOf(item)) } },
                        onOpen = item.installedPackageName
                            ?.takeIf { item.installOutcome == BatchInstallOutcome.INSTALLED }
                            ?.let { { viewModel.openApp(it) } },
                        onShowError = { errorItem = item }
                    )
                }
            }
        }
    }

    Overlay(visible = !closing && (current == null || current.phase == BatchPhase.PLANNING)) {
        PulsingLogoWithCaption(caption = stringResource(R.string.batch_patch_planning))
    }
}

/** The dialogs of the queue that hand over to one another, see [DialogStack]. */
private enum class QueueDialog { SourcePick, ApkChoice, ApkSearch, AttachPrompt, Edit }

/** Footer buttons of the batch dialog, one pair per phase. */
@Composable
private fun BatchDialogButtons(
    state: BatchRunState?,
    canInstall: Boolean,
    onStart: () -> Unit,
    onInstallAll: () -> Unit,
    onClose: () -> Unit
) {
    when (state?.phase) {
        // Closing keeps the queue for apps added later
        BatchPhase.PREFLIGHT -> AppDialogButtonRow(
            primaryText = stringResource(R.string.batch_patch_start),
            primaryIcon = MaterialSymbols.Rounded.Play_arrow,
            onPrimaryClick = onStart,
            primaryEnabled = state.runnable.isNotEmpty(),
            secondaryText = stringResource(R.string.close),
            onSecondaryClick = onClose
        )

        BatchPhase.FINISHED -> if (canInstall) {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.batch_patch_install_all),
                primaryIcon = MaterialSymbols.Rounded.Install_mobile,
                onPrimaryClick = onInstallAll,
                secondaryText = stringResource(R.string.done),
                onSecondaryClick = onClose
            )
        } else {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.done),
                onPrimaryClick = onClose
            )
        }

        else -> AppDialogButtonRow(
            primaryText = stringResource(android.R.string.cancel),
            onPrimaryClick = onClose
        )
    }
}

/** Queue counter shown above the patching screen while an app is being worked on. */
@Composable
private fun BatchRunHeader(state: BatchRunState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge)
            .padding(top = Defaults.SpaceSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
    ) {
        AnimatedContent(
            targetState = state.position to state.total,
            transitionSpec = Animations.counterTransitionSpec,
            label = "batch_run_counter"
        ) { (position, total) ->
            Text(
                text = stringResource(
                    R.string.batch_patch_progress_counter,
                    position.toString(),
                    total.toString()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Between apps there is no active item, and dropping the line would shift everything below
        var lastAppName by remember { mutableStateOf("") }
        val appName = state.activeItem?.appName ?: lastAppName
        SideEffect { lastAppName = appName }

        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** How the run stands: how many apps are ready while it is planned, how it went once it drained. */
@Composable
private fun batchSummary(state: BatchRunState): String = when (state.phase) {
    BatchPhase.FINISHED -> stringResource(
        R.string.batch_patch_summary,
        state.succeeded.toString(),
        state.failed.toString(),
        state.skipped.toString()
    )

    else -> pluralStringResource(
        R.plurals.batch_patch_ready_count,
        state.runnable.size,
        state.runnable.size.toString()
    )
}

/** Single switch deciding what happens once every app in the queue is patched. */
@Composable
private fun BatchPolicyCard(
    policy: BatchInstallPolicy,
    // How many of the apps to run are mounted rather than installed, which the wording names
    mountCount: Int,
    runnableCount: Int,
    onPolicyChange: (BatchInstallPolicy) -> Unit
) {
    val (title, subtitle) = when (mountCount) {
        0 -> R.string.batch_patch_policy_install to R.string.batch_patch_policy_install_description
        runnableCount -> R.string.batch_patch_policy_mount to R.string.batch_patch_policy_mount_description
        else -> R.string.batch_patch_policy_install_mount to R.string.batch_patch_policy_install_mount_description
    }
    SettingsGroup {
        SettingsSwitchItem(
            checked = policy == BatchInstallPolicy.INSTALL_AFTER,
            onToggle = {
                onPolicyChange(
                    if (policy == BatchInstallPolicy.INSTALL_AFTER) {
                        BatchInstallPolicy.SAVE_ONLY
                    } else {
                        BatchInstallPolicy.INSTALL_AFTER
                    }
                )
            },
            icon = MaterialSymbols.Rounded.Install_mobile,
            title = stringResource(title),
            subtitle = stringResource(subtitle)
        )
    }
}

@Composable
private fun BatchItemCard(
    item: BatchPatchItem,
    editable: Boolean,
    onSelectApk: () -> Unit,
    onToggleExcluded: () -> Unit,
    onForceVersion: () -> Unit,
    onAcceptSignature: () -> Unit,
    onChooseTarget: (() -> Unit)? = null,
    onEditPatches: (() -> Unit)? = null,
    onInstall: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onShowError: () -> Unit = {}
) {
    val excluded = item.state == BatchItemState.EXCLUDED
    val failed = !editable && item.state == BatchItemState.FAILED && !item.message.isNullOrBlank()
    val hasResultActions = !editable &&
            (onInstall != null || onExport != null || onOpen != null || failed)

    // Worn the way the home screen wears it, so the app reads as itself in the queue too. Looked
    // up by the app's own package, which a clone being rebuilt does not carry
    SectionCard(modifier = Modifier.fillMaxWidth(), accentColor = rememberAppColor(item.packageName)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = item.id,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                ) {
                    // The name gets a line to itself: badges beside it grow with translation
                    // and would push it out of the card entirely
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = dialogTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    StatusBadgeRow {
                        // The queue takes whatever APK is on hand and never stops to warn, so
                        // the caveat the single-app flow raises a dialog for is tagged here
                        if (item.experimentalVersion) {
                            VersionTagBadge(VersionTag.Experimental)
                        }

                        // The build only works mounted over the stock app, which is worth knowing
                        // before it is installed or shared
                        if (item.useMount) {
                            StatusBadge(text = stringResource(R.string.mount), tone = SemanticTone.Neutral)
                        }

                        // Once installing has been tried, its outcome is the newer and more
                        // useful fact about the app than how the patching went
                        when (item.installOutcome) {
                            BatchInstallOutcome.INSTALLED -> StatusBadge(
                                text = stringResource(if (item.useMount) R.string.mounted else R.string.installed),
                                tone = SemanticTone.Success
                            )

                            BatchInstallOutcome.FAILED -> StatusBadge(
                                text = stringResource(R.string.batch_patch_install_failed),
                                tone = SemanticTone.Error
                            )

                            null -> BatchStateBadge(item.state)
                        }
                    }

                    // This entry's own package, which is what tells clones of one app apart
                    Text(
                        text = item.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSecondaryTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val installFailure = item.installMessage
                        ?.takeIf { item.installOutcome == BatchInstallOutcome.FAILED }
                    Text(
                        text = installFailure ?: itemDetails(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installFailure != null ||
                            item.state.needsAttention ||
                            item.state == BatchItemState.FAILED
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            dialogSecondaryTextColor()
                        },
                        // An install failure explains what to do about it, so it is shown in
                        // full. Patcher errors are raw stack traces and stay clamped
                        maxLines = if (installFailure != null) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Paths planning left out. The app is patched either way, so this is said
                    // here rather than held against the item as a state that blocks the run
                    if (editable && item.unreadableOptionPaths.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.batch_patch_option_paths_skipped,
                                item.unreadableOptionPaths.joinToString { it.path }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            FoldingVisibility(visible = editable) {
                Column {
                    SettingsDivider()
                    ActionPillRow(
                        modifier = Modifier.padding(
                            horizontal = Defaults.SpaceLarge,
                            vertical = Defaults.SpaceMedium
                        )
                    ) {
                        val selectApkLabel = stringResource(R.string.home_select_apk_title)
                        ActionPillButton(
                            onClick = onSelectApk,
                            icon = MaterialSymbols.Rounded.File_open,
                            contentDescription = selectApkLabel,
                            tooltip = selectApkLabel
                        )

                        if (onChooseTarget != null) {
                            val targetLabel = stringResource(R.string.root_pre_patch_installer_title)
                            ActionPillButton(
                                onClick = onChooseTarget,
                                icon = if (item.useMount) {
                                    MaterialSymbols.Rounded.Link
                                } else {
                                    MaterialSymbols.Rounded.Install_mobile
                                },
                                contentDescription = targetLabel,
                                tooltip = targetLabel
                            )
                        }

                        if (onEditPatches != null) {
                            val editLabel = stringResource(R.string.batch_patch_edit_patches)
                            ActionPillButton(
                                onClick = onEditPatches,
                                icon = MaterialSymbols.Rounded.Tune,
                                contentDescription = editLabel,
                                tooltip = editLabel
                            )
                        }

                        if (item.state == BatchItemState.VERSION_MISMATCH) {
                            val forceLabel = stringResource(R.string.batch_patch_force_version)
                            ActionPillButton(
                                onClick = onForceVersion,
                                icon = MaterialSymbols.Rounded.Warning,
                                contentDescription = forceLabel,
                                tooltip = forceLabel,
                                colors = ActionPillColors.warning()
                            )
                        }

                        if (item.state == BatchItemState.UNVERIFIED_SIGNATURE) {
                            // The same wording the single-app flow answers this very question with
                            val acceptLabel =
                                stringResource(R.string.home_dialog_unsupported_version_dialog_proceed)
                            ActionPillButton(
                                onClick = onAcceptSignature,
                                icon = MaterialSymbols.Rounded.Gpp_bad,
                                contentDescription = acceptLabel,
                                tooltip = acceptLabel,
                                colors = ActionPillColors.warning()
                            )
                        }

                        val toggleLabel = stringResource(
                            if (excluded) R.string.include else R.string.exclude
                        )
                        ActionPillButton(
                            onClick = onToggleExcluded,
                            icon = if (excluded) MaterialSymbols.Rounded.Add_circle else MaterialSymbols.Rounded.Do_not_disturb_on,
                            contentDescription = toggleLabel,
                            tooltip = toggleLabel,
                            destructive = !excluded
                        )
                    }
                }
            }

            FoldingVisibility(visible = hasResultActions) {
                Column {
                    SettingsDivider()
                    ActionPillRow(
                        modifier = Modifier.padding(
                            horizontal = Defaults.SpaceLarge,
                            vertical = Defaults.SpaceMedium
                        )
                    ) {
                        if (failed) {
                            val errorLabel = stringResource(R.string.error_)
                            ActionPillButton(
                                onClick = onShowError,
                                icon = MaterialSymbols.Rounded.Error,
                                contentDescription = errorLabel,
                                tooltip = errorLabel,
                                destructive = true
                            )
                        }

                        if (onInstall != null) {
                            val installLabel = stringResource(R.string.install)
                            ActionPillButton(
                                onClick = onInstall,
                                icon = MaterialSymbols.Rounded.Install_mobile,
                                contentDescription = installLabel,
                                tooltip = installLabel,
                                colors = ActionPillColors.primary()
                            )
                        }

                        if (onExport != null) {
                            val exportLabel = stringResource(R.string.export)
                            ActionPillButton(
                                onClick = onExport,
                                icon = MaterialSymbols.Rounded.Download,
                                contentDescription = exportLabel,
                                tooltip = exportLabel
                            )
                        }

                        if (onShare != null) {
                            val shareLabel = stringResource(R.string.share)
                            ActionPillButton(
                                onClick = onShare,
                                icon = MaterialSymbols.Rounded.Share,
                                contentDescription = shareLabel,
                                tooltip = shareLabel
                            )
                        }

                        if (onOpen != null) {
                            val openLabel = stringResource(R.string.open)
                            ActionPillButton(
                                onClick = onOpen,
                                icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                                contentDescription = openLabel,
                                tooltip = openLabel
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The name a saved or shared APK of [this] item goes by, the one the single-app flow gives it. */
private fun BatchPatchItem.exportFileName(): String = ExportNameFormatter.format(
    PatchedAppExportData(
        appName = appName,
        packageName = id,
        appVersion = version,
        sources = bundles.map { PatchSourceRef(it.name, it.version) }
    )
)

/** Second detail line: either why the app cannot run, or what it will be patched from. */
@Composable
private fun itemDetails(item: BatchPatchItem): String = when (item.state) {
    BatchItemState.NEEDS_APK -> item.message?.let {
        stringResource(R.string.batch_patch_wrong_package, it)
    } ?: stringResource(R.string.batch_patch_needs_apk)

    BatchItemState.NO_PATCHES -> stringResource(R.string.home_no_patches_available)

    BatchItemState.NO_SELECTION -> stringResource(R.string.batch_patch_no_selection)

    BatchItemState.VERSION_MISMATCH -> stringResource(
        R.string.batch_patch_version_mismatch,
        item.version.orEmpty()
    )

    BatchItemState.UNVERIFIED_SIGNATURE -> stringResource(R.string.home_invalid_signature_badge)

    // The patch is what the user can deselect, while the error itself is left to the dialog
    BatchItemState.FAILED -> item.failedPatch?.let { stringResource(R.string.failed_to_execute_patch, it) }
        ?: item.message
        ?: stringResource(R.string.patcher_unknown_error)

    else -> {
        val source = when (item.source) {
            is BatchApkSource.SavedOriginal -> stringResource(R.string.batch_patch_source_saved)
            is BatchApkSource.Installed -> stringResource(R.string.batch_patch_source_installed)
            is BatchApkSource.UserFile -> stringResource(R.string.batch_patch_source_file)
            null -> stringResource(R.string.batch_patch_needs_apk)
        }
        val patches = pluralStringResource(
            R.plurals.patch_count,
            item.patchCount,
            item.patchCount.toString()
        )
        // Only the sources actually contributing patches, so a source the user took nothing
        // from is not listed as one the app is patched from
        val bundles = item.bundles
            .filter { item.selection.isEmpty() || it.uid in item.selection.keys }
            .joinToString(", ") { it.name }
            .takeIf { it.isNotEmpty() }
        listOfNotNull(item.version, source, patches, bundles).joinToString(" • ")
    }
}

/** Compact status pill, sized to its text so the app name keeps the rest of the row. */
@Composable
private fun BatchStateBadge(state: BatchItemState) {
    val (labelRes, tone) = when (state) {
        BatchItemState.READY -> R.string.ready to SemanticTone.Primary
        BatchItemState.RUNNING -> R.string.patching to SemanticTone.Primary
        BatchItemState.SUCCEEDED -> R.string.done to SemanticTone.Success
        BatchItemState.FAILED -> R.string.failed to SemanticTone.Error
        BatchItemState.CANCELLED -> R.string.canceled to SemanticTone.Neutral
        BatchItemState.EXCLUDED -> R.string.excluded to SemanticTone.Neutral
        BatchItemState.NEEDS_APK -> R.string.batch_patch_state_no_apk to SemanticTone.Error
        BatchItemState.VERSION_MISMATCH -> R.string.version to SemanticTone.Warning
        BatchItemState.UNVERIFIED_SIGNATURE -> R.string.home_unverified to SemanticTone.Warning

        BatchItemState.NO_PATCHES -> R.string.batch_patch_state_no_patches to SemanticTone.Error
        BatchItemState.NO_SELECTION -> R.string.batch_patch_state_no_selection to SemanticTone.Warning
    }
    StatusBadge(text = stringResource(labelRes), tone = tone)
}

