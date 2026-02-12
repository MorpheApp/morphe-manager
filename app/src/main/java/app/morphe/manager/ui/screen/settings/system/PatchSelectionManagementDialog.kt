package app.morphe.manager.ui.screen.settings.system

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.JSON_MIMETYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Dialog for managing patch selections
 * Allows viewing and deleting saved selections per package and bundle
 */
@Composable
fun PatchSelectionManagementDialog(
    selections: Map<String, Map<Int, Int>>, // Map<PackageName, Map<BundleUid, PatchCount>>
    onDismiss: () -> Unit,
    onResetAll: () -> Unit,
    onResetPackage: (String) -> Unit,
    onResetPackageBundle: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    importExportViewModel: ImportExportViewModel = koinInject()
) {
    var showResetAllConfirmation by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<ResetTarget?>(null) }
    var showPatchDetailsTarget by remember { mutableStateOf<PatchDetailsTarget?>(null) }

    val appDataResolver: AppDataResolver = koinInject()
    val patchBundleRepository: PatchBundleRepository = koinInject()

    // Get bundle names for display
    val bundles by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleNames = remember(bundles) {
        bundles.associate { it.uid to it.name }
    }

    // Calculate total selections
    val totalSelections = remember(selections) {
        selections.values.sumOf { bundleMap -> bundleMap.values.sum() }
    }

    PatchSelectionManagementDialogContent(
        selections = selections,
        totalSelections = totalSelections,
        bundleNames = bundleNames,
        onDismiss = onDismiss,
        onShowResetAllConfirmation = { showResetAllConfirmation = true },
        onSetResetTarget = { resetTarget = it },
        onShowPatchDetails = { showPatchDetailsTarget = it },
        appDataResolver = appDataResolver,
        importExportViewModel = importExportViewModel,
        onRefresh = onRefresh
    )

    // Reset all confirmation dialog
    if (showResetAllConfirmation) {
        val confirmAction: () -> Unit = {
            onResetAll()
            showResetAllConfirmation = false
            onDismiss()
        }
        val dismissAction: () -> Unit = { showResetAllConfirmation = false }

        ConfirmResetAllDialog(
            totalSelections = totalSelections,
            packageCount = selections.size,
            onConfirm = confirmAction,
            onDismiss = dismissAction
        )
    }

    // Reset specific target confirmation dialog
    resetTarget?.let { target ->
        when (target) {
            is ResetTarget.Package -> {
                val bundleMap = selections[target.packageName] ?: emptyMap()
                val patchCount = bundleMap.values.sum()

                val confirmAction: () -> Unit = {
                    onResetPackage(target.packageName)
                    resetTarget = null
                }
                val dismissAction: () -> Unit = { resetTarget = null }

                ConfirmResetPackageDialog(
                    packageName = target.packageName,
                    patchCount = patchCount,
                    bundleCount = bundleMap.size,
                    appDataResolver = appDataResolver,
                    onConfirm = confirmAction,
                    onDismiss = dismissAction
                )
            }
            is ResetTarget.PackageBundle -> {
                val patchCount = selections[target.packageName]?.get(target.bundleUid) ?: 0

                val confirmAction: () -> Unit = {
                    onResetPackageBundle(target.packageName, target.bundleUid)
                    resetTarget = null
                }
                val dismissAction: () -> Unit = { resetTarget = null }

                ConfirmResetPackageBundleDialog(
                    packageName = target.packageName,
                    bundleUid = target.bundleUid,
                    patchCount = patchCount,
                    appDataResolver = appDataResolver,
                    onConfirm = confirmAction,
                    onDismiss = dismissAction
                )
            }
        }
    }

    // Patch details dialog
    showPatchDetailsTarget?.let { target ->
        PatchDetailsDialog(
            packageName = target.packageName,
            bundleUid = target.bundleUid,
            bundleName = bundleNames[target.bundleUid],
            appDataResolver = appDataResolver,
            onDismiss = { showPatchDetailsTarget = null }
        )
    }
}

/**
 * Main dialog content
 */
@Composable
private fun PatchSelectionManagementDialogContent(
    selections: Map<String, Map<Int, Int>>,
    totalSelections: Int,
    bundleNames: Map<Int, String>,
    onDismiss: () -> Unit,
    onShowResetAllConfirmation: () -> Unit,
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    appDataResolver: AppDataResolver,
    importExportViewModel: ImportExportViewModel,
    onRefresh: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selections_title),
        titleTrailingContent = if (selections.isNotEmpty()) {
            {
                TextButton(onClick = onShowResetAllConfirmation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.reset_all),
                            color = LocalDialogTextColor.current
                        )
                    }
                }}
        } else {
            null
        },
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        scrollable = false
    ) {
        if (selections.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoBadge(
                    text = stringResource(R.string.settings_system_patch_selection_no_saved),
                    style = InfoBadgeStyle.Default,
                    isExpanded = true,
                    isCentered = true
                )
            }
        } else {
            SelectionList(
                selections = selections,
                totalSelections = totalSelections,
                bundleNames = bundleNames,
                appDataResolver = appDataResolver,
                onSetResetTarget = onSetResetTarget,
                onShowPatchDetails = onShowPatchDetails,
                importExportViewModel = importExportViewModel,
                onRefresh = onRefresh
            )
        }
    }
}

/**
 * List of selections
 */
@Composable
private fun SelectionList(
    selections: Map<String, Map<Int, Int>>,
    totalSelections: Int,
    bundleNames: Map<Int, String>,
    appDataResolver: AppDataResolver,
    onSetResetTarget: (ResetTarget) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    importExportViewModel: ImportExportViewModel,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary badge
        InfoBadge(
            text = stringResource(
                R.string.settings_system_patch_selection_total_summary,
                totalSelections,
                selections.size
            ),
            style = InfoBadgeStyle.Primary,
            isCompact = true
        )

        // List of packages with selections
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = selections.entries.toList(),
                key = { it.key }
            ) { (packageName, bundleMap) ->
                val resetPackageAction: () -> Unit = {
                    onSetResetTarget(ResetTarget.Package(packageName))
                }
                val resetBundleAction: (Int) -> Unit = { bundleUid ->
                    onSetResetTarget(ResetTarget.PackageBundle(packageName, bundleUid))
                }

                PackageSelectionItem(
                    packageName = packageName,
                    bundleMap = bundleMap,
                    bundleNames = bundleNames,
                    appDataResolver = appDataResolver,
                    onResetPackage = resetPackageAction,
                    onResetPackageBundle = resetBundleAction,
                    onShowPatchDetails = onShowPatchDetails,
                    importExportViewModel = importExportViewModel,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

/**
 * Individual package selection item
 */
@Composable
private fun PackageSelectionItem(
    packageName: String,
    bundleMap: Map<Int, Int>,
    bundleNames: Map<Int, String>,
    appDataResolver: AppDataResolver,
    onResetPackage: () -> Unit,
    onResetPackageBundle: (Int) -> Unit,
    onShowPatchDetails: (PatchDetailsTarget) -> Unit,
    importExportViewModel: ImportExportViewModel,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(packageName) }

    // Resolve app name
    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName
    }

    val totalPatches = remember(bundleMap) { bundleMap.values.sum() }

    SectionCard {
        Column {
            // Header - clickable to expand/collapse
            ExpandableSection(
                title = displayName,
                description = stringResource(
                    R.string.settings_system_patch_selection_patches_in_bundles,
                    totalPatches,
                    bundleMap.size
                ),
                expanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                // Bundle list
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    bundleMap.forEach { (bundleUid, patchCount) ->
                        val resetAction: () -> Unit = { onResetPackageBundle(bundleUid) }
                        val bundleName = bundleNames[bundleUid]

                        BundleSelectionItem(
                            packageName = packageName,
                            bundleUid = bundleUid,
                            bundleName = bundleName,
                            patchCount = patchCount,
                            onReset = resetAction,
                            onShowDetails = { onShowPatchDetails(PatchDetailsTarget(packageName, bundleUid)) },
                            importExportViewModel = importExportViewModel,
                            onRefresh = onRefresh
                        )
                    }

                    MorpheSettingsDivider()

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

/**
 * Individual bundle selection item
 */
@Composable
private fun BundleSelectionItem(
    packageName: String,
    bundleUid: Int,
    bundleName: String?,
    patchCount: Int,
    onReset: () -> Unit,
    onShowDetails: () -> Unit,
    importExportViewModel: ImportExportViewModel,
    onRefresh: () -> Unit
) {
    // Display bundle name or fallback to "Bundle #N"
    val displayName = bundleName ?: stringResource(R.string.settings_system_patch_selection_bundle_format, bundleUid)

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIMETYPE)
    ) { uri ->
        uri?.let {
            importExportViewModel.exportPackageBundleData(packageName, bundleUid, bundleName, it)
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.importPackageBundleData(bundleUid, it)
            // Refresh data after import to update UI
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bundle info header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowDetails)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = pluralStringResource(R.plurals.patch_count, patchCount, patchCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.view),
                tint = LocalDialogSecondaryTextColor.current
            )
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // Reset button
            ActionPillButton(
                onClick = onReset,
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.reset),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )

            // Import button
            ActionPillButton(
                onClick = { importLauncher.launch(JSON_MIMETYPE) },
                icon = Icons.Outlined.Download,
                contentDescription = stringResource(R.string.import_)
            )

            // Export button
            ActionPillButton(
                onClick = {
                    val fileName = importExportViewModel.getPackageBundleDataExportFileName(packageName, bundleUid, bundleName)
                    exportLauncher.launch(fileName)
                },
                icon = Icons.Outlined.Upload,
                contentDescription = stringResource(R.string.export)
            )
        }
    }
}

/**
 * Confirmation dialog for resetting all selections
 */
@Composable
private fun ConfirmResetAllDialog(
    totalSelections: Int,
    packageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_system_patch_selection_reset_all_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )

            DeletionWarningBox(
                warningText = stringResource(R.string.settings_system_patch_selection_will_delete)
            ) {
                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(
                        R.string.settings_system_patch_selection_total_summary,
                        totalSelections,
                        packageCount
                    )
                )
            }
        }
    }
}

/**
 * Confirmation dialog for resetting package selections
 */
@Composable
private fun ConfirmResetPackageDialog(
    packageName: String,
    patchCount: Int,
    bundleCount: Int,
    appDataResolver: AppDataResolver,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(packageName) }

    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                DeleteListItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(
                        R.string.settings_system_patch_selection_patches_in_bundles,
                        patchCount,
                        bundleCount
                    )
                )
            }
        }
    }
}

/**
 * Confirmation dialog for resetting package-bundle selections
 */
@Composable
private fun ConfirmResetPackageBundleDialog(
    packageName: String,
    bundleUid: Int,
    patchCount: Int,
    appDataResolver: AppDataResolver,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(packageName) }

    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_reset_bundle_confirm_title),
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.settings_system_patch_selection_reset_bundle_warning,
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
                    text = pluralStringResource(R.plurals.patch_count, patchCount, patchCount),
                )
            }
        }
    }
}

/**
 * Reset target sealed class for dialog state
 */
private sealed interface ResetTarget {
    data class Package(val packageName: String) : ResetTarget
    data class PackageBundle(val packageName: String, val bundleUid: Int) : ResetTarget
}

/**
 * Target for showing patch details
 */
private data class PatchDetailsTarget(
    val packageName: String,
    val bundleUid: Int
)

/**
 * Dialog showing detailed patch selections and options
 */
@Composable
private fun PatchDetailsDialog(
    packageName: String,
    bundleUid: Int,
    bundleName: String?,
    appDataResolver: AppDataResolver,
    onDismiss: () -> Unit
) {
    val selectionRepository: PatchSelectionRepository = koinInject()
    val optionsRepository: PatchOptionsRepository = koinInject()

    var displayName by remember { mutableStateOf(packageName) }
    var patchList by remember { mutableStateOf<List<String>>(emptyList()) }
    var optionsMap by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Resolve app name
    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName
    }

    // Load patch selections and options
    LaunchedEffect(packageName, bundleUid) {
        isLoading = true
        withContext(Dispatchers.IO) {
            patchList = selectionRepository.exportForPackageAndBundle(packageName, bundleUid)
            optionsMap = optionsRepository.getOptionsForBundle(
                packageName = packageName,
                bundleUid = bundleUid,
                bundlePatchInfo = emptyMap()
            )
        }
        isLoading = false
    }

    val bundleDisplayName = bundleName ?: stringResource(R.string.settings_system_patch_selection_bundle_format, bundleUid)

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_details_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = bundleDisplayName,
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
                // Patches section
                if (patchList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_system_selected_patches, patchList.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalDialogTextColor.current
                        )

                        SectionCard {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                patchList.forEach { patchName ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = patchName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LocalDialogTextColor.current,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Show if patch has options
                                        if (optionsMap.containsKey(patchName)) {
                                            InfoBadge(
                                                text = stringResource(
                                                    R.string.settings_system_options_count,
                                                    optionsMap[patchName]?.size ?: 0
                                                ),
                                                style = InfoBadgeStyle.Default,
                                                isCompact = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Options section
                if (optionsMap.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_system_patch_options_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalDialogTextColor.current
                        )

                        SectionCard {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                optionsMap.forEach { (patchName, options) ->
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = patchName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LocalDialogTextColor.current
                                        )

                                        options.forEach { (key, value) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalDialogSecondaryTextColor.current,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = value?.toString() ?: "null",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalDialogTextColor.current,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (patchName != optionsMap.keys.last()) {
                                        MorpheSettingsDivider()
                                    }
                                }
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
