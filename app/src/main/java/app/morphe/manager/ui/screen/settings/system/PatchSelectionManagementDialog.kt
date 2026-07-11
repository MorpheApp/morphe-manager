/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.rememberAdaptiveFilePicker
import kotlinx.coroutines.launch

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
        onImportUriPicked = { pendingImportUri = it },
        onExitSelection = exitSelection,
        onSelectAll = { selectedPackages.setAll(selections.keys) },
        onShowResetSelectedConfirmation = { showResetSelectedConfirmation.value = true }
    )

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

    MorpheDialog(
        onDismissRequest = {
            if (multiSelect.isSelectionMode) onExitSelection() else onDismiss()
        },
        title = stringResource(R.string.settings_system_patch_selections_title),
        titleTrailingContent = if (!multiSelect.isSelectionMode && selections.isNotEmpty()) {
            {
                DialogTitleAction(
                    icon = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    onClick = onShowResetAllConfirmation,
                    style = DialogTitleActionStyle.Destructive
                )
            }
        } else {
            null
        },
        footer = {
            if (multiSelect.isSelectionMode) {
                MultiSelectShell(visible = true) {
                    SelectionActionBar(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        selectedCount = multiSelect.selectedPackages.size,
                        totalCount = selections.size,
                        onSelectAll = onSelectAll,
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
                MorpheDialogButtonColumn {
                    if (selections.isNotEmpty()) {
                        MorpheDialogButtonRow(
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
                        MorpheDialogButton(
                            text = stringResource(R.string.import_),
                            onClick = { openImportAllSelectionsPicker() },
                            icon = Icons.Outlined.Download,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MorpheDialogOutlinedButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        scrollable = false,
        compactPadding = true
    ) {
        if (selections.isEmpty()) {
            EmptyState(message = stringResource(R.string.settings_system_no_patches_or_options))
        } else {
            SelectionList(
                data = data,
                multiSelect = multiSelect,
                settingsViewModel = settingsViewModel,
                importExportViewModel = importExportViewModel,
                onSetResetTarget = onSetResetTarget,
                onShowPatchDetails = onShowPatchDetails
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
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit
) {
    val selections = data.selections
    val listState = rememberLazyListState()
    val expandedPackages = remember { mutableStateOf<Set<String>>(emptySet()) }
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            // Summary box
            item(key = "summary") {
                InfoBox(
                    title = pluralStringResource(
                        R.plurals.package_count,
                        selections.size,
                        selections.size
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    titleColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Outlined.Tune
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.patch_selection_total_patches,
                            data.totalSelections,
                            data.totalSelections
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            }

            // List of packages with selections
            items(
                items = selections.entries.toList(),
                key = { it.key }
            ) { (packageName, bundleMap) ->
                PackageSelectionItem(
                    packageName = packageName,
                    bundleMap = bundleMap,
                    bundleNames = data.bundleNames,
                    settingsViewModel = settingsViewModel,
                    importExportViewModel = importExportViewModel,
                    onResetPackage = {
                        onSetResetTarget(ResetTarget.Package(packageName))
                    },
                    onResetPackageBundle = { bundleUid ->
                        onSetResetTarget(ResetTarget.PackageBundle(packageName, bundleUid))
                    },
                    onShowPatchDetails = onShowPatchDetails,
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

        ScrollToTopButton(listState = listState)
    }
}

/**
 * Individual package selection item.
 */
@Composable
private fun PackageSelectionItem(
    packageName: String,
    bundleMap: Map<Int, Int>,
    bundleNames: Map<Int, String>,
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    onResetPackage: () -> Unit,
    onResetPackageBundle: (Int) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    var displayName by remember { mutableStateOf(packageName) }
    var appDataSource by remember { mutableStateOf(AppDataSource.INSTALLED) }
    val view = LocalView.current

    // Resolve app name and source
    LaunchedEffect(packageName) {
        val (name, source) = settingsViewModel.resolveAppDisplayName(packageName)
        displayName = name
        appDataSource = source
    }

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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoBadge(
                                text = pluralStringResource(
                                    R.plurals.patch_count,
                                    totalPatches,
                                    totalPatches
                                ),
                                style = InfoBadgeStyle.Primary,
                                isCompact = true
                            )

                            if (bundleMap.size > 1) {
                                InfoBadge(
                                    text = pluralStringResource(
                                        R.plurals.source_count,
                                        bundleMap.size,
                                        bundleMap.size
                                    ),
                                    style = InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                            }
                        }
                    }

                    // Expand icon (hidden in selection mode)
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
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
                    enter = MorpheAnimations.expandTopFadeIn,
                    exit = MorpheAnimations.shrinkTopFadeOut
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
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
                                    onShowPatchDetails(PatchDetailsTarget(packageName, bundleUid))
                                }
                            )
                        }

                        MorpheSettingsDivider(fullWidth = true)

                        // Reset all for this package
                        MorpheDialogButton(
                            text = stringResource(R.string.reset_all),
                            onClick = onResetPackage,
                            isDestructive = true,
                            modifier = Modifier.fillMaxWidth()
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
    onShowDetails: () -> Unit
) {

    // Display bundle name or fallback to "Bundle #N"
    val displayName = bundleName
        ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
    val patchCountText = pluralStringResource(R.plurals.patch_count, patchCount, patchCount)
    val contentDesc = "$displayName: $patchCountText"

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let {
            importExportViewModel.exportPackageBundleData(packageName, bundleUid, bundleName, it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        MorpheSettingsDivider(fullWidth = true)

        // Bundle info card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = contentDesc
                    role = Role.Button
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onShowDetails
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Text(
                        text = patchCountText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Action buttons row
        val exportLabel = stringResource(R.string.export)
        val resetLabel = stringResource(R.string.reset)

        ActionPillRow {
            // Export button
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

            // Reset button
            ActionPillButton(
                onClick = onReset,
                icon = Icons.Outlined.Delete,
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
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_reset_selected_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.reset),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                isPrimaryDestructive = true
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                val patchesText = pluralStringResource(
                    R.plurals.patch_count,
                    totalPatches,
                    totalPatches
                )
                val packagesText = pluralStringResource(
                    R.plurals.package_count,
                    packageCount,
                    packageCount
                )
                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(
                        R.string.settings_system_patch_selection_total_summary_format,
                        patchesText,
                        packagesText
                    )
                )
            }
        }
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

    // Load total options count
    LaunchedEffect(Unit) {
        totalOptions = settingsViewModel.loadTotalOptionsCount()
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_reset_all_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.reset_all),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                isPrimaryDestructive = true
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = stringResource(R.string.settings_system_patch_selection_reset_all_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                val patchesText = pluralStringResource(
                    R.plurals.patch_count,
                    totalSelections,
                    totalSelections
                )

                val packagesText = pluralStringResource(
                    R.plurals.package_count,
                    packageCount,
                    packageCount
                )

                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(
                        R.string.settings_system_patch_selection_total_summary_format,
                        patchesText,
                        packagesText
                    )
                )

                if (totalOptions > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(
                            R.plurals.option_count,
                            totalOptions,
                            totalOptions
                        )
                    )
                }
            }
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

    // Load options count for this package
    LaunchedEffect(packageName) {
        val (name, _) = settingsViewModel.resolveAppDisplayName(packageName)
        displayName = name
        optionsCount = settingsViewModel.loadOptionsCountForPackage(packageName)
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_reset_package_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.reset),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                isPrimaryDestructive = true
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = stringResource(
                    R.string.settings_system_patch_selection_reset_package_warning,
                    displayName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                val patchesText = pluralStringResource(
                    R.plurals.patch_count,
                    patchCount,
                    patchCount
                )

                val sourcesText = pluralStringResource(
                    R.plurals.source_count,
                    bundleCount,
                    bundleCount
                )

                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(
                        R.string.settings_system_patch_selection_patches_in_sources_format,
                        patchesText,
                        sourcesText
                    )
                )

                if (optionsCount > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(
                            R.plurals.option_count,
                            optionsCount,
                            optionsCount
                        )
                    )
                }
            }
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

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_reset_source_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.reset),
                onPrimaryClick = onConfirm,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                isPrimaryDestructive = true
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
            Text(
                text = stringResource(
                    R.string.settings_system_patch_selection_reset_source_warning,
                    displayName,
                    bundleUid
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = pluralStringResource(
                        R.plurals.patch_count,
                        patchCount,
                        patchCount
                    )
                )

                if (optionsCount > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(
                            R.plurals.option_count,
                            optionsCount,
                            optionsCount
                        )
                    )
                }
            }
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

    val bundleDisplayName = bundleName ?: "Source"

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_details_title),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
        ) {
            // Header info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = details?.displayName ?: packageName,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = "$bundleDisplayName (#$bundleUid)",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val patchList = details?.patchList ?: emptyList()
                val optionsMap = details?.optionsMap ?: emptyMap()

                // Patches section
                if (patchList.isNotEmpty()) {
                    InfoBox(
                        title = stringResource(R.string.settings_system_selected_patches_title, patchList.size),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        titleColor = MaterialTheme.colorScheme.primary
                    ) {
                        patchList.forEach { patchName ->
                            Text(
                                text = "• $patchName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalDialogTextColor.current,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // Options section
                if (optionsMap.isNotEmpty()) {
                    InfoBox(
                        title = stringResource(R.string.settings_system_patch_options_title, optionsMap.size),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        titleColor = MaterialTheme.colorScheme.secondary
                    ) {
                        optionsMap.forEach { (patchName, options) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = patchName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalDialogTextColor.current
                                )

                                options.forEach { (key, value) ->
                                    val formattedValue = SettingsViewModel.formatOptionValue(value)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = "• $key",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalDialogSecondaryTextColor.current
                                        )
                                        Text(
                                            text = formattedValue,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalDialogTextColor.current,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (patchName != optionsMap.keys.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Empty state
                if (patchList.isEmpty() && optionsMap.isEmpty()) {
                    InfoBadge(
                        text = stringResource(R.string.settings_system_no_patches_or_options),
                        style = InfoBadgeStyle.Default,
                        isExpanded = true,
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
    val bundleUid: Int
)
