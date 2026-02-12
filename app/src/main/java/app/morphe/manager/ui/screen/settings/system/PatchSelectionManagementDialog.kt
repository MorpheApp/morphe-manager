package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.AppDataResolver
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
    onResetPackageBundle: (String, Int) -> Unit
) {
    var showResetAllConfirmation by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<ResetTarget?>(null) }

    val appDataResolver: AppDataResolver = koinInject()

    // Calculate total selections
    val totalSelections = remember(selections) {
        selections.values.sumOf { bundleMap -> bundleMap.values.sum() }
    }

    PatchSelectionManagementDialogContent(
        selections = selections,
        totalSelections = totalSelections,
        onDismiss = onDismiss,
        onShowResetAllConfirmation = { showResetAllConfirmation = true },
        onSetResetTarget = { resetTarget = it },
        appDataResolver = appDataResolver
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
}

/**
 * Main dialog content
 */
@Composable
private fun PatchSelectionManagementDialogContent(
    selections: Map<String, Map<Int, Int>>,
    totalSelections: Int,
    onDismiss: () -> Unit,
    onShowResetAllConfirmation: () -> Unit,
    onSetResetTarget: (ResetTarget) -> Unit,
    appDataResolver: AppDataResolver
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
                appDataResolver = appDataResolver,
                onSetResetTarget = onSetResetTarget
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
    appDataResolver: AppDataResolver,
    onSetResetTarget: (ResetTarget) -> Unit
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
                    appDataResolver = appDataResolver,
                    onResetPackage = resetPackageAction,
                    onResetPackageBundle = resetBundleAction
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
    appDataResolver: AppDataResolver,
    onResetPackage: () -> Unit,
    onResetPackageBundle: (Int) -> Unit
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bundleMap.forEach { (bundleUid, patchCount) ->
                        val resetAction: () -> Unit = { onResetPackageBundle(bundleUid) }

                        BundleSelectionItem(
                            bundleUid = bundleUid,
                            patchCount = patchCount,
                            onReset = resetAction
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
    bundleUid: Int,
    patchCount: Int,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_system_patch_selection_bundle_format, bundleUid),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogTextColor.current
            )
            Text(
                text = stringResource(R.string.settings_system_patch_selection_patches_count, patchCount),
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
        }

        MorpheDialogOutlinedButton(
            text = stringResource(R.string.reset),
            onClick = onReset,
            isDestructive = true,
            modifier = Modifier.wrapContentWidth()
        )
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
                    text = stringResource(
                        R.string.settings_system_patch_selection_patches_count,
                        patchCount
                    )
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
