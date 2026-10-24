/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    /** The apps of [selections] named and in order, null while their names are being resolved. */
    val apps: List<SelectionApp>?
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
    val copyCandidates = remember { mutableStateOf<List<CopySelectionCandidate>?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val selections by settingsViewModel.selectionsSummary.collectAsStateWithLifecycle()
    val bundleNames by settingsViewModel.bundleNames.collectAsStateWithLifecycle()
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
            apps = selectionApps
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
            copyCandidates.value = null
            scope.launch {
                val loaded = settingsViewModel.loadCopySelectionCandidates(
                    targetPackageName = target.packageName,
                    targetBundleUid = target.bundleUid
                )
                // Discard the result if the picker was closed or retargeted while loading.
                if (copyTarget.value == target) copyCandidates.value = loaded
            }
        },
        onImportUriPicked = { pendingImportUri = it },
        onExitSelection = exitSelection,
        onSelectAll = { selectedPackages.setAll(selections.keys) },
        onShowResetSelectedConfirmation = { showResetSelectedConfirmation.value = true }
    )

    DialogHost(target = showPatchDetailsTarget.value) { target ->
        PatchDetailsDialog(
            packageName = target.packageName,
            bundleUid = target.bundleUid,
            appDisplayName = target.appDisplayName,
            bundleName = bundleNames[target.bundleUid],
            settingsViewModel = settingsViewModel,
            onDismiss = { showPatchDetailsTarget.value = null }
        )
    }

    // Confirmed picks are written to the database immediately here, unlike the expert-mode
    // path which stages changes until the user proceeds to patching.
    DialogHost(target = copyTarget.value) { target ->
        CopySelectionFromBundleDialog(
            target = CopySelectionTarget(
                packageName = target.packageName,
                bundleUid = target.bundleUid,
                bundleName = bundleNames[target.bundleUid]
                    ?: stringResource(R.string.settings_system_patch_selection_source_format, target.bundleUid),
                appDisplayName = target.appDisplayName
            ),
            candidates = copyCandidates.value,
            onConfirm = { candidate ->
                scope.launch {
                    settingsViewModel.copySelectionFromBundle(
                        target = target,
                        candidate = candidate
                    )
                    copyTarget.value = null
                    copyCandidates.value = null
                }
            },
            onDismiss = {
                copyTarget.value = null
                copyCandidates.value = null
            }
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

    val exportAllSelectionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let { importExportViewModel.exportAllSelections(it) }
    }

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
                        onExport = if (selections.isNotEmpty()) {
                            {
                                exportAllSelectionsLauncher.launch(
                                    importExportViewModel.getAllSelectionsExportFileName()
                                )
                            }
                        } else null
                    )
                }
            }
        }
    ) { insets ->
        val apps = data.apps
        if (selections.isEmpty()) {
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
 * selection from while it is folded out.
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

        // Each app a block: its header, then a row for every source it has a selection from
        shownApps.forEach { app ->
            val packageName = app.packageName
            val bundleMap = app.bundles
            // Folded in selection mode, so a tap on the header always selects the app
            val expanded = packageName in expandedPackages.value && !multiSelect.isSelectionMode
            val rows = if (expanded) bundleMap.size + 1 else 1

            item(key = packageName) {
                PackageSelectionHeader(
                    packageName = packageName,
                    displayName = app.displayName,
                    appDataSource = app.dataSource,
                    bundleMap = bundleMap,
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
                                onShowPatchDetails(PatchDetailsTarget(packageName, bundleUid, app.displayName))
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
            }
        }
    }
}

/**
 * One row of an app's block, edged in the app's color the way the home screen wears it, so the
 * app reads as itself here too.
 */
@Composable
private fun SelectionSegment(
    packageName: String,
    segment: ListSegment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SectionCard(
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = pluralStringResource(
                                R.plurals.patch_count,
                                totalPatches,
                                totalPatches.toString()
                            ),
                            tone = SemanticTone.Primary
                        )

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
 * holds, which opens them, and what can be done with it.
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

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let {
            importExportViewModel.exportPackageBundleData(packageName, bundleUid, bundleName, it)
        }
    }

    SelectionSegment(packageName = packageName, segment = segment, modifier = modifier) {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            InfoPanel {
                InfoRow(
                    icon = MaterialSymbols.Rounded.Extension,
                    label = displayName,
                    value = patchCountText,
                    onClick = onShowDetails
                )
            }

            ActionPillRow {
                val copyLabel = stringResource(R.string.copy)
                ActionPillButton(
                    onClick = onCopyFromBundle,
                    icon = MaterialSymbols.Rounded.Content_copy,
                    contentDescription = copyLabel,
                    tooltip = copyLabel
                )

                val importLabel = stringResource(R.string.import_)
                ActionPillButton(
                    onClick = onImport,
                    icon = MaterialSymbols.Rounded.Download,
                    contentDescription = importLabel,
                    tooltip = importLabel
                )

                val exportLabel = stringResource(R.string.export)
                ActionPillButton(
                    onClick = {
                        val fileName = importExportViewModel.getPackageBundleDataExportFileName(
                            packageName, bundleUid, bundleName
                        )
                        exportLauncher.launch(fileName)
                    },
                    icon = MaterialSymbols.Rounded.Upload,
                    contentDescription = exportLabel,
                    tooltip = exportLabel
                )

                val resetLabel = stringResource(R.string.reset)
                ActionPillButton(
                    onClick = onReset,
                    icon = MaterialSymbols.Rounded.History,
                    contentDescription = resetLabel,
                    tooltip = resetLabel,
                    destructive = true
                )
            }
        }
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

/** The patch selections and options saved for one package and source, over the selections page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogHostScope.PatchDetailsDialog(
    packageName: String,
    bundleUid: Int,
    appDisplayName: String,
    bundleName: String?,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var details by remember { mutableStateOf<SettingsViewModel.PatchDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load patch selections and options
    LaunchedEffect(packageName, bundleUid) {
        isLoading = true
        details = settingsViewModel.loadPatchDetails(packageName, bundleUid)
        isLoading = false
    }

    val patchList = details?.patchList.orEmpty()
    // Options outlive their patch being deselected, so those are listed too, dimmed as not in effect
    val entries = remember(details) {
        details?.let { loaded ->
            val selected = loaded.patchList.toSet()
            patchEntries(
                keys = (loaded.patchList + loaded.optionsMap.keys).distinct(),
                options = loaded.optionsMap,
                displayName = loaded.displayNames::get,
                dimmed = { it !in selected }
            )
        }.orEmpty()
    }
    val subtitle = bundleName ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
    val copyToClipboard = rememberCopyToClipboard()

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier -> AppIcon(packageName = packageName, contentDescription = null, modifier = modifier) },
        title = appDisplayName,
        subtitle = subtitle,
        accentColor = rememberAppColor(packageName),
        actions = listOf(
            DialogAction(
                text = stringResource(R.string.copy),
                icon = MaterialSymbols.Rounded.Content_copy,
                enabled = entries.isNotEmpty(),
                onClick = {
                    copyToClipboard(patchListText(title = appDisplayName, lists = listOf(subtitle to entries)))
                }
            )
        )
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceExtraExtraLarge),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            if (entries.isNotEmpty()) {
                LabeledSection(
                    title = stringResource(R.string.settings_system_selected_patches_section),
                    count = patchList.size,
                    icon = MaterialSymbols.Rounded.Checklist
                ) {
                    PatchEntryList(entries)
                }
            }

            // Empty state
            if (entries.isEmpty()) {
                Notice(
                    text = stringResource(R.string.settings_system_no_patches_or_options),
                    tone = SemanticTone.Neutral,
                    isCentered = true
                )
            }
        }
    }
}

/** The selections of one app from one source, which a reset waits on a confirmation for. */
private data class ResetTarget(val packageName: String, val bundleUid: Int)

private data class PatchDetailsTarget(
    val packageName: String,
    val bundleUid: Int,
    val appDisplayName: String
)

/** Destination (package + bundle) for a copy-from-another-bundle operation. */
data class CopyTarget(
    val packageName: String,
    val bundleUid: Int,
    val appDisplayName: String
)
