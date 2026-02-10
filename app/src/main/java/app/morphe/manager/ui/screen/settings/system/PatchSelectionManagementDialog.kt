package app.morphe.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Data class representing a saved patch selection for display
 */
private data class SavedSelectionItemData(
    val packageName: String,
    val displayName: String,
    val patchCount: Int,
    val hasOptions: Boolean,
    val relatedPackages: Set<String> // All related packages (original + patched variants)
)

/**
 * Dialog for managing saved patch selections and options
 * Groups original and patched packages together
 */
@SuppressLint("LocalContextGetResourceValueCheck")
@Composable
fun PatchSelectionManagementDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm: PM = koinInject()

    val selectionRepository: PatchSelectionRepository = koinInject()
    val optionsRepository: PatchOptionsRepository = koinInject()
    val installedAppRepository: InstalledAppRepository = koinInject()
    val originalApkRepository: OriginalApkRepository = koinInject()
    val filesystem: Filesystem = koinInject()

    // Get packages with saved selections
    val packagesWithSelection by selectionRepository.getPackagesWithSavedSelection()
        .collectAsStateWithLifecycle(emptySet())

    // Get packages with saved options
    val packagesWithOptions by optionsRepository.getPackagesWithSavedOptions()
        .collectAsStateWithLifecycle(emptySet())

    // Combine both lists - use Set to avoid duplicates
    val allPackages = remember(packagesWithSelection, packagesWithOptions) {
        (packagesWithSelection + packagesWithOptions)
    }

    // Get detailed data for each package
    var selectionData by remember { mutableStateOf<List<SavedSelectionItemData>>(emptyList()) }

    // Extract strings to avoid LocalContext issues
    val selectionDeleted = stringResource(R.string.settings_system_patch_selection_deleted)

    // Create AppDataResolver
    val appDataResolver = remember(context, pm, originalApkRepository, installedAppRepository, filesystem) {
        AppDataResolver(context, pm, originalApkRepository, installedAppRepository, filesystem)
    }

    LaunchedEffect(allPackages) {
        if (allPackages.isEmpty()) {
            selectionData = emptyList()
            return@LaunchedEffect
        }

        val data = withContext(Dispatchers.IO) {
            // Group packages by their original package name
            val packageGroups = groupPackagesByOriginal(
                allPackages,
                installedAppRepository
            )

            packageGroups.map { (originalPackage, relatedPackages) ->
                // Get patch count only for original package
                val selections = selectionRepository.getSelection(originalPackage)
                val patchCount = selections.values.sumOf { it.size }

                // Check if any related package has options
                val hasOptions = relatedPackages.any { it in packagesWithOptions }

                // Use AppDataResolver to get data from: installed app → original APK → patched APK → constants
                val resolvedData = appDataResolver.resolveAppData(
                    originalPackage,
                    preferredSource = AppDataSource.INSTALLED
                )

                SavedSelectionItemData(
                    packageName = originalPackage,
                    displayName = resolvedData.displayName,
                    patchCount = patchCount,
                    hasOptions = hasOptions,
                    relatedPackages = relatedPackages
                )
            }.sortedBy { it.displayName }
        }

        selectionData = data
    }

    var itemToDelete by remember { mutableStateOf<String?>(null) }

    PatchSelectionManagementDialogContent(
        count = selectionData.size,
        isEmpty = selectionData.isEmpty(),
        onDismissRequest = onDismissRequest,
        itemsContent = {
            selectionData.forEach { item ->
                SavedSelectionItem(
                    data = item,
                    onDelete = { itemToDelete = item.packageName }
                )
            }
        }
    )

    if (itemToDelete != null) {
        val itemData = selectionData.find { it.packageName == itemToDelete }
        if (itemData != null) {
            DeleteSelectionConfirmationDialog(
                packageName = itemData.packageName,
                displayName = itemData.displayName,
                patchCount = itemData.patchCount,
                hasOptions = itemData.hasOptions,
                onDismiss = { itemToDelete = null },
                onConfirm = {
                    scope.launch {
                        // Delete selections and options for related packages
                        itemData.relatedPackages.forEach { pkg ->
                            selectionRepository.resetSelectionForPackage(pkg)
                            optionsRepository.resetOptionsForPackage(pkg)
                        }
                        context.toast(selectionDeleted)
                        itemToDelete = null
                    }
                }
            )
        }
    }
}

@Composable
private fun PatchSelectionManagementDialogContent(
    count: Int,
    isEmpty: Boolean,
    onDismissRequest: () -> Unit,
    itemsContent: @Composable ColumnScope.() -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.settings_system_patch_selections_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Summary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = pluralStringResource(R.plurals.settings_system_patch_selections_count, count, count),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalDialogTextColor.current
                        )
                        Text(
                            text = stringResource(R.string.settings_system_patch_selections_description_short),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // List of saved selections
            if (isEmpty) {
                EmptyState(message = stringResource(R.string.settings_system_patch_selections_empty))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsContent()
                }
            }
        }
    }
}

@Composable
private fun SavedSelectionItem(
    data: SavedSelectionItemData,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIcon(
                packageName = data.packageName,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                preferredSource = AppDataSource.ORIGINAL_APK
            )

            // Selection Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current
                )
                Text(
                    text = data.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )

                // Details
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.patchCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.patch_count,
                                data.patchCount,
                                data.patchCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                    }
                    if (data.hasOptions) {
                        InfoBadge(
                            text = stringResource(R.string.settings_system_patch_selections_has_options),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DeleteSelectionConfirmationDialog(
    packageName: String,
    displayName: String,
    patchCount: Int,
    hasOptions: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_patch_selection_delete_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Icon
            AppIcon(
                packageName = packageName,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                preferredSource = AppDataSource.ORIGINAL_APK
            )

            // App Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalDialogTextColor.current,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current,
                    textAlign = TextAlign.Center
                )
            }

            // What will be deleted
            DeletionWarningBox(
                warningText = stringResource(R.string.home_app_info_remove_app_warning)
            ) {
                if (patchCount > 0) {
                    DeleteListItem(
                        icon = Icons.Outlined.CheckCircle,
                        text = pluralStringResource(
                            R.plurals.patch_count,
                            patchCount,
                            patchCount
                        )
                    )
                }

                if (hasOptions) {
                    DeleteListItem(
                        icon = Icons.Outlined.Tune,
                        text = stringResource(R.string.settings_system_patch_selections_patches_options)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

/**
 * Groups packages by their original package name
 * Returns map of original package -> set of all related packages (including the original)
 */
suspend fun groupPackagesByOriginal(
    allPackages: Set<String>,
    installedAppRepository: InstalledAppRepository
): Map<String, Set<String>> {
    val packageToOriginal = mutableMapOf<String, String>()
    val originalPackages = mutableSetOf<String>()

    // First pass: identify which packages are patched and their originals
    for (packageName in allPackages) {
        val installedApp = installedAppRepository.get(packageName)
        if (installedApp != null) {
            // This is a patched app
            packageToOriginal[packageName] = installedApp.originalPackageName
            originalPackages.add(installedApp.originalPackageName)
        }
    }

    // Second pass: treat remaining packages as originals
    for (packageName in allPackages) {
        if (packageName !in packageToOriginal) {
            originalPackages.add(packageName)
            packageToOriginal[packageName] = packageName
        }
    }

    // Group by original package
    val groups = mutableMapOf<String, MutableSet<String>>()
    for ((pkg, original) in packageToOriginal) {
        groups.getOrPut(original) { mutableSetOf() }.add(pkg)
    }

    return groups
}
