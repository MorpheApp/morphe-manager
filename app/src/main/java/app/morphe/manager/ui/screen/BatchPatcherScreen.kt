/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.batch.*
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.home.ExpertModeDialog
import app.morphe.manager.ui.screen.home.ExpertPatchActions
import app.morphe.manager.ui.screen.home.SimpleBundleCandidate
import app.morphe.manager.ui.screen.home.SimpleBundleSelectDialog
import app.morphe.manager.ui.screen.patcher.ExpertPatchingInProgress
import app.morphe.manager.ui.screen.patcher.PatcherErrorDialog
import app.morphe.manager.ui.screen.patcher.PatcherErrorInfo
import app.morphe.manager.ui.screen.patcher.SimplePatchingInProgress
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BatchPatcherViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.APK_FILE_MIME_TYPES
import app.morphe.manager.util.rememberAdaptiveFilePicker
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Queue screen for patching several apps in a row.
 *
 * The screen has three faces driven by [BatchPhase]: a preflight list where blocked apps can
 * be fixed or dropped, a running view with the active app's progress, and a summary that
 * installs what was produced.
 */
@Composable
fun BatchPatcherScreen(
    packageNames: List<String>,
    useMount: Boolean,
    onBackClick: () -> Unit,
    viewModel: BatchPatcherViewModel = koinViewModel(),
    installViewModel: InstallViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    onAppStateChanged: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val miniGameState = remember { MiniGameState(prefs, scope) }

    LaunchedEffect(packageNames, useMount) {
        viewModel.ensurePlan(packageNames, useMount)
    }

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = viewModel::onApkPicked
    )

    val startInstallQueue = rememberInstallQueue(
        installViewModel = installViewModel,
        completedPluralRes = R.plurals.batch_install_summary
    )

    InstallerFlowDialogs(installViewModel = installViewModel)

    val current = state
    val installRequests: List<InstallQueueRequest> = remember(current?.items) {
        current?.patchedItems.orEmpty().mapNotNull { item ->
            val file = item.patchedFile ?: return@mapNotNull null
            InstallQueueRequest(
                file = file,
                originalPackageName = item.packageName,
                onPersistApp = { packageName, installType ->
                    viewModel.persistInstalled(item, packageName, installType)
                },
                onInstalled = { installedPackage ->
                    viewModel.markInstalled(item.packageName, installedPackage)
                    onAppStateChanged(installedPackage)
                },
                onFailed = { message -> viewModel.markInstallFailed(item.packageName, message) }
            )
        }
    }

    LaunchedEffect(current?.phase, current?.policy) {
        if (current?.phase == BatchPhase.FINISHED &&
            current.policy == BatchInstallPolicy.INSTALL_AFTER &&
            installRequests.isNotEmpty()
        ) {
            startInstallQueue(installRequests)
        }
    }

    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (current?.isActive == true) showCancelDialog = true else onBackClick()
    }

    if (showCancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.batch_patch_stop_title),
            message = stringResource(R.string.batch_patch_stop_description),
            primaryText = stringResource(R.string.yes),
            secondaryText = stringResource(R.string.no),
            onConfirm = {
                showCancelDialog = false
                viewModel.cancel()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    LaunchedEffect(viewModel.attachTarget) {
        if (viewModel.attachTarget != null) openApkPicker()
    }

    // The same dialog the single-app flow uses, pointed at one queued app instead of the
    // patcher, so the queue never has to grow a second patch list
    viewModel.edit?.let { edit ->
        ExpertModeDialog(
            newPatches = edit.newPatches,
            options = edit.options,
            allPatchesInfo = edit.allPatchesInfo,
            totalSelectedCount = edit.totalSelectedCount,
            totalPatchesCount = edit.totalPatchesCount,
            hasMultipleBundles = edit.hasMultipleBundles,
            patchActions = ExpertPatchActions(
                onPatchToggle = edit::togglePatch,
                onSelectAll = edit::selectAll,
                onDeselectAll = edit::deselectAll,
                onResetToDefault = edit::resetToDefault,
                onRestoreSaved = edit::restoreSaved,
                // Copying a selection between sources belongs to the app's own patch dialog,
                // where it can be saved, rather than to a single queued run
                onCopyFromBundle = {},
                onOptionChange = edit::updateOption,
                onResetOptions = edit::resetOptions
            ),
            savedPatches = edit.savedSelection,
            proceedText = stringResource(R.string.save),
            // The queue combines sources by design, and the tabs make it plain enough
            warnOnMultipleBundles = false,
            onDismiss = viewModel::cancelEdit,
            onProceed = viewModel::applyEdit
        )
    }

    // The same source question simple mode answers before a single-app patch
    viewModel.sourcePick?.let { item ->
        // Offered from the full plan, not the narrowed selection, so switching sources works
        val offered = item.resolvedSelection ?: item.selection
        SimpleBundleSelectDialog(
            candidates = item.bundles
                .filter { it.uid in offered.keys }
                .map { bundle ->
                    SimpleBundleCandidate(
                        uid = bundle.uid,
                        displayTitle = bundle.name,
                        patchCount = offered[bundle.uid]?.size ?: 0
                    )
                },
            onSelect = viewModel::pickSource,
            onDismiss = viewModel::cancelSourcePick
        )
    }

    // Cards clamp the failure text, so the full reason lives in the patcher's own error dialog
    var errorItem by remember { mutableStateOf<BatchPatchItem?>(null) }
    errorItem?.let { item ->
        PatcherErrorDialog(
            errorMessage = item.message ?: stringResource(R.string.patcher_unknown_error),
            errorInfo = PatcherErrorInfo(
                appName = item.appName,
                packageName = item.packageName,
                appVersion = item.version.orEmpty(),
                bundles = item.bundles.map {
                    PatcherErrorInfo.BundleInfo(name = it.name, version = null)
                }
            ),
            onDismiss = { errorItem = null }
        )
    }

    val listState = rememberLazyListState()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val activeRun = current?.activeRun

    // Patching an app looks exactly like a single run, with a queue counter on top
    if (current != null && current.phase == BatchPhase.RUNNING) {
        // The preflight dialog is a separate window and cannot animate into this one, so the
        // patcher fades in on its own to soften the switch
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }

        AnimatedVisibility(visibleState = appear, enter = MorpheAnimations.fadeIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                BatchRunHeader(state = current)

                if (activeRun == null) {
                    // Between apps: the previous run is over and the next has not started, so
                    // there are no live steps to show
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PulsingLogoWithCaption(
                            caption = stringResource(R.string.batch_patch_preparing_next)
                        )
                    }
                } else if (useExpertMode) {
                    ExpertPatchingInProgress(
                        progress = activeRun.progress,
                        patchesProgress = activeRun.patchesProgress,
                        patchProgress = activeRun,
                        miniGameState = miniGameState,
                        onCancelClick = { showCancelDialog = true },
                        onHomeClick = onBackClick
                    )
                } else {
                    val longStepWarning by activeRun.showLongStepWarning.collectAsStateWithLifecycle()
                    SimplePatchingInProgress(
                        progress = activeRun.progress,
                        patchesProgress = activeRun.patchesProgress,
                        patchProgress = activeRun,
                        showLongStepWarning = longStepWarning,
                        onCancelClick = { showCancelDialog = true },
                        onHomeClick = onBackClick
                    )
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
    val hasUnfinished = current?.items.orEmpty().any {
        it.state == BatchItemState.FAILED || it.state == BatchItemState.CANCELLED
    }

    MorpheDialog(
        onDismissRequest = close,
        title = stringResource(R.string.batch_patch_title),
        titleTrailingContent = if (current?.phase == BatchPhase.FINISHED && hasUnfinished) {
            {
                DialogTitleAction(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    onClick = viewModel::retryUnfinished
                )
            }
        } else {
            null
        },
        footer = {
            BatchDialogButtons(
                state = current,
                canInstall = installRequests.isNotEmpty(),
                onStart = viewModel::start,
                onInstallAll = { startInstallQueue(installRequests) },
                onClose = close
            )
        },
        scrollable = false,
        padding = DialogPadding.Compact,
        contentArrangement = Arrangement.Top
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
            ) {
                if (current == null) return@LazyColumn

                item { BatchStatusCard(state = current) }

                if (current.phase == BatchPhase.PREFLIGHT) {
                    item {
                        BatchPolicyCard(
                            policy = current.policy,
                            onPolicyChange = viewModel::setPolicy
                        )
                    }
                }

                items(current.items, key = { it.packageName }) { item ->
                    val request = installRequests.firstOrNull { it.originalPackageName == item.packageName }
                    BatchItemCard(
                        item = item,
                        editable = current.phase == BatchPhase.PREFLIGHT,
                        onAttach = { viewModel.requestAttach(item.packageName) },
                        onToggleExcluded = { viewModel.toggleExcluded(item.packageName) },
                        onForceVersion = { viewModel.forceVersion(item.packageName) },
                        // Simple mode never exposes individual patches, and the options edited
                        // here would not be persisted for it. Nothing to choose from until an
                        // APK resolves either, the patch list is scoped to its exact version
                        onEditPatches = item.source
                            ?.takeIf { useExpertMode }
                            ?.let { { viewModel.beginEdit(item) } },
                        // Simple mode gets the source question it knows from single-app
                        // patching instead of the patch list it never sees
                        onPickSource = item
                            .takeIf {
                                !useExpertMode &&
                                    (it.resolvedSelection ?: it.selection).keys.size > 1
                            }
                            ?.let { { viewModel.beginSourcePick(item) } },
                        // Installing everything is not always what the user wants once they
                        // see which apps succeeded
                        onInstall = request?.let { { startInstallQueue(listOf(it)) } },
                        onOpen = item.installedPackageName
                            ?.takeIf { item.installOutcome == BatchInstallOutcome.INSTALLED }
                            ?.let { { viewModel.openApp(it) } },
                        onShowError = { errorItem = item }
                    )
                }
            }

            ListScrollbar(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
            ScrollToTopButton(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
        }
    }

    MorpheOverlay(visible = !closing && (current == null || current.phase == BatchPhase.PLANNING)) {
        PulsingLogoWithCaption(caption = stringResource(R.string.batch_patch_planning))
    }
}

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
        BatchPhase.PREFLIGHT -> MorpheDialogButtonRow(
            primaryText = stringResource(R.string.batch_patch_start),
            primaryIcon = Icons.Outlined.PlayArrow,
            onPrimaryClick = onStart,
            primaryEnabled = state.runnable.isNotEmpty(),
            secondaryText = stringResource(android.R.string.cancel),
            onSecondaryClick = onClose
        )

        BatchPhase.FINISHED -> if (canInstall) {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.batch_patch_install_all),
                primaryIcon = Icons.Outlined.InstallMobile,
                onPrimaryClick = onInstallAll,
                secondaryText = stringResource(R.string.done),
                onSecondaryClick = onClose
            )
        } else {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.done),
                onPrimaryClick = onClose
            )
        }

        else -> MorpheDialogButtonRow(
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
            .padding(horizontal = MorpheDefaults.ContentPadding)
            .padding(top = MorpheDefaults.ContentPaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedContent(
            targetState = state.processed to state.total,
            transitionSpec = MorpheAnimations.counterTransitionSpec,
            label = "batch_run_counter"
        ) { (processed, total) ->
            Text(
                text = stringResource(R.string.batch_patch_progress_counter, processed, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.activeItem?.let { item ->
            Text(
                text = item.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Run summary card shown in the preflight list and once the queue has drained. */
@Composable
private fun BatchStatusCard(state: BatchRunState) {
    val summary = when (state.phase) {
        BatchPhase.FINISHED -> stringResource(
            R.string.batch_patch_summary,
            state.succeeded,
            state.failed,
            state.skipped
        )

        else -> pluralStringResource(
            R.plurals.batch_patch_ready_count,
            state.runnable.size,
            state.runnable.size
        )
    }

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            AnimatedContent(
                targetState = summary,
                transitionSpec = MorpheAnimations.counterTransitionSpec,
                label = "batch_summary"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
            }

            // Mixing sources is decided by the plan, not by the user, so this is the one place
            // it can be pointed out. Said once for the run rather than blocking the queue
            if (state.phase == BatchPhase.PREFLIGHT &&
                state.runnable.any { it.selection.keys.size > 1 }
            ) {
                Text(
                    text = stringResource(R.string.batch_patch_multiple_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            // Nothing was installed yet, so say where the APKs went
            if (state.phase == BatchPhase.FINISHED && state.patchedItems.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.batch_patch_saved_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }
    }
}

/** Single switch deciding what happens once every app in the queue is patched. */
@Composable
private fun BatchPolicyCard(
    policy: BatchInstallPolicy,
    onPolicyChange: (BatchInstallPolicy) -> Unit
) {
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
            icon = Icons.Outlined.InstallMobile,
            title = stringResource(R.string.batch_patch_policy_install),
            subtitle = stringResource(R.string.batch_patch_policy_install_description)
        )
    }
}

@Composable
private fun BatchItemCard(
    item: BatchPatchItem,
    editable: Boolean,
    onAttach: () -> Unit,
    onToggleExcluded: () -> Unit,
    onForceVersion: () -> Unit,
    onEditPatches: (() -> Unit)? = null,
    onPickSource: (() -> Unit)? = null,
    onInstall: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onShowError: () -> Unit = {}
) {
    val excluded = item.state == BatchItemState.EXCLUDED
    val failed = !editable && item.state == BatchItemState.FAILED && !item.message.isNullOrBlank()
    val hasResultActions = !editable && (onInstall != null || onOpen != null || failed)

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MorpheDefaults.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = item.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = LocalDialogTextColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Once installing has been tried, its outcome is the newer and more
                        // useful fact about the app than how the patching went
                        when (item.installOutcome) {
                            BatchInstallOutcome.INSTALLED -> PillBadge(
                                text = stringResource(R.string.installed),
                                style = InfoBadgeStyle.Success
                            )

                            BatchInstallOutcome.FAILED -> PillBadge(
                                text = stringResource(R.string.batch_patch_install_failed),
                                style = InfoBadgeStyle.Error
                            )

                            null -> BatchStateBadge(item.state)
                        }
                    }

                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current,
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
                            LocalDialogSecondaryTextColor.current
                        },
                        // An install failure explains what to do about it, so it is shown in
                        // full. Patcher errors are raw stack traces and stay clamped
                        maxLines = if (installFailure != null) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(
                visible = editable,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                Column {
                    MorpheSettingsDivider()
                    ActionPillRow(
                        modifier = Modifier.padding(
                            horizontal = MorpheDefaults.ContentPadding,
                            vertical = MorpheDefaults.ItemSpacing
                        )
                    ) {
                        val attachLabel = stringResource(R.string.batch_patch_attach_apk)
                        ActionPillButton(
                            onClick = onAttach,
                            icon = Icons.Outlined.FileOpen,
                            contentDescription = attachLabel,
                            tooltip = attachLabel
                        )

                        if (onEditPatches != null) {
                            val editLabel = stringResource(R.string.batch_patch_edit_patches)
                            ActionPillButton(
                                onClick = onEditPatches,
                                icon = Icons.Outlined.Tune,
                                contentDescription = editLabel,
                                tooltip = editLabel
                            )
                        }

                        if (onPickSource != null) {
                            val sourceLabel = stringResource(R.string.home_simple_bundle_select_title)
                            ActionPillButton(
                                onClick = onPickSource,
                                icon = Icons.Outlined.Layers,
                                contentDescription = sourceLabel,
                                tooltip = sourceLabel
                            )
                        }

                        if (item.state == BatchItemState.VERSION_MISMATCH) {
                            val forceLabel = stringResource(R.string.batch_patch_force_version)
                            ActionPillButton(
                                onClick = onForceVersion,
                                icon = Icons.Outlined.Warning,
                                contentDescription = forceLabel,
                                tooltip = forceLabel,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }

                        val toggleLabel = stringResource(
                            if (excluded) R.string.include else R.string.exclude
                        )
                        ActionPillButton(
                            onClick = onToggleExcluded,
                            icon = if (excluded) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline,
                            contentDescription = toggleLabel,
                            tooltip = toggleLabel,
                            colors = if (excluded) {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasResultActions,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                Column {
                    MorpheSettingsDivider()
                    ActionPillRow(
                        modifier = Modifier.padding(
                            horizontal = MorpheDefaults.ContentPadding,
                            vertical = MorpheDefaults.ItemSpacing
                        )
                    ) {
                        if (failed) {
                            val errorLabel = stringResource(R.string.error_)
                            ActionPillButton(
                                onClick = onShowError,
                                icon = Icons.Outlined.ErrorOutline,
                                contentDescription = errorLabel,
                                tooltip = errorLabel,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }

                        if (onInstall != null) {
                            val installLabel = stringResource(R.string.install)
                            ActionPillButton(
                                onClick = onInstall,
                                icon = Icons.Outlined.InstallMobile,
                                contentDescription = installLabel,
                                tooltip = installLabel,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        if (onOpen != null) {
                            val openLabel = stringResource(R.string.open)
                            ActionPillButton(
                                onClick = onOpen,
                                icon = Icons.AutoMirrored.Outlined.Launch,
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

/** Second detail line: either why the app cannot run, or what it will be patched from. */
@Composable
private fun itemDetails(item: BatchPatchItem): String = when (item.state) {
    BatchItemState.NEEDS_APK -> item.message?.let {
        stringResource(R.string.batch_patch_wrong_package, it)
    } ?: stringResource(R.string.batch_patch_needs_apk)

    BatchItemState.NO_PATCHES -> stringResource(R.string.home_no_patches_available)

    BatchItemState.VERSION_MISMATCH -> stringResource(
        R.string.batch_patch_version_mismatch,
        item.version.orEmpty()
    )

    BatchItemState.FAILED -> item.message ?: stringResource(R.string.patcher_unknown_error)

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
            item.patchCount
        )
        // Only the sources actually contributing patches, so narrowing an app to one source
        // is reflected here instead of still listing everything the plan looked at
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
    val (labelRes, style) = when (state) {
        BatchItemState.READY -> R.string.ready to InfoBadgeStyle.Primary
        BatchItemState.RUNNING -> R.string.patching to InfoBadgeStyle.Primary
        BatchItemState.SUCCEEDED -> R.string.done to InfoBadgeStyle.Success
        BatchItemState.FAILED -> R.string.failed to InfoBadgeStyle.Error
        BatchItemState.CANCELLED -> R.string.cancelled to InfoBadgeStyle.Default
        BatchItemState.EXCLUDED -> R.string.excluded to InfoBadgeStyle.Default
        BatchItemState.NEEDS_APK -> R.string.batch_patch_state_no_apk to InfoBadgeStyle.Error
        BatchItemState.VERSION_MISMATCH -> R.string.version to InfoBadgeStyle.Warning
        BatchItemState.NO_PATCHES -> R.string.batch_patch_state_no_patches to InfoBadgeStyle.Error
    }
    PillBadge(text = stringResource(labelRes), style = style)
}

