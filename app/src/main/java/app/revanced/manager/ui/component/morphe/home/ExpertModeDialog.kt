package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.manager.R
import app.revanced.manager.patcher.patch.Option
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.patches.OptionItem
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection

/**
 * Expert Mode Dialog - Advanced patch selection and configuration dialog
 * Shown before patching when expert mode is enabled
 *
 * Features:
 * - View all selected patches
 * - Deselect patches
 * - Configure patch options
 * - Modern Morphe-style UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertModeDialog(
    bundles: List<PatchBundleInfo.Scoped>,
    selectedPatches: PatchSelection,
    options: Options,
    onPatchToggle: (bundleUid: Int, patchName: String) -> Unit,
    onOptionChange: (bundleUid: Int, patchName: String, optionKey: String, value: Any?) -> Unit,
    onResetOptions: (bundleUid: Int, patchName: String) -> Unit,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
    allowIncompatible: Boolean = false
) {
    var selectedPatchForOptions by remember { mutableStateOf<Pair<Int, PatchInfo>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Calculate selected patches per bundle
    val selectedPatchesInfo = remember(bundles, selectedPatches) {
        bundles.mapNotNull { bundle ->
            val selected = selectedPatches[bundle.uid] ?: return@mapNotNull null
            if (selected.isEmpty()) return@mapNotNull null

            val patches = bundle.patchSequence(allowIncompatible)
                .filter { it.name in selected }
                .toList()

            if (patches.isEmpty()) return@mapNotNull null
            bundle to patches
        }
    }

    // Filter patches based on search
    val filteredPatchesInfo = remember(selectedPatchesInfo, searchQuery) {
        if (searchQuery.isBlank()) {
            selectedPatchesInfo
        } else {
            selectedPatchesInfo.mapNotNull { (bundle, patches) ->
                val filtered = patches.filter { patch ->
                    patch.name.contains(searchQuery, ignoreCase = true) ||
                            patch.description?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null else bundle to filtered
            }
        }
    }

    val totalSelectedCount = selectedPatches.values.sumOf { it.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                ExpertModeHeader(
                    totalSelectedCount = totalSelectedCount,
                    onDismiss = onDismiss,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it }
                )

                // Content
                if (filteredPatchesInfo.isEmpty()) {
                    EmptyStateContent(
                        hasSearch = searchQuery.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        filteredPatchesInfo.forEach { (bundle, patches) ->
                            item(key = "bundle_${bundle.uid}") {
                                BundleHeader(
                                    bundleName = bundle.name,
                                    patchCount = patches.size
                                )
                            }

                            items(patches, key = { "patch_${bundle.uid}_${it.name}" }) { patch ->
                                PatchCard(
                                    patch = patch,
                                    bundleUid = bundle.uid,
                                    onToggle = { onPatchToggle(bundle.uid, patch.name) },
                                    onConfigureOptions = {
                                        if (!patch.options.isNullOrEmpty()) {
                                            selectedPatchForOptions = bundle.uid to patch
                                        }
                                    },
                                    hasOptions = !patch.options.isNullOrEmpty()
                                )
                            }
                        }
                    }
                }

                // Footer
                ExpertModeFooter(
                    onProceed = onProceed,
                    canProceed = totalSelectedCount > 0
                )
            }
        }
    }

    // Options dialog
    selectedPatchForOptions?.let { (bundleUid, patch) ->
        PatchOptionsDialog(
            patch = patch,
            bundleUid = bundleUid,
            values = options[bundleUid]?.get(patch.name),
            onValueChange = { key, value ->
                onOptionChange(bundleUid, patch.name, key, value)
            },
            onReset = {
                onResetOptions(bundleUid, patch.name)
            },
            onDismiss = { selectedPatchForOptions = null }
        )
    }
}

/**
 * Expert mode dialog header with title and search
 */
@Composable
private fun ExpertModeHeader(
    totalSelectedCount: Int,
    onDismiss: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title row with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.morphe_expert_mode_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.morphe_expert_mode_subtitle, totalSelectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.morphe_expert_mode_search_placeholder))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Bundle header showing bundle name and patch count
 */
@Composable
private fun BundleHeader(
    bundleName: String,
    patchCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Source,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = bundleName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "($patchCount)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Individual patch card with toggle and options button
 */
@Composable
private fun PatchCard(
    patch: PatchInfo,
    bundleUid: Int,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with patch name and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = patch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!patch.description.isNullOrBlank()) {
                        Text(
                            text = patch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Options button
                    if (hasOptions) {
                        IconButton(
                            onClick = onConfigureOptions,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.morphe_patch_options),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Expand/collapse button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                            contentDescription = if (isExpanded)
                                stringResource(R.string.collapse)
                            else
                                stringResource(R.string.expand),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Remove button
                    FilledTonalIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Options indicator
            if (hasOptions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.morphe_expert_mode_has_options),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state content when no patches match search or none selected
 */
@Composable
private fun EmptyStateContent(
    hasSearch: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Outlined.SearchOff else Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(
                    if (hasSearch)
                        R.string.morphe_expert_mode_no_results
                    else
                        R.string.morphe_expert_mode_no_patches
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Footer with proceed button
 */
@Composable
private fun ExpertModeFooter(
    onProceed: () -> Unit,
    canProceed: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onProceed,
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.morphe_expert_mode_proceed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Options dialog for configuring patch options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchOptionsDialog(
    patch: PatchInfo,
    bundleUid: Int,
    values: Map<String, Any?>?,
    onValueChange: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = patch.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.morphe_patch_options),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = onReset) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = stringResource(R.string.reset)
                                    )
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(android.R.string.cancel)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (patch.options == null) return@LazyColumn

                    items(patch.options, key = { it.key }) { option ->
                        val key = option.key
                        val value = if (values == null || !values.contains(key)) {
                            option.default
                        } else {
                            values[key]
                        }

                        @Suppress("UNCHECKED_CAST")
                        OptionItem(
                            option = option as Option<Any>,
                            value = value,
                            setValue = { newValue ->
                                onValueChange(key, newValue)
                            },
                            selectionWarningEnabled = false
                        )
                    }
                }
            }
        }
    }
}
