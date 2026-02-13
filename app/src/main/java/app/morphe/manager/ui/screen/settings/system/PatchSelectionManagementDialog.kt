package app.morphe.manager.ui.screen.settings.system

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.JSON_MIMETYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.koin.compose.koinInject

/**
 * Dialog for managing patch selections
 * Allows viewing and deleting saved selections per package and bundle
 * Shows only original package names
 */
@Composable
fun PatchSelectionManagementDialog(
    onDismiss: () -> Unit,
    onResetAll: () -> Unit,
    onResetPackage: (String) -> Unit,
    onResetPackageBundle: (String, Int) -> Unit,
    importExportViewModel: ImportExportViewModel = koinInject()
) {
    val scope = rememberCoroutineScope()
    var showResetAllConfirmation by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<ResetTarget?>(null) }
    var showPatchDetailsTarget by remember { mutableStateOf<PatchDetailsTarget?>(null) }

    val appDataResolver: AppDataResolver = koinInject()
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val selectionRepository: PatchSelectionRepository = koinInject()

    // Get bundle names for display
    val bundles by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleNames = remember(bundles) {
        bundles.associate { it.uid to it.name }
    }

    // Load selections data internally with refresh capability
    var selections by remember { mutableStateOf<Map<String, Map<Int, Int>>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Load data from repository
    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            selections = selectionRepository.getSelectionsSummary()
        }
    }

    // Refresh function
    val onRefresh: () -> Unit = {
        refreshTrigger++
    }

    // Filter to show only patched packages
    var filteredSelections by remember(selections) { mutableStateOf(selections) }

    LaunchedEffect(selections) {
        filteredSelections = appDataResolver.filterPatchedPackagesOnly(selections)
    }

    // Calculate total selections from filtered data
    val totalSelections = remember(filteredSelections) {
        filteredSelections.values.sumOf { bundleMap -> bundleMap.values.sum() }
    }

    PatchSelectionManagementDialogContent(
        selections = filteredSelections,
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
            scope.launch {
                onResetAll()
                delay(50) // Small delay to ensure DB operation completes
                onRefresh()
                showResetAllConfirmation = false
                onDismiss()
            }
        }
        val dismissAction: () -> Unit = { showResetAllConfirmation = false }

        ConfirmResetAllDialog(
            totalSelections = totalSelections,
            packageCount = filteredSelections.size,
            onConfirm = confirmAction,
            onDismiss = dismissAction
        )
    }

    // Reset specific target confirmation dialog
    resetTarget?.let { target ->
        when (target) {
            is ResetTarget.Package -> {
                val bundleMap = filteredSelections[target.packageName] ?: emptyMap()
                val patchCount = bundleMap.values.sum()

                val confirmAction: () -> Unit = {
                    scope.launch {
                        onResetPackage(target.packageName)
                        delay(50) // Small delay to ensure DB operation completes
                        onRefresh()
                        resetTarget = null
                    }
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
                val patchCount = filteredSelections[target.packageName]?.get(target.bundleUid) ?: 0

                val confirmAction: () -> Unit = {
                    scope.launch {
                        onResetPackageBundle(target.packageName, target.bundleUid)
                        delay(50) // Small delay to ensure DB operation completes
                        onRefresh()
                        resetTarget = null
                    }
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
                IconButton(onClick = onShowResetAllConfirmation) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = stringResource(R.string.reset),
                        tint = LocalDialogTextColor.current
                    )
                }
            }
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
        scrollable = false,
        compactPadding = true
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
    var appDataSource by remember { mutableStateOf(AppDataSource.INSTALLED) }

    // Resolve app name and source
    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName
        appDataSource = appData.source
    }

    val totalPatches = remember(bundleMap) { bundleMap.values.sum() }

    SectionCard {
        Column {
            // Header with app icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            text = pluralStringResource(R.plurals.patch_count, totalPatches, totalPatches),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )

                        if (bundleMap.size > 1) {
                            InfoBadge(
                                text = pluralStringResource(R.plurals.source_count, bundleMap.size, bundleMap.size),
                                style = InfoBadgeStyle.Default,
                                isCompact = true
                            )
                        }
                    }
                }

                // Expand icon
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded)
                        stringResource(R.string.collapse)
                    else
                        stringResource(R.string.expand),
                    tint = LocalDialogSecondaryTextColor.current
                )
            }

            // Expanded content
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
    val displayName = bundleName ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)

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
            importExportViewModel.importPackageBundleData(
                targetBundleUid = bundleUid,
                source = it,
                onComplete = {
                    // Refresh data after successful import
                    onRefresh()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MorpheSettingsDivider(fullWidth = true)

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
    val optionsRepository: PatchOptionsRepository = koinInject()
    val appDataResolver: AppDataResolver = koinInject()
    var totalOptions by remember { mutableIntStateOf(0) }

    // Load total options count for patched packages only
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Get all packages with options
            val packagesWithOptions = optionsRepository.getPackagesWithSavedOptions().first()

            // Filter to only patched packages
            val patchedPackages = appDataResolver.filterPatchedPackageNames(packagesWithOptions)

            // Count options for patched packages only
            totalOptions = patchedPackages.sumOf { packageName ->
                optionsRepository.getOptionsCountForPackage(packageName)
            }
        }
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

                if (totalOptions > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(R.plurals.patch_options_count, totalOptions,  totalOptions)
                    )
                }
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
    val optionsRepository: PatchOptionsRepository = koinInject()
    var displayName by remember { mutableStateOf(packageName) }
    var optionsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(packageName) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName

        // Load options count for this package
        withContext(Dispatchers.IO) {
            optionsCount = optionsRepository.getOptionsCountForPackage(packageName)
        }
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
                        R.string.settings_system_patch_selection_patches_in_sources,
                        patchCount,
                        bundleCount
                    )
                )

                if (optionsCount > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(R.plurals.patch_options_count, optionsCount, optionsCount)
                    )
                }
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
    val optionsRepository: PatchOptionsRepository = koinInject()
    var displayName by remember { mutableStateOf(packageName) }
    var optionsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(packageName, bundleUid) {
        val appData = appDataResolver.resolveAppData(packageName)
        displayName = appData.displayName

        // Load options count for this package+bundle
        withContext(Dispatchers.IO) {
            optionsCount = optionsRepository.getOptionsCountForBundle(packageName, bundleUid)
        }
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
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    text = pluralStringResource(R.plurals.patch_count, patchCount, patchCount)
                )

                if (optionsCount > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = pluralStringResource(R.plurals.patch_options_count, optionsCount, optionsCount)
                    )
                }
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
            // Load selections for this package (always patched package)
            patchList = selectionRepository.exportForPackageAndBundle(packageName, bundleUid)

            // Get raw serialized options from repository
            val rawOptions = optionsRepository.exportOptionsForBundle(
                packageName = packageName,
                bundleUid = bundleUid
            )

            // Convert JSON strings to display values
            optionsMap = rawOptions.mapValues { (_, patchOptions) ->
                patchOptions.mapValues { (_, jsonString) ->
                    parseJsonValue(jsonString)
                }
            }
        }
        isLoading = false
    }

    val bundleDisplayName = bundleName ?: "Source"

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
                                    val formattedValue = formatOptionValue(value)

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

/**
 * Parse JSON string value to actual value type
 */
private fun parseJsonValue(jsonString: String): Any? {
    return try {
        // Use kotlinx.serialization to parse JSON properly
        val json = Json {
            ignoreUnknownKeys = true
        }
        val element = json.parseToJsonElement(jsonString)

        when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.floatOrNull != null -> element.float
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }

            is JsonArray -> {
                element.map { item ->
                    when (item) {
                        is JsonPrimitive -> {
                            when {
                                item.isString -> item.content
                                item.booleanOrNull != null -> item.boolean
                                item.intOrNull != null -> item.int
                                item.longOrNull != null -> item.long
                                item.floatOrNull != null -> item.float
                                else -> item.content
                            }
                        }

                        else -> item.toString()
                    }
                }
            }

            else -> jsonString
        }
    } catch (_: Exception) {
        jsonString // Return raw string if parsing fails
    }
}
/**
 * Format option value for display
 */
private fun formatOptionValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is String -> value
        is Boolean -> value.toString()
        is Number -> value.toString()
        is List<*> -> {
            if (value.isEmpty()) {
                "[]"
            } else {
                value.joinToString(", ")
            }
        }
        else -> value.toString()
    }
}
