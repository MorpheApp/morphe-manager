package app.morphe.manager.ui.screen.settings.system

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.JSON_MIMETYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.koin.compose.koinInject

/**
 * Dialog for managing patch selections
 * Displays patched package names but operates on original package names in database
 *
 * Architecture:
 * - Database stores: original package names (com.example.app)
 * - UI displays: patched package names (com.example.app.patched)
 * - Mapping layer: converts between original <-> patched names
 */
@Composable
fun PatchSelectionManagementDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showResetAllConfirmation by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<ResetTarget?>(null) }
    var showPatchDetailsTarget by remember { mutableStateOf<PatchDetailsTarget?>(null) }

    val appDataResolver: AppDataResolver = koinInject()
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val selectionRepository: PatchSelectionRepository = koinInject()
    val optionsRepository: PatchOptionsRepository = koinInject()

    // Get bundle names for display
    val bundles by patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleNames = remember(bundles) {
        bundles.associate { it.uid to it.name }
    }

    // State for selections and package name mapping
    val rawSelections by selectionRepository.getSelectionsSummaryFlow().collectAsStateWithLifecycle(emptyMap())
    // Build package name mapping (original -> patched)
    val packageNameMapping by produceState<Map<String, String>>(
        initialValue = emptyMap(),
        key1 = rawSelections
    ) {
        value = withContext(Dispatchers.IO) {
            val installedApps = appDataResolver.getInstalledApps()

            rawSelections.keys.associateWith { originalPackageName ->
                val patchedApp = installedApps.find {
                    it.originalPackageName == originalPackageName
                }
                patchedApp?.currentPackageName ?: originalPackageName
            }
        }
    }

    // Transform selections to display names
    val selections by remember(rawSelections, packageNameMapping) {
        derivedStateOf {
            rawSelections.mapKeys { (originalName, _) ->
                packageNameMapping[originalName] ?: originalName
            }
        }
    }

    // Show all selections from database
    val filteredSelections = selections

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
        appDataResolver = appDataResolver
    )

    // Reset all confirmation dialog
    if (showResetAllConfirmation) {
        val confirmAction: () -> Unit = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    selectionRepository.reset()
                    optionsRepository.reset()
                }
                showResetAllConfirmation = false
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
                // Convert display name back to original name for database operations
                val originalPackageName = remember(target.packageName, packageNameMapping) {
                    packageNameMapping.entries
                        .find { it.value == target.packageName }?.key
                        ?: target.packageName
                }

                val bundleMap = filteredSelections[target.packageName] ?: emptyMap()
                val patchCount = bundleMap.values.sum()

                val confirmAction: () -> Unit = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Use original package name for database operations
                            selectionRepository.resetSelectionForPackage(originalPackageName)
                            optionsRepository.resetOptionsForPackage(originalPackageName)
                        }
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
                // Convert display name back to original name for database operations
                val originalPackageName = remember(target.packageName, packageNameMapping) {
                    packageNameMapping.entries
                        .find { it.value == target.packageName }?.key
                        ?: target.packageName
                }

                val patchCount = filteredSelections[target.packageName]?.get(target.bundleUid) ?: 0

                val confirmAction: () -> Unit = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Use original package name for database operations
                            selectionRepository.resetSelectionForPackageAndBundle(originalPackageName, target.bundleUid)
                            optionsRepository.resetOptionsForPackageAndBundle(originalPackageName, target.bundleUid)
                        }
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
        // Convert display name back to original for data loading
        val originalPackageName = remember(target.packageName, packageNameMapping) {
            packageNameMapping.entries
                .find { it.value == target.packageName }?.key
                ?: target.packageName
        }

        PatchDetailsDialog(
            originalPackageName = originalPackageName,  // For loading data from database
            displayPackageName = target.packageName,    // For UI display
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
    appDataResolver: AppDataResolver
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
            EmptyState(message = stringResource(R.string.settings_system_no_patches_or_options))
        } else {
            SelectionList(
                selections = selections,
                totalSelections = totalSelections,
                bundleNames = bundleNames,
                appDataResolver = appDataResolver,
                onSetResetTarget = onSetResetTarget,
                onShowPatchDetails = onShowPatchDetails
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
    onShowPatchDetails: (PatchDetailsTarget) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary box
        InfoBox(
            title = pluralStringResource(
                R.plurals.patch_selection_packages_count,
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
                    totalSelections,
                    totalSelections
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )
        }

        // List of packages with selections
        LazyColumn(
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
                    onShowPatchDetails = onShowPatchDetails
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
    onShowPatchDetails: (PatchDetailsTarget) -> Unit
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
                            onShowDetails = { onShowPatchDetails(PatchDetailsTarget(packageName, bundleUid)) }
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
    onShowDetails: () -> Unit
) {
    val importExportViewModel: ImportExportViewModel = koinInject()

    // Display bundle name or fallback to "Bundle #N"
    val displayName = bundleName ?: stringResource(R.string.settings_system_patch_selection_source_format, bundleUid)
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

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importExportViewModel.importPackageBundleData(
                targetBundleUid = bundleUid,
                source = it
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
 *
 * @param originalPackageName Original package name for loading data from database
 * @param displayPackageName Patched package name for UI display
 * @param bundleUid Bundle identifier
 * @param bundleName Bundle display name
 * @param appDataResolver Resolver for app data
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
private fun PatchDetailsDialog(
    originalPackageName: String,
    displayPackageName: String,
    bundleUid: Int,
    bundleName: String?,
    appDataResolver: AppDataResolver,
    onDismiss: () -> Unit
) {
    val selectionRepository: PatchSelectionRepository = koinInject()
    val optionsRepository: PatchOptionsRepository = koinInject()

    var displayName by remember { mutableStateOf(displayPackageName) }
    var patchList by remember { mutableStateOf<List<String>>(emptyList()) }
    var optionsMap by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Resolve app name using display package name
    LaunchedEffect(displayPackageName) {
        val appData = appDataResolver.resolveAppData(displayPackageName)
        displayName = appData.displayName
    }

    // Load patch selections and options using original package name
    LaunchedEffect(originalPackageName, bundleUid) {
        isLoading = true
        withContext(Dispatchers.IO) {
            // Load selections using original package name (from database)
            patchList = selectionRepository.exportForPackageAndBundle(originalPackageName, bundleUid)

            // Get raw serialized options using original package name (from database)
            val rawOptions = optionsRepository.exportOptionsForBundle(
                packageName = originalPackageName,
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
