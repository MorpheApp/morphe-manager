/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.launch
import java.util.Locale

/** Snapshot of package/bundle selection counts. */
@Immutable
data class PatchSelectionData(
    val selections: Map<String, Map<Int, Int>>,
    val totalSelections: Int,
    val bundleNames: Map<Int, String>
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
 * Dialog for managing patch selections.
 */
@Composable
fun PatchSelectionManagementDialog(
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    onDismiss: () -> Unit
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

    PatchSelectionManagementDialogContent(
        data = PatchSelectionData(
            selections = selections,
            totalSelections = totalSelections,
            bundleNames = bundleNames
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
        settingsViewModel = settingsViewModel,
        importExportViewModel = importExportViewModel,
        onDismiss = onDismiss,
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

    // Confirmed picks are written to the database immediately here, unlike the expert-mode
    // path which stages changes until the user proceeds to patching.
    copyTarget.value?.let { target ->
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

    if (showResetSelectedConfirmation.value) {
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
    pendingImportUri?.let { uri ->
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
    if (showResetAllConfirmation.value) {
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
    resetTarget.value?.let { target ->
        when (target) {
            is ResetTarget.Package -> {
                val bundleMap = selections[target.packageName] ?: emptyMap()
                val patchCount = bundleMap.values.sum()

                ConfirmResetPackageDialog(
                    packageName = target.packageName,
                    patchCount = patchCount,
                    bundleCount = bundleMap.size,
                    settingsViewModel = settingsViewModel,
                    onConfirm = {
                        scope.launch {
                            settingsViewModel.resetSelectionsForPackage(target.packageName)
                            resetTarget.value = null
                        }
                    },
                    onDismiss = { resetTarget.value = null }
                )
            }

            is ResetTarget.PackageBundle -> {
                val patchCount = selections[target.packageName]?.get(target.bundleUid) ?: 0

                ConfirmResetPackageBundleDialog(
                    packageName = target.packageName,
                    bundleUid = target.bundleUid,
                    bundleName = bundleNames[target.bundleUid],
                    patchCount = patchCount,
                    settingsViewModel = settingsViewModel,
                    onConfirm = {
                        scope.launch {
                            settingsViewModel.resetSelectionsForPackageBundle(
                                target.packageName,
                                target.bundleUid
                            )
                            resetTarget.value = null
                        }
                    },
                    onDismiss = { resetTarget.value = null }
                )
            }
        }
    }

    // Patch details dialog
    showPatchDetailsTarget.value?.let { target ->
        PatchDetailsDialog(
            packageName = target.packageName,
            bundleUid = target.bundleUid,
            appDisplayName = target.appDisplayName,
            bundleName = bundleNames[target.bundleUid],
            settingsViewModel = settingsViewModel,
            onDismiss = { showPatchDetailsTarget.value = null }
        )
    }
}

/**
 * Main dialog content.
 */
@Composable
private fun PatchSelectionManagementDialogContent(
    data: PatchSelectionData,
    multiSelect: PatchSelectionMultiSelect,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    onDismiss: () -> Unit,
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

    AppDialog(
        onDismissRequest = {
            if (multiSelect.isSelectionMode) onExitSelection() else onDismiss()
        },
        title = stringResource(R.string.settings_system_patch_selections_title),
        titleTrailingContent = if (isSearchable || canResetAll) {
            {
                if (isSearchable) {
                    TitleAction(
                        icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        onClick = { search.toggle() },
                        style = TitleActionStyle.Toggle,
                        active = search.visible
                    )
                }
                if (canResetAll) {
                    TitleAction(
                        icon = Icons.Outlined.Restore,
                        contentDescription = stringResource(R.string.reset),
                        onClick = onShowResetAllConfirmation,
                        style = TitleActionStyle.Destructive
                    )
                }
            }
        } else {
            null
        },
        footer = {
            if (multiSelect.isSelectionMode) {
                MultiSelectShell(visible = true) {
                    SelectionActionBar(
                        modifier = Modifier.padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
                        selectedCount = multiSelect.selectedPackages.size,
                        totalCount = selections.size,
                        onSelectAll = onSelectAll,
                        onDeselectAll = { multiSelect.selectedPackages.clear() },
                        onCancel = onExitSelection
                    ) {
                        val resetLabel = stringResource(R.string.reset)
                        ActionPillButton(
                            onClick = onShowResetSelectedConfirmation,
                            icon = Icons.Outlined.Delete,
                            contentDescription = resetLabel,
                            tooltip = resetLabel,
                            enabled = multiSelect.selectedPackages.isNotEmpty,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
            } else {
                AppDialogButtonColumn {
                    if (selections.isNotEmpty()) {
                        AppDialogButtonRow(
                            primaryText = stringResource(R.string.export),
                            onPrimaryClick = {
                                exportAllSelectionsLauncher.launch(
                                    importExportViewModel.getAllSelectionsExportFileName()
                                )
                            },
                            primaryIcon = Icons.Outlined.Upload,
                            secondaryText = stringResource(R.string.import_),
                            onSecondaryClick = { openImportAllSelectionsPicker() },
                            secondaryIcon = Icons.Outlined.Download,
                            isSecondaryPrimary = true,
                            layout = DialogButtonLayout.Horizontal
                        )
                    } else {
                        AppDialogButton(
                            text = stringResource(R.string.import_),
                            onClick = { openImportAllSelectionsPicker() },
                            icon = Icons.Outlined.Download,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        scrollable = false,
        padding = DialogPadding.Compact,
        contentArrangement = Arrangement.Top
    ) {
        SearchFieldBackHandler(search)

        if (selections.isEmpty()) {
            EmptyState(message = stringResource(R.string.settings_system_no_patches_or_options))
        } else {
            SelectionList(
                data = data,
                multiSelect = multiSelect,
                settingsViewModel = settingsViewModel,
                importExportViewModel = importExportViewModel,
                search = search,
                onSetResetTarget = onSetResetTarget,
                onShowPatchDetails = onShowPatchDetails,
                onOpenCopyFromBundle = onOpenCopyFromBundle,
                onImport = openImportAllSelectionsPicker
            )
        }
    }
}

/**
 * List of selections.
 */
@Composable
private fun SelectionList(
    data: PatchSelectionData,
    multiSelect: PatchSelectionMultiSelect,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    search: SearchFieldState,
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    onOpenCopyFromBundle: (CopyTarget) -> Unit,
    onImport: () -> Unit
) {
    val selections = data.selections
    val listState = rememberLazyListState()
    val expandedPackages = remember { mutableStateOf<Set<String>>(emptySet()) }

    // Resolved here rather than per row: the list sorts by these names, and each row would
    // otherwise repeat the same lookup. Falls back to the package name while one is in flight.
    val resolvedApps = remember(selections) {
        mutableStateMapOf<String, Pair<String, AppDataSource>>()
    }
    LaunchedEffect(selections) {
        resolvedApps.clear()
        selections.keys.forEach { packageName ->
            launch { resolvedApps[packageName] = settingsViewModel.resolveAppDisplayName(packageName) }
        }
    }

    // Derived so the list re-filters and re-sorts as display names finish resolving
    val displayEntries by remember(selections) {
        derivedStateOf {
            val query = search.query
            val displayNameOf = { packageName: String ->
                resolvedApps[packageName]?.first ?: packageName
            }
            selections.entries
                .filter { (packageName, _) ->
                    query.isBlank() ||
                        packageName.contains(query, ignoreCase = true) ||
                        displayNameOf(packageName).contains(query, ignoreCase = true)
                }
                .sortedBy { (packageName, _) -> displayNameOf(packageName).lowercase(Locale.ROOT) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            stickyHeader(key = "search") {
                AppDialogSearchHeader(
                    visible = search.visible,
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = stringResource(R.string.home_search_apps)
                )
            }

            // Summary box
            item(key = "summary") {
                HeroInfoCard(
                    icon = Icons.Outlined.Tune,
                    title = pluralStringResource(
                        R.plurals.package_count,
                        selections.size,
                        selections.size
                    ),
                    subtitle = {
                        Text(
                            text = pluralStringResource(
                                R.plurals.patch_count,
                                data.totalSelections,
                                data.totalSelections
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                )
            }

            if (displayEntries.isEmpty()) {
                // No matches for the current search query
                item(key = "search_empty") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = Icons.Outlined.SearchOff
                    )
                }
            } else {
                // List of packages with selections
                items(
                    items = displayEntries,
                    key = { it.key }
                ) { (packageName, bundleMap) ->
                    val (displayName, appDataSource) = resolvedApps[packageName]
                        ?: (packageName to AppDataSource.INSTALLED)
                    PackageSelectionItem(
                        packageName = packageName,
                        displayName = displayName,
                        appDataSource = appDataSource,
                        bundleMap = bundleMap,
                        bundleNames = data.bundleNames,
                        importExportViewModel = importExportViewModel,
                        onResetPackage = {
                            onSetResetTarget(ResetTarget.Package(packageName))
                        },
                        onResetPackageBundle = { bundleUid ->
                            onSetResetTarget(ResetTarget.PackageBundle(packageName, bundleUid))
                        },
                        onShowPatchDetails = onShowPatchDetails,
                        onOpenCopyFromBundle = onOpenCopyFromBundle,
                        onImport = onImport,
                        isSelected = multiSelect.selectedPackages.contains(packageName),
                        isSelectionMode = multiSelect.isSelectionMode,
                        onEnterSelection = { multiSelect.onEnterSelection(packageName) },
                        onToggleSelection = { multiSelect.onToggleSelection(packageName) },
                        expanded = packageName in expandedPackages.value,
                        onToggleExpanded = {
                            expandedPackages.value = if (packageName in expandedPackages.value) {
                                expandedPackages.value - packageName
                            } else {
                                expandedPackages.value + packageName
                            }
                        }
                    )
                }
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

/**
 * Individual package selection item.
 */
@Composable
private fun PackageSelectionItem(
    packageName: String,
    displayName: String,
    appDataSource: AppDataSource,
    bundleMap: Map<Int, Int>,
    bundleNames: Map<Int, String>,
    importExportViewModel: ImportExportViewModel,
    onResetPackage: () -> Unit,
    onResetPackageBundle: (Int) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    onOpenCopyFromBundle: (CopyTarget) -> Unit,
    onImport: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val view = LocalView.current

    val totalPatches = remember(bundleMap) { bundleMap.values.sum() }
    // In selection mode force cards closed so nested bundle taps do not race with tap-to-toggle
    val effectiveExpanded = expanded && !isSelectionMode
    val expandRotation by animateFloatAsState(
        targetValue = if (effectiveExpanded) 180f else 0f,
        label = "expand_rotation"
    )

    SelectableCard(
        modifier = Modifier.fillMaxWidth(),
        isSelected = isSelected,
        isSelectionMode = isSelectionMode
    ) {
        SectionCard {
            Column {
                // Header with app icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) onToggleSelection() else onToggleExpanded()
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onEnterSelection()
                            }
                        )
                        .padding(Defaults.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App icon
                    AppIcon(
                        packageName = packageName,
                        contentDescription = displayName,
                        modifier = Modifier.size(48.dp),
                        preferredSource = appDataSource
                    )

                    // App info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalDialogTextColor.current
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(
                                text = pluralStringResource(
                                    R.plurals.patch_count,
                                    totalPatches,
                                    totalPatches
                                ),
                                tone = SemanticTone.Primary
                            )

                            if (bundleMap.size > 1) {
                                StatusBadge(
                                    text = pluralStringResource(
                                        R.plurals.source_count,
                                        bundleMap.size,
                                        bundleMap.size
                                    ),
                                    tone = SemanticTone.Neutral
                                )
                            }
                        }
                    }

                    // Expand icon (hidden in selection mode)
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = Animations.expandFadeEnter,
                        exit = Animations.shrinkFadeExit
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = if (effectiveExpanded)
                                stringResource(R.string.collapse)
                            else
                                stringResource(R.string.expand),
                            tint = LocalDialogSecondaryTextColor.current,
                            modifier = Modifier.rotate(expandRotation)
                        )
                    }
                }

                // Expanded content
                AnimatedVisibility(
                    visible = effectiveExpanded,
                    enter = Animations.expandTopFadeIn,
                    exit = Animations.shrinkTopFadeOut
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = Defaults.ContentPadding,
                            end = Defaults.ContentPadding,
                            bottom = Defaults.ContentPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                    ) {
                        bundleMap.forEach { (bundleUid, patchCount) ->
                            BundleSelectionItem(
                                packageName = packageName,
                                bundleUid = bundleUid,
                                bundleName = bundleNames[bundleUid],
                                patchCount = patchCount,
                                importExportViewModel = importExportViewModel,
                                onReset = { onResetPackageBundle(bundleUid) },
                                onShowDetails = {
                                    onShowPatchDetails(PatchDetailsTarget(packageName, bundleUid, displayName))
                                },
                                onCopyFromBundle = {
                                    onOpenCopyFromBundle(CopyTarget(packageName, bundleUid, displayName))
                                },
                                onImport = onImport
                            )
                        }

                        SettingsDivider(fullWidth = true)

                        // Reset all for this package
                        CardActionRow(
                            actions = listOf(
                                CardAction(
                                    icon = Icons.Outlined.Restore,
                                    label = stringResource(R.string.reset_all),
                                    onClick = onResetPackage,
                                    destructive = true
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual bundle selection item.
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
    onImport: () -> Unit
) {

    // Display bundle name or fallback to "Bundle #N"
    val displayName = bundleName
        ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
    val patchCountText = pluralStringResource(R.plurals.patch_count, patchCount, patchCount)

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let {
            importExportViewModel.exportPackageBundleData(packageName, bundleUid, bundleName, it)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        SettingsDivider(fullWidth = true)

        // Bundle info card
        BundleInfoCard(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Extension,
            title = displayName,
            value = patchCountText,
            onClick = onShowDetails
        )

        ActionPillRow {
            val copyLabel = stringResource(R.string.copy)
            ActionPillButton(
                onClick = onCopyFromBundle,
                icon = Icons.Outlined.ContentCopy,
                contentDescription = copyLabel,
                tooltip = copyLabel
            )

            val importLabel = stringResource(R.string.import_)
            ActionPillButton(
                onClick = onImport,
                icon = Icons.Outlined.Download,
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
                icon = Icons.Outlined.Upload,
                contentDescription = exportLabel,
                tooltip = exportLabel
            )

            val resetLabel = stringResource(R.string.reset)
            ActionPillButton(
                onClick = onReset,
                icon = Icons.Outlined.Restore,
                contentDescription = resetLabel,
                tooltip = resetLabel,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

@Composable
private fun ConfirmResetDialog(
    title: String,
    message: AnnotatedString,
    primaryText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    summaryItems: @Composable () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        footer = {
            AppDialogButtonRow(
                primaryText = primaryText,
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                isPrimaryDestructive = true
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            LabeledSection { summaryItems() }
        }
    }
}

/**
 * Confirmation dialog for resetting selections across the currently selected packages.
 */
@Composable
private fun ConfirmResetSelectedDialog(
    packageCount: Int,
    totalPatches: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val patchesText = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches)
    val packagesText = pluralStringResource(R.plurals.package_count, packageCount, packageCount)
    ConfirmResetDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_selected_confirm_title),
        message = AnnotatedString(stringResource(R.string.settings_system_patch_selection_reset_selected_warning)),
        primaryText = stringResource(R.string.reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        DeleteListItem(
            icon = Icons.Outlined.Delete,
            text = stringResource(R.string.settings_system_patch_selection_total_summary_format, patchesText, packagesText)
        )
    }
}

/**
 * Confirmation dialog for resetting all selections.
 * Options count is loaded via [SettingsViewModel].
 */
@Composable
private fun ConfirmResetAllDialog(
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

    val patchesText = pluralStringResource(R.plurals.patch_count, totalSelections, totalSelections)
    val packagesText = pluralStringResource(R.plurals.package_count, packageCount, packageCount)
    ConfirmResetDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_all_confirm_title),
        message = AnnotatedString(stringResource(R.string.settings_system_patch_selection_reset_all_warning)),
        primaryText = stringResource(R.string.reset_all),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        DeleteListItem(
            icon = Icons.Outlined.Delete,
            text = stringResource(R.string.settings_system_patch_selection_total_summary_format, patchesText, packagesText)
        )
        if (totalOptions > 0) {
            DeleteListItem(
                icon = Icons.Outlined.Tune,
                text = pluralStringResource(R.plurals.option_count, totalOptions, totalOptions)
            )
        }
    }
}

/**
 * Confirmation dialog for resetting package selections.
 */
@Composable
private fun ConfirmResetPackageDialog(
    packageName: String,
    patchCount: Int,
    bundleCount: Int,
    settingsViewModel: SettingsViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(packageName) }
    var optionsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(packageName) {
        val (name, _) = settingsViewModel.resolveAppDisplayName(packageName)
        displayName = name
        optionsCount = settingsViewModel.loadOptionsCountForPackage(packageName)
    }

    val patchesText = pluralStringResource(R.plurals.patch_count, patchCount, patchCount)
    val sourcesText = pluralStringResource(R.plurals.source_count, bundleCount, bundleCount)
    ConfirmResetDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_package_confirm_title),
        message = htmlAnnotatedString(stringResource(R.string.settings_system_patch_selection_reset_package_warning, displayName)),
        primaryText = stringResource(R.string.reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        DeleteListItem(
            icon = Icons.Outlined.Delete,
            text = stringResource(R.string.settings_system_patch_selection_patches_in_sources_format, patchesText, sourcesText)
        )
        if (optionsCount > 0) {
            DeleteListItem(
                icon = Icons.Outlined.Tune,
                text = pluralStringResource(R.plurals.option_count, optionsCount, optionsCount)
            )
        }
    }
}

/**
 * Confirmation dialog for resetting package-bundle selections.
 */
@Composable
private fun ConfirmResetPackageBundleDialog(
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
    ConfirmResetDialog(
        title = stringResource(R.string.settings_system_patch_selection_reset_source_confirm_title),
        message = htmlAnnotatedString(stringResource(R.string.settings_system_patch_selection_reset_source_warning, displayName, bundleDisplayName)),
        primaryText = stringResource(R.string.reset),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        DeleteListItem(
            icon = Icons.Outlined.Delete,
            text = pluralStringResource(R.plurals.patch_count, patchCount, patchCount)
        )
        if (optionsCount > 0) {
            DeleteListItem(
                icon = Icons.Outlined.Tune,
                text = pluralStringResource(R.plurals.option_count, optionsCount, optionsCount)
            )
        }
    }
}

/**
 * Dialog showing detailed patch selections and options for one package+bundle.
 */
@Composable
private fun PatchDetailsDialog(
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

    AppDialog(
        onDismissRequest = onDismiss,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            HeroInfoCard(
                icon = Icons.Outlined.Extension,
                title = appDisplayName,
                subtitle = {
                    Text(
                        text = bundleName ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Defaults.ContentPaddingExpanded),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val patchList = details?.patchList ?: emptyList()
                val optionsMap = details?.optionsMap ?: emptyMap()
                // Stored keys carry a suffix when the bundle ships duplicate patch names
                val displayNames = details?.displayNames ?: emptyMap()

                // Patches section
                if (patchList.isNotEmpty()) {
                    LabeledSection(
                        title = stringResource(R.string.settings_system_selected_patches_section),
                        count = patchList.size
                    ) {
                        patchList.forEach { patchName ->
                            PatchNameRow(name = displayNames[patchName] ?: patchName)
                        }
                    }
                }

                // Options section
                if (optionsMap.isNotEmpty()) {
                    LabeledSection(
                        title = stringResource(R.string.settings_system_patch_options_section),
                        count = optionsMap.size
                    ) {
                        optionsMap.entries.forEach { (patchName, options) ->
                            PatchOptionsGroup(
                                patchName = displayNames[patchName] ?: patchName,
                                options = options
                            )
                        }
                    }
                }

                // Empty state
                if (patchList.isEmpty() && optionsMap.isEmpty()) {
                    Notice(
                        text = stringResource(R.string.settings_system_no_patches_or_options),
                        tone = SemanticTone.Neutral,
                        isCentered = true
                    )
                }
            }
        }
    }
}

private sealed interface ResetTarget {
    data class Package(val packageName: String) : ResetTarget
    data class PackageBundle(val packageName: String, val bundleUid: Int) : ResetTarget
}

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
