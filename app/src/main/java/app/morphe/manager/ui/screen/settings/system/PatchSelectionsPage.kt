/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SelectionApp
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Snapshot of package/bundle selection counts. */
@Immutable
data class PatchSelectionData(
    val selections: Map<String, Map<Int, Int>>,
    val totalSelections: Int,
    val bundleNames: Map<Int, String>,
    /** The apps of [selections] and of the sets named and in order, null while being resolved. */
    val apps: List<SelectionApp>?,
    /** Whether any app has a set kept for it, which keeps the page from reading as empty. */
    val hasSets: Boolean
)

/** Multi-select state and its mutation callbacks. */
@Stable
class PatchSelectionMultiSelect(
    val selectedPackages: SelectionState<String>,
    val isSelectionMode: Boolean,
    val onEnterSelection: (String) -> Unit,
    val onToggleSelection: (String) -> Unit
)

/**
 * Entry into the patch selections expert mode saves per app and source, a row of the group it
 * is placed in, opening [PatchSelectionsPage].
 */
@Composable
fun PatchSelectionsItem(onClick: () -> Unit) {
    SettingsItem(
        onClick = onClick,
        title = stringResource(R.string.settings_system_patch_selections_title),
        subtitle = stringResource(R.string.settings_system_patch_selections_description),
        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Tune) }
    )
}

/**
 * The patch selections expert mode saves, each app a block of the sources it has one from, on a
 * page of their own. A selection made of the apps resets them together, and one source's
 * selection opens over the page, see [PatchDetailsDialog].
 *
 * @param onBack Leaves the page for the settings it was opened from.
 */
@Composable
fun PatchSelectionsPage(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val showResetAllConfirmation = remember { mutableStateOf(false) }
    val showResetSelectedConfirmation = remember { mutableStateOf(false) }
    val resetTarget = remember { mutableStateOf<ResetTarget?>(null) }
    val showPatchDetailsTarget = remember { mutableStateOf<PatchDetailsTarget?>(null) }
    val copyTarget = remember { mutableStateOf<CopyTarget?>(null) }
    val pendingSetDelete = remember { mutableStateOf<SelectionSetSummary?>(null) }
    val pendingSetRename = remember { mutableStateOf<SelectionSetSummary?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val selections by settingsViewModel.selectionsSummary.collectAsStateWithLifecycle()
    val bundleNames by settingsViewModel.bundleNames.collectAsStateWithLifecycle()
    val sets by settingsViewModel.selectionSets.collectAsStateWithLifecycle()
    val selectionApps by remember { settingsViewModel.selectionApps() }.collectAsStateWithLifecycle(initialValue = null)

    val totalSelections = remember(selections) {
        selections.values.sumOf { bundleMap -> bundleMap.values.sum() }
    }

    val selectedPackages = rememberSelectionState<String>()
    val isSelectionMode = remember { mutableStateOf(false) }

    LaunchedEffect(selections) {
        val currentPackages = selections.keys
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) isSelectionMode.value = false
    }

    val exitSelection = {
        isSelectionMode.value = false
        selectedPackages.clear()
    }

    PatchSelectionsContent(
        data = PatchSelectionData(
            selections = selections,
            totalSelections = totalSelections,
            bundleNames = bundleNames,
            apps = selectionApps,
            hasSets = sets.isNotEmpty()
        ),
        multiSelect = PatchSelectionMultiSelect(
            selectedPackages = selectedPackages,
            isSelectionMode = isSelectionMode.value,
            onEnterSelection = { pkg ->
                isSelectionMode.value = true
                selectedPackages.toggle(pkg)
            },
            onToggleSelection = { pkg -> selectedPackages.toggle(pkg) }
        ),
        importExportViewModel = importExportViewModel,
        onBack = onBack,
        onShowResetAllConfirmation = { showResetAllConfirmation.value = true },
        onSetResetTarget = { resetTarget.value = it },
        onShowPatchDetails = { showPatchDetailsTarget.value = it },
        onOpenCopyFromBundle = { target ->
            copyTarget.value = target
            settingsViewModel.openCopy(target)
        },
        onDeleteSet = { pendingSetDelete.value = it },
        onRenameSet = { pendingSetRename.value = it },
        // Written to the database at once here, unlike the expert-mode panel, which stages its
        // changes until the user proceeds to patching
        selectionPanel = copyTarget.value?.takeIf { settingsViewModel.selectionPanel.isOpen }?.let { target ->
            {
                val panel = settingsViewModel.selectionPanel
                SelectionPanel(
                    title = target.appDisplayName,
                    sets = null,
                    copyTargetName = bundleNames[target.bundleUid]
                        ?: stringResource(R.string.settings_system_patch_selection_source_format, target.bundleUid),
                    candidates = panel.candidates,
                    onCopy = { candidate -> settingsViewModel.copySelectionFromBundle(target, candidate) },
                    onClose = panel::close
                )
            }
        },
        onImportUriPicked = { pendingImportUri = it },
        onExitSelection = exitSelection,
        onSelectAll = { selectedPackages.setAll(selections.keys) },
        onShowResetSelectedConfirmation = { showResetSelectedConfirmation.value = true }
    )

    DialogHost(target = showPatchDetailsTarget.value) { target ->
        PatchDetailsDialog(
            target = target,
            bundleNames = bundleNames,
            settingsViewModel = settingsViewModel,
            onDismiss = { showPatchDetailsTarget.value = null }
        )
    }

    DialogHost(target = pendingSetDelete.value) { set ->
        DeleteSelectionSetDialog(
            set = set,
            onConfirm = {
                settingsViewModel.deleteSet(set)
                pendingSetDelete.value = null
            },
            onDismiss = { pendingSetDelete.value = null }
        )
    }

    DialogHost(target = pendingSetRename.value) { set ->
        RenameSelectionSetDialog(
            set = set,
            takenNames = sets.filter { it.packageName == set.packageName }.mapTo(mutableSetOf()) { it.name },
            onConfirm = { name ->
                settingsViewModel.renameSet(set, name)
                pendingSetRename.value = null
            },
            onDismiss = { pendingSetRename.value = null }
        )
    }

    DialogHost(visible = showResetSelectedConfirmation.value) {
        val selectedKeys = selectedPackages.keys.toList()
        val selectedTotalPatches = remember(selections, selectedKeys) {
            selectedKeys.sumOf { pkg ->
                selections[pkg]?.values?.sum() ?: 0
            }
        }
        ConfirmResetSelectedDialog(
            packageCount = selectedKeys.size,
            totalPatches = selectedTotalPatches,
            onConfirm = {
                scope.launch {
                    selectedKeys.forEach { settingsViewModel.resetSelectionsForPackage(it) }
                    exitSelection()
                    showResetSelectedConfirmation.value = false
                }
            },
            onDismiss = { showResetSelectedConfirmation.value = false }
        )
    }

    // Import-mode dialog: user picks Replace or Merge before selections are applied
    DialogHost(target = pendingImportUri) { uri ->
        ImportModeDialog(
            titleRes = R.string.settings_system_import_selections_mode_title,
            descriptionRes = R.string.settings_system_import_selections_mode_description,
            onDismiss = { pendingImportUri = null },
            onSelect = { mode ->
                importExportViewModel.importAllSelections(uri, mode)
                pendingImportUri = null
            }
        )
    }

    // Reset all confirmation dialog
    DialogHost(visible = showResetAllConfirmation.value) {
        ConfirmResetAllDialog(
            totalSelections = totalSelections,
            packageCount = selections.size,
            settingsViewModel = settingsViewModel,
            onConfirm = {
                scope.launch {
                    settingsViewModel.resetAllSelections()
                    showResetAllConfirmation.value = false
                }
            },
            onDismiss = { showResetAllConfirmation.value = false }
        )
    }

    // Reset specific target confirmation dialog
    DialogHost(target = resetTarget.value) { target ->
        ConfirmResetPackageBundleDialog(
            packageName = target.packageName,
            bundleUid = target.bundleUid,
            bundleName = bundleNames[target.bundleUid],
            patchCount = selections[target.packageName]?.get(target.bundleUid) ?: 0,
            settingsViewModel = settingsViewModel,
            onConfirm = {
                scope.launch {
                    settingsViewModel.resetSelectionsForPackageBundle(target.packageName, target.bundleUid)
                    resetTarget.value = null
                }
            },
            onDismiss = { resetTarget.value = null }
        )
    }
}

/**
 * The page itself: the apps under a header with the search and a reset of them all, over either
 * the import and export of the selections or, while apps are being selected, what can be done
 * with them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchSelectionsContent(
    data: PatchSelectionData,
    multiSelect: PatchSelectionMultiSelect,
    importExportViewModel: ImportExportViewModel,
    onBack: () -> Unit,
    onShowResetAllConfirmation: () -> Unit,
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    onOpenCopyFromBundle: (CopyTarget) -> Unit,
    onDeleteSet: (SelectionSetSummary) -> Unit,
    onRenameSet: (SelectionSetSummary) -> Unit,
    selectionPanel: (@Composable () -> Unit)?,
    onImportUriPicked: (Uri) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onShowResetSelectedConfirmation: () -> Unit
) {
    val selections = data.selections
    val gutter = rememberWindowSize().contentPadding
    val listState = rememberLazyListState()
    val openImportAllSelectionsPicker = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri -> uri?.let(onImportUriPicked) }
    )

    val exportAllSelections = rememberDocumentSaver(JSON_MIMETYPE, importExportViewModel::exportAllSelections)

    // Nothing to narrow down with a single entry
    val isSearchable = selections.size >= 2
    // Hoisted out of the list so the title action can drive it
    val search = rememberSearchFieldState(searchable = isSearchable)
    val canResetAll = !multiSelect.isSelectionMode && selections.isNotEmpty()
    // Registered inside the page so it outranks the way back out of it
    SearchFieldBackHandler(search)

    PinnedHeaderPage(
        header = {
            PageHeader(
                title = stringResource(R.string.settings_system_patch_selections_title),
                subtitle = listOf(
                    pluralStringResource(R.plurals.package_count, selections.size, selections.size.toString()),
                    pluralStringResource(R.plurals.patch_count, data.totalSelections, data.totalSelections.toString())
                ).joinToString(" · "),
                onBack = onBack,
                search = search,
                searchLabel = stringResource(R.string.home_search_apps),
                searchEnabled = isSearchable
            ) {
                TitleAction(
                    icon = MaterialSymbols.Rounded.History,
                    contentDescription = stringResource(R.string.reset),
                    onClick = onShowResetAllConfirmation,
                    style = TitleActionStyle.Destructive,
                    enabled = canResetAll
                )
            }
        },
        scrollState = listState,
        bottomBar = {
            when {
                selectionPanel != null -> selectionPanel()

                multiSelect.isSelectionMode -> DockedPanel(visible = true, onBack = onExitSelection) {
                    SelectionActionBar(
                        selectedCount = multiSelect.selectedPackages.size,
                        totalCount = selections.size,
                        onSelectAll = onSelectAll,
                        onDeselectAll = { multiSelect.selectedPackages.clear() },
                        onCancel = onExitSelection,
                        actions = listOf(
                            SelectionAction(
                                icon = MaterialSymbols.Rounded.Delete,
                                label = stringResource(R.string.reset),
                                onClick = onShowResetSelectedConfirmation,
                                tone = ActionTone.Destructive
                            )
                        )
                    )
                }

                // Out of the search's way while the keyboard is up, which wants every row of room
                !WindowInsets.isImeVisible -> Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(start = gutter, end = gutter, bottom = Defaults.SpaceLarge)
                ) {
                    ImportExportFooter(
                        onImport = { openImportAllSelectionsPicker() },
                        onExport = if (selections.isNotEmpty() || data.hasSets) {
                            { exportAllSelections(importExportViewModel.getAllSelectionsExportFileName()) }
                        } else null
                    )
                }
            }
        }
    ) { insets ->
        val apps = data.apps
        if (selections.isEmpty() && !data.hasSets) {
            EmptyState(
                message = stringResource(R.string.settings_system_no_patches_or_options),
                modifier = Modifier.padding(
                    start = insets.horizontal,
                    top = insets.top + Defaults.SpaceMedium,
                    end = insets.horizontal
                )
            )
        } else if (apps != null) {
            SelectionList(
                apps = apps,
                bundleNames = data.bundleNames,
                multiSelect = multiSelect,
                importExportViewModel = importExportViewModel,
                search = search,
                listState = listState,
                contentPadding = PaddingValues(
                    start = insets.horizontal,
                    top = insets.top + Defaults.SpaceMedium,
                    end = insets.horizontal,
                    bottom = insets.bottom + Defaults.SpaceLarge
                ),
                onSetResetTarget = onSetResetTarget,
                onShowPatchDetails = onShowPatchDetails,
                onOpenCopyFromBundle = onOpenCopyFromBundle,
                onDeleteSet = onDeleteSet,
                onRenameSet = onRenameSet,
                onImport = openImportAllSelectionsPicker
            )
        }

        ListScrollbar(
            listState = listState,
            modifier = Modifier.padding(
                top = insets.top + Defaults.SpaceMedium,
                bottom = insets.bottom + Defaults.SpaceMedium
            )
        )
        ScrollToTopButton(listState = listState, extraBottomPadding = insets.bottom)
    }
}

/**
 * The apps with selections, each a block: its header, then a row for every source it has a
 * selection from and for every set kept for it while it is folded out.
 */
@Composable
private fun SelectionList(
    apps: List<SelectionApp>,
    bundleNames: Map<Int, String>,
    multiSelect: PatchSelectionMultiSelect,
    importExportViewModel: ImportExportViewModel,
    search: SearchFieldState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    onOpenCopyFromBundle: (CopyTarget) -> Unit,
    onDeleteSet: (SelectionSetSummary) -> Unit,
    onRenameSet: (SelectionSetSummary) -> Unit,
    onImport: () -> Unit
) {
    val expandedPackages = remember { mutableStateOf<Set<String>>(emptySet()) }
    // Named and in order already, so the search only narrows them down
    val shownApps = remember(apps, search.query) {
        val query = search.query
        if (query.isBlank()) apps
        else apps.filter {
            it.packageName.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true)
        }
    }

    val toggleExpanded = { packageName: String ->
        expandedPackages.value = if (packageName in expandedPackages.value) {
            expandedPackages.value - packageName
        } else {
            expandedPackages.value + packageName
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollFade(listState),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = contentPadding
    ) {
        if (shownApps.isEmpty()) {
            // No matches for the current search query
            item(key = "search_empty") {
                EmptyState(
                    message = stringResource(R.string.search_no_results),
                    icon = MaterialSymbols.Rounded.Search_off
                )
            }
        }

        // Each app a block: its header, then a row for every source it has a selection from and
        // for every set kept for it
        shownApps.forEach { app ->
            val packageName = app.packageName
            val bundleMap = app.bundles
            // Folded in selection mode, so a tap on the header always selects the app
            val expanded = packageName in expandedPackages.value && !multiSelect.isSelectionMode
            val rows = if (expanded) bundleMap.size + app.sets.size + 1 else 1

            item(key = packageName) {
                PackageSelectionHeader(
                    packageName = packageName,
                    displayName = app.displayName,
                    appDataSource = app.dataSource,
                    bundleMap = bundleMap,
                    setCount = app.sets.size,
                    multiSelect = multiSelect,
                    expanded = expanded,
                    onToggleExpanded = { toggleExpanded(packageName) },
                    segment = ListSegment.of(0, rows),
                    modifier = Modifier.animatedListItem(this)
                )
            }

            if (expanded) {
                bundleMap.entries.forEachIndexed { index, (bundleUid, patchCount) ->
                    item(key = "$packageName:$bundleUid") {
                        BundleSelectionItem(
                            packageName = packageName,
                            bundleUid = bundleUid,
                            bundleName = bundleNames[bundleUid],
                            patchCount = patchCount,
                            importExportViewModel = importExportViewModel,
                            onReset = { onSetResetTarget(ResetTarget(packageName, bundleUid)) },
                            onShowDetails = {
                                onShowPatchDetails(PatchDetailsTarget.Source(packageName, app.displayName, bundleUid))
                            },
                            onCopyFromBundle = {
                                onOpenCopyFromBundle(CopyTarget(packageName, bundleUid, app.displayName))
                            },
                            onImport = onImport,
                            segment = ListSegment.of(index + 1, rows),
                            modifier = Modifier.animatedListItem(this)
                        )
                    }
                }
                app.sets.forEachIndexed { index, set ->
                    item(key = "$packageName:set:${set.uid}") {
                        SelectionSetItem(
                            packageName = packageName,
                            set = set,
                            onShowDetails = { onShowPatchDetails(PatchDetailsTarget.Set(app.displayName, set)) },
                            onDelete = { onDeleteSet(set) },
                            onRename = { onRenameSet(set) },
                            segment = ListSegment.of(bundleMap.size + index + 1, rows),
                            modifier = Modifier.animatedListItem(this)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row of an app's block for a set kept for it: its name and how many patches it selects, which
 * opens them, and what can be done with it. A set is switched to from the expert-mode panel, so
 * here it can only be renamed or deleted.
 */
@Composable
private fun SelectionSetItem(
    packageName: String,
    set: SelectionSetSummary,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    segment: ListSegment,
    modifier: Modifier = Modifier
) {
    SelectionSegment(packageName = packageName, segment = segment, onClick = onShowDetails, modifier = modifier) {
        InfoRow(
            icon = MaterialSymbols.Rounded.Bookmark,
            label = set.name,
            value = pluralStringResource(R.plurals.patch_count, set.patchCount, set.patchCount.toString()),
            trailing = { RowActionsMenu(selectionSetActions(onRename = onRename, onDelete = onDelete)) },
            contentPadding = SegmentRowPadding
        )
    }
}

/**
 * One row of an app's block, edged in the app's color the way the home screen wears it, so the
 * app reads as itself here too. [onClick] makes the whole of it the row's tap.
 */
@Composable
private fun SelectionSegment(
    packageName: String,
    segment: ListSegment,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SectionCard(
        onClick = onClick,
        accentColor = rememberAppColor(packageName),
        shape = segment.shape,
        modifier = modifier.endsBlock(segment.endsBlock),
        content = content
    )
}

/**
 * Header of an app's block: the app, how many patches its selections hold and across how many
 * sources. A tap folds the block out, or selects the app while a selection is being made.
 */
@Composable
private fun PackageSelectionHeader(
    packageName: String,
    displayName: String,
    appDataSource: AppDataSource,
    bundleMap: Map<Int, Int>,
    setCount: Int,
    multiSelect: PatchSelectionMultiSelect,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    segment: ListSegment,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isSelected = multiSelect.selectedPackages.contains(packageName)
    val isSelectionMode = multiSelect.isSelectionMode
    val totalPatches = remember(bundleMap) { bundleMap.values.sum() }
    val selectLabel = stringResource(R.string.accessibility_select_app)

    // The gap below goes outside the selection, so the check sits on the card rather than the gap
    SelectableCard(
        modifier = modifier.endsBlock(segment.endsBlock),
        isSelected = isSelected,
        isSelectionMode = isSelectionMode
    ) {
        SelectionSegment(packageName = packageName, segment = segment.copy(endsBlock = false)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shapedCombinedClickable(
                        shape = segment.shape,
                        onClick = {
                            if (isSelectionMode) multiSelect.onToggleSelection(packageName) else onToggleExpanded()
                        },
                        onLongClickLabel = selectLabel.takeIf { !isSelectionMode },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            multiSelect.onEnterSelection(packageName)
                        }
                    )
                    .semantics { if (isSelectionMode) selected = isSelected }
                    .padding(Defaults.SpaceLarge),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = packageName,
                    contentDescription = displayName,
                    modifier = Modifier.size(48.dp),
                    preferredSource = appDataSource
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Cloned copies of an app carry the same name, so the package is what
                    // says which of them a selection belongs to
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    StatusBadgeRow {
                        // An app with only sets kept for it has no selection to count
                        if (bundleMap.isNotEmpty()) {
                            StatusBadge(
                                text = pluralStringResource(
                                    R.plurals.patch_count,
                                    totalPatches,
                                    totalPatches.toString()
                                ),
                                tone = SemanticTone.Primary,
                                // Marked as the source rows below mark their patches
                                icon = MaterialSymbols.Rounded.Extension
                            )
                        }

                        if (bundleMap.size > 1) {
                            StatusBadge(
                                text = pluralStringResource(
                                    R.plurals.source_count,
                                    bundleMap.size,
                                    bundleMap.size.toString()
                                ),
                                tone = SemanticTone.Neutral
                            )
                        }

                        if (setCount > 0) {
                            StatusBadge(
                                text = pluralStringResource(
                                    R.plurals.selection_set_count,
                                    setCount,
                                    setCount.toString()
                                ),
                                tone = SemanticTone.Neutral,
                                icon = MaterialSymbols.Rounded.Bookmark
                            )
                        }
                    }
                }

                // A tap selects rather than folds in selection mode, so the mark takes the chevron's place
                SelectionMark(
                    selected = isSelected.takeIf { isSelectionMode },
                    idle = { ExpandChevron(expanded = expanded, announced = true) }
                )
            }
        }
    }
}

/**
 * Row of an app's block for one source it has a selection from: how many patches the selection
 * holds, which opens them, and what can be done with it, behind its menu as a set's is.
 */
@Composable
private fun BundleSelectionItem(
    packageName: String,
    bundleUid: Int,
    bundleName: String?,
    patchCount: Int,
    importExportViewModel: ImportExportViewModel,
    onReset: () -> Unit,
    onShowDetails: () -> Unit,
    onCopyFromBundle: () -> Unit,
    onImport: () -> Unit,
    segment: ListSegment,
    modifier: Modifier = Modifier
) {

    // Display bundle name or fallback to "Bundle #N"
    val displayName = bundleName
        ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
    val patchCountText = pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString())

    val export = rememberDocumentSaver(JSON_MIMETYPE) { uri ->
        importExportViewModel.exportPackageBundleData(packageName, bundleUid, bundleName, uri)
    }

    SelectionSegment(packageName = packageName, segment = segment, onClick = onShowDetails, modifier = modifier) {
        InfoRow(
            icon = MaterialSymbols.Rounded.Extension,
            label = displayName,
            value = patchCountText,
            contentPadding = SegmentRowPadding,
            trailing = {
                RowActionsMenu(
                    listOf(
                        SelectionAction(
                            icon = MaterialSymbols.Rounded.Content_copy,
                            label = stringResource(R.string.copy),
                            onClick = onCopyFromBundle
                        ),
                        SelectionAction(
                            icon = MaterialSymbols.Rounded.Download,
                            label = stringResource(R.string.import_),
                            onClick = onImport
                        ),
                        SelectionAction(
                            icon = MaterialSymbols.Rounded.Upload,
                            label = stringResource(R.string.export),
                            onClick = {
                                export(
                                    importExportViewModel.getPackageBundleDataExportFileName(
                                        packageName, bundleUid, bundleName
                                    )
                                )
                            }
                        ),
                        SelectionAction(
                            icon = MaterialSymbols.Rounded.History,
                            label = stringResource(R.string.reset),
                            onClick = onReset,
                            tone = ActionTone.Destructive
                        )
                    )
                )
            }
        )
    }
}

/**
 * Confirmation dialog for resetting selections across the currently selected packages.
 */
@Composable
private fun DialogHostScope.ConfirmResetSelectedDialog(
    packageCount: Int,
    totalPatches: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val patchesText = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches.toString())
    val packagesText = pluralStringResource(R.plurals.package_count, packageCount, packageCount.toString())
    ConfirmDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_selected_confirm_title),
        message = stringResource(R.string.settings_system_patch_selection_reset_selected_warning),
        primaryText = stringResource(R.string.reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        items = listOfNotNull(
            ConfirmItem(MaterialSymbols.Rounded.Delete, stringResource(R.string.settings_system_patch_selection_total_summary_format, patchesText, packagesText))
        )
    )
}

/**
 * Confirmation dialog for resetting all selections.
 * Options count is loaded via [SettingsViewModel].
 */
@Composable
private fun DialogHostScope.ConfirmResetAllDialog(
    totalSelections: Int,
    packageCount: Int,
    settingsViewModel: SettingsViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var totalOptions by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        totalOptions = settingsViewModel.loadTotalOptionsCount()
    }

    val patchesText = pluralStringResource(R.plurals.patch_count, totalSelections, totalSelections.toString())
    val packagesText = pluralStringResource(R.plurals.package_count, packageCount, packageCount.toString())
    ConfirmDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_all_confirm_title),
        message = stringResource(R.string.settings_system_patch_selection_reset_all_warning),
        primaryText = stringResource(R.string.reset_all),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        items = listOfNotNull(
            ConfirmItem(MaterialSymbols.Rounded.Delete, stringResource(R.string.settings_system_patch_selection_total_summary_format, patchesText, packagesText)),
            ConfirmItem(MaterialSymbols.Rounded.Tune, pluralStringResource(R.plurals.option_count, totalOptions, totalOptions.toString())).takeIf { totalOptions > 0 }
        )
    )
}

/**
 * Confirmation dialog for resetting package-bundle selections.
 */
@Composable
private fun DialogHostScope.ConfirmResetPackageBundleDialog(
    packageName: String,
    bundleUid: Int,
    bundleName: String?,
    patchCount: Int,
    settingsViewModel: SettingsViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(packageName) }
    var optionsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(packageName, bundleUid) {
        val (name, _) = settingsViewModel.resolveAppDisplayName(packageName)
        displayName = name
        optionsCount = settingsViewModel.loadOptionsCountForBundle(packageName, bundleUid)
    }

    val bundleDisplayName = bundleName
        ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
    ConfirmDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_source_confirm_title),
        message = htmlAnnotatedString(stringResource(R.string.settings_system_patch_selection_reset_source_warning, displayName, bundleDisplayName)),
        primaryText = stringResource(R.string.reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        items = listOfNotNull(
            ConfirmItem(MaterialSymbols.Rounded.Delete, pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString())),
            ConfirmItem(MaterialSymbols.Rounded.Tune, pluralStringResource(R.plurals.option_count, optionsCount, optionsCount.toString())).takeIf { optionsCount > 0 }
        )
    )
}

/**
 * The patches and options saved for one app, over the selections page: what a source's selection
 * holds, or a set's, source by source.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogHostScope.PatchDetailsDialog(
    target: PatchDetailsTarget,
    bundleNames: Map<Int, String>,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var details by remember { mutableStateOf<Map<Int, SettingsViewModel.PatchDetails>?>(null) }

    LaunchedEffect(target) {
        details = null
        details = when (target) {
            is PatchDetailsTarget.Source -> mapOf(
                target.bundleUid to settingsViewModel.loadPatchDetails(target.packageName, target.bundleUid)
            )
            is PatchDetailsTarget.Set -> settingsViewModel.loadSetDetails(target.set)
        }
    }

    val sourceFormat = stringResource(R.string.settings_system_patch_selection_source_format)
    fun sourceName(bundleUid: Int) = bundleNames[bundleUid] ?: sourceFormat.format(bundleUid)
    // A source's own selection is headed as such, a set's by the source each part comes from
    val selectedTitle = stringResource(R.string.settings_system_selected_patches_section)
    val sections = remember(details, bundleNames) {
        details.orEmpty().map { (bundleUid, loaded) ->
            // Options outlive their patch being deselected, so those are listed too, dimmed as not in effect
            val selected = loaded.patchList.toSet()
            DetailsSection(
                title = if (target is PatchDetailsTarget.Set) sourceName(bundleUid) else selectedTitle,
                count = loaded.patchList.size,
                entries = patchEntries(
                    keys = (loaded.patchList + loaded.optionsMap.keys).distinct(),
                    options = loaded.optionsMap,
                    displayName = loaded.displayNames::get,
                    dimmed = { it !in selected }
                )
            )
        }.filter { it.entries.isNotEmpty() }
    }
    val subtitle = when (target) {
        is PatchDetailsTarget.Source -> sourceName(target.bundleUid)
        is PatchDetailsTarget.Set -> target.set.name
    }
    val copyToClipboard = rememberCopyToClipboard()

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier ->
            AppIcon(packageName = target.packageName, contentDescription = null, modifier = modifier)
        },
        title = target.appDisplayName,
        subtitle = subtitle,
        accentColor = rememberAppColor(target.packageName),
        actions = listOf(
            DialogAction(
                text = stringResource(R.string.copy),
                icon = MaterialSymbols.Rounded.Content_copy,
                enabled = sections.isNotEmpty(),
                onClick = {
                    val lists = when (target) {
                        is PatchDetailsTarget.Source -> sections.map { subtitle to it.entries }
                        is PatchDetailsTarget.Set -> sections.map { it.title to it.entries }
                    }
                    copyToClipboard(patchListText(title = target.appDisplayName, lists = lists))
                }
            )
        )
    ) {
        if (details == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceExtraExtraLarge),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (sections.isEmpty()) {
            Notice(
                text = stringResource(R.string.settings_system_no_patches_or_options),
                tone = SemanticTone.Neutral,
                isCentered = true
            )
        } else {
            sections.forEach { section ->
                LabeledSection(
                    title = section.title,
                    count = section.count,
                    icon = MaterialSymbols.Rounded.Checklist
                ) {
                    PatchEntryList(section.entries)
                }
            }
        }
    }
}

/** One source's part of a [PatchDetailsDialog]. */
private class DetailsSection(val title: String, val count: Int, val entries: List<PatchEntry>)

/** How far the header's chevron sits in from its end, centered in the slot of its selection mark. */
private val HeaderMarkInset = (SelectionMarkSize - Defaults.IconSize) / 2

/**
 * Room inside an [InfoRow] standing as a row of an app's block on its own, rather than in a panel,
 * the header's room, so its icon and text line up with the header above. Its end is set so the
 * icon of its menu button sits under the header's chevron.
 */
private val SegmentRowPadding = PaddingValues(
    start = Defaults.SpaceLarge,
    top = Defaults.SpaceLarge,
    end = Defaults.SpaceLarge + HeaderMarkInset,
    bottom = Defaults.SpaceLarge
)


/** The selections of one app from one source, which a reset waits on a confirmation for. */
private data class ResetTarget(val packageName: String, val bundleUid: Int)

/** What a [PatchDetailsDialog] lists: the selection saved from one source, or a set. */
private sealed interface PatchDetailsTarget {
    val packageName: String
    val appDisplayName: String

    data class Source(
        override val packageName: String,
        override val appDisplayName: String,
        val bundleUid: Int
    ) : PatchDetailsTarget

    data class Set(override val appDisplayName: String, val set: SelectionSetSummary) : PatchDetailsTarget {
        override val packageName get() = set.packageName
    }
}

/** Destination (package + bundle) for a copy-from-another-bundle operation. */
data class CopyTarget(
    val packageName: String,
    val bundleUid: Int,
    val appDisplayName: String
)
