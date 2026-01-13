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
import app.morphe.manager.R
import app.revanced.manager.patcher.patch.Option
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.patches.OptionItem
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection

/**
 * Advanced patch selection and configuration dialog
 * Shown before patching when expert mode is enabled
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

    // Create local mutable state from incoming selectedPatches
    var localSelectedPatches by remember(selectedPatches) {
        mutableStateOf(selectedPatches.toMap())
    }

    // Get all patches with their enabled state
    val allPatchesInfo = remember(bundles, selectedPatches, allowIncompatible) {
        bundles.map { bundle ->
            val selected = selectedPatches[bundle.uid] ?: emptySet()
            // In expert mode, always show all patches (force allowIncompatible = true)
            val patches = bundle.patchSequence(true)
                .map { patch -> patch to (patch.name in selected) }
                .toList()

            bundle to patches
        }.filter { it.second.isNotEmpty() }
    }

    // Filter patches based on search query
    val filteredPatchesInfo = remember(allPatchesInfo, searchQuery) {
        if (searchQuery.isBlank()) {
            allPatchesInfo
        } else {
            allPatchesInfo.mapNotNull { (bundle, patches) ->
                val filtered = patches.filter { (patch, _) ->
                    patch.name.contains(searchQuery, ignoreCase = true) ||
                            patch.description?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null else bundle to filtered
            }
        }
    }

    val totalSelectedCount = localSelectedPatches.values.sumOf { it.size }
    val totalPatchesCount = allPatchesInfo.sumOf { it.second.size }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_expert_mode_title),
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.morphe_expert_mode_proceed),
                onClick = {
                    // Sync all local changes back before proceeding
                    localSelectedPatches.forEach { (bundleUid, patches) ->
                        val originalPatches = selectedPatches[bundleUid] ?: emptySet()
                        patches.forEach { patchName ->
                            if (patchName !in originalPatches) {
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                        originalPatches.forEach { patchName ->
                            if (patchName !in patches) {
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                    }
                    // Handle removed bundles
                    selectedPatches.forEach { (bundleUid, patches) ->
                        if (bundleUid !in localSelectedPatches) {
                            patches.forEach { patchName ->
                                onPatchToggle(bundleUid, patchName)
                            }
                        }
                    }
                    onProceed()
                },
                enabled = totalSelectedCount > 0,
                icon = Icons.Outlined.AutoFixHigh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subtitle with count
            Text(
                text = stringResource(R.string.morphe_expert_mode_subtitle_extended, totalSelectedCount, totalPatchesCount),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
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
                        IconButton(onClick = { searchQuery = "" }) {
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                )
            )

            // Content
            if (filteredPatchesInfo.isEmpty()) {
                EmptyStateContent(
                    hasSearch = searchQuery.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredPatchesInfo.forEach { (bundle, patches) ->
                        val enabledCount = patches.count { it.second }
                        val totalCount = patches.size

                        BundleHeader(
                            bundleName = bundle.name,
                            enabledCount = enabledCount,
                            totalCount = totalCount,
                            onToggleAll = {
                                val allEnabled = enabledCount == totalCount
                                // If all enabled -> disable all enabled patches
                                // If any disabled -> enable all disabled patches

                                val currentPatches = localSelectedPatches.toMutableMap()
                                val bundlePatches = currentPatches[bundle.uid]?.toMutableSet() ?: mutableSetOf()

                                patches.forEach { (patch, isEnabled) ->
                                    if (allEnabled) {
                                        // Disable all currently enabled patches
                                        if (isEnabled) {
                                            bundlePatches.remove(patch.name)
                                        }
                                    } else {
                                        // Enable all currently disabled patches
                                        if (!isEnabled) {
                                            bundlePatches.add(patch.name)
                                        }
                                    }
                                }

                                if (bundlePatches.isEmpty()) {
                                    currentPatches.remove(bundle.uid)
                                } else {
                                    currentPatches[bundle.uid] = bundlePatches
                                }

                                localSelectedPatches = currentPatches
                            }
                        )

                        patches.forEach { (patch, isEnabled) ->
                            PatchCard(
                                patch = patch,
                                bundleUid = bundle.uid,
                                isEnabled = isEnabled,
                                onToggle = {
                                    val currentPatches = localSelectedPatches.toMutableMap()
                                    val bundlePatches = currentPatches[bundle.uid]?.toMutableSet() ?: mutableSetOf()

                                    if (patch.name in bundlePatches) {
                                        bundlePatches.remove(patch.name)
                                    } else {
                                        bundlePatches.add(patch.name)
                                    }

                                    if (bundlePatches.isEmpty()) {
                                        currentPatches.remove(bundle.uid)
                                    } else {
                                        currentPatches[bundle.uid] = bundlePatches
                                    }

                                    localSelectedPatches = currentPatches
                                },
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
 * Bundle header showing bundle name, patch count, and toggle all button
 */
@Composable
private fun BundleHeader(
    bundleName: String,
    enabledCount: Int,
    totalCount: Int,
    onToggleAll: () -> Unit
) {
    val allEnabled = enabledCount == totalCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = bundleName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalDialogTextColor.current
            )
            Text(
                text = "($enabledCount/$totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )
        }

        FilledTonalIconButton(
            onClick = onToggleAll,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (allEnabled)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                contentColor = if (allEnabled)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (allEnabled) Icons.Outlined.ClearAll else Icons.Outlined.DoneAll,
                contentDescription = stringResource(
                    if (allEnabled) R.string.morphe_expert_mode_disable_all
                    else R.string.morphe_expert_mode_enable_all
                ),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Individual patch card with toggle and options button
 */
@Composable
private fun PatchCard(
    patch: PatchInfo,
    bundleUid: Int,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        color = if (isEnabled) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
        },
        contentColor = if (isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        tonalElevation = if (isEnabled) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled)
                            LocalDialogTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!patch.description.isNullOrBlank()) {
                        Text(
                            text = patch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled)
                                LocalDialogSecondaryTextColor.current
                            else
                                LocalDialogSecondaryTextColor.current.copy(alpha = 0.4f),
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Options button (only enabled if patch is enabled)
                    if (hasOptions) {
                        FilledTonalIconButton(
                            onClick = onConfigureOptions,
                            modifier = Modifier.size(36.dp),
                            enabled = isEnabled,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.morphe_patch_options),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Toggle button
                    FilledTonalIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isEnabled)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = stringResource(
                                if (isEnabled) R.string.morphe_expert_mode_disable
                                else R.string.morphe_expert_mode_enable
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Options indicator (only show if patch is enabled)
            if (hasOptions && isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
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
                tint = LocalDialogSecondaryTextColor.current
            )
            Text(
                text = stringResource(
                    if (hasSearch)
                        R.string.morphe_expert_mode_no_results
                    else
                        R.string.morphe_expert_mode_no_patches
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
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
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = patch.name,
        titleTrailingContent = {
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    tint = LocalDialogTextColor.current
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.morphe_patch_options),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            if (patch.options == null) return@Column

            patch.options.forEach { option ->
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
